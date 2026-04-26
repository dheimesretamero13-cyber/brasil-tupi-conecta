package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// RelatoriosViewModel.kt  · Fase 4.4
// ═══════════════════════════════════════════════════════════════════════════

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class RelatoriosUiState {
    object Carregando : RelatoriosUiState()
    data class Sucesso(
        val semanas:   List<StatsSemanal>,
        val horarios:  List<HorarioPico>,
        val resumo:    ResumoRelatorio,
    ) : RelatoriosUiState()
    data class Erro(val mensagem: String) : RelatoriosUiState()
    object SemDados : RelatoriosUiState()
}

data class ResumoRelatorio(
    val totalGanhoMes:       Double = 0.0,
    val totalAtendMes:       Int    = 0,
    val notaMediaGeral:      Double = 0.0,
    val taxaConversaoMedia:  Double = 0.0,
    val tempoMedioResposta:  Double = 0.0,
)

class RelatoriosViewModel(
    private val repository: RelatoriosRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RelatoriosUiState>(RelatoriosUiState.Carregando)
    val uiState: StateFlow<RelatoriosUiState> = _uiState

    init { carregarRelatorios() }

    fun carregarRelatorios() {
        viewModelScope.launch {
            _uiState.value = RelatoriosUiState.Carregando
            try {
                val semanas = repository.buscarStats(limite = 8)
                if (semanas.isEmpty()) {
                    _uiState.value = RelatoriosUiState.SemDados
                    return@launch
                }

                // Horários de pico da semana mais recente
                val horarios = repository.buscarHorariosPico(semanas.last().id)

                // Resumo das últimas 4 semanas (~1 mês)
                val ultimas4 = semanas.takeLast(4)
                val resumo = ResumoRelatorio(
                    totalGanhoMes      = ultimas4.sumOf { it.totalGanho },
                    totalAtendMes      = ultimas4.sumOf { it.totalAtendimentos },
                    notaMediaGeral     = ultimas4.map { it.notaMedia }.filter { it > 0 }
                        .average().takeIf { !it.isNaN() } ?: 0.0,
                    taxaConversaoMedia = ultimas4.map { it.taxaConversao }
                        .average().takeIf { !it.isNaN() } ?: 0.0,
                    tempoMedioResposta = ultimas4.map { it.tempoMedioResposta }.filter { it > 0 }
                        .average().takeIf { !it.isNaN() } ?: 0.0,
                )

                _uiState.value = RelatoriosUiState.Sucesso(semanas, horarios, resumo)
            } catch (e: Exception) {
                AppLogger.erro("RelatoriosVM", "Falha ao carregar relatórios", e)
                _uiState.value = RelatoriosUiState.Erro(
                    "Não foi possível carregar os relatórios."
                )
            }
        }
    }
}

class RelatoriosViewModelFactory(
    private val repository: RelatoriosRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RelatoriosViewModel(repository) as T
}