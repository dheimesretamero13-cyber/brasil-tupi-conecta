package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// OnboardingViewModel.kt
//
// Responsabilidades:
//  • Ler/escrever DataStore: onboarding_completed e user_role
//  • Expor StateFlow<OnboardingNavState> para a UI reagir
//  • Nunca tocar em Composables — lógica pura de estado e persistência
//
// DataStore keys:
//  • ONBOARDING_COMPLETED : Boolean  (default false)
//  • USER_ROLE            : String?  (null | "client" | "professional")
//  • ULTIMO_ACESSO        : Long     (timestamp ms — Fase 4.5 retenção)
// ═══════════════════════════════════════════════════════════════════════════

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ── DataStore singleton (extensão no Context) ─────────────────────────────
// Uma única instância por processo — padrão recomendado pelo Jetpack.
val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onboarding_prefs"
)

// ── Keys ──────────────────────────────────────────────────────────────────
private object OnboardingKeys {
    val COMPLETED     = booleanPreferencesKey("onboarding_completed")
    val USER_ROLE     = stringPreferencesKey("user_role")
    val ULTIMO_ACESSO = longPreferencesKey("ultimo_acesso")   // Fase 4.5

    // Fase 5 — guards de eventos first_* (disparam uma única vez por usuário)
    val FIRST_BOOKING  = booleanPreferencesKey("analytics_first_booking")
    val FIRST_CALL     = booleanPreferencesKey("analytics_first_call")
    val FIRST_PAYMENT  = booleanPreferencesKey("analytics_first_payment")
}

// ═══════════════════════════════════════════════════════════════════════════
// ESTADO DE NAVEGAÇÃO — o que a UI observa
// ═══════════════════════════════════════════════════════════════════════════

sealed class OnboardingNavState {
    /** DataStore ainda sendo lido — exibir splash/loading */
    object Carregando : OnboardingNavState()

    /** Onboarding não concluído — exibir OnboardingScreen */
    object MostrarOnboarding : OnboardingNavState()

    /** Onboarding concluído como cliente — ir para busca */
    object IrParaCliente : OnboardingNavState()

    /** Onboarding concluído como profissional — ir para perfil/KYC */
    object IrParaProfissional : OnboardingNavState()
}

// ═══════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════════════════════

class OnboardingViewModel(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _navState = MutableStateFlow<OnboardingNavState>(OnboardingNavState.Carregando)
    val navState: StateFlow<OnboardingNavState> = _navState.asStateFlow()

    init {
        // Lê o DataStore uma única vez ao criar o ViewModel.
        // Se já concluído, redireciona direto para o fluxo correto.
        viewModelScope.launch {
            val prefs     = dataStore.data.first()
            val completed = prefs[OnboardingKeys.COMPLETED] ?: false
            val role      = prefs[OnboardingKeys.USER_ROLE]

            _navState.value = when {
                !completed             -> OnboardingNavState.MostrarOnboarding
                role == "client"       -> OnboardingNavState.IrParaCliente
                role == "professional" -> OnboardingNavState.IrParaProfissional
                else                   -> OnboardingNavState.MostrarOnboarding
            }
        }
    }

    // ── Chamado quando o usuário escolhe "Sou Cliente" na Tela 4 ─────────
    fun selecionarCliente() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[OnboardingKeys.COMPLETED] = true
                prefs[OnboardingKeys.USER_ROLE] = "client"
            }
            _navState.value = OnboardingNavState.IrParaCliente
        }
    }

    // ── Chamado quando o usuário escolhe "Sou Profissional" na Tela 4 ────
    fun selecionarProfissional() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[OnboardingKeys.COMPLETED] = true
                prefs[OnboardingKeys.USER_ROLE] = "professional"
            }
            _navState.value = OnboardingNavState.IrParaProfissional
        }
    }

    // ── Limpar (para logout ou troca de conta) ────────────────────────────
    fun resetOnboarding() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[OnboardingKeys.COMPLETED] = false
                prefs.remove(OnboardingKeys.USER_ROLE)
            }
            _navState.value = OnboardingNavState.MostrarOnboarding
        }
    }

    // ── Fase 4.5 — Retenção ───────────────────────────────────────────────

    /** Retorna o timestamp (ms) do último acesso. 0L se nunca gravado. */
    suspend fun ultimoAcesso(): Long =
        dataStore.data.map { prefs ->
            prefs[OnboardingKeys.ULTIMO_ACESSO] ?: 0L
        }.first()

    /** Grava o timestamp do acesso atual. */
    suspend fun salvarUltimoAcesso(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[OnboardingKeys.ULTIMO_ACESSO] = timestamp
        }
    }

    // ── Fase 5 — Guards de eventos first_* ───────────────────────────────
    // Cada método lê a flag, retorna false se já disparou, grava true e
    // retorna true se for a primeira vez.
    // Thread-safe — DataStore.edit() é atômico e serializado.
    //
    // Padrão de uso nas telas:
    //   val onboardingVm = ... (passado via parâmetro ou hoist)
    //   if (onboardingVm.registrarPrimeiroBooking()) {
    //       AnalyticsTracker.firstBooking(tipo, valor)
    //   }

    /** Retorna true apenas na primeira chamada por instalação. */
    suspend fun registrarPrimeiroBooking(): Boolean {
        val prefs = dataStore.data.first()
        if (prefs[OnboardingKeys.FIRST_BOOKING] == true) return false
        dataStore.edit { it[OnboardingKeys.FIRST_BOOKING] = true }
        return true
    }

    /** Retorna true apenas na primeira chamada por instalação. */
    suspend fun registrarPrimeiraChamada(): Boolean {
        val prefs = dataStore.data.first()
        if (prefs[OnboardingKeys.FIRST_CALL] == true) return false
        dataStore.edit { it[OnboardingKeys.FIRST_CALL] = true }
        return true
    }

    /** Retorna true apenas na primeira chamada por instalação. */
    suspend fun registrarPrimeiroPagamento(): Boolean {
        val prefs = dataStore.data.first()
        if (prefs[OnboardingKeys.FIRST_PAYMENT] == true) return false
        dataStore.edit { it[OnboardingKeys.FIRST_PAYMENT] = true }
        return true
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FACTORY — necessária porque o ViewModel recebe DataStore no construtor,
// e o Compose não consegue instanciá-lo com viewModel() sem factory.
// ═══════════════════════════════════════════════════════════════════════════

class OnboardingViewModelFactory(
    private val dataStore: DataStore<Preferences>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OnboardingViewModel(dataStore) as T
    }
}