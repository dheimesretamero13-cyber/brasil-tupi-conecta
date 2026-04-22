package br.com.brasiltupi.conecta

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SUPABASE_URL = "https://qfzdchrlbqcvewjivaqz.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_SM-UHBh_5lzTSBZ2YPUIYw_Sw1i8qeq"

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

val httpClient = HttpClient(Android) {
    install(ContentNegotiation) { json(json) }
}

var currentToken: String? = null
var currentUserId: String? = null

// ── RESULTADO DE AUTH (sealed class) ─────────────────
sealed class AuthResult {
    data class Sucesso(val perfil: PerfilUsuario) : AuthResult()
    object SenhaErrada : AuthResult()
    object EmailNaoEncontrado : AuthResult()
    object SemInternet : AuthResult()
    object Desconhecido : AuthResult()
}

// ── MODELOS ───────────────────────────────────────────
@Serializable
data class PerfilUsuario(
    val id: String,
    val nome: String,
    val email: String,
    val tipo: String,
    val telefone: String? = null,
    val cpf: String? = null,
    val cidade: String? = null,
    val estado: String? = null,
    val foto_url: String? = null,
    val capa_url: String? = null,
    @SerialName("criado_em") val criadoEm: String? = null,
)

@Serializable
data class PerfilNested(
    val nome: String? = null,
    val email: String? = null,
    val cidade: String? = null,
    val estado: String? = null,
    val foto_url: String? = null,
    val capa_url: String? = null,
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
    val perfis: PerfilNested? = null,
)

