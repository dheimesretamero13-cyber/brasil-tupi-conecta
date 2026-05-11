package br.com.brasiltupi.conecta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

private const val TAG         = "FinanceiroViewModel"
private const val TAXA_PADRAO = 15.0

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════ DTOs ═══════════════════════

@Serializable
data class ResumoFinanceiro(
    @SerialName("total_bruto")    val totalBruto:    Double = 0.0,
    @SerialName("taxa_pct")       val taxaPct:       Double = TAXA_PADRAO,
    @SerialName("total_liquido")  val totalLiquido:  Double = 0.0,
    @SerialName("total_pendente") val totalPendente: Double = 0.0,
)

@Serializable
data class TransacaoFinanceira(
    val id:         String = "",
    @SerialName("urgencia_id") val urgenciaId: String = "",
    val status:     String = "",
    val valor:      Double = 0.0,
    @SerialName("criado_em")   val criadoEm:  String? = null,
)

@Serializable
private data class ValorResponse(val valor: Double = 0.0)

data class PontoGrafico(val dia: String, val valor: Double)

@Serializable
data class ResumoFinanceiroRequest(@SerialName("p_prof_id") val profId: String)

// NOVO: DTO para informações de bloqueio de saque
data class BloqueioSaqueInfo(
    val bloqueado: Boolean = false,
    val totalPendente: Double = 0.0,
    val totalVencido30d: Double = 0.0,
    val mensagem: String = "",
)
// ═══════════════════════ UI State ═══════════════════════

sealed class FinanceiroUiState {
    object Carregando : FinanceiroUiState()
    data class Sucesso(
        val resumo:           ResumoFinanceiro,
        val transacoes:       List<TransacaoFinanceira>,
        val grafico:          List<PontoGrafico>,
        val resumoIsEstimado: Boolean = false,
    ) : FinanceiroUiState()
    data class Erro(val mensagem: String) : FinanceiroUiState()
}

// ═══════════════════════ VIEWMODEL ═══════════════════════

class FinanceiroViewModel(private val isPmp: Boolean) : ViewModel() {

    private val _uiState = MutableStateFlow<FinanceiroUiState>(FinanceiroUiState.Carregando)
    val uiState: StateFlow<FinanceiroUiState> = _uiState.asStateFlow()

    private val _mostrarDialogSaque = MutableStateFlow(false)
    val mostrarDialogSaque: StateFlow<Boolean> = _mostrarDialogSaque.asStateFlow()

    private val _saldoDisponivelSaque = MutableStateFlow(0.0)
    val saldoDisponivelSaque: StateFlow<Double> = _saldoDisponivelSaque.asStateFlow()

    private val _bloqueioSaque = MutableStateFlow<BloqueioSaqueInfo?>(null)
    val bloqueioSaque: StateFlow<BloqueioSaqueInfo?> = _bloqueioSaque.asStateFlow()

    init {
        carregarDados()
    }

    fun carregarDados() {
        viewModelScope.launch {
            _uiState.emit(FinanceiroUiState.Carregando)

            val uid = AuthRepository.userId ?: run {
                AppLogger.erroAuth("financeiro_carregar", mensagemExtra = "userId nulo")
                _uiState.emit(FinanceiroUiState.Erro("Sessão inválida. Faça login novamente."))
                return@launch
            }

            AppLogger.chave("financeiro_prof_id", uid)
            AppLogger.info(TAG, "Carregando dados financeiros para prof=$uid")

            val resumoResult = buscarResumoViaRpc(uid)
            val transacoesResult = buscarTransacoesRecentes(uid)

            if (resumoResult.isFailure) {
                AppLogger.erro(TAG, "Falha ao carregar resumo financeiro", resumoResult.exceptionOrNull()!!)
                _uiState.emit(FinanceiroUiState.Erro("Erro ao carregar dados financeiros."))
                return@launch
            }

            val resumo = resumoResult.getOrThrow()
            val isEstimado = resumo.taxaPct == TAXA_PADRAO && resumo.totalLiquido > 0.0

            val transacoes = transacoesResult.getOrElse {
                AppLogger.aviso(TAG, "Falha ao carregar transacoes: ${it.message}")
                emptyList()
            }

            val grafico = construirGrafico(transacoes)

            AppLogger.info(TAG,
                "Dados carregados: bruto=${resumo.totalBruto} liquido=${resumo.totalLiquido} transacoes=${transacoes.size} estimado=$isEstimado"
            )

            _uiState.emit(FinanceiroUiState.Sucesso(
                resumo = resumo,
                transacoes = transacoes,
                grafico = grafico,
                resumoIsEstimado = isEstimado,
            ))
        }
    }

