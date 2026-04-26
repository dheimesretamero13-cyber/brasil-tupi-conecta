package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// UrgenciasRealtime.kt  · v2
// Supabase Realtime via WebSocket (protocolo Phoenix) — Ktor puro.
//
// MUDANÇAS v2 (em relação à v1):
//  • aceitarUrgencia() substituído por aceitarViaRpc() — chama a função
//    PostgreSQL `accept_urgencia(p_urgencia_id)` via /rest/v1/rpc/.
//    Nenhum PATCH direto em urgencias existe mais neste projeto.
//  • Retorno tipado: sealed class ResultadoAceitacao
//    (Sucesso | JaAtendida | ErroRede)
//  • StateFlow<AceitacaoState> exposto ao objeto — UI nunca fica em estado
//    indefinido, mesmo em timeout ou erro de rede.
//  • AceitarUrgenciaRequest e o PATCH antigo removidos por completo.
// ═══════════════════════════════════════════════════════════════════════════

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private const val TAG      = "UrgenciasRealtime"
private const val WS_URL   = "wss://qfzdchrlbqcvewjivaqz.supabase.co/realtime/v1/websocket"
private const val REST_URL = "https://qfzdchrlbqcvewjivaqz.supabase.co"
private const val API_KEY  = "sb_publishable_SM-UHBh_5lzTSBZ2YPUIYw_Sw1i8qeq"

// JSON singleton — evita recriar o parser a cada frame WebSocket
private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════════════════
// STATUS DO REALTIME
// ═══════════════════════════════════════════════════════════════════════════

enum class StatusRealtime {
    CONECTANDO,
    CONECTADO,
    INSTAVEL,
    OFFLINE,
}

// ═══════════════════════════════════════════════════════════════════════════
// MODELOS
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
data class Urgencia(
    val id: String,
    @SerialName("cliente_id")      val clienteId: String?      = null,
    @SerialName("professional_id") val professionalId: String? = null,
    val status: String                                          = "pending",
    val especialidade: String?                                  = null,
    @SerialName("nome_cliente")    val nomeCliente: String?     = null,
    @SerialName("criado_em")       val criadoEm: String?        = null,
)

// ── Eventos Realtime ──────────────────────────────────────────────────────

sealed class EventoUrgencia {
    /** Novo chamado pendente — exibir alerta fullscreen no profissional. */
    data class NovoChamado(val urgencia: Urgencia)             : EventoUrgencia()
    /** Aceito por outro — fechar alerta com feedback. */
    data class ChamadoAceito(val urgenciaId: String)           : EventoUrgencia()
    /** Cancelado/expirado/finalizado — fechar alerta. */
    data class ChamadoEncerrado(val urgenciaId: String,
                                val motivo: String)            : EventoUrgencia()
    /** Em progresso — redirecionar para sala de chamada. */
    data class ChamadaIniciada(val urgenciaId: String)         : EventoUrgencia()
}

// ═══════════════════════════════════════════════════════════════════════════
// RESULTADO DA RPC accept_urgencia — tarefa 2
//
//  Sucesso    → RPC retornou true  → navegar para sala de chamada imediatamente
//  JaAtendida → RPC retornou false → exibir "Já atendida", remover da lista
//  ErroRede   → exception/timeout  → exibir erro, manter estado consistente
// ═══════════════════════════════════════════════════════════════════════════

sealed class ResultadoAceitacao {
    object Sucesso    : ResultadoAceitacao()
    object JaAtendida : ResultadoAceitacao()
    data class ErroRede(val mensagem: String) : ResultadoAceitacao()
}

// ── Estado observável durante o ciclo de aceitação — tarefa 3 ────────────

sealed class AceitacaoState {
    /** Nenhuma operação em curso — estado padrão. */
    object Idle       : AceitacaoState()
    /** RPC disparada, aguardando resposta do servidor. */
    object Carregando : AceitacaoState()
    /** RPC concluída — UI deve reagir ao resultado e então o estado volta a Idle. */
    data class Resultado(val resultado: ResultadoAceitacao) : AceitacaoState()
}

// ═══════════════════════════════════════════════════════════════════════════
// CLIENTE WEBSOCKET DEDICADO
// Separado do httpClient de REST para não misturar ciclos de vida.
// ═══════════════════════════════════════════════════════════════════════════

