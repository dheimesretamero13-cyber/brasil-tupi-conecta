package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// StreamVideoRepository.kt
//
// Responsabilidades deste arquivo:
//  1. Solicitar token à Edge Function Supabase (stream-token) via Ktor
//  2. Gerenciar o StateFlow<VideoCallState> que a UI observa
//  3. Transicionar status da urgência para 'in_progress' após join()
//  4. Renovar token quando o SDK reportar expiração (HTTP 401)
//  5. Instrumentar todo erro via AppLogger
//
// O QUE ESTE ARQUIVO NÃO FAZ:
//  • NÃO importa o Stream SDK diretamente
//  • A inicialização do StreamVideoClient, join() e leave() ficam na
//    VideoCallScreen.kt, onde o Context é garantido e o ciclo de vida
//    do Composable gerencia os recursos do SDK.
//
// PRINCÍPIO DE SEGURANÇA:
//  Nenhuma chave secreta do Stream existe no app. O token vem exclusivamente
//  do backend (Edge Function), que valida ownership e status antes de gerar.
// ═══════════════════════════════════════════════════════════════════════════


import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

private const val TAG = "StreamVideoRepo"

// URL da Edge Function — construída dinamicamente a partir da URL base
private val EDGE_FUNCTION_URL get() = "$LOCAL_URL/functions/v1/stream-token"

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════════════════
// DTO para atualizar status da urgência para 'in_progress'
// ═══════════════════════════════════════════════════════════════════════════

@kotlinx.serialization.Serializable
private data class AtualizarStatusRequest(val status: String)

// ═══════════════════════════════════════════════════════════════════════════
// REPOSITÓRIO
// ═══════════════════════════════════════════════════════════════════════════

object StreamVideoRepository {

    private val _callState = MutableStateFlow<VideoCallState>(VideoCallState.Idle)

    /**
     * Colete este Flow no Composable para reagir a cada transição de estado.
     * Padrão: val state by StreamVideoRepository.callState.collectAsState()
     */
    val callState: StateFlow<VideoCallState> = _callState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── PASSO 1: SOLICITAR TOKEN ──────────────────────────────────────────