@Serializable
data class AuthRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(
    val access_token: String? = null,
    val user: AuthUser? = null,
    val error_code: String? = null,
    val msg: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@Serializable
data class AuthUser(val id: String)

// ── DTOs INTERNOS: CONSULTA COM JOINs ────────────────
@Serializable
private data class PerfilNestedSimples(
    val nome: String? = null,
)

@Serializable
private data class ProfissionalNested(
    val area: String? = null,
    @SerialName("valor_normal") val valorNormal: Int? = null,
    val perfis: PerfilNestedSimples? = null,
)

@Serializable
private data class AvaliacaoNested(
    val nota: Int? = null,
)

@Serializable
private data class ConsultaSupabase(
    val id: String,
    @SerialName("profissional_id") val profissionalId: String? = null,
    val tipo: String,
    val status: String,
    @SerialName("data_agendada") val dataAgendada: String? = null,
    val valor: Int,
    val profissionais: ProfissionalNested? = null,
    val avaliacoes: List<AvaliacaoNested>? = null,
)

// ── REQUEST DTOs (substituem buildString) ─────────────
// Cada função de escrita tem seu próprio DTO privado.
// @Serializable garante escape correto de caracteres especiais —
// um titulo com aspas ("Curso \"Avançado\"") não quebra o JSON.

@Serializable
private data class CriarItemEstudioRequest(
    val profissional_id: String,
    val titulo: String,
    val descricao: String,
    val tipo: String,
    val preco: Double,
    val preco_original: Double? = null,
    val video_url: String? = null,
    val arquivo_url: String? = null,
    val link_externo: String? = null,
    val tem_entrega: Boolean = false,
    val destaque: Boolean = false,
    val ativo: Boolean = true,
)

@Serializable
private data class GravarAvaliacaoRequest(
    val consulta_id: String,
    val cliente_id: String,
    val profissional_id: String,
    val nota: Int,
    val comentario: String? = null,
)

@Serializable
private data class AtualizarPerfilProfissionalRequest(
    val id: String,
    val descricao: String,
    val area: String,
    val conselho: String,
    val numero_conselho: String,
    val valor_normal: Int,
    val valor_urgente: Int,
)

@Serializable
private data class CriarAgendamentoRequest(
    val cliente_id: String,
    val profissional_id: String,
    val tipo: String,
    val status: String = "agendada",
    val data_agendada: String,
    val valor: Int,
)

@Serializable
private data class CriarPedidoRequest(
    val consulta_id: String,
    val cliente_id: String,
    val profissional_id: String,
    val valor: Double,
    val status: String = "pendente",
    val tipo: String,
)

@Serializable
private data class EnviarMensagemRequest(
    val remetente_id: String,
    val destinatario_id: String,
    val texto: String,
)
@Serializable
private data class SalvarFotoPerfilRequest(
    val foto_url: String? = null,
    val capa_url: String? = null,
)
@Serializable
private data class ResetSenhaRequest(
    val email: String,
)
@Serializable
private data class AtualizarAvaliacaoConsultaRequest(
    val avaliacao: Int,
    val avaliada : Boolean = true,
)
@Serializable
private data class DisponibilidadeUrgenteRequest(
    val disponivel_urgente: Boolean,
)
@Serializable
private data class SalvarDadosPerfilRequest(
    val nome    : String,
    val telefone: String,
)
@Serializable
private data class SalvarCidadeEstadoRequest(
    val cidade: String,
    val estado: String,
)

@Serializable
private data class SalvarDescricaoProfissionalRequest(
    val descricao: String,
)


// ── HELPERS ───────────────────────────────────────────
// Compatível com API 24+: parse manual do timestamptz do Supabase
// Formato esperado: "2025-04-20T14:30:00+00:00" ou "2025-04-20T14:30:00.000000+00:00"
private fun parseTimestamptz(raw: String?): Pair<String, String> {
    if (raw == null) return "--" to "--"
    return try {
        val semFuso = raw.substringBefore("+").substringBefore("Z").take(19)
        val partes = semFuso.split("T")
        val dateParts = partes[0].split("-")
        val timeParts = partes.getOrNull(1)?.split(":")
        val data = "${dateParts[2]}/${dateParts[1]}/${dateParts[0]}"
        val hora = "${timeParts?.get(0) ?: "00"}:${timeParts?.get(1) ?: "00"}"
        data to hora
    } catch (e: Exception) {
        "--" to "--"
    }
}

// Formata "2025-03-15T..." → "Março 2025" para exibição de "Membro desde"
fun formatarMembroDesde(criadoEm: String?): String {
    if (criadoEm == null) return "--"
    return try {
        val partes = criadoEm.substringBefore("T").split("-")
        val ano = partes[0]
        val mes = when (partes[1]) {
            "01" -> "Janeiro"; "02" -> "Fevereiro"; "03" -> "Março"
            "04" -> "Abril";   "05" -> "Maio";      "06" -> "Junho"
            "07" -> "Julho";   "08" -> "Agosto";    "09" -> "Setembro"
            "10" -> "Outubro"; "11" -> "Novembro";  "12" -> "Dezembro"
            else -> partes[1]
        }
        "$mes $ano"
    } catch (e: Exception) { "--" }
}

private fun ConsultaSupabase.toConsultaCliente(): ConsultaCliente {
    val (data, hora) = parseTimestamptz(dataAgendada)
    val avaliacao = avaliacoes?.firstOrNull()
    val avaliada  = avaliacao != null
    val nota      = avaliacao?.nota ?: 0

    return ConsultaCliente(
        id             = id,
        profissionalId = profissionalId ?: "",
        profissional   = profissionais?.perfis?.nome ?: "Profissional",
        area           = profissionais?.area ?: "--",
        data           = data,
        hora           = hora,
        tipo           = tipo,
        status         = status,
        avaliada       = avaliada,
        avaliacao      = nota,
        valor          = "R$ $valor",
    )
}

// ── AUTH ──────────────────────────────────────────────

// signInAndroid retorna AuthResult, diferenciando causas de erro.
// signInAndroidLegado() removido — LoginScreen já usa AuthResult diretamente.
suspend fun signInAndroid(email: String, senha: String): AuthResult {
    return try {
        val response = httpClient.post("$SUPABASE_URL/auth/v1/token?grant_type=password") {
            header("apikey", SUPABASE_KEY)
            header("Content-Type", "application/json")
            setBody(AuthRequest(email, senha))
        }.body<AuthResponse>()

        // Supabase retorna 200 com access_token em sucesso,
        // ou 400 com error_code em falha.
        if (response.access_token != null && response.user != null) {
            currentToken = response.access_token
            currentUserId = response.user.id
            val perfil = getPerfilAndroid(currentUserId!!)
            if (perfil != null) AuthResult.Sucesso(perfil)
            else AuthResult.Desconhecido
        } else {
            // Mapear error_code do Supabase para sealed class
            when (response.error_code) {
                "invalid_credentials" -> AuthResult.SenhaErrada
                "user_not_found"      -> AuthResult.EmailNaoEncontrado
                else                  -> AuthResult.Desconhecido
            }
        }
    } catch (e: java.net.UnknownHostException) {
        AuthResult.SemInternet
    } catch (e: java.net.ConnectException) {
        AuthResult.SemInternet
    } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
        AuthResult.SemInternet
    } catch (e: Exception) {
        // Ktor lança exceção em status 4xx dependendo da config —
        // tenta extrair mensagem para diferenciar credenciais vs rede
        val msg = e.message?.lowercase() ?: ""
        when {
            msg.contains("invalid") || msg.contains("credentials") -> AuthResult.SenhaErrada
            msg.contains("network") || msg.contains("connect")     -> AuthResult.SemInternet
            else -> AuthResult.Desconhecido
        }
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
            inserirPerfil(PerfilUsuario(
                id       = currentUserId!!,
                nome     = nome,
                email    = email,
                telefone = telefone,
                tipo     = tipo,
                cpf      = cpf,
                cidade   = cidade,
                estado   = estado,
            ))
            true
        } else false
    } catch (e: Exception) { false }
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
suspend fun getPerfilAndroid(userId: String): PerfilUsuario? {
    return try {
        httpClient.get("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId&select=*") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<PerfilUsuario>>().firstOrNull()
    } catch (e: Exception) { null }
}

