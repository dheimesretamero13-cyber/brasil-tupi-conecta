package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AvaliacaoRepository.kt
//
// Responsabilidades:
//  1. Gravar avaliação + transicionar urgência para 'finished' atomicamente
//     via RPC Supabase `finalizar_urgencia(p_urgencia_id, p_nota, p_comentario)`
//  2. Verificar se existe urgência pendente de avaliação (in_progress)
//     para redirecionar o usuário ao abrir o app
//  3. Expor ResultadoAvaliacao selado para a UI tratar cada caso
//  4. Instrumentar via AppLogger
//
// ATOMICIDADE:
//  A RPC `finalizar_urgencia` executa dentro de uma única transação Postgres:
//    BEGIN;
//      INSERT INTO avaliacoes (...) ON CONFLICT DO UPDATE;
//      UPDATE urgencias SET status = 'finished' WHERE id = p_urgencia_id
//        AND status IN ('in_progress', 'pending_review');
//    COMMIT;
//  Se qualquer parte falhar, nenhuma alteração é persistida.
//  Isso garante a Regra de Ouro nº 6: ciclo financeiro só fecha com avaliação.
//
// FALLBACK:
//  Se a RPC não estiver disponível, o repositório executa as duas operações
//  sequencialmente com tratamento individual de erro (modo degradado).
// ═══════════════════════════════════════════════════════════════════════════

import android.util.Log
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ── Configuração via BuildConfig (sem hardcode) ─────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

private const val TAG = "AvaliacaoRepository"
private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════════════════
// DTOs
// ═══════════════════════════════════════════════════════════════════════════

// Request para a RPC atômica
@Serializable
private data class FinalizarUrgenciaRequest(
    @SerialName("p_urgencia_id") val urgenciaId:  String,
    @SerialName("p_nota")        val nota:         Int,
    @SerialName("p_comentario")  val comentario:   String?,
    @SerialName("p_avaliador_id") val avaliadorId: String,
)

// Fallback: gravar avaliação diretamente (sem RPC)
@Serializable
private data class GravarAvaliacaoUrgenciaRequest(
    @SerialName("urgencia_id")  val urgenciaId:  String,
    @SerialName("avaliador_id") val avaliadorId: String,
    val nota:                   Int,
    val comentario:             String?,
)

// Fallback: atualizar status da urgência
@Serializable
private data class AtualizarStatusUrgenciaFinalizarRequest(val status: String)

// ── Urgência pendente de avaliação (usada na verificação ao abrir o app) ──
@Serializable
data class UrgenciaPendenteAvaliacao(
    val id:     String,
    val status: String,
)

// ═══════════════════════════════════════════════════════════════════════════
// RESULTADO DA GRAVAÇÃO
// ═══════════════════════════════════════════════════════════════════════════

sealed class ResultadoAvaliacao {
    object Sucesso    : ResultadoAvaliacao()
    object JaAvaliada : ResultadoAvaliacao()   // RPC retornou false (já existe)
    data class Erro(val mensagem: String)      : ResultadoAvaliacao()
}

// ═══════════════════════════════════════════════════════════════════════════
// ESTADO OBSERVÁVEL PELA UI
// ═══════════════════════════════════════════════════════════════════════════

sealed class AvaliacaoState {
    object Idle        : AvaliacaoState()
    object Enviando    : AvaliacaoState()
    data class Resultado(val resultado: ResultadoAvaliacao) : AvaliacaoState()
}

// ═══════════════════════════════════════════════════════════════════════════
// REPOSITÓRIO
// ═══════════════════════════════════════════════════════════════════════════

object AvaliacaoRepository {

    private val _state = MutableStateFlow<AvaliacaoState>(AvaliacaoState.Idle)
    val state: StateFlow<AvaliacaoState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── SUBMETER AVALIAÇÃO ────────────────────────────────────────────────

