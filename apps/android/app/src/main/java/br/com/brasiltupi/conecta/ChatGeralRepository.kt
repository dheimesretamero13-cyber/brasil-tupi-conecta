package br.com.brasiltupi.conecta

import io.ktor.client.HttpClient          // ← ESSENCIAL para usar HttpClient(Android)
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

// ═══════════════════════════════════════════════════════════════════════════
// ChatGeralRepository.kt  · (Fase 3.5 - Realtime no chat geral)
//
// Gerencia mensagens persistentes (tabela mensagens) entre dois usuários.
// Substitui o antigo polling/fetch pós‑envio por Realtime Phoenix.
// ═══════════════════════════════════════════════════════════════════════════

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

private val WS_URL = LOCAL_URL
    .replace("https://", "wss://")
    .replace("http://", "ws://") + "/realtime/v1/websocket"

private val jsonChat = Json { ignoreUnknownKeys = true; isLenient = true }

// ── Modelos ───────────────────────────────────────────────────────────────

@Serializable
data class MensagemGeral(
    val id: String,
    @SerialName("remetente_id")    val remetenteId:    String,
    @SerialName("destinatario_id") val destinatarioId: String,
    val texto: String,
    @SerialName("created_at")      val createdAt:      String = "",
)

@Serializable
private data class EnviarMensagemGeralRequest(
    @SerialName("remetente_id")    val remetenteId:    String,
    @SerialName("destinatario_id") val destinatarioId: String,
    val texto: String,
)

// ═══════════════════════════════════════════════════════════════════════════
// REPOSITÓRIO
// ═══════════════════════════════════════════════════════════════════════════

class ChatGeralRepository(
    private val meuId: String,
    private val outroId: String,
) {
    private val _mensagens = MutableStateFlow<List<MensagemGeral>>(emptyList())
    val mensagens: StateFlow<List<MensagemGeral>> = _mensagens.asStateFlow()

    private val _statusConexao = MutableStateFlow(false)
    val statusConexao: StateFlow<Boolean> = _statusConexao.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wsJob: Job? = null

    // ── 1. BUSCAR HISTÓRICO ───────────────────────────────────────────────
    suspend fun carregarHistorico() {
        val token = currentToken ?: LOCAL_KEY
        try {
            val lista = httpClient.get("$LOCAL_URL/rest/v1/mensagens") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("or",
                    "(and(remetente_id.eq.$meuId,destinatario_id.eq.$outroId)," +
                            "and(remetente_id.eq.$outroId,destinatario_id.eq.$meuId))")
                parameter("select", "id,remetente_id,destinatario_id,texto,created_at")
                parameter("order", "created_at.asc")
                parameter("limit", "100")
            }.body<List<MensagemGeral>>()
            _mensagens.value = lista
        } catch (e: Exception) {
            AppLogger.erroRede("chat_geral_historico", e, "meu=$meuId outro=$outroId")
        }
    }

    // ── 2. ENVIAR MENSAGEM ────────────────────────────────────────────────
    suspend fun enviarMensagem(texto: String): Boolean {
        val token = currentToken ?: LOCAL_KEY
        return try {
            val response = httpClient.post("$LOCAL_URL/rest/v1/mensagens") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody(EnviarMensagemGeralRequest(
                    remetenteId    = meuId,
                    destinatarioId = outroId,
                    texto          = texto,
                ))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            AppLogger.erroRede("chat_geral_enviar", e, "de=$meuId para=$outroId")
            false
        }
    }

    // ── 3. REALTIME ───────────────────────────────────────────────────────
    fun iniciarRealtime() {
        wsJob?.cancel()
        wsJob = scope.launch {
            val wsClient = HttpClient(Android) {
                install(WebSockets) { pingInterval = 25_000L }
            }
            val delays = listOf(2_000L, 5_000L, 15_000L, 30_000L)
            var tentativa = 0

            while (isActive) {
                try {
                    val token = currentToken ?: LOCAL_KEY
                    wsClient.webSocket(
                        urlString = "$WS_URL?apikey=$LOCAL_KEY&vsn=1.0.0",
                        request = { header("Authorization", "Bearer $token") },
                    ) {
                        tentativa = 0
                        _statusConexao.value = true
                        send(Frame.Text(buildJoinMensagens()))
                        AppLogger.info("ChatGeral", "Realtime conectado")

                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            processarFrame(frame.readText())
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _statusConexao.value = false
                    if (tentativa >= 2)
                        AppLogger.erroRealtime("chat_geral_ws", "$meuId-$outroId", e)
                    else
                        AppLogger.aviso("ChatGeral", "Realtime reconectando: ${e.message}")
                    delay(delays.getOrElse(tentativa) { 30_000L })
                    tentativa = (tentativa + 1).coerceAtMost(delays.lastIndex)
                }
            }
            wsClient.close()
        }
    }

    private fun buildJoinMensagens(): String = buildJsonObject {
        put("event", "phx_join")
        put("topic", "realtime:mensagens")
        putJsonObject("payload") {
            putJsonObject("config") {
                putJsonObject("broadcast") { put("self", false) }
                putJsonArray("postgres_changes") {
                    addJsonObject {
                        put("event",  "INSERT")
                        put("schema", "public")
                        put("table",  "mensagens")
                        // Sem filtro no Phoenix; o filtro será feito no processarFrame
                    }
                }
            }
        }
        put("ref", "msg_1")
    }.toString()

    private fun processarFrame(texto: String) {
        try {
            val obj = jsonChat.parseToJsonElement(texto).jsonObject
            val event = obj["event"]?.jsonPrimitive?.content ?: return
            if (event != "postgres_changes") return

            val record = obj["payload"]?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("record")?.jsonObject ?: return

            val msg = MensagemGeral(
                id              = record["id"]?.jsonPrimitive?.content ?: return,
                remetenteId     = record["remetente_id"]?.jsonPrimitive?.content ?: return,
                destinatarioId  = record["destinatario_id"]?.jsonPrimitive?.content ?: return,
                texto           = record["texto"]?.jsonPrimitive?.content ?: return,
                createdAt       = record["created_at"]?.jsonPrimitive?.content ?: "",
            )

            // Filtra apenas mensagens desta conversa
            val relevante = (msg.remetenteId == meuId && msg.destinatarioId == outroId) ||
                    (msg.remetenteId == outroId && msg.destinatarioId == meuId)
            if (!relevante) return

            if (_mensagens.value.none { it.id == msg.id }) {
                _mensagens.value = _mensagens.value + msg
            }
        } catch (e: Exception) {
            AppLogger.aviso("ChatGeral", "Falha ao processar frame: ${e.message}")
        }
    }

    // ── 4. MÉTODOS PARA OPTIMISTIC UPDATE ─────────────────────────────────
    fun adicionarTemp(msg: MensagemGeral) {
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