private suspend fun inserirPerfil(perfil: PerfilUsuario) {
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

// ── CONSULTAS DO CLIENTE ──────────────────────────────
suspend fun buscarConsultasCliente(userId: String): List<ConsultaCliente> {
    return try {
        httpClient.get("$SUPABASE_URL/rest/v1/consultas") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("cliente_id", "eq.$userId")
            parameter("select", "id,profissional_id,tipo,status,data_agendada,valor,profissionais(area,valor_normal,perfis(nome)),avaliacoes(nota)")
            parameter("order", "data_agendada.desc")
        }.body<List<ConsultaSupabase>>().map { it.toConsultaCliente() }
    } catch (e: Exception) { emptyList() }
}

// ── CONSULTAS DO PROFISSIONAL ─────────────────────────
@Serializable
data class ConsultaProfissional(
    val id: String,
    @SerialName("cliente_id") val clienteId: String? = null,
    val tipo: String,
    val status: String,
    @SerialName("data_agendada") val dataAgendada: String? = null,
    val valor: Int,
    val nomeCliente: String = "",
    val data: String = "--",
    val hora: String = "--",
    val avaliacao: Int = 0,
)

@Serializable
private data class ConsultaProfissionalSupabase(
    val id: String,
    @SerialName("cliente_id") val clienteId: String? = null,
    val tipo: String,
    val status: String,
    @SerialName("data_agendada") val dataAgendada: String? = null,
    val valor: Int,
    val perfis: PerfilNestedSimples? = null,
    val avaliacoes: List<AvaliacaoNested>? = null,
)

suspend fun buscarConsultasProfissional(profissionalId: String): List<ConsultaProfissional> {
    return try {
        httpClient.get("$SUPABASE_URL/rest/v1/consultas") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("profissional_id", "eq.$profissionalId")
            parameter("select", "id,cliente_id,tipo,status,data_agendada,valor,perfis:cliente_id(nome),avaliacoes(nota)")
            parameter("order", "data_agendada.desc")
        }.body<List<ConsultaProfissionalSupabase>>().map { c ->
            val (data, hora) = parseTimestamptz(c.dataAgendada)
            ConsultaProfissional(
                id           = c.id,
                clienteId    = c.clienteId,
                tipo         = c.tipo,
                status       = c.status,
                dataAgendada = c.dataAgendada,
                valor        = c.valor,
                nomeCliente  = c.perfis?.nome ?: "Cliente",
                data         = data,
                hora         = hora,
                avaliacao    = c.avaliacoes?.firstOrNull()?.nota ?: 0,
            )
        }
    } catch (e: Exception) {
        android.util.Log.e("Profissional", "Erro ao buscar consultas: ${e.message}")
        emptyList()
    }
}