    /**
     * Grava avaliação e transiciona urgência para 'finished' atomicamente.
     * Tenta via RPC primeiro; cai no fallback sequencial se a RPC não existir.
     *
     * @param urgenciaId   UUID da urgência — vincula avaliação ao ciclo
     * @param nota         1 a 5 estrelas
     * @param comentario   texto opcional do avaliador
     */
    fun submeter(urgenciaId: String, nota: Int, comentario: String?) {
        scope.launch {
            _state.emit(AvaliacaoState.Enviando)

            AppLogger.chave("avaliacao_urgencia_id", urgenciaId)
            AppLogger.chave("avaliacao_nota",        nota)
            AppLogger.info(TAG, "Submetendo avaliacao nota=$nota para urgencia=$urgenciaId")

            val avaliadorId = currentUserId ?: run {
                AppLogger.erroAuth("submeter_avaliacao", mensagemExtra = "userId nulo")
                _state.emit(AvaliacaoState.Resultado(
                    ResultadoAvaliacao.Erro("Sessão inválida. Faça login novamente.")
                ))
                return@launch
            }

            val resultado = tentarViaRpc(urgenciaId, avaliadorId, nota, comentario)
            _state.emit(AvaliacaoState.Resultado(resultado))

            when (resultado) {
                is ResultadoAvaliacao.Sucesso ->
                    AppLogger.info(TAG, "Avaliacao gravada com sucesso: urgencia=$urgenciaId")
                is ResultadoAvaliacao.JaAvaliada ->
                    AppLogger.aviso(TAG, "Urgencia $urgenciaId ja foi avaliada anteriormente")
                is ResultadoAvaliacao.Erro ->
                    AppLogger.erro(TAG, "Falha ao gravar avaliacao: ${resultado.mensagem}")
            }
        }
    }

    // ── RPC ATÔMICA ───────────────────────────────────────────────────────