    /**
     * Solicita token à Edge Function e avança o estado para TokenObtido.
     * Chamado pelo VideoCallScreen após permissões concedidas.
     *
     * A VideoCallScreen observa callState e, ao receber TokenObtido,
     * inicializa o StreamVideoClient com os dados retornados.
     */
    fun solicitarToken(urgenciaId: String) {
        scope.launch {
            _callState.emit(VideoCallState.SolicitandoToken(urgenciaId))
            AppLogger.chave("video_urgencia_id", urgenciaId)
            AppLogger.info(TAG, "Solicitando token para urgencia=$urgenciaId")

            // Guard: usuário autenticado via AuthRepository (thread-safe)
            val token = AuthRepository.token ?: run {
                AppLogger.erroAuth("get_call_token", mensagemExtra = "token Supabase ausente")
                _callState.emit(VideoCallState.Erro(
                    motivo = "Sessão inválida. Faça login novamente.",
                    tipo   = TipoErroVideo.TOKEN_NEGADO,
                ))
                return@launch
            }

            try {
                val response = httpClient.post(EDGE_FUNCTION_URL) {
                    header("Authorization", "Bearer $token")
                    header("apikey", LOCAL_KEY)
                    contentType(ContentType.Application.Json)
                    setBody(CallTokenRequest(urgenciaId = urgenciaId))
                }

                val corpo = response.bodyAsText()
                AppLogger.info(TAG, "Edge Function HTTP ${response.status.value}")

                when (response.status.value) {
                    200 -> {
                        val resposta = jsonParser.decodeFromString<CallTokenResponse>(corpo)
                        AppLogger.info(TAG, "Token obtido para call=${resposta.callId}")
                        // NUNCA logar resposta.token — é uma credencial de acesso
                        AppLogger.chave("stream_call_id", resposta.callId)
                        _callState.emit(VideoCallState.TokenObtido(
                            token  = resposta.token,
                            userId = resposta.userId,
                            callId = resposta.callId,
                        ))
                    }
                    401, 403 -> {
                        AppLogger.erroAuth(
                            operacao      = "get_call_token",
                            mensagemExtra = "HTTP ${response.status.value} — uid sem acesso a urgencia=$urgenciaId",
                        )
                        _callState.emit(VideoCallState.Erro(
                            motivo = "Acesso não autorizado a esta chamada.",
                            tipo   = TipoErroVideo.ACESSO_NAO_AUTORIZADO,
                        ))
                    }
                    409 -> {
                        // Urgência com status incompatível com videochamada
                        AppLogger.aviso(TAG, "Status inválido para urgencia=$urgenciaId: $corpo")
                        _callState.emit(VideoCallState.Erro(
                            motivo = "Esta chamada não está mais disponível.",
                            tipo   = TipoErroVideo.STATUS_INVALIDO,
                        ))
                    }
                    else -> {
                        val erro = RuntimeException("Edge Function HTTP ${response.status.value}: $corpo")
                        AppLogger.erroRede(EDGE_FUNCTION_URL, erro, "urgencia=$urgenciaId")
                        _callState.emit(VideoCallState.Erro(
                            motivo = "Erro ao conectar com o servidor. Tente novamente.",
                            tipo   = TipoErroVideo.REDE,
                        ))
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                AppLogger.erroRede(EDGE_FUNCTION_URL, e, "sem_rede urgencia=$urgenciaId")
                _callState.emit(VideoCallState.Erro(
                    motivo = "Sem conexão com a internet.",
                    tipo   = TipoErroVideo.REDE,
                ))
            } catch (e: Exception) {
                AppLogger.erroRede(EDGE_FUNCTION_URL, e, "urgencia=$urgenciaId")
                _callState.emit(VideoCallState.Erro(
                    motivo = "Falha inesperada ao obter token de chamada.",
                    tipo   = TipoErroVideo.DESCONHECIDO,
                ))
            }
        }
    }

    // ── PASSO 2: NOTIFICAR QUE A CHAMADA ESTÁ ATIVA ───────────────────────

    /**
     * Chamado pela VideoCallScreen após join() bem-sucedido no Stream SDK.
     * Responsabilidade dividida:
     *   • VideoCallScreen: inicializa SDK, executa join()
     *   • Repositório:     atualiza estado + banco (in_progress)
     */
    fun notificarChamadaAtiva(callId: String, userId: String) {
        scope.launch {
            _callState.emit(VideoCallState.EmChamada(callId = callId, userId = userId))
            AppLogger.info(TAG, "Chamada ativa: call=$callId user=$userId")
            AppLogger.chave("stream_call_ativa", true)
            // Atualizar status no banco APÓS join() confirmado pela UI
            atualizarStatusInProgress(callId)
        }
    }

    // ── RENOVAÇÃO DE TOKEN ────────────────────────────────────────────────

    /**
     * Chamado pela VideoCallScreen quando o Stream SDK reportar erro 401.
     * Emite TokenExpirado (UI mostra indicador de reconexão) e solicita
     * novo token — a VideoCallScreen reage ao TokenObtido para re-inicializar.
     */
    fun renovarToken(urgenciaId: String) {
        scope.launch {
            AppLogger.aviso(TAG, "Token expirado. Renovando para urgencia=$urgenciaId")
            _callState.emit(VideoCallState.TokenExpirado(urgenciaId))
            // Reutiliza o mesmo fluxo de solicitarToken
            solicitarToken(urgenciaId)
        }
    }

    // ── PASSO 3: ATUALIZAR STATUS NO BANCO ───────────────────────────────

    /**
     * PATCH na tabela urgencias: accepted → in_progress.
     * Executado APENAS após join() confirmado pela VideoCallScreen.
     * Falha aqui não encerra a chamada — o cron do backend corrige.
     */
    private suspend fun atualizarStatusInProgress(urgenciaId: String) {
        try {
            val token = AuthRepository.token ?: LOCAL_KEY
            val response = httpClient.patch(
                "$LOCAL_URL/rest/v1/urgencias?id=eq.$urgenciaId&status=eq.accepted"
            ) {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody(AtualizarStatusRequest(status = "in_progress"))
            }
            if (response.status.value in 200..299) {
                AppLogger.info(TAG, "Status in_progress confirmado: urgencia=$urgenciaId")
            } else {
                AppLogger.aviso(TAG, "Falha ao atualizar status: HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            // Não encerrar a chamada — erro não crítico
            AppLogger.erroRede(
                "/rest/v1/urgencias (in_progress)",
                e,
                "urgencia=$urgenciaId — chamada ativa, erro não crítico",
            )
        }
    }

    // ── NOTIFICAÇÕES DE ESTADO ────────────────────────────────────────────

    /** Emite Conectando — chamado pela VideoCallScreen antes de join(). */
    fun notificarConectando(callId: String) {
        scope.launch {
            AppLogger.info(TAG, "Conectando à sala call=$callId")
            _callState.emit(VideoCallState.Conectando(callId))
        }
    }

    /**
     * Chamado pela VideoCallScreen se o Stream SDK reportar erro fatal.
     */
    fun notificarErroChamada(motivo: String, tipo: TipoErroVideo, throwable: Throwable? = null) {
        scope.launch {
            AppLogger.erroRealtime(
                fase           = tipo.name.lowercase(),
                profissionalId = AuthRepository.userId ?: "desconhecido",
                throwable      = throwable ?: RuntimeException(motivo),
            )
            _callState.emit(VideoCallState.Erro(motivo = motivo, tipo = tipo))
        }
    }

    // ── ENCERRAMENTO ──────────────────────────────────────────────────────

    /**
     * Chamado pela VideoCallScreen após leave() no Stream SDK.
     * Emite Encerrada para navegar à tela de avaliação.
     */
    fun notificarChamadaEncerrada(callId: String) {
        scope.launch {
            AppLogger.info(TAG, "Chamada encerrada: call=$callId")
            AppLogger.chave("stream_call_ativa", false)
            _callState.emit(VideoCallState.Encerrada(callId))
        }
    }

    /** Volta para Idle após o usuário dispensar um diálogo de erro. */
    fun resetar() {
        scope.launch {
            AppLogger.info(TAG, "Estado resetado para Idle")
            _callState.emit(VideoCallState.Idle)
        }
    }
}