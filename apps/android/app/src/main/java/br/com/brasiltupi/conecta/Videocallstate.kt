package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// VideoCallState.kt
//
// Estados tipados que representam todo o ciclo de vida de uma videochamada.
// A UI observa um StateFlow<VideoCallState> e reage a cada transição.
//
// FLUXO NORMAL:
//   Idle → SolicitandoToken → TokenObtido → Conectando → EmChamada → Encerrada
//
// FLUXO DE ERRO:
//   Qualquer estado → Erro(motivo) → Idle (após dismiss)
//
// FLUXO DE REAUTENTICAÇÃO:
//   EmChamada → TokenExpirado → SolicitandoToken → EmChamada
// ═══════════════════════════════════════════════════════════════════════════

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── RESPOSTA DA EDGE FUNCTION get-call-token ──────────────────────────────
// Mapeada diretamente do JSON retornado pelo backend Supabase.

@Serializable
data class CallTokenResponse(
    val token:   String,
    @SerialName("user_id")  val userId:  String,
    @SerialName("call_id")  val callId:  String,
)

// ── REQUEST PARA A EDGE FUNCTION ──────────────────────────────────────────

@Serializable
data class CallTokenRequest(
    @SerialName("urgencia_id") val urgenciaId: String,
)

// ── ESTADOS DO CICLO DE VIDA DA CHAMADA ───────────────────────────────────

sealed class VideoCallState {

    /** Nenhuma chamada em curso — estado padrão. */
    object Idle : VideoCallState()

    /** Chamada à Edge Function em andamento — mostrar loading. */
    data class SolicitandoToken(val urgenciaId: String) : VideoCallState()

    /**
     * Token recebido — StreamVideoClient pode ser inicializado.
     * A UI não precisa reagir a este estado diretamente;
     * o repositório avança automaticamente para Conectando.
     */
    data class TokenObtido(
        val token:   String,
        val userId:  String,
        val callId:  String,
    ) : VideoCallState()

    /** Stream SDK inicializado, aguardando join() completar. */
    data class Conectando(val callId: String) : VideoCallState()

    /** join() concluído — Stream está renderizando vídeo. */
    data class EmChamada(
        val callId:  String,
        val userId:  String,
    ) : VideoCallState()

    /**
     * Token expirou (HTTP 401 do Stream) durante uma chamada ativa.
     * O repositório solicita novo token automaticamente e retorna para EmChamada.
     * A UI pode exibir um indicador discreto de reconexão.
     */
    data class TokenExpirado(val callId: String) : VideoCallState()

    /** Chamada encerrada normalmente — navegar para tela de avaliação. */
    data class Encerrada(val callId: String) : VideoCallState()

    /**
     * Erro não recuperável — exibir mensagem e voltar para Idle.
     * [motivo] é a string exibida ao usuário.
     * [tipo]   categoriza o erro para o AppLogger.
     */
    data class Erro(
        val motivo: String,
        val tipo:   TipoErroVideo = TipoErroVideo.DESCONHECIDO,
    ) : VideoCallState()
}

// ── CATEGORIAS DE ERRO — alimentam o AppLogger ────────────────────────────

enum class TipoErroVideo {
    TOKEN_NEGADO,        // Edge Function retornou 401/403 (acesso negado)
    STATUS_INVALIDO,     // urgência com status incorreto (ex: já finalizada)
    ACESSO_NAO_AUTORIZADO, // uid não é cliente nem profissional da urgência
    CHAMADA_ENCERRADA,   // tentativa de entrar em call já finalizada
    REDE,                // falha de rede (sem internet, timeout)
    STREAM_SDK,          // erro interno do Stream SDK (join falhou)
    DESCONHECIDO,
}