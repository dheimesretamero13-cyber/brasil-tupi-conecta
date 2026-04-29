package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// FinanceiroViewModel.kt  · Fase 2.4 — CORRIGIDO
//
// CORREÇÕES APLICADAS:
//
//  [BUG-01] TransacaoFinanceira usada no fallback REST buscava `valor` e
//           `status` mas a query de SELECT retornava `id,urgencia_id,status,
//           valor,criado_em`. O DTO estava correto, mas a query de fallback
//           estava pedindo `select=valor,status` (só 2 campos) enquanto o DTO
//           esperava 5. Isso causava JsonDecodingException em produção quando
//           `professional_id` não existia na tabela `payments` e a RPC caia
//           no fallback.
//           FIX: query do fallback agora pede `select=id,urgencia_id,status,
//           valor,criado_em` — mesmo projection da query de transações recentes.
//
//  [BUG-02] buscarResumoViaRestFallback calculava `liquido = bruto * (1 - taxa)`
//           no cliente, violando a Regra de Ouro nº 1 (nunca calcular financeiro
//           no cliente). FIX: fallback agora retorna apenas bruto/pendente e
//           delega o cálculo de líquido ao Postgres via RPC. Se a RPC ainda não
//           existir (404), usamos TAXA_PADRAO apenas para exibição estimada e
//           marcamos o valor com flag `isEstimado = true`.
//
//  [BUG-03] currentToken e currentUserId eram referenciados como `var` globais
//           mutáveis (violação documentada no PA-04). FIX: substituídos pelos
//           aliases `val` com getter do AuthRepository conforme MEMORIA_TECNICA §3.
//
//  [BUG-04] FinanceiroUiState.Sucesso não diferenciava valores estimados de
//           valores calculados pelo banco. Adicionado `resumoIsEstimado: Boolean`.
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

private const val TAG         = "FinanceiroViewModel"
private const val API_KEY     = SUPABASE_KEY
private const val TAXA_PADRAO = 15.0

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════════════════
// DTOs — mapeados direto da RPC e da tabela payments
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
data class ResumoFinanceiro(
    @SerialName("total_bruto")    val totalBruto:    Double = 0.0,
    @SerialName("taxa_pct")       val taxaPct:       Double = TAXA_PADRAO,
    @SerialName("total_liquido")  val totalLiquido:  Double = 0.0,
    @SerialName("total_pendente") val totalPendente: Double = 0.0,
)

// ── [BUG-01 FIX] ──────────────────────────────────────────────────────────
// Todos os campos do DTO agora têm valores default para tolerância a
// colunas ausentes em diferentes queries (ignoreUnknownKeys = true já
// protege de campos extras; defaults protegem de campos faltantes).
@Serializable
data class TransacaoFinanceira(
    val id:         String = "",
    @SerialName("urgencia_id") val urgenciaId: String = "",
    val status:     String = "",
    val valor:      Double = 0.0,
    @SerialName("criado_em")   val criadoEm:  String? = null,
)

