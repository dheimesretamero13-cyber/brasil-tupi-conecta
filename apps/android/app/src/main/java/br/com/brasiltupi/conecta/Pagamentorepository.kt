package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// PagamentoRepository.kt  · Fase 2.2
//
// Responsabilidades:
//  1. Solicitar preferência de pagamento à Edge Function `criar-preferencia-pagamento`
//     usando urgencia_id como idempotency_key (Regra de Ouro nº 3)
//  2. Expor PagamentoState via StateFlow para a UI orquestrar o WebView
//  3. Monitorar a tabela `payments` via Realtime WebSocket para confirmar
//     o pagamento sem depender da URL de retorno (que pode não chegar)
//  4. Fallback: polling da tabela `payments` quando Realtime falhar
//  5. Instrumentar cada etapa via AppLogger
//
// PRINCÍPIOS DE SEGURANÇA (Regras de Ouro 1 e 3):
//  • O app NUNCA calcula valor, taxa ou split — isso é 100% do backend
//  • O app NUNCA gera o init_point — vem exclusivamente da Edge Function
//  • idempotency_key = urgencia_id garante que mesmo se a requisição for
//    repetida (rede instável, app fechado e reaberto), o Mercado Pago
//    retorna a mesma preferência sem criar cobrança dupla
//
// FONTE DA VERDADE:
//  A confirmação de pagamento NUNCA vem da URL de retorno do MP.
//  Ela vem do webhook backend → tabela `payments` → Realtime no app.
//  URL de retorno apenas traz o usuário de volta ao app.
// ═══════════════════════════════════════════════════════════════════════════

import android.util.Log
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

// WebSocket dinâmico a partir da URL base de REST
private val WS_URL = LOCAL_URL
    .replace("https://", "wss://")
    .replace("http://", "ws://") + "/realtime/v1/websocket"

// Edge Functions — URLs construídas dinamicamente
private val EDGE_URL = "$LOCAL_URL/functions/v1/criar-preferencia-pagamento"
private val EDGE_URL_REGULAR = "$LOCAL_URL/functions/v1/criar-preferencia-regular"

private const val TAG = "PagamentoRepository"

// Timeout para aguardar confirmação via Realtime antes de ativar polling
private const val REALTIME_CONFIRM_TIMEOUT_MS = 30_000L
private const val POLLING_INTERVAL_MS         = 5_000L

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════════════════
// OBJETO REPOSITÓRIO
// ═══════════════════════════════════════════════════════════════════════════

object PagamentoRepository {

    private val _state = MutableStateFlow<PagamentoState>(PagamentoState.Idle)

    /**
     * Colete este Flow no Composable:
     *   val state by PagamentoRepository.state.collectAsState()
     */
    val state: StateFlow<PagamentoState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Job do Realtime — cancelado ao confirmar ou encerrar
    private var realtimeJob: Job? = null
    private var pollingJob:  Job? = null

    // ── PASSO 1: CRIAR PREFERÊNCIA ────────────────────────────────────────

