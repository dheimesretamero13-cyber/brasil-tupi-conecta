package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// SearchViewModel.kt  · Fase 3.4
// ═══════════════════════════════════════════════════════════════════════════

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SearchUiState {
    object Idle                                      : SearchUiState()
    object Carregando                                : SearchUiState()
    data class Sucesso(val itens: List<ResultadoBusca>) : SearchUiState()
    data class Erro(val mensagem: String)            : SearchUiState()
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: SearchRepository,
) : ViewModel() {

    private val _filtro   = MutableStateFlow(FiltroBusca())
    private val _uiState  = MutableStateFlow<SearchUiState>(SearchUiState.Idle)

    val filtro:   StateFlow<FiltroBusca>   = _filtro.asStateFlow()
    val uiState:  StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // Debounce de 400ms no query — dispara busca automática ao digitar
        viewModelScope.launch {
            _filtro
                .debounce(400)
                .distinctUntilChanged()
                .collect { f ->
                    if (f.query.isBlank() && f == FiltroBusca()) {
                        _uiState.value = SearchUiState.Idle
                    } else {
                        executarBusca(f)
                    }
                }
        }
    }

    fun atualizarQuery(query: String) {
        _filtro.value = _filtro.value.copy(query = query)
    }

    fun atualizarTipo(tipo: String) {
        _filtro.value = _filtro.value.copy(tipo = tipo)
        buscarImediato()
    }

    fun atualizarPreco(min: Double?, max: Double?) {
        _filtro.value = _filtro.value.copy(precoMin = min, precoMax = max)
        buscarImediato()
    }

    fun atualizarAvaliacao(min: Double?) {
        _filtro.value = _filtro.value.copy(avaliacaoMin = min)
        buscarImediato()
    }

    fun atualizarOrdenacao(ord: OrdenacaoBusca) {
        _filtro.value = _filtro.value.copy(ordenacao = ord)
        buscarImediato()
    }

    fun limparFiltros() {
        _filtro.value = FiltroBusca()
        _uiState.value = SearchUiState.Idle
    }

    private fun buscarImediato() {
        viewModelScope.launch { executarBusca(_filtro.value) }
    }

    private suspend fun executarBusca(f: FiltroBusca) {
        _uiState.value = SearchUiState.Carregando
        val resultado = repository.buscar(f)
        _uiState.value = if (resultado.isEmpty() && f.query.isNotBlank()) {
            SearchUiState.Sucesso(emptyList())
        } else {
            SearchUiState.Sucesso(resultado)
        }
    }
}

class SearchViewModelFactory(
    private val repository: SearchRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SearchViewModel(repository) as T
}