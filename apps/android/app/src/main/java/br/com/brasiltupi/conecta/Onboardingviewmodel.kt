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
// ═══════════════════════════════════════════════════════════════════════════

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── DataStore singleton (extensão no Context) ─────────────────────────────
// Uma única instância por processo — padrão recomendado pelo Jetpack.
val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onboarding_prefs"
)

// ── Keys ──────────────────────────────────────────────────────────────────
private object OnboardingKeys {
    val COMPLETED = booleanPreferencesKey("onboarding_completed")
    val USER_ROLE = stringPreferencesKey("user_role")
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
                !completed          -> OnboardingNavState.MostrarOnboarding
                role == "client"    -> OnboardingNavState.IrParaCliente
                role == "professional" -> OnboardingNavState.IrParaProfissional
                else                -> OnboardingNavState.MostrarOnboarding
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