// ── AVALIAÇÃO ─────────────────────────────────────────
suspend fun gravarAvaliacaoAndroid(
    consultaId: String,
    clienteId: String,
    profissionalId: String,
    nota: Int,
    comentario: String? = null,
): Boolean {
    return try {
        val request = GravarAvaliacaoRequest(
            consulta_id     = consultaId,
            cliente_id      = clienteId,
            profissional_id = profissionalId,
            nota            = nota,
            comentario      = comentario,
        )
        val response = httpClient.post("$SUPABASE_URL/rest/v1/avaliacoes") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(request)
        }
        response.status.value in 200..299
    } catch (e: Exception) { false }
}

suspend fun atualizarAvaliacaoConsulta(consultaId: String, nota: Int): Boolean {
    return try {
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/consultas?id=eq.$consultaId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(AtualizarAvaliacaoConsultaRequest(avaliacao = nota))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Avaliacao", "Erro ao atualizar consulta: ${e.message}")
        false
    }
}

// ── PROFISSIONAIS PMP ─────────────────────────────────
// aplicarFiltroPMP = true por padrão: BuscaScreen, DashboardClienteScreen e
// demais telas recebem apenas profissionais PMP verificados automaticamente.
// Passe aplicarFiltroPMP = false APENAS em contextos administrativos/internos.
// Para buscar o próprio perfil use getMeuPerfilProfissional() — não usa este parâmetro.
suspend fun getProfissionaisPMPAndroid(
    somenteUrgente: Boolean = false,
    busca: String = "",
    aplicarFiltroPMP: Boolean = true,
): List<ProfissionalComPerfil> {
    return try {
        val result = httpClient.get("$SUPABASE_URL/rest/v1/profissionais") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("select", "*, perfis(nome,email,cidade,estado,foto_url,capa_url)")
            if (aplicarFiltroPMP) {
                parameter("is_pmp",        "eq.true")
                parameter("verificado",    "eq.true")
                parameter("credibilidade", "gte.80")
            }
            parameter("order", "credibilidade.desc")
            if (somenteUrgente) parameter("disponivel_urgente", "eq.true")
        }.body<List<ProfissionalComPerfil>>()

        if (busca.isEmpty()) result
        else result.filter { p ->
            val nome   = p.perfis?.nome ?: ""
            val area   = p.area
            val cidade = p.perfis?.cidade ?: ""
            nome.contains(busca, ignoreCase = true) ||
                    area.contains(busca, ignoreCase = true) ||
                    cidade.contains(busca, ignoreCase = true)
        }
    } catch (e: Exception) { emptyList() }
}

// ── ESTÚDIO ───────────────────────────────────────────
suspend fun getProfissionaisEstudioAndroid(filtroTipo: String = "todos"): List<ItemEstudio> {
    return try {
        var url = "$SUPABASE_URL/rest/v1/estudio?select=*,perfis(nome,foto_url,capa_url)&ativo=eq.true&order=destaque.desc,criado_em.desc"
        if (filtroTipo != "todos") url += "&tipo=eq.$filtroTipo"
        httpClient.get(url) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<Map<String, Any?>>>().map { mapToItemEstudio(it) }
    } catch (e: Exception) { emptyList() }
}

suspend fun getEstudioProfissionalAndroid(profissionalId: String, filtroTipo: String = "todos"): List<ItemEstudio> {
    return try {
        var url = "$SUPABASE_URL/rest/v1/estudio?select=*,perfis(nome,foto_url,capa_url)&ativo=eq.true&profissional_id=eq.$profissionalId&order=destaque.desc,criado_em.desc"
        if (filtroTipo != "todos") url += "&tipo=eq.$filtroTipo"
        httpClient.get(url) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<Map<String, Any?>>>().map { mapToItemEstudio(it) }
    } catch (e: Exception) { emptyList() }
}

