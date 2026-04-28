package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AuthRepository.kt
//
// Substitui as variáveis globais `currentToken` e `currentUserId` por um
// StateFlow tipado e thread-safe.
//
// MOTIVAÇÃO:
//   currentToken/currentUserId eram `var` em nível de arquivo — qualquer
//   coroutine podia sobrescrevê-las simultaneamente. O StreamVideoClient
//   observa o estado de auth em uma coroutine interna; sem StateFlow, há
//   risco de race condition no momento de connectUser() / disconnectUser().
//
// USO:
//   // Ler estado atual (fora de coroutine):
//   val estado = AuthRepository.estado.value
//
//   // Ler de forma reativa em Compose:
//   val estado by AuthRepository.estado.collectAsState()
//
//   // Ler token/userId de forma segura em suspend fun:
//   val token  = AuthRepository.token   ?: return  // null → não autenticado
//   val userId = AuthRepository.userId  ?: return
//
// INTEGRAÇÃO COM SUPABASECLIENT:
//   signInAndroid()  → chama AuthRepository.login(token, perfil)
//   signUpAndroid()  → chama AuthRepository.login(token, perfil) após criar perfil
//   signOutAndroid() → chama AuthRepository.logout()
//
// INTEGRAÇÃO COM STREAM SDK (Fase 2):
//   StreamVideoRepository observa AuthRepository.estado para
//   connectUser() / disconnectUser() de forma reativa.
// ═══════════════════════════════════════════════════════════════════════════

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── SEALED CLASS DE ESTADO ────────────────────────────────────────────────
// Três estados possíveis do ciclo de autenticação.

sealed class AuthState {

    /** Estado inicial — DataStore ainda está carregando ou nenhum login ativo. */
    object Carregando : AuthState()

    /** Usuário autenticado com JWT válido. */
    data class Autenticado(
        val userId: String,
        val token: String,
        val perfil: PerfilUsuario,
    ) : AuthState()

    /** Nenhum usuário logado (logout explícito ou sessão expirada). */
    object Deslogado : AuthState()
}

// ── REPOSITÓRIO (singleton) ───────────────────────────────────────────────

object AuthRepository {

    // StateFlow interno — mutável apenas dentro deste objeto
    private val _estado = MutableStateFlow<AuthState>(AuthState.Carregando)

    /** Flow público somente-leitura. Observe em ViewModels ou Composables. */
    val estado: StateFlow<AuthState> = _estado.asStateFlow()

    // ── ATALHOS DE CONVENIÊNCIA ───────────────────────────────────────────
    // Retornam null se não autenticado — use com ?: return para fail-fast.

    val token: String?
        get() = (_estado.value as? AuthState.Autenticado)?.token

    val userId: String?
        get() = (_estado.value as? AuthState.Autenticado)?.userId

    val perfil: PerfilUsuario?
        get() = (_estado.value as? AuthState.Autenticado)?.perfil

    val estaAutenticado: Boolean
        get() = _estado.value is AuthState.Autenticado

    // ── MUTAÇÕES (chamadas apenas pelo SupabaseClient) ────────────────────

    /**
     * Chamado após signIn ou signUp bem-sucedidos.
     * Atualiza o estado e garante que AppLogger registre o userId.
     */
    fun login(token: String, perfil: PerfilUsuario) {
        AppLogger.identificarUsuario(perfil.id)
        AppLogger.chave("user_tipo", perfil.tipo)
        _estado.value = AuthState.Autenticado(
            userId = perfil.id,
            token  = token,
            perfil = perfil,
        )
    }

    /**
     * Chamado após signOut ou quando o token expira.
     * Limpa estado e identidade no Crashlytics.
     */
    fun logout() {
        AppLogger.limparUsuario()
        _estado.value = AuthState.Deslogado
    }

    /**
     * Chamado na inicialização do app quando ainda não sabemos o estado.
     * O LaunchedEffect no MainActivity resolve para Deslogado se o DataStore
     * não encontrar sessão ativa.
     */
    fun marcarCarregando() {
        _estado.value = AuthState.Carregando
    }

    /**
     * Chamado quando o DataStore confirma que não há sessão persistida.
     * Evita que o app fique preso em estado Carregando indefinidamente.
     */
    fun marcarDeslogado() {
        _estado.value = AuthState.Deslogado
    }

    /**
     * Atualiza apenas o perfil (ex: após edição de nome/foto) sem
     * alterar token ou userId. Ignora chamadas se não estiver autenticado.
     */
    fun atualizarPerfil(novoPerfil: PerfilUsuario) {
        val atual = _estado.value as? AuthState.Autenticado ?: return
        _estado.value = atual.copy(perfil = novoPerfil)
    }
}