package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// DisputaRepository.kt  · Fase 4.3
// ═══════════════════════════════════════════════════════════════════════════

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class CategoriaDisputa(val id: String, val label: String, val icone: String) {
    COBRANCA_INDEVIDA("cobranca_indevida", "Cobrança indevida", "💳"),
    NAO_COMPARECEU("profissional_nao_compareceu", "Profissional não compareceu", "🚫"),
    QUALIDADE("qualidade", "Qualidade do atendimento", "⭐"),
    TECNICO("tecnico", "Problema técnico", "🔧"),
    OUTRO("outro", "Outro", "📝");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class Disputa(
    val id:                  String,
    @SerialName("agendamento_id")  val agendamentoId: String? = null,
    @SerialName("usuario_id")      val usuarioId:     String  = "",
    val categoria:           String  = "",
    val descricao:           String  = "",
    val status:              String  = "aberta",
    val resolucao:           String? = null,
    @SerialName("criado_em")       val criadoEm:      String  = "",
    @SerialName("atualizado_em")   val atualizadoEm:  String  = "",
)

@Serializable
private data class CriarDisputaRequest(
    @SerialName("agendamento_id") val agendamentoId: String?,
    @SerialName("usuario_id")     val usuarioId:     String,
    val categoria:    String,
    val descricao:    String,
    val status:       String = "aberta",
)

class DisputaRepository {

    // ── 1. ABRIR DISPUTA ──────────────────────────────────────────────────
    suspend fun abrirDisputa(
        agendamentoId: String?,
        categoria:     CategoriaDisputa,
        descricao:     String,
    ): Result<Disputa> {
        val userId = currentUserId ?: return Result.failure(Exception("Sessão inválida"))
        val token  = currentToken  ?: SUPABASE_KEY
        return try {
            val response = httpClient.post("$SUPABASE_URL/rest/v1/disputes") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer $token")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=representation")
                setBody(CriarDisputaRequest(
                    agendamentoId = agendamentoId,
                    usuarioId     = userId,
                    categoria     = categoria.id,
                    descricao     = descricao,
                ))
            }.body<List<Disputa>>().first()
            Result.success(response)
        } catch (e: Exception) {
            AppLogger.erroRede("/rest/v1/disputes", e, "categoria=${categoria.id}")
            Result.failure(e)
        }
    }

    // ── 2. BUSCAR DISPUTAS DO USUÁRIO ─────────────────────────────────────
    suspend fun buscarDisputas(): List<Disputa> {
        val userId = currentUserId ?: return emptyList()
        val token  = currentToken  ?: SUPABASE_KEY
        return try {
            httpClient.get("$SUPABASE_URL/rest/v1/disputes") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("usuario_id", "eq.$userId")
                parameter("select",
                    "id,agendamento_id,usuario_id,categoria," +
                            "descricao,status,resolucao,criado_em,atualizado_em"
                )
                parameter("order", "criado_em.desc")
            }.body<List<Disputa>>()
        } catch (e: Exception) {
            AppLogger.erroRede("/rest/v1/disputes", e)
            emptyList()
        }
    }
}

object DisputaRepositoryFactory {
    fun create(): DisputaRepository = DisputaRepository()
}