    private suspend fun buscarResumoViaRpc(profId: String): Result<ResumoFinanceiro> {
        return try {
            val token = AuthRepository.token ?: LOCAL_KEY
            val response = httpClient.post("$LOCAL_URL/rest/v1/rpc/resumo_financeiro") {
                header("apikey", LOCAL_KEY)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(ResumoFinanceiroRequest(profId = profId))
            }

            val corpo = response.bodyAsText()
            AppLogger.info(TAG, "RPC resumo_financeiro HTTP ${response.status.value}")

            when (response.status.value) {
                200 -> {
                    val lista = jsonParser.decodeFromString<List<ResumoFinanceiro>>(corpo)
                    val resumoBruto = lista.first()

                    val taxaCorreta = if (isPmp) 10.0 else 30.0
                    val liquidoCorreto = resumoBruto.totalBruto * (1.0 - taxaCorreta / 100.0)
                    val resumoAjustado = resumoBruto.copy(
                        taxaPct = taxaCorreta,
                        totalLiquido = liquidoCorreto,
                    )

                    AppLogger.infoPagamento(
                        etapa = "resumo_financeiro_ok",
                        urgenciaId = profId,
                        detalhe = "bruto=${resumoAjustado.totalBruto} taxa=${resumoAjustado.taxaPct}%",
                    )
                    Result.success(resumoAjustado)
                }
                404 -> {
                    AppLogger.aviso(TAG, "RPC resumo_financeiro nao existe (404) — usando fallback REST")
                    buscarResumoViaRestFallback(profId)
                }
                else -> {
                    AppLogger.erroPagamento(
                        etapa = "rpc_resumo_erro",
                        urgenciaId = profId,
                        httpStatus = response.status.value,
                        corpo = corpo.take(200),
                    )
                    Result.failure(RuntimeException("RPC HTTP ${response.status.value}"))
                }
            }
        } catch (e: Exception) {
            AppLogger.erroRede("rpc/resumo_financeiro", e, "prof=$profId")
            Result.failure(e)
        }
    }

