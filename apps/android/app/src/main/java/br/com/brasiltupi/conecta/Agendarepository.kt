package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AgendaRepository.kt  · Fase 2.3
//
// Responsabilidades:
//  1. PROFISSIONAL: buscar próprios slots, criar slot via RPC com validação
//     de sobreposição, deletar slot vazio, cancelar agendamento
//  2. CLIENTE: buscar slots disponíveis de um profissional específico,
//     reservar slot via RPC atômica (UPDATE + INSERT em transação)
//  3. REALTIME: assinar canal `availability` para atualizar agenda
//     instantaneamente quando slots são reservados ou cancelados
//  4. Instrumentar via AppLogger
//
// PADRÃO DE SEGURANÇA:
//  • RPCs validam ownership e sobreposição no Postgres — não no cliente
//  • Double-booking impossível: RPC usa FOR UPDATE SKIP LOCKED
//  • RLS do Supabase garante que cliente só vê slots is_booked = false
// ═══════════════════════════════════════════════════════════════════════════

import android.util.Log
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

private const val TAG          = "AgendaRepository"
private const val SUPABASE_URL = "https://qfzdchrlbqcvewjivaqz.supabase.co"
private const val API_KEY      = "sb_publishable_SM-UHBh_5lzTSBZ2YPUIYw_Sw1i8qeq"
private const val WS_URL       = "wss://qfzdchrlbqcvewjivaqz.supabase.co/realtime/v1/websocket"

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════════════════
// REPOSITÓRIO
// ═══════════════════════════════════════════════════════════════════════════

object AgendaRepository {

    // ── StateFlows expostos para a UI ─────────────────────────────────────
    private val _profState    = MutableStateFlow<AgendaProfState>(AgendaProfState.Idle)
    private val _clienteState = MutableStateFlow<AgendaClienteState>(AgendaClienteState.Idle)
    private val _criarSlot    = MutableStateFlow<AgendaProfState.ResultadoCriarSlot?>(null)
    private val _reserva      = MutableStateFlow<AgendaClienteState.ResultadoReserva?>(null)

    val profState:    StateFlow<AgendaProfState>    = _profState.asStateFlow()
    val clienteState: StateFlow<AgendaClienteState> = _clienteState.asStateFlow()
    val resultadoCriarSlot: StateFlow<AgendaProfState.ResultadoCriarSlot?> = _criarSlot.asStateFlow()
    val resultadoReserva:   StateFlow<AgendaClienteState.ResultadoReserva?> = _reserva.asStateFlow()

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wsJob:     Job? = null
    private var pollingJob: Job? = null

    // ═══════════════════════════════════════════════════════════════════════
    // FLUXO DO PROFISSIONAL
    // ═══════════════════════════════════════════════════════════════════════

    // ── Carregar próprios slots + agendamentos ─────────────────────────────

    fun carregarAgendaProfissional() {
        scope.launch {
            val uid = currentUserId ?: return@launch
            _profState.emit(AgendaProfState.Carregando)
            AppLogger.info(TAG, "Carregando agenda do profissional=$uid")

            try {
                val token = currentToken ?: API_KEY

                // Buscar slots do profissional (todos, incluindo reservados)
                val slotsResp = httpClient.get(
                    "$SUPABASE_URL/rest/v1/availability" +
                            "?professional_id=eq.$uid" +
                            "&select=*" +
                            "&order=start_time.asc" +
                            "&start_time=gte.${agoraIsoUtc()}"   // apenas futuros
                ) {
                    header("apikey",        API_KEY)
                    header("Authorization", "Bearer $token")
                    header("Accept",        "application/json")
                }

                val slots = jsonParser.decodeFromString<List<SlotDisponibilidade>>(
                    slotsResp.bodyAsText()
                )

                // Buscar agendamentos confirmados
                val agendResp = httpClient.get(
                    "$SUPABASE_URL/rest/v1/agendamentos" +
                            "?professional_id=eq.$uid" +
                            "&status=eq.confirmed" +
                            "&select=*,availability(*)" +
                            "&order=criado_em.desc&limit=30"
                ) {
                    header("apikey",        API_KEY)
                    header("Authorization", "Bearer $token")
                    header("Accept",        "application/json")
                }

                val agendamentos = try {
                    jsonParser.decodeFromString<List<Agendamento>>(agendResp.bodyAsText())
                } catch (_: Exception) { emptyList() }

                val slotsPorDia = slots.groupBy { it.chaveData }

                AppLogger.info(TAG,
                    "Agenda carregada: ${slots.size} slots, ${agendamentos.size} agendamentos"
                )

                _profState.emit(AgendaProfState.Carregado(
                    slots        = slots,
                    slotsPorDia  = slotsPorDia,
                    agendamentos = agendamentos,
                ))

                // Iniciar Realtime após carregar
                iniciarRealtimeProfissional(uid)

            } catch (e: Exception) {
                AppLogger.erroRede("availability (prof)", e, "uid=$uid")
                _profState.emit(AgendaProfState.Erro("Falha ao carregar agenda. Verifique sua conexão."))
            }
        }
    }

