package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// SearchRepository.kt  · Fase 3.4
//
// Responsabilidades:
//  • Busca Full-Text Search via TSVector no Postgres (coluna fts)
//  • Filtros encadeados: tipo, precoMin/Max, avaliacaoMin, profissionalId
//  • Debounce gerenciado pelo ViewModel — repositório é stateless
// ═══════════════════════════════════════════════════════════════════════════

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResultadoBusca(
    val id:              String,
    val titulo:          String,
    val descricao:       String  = "",
    val tipo:            String  = "",
    val preco:           Double  = 0.0,
    @SerialName("preco_original")   val precoOriginal:   Double?  = null,
    @SerialName("avaliacao_media")  val avaliacaoMedia:  Double   = 0.0,
    @SerialName("capa_url")         val capaUrl:         String?  = null,
    @SerialName("profissional_id")  val profissionalId:  String   = "",
    val destaque:        Boolean = false,
    val perfis:          ProfNestadoBusca? = null,
)

@Serializable
data class ProfNestadoBusca(
    val nome:          String? = null,
    @SerialName("ranking_score") val rankingScore: Double? = null, // ← adicionar
)

data class FiltroBusca(
    val query:          String  = "",
    val tipo:           String  = "todos",
    val precoMin:       Double? = null,
    val precoMax:       Double? = null,
    val avaliacaoMin:   Double? = null,
    val profissionalId: String? = null,
    val ordenacao:      OrdenacaoBusca = OrdenacaoBusca.RELEVANCIA,
)

enum class OrdenacaoBusca(val label: String, val coluna: String) {
    RELEVANCIA("Relevância",    "ranking_score.desc,avaliacao_media.desc"),
    MENOR_PRECO("Menor preço",  "preco.asc"),
    MAIOR_PRECO("Maior preço",  "preco.desc"),
    AVALIACAO("Avaliação",      "avaliacao_media.desc"),
}

class SearchRepository {

    suspend fun buscar(filtro: FiltroBusca): List<ResultadoBusca> {
        val token = currentToken ?: SUPABASE_KEY
        return try {
            httpClient.get("$SUPABASE_URL/rest/v1/estudio") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")

                // Sempre filtrar apenas itens ativos
                parameter("ativo", "eq.true")
                parameter("select",
                    "id,titulo,descricao,tipo,preco,preco_original," +
                            "avaliacao_media,capa_url,profissional_id,destaque," +
                            "perfis(nome,ranking_score)"
                )

                // ── Full-Text Search ──────────────────────────────────────
                // fts.plfts usa o dicionário 'portuguese' definido no TSVector
                if (filtro.query.isNotBlank()) {
                    val termos = filtro.query.trim()
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" & ")           // AND entre termos
                    parameter("fts", "plfts(portuguese).$termos")
                }

                // ── Filtros encadeados ────────────────────────────────────
                if (filtro.tipo != "todos") {
                    parameter("tipo", "eq.${filtro.tipo}")
                }
                filtro.precoMin?.let { parameter("preco", "gte.$it") }
                filtro.precoMax?.let { parameter("preco", "lte.$it") }
                filtro.avaliacaoMin?.let { parameter("avaliacao_media", "gte.$it") }
                filtro.profissionalId?.let { parameter("profissional_id", "eq.$it") }

                // ── Ordenação ─────────────────────────────────────────────
                parameter("order", filtro.ordenacao.coluna)
                parameter("limit", "50")
            }.body<List<ResultadoBusca>>()
        } catch (e: Exception) {
            AppLogger.erroRede("estudio (busca)", e, "query=${filtro.query}")
            emptyList()
        }
    }
}

object SearchRepositoryFactory {
    fun create(): SearchRepository = SearchRepository()
}
// ── Conversão ResultadoBusca → ItemEstudio (para reuso na EstudioDetalheScreen) ──
fun ResultadoBusca.toItemEstudio() = ItemEstudio(
    id             = id,
    profissionalId = profissionalId,
    titulo         = titulo,
    descricao      = descricao,
    tipo           = tipo,
    preco          = preco,
    precoOriginal  = precoOriginal,
    capaUrl        = capaUrl,
    autorNome      = perfis?.nome ?: "",
    destaque       = destaque,
    avaliacaoMedia = avaliacaoMedia,
)