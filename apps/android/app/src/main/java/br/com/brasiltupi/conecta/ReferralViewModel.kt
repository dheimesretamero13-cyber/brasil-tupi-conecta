package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// ReferralViewModel.kt  · Fase 4.2
// ═══════════════════════════════════════════════════════════════════════════

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ReferralUiState(
    val carregando:       Boolean             = true,
    val codigo:           String              = "",
    val creditos:         List<CreditoUsuario> = emptyList(),
    val indicacoes:       List<ReferralInfo>   = emptyList(),
    val totalCreditos:    Double              = 0.0,
    val erro:             String?             = null,
)

sealed class AplicarCodigoState {
    object Idle                          : AplicarCodigoState()
    object Carregando                    : AplicarCodigoState()
    object Sucesso                       : AplicarCodigoState()
    data class Erro(val mensagem: String): AplicarCodigoState()
}

class ReferralViewModel(
    private val repository: ReferralRepository,
) : ViewModel() {

    private val _uiState         = MutableStateFlow(ReferralUiState())
    private val _aplicarState    = MutableStateFlow<AplicarCodigoState>(AplicarCodigoState.Idle)

    val uiState:      StateFlow<ReferralUiState>      = _uiState
    val aplicarState: StateFlow<AplicarCodigoState>   = _aplicarState

    init { carregarDados() }

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.value = ReferralUiState(carregando = true)
            try {
                val codigo     = repository.obterOuGerarCodigo() ?: ""
                val creditos   = repository.buscarCreditos()
                val indicacoes = repository.buscarIndicacoes()
                val total      = creditos.sumOf { it.amount }

                _uiState.value = ReferralUiState(
                    carregando    = false,
                    codigo        = codigo,
                    creditos      = creditos,
                    indicacoes    = indicacoes,
                    totalCreditos = total,
                )
            } catch (e: Exception) {
                _uiState.value = ReferralUiState(
                    carregando = false,
                    erro       = "Não foi possível carregar suas indicações.",
                )
            }
        }
    }

    fun aplicarCodigo(codigo: String) {
        if (codigo.isBlank()) return
        viewModelScope.launch {
            _aplicarState.value = AplicarCodigoState.Carregando
            val resultado = repository.aplicarCodigo(codigo.trim().uppercase())
            _aplicarState.value = when (resultado) {
                "ok"             -> AplicarCodigoState.Sucesso
                "codigo_invalido"-> AplicarCodigoState.Erro("Código inválido. Verifique e tente novamente.")
                "codigo_proprio" -> AplicarCodigoState.Erro("Você não pode usar seu próprio código.")
                "ja_indicado"    -> AplicarCodigoState.Erro("Você já usou um código de indicação.")
                "sessao_invalida"-> AplicarCodigoState.Erro("Sessão inválida. Faça login novamente.")
                "erro_rede"      -> AplicarCodigoState.Erro("Sem conexão. Tente novamente.")
                else             -> AplicarCodigoState.Erro("Erro inesperado. Tente novamente.")
            }
        }
    }

    fun resetarAplicar() {
        _aplicarState.value = AplicarCodigoState.Idle
    }
}

class ReferralViewModelFactory(
    private val repository: ReferralRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReferralViewModel(repository) as T
}