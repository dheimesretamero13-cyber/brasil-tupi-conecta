package br.com.brasiltupi.conecta

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.firebase.crashlytics.FirebaseCrashlytics

private const val TAG = "UrgenciasRealtime"

private val SUPABASE_URL get() = BuildConfig.SUPABASE_URL
private val WS_URL = "$SUPABASE_URL/realtime/v1/websocket"
private val REST_URL = SUPABASE_URL
private val ANON_KEY get() = BuildConfig.SUPABASE_KEY

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

private var wsClient: HttpClient? = null

enum class StatusRealtime { CONECTANDO, CONECTADO, INSTAVEL, OFFLINE }

@Serializable
data class Urgencia(
    val id: String,
    @SerialName("cliente_id") val clienteId: String? = null,
    @SerialName("professional_id") val professionalId: String? = null,
    val status: String = "pending",
    val especialidade: String? = null,
    @SerialName("nome_cliente") val nomeCliente: String? = null,
    @SerialName("criado_em") val criadoEm: String? = null,
)

sealed class EventoUrgencia {
    data class NovoChamado(val urgencia: Urgencia) : EventoUrgencia()
    data class ChamadoAceito(val urgenciaId: String) : EventoUrgencia()
    data class ChamadoEncerrado(val urgenciaId: String, val motivo: String) : EventoUrgencia()
    data class ChamadaIniciada(val urgenciaId: String) : EventoUrgencia()
}

sealed class ResultadoAceitacao {
    object Sucesso : ResultadoAceitacao()
    object JaAtendida : ResultadoAceitacao()
    data class ErroRede(val mensagem: String) : ResultadoAceitacao()
}

sealed class AceitacaoState {
    object Idle : AceitacaoState()
    object Carregando : AceitacaoState()
    data class Resultado(val resultado: ResultadoAceitacao) : AceitacaoState()
}

object UrgenciasRealtimeManager {

    private val _status = MutableStateFlow(StatusRealtime.CONECTANDO)
    val status: StateFlow<StatusRealtime> = _status.asStateFlow()

    private val _eventos = MutableSharedFlow<EventoUrgencia>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val eventos: SharedFlow<EventoUrgencia> = _eventos.asSharedFlow()

    private val _aceitacaoState = MutableStateFlow<AceitacaoState>(AceitacaoState.Idle)
    val aceitacaoState: StateFlow<AceitacaoState> = _aceitacaoState.asStateFlow()

    var onEvento: ((EventoUrgencia) -> Unit)? = null

    private var realtimeJob: Job? = null
    private var pollingJob: Job? = null
    private var scope: CoroutineScope? = null
    private var contagemReconexoes = 0
    private var ultimoTimestampConexaoEstavel: Long? = null
    private var ultimaQuedaTimestamp: Long? = null

    fun iniciar(profissionalId: String, especialidade: String? = null) {
        val token = AuthRepository.token
        if (token.isNullOrEmpty()) {
            AppLogger.aviso(TAG, "iniciar() abortado: nenhum token de usuário")
            _status.value = StatusRealtime.OFFLINE
            return
        }
        AppLogger.info(TAG, "Realtime iniciado para prof=$profissionalId")
        val ctx = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = ctx
        conectarComRetry(ctx, profissionalId, especialidade)
    }

    fun parar() {
        wsClient?.close()
        wsClient = null
        realtimeJob?.cancel()
        pollingJob?.cancel()
        scope?.cancel()
        _status.tryEmit(StatusRealtime.OFFLINE)
        _aceitacaoState.tryEmit(AceitacaoState.Idle)
    }

