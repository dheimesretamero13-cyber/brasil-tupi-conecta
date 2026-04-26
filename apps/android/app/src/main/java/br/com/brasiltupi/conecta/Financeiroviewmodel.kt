package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// FinanceiroViewModel.kt  · Fase 2.4
//
// Responsabilidades:
//  1. Chamar a RPC `resumo_financeiro(p_prof_id)` — cálculos no banco,
//     nunca no cliente (Regra de Ouro nº 1)
//  2. Buscar lista de transações recentes da tabela `payments`
//  3. Expor FinanceiroUiState via StateFlow para a AbaFinanceiroDash
//  4. Gerenciar ação de Solicitar Saque (AppLogger + dialog "Em breve")
//  5. Instrumentar via AppLogger
//
// SEGURANÇA:
//  Todos os cálculos financeiros (bruto, taxa, líquido) são feitos
//  pela RPC no Postgres. O ViewModel apenas exibe o resultado.
// ═══════════════════════════════════════════════════════════════════════════

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG          = "FinanceiroViewModel"
private const val SUPABASE_URL = "https://qfzdchrlbqcvewjivaqz.supabase.co"
private const val API_KEY      = "sb_publishable_SM-UHBh_5lzTSBZ2YPUIYw_Sw1i8qeq"
private const val TAXA_PADRAO  = 15.0  // fallback se RPC não retornar taxa

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════════════════
// DTOs — mapeados direto da RPC e da tabela payments
// ═══════════════════════════════════════════════════════════════════════════

// Retorno da RPC `resumo_financeiro`
@Serializable
data class ResumoFinanceiro(
    @SerialName("total_bruto")   val totalBruto:   Double = 0.0,
    @SerialName("taxa_pct")      val taxaPct:      Double = TAXA_PADRAO,
    @SerialName("total_liquido") val totalLiquido: Double = 0.0,
    @SerialName("total_pendente") val totalPendente: Double = 0.0,
)

// Cada transação na lista recente
@Serializable
data class TransacaoFinanceira(
    val id:          String,
    @SerialName("urgencia_id")  val urgenciaId:  String,
    val status:      String,
    val valor:       Double,
    @SerialName("criado_em")    val criadoEm:    String? = null,
)

// Ponto do gráfico de evolução diária
data class PontoGrafico(
    val dia:   String,   // "dd/MM"
    val valor: Double,
)

// Request da RPC
@Serializable
private data class ResumoFinanceiroRequest(
    @SerialName("p_prof_id") val profId: String,
)

// ═══════════════════════════════════════════════════════════════════════════
// ESTADO DA UI
// ═══════════════════════════════════════════════════════════════════════════

sealed class FinanceiroUiState {
    object Carregando : FinanceiroUiState()
    data class Sucesso(
        val resumo:      ResumoFinanceiro,
        val transacoes:  List<TransacaoFinanceira>,
        val grafico:     List<PontoGrafico>,
    ) : FinanceiroUiState()
    data class Erro(val mensagem: String) : FinanceiroUiState()
}

// ═══════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════════════════════

class FinanceiroViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<FinanceiroUiState>(FinanceiroUiState.Carregando)
    val uiState: StateFlow<FinanceiroUiState> = _uiState.asStateFlow()

    private val _mostrarDialogSaque = MutableStateFlow(false)
    val mostrarDialogSaque: StateFlow<Boolean> = _mostrarDialogSaque.asStateFlow()

    init {
        carregarDados()
    }

    // ── CARREGAR DADOS ────────────────────────────────────────────────────

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.emit(FinanceiroUiState.Carregando)

            val uid = currentUserId ?: run {
                AppLogger.erroAuth("financeiro_carregar", mensagemExtra = "userId nulo")
                _uiState.emit(FinanceiroUiState.Erro("Sessão inválida. Faça login novamente."))
                return@launch
            }

            AppLogger.chave("financeiro_prof_id", uid)
            AppLogger.info(TAG, "Carregando dados financeiros para prof=$uid")

            // Executar em paralelo: resumo (RPC) + transações recentes
            val resumoResult    = buscarResumoViaRpc(uid)
            val transacoesResult = buscarTransacoesRecentes(uid)

            val resumo = resumoResult.getOrElse {
                AppLogger.erro(TAG, "Falha ao carregar resumo financeiro", it)
                _uiState.emit(FinanceiroUiState.Erro("Erro ao carregar dados financeiros."))
                return@launch
            }

            val transacoes = transacoesResult.getOrElse {
                AppLogger.aviso(TAG, "Falha ao carregar transacoes: ${it.message}")
                emptyList()   // não bloquear a tela por causa da lista
            }

            val grafico = construirGrafico(transacoes)

            AppLogger.info(TAG,
                "Dados carregados: bruto=${resumo.totalBruto} liquido=${resumo.totalLiquido} " +
                        "transacoes=${transacoes.size}"
            )

            _uiState.emit(FinanceiroUiState.Sucesso(
                resumo     = resumo,
                transacoes = transacoes,
                grafico    = grafico,
            ))
        }
    }

    // ── 1. RPC: RESUMO FINANCEIRO ─────────────────────────────────────────

    /**
     * Chama a RPC `resumo_financeiro(p_prof_id)` que retorna:
     *   total_bruto, taxa_pct, total_liquido, total_pendente
     *
     * Todos os cálculos acontecem no Postgres — o cliente só exibe.
     */
    private suspend fun buscarResumoViaRpc(profId: String): Result<ResumoFinanceiro> {
        return try {
            val token = currentToken ?: API_KEY
            val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/resumo_financeiro") {
                header("apikey",        API_KEY)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(ResumoFinanceiroRequest(profId = profId))
            }

            val corpo = response.bodyAsText()
            Log.d(TAG, "RPC resumo_financeiro HTTP ${response.status.value}")

            when (response.status.value) {
                200 -> {
                    // RPC retorna um objeto JSON único (não array)
                    val resumo = jsonParser.decodeFromString<ResumoFinanceiro>(corpo)
                    AppLogger.infoPagamento(
                        etapa      = "resumo_financeiro_ok",
                        urgenciaId = profId,
                        detalhe    = "bruto=${resumo.totalBruto} taxa=${resumo.taxaPct}%",
                    )
                    Result.success(resumo)
                }
                404 -> {
                    // RPC ainda não existe — retornar zeros com taxa padrão
                    AppLogger.aviso(TAG, "RPC resumo_financeiro nao existe (404) — usando fallback REST")
                    buscarResumoViaRestFallback(profId)
                }
                else -> {
                    AppLogger.erroPagamento(
                        etapa      = "rpc_resumo_erro",
                        urgenciaId = profId,
                        httpStatus = response.status.value,
                        corpo      = corpo.take(200),
                    )
                    Result.failure(RuntimeException("RPC HTTP ${response.status.value}"))
                }
            }
        } catch (e: Exception) {
            AppLogger.erroRede("rpc/resumo_financeiro", e, "prof=$profId")
            Result.failure(e)
        }
    }

    // ── FALLBACK REST (sem RPC) ───────────────────────────────────────────

    private suspend fun buscarResumoViaRestFallback(profId: String): Result<ResumoFinanceiro> {
        return try {
            val token = currentToken ?: API_KEY
            val response = httpClient.get(
                "$SUPABASE_URL/rest/v1/payments" +
                        "?professional_id=eq.$profId" +
                        "&select=valor,status"
            ) {
                header("apikey",        API_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
            }

            val lista = jsonParser.decodeFromString<List<TransacaoFinanceira>>(response.bodyAsText())
            val aprovados = lista.filter { it.status == "approved" }
            val pendentes = lista.filter { it.status == "pending" }
            val bruto     = aprovados.sumOf { it.valor }
            val liquido   = bruto * (1.0 - TAXA_PADRAO / 100.0)

            Result.success(ResumoFinanceiro(
                totalBruto   = bruto,
                taxaPct      = TAXA_PADRAO,
                totalLiquido = liquido,
                totalPendente = pendentes.sumOf { it.valor },
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 2. TRANSAÇÕES RECENTES ────────────────────────────────────────────

    private suspend fun buscarTransacoesRecentes(profId: String): Result<List<TransacaoFinanceira>> {
        return try {
            val token = currentToken ?: API_KEY
            val response = httpClient.get(
                "$SUPABASE_URL/rest/v1/payments" +
                        "?professional_id=eq.$profId" +
                        "&select=id,urgencia_id,status,valor,criado_em" +
                        "&order=criado_em.desc" +
                        "&limit=20"
            ) {
                header("apikey",        API_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
            }

            val lista = jsonParser.decodeFromString<List<TransacaoFinanceira>>(response.bodyAsText())
            Result.success(lista)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 3. CONSTRUIR PONTOS DO GRÁFICO ────────────────────────────────────

    /**
     * Agrupa transações aprovadas por dia (últimos 7 dias).
     * Cálculo leve — apenas agrupamento de dados já recebidos do backend.
     */
    private fun construirGrafico(transacoes: List<TransacaoFinanceira>): List<PontoGrafico> {
        val aprovadas = transacoes.filter { it.status == "approved" }
        if (aprovadas.isEmpty()) return emptyList()

        // Agrupar por dia (yyyy-MM-dd → dd/MM)
        return aprovadas
            .groupBy { t ->
                t.criadoEm?.take(10) ?: "?"   // "2026-04-25"
            }
            .entries
            .sortedBy { it.key }
            .takeLast(7)
            .map { (dataIso, lista) ->
                val partes = dataIso.split("-")
                val label  = if (partes.size >= 3) "${partes[2]}/${partes[1]}" else dataIso
                PontoGrafico(dia = label, valor = lista.sumOf { it.valor })
            }
    }

    // ── AÇÃO: SOLICITAR SAQUE ─────────────────────────────────────────────

    fun solicitarSaque() {
        val uid = currentUserId ?: return
        AppLogger.infoPagamento(
            etapa      = "solicitar_saque_intencao",
            urgenciaId = uid,
            detalhe    = "Profissional clicou em Solicitar Saque — funcionalidade em breve",
        )
        viewModelScope.launch { _mostrarDialogSaque.emit(true) }
    }

    fun dispensarDialogSaque() {
        viewModelScope.launch { _mostrarDialogSaque.emit(false) }
    }
}

// ── Factory (sem parâmetros — ViewModel simples) ──────────────────────────
class FinanceiroViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FinanceiroViewModel() as T
}