@Suppress("UNCHECKED_CAST")
private fun mapToItemEstudio(map: Map<String, Any?>): ItemEstudio {
    val perfis = map["perfis"] as? Map<String, Any?>
    return ItemEstudio(
        id             = map["id"] as? String ?: "",
        profissionalId = map["profissional_id"] as? String ?: "",
        titulo         = map["titulo"] as? String ?: "",
        descricao      = map["descricao"] as? String ?: "",
        tipo           = map["tipo"] as? String ?: "aula",
        preco          = (map["preco"] as? Number)?.toDouble() ?: 0.0,
        precoOriginal  = (map["preco_original"] as? Number)?.toDouble(),
        capaUrl        = map["capa_url"] as? String,
        videoUrl       = map["video_url"] as? String,
        linkExterno    = map["link_externo"] as? String,
        temEntrega     = map["tem_entrega"] as? Boolean ?: false,
        destaque       = map["destaque"] as? Boolean ?: false,
        totalVendas    = (map["total_vendas"] as? Number)?.toInt() ?: 0,
        avaliacaoMedia = (map["avaliacao_media"] as? Number)?.toDouble() ?: 0.0,
        autorNome      = perfis?.get("nome") as? String ?: "",
        autorFotoUrl   = perfis?.get("foto_url") as? String,
        autorCapaUrl   = perfis?.get("capa_url") as? String,
    )
}

// criarItemEstudioAndroid — usa DTO @Serializable.
// Antes: buildString com interpolação quebrava se titulo tivesse aspas.
// Agora: kotlinx.serialization escapa corretamente qualquer caractere.
suspend fun criarItemEstudioAndroid(
    profissionalId: String, titulo: String, descricao: String, tipo: String,
    preco: Double, precoOriginal: Double? = null, videoUrl: String? = null,
    arquivoUrl: String? = null, linkExterno: String? = null,
    temEntrega: Boolean = false, destaque: Boolean = false,
): Boolean {
    return try {
        val request = CriarItemEstudioRequest(
            profissional_id = profissionalId,
            titulo          = titulo,
            descricao       = descricao,
            tipo            = tipo,
            preco           = preco,
            preco_original  = precoOriginal,
            video_url       = videoUrl,
            arquivo_url     = arquivoUrl,
            link_externo    = linkExterno,
            tem_entrega     = temEntrega,
            destaque        = destaque,
        )
        val response = httpClient.post("$SUPABASE_URL/rest/v1/estudio") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(request)
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Estudio", "Erro ao criar item: ${e.message}")
        false
    }
}

// ── UPLOAD DE IMAGEM ──────────────────────────────────
suspend fun uploadImagemSupabase(
    context: android.content.Context,
    uri: android.net.Uri,
    bucket: String,
    userId: String,
): String? {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
        val fileName = "$userId/${System.currentTimeMillis()}.jpg"
        httpClient.put("$SUPABASE_URL/storage/v1/object/$bucket/$fileName") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "image/jpeg")
            setBody(bytes)
        }
        "$SUPABASE_URL/storage/v1/object/public/$bucket/$fileName"
    } catch (e: Exception) {
        android.util.Log.e("Upload", "Erro: ${e.message}")
        null
    }
}

suspend fun salvarFotoPerfilAndroid(userId: String, fotoUrl: String? = null, capaUrl: String? = null): Boolean {
    if (fotoUrl == null && capaUrl == null) return true
    return try {
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(SalvarFotoPerfilRequest(foto_url = fotoUrl, capa_url = capaUrl))
        }
        response.status.value in 200..299
    } catch (e: Exception) { false }
}

suspend fun resetSenhaAndroid(email: String): Boolean {
    return try {
        httpClient.post("$SUPABASE_URL/auth/v1/recover") {
            header("apikey", SUPABASE_KEY)
            header("Content-Type", "application/json")
            setBody(ResetSenhaRequest(email = email))
        }
        true
    } catch (e: Exception) { false }
}

