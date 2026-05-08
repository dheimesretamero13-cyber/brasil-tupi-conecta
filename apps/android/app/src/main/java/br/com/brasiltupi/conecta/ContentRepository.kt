package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// ContentRepository.kt  · Fase 3.1 + 3.2
//
// Responsabilidades:
//  • Gerar URL temporária (1h) do Supabase Storage para aulas (vídeo)
//  • Gerar URL temporária (1h) do Supabase Storage para PDFs
//  • Buscar progresso salvo do usuário na tabela course_progress
//  • Salvar progresso (posição em ms + % concluído) no Postgres
//
// Sem Hilt: instanciado manualmente via ContentRepositoryFactory
// ═══════════════════════════════════════════════════════════════════════════

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

// ── Modelo de progresso retornado pelo Supabase ───────────────────────────
data class ProgressoAula(
    val posicaoMs: Long,
    val percentualConcluido: Double,
)

// ── DTO interno — deserialização segura via kotlinx.serialization ─────────
@Serializable
private data class ProgressoSupabase(
    @SerialName("posicao_ms")           val posicaoMs: Long   = 0L,
    @SerialName("percentual_concluido") val percentualConcluido: Double = 0.0,
)

// ── DTO de upsert — evita Map<String, Any> que causa falha de serialização ─
@Serializable
private data class SalvarProgressoRequest(
    @SerialName("user_id")              val userId: String,
    @SerialName("aula_id")              val aulaId: String,
    @SerialName("curso_id")             val cursoId: String,
    @SerialName("posicao_ms")           val posicaoMs: Long,
    @SerialName("percentual_concluido") val percentualConcluido: Double,
)

class ContentRepository {

    // ── URL assinada genérica (bucket + objectPath) ────────────────────────
    suspend fun gerarUrlTemporaria(bucket: String, objectPath: String, expiresIn: Int = 3600): String {
        return try {
            val response = httpClient.post(
                "$LOCAL_URL/storage/v1/object/sign/$bucket/$objectPath"
            ) {
                header("apikey", LOCAL_KEY)
                header("Authorization", "Bearer $LOCAL_KEY")
                header("Content-Type", "application/json")
                setBody("{\"expiresIn\": $expiresIn}")
            }.body<Map<String, String?>>()

            val signedUrl = response["signedURL"] ?: response["signedUrl"]
            ?: throw IllegalStateException("URL assinada não gerada para $bucket/$objectPath")
            "$LOCAL_URL/storage/v1$signedUrl"
        } catch (e: Exception) {
            AppLogger.erro("ContentRepository", "Falha ao gerar URL assinada $bucket/$objectPath", e)
            throw e
        }
    }

    // Manter compatibilidade com chamadas antigas
    suspend fun gerarUrlTemporaria(aulaId: String): String =
        gerarUrlTemporaria(bucket = "aulas", objectPath = aulaId)

    suspend fun gerarUrlTemporariaPdf(produtoId: String): String =
        gerarUrlTemporaria(bucket = "produtos-pdf", objectPath = "$produtoId.pdf")

    // ── 3. BUSCAR PROGRESSO SALVO ─────────────────────────────────────────
    suspend fun buscarProgresso(aulaId: String, cursoId: String): ProgressoAula? {
        val userId = currentUserId ?: return null
        return try {
            httpClient.get("$LOCAL_URL/rest/v1/course_progress") {
                header("apikey", LOCAL_KEY)
                header("Authorization", "Bearer ${currentToken ?: LOCAL_KEY}")
                header("Accept", "application/json")
                parameter("user_id",  "eq.$userId")
                parameter("aula_id",  "eq.$aulaId")
                parameter("curso_id", "eq.$cursoId")
                parameter("select",   "posicao_ms,percentual_concluido")
                parameter("limit",    "1")
            }.body<List<ProgressoSupabase>>().firstOrNull()?.let {
                ProgressoAula(
                    posicaoMs           = it.posicaoMs,
                    percentualConcluido = it.percentualConcluido,
                )
            }
        } catch (e: Exception) {
            AppLogger.aviso("ContentRepository", "Falha ao buscar progresso aula=$aulaId: ${e.message}")
            null
        }
    }

    // ── 4. SALVAR PROGRESSO ───────────────────────────────────────────────
    // Usa upsert (merge-duplicates) — seguro chamar multiplas vezes.
    // Chave unica esperada no Supabase: (user_id, aula_id, curso_id)
    suspend fun salvarProgresso(
        aulaId: String,
        cursoId: String,
        posicaoMs: Long,
        percentualConcluido: Double,
    ) {
        val userId = currentUserId ?: return
        try {
            httpClient.post("$LOCAL_URL/rest/v1/course_progress") {
                header("apikey", LOCAL_KEY)
                header("Authorization", "Bearer ${currentToken ?: LOCAL_KEY}")
                header("Content-Type", "application/json")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                setBody(SalvarProgressoRequest(
                    userId              = userId,
                    aulaId              = aulaId,
                    cursoId             = cursoId,
                    posicaoMs           = posicaoMs,
                    percentualConcluido = percentualConcluido,
                ))
            }
        } catch (e: Exception) {
            AppLogger.aviso("ContentRepository", "Falha ao salvar progresso aula=$aulaId: ${e.message}")
        }
    }

} // ← fim de ContentRepository

// ── Factory — para passar ao ViewModel via factory manual ─────────────────
object ContentRepositoryFactory {
    fun create(): ContentRepository = ContentRepository()
}