    // ── Criar novo slot via RPC ────────────────────────────────────────────

    /**
     * Chama RPC `validar_e_criar_slot` que:
     *  1. Trava linhas sobrepostas com FOR UPDATE SKIP LOCKED
     *  2. Verifica sobreposição
     *  3. Insere o slot se livre
     * Retorna: 'ok' | 'sobreposicao' | 'erro'
     *
     * @param startIso   "yyyy-MM-ddTHH:mm:ss+00:00" — UTC obrigatório
     * @param endIso     idem
     */
    fun criarSlot(startIso: String, endIso: String) {
        scope.launch {
            val uid = currentUserId ?: return@launch
            _criarSlot.emit(AgendaProfState.ResultadoCriarSlot.Criando)

            AppLogger.info(TAG, "Criando slot: $startIso → $endIso para prof=$uid")

            try {
                val token    = currentToken ?: API_KEY
                val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/validar_e_criar_slot") {
                    header("apikey",        API_KEY)
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(ValidarCriarSlotRequest(
                        profId = uid,
                        start  = startIso,
                        end    = endIso,
                    ))
                }

                val corpo = response.bodyAsText().trim().removeSurrounding("\"")
                Log.d(TAG, "RPC validar_e_criar_slot → $corpo")

                val resultado = when (corpo) {
                    "ok" -> {
                        AppLogger.info(TAG, "Slot criado: $startIso")
                        carregarAgendaProfissional()   // recarregar lista
                        AgendaProfState.ResultadoCriarSlot.Sucesso
                    }
                    "sobreposicao" -> {
                        AppLogger.aviso(TAG, "Sobreposicao detectada: $startIso → $endIso")
                        AgendaProfState.ResultadoCriarSlot.Sobreposicao
                    }
                    else -> {
                        AppLogger.erroRpc(
                            urgenciaId    = uid,
                            httpStatus    = response.status.value,
                            corpoResposta = corpo,
                        )
                        AgendaProfState.ResultadoCriarSlot.Erro(
                            "Erro ao criar horário. Tente novamente."
                        )
                    }
                }
                _criarSlot.emit(resultado)

            } catch (e: java.net.UnknownHostException) {
                AppLogger.erroRede("rpc/validar_e_criar_slot", e, "sem_rede")
                _criarSlot.emit(AgendaProfState.ResultadoCriarSlot.Erro("Sem conexão."))
            } catch (e: Exception) {
                AppLogger.erroRede("rpc/validar_e_criar_slot", e)
                _criarSlot.emit(AgendaProfState.ResultadoCriarSlot.Erro(
                    "Falha inesperada ao criar horário."
                ))
            }
        }
    }

    // ── Deletar slot vazio (não reservado) ────────────────────────────────

    fun deletarSlot(slotId: String) {
        scope.launch {
            val uid   = currentUserId ?: return@launch
            val token = currentToken  ?: API_KEY
            try {
                // RLS garante que só o dono pode deletar
                // Filtro is_booked=eq.false evita deletar slots com agendamento
                httpClient.delete(
                    "$SUPABASE_URL/rest/v1/availability" +
                            "?id=eq.$slotId&is_booked=eq.false"
                ) {
                    header("apikey",        API_KEY)
                    header("Authorization", "Bearer $token")
                }
                AppLogger.info(TAG, "Slot $slotId deletado por prof=$uid")
                carregarAgendaProfissional()
            } catch (e: Exception) {
                AppLogger.erroRede("DELETE /availability", e, "slot=$slotId")
            }
        }
    }

    // ── Cancelar agendamento ──────────────────────────────────────────────

    fun cancelarAgendamento(agendamentoId: String) {
        scope.launch {
            val uid   = currentUserId ?: return@launch
            val token = currentToken  ?: API_KEY
            try {
                val response = httpClient.post(
                    "$SUPABASE_URL/rest/v1/rpc/cancelar_agendamento"
                ) {
                    header("apikey",        API_KEY)
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    // RPC usa auth.uid() internamente — sem cancelador_id
                    setBody(kotlinx.serialization.json.buildJsonObject {
                        put("p_agendamento_id", agendamentoId)
                    })
                }
                val corpo = response.bodyAsText().trim().removeSurrounding("\"")
                AppLogger.info(TAG, "cancelar_agendamento → $corpo id=$agendamentoId")
                carregarAgendaProfissional()
            } catch (e: Exception) {
                AppLogger.erroRede("rpc/cancelar_agendamento", e, "agend=$agendamentoId")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FLUXO DO CLIENTE
    // ═══════════════════════════════════════════════════════════════════════

    // ── Carregar slots disponíveis de um profissional ─────────────────────

    fun carregarSlotsDisponiveis(professionalId: String) {
        scope.launch {
            _clienteState.emit(AgendaClienteState.Carregando)
            val token = currentToken ?: API_KEY

            try {
                // RLS filtra is_booked = false automaticamente para o cliente
                val response = httpClient.get(
                    "$SUPABASE_URL/rest/v1/availability" +
                            "?professional_id=eq.$professionalId" +
                            "&is_booked=eq.false" +
                            "&select=*" +
                            "&order=start_time.asc" +
                            "&start_time=gte.${agoraIsoUtc()}" +
                            "&limit=60"
                ) {
                    header("apikey",        API_KEY)
                    header("Authorization", "Bearer $token")
                    header("Accept",        "application/json")
                }

                val slots = jsonParser.decodeFromString<List<SlotDisponibilidade>>(
                    response.bodyAsText()
                )
                val slotsPorDia = slots.groupBy { it.chaveData }

                AppLogger.info(TAG, "${slots.size} slots disponíveis para prof=$professionalId")

                _clienteState.emit(AgendaClienteState.Carregado(
                    slots       = slots,
                    slotsPorDia = slotsPorDia,
                ))

                iniciarRealtimeCliente(professionalId)

            } catch (e: Exception) {
                AppLogger.erroRede("availability (cliente)", e, "prof=$professionalId")
                _clienteState.emit(AgendaClienteState.Erro(
                    "Falha ao carregar horários disponíveis."
                ))
            }
        }
    }

    // ── Reservar slot via RPC atômica ─────────────────────────────────────

    /**
     * Chama RPC `reservar_slot` que executa atomicamente:
     *   UPDATE availability SET is_booked = true WHERE id = p_slot_id
     *   INSERT INTO agendamentos (...)
     * Retorna: 'ok' | 'ja_reservado' | 'nao_encontrado' | 'erro'
     */
    fun reservarSlot(slotId: String, observacao: String? = null) {
        scope.launch {
            val uid = currentUserId ?: return@launch
            _reserva.emit(AgendaClienteState.ResultadoReserva.Reservando)

            AppLogger.info(TAG, "Reservando slot=$slotId para client=$uid")

            try {
                val token    = currentToken ?: API_KEY
                val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/reservar_slot") {
                    header("apikey",        API_KEY)
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    // RPC usa auth.uid() internamente — nunca passamos clientId
                    setBody(kotlinx.serialization.json.buildJsonObject {
                        put("p_slot_id",    slotId)
                        if (observacao != null) put("p_observacao", observacao)
                    })
                }

                val corpo = response.bodyAsText().trim().removeSurrounding("\"")
                Log.d(TAG, "RPC reservar_slot → $corpo")

                val resultado = when (corpo) {
                    "ok" -> {
                        AppLogger.info(TAG, "Slot $slotId reservado com sucesso")
                        AgendaClienteState.ResultadoReserva.Sucesso
                    }
                    "ja_reservado" -> {
                        AppLogger.aviso(TAG, "Slot $slotId ja foi reservado por outro cliente")
                        AgendaClienteState.ResultadoReserva.JaReservado
                    }
                    else -> {
                        AppLogger.erroRpc(
                            urgenciaId    = slotId,
                            httpStatus    = response.status.value,
                            corpoResposta = corpo,
                        )
                        AgendaClienteState.ResultadoReserva.Erro(
                            "Falha ao confirmar agendamento. Tente novamente."
                        )
                    }
                }
                _reserva.emit(resultado)

            } catch (e: java.net.UnknownHostException) {
                AppLogger.erroRede("rpc/reservar_slot", e, "sem_rede slot=$slotId")
                _reserva.emit(AgendaClienteState.ResultadoReserva.Erro("Sem conexão."))
            } catch (e: Exception) {
                AppLogger.erroRede("rpc/reservar_slot", e, "slot=$slotId")
                _reserva.emit(AgendaClienteState.ResultadoReserva.Erro(
                    "Falha inesperada ao agendar."
                ))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REALTIME — assinar canal availability
    // ═══════════════════════════════════════════════════════════════════════

    private fun iniciarRealtimeProfissional(profId: String) {
        wsJob?.cancel()
        wsJob = scope.launch {
            escutarAvailability(
                filtro        = "professional_id=eq.$profId",
                topico        = "realtime:availability:prof=$profId",
                aoAtualizar   = { carregarAgendaProfissional() },
            )
        }
    }

    private fun iniciarRealtimeCliente(profId: String) {
        wsJob?.cancel()
        wsJob = scope.launch {
            escutarAvailability(
                filtro        = "professional_id=eq.$profId",
                topico        = "realtime:availability:cliente=$profId",
                aoAtualizar   = { carregarSlotsDisponiveis(profId) },
            )
        }
    }

    /**
     * WebSocket Phoenix — mesmo protocolo do UrgenciasRealtime.
     * Assina UPDATE na tabela availability e chama aoAtualizar() quando detecta mudança.
     */
    private suspend fun escutarAvailability(
        filtro:      String,
        topico:      String,
        aoAtualizar: suspend () -> Unit,
    ) {
        val wsClient = io.ktor.client.HttpClient(Android) {
            install(WebSockets) { pingInterval = 25_000L }
        }

        val delays    = listOf(2_000L, 5_000L, 10_000L, 30_000L)
        var tentativa = 0

        while (currentCoroutineContext().isActive) {
            try {
                val token = currentToken ?: API_KEY
                wsClient.webSocket(
                    urlString = "$WS_URL?apikey=$API_KEY&vsn=1.0.0",
                    request   = { header("Authorization", "Bearer $token") },
                ) {
                    tentativa = 0
                    send(Frame.Text(buildJoinAvailability(topico, filtro)))
                    AppLogger.info(TAG, "Realtime availability conectado: $topico")

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val texto  = frame.readText()
                        val obj    = try { jsonParser.parseToJsonElement(texto).jsonObject }
                        catch (_: Exception) { continue }
                        val event  = obj["event"]?.jsonPrimitive?.content ?: continue

                        if (event == "postgres_changes") {
                            AppLogger.info(TAG, "Realtime: mudança em availability")
                            aoAtualizar()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (tentativa >= 2) {
                    AppLogger.erroRealtime(
                        fase           = "availability_ws_tentativa_${tentativa + 1}",
                        profissionalId = filtro,
                        throwable      = e,
                    )
                } else {
                    AppLogger.aviso(TAG, "Realtime availability reconectando: ${e.message}")
                }
                val delayMs = delays.getOrElse(tentativa) { 30_000L }
                delay(delayMs)
                tentativa = (tentativa + 1).coerceAtMost(delays.lastIndex)
            }
        }
        wsClient.close()
    }

    private fun buildJoinAvailability(topico: String, filtro: String): String =
        buildJsonObject {
            put("event", "phx_join")
            put("topic", topico)
            putJsonObject("payload") {
                putJsonObject("config") {
                    putJsonObject("broadcast") { put("self", false) }
                    putJsonArray("postgres_changes") {
                        addJsonObject {
                            put("event",  "UPDATE")
                            put("schema", "public")
                            put("table",  "availability")
                            put("filter", filtro)
                        }
                        addJsonObject {
                            put("event",  "INSERT")
                            put("schema", "public")
                            put("table",  "availability")
                            put("filter", filtro)
                        }
                    }
                }
            }
            put("ref", "agenda_1")
        }.toString()

    // ── Encerrar Realtime ─────────────────────────────────────────────────

    fun parar() {
        wsJob?.cancel()
        pollingJob?.cancel()
        scope.launch {
            _profState.emit(AgendaProfState.Idle)
            _clienteState.emit(AgendaClienteState.Idle)
        }
    }

    fun resetarResultados() {
        scope.launch {
            _criarSlot.emit(null)
            _reserva.emit(null)
        }
    }
}

// ── Helper: timestamp atual em ISO UTC sem java.time ─────────────────────
private fun agoraIsoUtc(): String {
    val agora = System.currentTimeMillis()
    val sdf   = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(agora))
}