private val wsClient = HttpClient(Android) {
    install(WebSockets) {
        pingInterval = 25_000L   // keep-alive (< timeout Supabase de 60 s)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// OBJETO GERENCIADOR
// ═══════════════════════════════════════════════════════════════════════════

object UrgenciasRealtimeManager {

    // ── Flows públicos ────────────────────────────────────────────────────

    private val _status = MutableStateFlow(StatusRealtime.CONECTANDO)
    val status: StateFlow<StatusRealtime> = _status.asStateFlow()

    private val _eventos = MutableSharedFlow<EventoUrgencia>(
        extraBufferCapacity = 8,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST,
    )
    val eventos: SharedFlow<EventoUrgencia> = _eventos.asSharedFlow()

    private val _aceitacaoState = MutableStateFlow<AceitacaoState>(AceitacaoState.Idle)
    /**
     * Estado da operação de aceitação.
     * Colete este flow no Composable para:
     *  • Mostrar CircularProgressIndicator enquanto Carregando
     *  • Navegar para a sala quando Resultado(Sucesso)
     *  • Exibir diálogo quando Resultado(JaAtendida)
     *  • Exibir toast de erro quando Resultado(ErroRede)
     */
    val aceitacaoState: StateFlow<AceitacaoState> = _aceitacaoState.asStateFlow()

    private var realtimeJob: Job? = null
    private var pollingJob:  Job? = null
    private var scope: CoroutineScope? = null

    /** Callback alternativo ao Flow — opcional, para quem preferir lambda. */
    var onEvento: ((EventoUrgencia) -> Unit)? = null

    // ── Ciclo de vida ─────────────────────────────────────────────────────

    fun iniciar(profissionalId: String, especialidade: String? = null) {
        // Registrar contexto no Crashlytics — qualquer crash posterior
        // mostrará o ID do profissional e o estado do Realtime
        AppLogger.chave("realtime_prof_id",   profissionalId)
        AppLogger.chave("realtime_ativo",      true)
        AppLogger.chave("realtime_especial",   especialidade ?: "todas")
        AppLogger.info(TAG, "Realtime iniciado para prof=$profissionalId")

        val ctx = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = ctx
        conectarComRetry(ctx, profissionalId, especialidade)
    }

    fun parar() {
        realtimeJob?.cancel()
        pollingJob?.cancel()
        scope?.cancel()
        _status.tryEmit(StatusRealtime.OFFLINE)
        _aceitacaoState.tryEmit(AceitacaoState.Idle)
        Log.i(TAG, "Realtime encerrado.")
    }

    // ── Retry com back-off exponencial ────────────────────────────────────

    private fun conectarComRetry(
        ctx: CoroutineScope,
        profissionalId: String,
        especialidade: String?,
    ) {
        realtimeJob?.cancel()
        realtimeJob = ctx.launch {
            val delays    = listOf(2_000L, 5_000L, 10_000L, 30_000L)
            var tentativa = 0
            while (isActive) {
                _status.emit(StatusRealtime.CONECTANDO)
                try {
                    conectarWebSocket(profissionalId, especialidade)
                    tentativa = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Conexão falhou (tentativa ${tentativa + 1}): ${e.message}")
                    // Instrumentar no Crashlytics apenas a partir da 3ª tentativa
                    // (1ª e 2ª são esperadas em mobile com rede instável)
                    if (tentativa >= 2) {
                        AppLogger.erroRealtime(
                            fase           = "conexao_tentativa_${tentativa + 1}",
                            profissionalId = profissionalId,
                            throwable      = e,
                        )
                    } else {
                        AppLogger.aviso(TAG, "Reconexão ${tentativa + 1}: ${e.message}")
                    }
                }
                _status.emit(StatusRealtime.INSTAVEL)
                ativarPollingFallback(profissionalId, especialidade)
                val delayMs = delays.getOrElse(tentativa) { 30_000L }
                Log.i(TAG, "Reconectando em ${delayMs / 1000}s…")
                delay(delayMs)
                tentativa = (tentativa + 1).coerceAtMost(delays.lastIndex)
            }
        }
    }

    // ── WebSocket principal ───────────────────────────────────────────────

    private suspend fun conectarWebSocket(profissionalId: String, especialidade: String?) {
        val token = currentToken ?: API_KEY
        wsClient.webSocket(
            urlString = "$WS_URL?apikey=$API_KEY&vsn=1.0.0",
            request   = { header("Authorization", "Bearer $token") },
        ) {
            Log.i(TAG, "WebSocket conectado.")
            _status.emit(StatusRealtime.CONECTADO)
            pollingJob?.cancel()

            send(Frame.Text(buildJoinMessage()))
            sincronizarPendentes(profissionalId, especialidade)

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    processarFrame(frame.readText(), profissionalId, especialidade)
                }
            }
            Log.w(TAG, "WebSocket encerrado pelo servidor.")
        }
    }

    // ── Protocolo Phoenix ─────────────────────────────────────────────────

    private fun buildJoinMessage(): String {
        val payload = buildJsonObject {
            putJsonObject("config") {
                putJsonObject("broadcast") { put("self", false) }
                putJsonObject("presence")  { put("key", "") }
                putJsonArray("postgres_changes") {
                    addJsonObject { put("event", "INSERT"); put("schema", "public"); put("table", "urgencias") }
                    addJsonObject { put("event", "UPDATE"); put("schema", "public"); put("table", "urgencias") }
                }
            }
        }
        return buildJsonObject {
            put("event",   "phx_join")
            put("topic",   "realtime:urgencias")
            put("payload", payload)
            put("ref",     "1")
        }.toString()
    }

    // ── Processar frame WebSocket ─────────────────────────────────────────

    private suspend fun processarFrame(
        texto: String,
        profissionalId: String,
        especialidade: String?,
    ) {
        try {
            val objeto  = jsonParser.parseToJsonElement(texto).jsonObject
            val event   = objeto["event"]?.jsonPrimitive?.content ?: return
            val payload = objeto["payload"]?.jsonObject ?: return
            when (event) {
                "phx_reply" ->
                    Log.d(TAG, "Canal confirmado: ${payload["status"]?.jsonPrimitive?.content}")
                "postgres_changes" -> {
                    val data       = payload["data"]?.jsonObject ?: return
                    val tipoEvento = data["type"]?.jsonPrimitive?.content ?: return
                    val record     = data["record"]?.jsonObject ?: return
                    val urgencia   = jsonParser.decodeFromJsonElement<Urgencia>(record)
                    when (tipoEvento) {
                        "INSERT" -> tratarInsert(urgencia, profissionalId, especialidade)
                        "UPDATE" -> tratarUpdate(urgencia, profissionalId)
                    }
                }
                "phx_error", "phx_close" -> {
                    Log.w(TAG, "Canal encerrado: $event")
                    throw Exception("Canal Phoenix encerrado: $event")
                }
                else -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar frame: ${e.message}")
            AppLogger.erroRealtime(
                fase           = "frame",
                profissionalId = profissionalId,
                throwable      = e,
            )
            if (e.message?.contains("Canal Phoenix") == true) throw e
        }
    }

    // ── Lógica INSERT ─────────────────────────────────────────────────────

    private suspend fun tratarInsert(
        urgencia: Urgencia,
        profissionalId: String,
        especialidade: String?,
    ) {
        val eParaMim       = urgencia.professionalId == null || urgencia.professionalId == profissionalId
        val especialidadeOk = especialidade == null ||
                urgencia.especialidade?.equals(especialidade, ignoreCase = true) == true
        if (eParaMim && especialidadeOk && urgencia.status == "pending") {
            Log.i(TAG, "Novo chamado: ${urgencia.id}")
            emitirEvento(EventoUrgencia.NovoChamado(urgencia))
        }
    }

    // ── Lógica UPDATE ─────────────────────────────────────────────────────

    private suspend fun tratarUpdate(urgencia: Urgencia, profissionalId: String) {
        when (urgencia.status) {
            "accepted"    -> if (urgencia.professionalId != profissionalId)
                emitirEvento(EventoUrgencia.ChamadoAceito(urgencia.id))
            "cancelled"   -> emitirEvento(EventoUrgencia.ChamadoEncerrado(urgencia.id, "cancelled"))
            "expired"     -> emitirEvento(EventoUrgencia.ChamadoEncerrado(urgencia.id, "expired"))
            "in_progress" -> emitirEvento(EventoUrgencia.ChamadaIniciada(urgencia.id))
            "finished"    -> emitirEvento(EventoUrgencia.ChamadoEncerrado(urgencia.id, "finished"))
            else          -> Unit
        }
    }

    // ── Emitir evento ─────────────────────────────────────────────────────

    private suspend fun emitirEvento(evento: EventoUrgencia) {
        _eventos.emit(evento)
        withContext(Dispatchers.Main) { onEvento?.invoke(evento) }
    }

    // ── Fallback: polling inteligente ─────────────────────────────────────

    private fun ativarPollingFallback(profissionalId: String, especialidade: String?) {
        pollingJob?.cancel()
        pollingJob = scope?.launch(Dispatchers.IO) {
            Log.i(TAG, "Polling fallback ativado (3s).")
            val vistasNoFallback = mutableSetOf<String>()
            while (isActive) {
                delay(3_000L)
                try {
                    buscarUrgenciasPendentesRest(profissionalId, especialidade)
                        .filter { vistasNoFallback.add(it.id) }
                        .forEach { emitirEvento(EventoUrgencia.NovoChamado(it)) }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling falhou: ${e.message}")
                    AppLogger.erroRealtime(
                        fase           = "polling",
                        profissionalId = profissionalId,
                        throwable      = e,
                    )
                    _status.emit(StatusRealtime.OFFLINE)
                }
            }
        }
    }

    // ── Sincronizar ao reconectar ─────────────────────────────────────────

    private suspend fun sincronizarPendentes(profissionalId: String, especialidade: String?) {
        try {
            val pendentes = buscarUrgenciasPendentesRest(profissionalId, especialidade)
            pendentes.forEach { emitirEvento(EventoUrgencia.NovoChamado(it)) }
            if (pendentes.isNotEmpty()) Log.i(TAG, "Sync: ${pendentes.size} pendente(s).")
        } catch (e: Exception) {
            Log.e(TAG, "Erro na sync: ${e.message}")
        }
    }

    // ── REST: buscar pendentes (polling + sync) ───────────────────────────

    private suspend fun buscarUrgenciasPendentesRest(
        profissionalId: String,
        especialidade: String?,
    ): List<Urgencia> {
        return try {
            val token  = currentToken ?: API_KEY
            val agoMs  = System.currentTimeMillis() - 5 * 60 * 1_000L
            val agoIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date(agoMs))
            var url = "$REST_URL/rest/v1/urgencias?status=eq.pending&criado_em=gte.$agoIso" +
                    "&select=*&order=criado_em.desc&limit=20"
            especialidade?.let { url += "&especialidade=eq.$it" }
            httpClient.get(url) {
                header("apikey",        API_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
            }.bodyAsText()
                .let { jsonParser.decodeFromString<List<Urgencia>>(it) }
                .filter { u -> u.professionalId == null || u.professionalId == profissionalId }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar pendentes: ${e.message}")
            emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACEITAR VIA RPC — ponto de entrada único (tarefa 1)
    //
    // Dispara a RPC no escopo interno e atualiza _aceitacaoState em cada
    // etapa. A UI coleta aceitacaoState e reage sem precisar de callbacks.
    //
    // Uso no Composable:
    //   val state by UrgenciasRealtimeManager.aceitacaoState.collectAsState()
    //   Button(onClick = { UrgenciasRealtimeManager.aceitarViaRpc(urgencia.id) })
    // ═══════════════════════════════════════════════════════════════════════

    fun aceitarViaRpc(urgenciaId: String) {
        val ctx = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
        ctx.launch {
            _aceitacaoState.emit(AceitacaoState.Carregando)

            val resultado = executarRpcAceitarUrgencia(urgenciaId)

            _aceitacaoState.emit(AceitacaoState.Resultado(resultado))

            // Reseta para Idle após 3 s (tempo suficiente para a UI reagir)
            delay(3_000L)
            _aceitacaoState.emit(AceitacaoState.Idle)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RPC: executar accept_urgencia — tarefa 1 (implementação)
//
// Chamada atômica ao backend. O banco garante que somente 1 profissional
// terá `true` de retorno — todos os outros recebem `false`.
//
// Endpoint: POST /rest/v1/rpc/accept_urgencia
// Body:     { "p_urgencia_id": "<uuid>" }
// Retorno:  BOOLEAN — `true` ou `false` como texto puro, ou envolvido em JSON
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
private data class AcceptUrgenciaRequest(
    val p_urgencia_id: String,
)

private suspend fun executarRpcAceitarUrgencia(urgenciaId: String): ResultadoAceitacao {
    return try {
        val token    = currentToken ?: API_KEY
        val response = httpClient.post("$REST_URL/rest/v1/rpc/accept_urgencia") {
            header("apikey",        API_KEY)
            header("Authorization", "Bearer $token")
            header("Content-Type",  "application/json")
            setBody(AcceptUrgenciaRequest(p_urgencia_id = urgenciaId))
        }

        val corpo = response.bodyAsText().trim()
        Log.d(TAG, "RPC accept_urgencia → HTTP ${response.status.value}, body: $corpo")

        if (response.status.value !in 200..299) {
            // HTTP de erro — provável problema de RLS, permissão ou função ausente
            AppLogger.erroRpc(
                urgenciaId     = urgenciaId,
                httpStatus     = response.status.value,
                corpoResposta  = corpo,
            )
            return ResultadoAceitacao.ErroRede("Servidor retornou ${response.status.value}: $corpo")
        }

        // Interpretar o Boolean retornado pela RPC
        interpretarBooleanRpc(corpo)

    } catch (e: CancellationException) {
        throw e   // nunca engolir — deixa a coroutine cancelar normalmente
    } catch (e: java.net.UnknownHostException) {
        AppLogger.erroRede("/rest/v1/rpc/accept_urgencia", e, "urgencia_id=$urgenciaId")
        ResultadoAceitacao.ErroRede("Sem conexão com a internet")
    } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
        AppLogger.erroRede("/rest/v1/rpc/accept_urgencia", e, "timeout urgencia_id=$urgenciaId")
        ResultadoAceitacao.ErroRede("Tempo de resposta esgotado")
    } catch (e: Exception) {
        AppLogger.erroRpc(
            urgenciaId    = urgenciaId,
            httpStatus    = 0,
            corpoResposta = e.message ?: "exception sem mensagem",
            throwable     = e,
        )
        ResultadoAceitacao.ErroRede(e.message ?: "Erro desconhecido")
    }
}

/**
 * Supabase pode retornar o Boolean de duas formas:
 *   • Texto puro:  `true`  /  `false`
 *   • JSON object: `{"accept_urgencia": true}`
 *
 * Esta função normaliza os dois casos para ResultadoAceitacao.
 */
private fun interpretarBooleanRpc(corpo: String): ResultadoAceitacao {
    // Caso 1: texto puro
    if (corpo.equals("true", ignoreCase = true))  return ResultadoAceitacao.Sucesso
    if (corpo.equals("false", ignoreCase = true)) return ResultadoAceitacao.JaAtendida

    // Caso 2: JSON envolvido
    return try {
        val el = jsonParser.parseToJsonElement(corpo)
        val valor: Boolean? = when {
            el is JsonPrimitive -> el.booleanOrNull
            el is JsonObject    -> el.values.firstOrNull()?.jsonPrimitive?.booleanOrNull
            else                -> null
        }
        when (valor) {
            true  -> ResultadoAceitacao.Sucesso
            false -> ResultadoAceitacao.JaAtendida
            null  -> {
                Log.e(TAG, "Resposta não interpretável da RPC: $corpo")
                ResultadoAceitacao.ErroRede("Resposta inesperada: $corpo")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao parsear resposta da RPC: $corpo — ${e.message}")
        ResultadoAceitacao.ErroRede("Parse error: $corpo")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RECUSAR — sem alteração no banco (backend expira via cron)
// ═══════════════════════════════════════════════════════════════════════════

suspend fun recusarUrgencia(urgenciaId: String): Boolean {
    Log.i(TAG, "Urgência recusada (sem ação no banco): $urgenciaId")
    return true
}