package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// PagamentoState.kt
//
// Estados que cobrem todo o ciclo de pagamento:
//
// FLUXO NORMAL:
//   Idle → CriandoPreferencia → CheckoutAberto(url) → Processando → Confirmado
//
// FLUXOS ALTERNATIVOS:
//   CheckoutAberto → Pendente  (pagamento em análise — ex: boleto)
//   CheckoutAberto → Cancelado (usuário fechou sem pagar)
//   Qualquer → Erro(motivo)    → Idle
//
// CONFIRMAÇÃO:
//   O estado Confirmado chega via Realtime (tabela `payments`), não via
//   URL de retorno — a URL de retorno apenas sinaliza que o usuário voltou
//   do checkout. A fonte da verdade é sempre o webhook do backend.
// ═══════════════════════════════════════════════════════════════════════════

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── REQUEST para a Edge Function criar-preferencia-pagamento (urgências) ──
@Serializable
data class CriarPreferenciaRequest(
    @SerialName("urgencia_id")     val urgenciaId:     String,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

// ── REQUEST para a Edge Function criar-preferencia-regular ────────────────
@Serializable
data class CriarPreferenciaRegularRequest(
    @SerialName("agendamento_regular_id") val agendamentoRegularId: String,
)

// ── RESPOSTA da Edge Function ─────────────────────────────────────────────
@Serializable
data class PreferenciaResponse(
    @SerialName("init_point")      val initPoint:      String,  // URL do checkout MP
    @SerialName("preference_id")   val preferenceId:   String,  // ID da preferência no MP
    @SerialName("payment_id")      val paymentId:      String? = null, // preenchido após aprovação
    val valor:                     Double,                       // apenas para exibição
    val descricao:                 String,                       // ex: "Consulta urgente — 25min"
)

// ── Pagamento na tabela `payments` — observado via Realtime ───────────────
@Serializable
data class Payment(
    val id:          String,
    @SerialName("urgencia_id")            val urgenciaId:           String?  = null,
    @SerialName("agendamento_regular_id") val agendamentoRegularId: String?  = null,
    val status:      String,
    val valor:       Double,
    @SerialName("mp_payment_id") val mpPaymentId: String? = null,
    @SerialName("criado_em")    val criadoEm:    String? = null,
)

// ═══════════════════════════════════════════════════════════════════════════
// ESTADOS DO CICLO DE PAGAMENTO
// ═══════════════════════════════════════════════════════════════════════════

sealed class PagamentoState {

    /** Nenhuma operação em curso. */
    object Idle : PagamentoState()

    /** Chamando a Edge Function para criar preferência no MP. */
    data class CriandoPreferencia(val urgenciaId: String) : PagamentoState()

    /**
     * `init_point` obtido — WebView/CustomTabs pode ser aberto.
     * [initPoint] é a URL do checkout do Mercado Pago.
     * [preferenceId] identifica a transação para rastreamento.
     * [descricao] e [valor] são exibidos na tela antes de abrir o checkout.
     */
    data class CheckoutAberto(
        val initPoint:            String,
        val preferenceId:         String,
        val descricao:            String,
        val valor:                Double,
        val urgenciaId:           String = "",
        val agendamentoRegularId: String = "",
    ) : PagamentoState()

    data class Processando(
        val urgenciaId:           String = "",
        val agendamentoRegularId: String = "",
    ) : PagamentoState()

    data class Confirmado(
        val urgenciaId:           String = "",
        val agendamentoRegularId: String = "",
        val valor:                Double,
    ) : PagamentoState()

    data class Pendente(
        val urgenciaId:           String = "",
        val agendamentoRegularId: String = "",
    ) : PagamentoState()

    data class Cancelado(
        val urgenciaId:           String = "",
        val agendamentoRegularId: String = "",
        val initPoint:            String,
    ) : PagamentoState()

    /**
     * Erro não recuperável — exibir motivo e voltar para Idle.
     * [tipo] alimenta o AppLogger para categorização no Firebase.
     */
    data class Erro(
        val motivo: String,
        val tipo:   TipoErroPagamento = TipoErroPagamento.DESCONHECIDO,
    ) : PagamentoState()
}

// ── Categorias de erro para AppLogger ────────────────────────────────────

enum class TipoErroPagamento {
    PREFERENCIA_NEGADA,    // Edge Function retornou 4xx
    IDEMPOTENCIA,          // requisição duplicada detectada pelo backend
    REDE,                  // sem conexão ou timeout
    WEBHOOK_TIMEOUT,       // Realtime não confirmou em tempo razoável
    DESCONHECIDO,
}