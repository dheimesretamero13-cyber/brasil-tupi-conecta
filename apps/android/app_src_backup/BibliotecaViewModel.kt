package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// BibliotecaViewModel.kt  · Fase 3.3
// ═══════════════════════════════════════════════════════════════════════════

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class BibliotecaUiState {
    object Carregando : BibliotecaUiState()
    data class Sucesso(
        val cursos:   List<CursoComprado>,
        val produtos: List<ProdutoComprado>,
    ) : BibliotecaUiState()
    data class Erro(val mensagem: String) : BibliotecaUiState()
}

sealed class DownloadState {
    object Idle                          : DownloadState()
    data class Progresso(val pct: Float) : DownloadState()
    object Concluido                     : DownloadState()
    data class Erro(val mensagem: String): DownloadState()
}

class BibliotecaViewModel(
    private val repository: BibliotecaRepository,
    private val contentRepository: ContentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BibliotecaUiState>(BibliotecaUiState.Carregando)
    val uiState: StateFlow<BibliotecaUiState> = _uiState

    // Download state por produtoId
    private val _downloadState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadState: StateFlow<Map<String, DownloadState>> = _downloadState

    init { carregarBiblioteca() }

    fun carregarBiblioteca() {
        viewModelScope.launch {
            _uiState.value = BibliotecaUiState.Carregando
            try {
                val (cursos, produtos) = repository.buscarCompras()
                _uiState.value = BibliotecaUiState.Sucesso(cursos, produtos)
            } catch (e: Exception) {
                AppLogger.erro("BibliotecaVM", "Falha ao carregar biblioteca", e)
                _uiState.value = BibliotecaUiState.Erro(
                    "Não foi possível carregar sua biblioteca. Verifique sua conexão."
                )
            }
        }
    }

    fun baixarPdfOffline(produtoId: String) {
        viewModelScope.launch {
            // Evitar download duplo
            if (_downloadState.value[produtoId] is DownloadState.Progresso) return@launch

            atualizarDownload(produtoId, DownloadState.Progresso(0f))
            try {
                val url = contentRepository.gerarUrlTemporariaPdf(produtoId)
                val resultado = repository.downloadPdfOffline(
                    produtoId  = produtoId,
                    signedUrl  = url,
                    onProgress = { pct -> atualizarDownload(produtoId, DownloadState.Progresso(pct)) },
                )
                if (resultado.isSuccess) {
                    atualizarDownload(produtoId, DownloadState.Concluido)
                    // Recarregar para atualizar flag disponivelOffline
                    carregarBiblioteca()
                } else {
                    atualizarDownload(produtoId, DownloadState.Erro("Falha no download."))
                }
            } catch (e: Exception) {
                AppLogger.erro("BibliotecaVM", "Erro ao baixar PDF offline=$produtoId", e)
                atualizarDownload(produtoId, DownloadState.Erro("Erro ao baixar o arquivo."))
            }
        }
    }

    fun removerOffline(produtoId: String) {
        repository.removerPdfOffline(produtoId)
        carregarBiblioteca()
    }

    private fun atualizarDownload(produtoId: String, estado: DownloadState) {
        _downloadState.value = _downloadState.value + (produtoId to estado)
    }
}

class BibliotecaViewModelFactory(
    private val repository: BibliotecaRepository,
    private val contentRepository: ContentRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        BibliotecaViewModel(repository, contentRepository) as T
}