package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AppLogger.kt
//
// Logger centralizado do projeto. Todo erro crítico passa por aqui.
// Em produção: envia para Firebase Crashlytics.
// Em debug:    imprime no Logcat (Crashlytics desabilitado por padrão em debug).
//
// USO:
//   AppLogger.erro("TAG", "mensagem", exception)          // erro com exceção
//   AppLogger.erro("TAG", "mensagem")                     // erro sem exceção
//   AppLogger.aviso("TAG", "mensagem")                    // breadcrumb de aviso
//   AppLogger.chave("user_id", currentUserId ?: "anon")   // contexto do usuário
//
// PONTOS INSTRUMENTADOS:
//   • Falhas de rede Ktor            → AppLogger.erroRede()
//   • Falhas na RPC accept_urgencia  → AppLogger.erroRpc()
//   • Falhas no UrgenciasRealtime    → AppLogger.erroRealtime()
//   • Falhas de auth / token         → AppLogger.erroAuth()
// ═══════════════════════════════════════════════════════════════════════════

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

object AppLogger {

    // Crashlytics é thread-safe — podemos acessar de qualquer coroutine
    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    // ── LOG DE ERRO GENÉRICO ──────────────────────────────────────────────
    // Envia exceção + mensagem como log customizado ao Crashlytics.
    // Se `throwable` for null, registra apenas como log (breadcrumb).

    fun erro(tag: String, mensagem: String, throwable: Throwable? = null) {
        Log.e(tag, mensagem, throwable)
        crashlytics.log("[$tag] $mensagem")
        if (throwable != null) {
            crashlytics.recordException(throwable)
        }
    }

    // ── AVISO (breadcrumb — não gera crash no dashboard) ─────────────────
    fun aviso(tag: String, mensagem: String) {
        Log.w(tag, mensagem)
        crashlytics.log("[WARN][$tag] $mensagem")
    }

    // ── INFO (breadcrumb leve — apenas Logcat + Crashlytics log) ─────────
    fun info(tag: String, mensagem: String) {
        Log.i(tag, mensagem)
        crashlytics.log("[INFO][$tag] $mensagem")
    }

    // ── CHAVE DE CONTEXTO (aparece nos detalhes do crash no console) ──────
    // Exemplos úteis: user_id, user_role, supabase_token_presente
    fun chave(chave: String, valor: String) {
        crashlytics.setCustomKey(chave, valor)
    }

    fun chave(chave: String, valor: Boolean) {
        crashlytics.setCustomKey(chave, valor)
    }

    fun chave(chave: String, valor: Int) {
        crashlytics.setCustomKey(chave, valor)
    }

    // ── IDENTIFICAR USUÁRIO (associa crashes ao ID do Supabase) ──────────
    // Chamar após login bem-sucedido. userId nunca deve ser PII (nome/email).
    fun identificarUsuario(userId: String) {
        crashlytics.setUserId(userId)
        crashlytics.setCustomKey("supabase_user_id", userId)
    }

