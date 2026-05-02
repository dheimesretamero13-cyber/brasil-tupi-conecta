package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// ChatRepository.kt  · Fase 3.5
//
// Responsabilidades:
//  • Buscar histórico da sessão via REST
//  • Enviar mensagem (INSERT na tabela chats)
//  • Assinar canal Realtime via WebSocket Phoenix
//  • O chat é volátil — a trigger no banco limpa ao encerrar agendamento
// ═══════════════════════════════════════════════════════════════════════════

import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

private val WS_URL_CHAT = LOCAL_URL
    .replace("https://", "wss://")
    .replace("http://", "ws://") + "/realtime/v1/websocket"

private const val TAG_CHAT = "ChatRepository"
private val jsonChat = Json { ignoreUnknownKeys = true; isLenient = true }

// ── Modelos ───────────────────────────────────────────────────────────────

@Serializable
data class MensagemChat(
    val id:           String,
    @SerialName("session_id")    val sessionId:    String,
    @SerialName("remetente_id")  val remetenteId:  String,
    val texto:        String,
    @SerialName("criado_em")     val criadoEm:     String = "",
)

@Serializable
private data class EnviarMensagemChatRequest(
    @SerialName("session_id")   val sessionId:   String,
    @SerialName("remetente_id") val remetenteId: String,
    val texto: String,
)

// ── Repositório ───────────────────────────────────────────────────────────

class ChatRepository {

    private val _mensagens = MutableStateFlow<List<MensagemChat>>(emptyList())
    val mensagens: StateFlow<List<MensagemChat>> = _mensagens.asStateFlow()

    private val _statusConexao = MutableStateFlow(false)
    val statusConexao: StateFlow<Boolean> = _statusConexao.asStateFlow()

    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wsJob: Job? = null

    // ── 1. BUSCAR HISTÓRICO ───────────────────────────────────────────────
    suspend fun buscarHistorico(sessionId: String) {
        val token = currentToken ?: LOCAL_KEY
        try {
            val lista = httpClient.get("$LOCAL_URL/rest/v1/chats") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("session_id", "eq.$sessionId")
                parameter("select",     "id,session_id,remetente_id,texto,criado_em")
                parameter("order",      "criado_em.asc")
                parameter("limit",      "100")
            }.body<List<MensagemChat>>()
            _mensagens.value = lista
        } catch (e: Exception) {
            AppLogger.erroRede("/rest/v1/chats", e, "sessionId=$sessionId")
        }
    }

    // ── 2. ENVIAR MENSAGEM ────────────────────────────────────────────────
    suspend fun enviar(sessionId: String, texto: String): Boolean {
        val remetenteId = currentUserId ?: return false
        val token       = currentToken  ?: LOCAL_KEY
        return try {
            val response = httpClient.post("$LOCAL_URL/rest/v1/chats") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody(EnviarMensagemChatRequest(
                    sessionId   = sessionId,
                    remetenteId = remetenteId,
                    texto       = texto,
                ))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            AppLogger.erroRede("/rest/v1/chats (insert)", e, "session=$sessionId")
            false
        }
    }

    // ── 3. REALTIME VIA WEBSOCKET PHOENIX ─────────────────────────────────
    fun iniciarRealtime(sessionId: String) {
        wsJob?.cancel()
        wsJob = scope.launch {
            val wsClient = io.ktor.client.HttpClient(Android) {
                install(WebSockets) { pingInterval = 25_000L }
            }
            val delays    = listOf(2_000L, 5_000L, 15_000L, 30_000L)
            var tentativa = 0

            while (currentCoroutineContext().isActive) {
                try {
                    val token = currentToken ?: LOCAL_KEY
                    wsClient.webSocket(
                        urlString = "$WS_URL_CHAT?apikey=$LOCAL_KEY&vsn=1.0.0",
                        request   = { header("Authorization", "Bearer $token") },
                    ) {
                        tentativa = 0
                        _statusConexao.value = true
                        send(Frame.Text(buildJoinChat(sessionId)))
                        AppLogger.info(TAG_CHAT, "Realtime chat conectado: session=$sessionId")

                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            processarFrame(frame.readText(), sessionId)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _statusConexao.value = false
                    AppLogger.erroRealtime("chat_ws", sessionId, e)
                    val delayMs = delays.getOrElse(tentativa) { 30_000L }
                    delay(delayMs)
                    tentativa = (tentativa + 1).coerceAtMost(delays.lastIndex)
                }
            }
            wsClient.close()
        }
    }

    private fun processarFrame(texto: String, sessionId: String) {
        try {
            val obj   = jsonChat.parseToJsonElement(texto).jsonObject
            val event = obj["event"]?.jsonPrimitive?.content ?: return
            if (event != "postgres_changes") return

            val record = obj["payload"]?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("record")?.jsonObject ?: return

            val msg = MensagemChat(
                id          = record["id"]?.jsonPrimitive?.content ?: return,
                sessionId   = record["session_id"]?.jsonPrimitive?.content ?: return,
                remetenteId = record["remetente_id"]?.jsonPrimitive?.content ?: return,
                texto       = record["texto"]?.jsonPrimitive?.content ?: return,
                criadoEm    = record["criado_em"]?.jsonPrimitive?.content ?: "",
            )

            // Evitar duplicatas (pode chegar do Realtime após optimistic update)
            if (_mensagens.value.none { it.id == msg.id }) {
                _mensagens.value = _mensagens.value + msg
            }
        } catch (e: Exception) {
            AppLogger.aviso(TAG_CHAT, "Falha ao processar frame: ${e.message}")
        }
    }

    private fun buildJoinChat(sessionId: String): String =
        buildJsonObject {
            put("event", "phx_join")
            put("topic", "realtime:chats:session_id=eq.$sessionId")
            putJsonObject("payload") {
                putJsonObject("config") {
                    putJsonObject("broadcast") { put("self", false) }
                    putJsonArray("postgres_changes") {
                        addJsonObject {
                            put("event",  "INSERT")
                            put("schema", "public")
                            put("table",  "chats")
                            put("filter", "session_id=eq.$sessionId")
                        }
                    }
                }
            }
            put("ref", "chat_1")
        }.toString()

    // ── 4. MÉTODOS PARA OPTIMISTIC UPDATE ─────────────────────────────────
    fun adicionarTemp(msg: MensagemChat) {
        _mensagens.value = _mensagens.value + msg
    }

    fun removerTemp(tempId: String) {
        _mensagens.value = _mensagens.value.filter { it.id != tempId }
    }

    fun parar() {
        wsJob?.cancel()
        _statusConexao.value = false
        _mensagens.value = emptyList()
    }
}

object ChatRepositoryFactory {
    fun create(): ChatRepository = ChatRepository()
}