    private fun conectarComRetry(ctx: CoroutineScope, profissionalId: String, especialidade: String?) {
        realtimeJob?.cancel()
        realtimeJob = ctx.launch {
            wsClient = createWebSocketClient()   // ← NOVO
            val delays = listOf(2_000L, 5_000L, 10_000L, 30_000L)
            var tentativa = 0
            try {
                while (isActive) {
                    _status.emit(StatusRealtime.CONECTANDO)
                    try {
                        conectarWebSocket(wsClient!!, profissionalId, especialidade)
                        tentativa = 0

                        ultimaQuedaTimestamp?.let { quedaMs ->
                            val tempoAteRestabelecer = System.currentTimeMillis() - quedaMs
                            AppLogger.chave("realtime_tempo_recuperacao_ms", tempoAteRestabelecer.toString())
                            FirebaseCrashlytics.getInstance().log("Realtime restabelecido após ${tempoAteRestabelecer}ms")
                            ultimaQuedaTimestamp = null
                        }

                    if (contagemReconexoes > 0) {
                        AppLogger.chave("realtime_total_reconexoes", contagemReconexoes)
                        contagemReconexoes = 0
                    }
                }
                    catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                    AppLogger.aviso(TAG, "Conexão falhou tentativa ${tentativa + 1}: ${e.message}")
                    if (tentativa >= 2) {
                        AppLogger.erroRealtime(
                            fase = "conexao_tentativa_${tentativa + 1}",
                            profissionalId = profissionalId,
                            throwable = e,
                        )
                    }
                    }
                    contagemReconexoes++
                    AppLogger.chave("realtime_reconexoes", contagemReconexoes)
                    if (ultimaQuedaTimestamp == null) {
                        ultimaQuedaTimestamp = System.currentTimeMillis()
                        AppLogger.chave("realtime_queda_ms", ultimaQuedaTimestamp!!.toString())
                    }
                    _status.emit(StatusRealtime.INSTAVEL)
                    ativarPollingFallback(profissionalId, especialidade)
                    val delayMs = delays.getOrElse(tentativa) { 30_000L }
                    delay(delayMs)
                    tentativa = (tentativa + 1).coerceAtMost(delays.lastIndex)
                }
            } finally {
                wsClient?.close()
                wsClient = null
            }
        }
    }

    private suspend fun conectarWebSocket(
        client: HttpClient,
        profissionalId: String,
        especialidade: String?,
    ) {
        val token = AuthRepository.token ?: throw Exception("Token não disponível")
        client.webSocket(
            urlString = "$WS_URL?apikey=$ANON_KEY&vsn=1.0.0",
            request = { header("Authorization", "Bearer $token") },
        ) {
            AppLogger.info(TAG, "WebSocket conectado.")
            _status.emit(StatusRealtime.CONECTADO)
            pollingJob?.cancel()

            send(Frame.Text(buildJoinMessage()))
            sincronizarPendentes(profissionalId, especialidade)

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    processarFrame(frame.readText(), profissionalId, especialidade)
                }
            }
            AppLogger.aviso(TAG, "WebSocket encerrado pelo servidor.")
        }
    }

    private fun buildJoinMessage(): String {
        // Sintaxe correta para nested JsonObjects
        val payload = buildJsonObject {
            put("config", buildJsonObject {
                put("broadcast", buildJsonObject { put("self", false) })
                put("presence", buildJsonObject { put("key", "") })
                put("postgres_changes", buildJsonArray {
                    add(buildJsonObject {
                        put("event", "INSERT")
                        put("schema", "public")
                        put("table", "urgencias")
                    })
                    add(buildJsonObject {
                        put("event", "UPDATE")
                        put("schema", "public")
                        put("table", "urgencias")
                    })
                })
            })
        }
        return buildJsonObject {
            put("event", "phx_join")
            put("topic", "realtime:urgencias")
            put("payload", payload)
            put("ref", "1")
        }.toString()
    }

    private suspend fun processarFrame(
        texto: String,
        profissionalId: String,
        especialidade: String?,
    ) {
        try {
            val objeto = jsonParser.parseToJsonElement(texto).jsonObject
            val event = objeto["event"]?.jsonPrimitive?.content ?: return
            val payload = objeto["payload"]?.jsonObject ?: return
            when (event) {
                "phx_reply" -> {
                    val status = payload["status"]?.toString() ?: "desconhecido"
                    AppLogger.info(TAG, "Canal confirmado: $status")
                }
                "postgres_changes" -> {
                    val data = payload["data"]?.jsonObject ?: return
                    val tipoEvento = data["type"]?.jsonPrimitive?.content ?: return
                    val record = data["record"]?.jsonObject ?: return
                    val urgencia = jsonParser.decodeFromJsonElement<Urgencia>(record)
                    when (tipoEvento) {
                        "INSERT" -> tratarInsert(urgencia, profissionalId, especialidade)
                        "UPDATE" -> tratarUpdate(urgencia, profissionalId)
                    }
                }
                "phx_error", "phx_close" -> {
                    AppLogger.aviso(TAG, "Canal encerrado: $event")
                    throw Exception("Canal Phoenix encerrado: $event")
                }
            }
        } catch (e: Exception) {
            AppLogger.erroRealtime(
                fase = "frame",
                profissionalId = profissionalId,
                throwable = e,
            )
            if (e.message?.contains("Canal Phoenix") == true) throw e
        }
    }

    private suspend fun tratarInsert(
        urgencia: Urgencia,
        profissionalId: String,
        especialidade: String?,
    ) {
        val eParaMim = urgencia.professionalId == null || urgencia.professionalId == profissionalId
        val especialidadeOk = especialidade == null ||
                urgencia.especialidade?.equals(especialidade, ignoreCase = true) == true
        if (eParaMim && especialidadeOk && urgencia.status == "pending") {
            AppLogger.info(TAG, "Novo chamado: ${urgencia.id}")
            emitirEvento(EventoUrgencia.NovoChamado(urgencia))
        }
    }

    private suspend fun tratarUpdate(urgencia: Urgencia, profissionalId: String) {
        when (urgencia.status) {
            "accepted" -> if (urgencia.professionalId != profissionalId)
                emitirEvento(EventoUrgencia.ChamadoAceito(urgencia.id))
            "cancelled" -> emitirEvento(EventoUrgencia.ChamadoEncerrado(urgencia.id, "cancelled"))
            "expired" -> emitirEvento(EventoUrgencia.ChamadoEncerrado(urgencia.id, "expired"))
            "in_progress" -> emitirEvento(EventoUrgencia.ChamadaIniciada(urgencia.id))
            "finished" -> emitirEvento(EventoUrgencia.ChamadoEncerrado(urgencia.id, "finished"))
        }
    }

    private suspend fun emitirEvento(evento: EventoUrgencia) {
        _eventos.emit(evento)
        withContext(Dispatchers.Main) { onEvento?.invoke(evento) }
    }

    private fun ativarPollingFallback(profissionalId: String, especialidade: String?) {
        pollingJob?.cancel()
        pollingJob = scope?.launch(Dispatchers.IO) {
            AppLogger.info(TAG, "Polling fallback ativado (3s).")
            AppLogger.chave("realtime_polling_fallback", true)
            FirebaseCrashlytics.getInstance().log("Polling fallback ativado para prof=$profissionalId")
            val vistasNoFallback = mutableSetOf<String>()
            while (isActive) {
                delay(3_000L)
                try {
                    buscarUrgenciasPendentesRest(profissionalId, especialidade)
                        .filter { vistasNoFallback.add(it.id) }
                        .forEach { emitirEvento(EventoUrgencia.NovoChamado(it)) }
                } catch (e: Exception) {
                    AppLogger.erroRealtime(
                        fase = "polling",
                        profissionalId = profissionalId,
                        throwable = e,
                    )
                    _status.emit(StatusRealtime.OFFLINE)
                }
            }
        }
    }

    private suspend fun sincronizarPendentes(profissionalId: String, especialidade: String?) {
        try {
            val pendentes = buscarUrgenciasPendentesRest(profissionalId, especialidade)
            pendentes.forEach { emitirEvento(EventoUrgencia.NovoChamado(it)) }
            if (pendentes.isNotEmpty()) AppLogger.info(TAG, "Sync: ${pendentes.size} pendente(s).")
        } catch (e: Exception) {
            AppLogger.aviso(TAG, "Erro na sync: ${e.message}")
        }
    }

    private suspend fun buscarUrgenciasPendentesRest(
        profissionalId: String,
        especialidade: String?,
    ): List<Urgencia> {
        val token = AuthRepository.token ?: return emptyList()
        val agoMs = System.currentTimeMillis() - 5 * 60 * 1000L
        val agoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(agoMs))
        var url = "$REST_URL/rest/v1/urgencias?status=eq.pending&criado_em=gte.$agoIso" +
                "&select=*&order=criado_em.desc&limit=20"
        especialidade?.let { url += "&especialidade=eq.$it" }
        return try {
            val response = httpClient.get(url) {
                header("apikey", ANON_KEY)
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
            }.bodyAsText() ?: "[]"  // garante String não nula
            jsonParser.decodeFromString<List<Urgencia>>(response)
                .filter { u -> u.professionalId == null || u.professionalId == profissionalId }
        } catch (e: Exception) {
            AppLogger.erroRealtime(
                fase = "polling_busca",
                profissionalId = profissionalId,
                throwable = e,
            )
            emptyList()
        }
    }

    fun aceitarViaRpc(urgenciaId: String) {
        val ctx = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
        ctx.launch {
            _aceitacaoState.emit(AceitacaoState.Carregando)
            val resultado = executarRpcAceitarUrgencia(urgenciaId)
            _aceitacaoState.emit(AceitacaoState.Resultado(resultado))
            delay(3_000L)
            _aceitacaoState.emit(AceitacaoState.Idle)
        }
    }
}

