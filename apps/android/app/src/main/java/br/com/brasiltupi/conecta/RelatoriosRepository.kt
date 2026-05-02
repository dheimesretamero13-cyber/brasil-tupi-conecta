package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// RelatoriosRepository.kt  · Fase 4.4
// ═══════════════════════════════════════════════════════════════════════════

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

@Serializable
data class StatsSemanal(
    val id:                   String,
    @SerialName("profissional_id")      val profissionalId:    String  = "",
    @SerialName("semana_referencia")    val semanaReferencia:  String  = "",
    @SerialName("total_atendimentos")   val totalAtendimentos: Int     = 0,
    @SerialName("total_solicitacoes")   val totalSolicitacoes: Int     = 0,
    @SerialName("taxa_conversao")       val taxaConversao:     Double  = 0.0,
    @SerialName("tempo_medio_resposta") val tempoMedioResposta: Double = 0.0,
    @SerialName("nota_media")           val notaMedia:         Double  = 0.0,
    @SerialName("total_ganho")          val totalGanho:        Double  = 0.0,
    @SerialName("hora_pico")            val horaPico:          Int?    = null,
)

@Serializable
data class HorarioPico(
    val hora:  Int = 0,
    val total: Int = 0,
)

data class RelatorioSemana(
    val stats:    StatsSemanal,
    val horarios: List<HorarioPico>,
)

class RelatoriosRepository {

    // ── Buscar últimas N semanas de stats ─────────────────────────────────
    suspend fun buscarStats(limite: Int = 8): List<StatsSemanal> {
        val userId = currentUserId ?: return emptyList()
        val token  = currentToken  ?: LOCAL_KEY
        return try {
            httpClient.get("$LOCAL_URL/rest/v1/profissional_stats_semanal") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("profissional_id", "eq.$userId")
                parameter("select",
                    "id,profissional_id,semana_referencia,total_atendimentos," +
                            "total_solicitacoes,taxa_conversao,tempo_medio_resposta," +
                            "nota_media,total_ganho,hora_pico"
                )
                parameter("order", "semana_referencia.desc")
                parameter("limit", "$limite")
            }.body<List<StatsSemanal>>().reversed() // mais antigo primeiro para gráfico
        } catch (e: Exception) {
            AppLogger.erroRede("profissional_stats_semanal", e)
            emptyList()
        }
    }

    // ── Buscar horários de pico da semana mais recente ────────────────────
    suspend fun buscarHorariosPico(statsId: String): List<HorarioPico> {
        val token = currentToken ?: LOCAL_KEY
        return try {
            httpClient.get("$LOCAL_URL/rest/v1/profissional_horarios_pico") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("stats_id", "eq.$statsId")
                parameter("select",   "hora,total")
                parameter("order",    "hora.asc")
            }.body<List<HorarioPico>>()
        } catch (e: Exception) {
            AppLogger.erroRede("profissional_horarios_pico", e)
            emptyList()
        }
    }
}

object RelatoriosRepositoryFactory {
    fun create(): RelatoriosRepository = RelatoriosRepository()
}