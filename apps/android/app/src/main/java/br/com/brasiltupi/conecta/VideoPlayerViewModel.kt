package br.com.brasiltupi.conecta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// VideoPlayerViewModel.kt  · Fase 3.1
//
// Responsabilidades:
//  • Buscar URL temporária do Supabase Storage via ContentRepository
//  • Expor estado de UI (loading / pronto / erro)
//  • Salvar progresso (posicaoMs + percentualConcluido) no Postgres
//    — chamado pelo onDispose da tela e a cada 30s enquanto reproduz
//
// Sem Hilt: instanciado via VideoPlayerViewModelFactory
// ═══════════════════════════════════════════════════════════════════════════

data class VideoPlayerUiState(
    val carregando: Boolean       = true,
    val videoUrl: String          = "",
    val posicaoInicialMs: Long    = 0L,
    val erro: String?             = null,
)

class VideoPlayerViewModel(
    private val aulaId: String,
    private val cursoId: String,
    private val repository: ContentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState

    init {
        carregarAula()
    }

    private fun carregarAula() {
        viewModelScope.launch {
            _uiState.value = VideoPlayerUiState(carregando = true)
            try {
                // 1. URL temporária (expira em 1h — estratégia DRM leve validada pelo Gemini)
                val url = repository.gerarUrlTemporaria(aulaId)

                // 2. Progresso salvo anteriormente (retomar de onde parou)
                val progresso = repository.buscarProgresso(
                    aulaId  = aulaId,
                    cursoId = cursoId,
                )

                _uiState.value = VideoPlayerUiState(
                    carregando      = false,
                    videoUrl        = url,
                    posicaoInicialMs = progresso?.posicaoMs ?: 0L,
                )
            } catch (e: Exception) {
                AppLogger.erro("VideoPlayerVM", "Falha ao carregar aula=$aulaId", e)
                _uiState.value = VideoPlayerUiState(
                    carregando = false,
                    erro       = "Não foi possível carregar o vídeo. Verifique sua conexão.",
                )
            }
        }
    }

    // Chamado pelo onDispose e pelo ticker de 30s na tela
    fun salvarProgresso(posicaoMs: Long, duracaoMs: Long) {
        if (duracaoMs <= 0L) return
        val percentual = ((posicaoMs.toDouble() / duracaoMs) * 100).coerceIn(0.0, 100.0)
        viewModelScope.launch {
            try {
                repository.salvarProgresso(
                    aulaId             = aulaId,
                    cursoId            = cursoId,
                    posicaoMs          = posicaoMs,
                    percentualConcluido = percentual,
                )
            } catch (e: Exception) {
                // Falha silenciosa — não interrompe a reprodução
                AppLogger.aviso("VideoPlayerVM", "Falha ao salvar progresso: ${e.message}")
            }
        }
    }

    fun tentarNovamente() = carregarAula()
}

// ── Factory — injeção manual sem Hilt ────────────────────────────────────
class VideoPlayerViewModelFactory(
    private val aulaId: String,
    private val cursoId: String,
    private val repository: ContentRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VideoPlayerViewModel(aulaId, cursoId, repository) as T
}