// ── ONBOARDING PROFISSIONAL ───────────────────────────
// atualizarPerfilProfissional — usa DTO @Serializable.
// Antes: replace("\"","\\\"") manual em cada campo — frágil.
// Agora: serialização correta sem risco de injection.
suspend fun atualizarPerfilProfissional(
    userId: String,
    bio: String,
    area: String,
    conselho: String,
    numeroConselho: String,
    precoNormal: Int,
    precoUrgente: Int,
): Boolean {
    return try {
        val request = AtualizarPerfilProfissionalRequest(
            id              = userId,
            descricao       = bio,
            area            = area,
            conselho        = conselho,
            numero_conselho = numeroConselho,
            valor_normal    = precoNormal,
            valor_urgente   = precoUrgente,
        )
        val response = httpClient.post("$SUPABASE_URL/rest/v1/profissionais") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(request)
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Onboarding", "Erro: ${e.message}")
        false
    }
}

// ── DISPONIBILIDADE URGENTE ───────────────────────────
suspend fun atualizarDisponibilidadeUrgente(profissionalId: String, disponivel: Boolean): Boolean {
    return try {
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/profissionais?id=eq.$profissionalId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(DisponibilidadeUrgenteRequest(disponivel_urgente = disponivel))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Urgente", "Erro ao atualizar disponibilidade: ${e.message}")
        false
    }
}

// ── AGENDAMENTO + PEDIDO ──────────────────────────────
// criarAgendamento — usa DTOs @Serializable para consulta e pedido.
suspend fun criarAgendamento(
    clienteId: String,
    profId: String,
    data: String,
    hora: String,
    tipo: String,
    valor: Double,
): String? {
    return try {
        // Montar timestamp ISO 8601: "dd/MM/yyyy" + "HH:mm" → "yyyy-MM-ddTHH:mm:00"
        val partes = data.split("/")
        val dataIso = if (partes.size == 3)
            "${partes[2]}-${partes[1]}-${partes[0]}T${hora}:00"
        else "${data}T${hora}:00"

        val agendamentoRequest = CriarAgendamentoRequest(
            cliente_id      = clienteId,
            profissional_id = profId,
            tipo            = tipo,
            data_agendada   = dataIso,
            valor           = valor.toInt(),
        )

        val consultaResponse = httpClient.post("$SUPABASE_URL/rest/v1/consultas") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=representation")
            setBody(agendamentoRequest)
        }.body<List<Map<String, String?>>>()

        val consultaId = consultaResponse.firstOrNull()?.get("id") ?: return null

        val pedidoRequest = CriarPedidoRequest(
            consulta_id     = consultaId,
            cliente_id      = clienteId,
            profissional_id = profId,
            valor           = valor,
            tipo            = tipo,
        )

        val pedidoResponse = httpClient.post("$SUPABASE_URL/rest/v1/pedidos") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(pedidoRequest)
        }

        // Se o pedido falhou, reverter a consulta criada
        if (pedidoResponse.status.value !in 200..299) {
            try {
                httpClient.delete("$SUPABASE_URL/rest/v1/consultas?id=eq.$consultaId") {
                    header("apikey", SUPABASE_KEY)
                    header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                }
            } catch (e: Exception) {
                android.util.Log.e("Agendamento", "Rollback falhou: ${e.message}")
            }
            return null
        }

        consultaId
    } catch (e: Exception) {
        android.util.Log.e("Agendamento", "Erro ao criar agendamento: ${e.message}")
        null
    }
}

// ── CHAT ──────────────────────────────────────────────
@Serializable
data class Mensagem(
    val id: String,
    val remetente_id: String,
    val destinatario_id: String,
    val texto: String,
    val created_at: String = "",
)

// enviarMensagem — usa DTO @Serializable.
// Antes: replace("\"","\\\"") manual deixava outros caracteres escapar errado.
suspend fun enviarMensagem(remetenteId: String, destinoId: String, texto: String): Boolean {
    return try {
        val request = EnviarMensagemRequest(
            remetente_id    = remetenteId,
            destinatario_id = destinoId,
            texto           = texto,
        )
        val response = httpClient.post("$SUPABASE_URL/rest/v1/mensagens") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(request)
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Chat", "Erro ao enviar: ${e.message}")
        false
    }
}

