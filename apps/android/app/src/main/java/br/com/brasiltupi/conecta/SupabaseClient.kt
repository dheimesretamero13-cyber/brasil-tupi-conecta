package br.com.brasiltupi.conecta

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── CONFIGURAÇÃO ──────────────────────────────────────
private const val SUPABASE_URL = "https://qfzdchrlbqcvewjivaqz.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_SM-UHBh_5lzTSBZ2YPUIYw_Sw1i8qeq"

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

val httpClient = HttpClient(Android) {
    install(ContentNegotiation) {
        json(json)
    }
}

// Token de sessão atual
var currentToken: String? = null
var currentUserId: String? = null

// ── MODELOS ───────────────────────────────────────────
@Serializable
data class PerfilModel(
    val id: String,
    val nome: String,
    val email: String,
    val telefone: String? = null,
    val cpf: String? = null,
    val tipo: String,
    val cidade: String? = null,
    val estado: String? = null,
)

@Serializable
data class ProfissionalComPerfil(
    val id: String,
    val area: String,
    val descricao: String? = null,
    val conselho: String? = null,
    val numero_conselho: String? = null,
    val credibilidade: Int = 0,
    val is_pmp: Boolean = false,
    val disponivel_urgente: Boolean = false,
    val valor_normal: Int = 80,
    val valor_urgente: Int? = null,
    val verificado: Boolean = false,
    val perfis: PerfilModel? = null,
)

@Serializable
data class AuthRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(
    val access_token: String? = null,
    val user: AuthUser? = null,
)

@Serializable
data class AuthUser(val id: String)

// ── AUTH ──────────────────────────────────────────────
suspend fun signInAndroid(email: String, senha: String): PerfilModel? {
    return try {
        val response = httpClient.post("$SUPABASE_URL/auth/v1/token?grant_type=password") {
            header("apikey", SUPABASE_KEY)
            header("Content-Type", "application/json")
            setBody(AuthRequest(email, senha))
        }.body<AuthResponse>()

        currentToken = response.access_token
        currentUserId = response.user?.id

        if (currentUserId != null) getPerfilAndroid(currentUserId!!)
        else null
    } catch (e: Exception) {
        null
    }
}

suspend fun signUpAndroid(
    email: String,
    senha: String,
    nome: String,
    telefone: String,
    tipo: String,
    cpf: String? = null,
    cidade: String? = null,
    estado: String? = null,
): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/auth/v1/signup") {
            header("apikey", SUPABASE_KEY)
            header("Content-Type", "application/json")
            setBody(AuthRequest(email, senha))
        }.body<AuthResponse>()

        currentToken = response.access_token
        currentUserId = response.user?.id

        if (currentUserId != null) {
            inserirPerfil(PerfilModel(
                id = currentUserId!!,
                nome = nome,
                email = email,
                telefone = telefone,
                tipo = tipo,
                cpf = cpf,
                cidade = cidade,
                estado = estado,
            ))
            true
        } else false
    } catch (e: Exception) {
        false
    }
}

suspend fun signOutAndroid() {
    try {
        httpClient.post("$SUPABASE_URL/auth/v1/logout") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer $currentToken")
        }
    } catch (e: Exception) {}
    currentToken = null
    currentUserId = null
}

// ── PERFIS ────────────────────────────────────────────
suspend fun getPerfilAndroid(userId: String): PerfilModel? {
    return try {
        httpClient.get("$SUPABASE_URL/rest/v1/perfis") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            parameter("id", "eq.$userId")
            parameter("select", "*")
            header("Accept", "application/json")
        }.body<List<PerfilModel>>().firstOrNull()
    } catch (e: Exception) {
        null
    }
}

private suspend fun inserirPerfil(perfil: PerfilModel) {
    try {
        httpClient.post("$SUPABASE_URL/rest/v1/perfis") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(perfil)
        }
    } catch (e: Exception) {}
}