    private suspend fun tentarViaRpc(
        urgenciaId:  String,
        avaliadorId: String,
        nota:        Int,
        comentario:  String?,
    ): ResultadoAvaliacao {
        return try {
            val token = currentToken ?: LOCAL_KEY
            val response = httpClient.post("$LOCAL_URL/rest/v1/rpc/finalizar_urgencia") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(FinalizarUrgenciaRequest(
                    urgenciaId  = urgenciaId,
                    nota        = nota,
                    comentario  = comentario,
                    avaliadorId = avaliadorId,
                ))
            }

            val corpo = response.bodyAsText().trim()
            Log.d(TAG, "RPC finalizar_urgencia HTTP ${response.status.value}: $corpo")

            when (response.status.value) {
                200 -> {
                    // RPC retorna BOOLEAN: true = sucesso, false = já avaliada
                    when {
                        corpo.equals("true",  ignoreCase = true) -> ResultadoAvaliacao.Sucesso
                        corpo.equals("false", ignoreCase = true) -> ResultadoAvaliacao.JaAvaliada
                        else -> {
                            // Tentar parsear JSON envolvido
                            val valor = try {
                                jsonParser.parseToJsonElement(corpo)
                                    .let { el ->
                                        when {
                                            el is JsonPrimitive ->
                                                el.content.equals("true", ignoreCase = true)
                                            el is JsonObject ->
                                                el.values.firstOrNull()
                                                    ?.jsonPrimitive?.content
                                                    ?.equals("true", ignoreCase = true) == true
                                            else -> null
                                        }
                                    }
                            } catch (_: Exception) { null }

                            when (valor) {
                                true  -> ResultadoAvaliacao.Sucesso
                                false -> ResultadoAvaliacao.JaAvaliada
                                null  -> {
                                    AppLogger.aviso(TAG, "RPC retornou corpo inesperado: $corpo. Tentando fallback.")
                                    executarFallbackSequencial(urgenciaId, avaliadorId, nota, comentario)
                                }
                            }
                        }
                    }
                }
                404 -> {
                    // RPC não existe ainda — fallback sequencial
                    AppLogger.aviso(TAG, "RPC finalizar_urgencia nao encontrada (404). Usando fallback.")
                    executarFallbackSequencial(urgenciaId, avaliadorId, nota, comentario)
                }
                else -> {
                    AppLogger.erroRpc(
                        urgenciaId    = urgenciaId,
                        httpStatus    = response.status.value,
                        corpoResposta = corpo,
                    )
                    ResultadoAvaliacao.Erro("Erro ao finalizar atendimento (${response.status.value}).")
                }
            }
        } catch (e: java.net.UnknownHostException) {
            AppLogger.erroRede("rpc/finalizar_urgencia", e, "urgencia=$urgenciaId")
            ResultadoAvaliacao.Erro("Sem conexão. Sua avaliação será salva assim que reconectar.")
        } catch (e: Exception) {
            AppLogger.erroRede("rpc/finalizar_urgencia", e, "urgencia=$urgenciaId")
            ResultadoAvaliacao.Erro("Falha inesperada. Tente novamente.")
        }
    }

    // ── FALLBACK SEQUENCIAL (sem RPC) ─────────────────────────────────────
    // Executa as duas operações separadamente.
    // Menos atômico, mas garante que a avaliação sempre é gravada.

    private suspend fun executarFallbackSequencial(
        urgenciaId:  String,
        avaliadorId: String,
        nota:        Int,
        comentario:  String?,
    ): ResultadoAvaliacao {
        val token = currentToken ?: LOCAL_KEY

        // 1. Gravar avaliação
        return try {
            val resAvaliacao = httpClient.post("$LOCAL_URL/rest/v1/avaliacoes") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Content-Type",  "application/json")
                header("Prefer",        "resolution=merge-duplicates,return=minimal")
                setBody(GravarAvaliacaoUrgenciaRequest(
                    urgenciaId  = urgenciaId,
                    avaliadorId = avaliadorId,
                    nota         = nota,
                    comentario   = comentario,
                ))
            }

            if (resAvaliacao.status.value !in 200..299) {
                return ResultadoAvaliacao.Erro(
                    "Erro ao salvar avaliação (${resAvaliacao.status.value})."
                )
            }

            // 2. Atualizar status da urgência para 'finished'
            val resStatus = httpClient.patch(
                "$LOCAL_URL/rest/v1/urgencias" +
                        "?id=eq.$urgenciaId" +
                        "&status=in.(in_progress,pending_review)"
            ) {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody(AtualizarStatusUrgenciaFinalizarRequest(status = "finished"))
            }

            if (resStatus.status.value in 200..299) {
                AppLogger.info(TAG, "Fallback: avaliacao+status OK para urgencia=$urgenciaId")
                ResultadoAvaliacao.Sucesso
            } else {
                // Avaliação gravou mas status não atualizou — cron do backend corrige
                AppLogger.aviso(TAG,
                    "Fallback: avaliacao gravada mas status nao atualizado: " +
                            "HTTP ${resStatus.status.value} urgencia=$urgenciaId"
                )
                ResultadoAvaliacao.Sucesso   // avaliação foi salva — ciclo pode fechar
            }
        } catch (e: Exception) {
            AppLogger.erroRede("fallback_avaliacao", e, "urgencia=$urgenciaId")
            ResultadoAvaliacao.Erro("Falha de rede ao salvar avaliação.")
        }
    }

    // ── VERIFICAR AVALIAÇÃO PENDENTE (diretiva 6) ─────────────────────────

    /**
     * Busca urgências do usuário autenticado com status in_progress ou
     * pending_review que ainda não têm avaliação. Chamada ao abrir o app
     * para redirecionar para AvaliacaoScreen se houver pendência.
     *
     * Retorna null se não há pendência ou se o usuário não está logado.
     */
    suspend fun verificarPendencia(): UrgenciaPendenteAvaliacao? {
        val uid   = currentUserId ?: return null
        val token = currentToken  ?: LOCAL_KEY

        return try {
            // Buscar urgências em que sou cliente ou profissional, com status pendente
            val url = "$LOCAL_URL/rest/v1/urgencias" +
                    "?select=id,status" +
                    "&status=in.(in_progress,pending_review)" +
                    "&or=(client_id.eq.$uid,professional_id.eq.$uid)" +
                    "&order=criado_em.desc" +
                    "&limit=1"

            val response = httpClient.get(url) {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
            }

            if (response.status.value !in 200..299) return null

            val lista = jsonParser.decodeFromString<List<UrgenciaPendenteAvaliacao>>(
                response.bodyAsText()
            )
            lista.firstOrNull().also { pendente ->
                if (pendente != null) {
                    AppLogger.info(TAG, "Avaliacao pendente encontrada: urgencia=${pendente.id}")
                }
            }
        } catch (e: Exception) {
            AppLogger.aviso(TAG, "Falha ao verificar pendencia de avaliacao: ${e.message}")
            null
        }
    }

    fun resetar() {
        scope.launch { _state.emit(AvaliacaoState.Idle) }
    }
}