suspend fun buscarMensagens(meuId: String, outroId: String): List<Mensagem> {
    return try {
        httpClient.get("$SUPABASE_URL/rest/v1/mensagens") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("or", "(and(remetente_id.eq.$meuId,destinatario_id.eq.$outroId),and(remetente_id.eq.$outroId,destinatario_id.eq.$meuId))")
            parameter("order", "created_at.asc")
            parameter("limit", "100")
        }.body<List<Mensagem>>()
    } catch (e: Exception) {
        android.util.Log.e("Chat", "Erro ao buscar: ${e.message}")
        emptyList()
    }
}

// ── ACESSO AO CHAT ────────────────────────────────────
@Serializable
private data class PlanoUsuario(
    val plano_ativo: Boolean = false,
)

@Serializable
private data class LiberacaoChat(
    val id: String,
)

suspend fun verificarAcessoChat(clienteId: String, profissionalId: String): Boolean {
    return try {
        val planoCliente = httpClient.get("$SUPABASE_URL/rest/v1/perfis?id=eq.$clienteId&select=plano_ativo") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<PlanoUsuario>>().firstOrNull()

        if (planoCliente?.plano_ativo == true) return true

        val planoProfissional = httpClient.get("$SUPABASE_URL/rest/v1/perfis?id=eq.$profissionalId&select=plano_ativo") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<PlanoUsuario>>().firstOrNull()

        if (planoProfissional?.plano_ativo == true) return true

        val liberacao = httpClient.get("$SUPABASE_URL/rest/v1/liberacoes_chat") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("cliente_id",      "eq.$clienteId")
            parameter("profissional_id", "eq.$profissionalId")
            parameter("select",          "id")
            parameter("limit",           "1")
        }.body<List<LiberacaoChat>>()

        liberacao.isNotEmpty()
    } catch (e: Exception) {
        android.util.Log.e("Chat", "Erro ao verificar acesso: ${e.message}")
        false
    }
}

// ── EDITAR / EXCLUIR ITEM DO ESTÚDIO ─────────────────
suspend fun editarItemEstudio(itemId: String, novosDados: Map<String, Any>): Boolean {
    return try {
        val body = buildString {
            append("{")
            novosDados.entries.forEachIndexed { i, (k, v) ->
                val valor = when (v) {
                    is String  -> "\"$k\":\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                    is Boolean -> "\"$k\":$v"
                    is Number  -> "\"$k\":$v"
                    else       -> "\"$k\":\"$v\""
                }
                append(valor)
                if (i < novosDados.size - 1) append(",")
            }
            append("}")
        }
        val response = httpClient.patch(
            "$SUPABASE_URL/rest/v1/estudio?id=eq.$itemId&profissional_id=eq.${currentUserId ?: ""}"
        ) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(body)
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Estudio", "Erro ao editar: ${e.message}")
        false
    }
}

suspend fun excluirItemEstudio(itemId: String): Boolean {
    return try {
        val response = httpClient.delete(
            "$SUPABASE_URL/rest/v1/estudio?id=eq.$itemId&profissional_id=eq.${currentUserId ?: ""}"
        ) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Prefer", "return=minimal")
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Estudio", "Erro ao excluir: ${e.message}")
        false
    }
}

@Serializable
data class ResultadoAcesso(
    val acesso: Boolean,
    val motivo: String = "",
    val plano_atual: String? = null,
    val limite: Int? = null,
    val profs_usados: Int? = null,
    val acao: String? = null,
)

suspend fun verificarAcessoAgendamento(clienteId: String, profissionalId: String): ResultadoAcesso {
    return try {
        httpClient.post("$SUPABASE_URL/rest/v1/rpc/verificar_acesso_agendamento") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            setBody("{\"p_cliente_id\":\"$clienteId\",\"p_profissional_id\":\"$profissionalId\"}")
        }.body<ResultadoAcesso>()
    } catch (e: Exception) {
        android.util.Log.e("Plano", "Erro: ${e.message}")
        ResultadoAcesso(acesso = false, motivo = "erro")
    }
}