    // ── LIMPAR IDENTIDADE (chamar no logout) ──────────────────────────────
    fun limparUsuario() {
        crashlytics.setUserId("")
        crashlytics.setCustomKey("supabase_user_id", "logged_out")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS ESPECIALIZADOS — instrumentam os pontos críticos do projeto
    // ═══════════════════════════════════════════════════════════════════════

    // ── 1. ERROS DE REDE KTOR ─────────────────────────────────────────────
    // Chamado em qualquer catch de exception Ktor nos repositórios.
    // `endpoint` ex: "/rest/v1/perfis", "/auth/v1/token"
    fun erroRede(
        endpoint: String,
        throwable: Throwable,
        contexto: String = "",
    ) {
        crashlytics.setCustomKey("erro_endpoint",  endpoint)
        crashlytics.setCustomKey("erro_contexto",  contexto)
        crashlytics.setCustomKey("erro_tipo",      throwable.javaClass.simpleName)
        crashlytics.log("[REDE] Falha em $endpoint — $contexto: ${throwable.message}")
        crashlytics.recordException(throwable)
        Log.e("AppLogger.Rede", "Falha em $endpoint", throwable)
    }

    // ── 2. ERROS NA RPC accept_urgencia ──────────────────────────────────
    // `urgenciaId` é UUID — seguro para logar.
    // `httpStatus` é o código HTTP retornado (ex: 403, 500).
    fun erroRpc(
        urgenciaId: String,
        httpStatus: Int,
        corpoResposta: String,
        throwable: Throwable? = null,
    ) {
        crashlytics.setCustomKey("rpc_urgencia_id",  urgenciaId)
        crashlytics.setCustomKey("rpc_http_status",  httpStatus)
        crashlytics.setCustomKey("rpc_corpo",        corpoResposta.take(200))
        crashlytics.log("[RPC] accept_urgencia falhou — id=$urgenciaId status=$httpStatus")
        if (throwable != null) {
            crashlytics.recordException(throwable)
        } else {
            // Sem throwable: criar sintético para aparecer no dashboard
            crashlytics.recordException(
                RuntimeException("RPC accept_urgencia HTTP $httpStatus: $corpoResposta")
            )
        }
        Log.e("AppLogger.RPC", "accept_urgencia falhou: status=$httpStatus, id=$urgenciaId")
    }

    // ── 3. ERROS NO REALTIME (WebSocket / reconexão) ─────────────────────
    fun erroRealtime(
        fase: String,           // "conexao", "join", "frame", "polling"
        profissionalId: String,
        throwable: Throwable,
    ) {
        crashlytics.setCustomKey("realtime_fase",          fase)
        crashlytics.setCustomKey("realtime_prof_id",       profissionalId)
        crashlytics.setCustomKey("realtime_erro_tipo",     throwable.javaClass.simpleName)
        crashlytics.log("[REALTIME] Falha na fase '$fase' para prof=$profissionalId: ${throwable.message}")
        // Erros de reconexão são esperados em mobile — só registra como log,
        // não como exception, para não poluir o dashboard.
        // Se quiser que apareça como crash: use recordException() aqui.
        Log.w("AppLogger.Realtime", "Falha na fase $fase", throwable)
    }

    // ── 4. ERROS DE AUTH / TOKEN ──────────────────────────────────────────
    fun erroAuth(
        operacao: String,       // "signIn", "signUp", "token_refresh", "getProfile"
        throwable: Throwable? = null,
        mensagemExtra: String = "",
    ) {
        crashlytics.setCustomKey("auth_operacao", operacao)
        if (mensagemExtra.isNotEmpty()) {
            crashlytics.setCustomKey("auth_detalhe", mensagemExtra)
        }
        crashlytics.log("[AUTH] Falha em $operacao — $mensagemExtra")
        if (throwable != null) {
            crashlytics.recordException(throwable)
        }
        Log.e("AppLogger.Auth", "Falha em $operacao: $mensagemExtra", throwable)
    }

    // ── 5. PAGAMENTO — etapas e erros ────────────────────────────────────
    // `etapa` ex: "criar_preferencia", "checkout_aberto", "retorno_checkout",
    //             "pagamento_confirmado", "polling_timeout"
    // Registra como breadcrumb (info) ou exception (erro) conforme gravidade.

    fun infoPagamento(etapa: String, urgenciaId: String, detalhe: String = "") {
        crashlytics.setCustomKey("pag_etapa",      etapa)
        crashlytics.setCustomKey("pag_urgencia",   urgenciaId)
        crashlytics.log("[PAGAMENTO] $etapa — urgencia=$urgenciaId $detalhe")
        Log.i("AppLogger.Pagamento", "$etapa urgencia=$urgenciaId $detalhe")
    }

    fun erroPagamento(
        etapa:      String,
        urgenciaId: String,
        httpStatus: Int     = 0,
        corpo:      String  = "",
        throwable:  Throwable? = null,
    ) {
        crashlytics.setCustomKey("pag_etapa",      etapa)
        crashlytics.setCustomKey("pag_urgencia",   urgenciaId)
        crashlytics.setCustomKey("pag_http",       httpStatus)
        crashlytics.setCustomKey("pag_corpo",      corpo.take(200))
        crashlytics.log("[PAGAMENTO_ERRO] $etapa — urgencia=$urgenciaId http=$httpStatus $corpo")
        if (throwable != null) {
            crashlytics.recordException(throwable)
        } else if (httpStatus > 0) {
            crashlytics.recordException(
                RuntimeException("Pagamento $etapa HTTP $httpStatus: ${corpo.take(100)}")
            )
        }
        Log.e("AppLogger.Pagamento", "$etapa urgencia=$urgenciaId http=$httpStatus", throwable)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FORCE CRASH — APENAS PARA TESTE (remover antes de produção real)
    //
    // Uso: AppLogger.forceCrashParaTeste()
    // Valida que o Crashlytics está configurado corretamente e que
    // o dashboard do Firebase recebe o evento.
    //
    // INSTRUÇÃO: chame esse método uma vez, feche o app, abra novamente.
    // O crash aparece no Firebase Console em ~2 minutos.
    // ═══════════════════════════════════════════════════════════════════════

    fun forceCrashParaTeste() {
        crashlytics.log("Force crash de teste disparado pelo AppLogger")
        throw RuntimeException("TESTE CRASHLYTICS — Brasil Tupi Conecta")
    }
}