@Serializable
private data class AcceptUrgenciaRequest(val p_urgencia_id: String)

private suspend fun executarRpcAceitarUrgencia(urgenciaId: String): ResultadoAceitacao {
    val token = AuthRepository.token ?: return ResultadoAceitacao.ErroRede("Usuário não autenticado")
    return try {
        val response = httpClient.post("$REST_URL/rest/v1/rpc/accept_urgencia") {
            header("apikey", ANON_KEY)
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody(AcceptUrgenciaRequest(p_urgencia_id = urgenciaId))
        }
        val corpo = response.bodyAsText().trim()
        if (response.status.value !in 200..299) {
            AppLogger.erroRpc(urgenciaId, response.status.value, corpo ?: "")
            return ResultadoAceitacao.ErroRede("HTTP ${response.status.value}")
        }
        interpretarBooleanRpc(corpo ?: "")
    } catch (e: java.net.UnknownHostException) {
        ResultadoAceitacao.ErroRede("Sem conexão com a internet")
    } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
        ResultadoAceitacao.ErroRede("Tempo de resposta esgotado")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.erroRpc(urgenciaId, 0, e.message ?: "", e)
        ResultadoAceitacao.ErroRede(e.message ?: "Erro desconhecido")
    }
}

private fun interpretarBooleanRpc(corpo: String): ResultadoAceitacao {
    if (corpo.equals("true", ignoreCase = true)) return ResultadoAceitacao.Sucesso
    if (corpo.equals("false", ignoreCase = true)) return ResultadoAceitacao.JaAtendida
    return try {
        val el = jsonParser.parseToJsonElement(corpo)
        val valor = when (el) {
            is JsonPrimitive -> el.booleanOrNull
            is JsonObject -> el.values.firstOrNull()?.jsonPrimitive?.booleanOrNull
            else -> null
        }
        when (valor) {
            true -> ResultadoAceitacao.Sucesso
            false -> ResultadoAceitacao.JaAtendida
            else -> ResultadoAceitacao.ErroRede("Resposta inesperada")
        }
    } catch (e: Exception) {
        ResultadoAceitacao.ErroRede("Erro de parse")
    }
}

suspend fun recusarUrgencia(urgenciaId: String): Boolean {
    AppLogger.info(TAG, "Urgência recusada (sem ação no banco): $urgenciaId")
    return true
}