    private suspend fun buscarResumoViaRestFallback(profId: String): Result<ResumoFinanceiro> {
        return try {
            val token = AuthRepository.token ?: LOCAL_KEY

            val response = httpClient.get(
                "$LOCAL_URL/rest/v1/payments" +
                        "?professional_id=eq.$profId" +
                        "&select=id,urgencia_id,status,valor,criado_em"
            ) {
                header("apikey", LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
            }

            if (response.status.value !in 200..299) {
                return Result.failure(RuntimeException("Pagamentos HTTP ${response.status.value}"))
            }

            val lista = jsonParser.decodeFromString<List<TransacaoFinanceira>>(response.bodyAsText())
            val aprovados = lista.filter { it.status == "approved" }
            val pendentes = lista.filter { it.status == "pending" }
            val bruto = aprovados.sumOf { it.valor }
            val pendente = pendentes.sumOf { it.valor }

            val taxaPct = if (isPmp) 10.0 else 30.0
            val liquido = bruto * (1.0 - taxaPct / 100.0)

            AppLogger.aviso(TAG,
                "Fallback REST: bruto=$bruto liquido=$liquido (taxa=${taxaPct}%, isPmp=$isPmp)"
            )

            Result.success(ResumoFinanceiro(
                totalBruto = bruto,
                taxaPct = taxaPct,
                totalLiquido = liquido,
                totalPendente = pendente,
            ))
        } catch (e: Exception) {
            AppLogger.erroRede("fallback_rest_financeiro", e, "prof=$profId")
            Result.failure(e)
        }
    }

    private suspend fun buscarTransacoesRecentes(profId: String): Result<List<TransacaoFinanceira>> {
        return try {
            val token = AuthRepository.token ?: LOCAL_KEY
            val response = httpClient.get(
                "$LOCAL_URL/rest/v1/payments" +
                        "?professional_id=eq.$profId" +
                        "&select=id,urgencia_id,status,valor,criado_em" +
                        "&order=criado_em.desc" +
                        "&limit=20"
            ) {
                header("apikey", LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
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
                val label = if (partes.size >= 3) "${partes[2]}/${partes[1]}" else dataIso
                PontoGrafico(dia = label, valor = lista.sumOf { it.valor })
            }
    }

    // ── AÇÃO: SOLICITAR SAQUE ─────────────────────────────────────────

    fun solicitarSaque() {
        viewModelScope.launch {
            val uid = AuthRepository.userId ?: return@launch
            AppLogger.infoPagamento(
                etapa = "solicitar_saque",
                urgenciaId = uid,
                detalhe = "Consultando saldo disponível (todos os pagamentos aprovados > 15 dias)",
            )

            _saldoDisponivelSaque.emit(0.0)
            _mostrarDialogSaque.emit(true)

            try {
                val token = AuthRepository.token ?: BuildConfig.SUPABASE_KEY
                val quinzeDiasAtras = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(System.currentTimeMillis() - 15L * 24 * 60 * 60 * 1000))

                val response = httpClient.get(
                    "${BuildConfig.SUPABASE_URL}/rest/v1/payments" +
                            "?professional_id=eq.$uid" +
                            "&status=eq.approved" +
                            "&criado_em=lte.$quinzeDiasAtras" +
                            "&select=valor"
                ) {
                    header("apikey", BuildConfig.SUPABASE_KEY)
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                }

                if (response.status.value in 200..299) {
                    // Usa DTO ValorResponse para evitar Map<String, Double>
                    val valores = jsonParser.decodeFromString<List<ValorResponse>>(response.bodyAsText())
                    val totalBruto = valores.sumOf { it.valor }

                    // NOVO: Verificar débitos e bloqueio de saque via RPC
                    val debitos = AtendimentosRepository.verificarDebitosProfissional(uid)
                    val saldoLiquido = totalBruto - debitos.totalPendente

                    _saldoDisponivelSaque.emit(saldoLiquido.coerceAtLeast(0.0))

                    // Emitir estado de bloqueio para a UI
                    _bloqueioSaque.emit(BloqueioSaqueInfo(
                        bloqueado = debitos.bloqueadoSaque,
                        totalPendente = debitos.totalPendente,
                        totalVencido30d = debitos.totalVencido30d,
                        mensagem = when {
                            debitos.bloqueadoSaque && debitos.totalVencido30d > 0 ->
                                "Saque bloqueado: você tem R$ ${"%.2f".format(debitos.totalVencido30d)} em débitos vencidos há mais de 30 dias. Regularize para liberar o saque."
                            debitos.totalPendente > 0 ->
                                "Você tem R$ ${"%.2f".format(debitos.totalPendente)} em débitos pendentes. Após 30 dias, o saque será bloqueado."
                            else -> ""
                        }
                    ))

                    // Se houver débitos vencidos ou saque bloqueado, emitir estado para UI exibir
                    if (debitos.bloqueadoSaque) {
                        AppLogger.aviso(TAG, "Saque bloqueado para prof=$uid — débitos pendentes: ${debitos.totalPendente}, vencidos: ${debitos.totalVencido30d}")
                    }
                } else {
                    AppLogger.erro(TAG, "Falha ao consultar saldo disponível: HTTP ${response.status.value}")
                    _saldoDisponivelSaque.emit(0.0)
                }
            } catch (e: Exception) {
                AppLogger.erroRede("solicitar_saque_saldo", e, "prof=$uid")
                _saldoDisponivelSaque.emit(0.0)
            }
        }
    }

    fun dispensarDialogSaque() {
        viewModelScope.launch { _mostrarDialogSaque.emit(false) }
    }
}

class FinanceiroViewModelFactory(private val isPmp: Boolean) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FinanceiroViewModel(isPmp) as T
}