package br.com.brasiltupi.conecta

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════════════════════════
// PdfViewerViewModel.kt  · Fase 3.2 + 3.3
//
// Responsabilidades:
//  • Verificar PDF criptografado offline (BibliotecaRepository) antes da rede
//  • Baixar o PDF em memória (ByteArray) via URL assinada — NUNCA salvar em disco
//  • Renderizar páginas via PdfRenderer nativo do Android
//  • Expor lista de Bitmaps para a UI via StateFlow
//  • Expor flag allowScreenshot para a Activity aplicar FLAG_SECURE
//
// Ordem de resolução (offline-first):
//  1. BibliotecaRepository.lerPdfOffline() → arquivo criptografado local
//  2. ContentRepository.gerarUrlTemporariaPdf() → download da rede
// ═══════════════════════════════════════════════════════════════════════════

data class PdfViewerUiState(
    val carregando: Boolean      = true,
    val paginas: List<Bitmap>    = emptyList(),
    val totalPaginas: Int        = 0,
    val allowScreenshot: Boolean = true,
    val erro: String?            = null,
)

class PdfViewerViewModel(
    private val produtoId:      String,
    private val allowScreenshot: Boolean,
    private val repository:     ContentRepository,
    private val bibliotecaRepo: BibliotecaRepository? = null,  // null = sem suporte offline
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState

    init {
        carregarPdf()
    }

    fun carregarPdf() {
        viewModelScope.launch {
            _uiState.value = PdfViewerUiState(
                carregando      = true,
                allowScreenshot = allowScreenshot,
            )
            try {
                // 1. Offline-first: verificar arquivo criptografado local
                val bytes = withContext(Dispatchers.IO) {
                    bibliotecaRepo?.lerPdfOffline(produtoId) ?: baixarDaRede()
                }

                // 2. Renderizar páginas via PdfRenderer nativo
                val bitmaps = withContext(Dispatchers.IO) {
                    renderizarPaginas(bytes)
                }

                _uiState.value = PdfViewerUiState(
                    carregando      = false,
                    paginas         = bitmaps,
                    totalPaginas    = bitmaps.size,
                    allowScreenshot = allowScreenshot,
                )
            } catch (e: Exception) {
                AppLogger.erro("PdfViewerVM", "Falha ao carregar PDF produto=$produtoId", e)
                _uiState.value = PdfViewerUiState(
                    carregando      = false,
                    allowScreenshot = allowScreenshot,
                    erro            = "Não foi possível carregar o documento. Verifique sua conexão.",
                )
            }
        }
    }

    // ── Download da rede em memória ───────────────────────────────────────
    // Chamado apenas se não houver arquivo offline disponível.
    // HttpURLConnection puro — sem dependência externa.
    // Proibido usar File ou qualquer escrita em disco.
    private suspend fun baixarDaRede(): ByteArray {
        val url = repository.gerarUrlTemporariaPdf(produtoId)
        return withContext(Dispatchers.IO) { baixarEmMemoria(url) }
    }

    private fun baixarEmMemoria(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout    = 30_000
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode} ao baixar PDF")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    // ── Renderizar páginas via PdfRenderer nativo ─────────────────────────
    // ParcelFileDescriptor.createPipe() para compatibilidade com API 24+.
    // fromData() exigiria API 31+ — não compatível com minSdk 24 do projeto.
    private fun renderizarPaginas(bytes: ByteArray): List<Bitmap> {
        val pipe    = ParcelFileDescriptor.createPipe()
        val writeFd = pipe[1]
        val readFd  = pipe[0]

        Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { it.write(bytes) }
            } catch (_: Exception) {}
        }.start()

        val renderer = PdfRenderer(readFd)
        val bitmaps  = mutableListOf<Bitmap>()
        try {
            for (i in 0 until renderer.pageCount) {
                val page   = renderer.openPage(i)
                val largura = 1080
                val altura  = (largura.toFloat() / page.width * page.height).toInt()
                val bitmap  = android.graphics.Bitmap.createBitmap(
                    largura, altura, android.graphics.Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmaps.add(bitmap)
            }
        } finally {
            renderer.close()
        }
        return bitmaps
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.paginas.forEach { it.recycle() }
    }
}

// ── Factory manual — sem Hilt ─────────────────────────────────────────────
// Versão completa com suporte offline (Fase 3.3)
class PdfViewerViewModelFactory(
    private val produtoId:       String,
    private val allowScreenshot: Boolean,
    private val repository:      ContentRepository,
    private val bibliotecaRepo:  BibliotecaRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PdfViewerViewModel(produtoId, allowScreenshot, repository, bibliotecaRepo) as T
}