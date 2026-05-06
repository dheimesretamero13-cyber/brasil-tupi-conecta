package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// DisputaViewModel.kt  · Fase 4.3
// ═══════════════════════════════════════════════════════════════════════════

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DisputaListaState {
    object Carregando                              : DisputaListaState()
    data class Sucesso(val disputas: List<Disputa>): DisputaListaState()
    data class Erro(val mensagem: String)          : DisputaListaState()
}

sealed class AbrirDisputaState {
    object Idle                          : AbrirDisputaState()
    object Enviando                      : AbrirDisputaState()
    data class Sucesso(val disputa: Disputa) : AbrirDisputaState()
    data class Erro(val mensagem: String): AbrirDisputaState()
}

class DisputaViewModel(
    private val repository: DisputaRepository,
) : ViewModel() {

    private val _listaState  = MutableStateFlow<DisputaListaState>(DisputaListaState.Carregando)
    private val _abrirState  = MutableStateFlow<AbrirDisputaState>(AbrirDisputaState.Idle)

    val listaState:  StateFlow<DisputaListaState> = _listaState
    val abrirState:  StateFlow<AbrirDisputaState> = _abrirState

    init { carregarDisputas() }

    fun carregarDisputas() {
        viewModelScope.launch {
            _listaState.value = DisputaListaState.Carregando
            val lista = repository.buscarDisputas()
            _listaState.value = DisputaListaState.Sucesso(lista)
        }
    }

    fun abrirDisputa(
        agendamentoId: String?,
        categoria:     CategoriaDisputa,
        descricao:     String,
    ) {
        if (descricao.length < 20) {
            _abrirState.value = AbrirDisputaState.Erro(
                "Descreva o problema com pelo menos 20 caracteres."
            )
            return
        }
        viewModelScope.launch {
            _abrirState.value = AbrirDisputaState.Enviando
            val resultado = repository.abrirDisputa(agendamentoId, categoria, descricao)
            _abrirState.value = resultado.fold(
                onSuccess = { AbrirDisputaState.Sucesso(it) },
                onFailure = { AbrirDisputaState.Erro("Falha ao abrir disputa. Tente novamente.") },
            )
            if (resultado.isSuccess) carregarDisputas()
        }
    }

    fun resetarAbrirState() {
        _abrirState.value = AbrirDisputaState.Idle
    }
}

class DisputaViewModelFactory(
    private val repository: DisputaRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DisputaViewModel(repository) as T
}