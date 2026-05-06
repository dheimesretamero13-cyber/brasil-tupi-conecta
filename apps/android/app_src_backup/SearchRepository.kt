package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// SearchRepository.kt  · Fase 3.4 + 4 (Motor de Recomendação)
//
// Responsabilidades:
//  • Busca Full-Text Search via TSVector no Postgres (coluna fts)
//  • Filtros encadeados: tipo, precoMin/Max, avaliacaoMin, profissionalId
//  • Debounce gerenciado pelo ViewModel — repositório é stateless
//
// FASE 4 — MUDANÇA ARQUITETURAL:
//  A query migrou de GET /rest/v1/estudio para POST /rest/v1/rpc/buscar_estudio.
//
//  MOTIVO: O PostgREST não ordena por colunas de tabelas relacionadas via
//  parâmetro `order`. O campo `ranking_score` fica em `perfis`, não em
//  `estudio`. A ordenação por relevância era silenciosamente ignorada.
//  A RPC resolve o JOIN no Postgres e retorna já ordenado corretamente.
//
//  COMPATIBILIDADE:
//  • ResultadoBusca: inalterado — SearchScreen e EstudioDetalheScreen
//    continuam funcionando sem nenhuma mudança.
//  • toItemEstudio(): inalterada.
//  • OrdenacaoBusca: campo `coluna` renomeado para `id` — o SearchViewModel
//    precisa trocar .coluna por .id em qualquer referência.
// ═══════════════════════════════════════════════════════════════════════════

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

// ── DTO PÚBLICO — inalterado desde a Fase 3.4 ────────────────────────────
// SearchScreen, EstudioDetalheScreen e toItemEstudio() dependem deste DTO.
// Não alterar campos ou SerialNames.

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
    @SerialName("ranking_score") val rankingScore: Double? = null,
)

// ── DTO INTERNO — response flat da RPC buscar_estudio ────────────────────
// A RPC retorna colunas flat (perfil_nome, ranking_score) em vez de objeto
// aninhado. Este DTO mapeia o response e converte para ResultadoBusca,
// mantendo o contrato público inalterado para toda a UI.

@Serializable
private data class ResultadoBuscaRpc(
    val id:              String,
    val titulo:          String,
    val descricao:       String  = "",
    val tipo:            String  = "",
    val preco:           Double  = 0.0,
    @SerialName("preco_original")  val precoOriginal:  Double?  = null,
    @SerialName("avaliacao_media") val avaliacaoMedia: Double   = 0.0,
    @SerialName("capa_url")        val capaUrl:        String?  = null,
    @SerialName("profissional_id") val profissionalId: String   = "",
    val destaque:                  Boolean             = false,
    // Campos do JOIN com perfis — chegam flat da RPC
    @SerialName("perfil_nome")   val perfilNome:   String? = null,
    @SerialName("ranking_score") val rankingScore: Double? = null,
) {
    fun toResultadoBusca() = ResultadoBusca(
        id             = id,
        titulo         = titulo,
        descricao      = descricao,
        tipo           = tipo,
        preco          = preco,
        precoOriginal  = precoOriginal,
        avaliacaoMedia = avaliacaoMedia,
        capaUrl        = capaUrl,
        profissionalId = profissionalId,
        destaque       = destaque,
        perfis         = ProfNestadoBusca(
            nome         = perfilNome,
            rankingScore = rankingScore,
        ),
    )
}

// ── DTO DO REQUEST PARA A RPC ─────────────────────────────────────────────
// Parâmetros nomeados com p_ conforme assinatura da função SQL.

@Serializable
private data class BuscarEstudioRequest(
    @SerialName("p_query")           val query:          String  = "",
    @SerialName("p_tipo")            val tipo:           String  = "todos",
    @SerialName("p_preco_min")       val precoMin:       Double? = null,
    @SerialName("p_preco_max")       val precoMax:       Double? = null,
    @SerialName("p_avaliacao_min")   val avaliacaoMin:   Double? = null,
    @SerialName("p_profissional_id") val profissionalId: String? = null,
    @SerialName("p_ordenacao")       val ordenacao:      String  = "relevancia",
    @SerialName("p_limite")          val limite:         Int     = 50,
)

// ── FILTRO DE BUSCA (data class de domínio — inalterado) ──────────────────

data class FiltroBusca(
    val query:          String  = "",
    val tipo:           String  = "todos",
    val precoMin:       Double? = null,
    val precoMax:       Double? = null,
    val avaliacaoMin:   Double? = null,
    val profissionalId: String? = null,
    val ordenacao:      OrdenacaoBusca = OrdenacaoBusca.RELEVANCIA,
)

// `id` é o valor enviado como p_ordenacao para a RPC.
// A RPC interpreta: 'relevancia' | 'menor_preco' | 'maior_preco' | 'avaliacao'
//
// ATENÇÃO PARA O SearchViewModel: se havia referência a `.coluna`, trocar por `.id`.
enum class OrdenacaoBusca(val label: String, val id: String) {
    RELEVANCIA("Relevância",   "relevancia"),
    MENOR_PRECO("Menor preço", "menor_preco"),
    MAIOR_PRECO("Maior preço", "maior_preco"),
    AVALIACAO("Avaliação",     "avaliacao"),
}

// ── REPOSITÓRIO ───────────────────────────────────────────────────────────

class SearchRepository {

    suspend fun buscar(filtro: FiltroBusca): List<ResultadoBusca> {
        val token = currentToken ?: LOCAL_KEY
        return try {
            // ── Fase 4: POST para RPC em vez de GET em /rest/v1/estudio ──
            // A RPC faz o JOIN com perfis e ordena por ranking_score no Postgres,
            // resolvendo a limitação do PostgREST com colunas de tabelas relacionadas.
            httpClient.post("$LOCAL_URL/rest/v1/rpc/buscar_estudio") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                header("Accept",        "application/json")
                setBody(BuscarEstudioRequest(
                    query          = filtro.query.trim(),
                    tipo           = filtro.tipo,
                    precoMin       = filtro.precoMin,
                    precoMax       = filtro.precoMax,
                    avaliacaoMin   = filtro.avaliacaoMin,
                    profissionalId = filtro.profissionalId,
                    ordenacao      = filtro.ordenacao.id,
                    limite         = 50,
                ))
            }.body<List<ResultadoBuscaRpc>>().map { it.toResultadoBusca() }
        } catch (e: Exception) {
            AppLogger.erroRede("rpc/buscar_estudio", e, "query=${filtro.query}")
            emptyList()
        }
    }
}

object SearchRepositoryFactory {
    fun create(): SearchRepository = SearchRepository()
}

// ── Conversão ResultadoBusca → ItemEstudio (inalterada desde Fase 3.4) ───
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