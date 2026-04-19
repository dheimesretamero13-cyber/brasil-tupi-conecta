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