    /**
     * Solicita à Edge Function a criação da preferência de pagamento.
     * O urgencia_id é enviado como idempotency_key — o backend repassa
     * ao Mercado Pago, garantindo que requisições repetidas retornem
     * o mesmo init_point sem gerar nova cobrança.
     *
     * Chamada SOMENTE após avaliação confirmada (status = finished).
     */
    fun criarPreferencia(urgenciaId: String) {
        scope.launch {
            _state.emit(PagamentoState.CriandoPreferencia(urgenciaId))

            AppLogger.chave("pagamento_urgencia_id", urgenciaId)
            AppLogger.infoPagamento(
                etapa      = "criar_preferencia",
                urgenciaId = urgenciaId,
                detalhe    = "Solicitando init_point a Edge Function",
            )

            val token = currentToken ?: run {
                AppLogger.erroAuth("criar_preferencia_pagamento",
                    mensagemExtra = "token Supabase ausente")
                _state.emit(PagamentoState.Erro(
                    motivo = "Sessão inválida. Faça login novamente.",
                    tipo   = TipoErroPagamento.PREFERENCIA_NEGADA,
                ))
                return@launch
            }

            try {
                val response = httpClient.post(EDGE_URL) {
                    header("Authorization",   "Bearer $token")
                    header("apikey",          LOCAL_KEY)
                    // Idempotência: mesmo urgencia_id sempre retorna a mesma preferência
                    header("Idempotency-Key", urgenciaId)
                    contentType(ContentType.Application.Json)
                    setBody(CriarPreferenciaRequest(
                        urgenciaId     = urgenciaId,
                        idempotencyKey = urgenciaId,
                    ))
                }

                val corpo = response.bodyAsText()
                Log.d(TAG, "Edge Function HTTP ${response.status.value}")

                when (response.status.value) {
                    200 -> {
                        val preferencia = jsonParser.decodeFromString<PreferenciaResponse>(corpo)

                        AppLogger.infoPagamento(
                            etapa      = "preferencia_criada",
                            urgenciaId = urgenciaId,
                            detalhe    = "preference_id=${preferencia.preferenceId} valor=${preferencia.valor}",
                        )

                        _state.emit(PagamentoState.CheckoutAberto(
                            initPoint    = preferencia.initPoint,
                            preferenceId = preferencia.preferenceId,
                            descricao    = preferencia.descricao,
                            valor        = preferencia.valor,
                            urgenciaId   = urgenciaId,
                        ))
                    }
                    409 -> {
                        // Idempotência: preferência já existe — retornar a existente
                        AppLogger.aviso(TAG, "Preferencia ja existe (409) para urgencia=$urgenciaId")
                        val preferencia = try {
                            jsonParser.decodeFromString<PreferenciaResponse>(corpo)
                        } catch (_: Exception) { null }

                        if (preferencia != null) {
                            _state.emit(PagamentoState.CheckoutAberto(
                                initPoint    = preferencia.initPoint,
                                preferenceId = preferencia.preferenceId,
                                descricao    = preferencia.descricao,
                                valor        = preferencia.valor,
                                urgenciaId   = urgenciaId,
                            ))
                        } else {
                            // 409 sem corpo parseável — tratar como erro
                            _state.emit(PagamentoState.Erro(
                                motivo = "Pagamento em processamento. Tente novamente em instantes.",
                                tipo   = TipoErroPagamento.IDEMPOTENCIA,
                            ))
                        }
                    }
                    401, 403 -> {
                        AppLogger.erroPagamento(
                            etapa      = "preferencia_negada",
                            urgenciaId = urgenciaId,
                            httpStatus = response.status.value,
                            corpo      = corpo,
                        )
                        _state.emit(PagamentoState.Erro(
                            motivo = "Acesso negado. Verifique se a consulta foi finalizada.",
                            tipo   = TipoErroPagamento.PREFERENCIA_NEGADA,
                        ))
                    }
                    else -> {
                        AppLogger.erroPagamento(
                            etapa      = "preferencia_erro",
                            urgenciaId = urgenciaId,
                            httpStatus = response.status.value,
                            corpo      = corpo,
                        )
                        _state.emit(PagamentoState.Erro(
                            motivo = "Erro ao iniciar pagamento (${response.status.value}). Tente novamente.",
                            tipo   = TipoErroPagamento.REDE,
                        ))
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                AppLogger.erroRede(EDGE_URL, e, "criar_preferencia urgencia=$urgenciaId")
                _state.emit(PagamentoState.Erro(
                    motivo = "Sem conexão com a internet.",
                    tipo   = TipoErroPagamento.REDE,
                ))
            } catch (e: Exception) {
                AppLogger.erroRede(EDGE_URL, e, "criar_preferencia urgencia=$urgenciaId")
                _state.emit(PagamentoState.Erro(
                    motivo = "Falha inesperada ao criar preferência de pagamento.",
                    tipo   = TipoErroPagamento.DESCONHECIDO,
                ))
            }
        }
    }

    // ── PASSO 2: NOTIFICAR QUE O CHECKOUT FOI ABERTO ─────────────────────

    /**
     * Chamado pela PagamentoScreen quando o WebView/CustomTabs é aberto.
     * Inicia a escuta do Realtime para confirmar o pagamento.
     */
    fun notificarCheckoutAberto(urgenciaId: String) {
        AppLogger.infoPagamento(
            etapa      = "checkout_aberto",
            urgenciaId = urgenciaId,
            detalhe    = "WebView/CustomTabs aberto pelo usuario",
        )
        iniciarEscutaRealtime(urgenciaId)
    }

    // ── PASSO 3: TRATAR URL DE RETORNO ────────────────────────────────────

    /**
     * Chamado pela PagamentoScreen quando o WebView detecta redirect para
     * as URLs de retorno configuradas no backend do MP.
     *
     * A URL de retorno NÃO confirma o pagamento — apenas traz o usuário
     * de volta. A confirmação real vem do Realtime (webhook → payments).
     */
    fun notificarRetornoCheckout(url: String, urgenciaId: String) {
        scope.launch {
            val tipo = when {
                url.contains("success", ignoreCase = true) -> "success"
                url.contains("pending", ignoreCase = true) -> "pending"
                else                                       -> "failure"
            }

            AppLogger.infoPagamento(
                etapa      = "retorno_checkout",
                urgenciaId = urgenciaId,
                detalhe    = "tipo=$tipo url=${url.take(80)}",
            )

            when (tipo) {
                "success" -> {
                    // Usuário completou o fluxo — aguardar confirmação do Realtime
                    _state.emit(PagamentoState.Processando(urgenciaId))
                    // Iniciar polling de fallback com timeout
                    iniciarPollingConfirmacao(urgenciaId)
                }
                "pending" -> {
                    _state.emit(PagamentoState.Pendente(urgenciaId))
                    realtimeJob?.cancel()
                    pollingJob?.cancel()
                }
                else -> {
                    // failure ou fechou sem pagar
                    val estadoAtual = _state.value
                    val initPoint = (estadoAtual as? PagamentoState.CheckoutAberto)?.initPoint
                        ?: (estadoAtual as? PagamentoState.Processando)?.urgenciaId?.let { "" }
                        ?: ""
                    _state.emit(PagamentoState.Cancelado(
                        urgenciaId = urgenciaId,
                        initPoint  = initPoint,
                    ))
                    realtimeJob?.cancel()
                    pollingJob?.cancel()
                }
            }
        }
    }

    // ── REALTIME: ESCUTAR TABELA payments ────────────────────────────────

    /**
     * Abre canal WebSocket no Supabase e escuta INSERT/UPDATE na tabela
     * `payments` filtrado pelo urgencia_id.
     * Quando `status = approved` chega, emite Confirmado e cancela os jobs.
     */
    private fun iniciarEscutaRealtime(urgenciaId: String) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            val wsClient = io.ktor.client.HttpClient(Android) {
                install(WebSockets) { pingInterval = 25_000L }
            }

            try {
                val token = currentToken ?: LOCAL_KEY
                wsClient.webSocket(
                    urlString = "$WS_URL?apikey=$LOCAL_KEY&vsn=1.0.0",
                    request   = { header("Authorization", "Bearer $token") },
                ) {
                    // JOIN no canal filtrado por urgencia_id
                    send(Frame.Text(buildJoinPayments(urgenciaId)))
                    AppLogger.infoPagamento(
                        etapa      = "realtime_payments_conectado",
                        urgenciaId = urgenciaId,
                        detalhe    = "Escutando tabela payments",
                    )

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val texto = frame.readText()
                        val confirmado = processarFramePayment(texto, urgenciaId)
                        if (confirmado) break   // pagamento confirmado — sair do loop
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.aviso(TAG, "Realtime payments falhou: ${e.message}. Polling assumira.")
            } finally {
                wsClient.close()
            }
        }
    }

    private fun buildJoinPayments(urgenciaId: String): String {
        val payload = buildJsonObject {
            putJsonObject("config") {
                putJsonObject("broadcast") { put("self", false) }
                putJsonArray("postgres_changes") {
                    addJsonObject {
                        put("event",  "INSERT")
                        put("schema", "public")
                        put("table",  "payments")
                        put("filter", "urgencia_id=eq.$urgenciaId")
                    }
                    addJsonObject {
                        put("event",  "UPDATE")
                        put("schema", "public")
                        put("table",  "payments")
                        put("filter", "urgencia_id=eq.$urgenciaId")
                    }
                }
            }
        }
        return buildJsonObject {
            put("event",   "phx_join")
            put("topic",   "realtime:payments:urgencia_id=eq.$urgenciaId")
            put("payload", payload)
            put("ref",     "pagamento_1")
        }.toString()
    }

    private suspend fun processarFramePayment(texto: String, urgenciaId: String): Boolean {
        return try {
            val obj    = jsonParser.parseToJsonElement(texto).jsonObject
            val event  = obj["event"]?.jsonPrimitive?.content ?: return false
            if (event != "postgres_changes") return false

            val record  = obj["payload"]?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("record")?.jsonObject ?: return false

            val status  = record["status"]?.jsonPrimitive?.content ?: return false
            val valor   = record["valor"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            AppLogger.infoPagamento(
                etapa      = "realtime_payment_evento",
                urgenciaId = urgenciaId,
                detalhe    = "status=$status valor=$valor",
            )

            when (status) {
                "approved" -> {
                    realtimeJob?.cancel()
                    pollingJob?.cancel()
                    _state.emit(PagamentoState.Confirmado(
                        urgenciaId = urgenciaId,
                        valor      = valor,
                    ))
                    AppLogger.infoPagamento(
                        etapa      = "pagamento_confirmado",
                        urgenciaId = urgenciaId,
                        detalhe    = "Realtime confirmou aprovacao",
                    )
                    true
                }
                "rejected" -> {
                    realtimeJob?.cancel()
                    pollingJob?.cancel()
                    _state.emit(PagamentoState.Erro(
                        motivo = "Pagamento recusado. Tente novamente com outro método.",
                        tipo   = TipoErroPagamento.DESCONHECIDO,
                    ))
                    true
                }
                else -> false   // pending, processing — continuar escutando
            }
        } catch (_: Exception) { false }
    }

    // ── FALLBACK: POLLING DA TABELA payments ──────────────────────────────

    /**
     * Ativado quando:
     *  (a) o usuário voltou via URL de success mas o Realtime não confirmou ainda
     *  (b) o Realtime perdeu conexão
     *
     * Consulta a tabela `payments` a cada 5s por até 30s.
     * Se não confirmar, emite Pendente (webhook pode chegar depois via FCM).
     */
    private fun iniciarPollingConfirmacao(urgenciaId: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val inicio = System.currentTimeMillis()
            AppLogger.aviso(TAG, "Polling fallback iniciado para urgencia=$urgenciaId")

            while (isActive) {
                delay(POLLING_INTERVAL_MS)

                if (System.currentTimeMillis() - inicio > REALTIME_CONFIRM_TIMEOUT_MS) {
                    // Timeout — pagamento pode confirmar depois via push
                    AppLogger.erroPagamento(
                        etapa      = "polling_timeout",
                        urgenciaId = urgenciaId,
                        httpStatus = 0,
                        corpo      = "Realtime nao confirmou em ${REALTIME_CONFIRM_TIMEOUT_MS / 1000}s",
                    )
                    if (_state.value is PagamentoState.Processando) {
                        _state.emit(PagamentoState.Pendente(urgenciaId))
                    }
                    break
                }

                // Consultar tabela payments via REST
                try {
                    val token    = currentToken ?: LOCAL_KEY
                    val response = httpClient.get(
                        "$LOCAL_URL/rest/v1/payments" +
                                "?urgencia_id=eq.$urgenciaId" +
                                "&select=id,status,valor" +
                                "&order=criado_em.desc&limit=1"
                    ) {
                        header("apikey",        LOCAL_KEY)
                        header("Authorization", "Bearer $token")
                        header("Accept",        "application/json")
                    }

                    val corpo = response.bodyAsText()
                    val lista  = jsonParser.decodeFromString<List<Payment>>(corpo)
                    val pagamento = lista.firstOrNull() ?: continue

                    when (pagamento.status) {
                        "approved" -> {
                            realtimeJob?.cancel()
                            pollingJob?.cancel()
                            _state.emit(PagamentoState.Confirmado(
                                urgenciaId = urgenciaId,
                                valor      = pagamento.valor,
                            ))
                            AppLogger.infoPagamento(
                                etapa      = "pagamento_confirmado",
                                urgenciaId = urgenciaId,
                                detalhe    = "Polling confirmou aprovacao",
                            )
                            return@launch
                        }
                        "rejected" -> {
                            realtimeJob?.cancel()
                            pollingJob?.cancel()
                            _state.emit(PagamentoState.Erro(
                                motivo = "Pagamento recusado pelo processador.",
                                tipo   = TipoErroPagamento.DESCONHECIDO,
                            ))
                            return@launch
                        }
                        else -> Unit   // pending — continuar polling
                    }
                } catch (e: Exception) {
                    AppLogger.aviso(TAG, "Polling REST falhou: ${e.message}")
                }
            }
        }
    }

    // ── RETRY SEM NOVA PREFERÊNCIA ────────────────────────────────────────

    /**
     * Chamado quando o estado é Cancelado e o usuário clica "Tentar novamente".
     * Reutiliza o initPoint existente sem chamar a Edge Function novamente.
     */
    fun tentarNovamente(urgenciaId: String, initPoint: String) {
        scope.launch {
            AppLogger.infoPagamento(
                etapa      = "retry_checkout",
                urgenciaId = urgenciaId,
                detalhe    = "Reabrindo checkout sem nova preferencia",
            )
            // Buscar descricao/valor do estado anterior se disponível
            _state.emit(PagamentoState.CheckoutAberto(
                initPoint    = initPoint,
                preferenceId = "",   // não necessário para retry
                descricao    = "Pagamento da consulta",
                valor        = 0.0,  // recarregado ao reabrir o WebView
                urgenciaId   = urgenciaId,
            ))
        }
    }

    // ── VERIFICAR PENDÊNCIA ───────────────────────────────────────────────

    /**
     * Verifica se existe um pagamento pending para o usuário logado.
     * Chamada ao abrir o app para redirecionar para PagamentoScreen.
     * Retorna o urgencia_id pendente ou null se não houver.
     */
    suspend fun verificarPendencia(): String? {
        val uid   = currentUserId ?: return null
        val token = currentToken  ?: LOCAL_KEY
        return try {
            val response = httpClient.get(
                "$LOCAL_URL/rest/v1/payments" +
                        "?client_id=eq.$uid" +
                        "&status=eq.pending" +
                        "&select=urgencia_id" +
                        "&order=criado_em.desc&limit=1"
            ) {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
            }
            if (response.status.value !in 200..299) return null
            val lista = jsonParser.decodeFromString<List<Payment>>(response.bodyAsText())
            lista.firstOrNull()?.urgenciaId.also { id ->
                if (id != null) AppLogger.infoPagamento(
                    etapa      = "pagamento_pendente_encontrado",
                    urgenciaId = id,
                    detalhe    = "Redirecionando ao abrir app",
                )
            }
        } catch (e: Exception) {
            AppLogger.aviso(TAG, "Falha ao verificar pagamento pendente: ${e.message}")
            null
        }
    }

    // ── RESET ─────────────────────────────────────────────────────────────

    fun resetar() {
        realtimeJob?.cancel()
        pollingJob?.cancel()
        scope.launch { _state.emit(PagamentoState.Idle) }
    }

    // ── ATENDIMENTOS REGULARES: CRIAR PREFERÊNCIA ─────────────────────────

    /**
     * Solicita preferência de pagamento para um Atendimento Regular.
     * Fluxo idêntico ao de urgências, mas usa a Edge Function separada
     * e filtra o Realtime por agendamento_regular_id.
     */
    fun criarPreferenciaRegular(agendamentoRegularId: String) {
        scope.launch {
            _state.emit(PagamentoState.CriandoPreferencia(agendamentoRegularId))

            val token = currentToken ?: run {
                _state.emit(PagamentoState.Erro(
                    motivo = "Sessão inválida. Faça login novamente.",
                    tipo   = TipoErroPagamento.PREFERENCIA_NEGADA,
                ))
                return@launch
            }

            try {
                val response = httpClient.post(EDGE_URL_REGULAR) {
                    header("Authorization",   "Bearer $token")
                    header("apikey",          LOCAL_KEY)
                    header("Idempotency-Key", "regular_$agendamentoRegularId")
                    contentType(ContentType.Application.Json)
                    setBody(CriarPreferenciaRegularRequest(
                        agendamentoRegularId = agendamentoRegularId,
                    ))
                }

                val corpo = response.bodyAsText()
                Log.d(TAG, "Edge Function regular HTTP ${response.status.value}")

                when (response.status.value) {
                    200, 409 -> {
                        val preferencia = jsonParser.decodeFromString<PreferenciaResponse>(corpo)
                        _state.emit(PagamentoState.CheckoutAberto(
                            initPoint            = preferencia.initPoint,
                            preferenceId         = preferencia.preferenceId,
                            descricao            = preferencia.descricao,
                            valor                = preferencia.valor,
                            urgenciaId           = "",
                            agendamentoRegularId = agendamentoRegularId,
                        ))
                        iniciarEscutaRealtimeRegular(agendamentoRegularId)
                    }
                    else -> {
                        _state.emit(PagamentoState.Erro(
                            motivo = "Erro ao iniciar pagamento (${response.status.value}).",
                            tipo   = TipoErroPagamento.REDE,
                        ))
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                AppLogger.erroRede(EDGE_URL_REGULAR, e, "regular=$agendamentoRegularId")
                _state.emit(PagamentoState.Erro(
                    motivo = "Sem conexão com a internet.",
                    tipo   = TipoErroPagamento.REDE,
                ))
            } catch (e: Exception) {
                AppLogger.erroRede(EDGE_URL_REGULAR, e, "regular=$agendamentoRegularId")
                _state.emit(PagamentoState.Erro(
                    motivo = "Falha inesperada ao criar preferência.",
                    tipo   = TipoErroPagamento.DESCONHECIDO,
                ))
            }
        }
    }

    // ── REALTIME PARA ATENDIMENTOS REGULARES ──────────────────────────────

    private fun iniciarEscutaRealtimeRegular(agendamentoRegularId: String) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            val wsClient = io.ktor.client.HttpClient(Android) {
                install(WebSockets) { pingInterval = 25_000L }
            }
            try {
                val token = currentToken ?: LOCAL_KEY
                wsClient.webSocket(
                    urlString = "$WS_URL?apikey=$LOCAL_KEY&vsn=1.0.0",
                    request   = { header("Authorization", "Bearer $token") },
                ) {
                    val joinMsg = buildJsonObject {
                        put("event", "phx_join")
                        put("topic", "realtime:payments:agendamento_regular_id=eq.$agendamentoRegularId")
                        putJsonObject("payload") {
                            putJsonObject("config") {
                                putJsonObject("broadcast") { put("self", false) }
                                putJsonArray("postgres_changes") {
                                    addJsonObject {
                                        put("event",  "INSERT")
                                        put("schema", "public")
                                        put("table",  "payments")
                                        put("filter", "agendamento_regular_id=eq.$agendamentoRegularId")
                                    }
                                    addJsonObject {
                                        put("event",  "UPDATE")
                                        put("schema", "public")
                                        put("table",  "payments")
                                        put("filter", "agendamento_regular_id=eq.$agendamentoRegularId")
                                    }
                                }
                            }
                        }
                        put("ref", "regular_1")
                    }.toString()
                    send(Frame.Text(joinMsg))

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val confirmado = processarFramePaymentRegular(frame.readText(), agendamentoRegularId)
                        if (confirmado) break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.aviso(TAG, "Realtime regular falhou: ${e.message}")
            } finally {
                wsClient.close()
            }
        }
    }

    private suspend fun processarFramePaymentRegular(texto: String, agendamentoRegularId: String): Boolean {
        return try {
            val obj    = jsonParser.parseToJsonElement(texto).jsonObject
            val event  = obj["event"]?.jsonPrimitive?.content ?: return false
            if (event != "postgres_changes") return false
            val record = obj["payload"]?.jsonObject?.get("data")?.jsonObject?.get("record")?.jsonObject ?: return false
            val status = record["status"]?.jsonPrimitive?.content ?: return false
            val valor  = record["valor"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            when (status) {
                "approved" -> {
                    realtimeJob?.cancel()
                    pollingJob?.cancel()
                    _state.emit(PagamentoState.Confirmado(
                        agendamentoRegularId = agendamentoRegularId,
                        valor                = valor,
                    ))
                    true
                }
                "rejected" -> {
                    realtimeJob?.cancel()
                    pollingJob?.cancel()
                    _state.emit(PagamentoState.Erro(
                        motivo = "Pagamento recusado. Tente novamente.",
                        tipo   = TipoErroPagamento.DESCONHECIDO,
                    ))
                    true
                }
                else -> false
            }
        } catch (_: Exception) { false }
    }

} // fim do object PagamentoRepository