// ── SALVAR DADOS PESSOAIS DO CLIENTE ─────────────────
suspend fun salvarDadosPerfilAndroid(userId: String, nome: String, telefone: String): Boolean {
    return try {
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(SalvarDadosPerfilRequest(nome = nome, telefone = telefone))
        }
        response.status.value in 200..299
    } catch (e: Exception) { false }
}

// ── REGISTRAR VENDA NO ESTÚDIO ────────────────────────
suspend fun registrarVendaEstudio(itemId: String): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/incrementar_vendas_estudio") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            setBody("{\"p_item_id\":\"$itemId\"}")
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Estudio", "Erro ao registrar venda: ${e.message}")
        false
    }
}

// ── SALVAR BIO + CIDADE DO PROFISSIONAL ──────────────
suspend fun salvarBioProfissionalAndroid(
    userId: String,
    bio: String,
    cidade: String,
    estado: String,
): Boolean {
    return try {
        httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(SalvarCidadeEstadoRequest(cidade = cidade, estado = estado))
        }
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/profissionais?id=eq.$userId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(SalvarDescricaoProfissionalRequest(descricao = bio))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Perfil", "Erro ao salvar bio: ${e.message}")
        false
    }
}

// ── PERFIL PROFISSIONAL INDIVIDUAL ───────────────────
suspend fun getMeuPerfilProfissional(userId: String): ProfissionalComPerfil? {
    return try {
        httpClient.get(
            "$SUPABASE_URL/rest/v1/profissionais?id=eq.$userId&select=*,perfis(nome,email,cidade,estado,foto_url,capa_url)"
        ) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<ProfissionalComPerfil>>().firstOrNull()
    } catch (e: Exception) {
        android.util.Log.e("Perfil", "Erro ao buscar perfil profissional: ${e.message}")
        null
    }
}

// ── SALVAR TOKEN FCM ──────────────────────────────────
// Corrigido: usa SUPABASE_URL e currentToken das constantes do arquivo,
// não mais strings duplicadas. Token salvo via PATCH em perfis.fcm_token.
@Serializable
private data class SalvarFcmTokenRequest(
    val fcm_token: String,
)
suspend fun salvarFcmTokenAndroid(userId: String, token: String): Boolean {
    return try {
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(SalvarFcmTokenRequest(fcm_token = token))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("FCM", "Erro ao salvar token: ${e.message}")
        false
    }
}

// ── ASSINATURAS ───────────────────────────────────────
// Cria assinatura na tabela `assinaturas` e marca plano_ativo=true em `perfis`.
// IMPORTANTE: Em produção, a criação da assinatura deve ser feita por uma
// Edge Function do Supabase acionada pelo webhook do MercadoPago (usando
// service_role), não diretamente pelo app. Esta função é usada apenas
// enquanto a integração real com MercadoPago não está implementada.
// Quando o webhook estiver pronto: aplicar bloco 2 do supabase_rls.sql.
@Serializable
private data class CriarAssinaturaRequest(
    val usuario_id: String,
    val plano_id  : String,
    val status    : String = "ativa",
    val valor_pago: Double,
)

suspend fun criarAssinaturaAndroid(plano: PlanoInfo): Boolean {
    val userId = currentUserId ?: return false
    return try {
        val planoNoBanco = httpClient.get("$SUPABASE_URL/rest/v1/planos?tipo=eq.${plano.id}&select=id&limit=1") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<Map<String, String?>>>().firstOrNull()

        val planoIdBanco = planoNoBanco?.get("id") ?: run {
            android.util.Log.e("Assinatura", "Plano '${plano.id}' não encontrado")
            return false
        }

        val resAssinatura = httpClient.post("$SUPABASE_URL/rest/v1/assinaturas") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(CriarAssinaturaRequest(
                usuario_id = userId,
                plano_id   = planoIdBanco,
                valor_pago = plano.precoDecimal,
            ))
        }
        if (resAssinatura.status.value !in 200..299) return false

        val resPerfil = httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody("{\"plano_ativo\":true}")
        }
        resPerfil.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Assinatura", "Erro: ${e.message}")
        false
    }
}