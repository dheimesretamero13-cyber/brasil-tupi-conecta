package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AnalyticsTracker.kt  · Fase 4.5
//
// Singleton manual — sem Hilt, sem frameworks.
// Inicializado uma única vez em MainActivity.onCreate via AnalyticsTracker.init().
// Todos os eventos do funil de conversão são disparados a partir daqui.
// ═══════════════════════════════════════════════════════════════════════════

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object AnalyticsTracker {

    // Instância lazy — segura, pois init() garante que o Firebase já subiu.
    private val fa: FirebaseAnalytics by lazy { Firebase.analytics }

    // ── Inicialização ─────────────────────────────────────────────────────
    // Chame em MainActivity.onCreate() ANTES de setContent{}.
    // Serve apenas para acionar o lazy e confirmar que o Firebase está pronto.
    fun init() {
        fa.setAnalyticsCollectionEnabled(true)
    }

    // ── Helpers internos ──────────────────────────────────────────────────
    private fun log(event: String, params: Bundle? = null) {
        fa.logEvent(event, params)
    }

    private fun bundle(vararg pairs: Pair<String, String>): Bundle =
        Bundle().apply { pairs.forEach { (k, v) -> putString(k, v) } }

    // ═════════════════════════════════════════════════════════════════════
    // FUNIL DE CONVERSÃO
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Disparado no primeiro lançamento do app (OnboardingScreen, quando
     * o usuário ainda não completou o onboarding).
     * Equivale ao evento padrão do Firebase, mas explícito para o funil.
     */
    fun appInstall() = log("app_install")

    /**
     * Disparado imediatamente após cadastro bem-sucedido em CadastroScreen,
     * no callback onConcluido.
     *
     * @param tipo "cliente" | "profissional_certificado" | "profissional_liberal"
     */
    fun signUp(tipo: String) = log(
        FirebaseAnalytics.Event.SIGN_UP,
        bundle(FirebaseAnalytics.Param.METHOD to tipo)
    )

    /**
     * Disparado na primeira busca do cliente em BuscaScreen,
     * no momento em que a query é submetida.
     *
     * @param termo texto que o usuário digitou
     */
    fun firstSearch(termo: String) = log(
        "first_search",
        bundle("search_term" to termo.take(100))
    )

    /**
     * Disparado quando o cliente abre o perfil/estúdio de um profissional.
     * Chame em EstudioVitrineScreen ao entrar na tela (LaunchedEffect).
     *
     * @param profissionalId UUID do profissional visualizado
     * @param area especialidade exibida (ex: "Nutricionista")
     */
    fun profileView(profissionalId: String, area: String) = log(
        "profile_view",
        bundle(
            "profissional_id" to profissionalId,
            "area"            to area,
        )
    )

    /**
     * Disparado no onClick de confirmação do primeiro agendamento
     * em AgendamentoScreen / PagamentoScreen (após criarAgendamento bem-sucedido).
     *
     * @param tipo "normal" | "urgente"
     * @param valor valor em centavos (Int.toString())
     */
    fun firstBooking(tipo: String, valor: Int) = log(
        "first_booking",
        bundle(
            "booking_type" to tipo,
            "value_cents"  to valor.toString(),
        )
    )

    /**
     * Disparado em VideoCallScreen quando a chamada é iniciada
     * (LaunchedEffect no estado "conectado" / "chamada iniciada").
     *
     * @param urgenciaId UUID da urgência associada
     */
    fun firstCall(urgenciaId: String) = log(
        "first_call",
        bundle("urgencia_id" to urgenciaId)
    )

    /**
     * Disparado em PagamentoScreen quando o pagamento é confirmado
     * com sucesso (onConfirmado).
     *
     * @param valor  valor em reais (Double)
     * @param metodo "pix" | "cartao" | "credito"
     */
    fun firstPayment(valor: Double, metodo: String) {
        val params = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, valor)
            putString(FirebaseAnalytics.Param.CURRENCY, "BRL")
            putString("payment_method", metodo)
        }
        log(FirebaseAnalytics.Event.PURCHASE, params)
    }

    /**
     * Disparado quando o usuário retorna ao app após 7 dias sem abrir.
     * Chame em AppNavigation (LaunchedEffect) comparando a data do
     * último acesso salva em DataStore com a data atual.
     *
     * @param tipoConta "cliente" | "profissional"
     */
    fun retention7d(tipoConta: String) = log(
        "retention_7d",
        bundle("account_type" to tipoConta)
    )

    /**
     * Mesmo que retention7d, mas para 30 dias.
     *
     * @param tipoConta "cliente" | "profissional"
     */
    fun retention30d(tipoConta: String) = log(
        "retention_30d",
        bundle("account_type" to tipoConta)
    )

    // ── Utilitário: User Property ─────────────────────────────────────────
    /**
     * Define propriedades de usuário reutilizáveis em todos os relatórios.
     * Chame após login bem-sucedido em LoginScreen.
     *
     * @param tipo "cliente" | "profissional_certificado" | "profissional_liberal"
     */
    fun setUserType(tipo: String) {
        fa.setUserProperty("account_type", tipo)
    }
}