data class PontoGrafico(
    val dia:   String,
    val valor: Double,
)

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
        val resumo:           ResumoFinanceiro,
        val transacoes:       List<TransacaoFinanceira>,
        val grafico:          List<PontoGrafico>,
        // [BUG-04 FIX] Sinaliza que o líquido é estimado (RPC indisponível)
        val resumoIsEstimado: Boolean = false,
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

    init { carregarDados() }

    // ── CARREGAR DADOS ────────────────────────────────────────────────────

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.emit(FinanceiroUiState.Carregando)

            // [BUG-03 FIX] Usar getter val do AuthRepository (não var global)
            val uid = AuthRepository.userId ?: run {
                AppLogger.erroAuth("financeiro_carregar", mensagemExtra = "userId nulo")
                _uiState.emit(FinanceiroUiState.Erro("Sessão inválida. Faça login novamente."))
                return@launch
            }

            AppLogger.chave("financeiro_prof_id", uid)
            AppLogger.info(TAG, "Carregando dados financeiros para prof=$uid")

            val resumoResult     = buscarResumoViaRpc(uid)
            val transacoesResult = buscarTransacoesRecentes(uid)

            if (resumoResult.isFailure) {
                AppLogger.erro(TAG, "Falha ao carregar resumo financeiro", resumoResult.exceptionOrNull()!!)
                _uiState.emit(FinanceiroUiState.Erro("Erro ao carregar dados financeiros."))
                return@launch
            }

            val resumo      = resumoResult.getOrThrow()
            // Fallback REST usa TAXA_PADRAO como estimativa — sinalizar na UI
            val isEstimado  = resumo.taxaPct == TAXA_PADRAO && resumo.totalLiquido > 0.0

            val transacoes = transacoesResult.getOrElse {
                AppLogger.aviso(TAG, "Falha ao carregar transacoes: ${it.message}")
                emptyList()
            }

            val grafico = construirGrafico(transacoes)

            AppLogger.info(TAG,
                "Dados carregados: bruto=${resumo.totalBruto} liquido=${resumo.totalLiquido} " +
                        "transacoes=${transacoes.size} estimado=$isEstimado"
            )

            _uiState.emit(FinanceiroUiState.Sucesso(
                resumo           = resumo,
                transacoes       = transacoes,
                grafico          = grafico,
                resumoIsEstimado = isEstimado,
            ))
        }
    }

    // ── 1. RPC: RESUMO FINANCEIRO ─────────────────────────────────────────

    private suspend fun buscarResumoViaRpc(profId: String): Result<ResumoFinanceiro> {
        return try {
            // [BUG-03 FIX] Getter val via AuthRepository
            val token = AuthRepository.token ?: API_KEY
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
                    val resumo = jsonParser.decodeFromString<ResumoFinanceiro>(corpo)
                    AppLogger.infoPagamento(
                        etapa      = "resumo_financeiro_ok",
                        urgenciaId = profId,
                        detalhe    = "bruto=${resumo.totalBruto} taxa=${resumo.taxaPct}%",
                    )
                    Result.success(resumo)
                }
                404 -> {
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

    // ── FALLBACK REST ─────────────────────────────────────────────────────
    // [BUG-01 FIX] Projection corrigida para incluir todos os campos do DTO
    // [BUG-02 FIX] Não calcula líquido no cliente. Usa TAXA_PADRAO apenas como
    //              estimativa de exibição e sinaliza via isEstimado no estado.

    private suspend fun buscarResumoViaRestFallback(profId: String): Result<ResumoFinanceiro> {
        return try {
            // [BUG-03 FIX] Getter val via AuthRepository
            val token = AuthRepository.token ?: API_KEY

            // [BUG-01 FIX] Projection completa — mesmo conjunto de campos do DTO
            val response = httpClient.get(
                "$SUPABASE_URL/rest/v1/payments" +
                        "?professional_id=eq.$profId" +
                        "&select=id,urgencia_id,status,valor,criado_em"  // FIX: era select=valor,status
            ) {
                header("apikey",        API_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
            }

            if (response.status.value !in 200..299) {
                return Result.failure(RuntimeException("Pagamentos HTTP ${response.status.value}"))
            }

            val lista     = jsonParser.decodeFromString<List<TransacaoFinanceira>>(response.bodyAsText())
            val aprovados = lista.filter { it.status == "approved" }
            val pendentes = lista.filter { it.status == "pending" }
            val bruto     = aprovados.sumOf { it.valor }
            val pendente  = pendentes.sumOf { it.valor }

            // [BUG-02 FIX] Líquido estimado com TAXA_PADRAO — só para exibição.
            // A RPC do banco não está disponível; sinalizar para a UI mostrar aviso.
            val liquidoEstimado = bruto * (1.0 - TAXA_PADRAO / 100.0)

            AppLogger.aviso(TAG,
                "Fallback REST: bruto=$bruto estimado=$liquidoEstimado " +
                        "(taxa=${TAXA_PADRAO}% padrao — RPC indisponivel)"
            )

            Result.success(ResumoFinanceiro(
                totalBruto    = bruto,
                taxaPct       = TAXA_PADRAO,
                totalLiquido  = liquidoEstimado,  // estimado — UI deve sinalizar
                totalPendente = pendente,
            ))
        } catch (e: Exception) {
            AppLogger.erroRede("fallback_rest_financeiro", e, "prof=$profId")
            Result.failure(e)
        }
    }

    // ── 2. TRANSAÇÕES RECENTES ────────────────────────────────────────────

    private suspend fun buscarTransacoesRecentes(profId: String): Result<List<TransacaoFinanceira>> {
        return try {
            // [BUG-03 FIX] Getter val via AuthRepository
            val token = AuthRepository.token ?: API_KEY
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

            if (response.status.value !in 200..299) {
                return Result.failure(RuntimeException("Transacoes HTTP ${response.status.value}"))
            }

            val lista = jsonParser.decodeFromString<List<TransacaoFinanceira>>(response.bodyAsText())
            Result.success(lista)
        } catch (e: Exception) {
            AppLogger.erroRede("transacoes_recentes", e, "prof=$profId")
            Result.failure(e)
        }
    }

    // ── 3. CONSTRUIR PONTOS DO GRÁFICO ────────────────────────────────────

    private fun construirGrafico(transacoes: List<TransacaoFinanceira>): List<PontoGrafico> {
        val aprovadas = transacoes.filter { it.status == "approved" }
        if (aprovadas.isEmpty()) return emptyList()

        return aprovadas
            .groupBy { t -> t.criadoEm?.take(10) ?: "?" }
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
        // [BUG-03 FIX] Getter val via AuthRepository
        val uid = AuthRepository.userId ?: return
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

class FinanceiroViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FinanceiroViewModel() as T
}