// ── PROFISSIONAIS PMP ─────────────────────────────────
suspend fun getProfissionaisPMPAndroid(
    somenteUrgente: Boolean = false,
    busca: String = "",
): List<ProfissionalComPerfil> {
    return try {
        var selectQuery = "*, perfis(nome,email,cidade,estado)"
        val result = httpClient.get("$SUPABASE_URL/rest/v1/profissionais") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("select", selectQuery)
            parameter("is_pmp", "eq.true")
            parameter("verificado", "eq.true")
            parameter("credibilidade", "gte.80")
            parameter("order", "credibilidade.desc")
            if (somenteUrgente) parameter("disponivel_urgente", "eq.true")
        }.body<List<ProfissionalComPerfil>>()

        if (busca.isEmpty()) result
        else result.filter { p ->
            val nome = p.perfis?.nome ?: ""
            val area = p.area
            val cidade = p.perfis?.cidade ?: ""
            nome.contains(busca, ignoreCase = true) ||
                    area.contains(busca, ignoreCase = true) ||
                    cidade.contains(busca, ignoreCase = true)
        }
    } catch (e: Exception) {
        emptyList()
    }
}
// ── ESTÚDIO ───────────────────────────────────────────
suspend fun getProfissionaisEstudioAndroid(
    filtroTipo: String = "todos"
): List<ItemEstudio> {
    return try {
        var url = "$SUPABASE_URL/rest/v1/estudio?select=*,perfis(nome)&ativo=eq.true&order=destaque.desc,criado_em.desc"
        if (filtroTipo != "todos") url += "&tipo=eq.$filtroTipo"

        val result = httpClient.get(url) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<Map<String, Any?>>>()

        result.map { mapToItemEstudio(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun getEstudioProfissionalAndroid(
    profissionalId: String,
    filtroTipo: String = "todos"
): List<ItemEstudio> {
    return try {
        var url = "$SUPABASE_URL/rest/v1/estudio?select=*,perfis(nome)&ativo=eq.true&profissional_id=eq.$profissionalId&order=destaque.desc,criado_em.desc"
        if (filtroTipo != "todos") url += "&tipo=eq.$filtroTipo"

        val result = httpClient.get(url) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<Map<String, Any?>>>()

        result.map { mapToItemEstudio(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

@Suppress("UNCHECKED_CAST")
private fun mapToItemEstudio(map: Map<String, Any?>): ItemEstudio {
    val perfis = map["perfis"] as? Map<String, Any?>
    return ItemEstudio(
        id = map["id"] as? String ?: "",
        profissionalId = map["profissional_id"] as? String ?: "",
        titulo = map["titulo"] as? String ?: "",
        descricao = map["descricao"] as? String ?: "",
        tipo = map["tipo"] as? String ?: "aula",
        preco = (map["preco"] as? Number)?.toDouble() ?: 0.0,
        precoOriginal = (map["preco_original"] as? Number)?.toDouble(),
        capaUrl = map["capa_url"] as? String,
        videoUrl = map["video_url"] as? String,
        linkExterno = map["link_externo"] as? String,
        temEntrega = map["tem_entrega"] as? Boolean ?: false,
        destaque = map["destaque"] as? Boolean ?: false,
        totalVendas = (map["total_vendas"] as? Number)?.toInt() ?: 0,
        avaliacaoMedia = (map["avaliacao_media"] as? Number)?.toDouble() ?: 0.0,
        autorNome = perfis?.get("nome") as? String ?: "",
    )
}
suspend fun criarItemEstudioAndroid(
    profissionalId: String,
    titulo: String,
    descricao: String,
    tipo: String,
    preco: Double,
    precoOriginal: Double? = null,
    videoUrl: String? = null,
    arquivoUrl: String? = null,
    linkExterno: String? = null,
    temEntrega: Boolean = false,
    destaque: Boolean = false,
): Boolean {
    return try {
        val body = buildString {
            append("{")
            append("\"profissional_id\":\"$profissionalId\",")
            append("\"titulo\":\"$titulo\",")
            append("\"descricao\":\"$descricao\",")
            append("\"tipo\":\"$tipo\",")
            append("\"preco\":$preco,")
            if (precoOriginal != null) append("\"preco_original\":$precoOriginal,")
            if (videoUrl != null) append("\"video_url\":\"$videoUrl\",")
            if (arquivoUrl != null) append("\"arquivo_url\":\"$arquivoUrl\",")
            if (linkExterno != null) append("\"link_externo\":\"$linkExterno\",")
            append("\"tem_entrega\":$temEntrega,")
            append("\"destaque\":$destaque,")
            append("\"ativo\":true")
            append("}")
        }
        httpClient.post("$SUPABASE_URL/rest/v1/estudio") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(body)
        }
        true
    } catch (e: Exception) {
        false
    }
}