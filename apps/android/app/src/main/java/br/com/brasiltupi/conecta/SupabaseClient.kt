package br.com.brasiltupi.conecta

import br.com.brasiltupi.conecta.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ── CONSTANTES (via BuildConfig — nunca hardcoded) ────────────────────
private val SUPABASE_URL get() = BuildConfig.SUPABASE_URL
private val SUPABASE_KEY get() = BuildConfig.SUPABASE_KEY

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

val httpClient = HttpClient(Android) {
    install(ContentNegotiation) { json(json) }
}

// ── Aliases de autenticação (via AuthRepository) ──────────────────────
val currentToken: String?  get() = AuthRepository.token
val currentUserId: String? get() = AuthRepository.userId

// ── RESULTADO DE AUTH ─────────────────────────────────────────────────
sealed class AuthResult {
    data class Sucesso(val perfil: PerfilUsuario) : AuthResult()
    object SenhaErrada       : AuthResult()
    object EmailNaoEncontrado: AuthResult()
    object SemInternet       : AuthResult()
    object Desconhecido      : AuthResult()
}

// ── MODELOS ───────────────────────────────────────────────────────────
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
    @SerialName("avaliacao_media") val avaliacao_media: Double? = null,
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

// ── ITEM DO ESTÚDIO ───────────────────────────────────────────────────
// Declaração única e canônica — NÃO redeclarar em EstudioScreen.kt nem em nenhum outro arquivo.
@Serializable
data class ItemEstudio(
    val id: String,
    val profissionalId: String,
    val titulo: String,
    val descricao: String = "",
    val tipo: String = "aula",
    val preco: Double = 0.0,
    val precoOriginal: Double? = null,
    val capaUrl: String? = null,
    val videoUrl: String? = null,
    val videoUploadUrl: String? = null,
    val arquivoPdfUrl: String? = null,
    val linkExterno: String? = null,
    val mimeType: String? = null,
    val temEntrega: Boolean = false,
    val destaque: Boolean = false,
    val totalVendas: Int = 0,
    val avaliacaoMedia: Double = 0.0,
    val autorNome: String = "",
    val autorFotoUrl: String? = null,
    val autorCapaUrl: String? = null,
    // campos por tipo
    val materia: String? = null,
    val duracaoMinutos: Int? = null,
    val nivelAula: String? = null,
    val cargaHorariaH: Int? = null,
    val numModulos: Int? = null,
    val certificado: Boolean = false,
    val nivelCurso: String? = null,
    val autorLivro: String? = null,
    val isbn: String? = null,
    val numPaginas: Int? = null,
    val edicao: String? = null,
    val plataforma: String? = null,
    val versaoProduto: String? = null,
    val suporteIncluido: Boolean = false,
    val linkAcessoDigital: String? = null,
)

// ── PROFISSIONAL PMP ──────────────────────────────────────────────────
// Declaração única e canônica — NÃO redeclarar em BuscaScreen.kt nem em nenhum outro arquivo.
@Serializable
data class ProfissionalPMP(
    val id: Int,
    val supabaseId: String = "",
    val iniciais: String,
    val nome: String,
    val area: String,
    val cidade: String,
    val avaliacao: Double = 5.0,
    val atendimentos: Int = 0,
    val disponivelUrgente: Boolean = false,
    val valorNormal: Int = 80,
    val valorUrgente: Int? = null,
    val conselho: String = "",
    val descricao: String = "",
    val especialidades: List<String> = emptyList(),
)

// ── CURSO ─────────────────────────────────────────────────────────────
@Serializable
data class Curso(
    val id: String,
    val titulo: String,
    val descricao: String = "",
    val progresso: Float = 0f,
    val totalModulos: Int = 0,
    val modulosConcluidos: Int = 0,
)

// ── DTOs INTERNOS ─────────────────────────────────────────────────────
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

// ── DTO: buscarUltimoCursoEmAndamento ─────────────────────────────────
@Serializable
private data class CursoEmAndamentoSupabase(
    val id: String? = null,
    val titulo: String? = null,
    val descricao: String? = null,
    val progresso: Double? = null,
    val total_modulos: Int? = null,
    val modulos_concluidos: Int? = null,
)

// ── DTO: estúdio com perfil nested ────────────────────────────────────
@Serializable
private data class PerfilEstudioNested(
    val nome: String? = null,
    val foto_url: String? = null,
    val capa_url: String? = null,
)

@Serializable
private data class ItemEstudioSupabase(
    val id: String? = null,
    val profissional_id: String? = null,
    val titulo: String? = null,
    val descricao: String? = null,
    val tipo: String? = null,
    val preco: Double? = null,
    val preco_original: Double? = null,
    val capa_url: String? = null,
    val video_url: String? = null,
    val video_upload_url: String? = null,
    val arquivo_pdf_url: String? = null,
    val link_externo: String? = null,
    val mime_type: String? = null,
    val tem_entrega: Boolean? = null,
    val destaque: Boolean? = null,
    val total_vendas: Int? = null,
    val avaliacao_media: Double? = null,
    val materia: String? = null,
    val duracao_minutos: Int? = null,
    val nivel_aula: String? = null,
    val carga_horaria_h: Int? = null,
    val num_modulos: Int? = null,
    val certificado: Boolean? = null,
    val nivel_curso: String? = null,
    val autor_livro: String? = null,
    val isbn: String? = null,
    val num_paginas: Int? = null,
    val edicao: String? = null,
    val plataforma: String? = null,
    val versao_produto: String? = null,
    val suporte_incluido: Boolean? = null,
    val link_acesso_digital: String? = null,
    val perfis: PerfilEstudioNested? = null,
)

private fun ItemEstudioSupabase.toItemEstudio(): ItemEstudio = ItemEstudio(
    id               = id ?: "",
    profissionalId   = profissional_id ?: "",
    titulo           = titulo ?: "",
    descricao        = descricao ?: "",
    tipo             = tipo ?: "aula",
    preco            = preco ?: 0.0,
    precoOriginal    = preco_original,
    capaUrl          = capa_url,
    videoUrl         = video_url,
    videoUploadUrl   = video_upload_url,
    arquivoPdfUrl    = arquivo_pdf_url,
    linkExterno      = link_externo,
    mimeType         = mime_type,
    temEntrega       = tem_entrega ?: false,
    destaque         = destaque ?: false,
    totalVendas      = total_vendas ?: 0,
    avaliacaoMedia   = avaliacao_media ?: 0.0,
    autorNome        = perfis?.nome ?: "",
    autorFotoUrl     = perfis?.foto_url,
    autorCapaUrl     = perfis?.capa_url,
    materia          = materia,
    duracaoMinutos   = duracao_minutos,
    nivelAula        = nivel_aula,
    cargaHorariaH    = carga_horaria_h,
    numModulos       = num_modulos,
    certificado      = certificado ?: false,
    nivelCurso       = nivel_curso,
    autorLivro       = autor_livro,
    isbn             = isbn,
    numPaginas       = num_paginas,
    edicao           = edicao,
    plataforma       = plataforma,
    versaoProduto    = versao_produto,
    suporteIncluido  = suporte_incluido ?: false,
    linkAcessoDigital = link_acesso_digital,
)

// ── REQUEST DTOs ──────────────────────────────────────────────────────
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
private data class ResetSenhaRequest(val email: String)

@Serializable
private data class AtualizarAvaliacaoConsultaRequest(
    val avaliacao: Int,
    val avaliada: Boolean = true,
)

@Serializable
private data class DisponibilidadeUrgenteRequest(
    val disponivel_urgente: Boolean,
)

@Serializable
private data class SalvarDadosPerfilRequest(
    val nome: String,
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

@Serializable
private data class SalvarFcmTokenRequest(val fcm_token: String)

@Serializable
private data class CriarAssinaturaRequest(
    val usuario_id: String,
    val plano_id: String,
    val status: String = "ativa",
    val valor_pago: Double,
)

@Serializable
private data class PlanoAtivoPatch(val plano_ativo: Boolean)

@Serializable
private data class GravarConsentimentoRequest(
    val user_id: String,
    val aceito_termos: Boolean,
    val aceito_privacidade: Boolean,
    val versao_termos: String,
    val timestamp_ms: Long,
)

@Serializable
private data class GravarAceiteTermosRequest(
    val profissional_id: String,
    val versao_termo: String,
    val aceito: Boolean = true,
)

@Serializable
private data class VerificarAceiteTermosRequest(
    val profissional_id: String,
    val versao_termo: String,
)

@Serializable
private data class GravarKycDocumentoRequest(
    val user_id: String,
    val tipo_documento: String,
    val storage_path: String,
    val mime_type: String,
    val status: String = "pending",
)

// ── HELPERS ───────────────────────────────────────────────────────────
private fun parseTimestamptz(raw: String?): Pair<String, String> {
    if (raw == null) return "--" to "--"
    return try {
        val semFuso  = raw.substringBefore("+").substringBefore("Z").take(19)
        val partes   = semFuso.split("T")
        val dateParts = partes[0].split("-")
        val timeParts = partes.getOrNull(1)?.split(":")
        val data = "${dateParts[2]}/${dateParts[1]}/${dateParts[0]}"
        val hora = "${timeParts?.get(0) ?: "00"}:${timeParts?.get(1) ?: "00"}"
        data to hora
    } catch (e: Exception) { "--" to "--" }
}

fun formatarMembroDesde(criadoEm: String?): String {
    if (criadoEm == null) return "--"
    return try {
        val partes = criadoEm.substringBefore("T").split("-")
        val ano = partes[0]
        val mes = when (partes[1]) {
            "01" -> "Janeiro";  "02" -> "Fevereiro"; "03" -> "Março"
            "04" -> "Abril";    "05" -> "Maio";       "06" -> "Junho"
            "07" -> "Julho";    "08" -> "Agosto";     "09" -> "Setembro"
            "10" -> "Outubro";  "11" -> "Novembro";   "12" -> "Dezembro"
            else -> partes[1]
        }
        "$mes $ano"
    } catch (e: Exception) { "--" }
}

private fun ConsultaSupabase.toConsultaCliente(): ConsultaCliente {
    val (data, hora) = parseTimestamptz(dataAgendada)
    val avaliacao    = avaliacoes?.firstOrNull()
    val avaliada     = avaliacao != null
    val nota         = avaliacao?.nota ?: 0
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

// ── AUTH ──────────────────────────────────────────────────────────────
suspend fun signInAndroid(email: String, senha: String): AuthResult {
    return try {
        val response = httpClient.post("$SUPABASE_URL/auth/v1/token?grant_type=password") {
            header("apikey", SUPABASE_KEY)
            header("Content-Type", "application/json")
            setBody(AuthRequest(email, senha))
        }.body<AuthResponse>()
        if (response.access_token != null && response.user != null) {
            val perfil = getPerfilAndroid(response.user.id)
            if (perfil != null) {
                AuthRepository.login(token = response.access_token, perfil = perfil)
                AuthResult.Sucesso(perfil)
            } else {
                AuthResult.Desconhecido
            }
        } else {
            when (response.error_code) {
                "invalid_credentials" -> AuthResult.SenhaErrada
                "user_not_found"      -> AuthResult.EmailNaoEncontrado
                else                  -> AuthResult.Desconhecido
            }
        }
    } catch (e: java.net.UnknownHostException)                           { AuthResult.SemInternet
    } catch (e: java.net.ConnectException)                               { AuthResult.SemInternet
    } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException)      { AuthResult.SemInternet
    } catch (e: Exception) {
        val msg = e.message?.lowercase() ?: ""
        when {
            msg.contains("invalid") || msg.contains("credentials") -> AuthResult.SenhaErrada
            msg.contains("network") || msg.contains("connect")     -> AuthResult.SemInternet
            else -> AuthResult.Desconhecido
        }
    }
}

suspend fun signUpAndroid(
    email: String, senha: String, nome: String, telefone: String, tipo: String,
    cpf: String? = null, cidade: String? = null, estado: String? = null,
): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/auth/v1/signup") {
            header("apikey", SUPABASE_KEY)
            header("Content-Type", "application/json")
            setBody(AuthRequest(email, senha))
        }.body<AuthResponse>()
        val token  = response.access_token ?: return false
        val userId = response.user?.id     ?: return false
        val perfilProvisorio = PerfilUsuario(
            id = userId, nome = nome, email = email, telefone = telefone,
            tipo = tipo, cpf = cpf, cidade = cidade, estado = estado,
        )
        AuthRepository.login(token = token, perfil = perfilProvisorio)
        inserirPerfil(perfilProvisorio)
        true
    } catch (e: Exception) {
        AppLogger.erroRede("signUpAndroid", e, "email=$email")
        false
    }
}

suspend fun signOutAndroid() {
    try {
        httpClient.post("$SUPABASE_URL/auth/v1/logout") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${AuthRepository.token}")
        }
    } catch (e: Exception) {
        AppLogger.aviso("SupabaseClient", "signOut silenciado: ${e.message}")
    }
    AuthRepository.logout()
}

// ── PERFIS ────────────────────────────────────────────────────────────
suspend fun getPerfilAndroid(userId: String): PerfilUsuario? {
    return try {
        httpClient.get("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId&select=*") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<PerfilUsuario>>().firstOrNull()
    } catch (e: Exception) {
        AppLogger.erroRede("getPerfilAndroid", e, "userId=$userId")
        null
    }
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
    } catch (e: Exception) {
        AppLogger.erroRede("inserirPerfil", e, "userId=${perfil.id}")
    }
}

// ── CONSULTAS DO CLIENTE ──────────────────────────────────────────────
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
    } catch (e: Exception) {
        AppLogger.erroRede("buscarConsultasCliente", e, "userId=$userId")
        emptyList()
    }
}

// ── CONSULTAS DO PROFISSIONAL ─────────────────────────────────────────
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
        AppLogger.erroRede("buscarConsultasProfissional", e, "profId=$profissionalId")
        emptyList()
    }
}

// ── CURSOS / BIBLIOTECA ───────────────────────────────────────────────
// ✅ DTO @Serializable em vez de Map<String, Any?>
suspend fun buscarUltimoCursoEmAndamento(userId: String): Curso? {
    return try {
        val resultado = httpClient.get("$SUPABASE_URL/rest/v1/cliente_cursos") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("cliente_id", "eq.$userId")
            parameter("select", "id,titulo,descricao,progresso,total_modulos,modulos_concluidos")
            parameter("order", "atualizado_em.desc")
            parameter("limit", "1")
        }.body<List<CursoEmAndamentoSupabase>>().firstOrNull()

        resultado?.let {
            Curso(
                id                = it.id ?: "",
                titulo            = it.titulo ?: "",
                descricao         = it.descricao ?: "",
                progresso         = (it.progresso?.toFloat() ?: 0f) / 100f,
                totalModulos      = it.total_modulos ?: 0,
                modulosConcluidos = it.modulos_concluidos ?: 0,
            )
        }
    } catch (e: Exception) {
        AppLogger.erroRede("buscarUltimoCursoEmAndamento", e, "userId=$userId")
        null
    }
}

// ── AVALIAÇÃO ─────────────────────────────────────────────────────────
suspend fun gravarAvaliacaoAndroid(
    consultaId: String, clienteId: String, profissionalId: String,
    nota: Int, comentario: String? = null,
): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/avaliacoes") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(GravarAvaliacaoRequest(
                consulta_id     = consultaId,
                cliente_id      = clienteId,
                profissional_id = profissionalId,
                nota            = nota,
                comentario      = comentario,
            ))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("gravarAvaliacaoAndroid", e, "consultaId=$consultaId")
        false
    }
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
        AppLogger.erroRede("atualizarAvaliacaoConsulta", e, "consultaId=$consultaId")
        false
    }
}

// ── PROFISSIONAIS PMP ─────────────────────────────────────────────────
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
                parameter("is_pmp",       "eq.true")
                parameter("verificado",   "eq.true")
                parameter("credibilidade","gte.80")
            }
            parameter("order", "credibilidade.desc")
            if (somenteUrgente) parameter("disponivel_urgente", "eq.true")
        }.body<List<ProfissionalComPerfil>>()
        if (busca.isEmpty()) result
        else result.filter { p ->
            val nome = p.perfis?.nome ?: ""
            nome.contains(busca, ignoreCase = true)                   ||
                    p.area.contains(busca, ignoreCase = true)             ||
                    (p.perfis?.cidade ?: "").contains(busca, ignoreCase = true)
        }
    } catch (e: Exception) {
        AppLogger.erroRede("getProfissionaisPMPAndroid", e, "busca=$busca")
        emptyList()
    }
}

// ── ESTÚDIO ───────────────────────────────────────────────────────────
// ✅ Deserialização via DTO @Serializable em vez de Map<String, Any?>
suspend fun getProfissionaisEstudioAndroid(filtroTipo: String = "todos"): List<ItemEstudio> {
    return try {
        val url = buildString {
            append("$SUPABASE_URL/rest/v1/estudio")
            append("?select=*,perfis(nome,foto_url,capa_url)")
            append("&ativo=eq.true")
            append("&order=destaque.desc,criado_em.desc")
            if (filtroTipo != "todos") append("&tipo=eq.$filtroTipo")
        }
        httpClient.get(url) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<ItemEstudioSupabase>>().map { it.toItemEstudio() }
    } catch (e: Exception) {
        AppLogger.erroRede("getProfissionaisEstudioAndroid", e, "filtro=$filtroTipo")
        emptyList()
    }
}

suspend fun getEstudioProfissionalAndroid(profissionalId: String, filtroTipo: String = "todos"): List<ItemEstudio> {
    return try {
        val url = buildString {
            append("$SUPABASE_URL/rest/v1/estudio")
            append("?select=*,perfis(nome,foto_url,capa_url)")
            append("&ativo=eq.true")
            append("&profissional_id=eq.$profissionalId")
            append("&order=destaque.desc,criado_em.desc")
            if (filtroTipo != "todos") append("&tipo=eq.$filtroTipo")
        }
        httpClient.get(url) {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<ItemEstudioSupabase>>().map { it.toItemEstudio() }
    } catch (e: Exception) {
        AppLogger.erroRede("getEstudioProfissionalAndroid", e, "profId=$profissionalId filtro=$filtroTipo")
        emptyList()
    }
}

suspend fun criarItemEstudioAndroid(
    profissionalId: String, titulo: String, descricao: String, tipo: String,
    preco: Double, precoOriginal: Double? = null, videoUrl: String? = null,
    arquivoUrl: String? = null, linkExterno: String? = null,
    temEntrega: Boolean = false, destaque: Boolean = false,
): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/estudio") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(CriarItemEstudioRequest(
                profissional_id = profissionalId, titulo = titulo, descricao = descricao,
                tipo = tipo, preco = preco, preco_original = precoOriginal,
                video_url = videoUrl, arquivo_url = arquivoUrl, link_externo = linkExterno,
                tem_entrega = temEntrega, destaque = destaque,
            ))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("criarItemEstudioAndroid", e, "profId=$profissionalId")
        false
    }
}

// ── UPLOAD DE IMAGEM ──────────────────────────────────────────────────
suspend fun uploadImagemSupabase(
    context: android.content.Context,
    uri: android.net.Uri,
    bucket: String,
    userId: String,
): String? {
    return try {
        val bytes    = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
        val fileName = "$userId/${System.currentTimeMillis()}.jpg"
        httpClient.put("$SUPABASE_URL/storage/v1/object/$bucket/$fileName") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "image/jpeg")
            setBody(bytes)
        }
        "$SUPABASE_URL/storage/v1/object/public/$bucket/$fileName"
    } catch (e: Exception) {
        AppLogger.erroRede("uploadImagemSupabase", e, "bucket=$bucket userId=$userId")
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
    } catch (e: Exception) {
        AppLogger.erroRede("salvarFotoPerfilAndroid", e, "userId=$userId")
        false
    }
}

suspend fun resetSenhaAndroid(email: String): Boolean {
    return try {
        httpClient.post("$SUPABASE_URL/auth/v1/recover") {
            header("apikey", SUPABASE_KEY)
            header("Content-Type", "application/json")
            setBody(ResetSenhaRequest(email = email))
        }
        true
    } catch (e: Exception) {
        AppLogger.erroRede("resetSenhaAndroid", e, "email=$email")
        false
    }
}

// ── ONBOARDING PROFISSIONAL ───────────────────────────────────────────
suspend fun atualizarPerfilProfissional(
    userId: String, bio: String, area: String, conselho: String,
    numeroConselho: String, precoNormal: Int, precoUrgente: Int,
): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/profissionais") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(AtualizarPerfilProfissionalRequest(
                id              = userId,
                descricao       = bio,
                area            = area,
                conselho        = conselho,
                numero_conselho = numeroConselho,
                valor_normal    = precoNormal,
                valor_urgente   = precoUrgente,
            ))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("atualizarPerfilProfissional", e, "userId=$userId")
        false
    }
}

// ── DISPONIBILIDADE URGENTE ───────────────────────────────────────────
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
        AppLogger.erroRede("atualizarDisponibilidadeUrgente", e, "profId=$profissionalId")
        false
    }
}

// ── AGENDAMENTO + PEDIDO ──────────────────────────────────────────────
suspend fun criarAgendamento(
    clienteId: String, profId: String, data: String, hora: String,
    tipo: String, valor: Double,
): String? {
    return try {
        val partes  = data.split("/")
        val dataIso = if (partes.size == 3)
            "${partes[2]}-${partes[1]}-${partes[0]}T${hora}:00"
        else
            "${data}T${hora}:00"

        // 1. Criar consulta
        val consultaResponse = httpClient.post("$SUPABASE_URL/rest/v1/consultas") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=representation")
            setBody(CriarAgendamentoRequest(
                cliente_id      = clienteId,
                profissional_id = profId,
                tipo            = tipo,
                data_agendada   = dataIso,
                valor           = valor.toInt(),
            ))
        }.body<List<Map<String, String?>>>()
        val consultaId = consultaResponse.firstOrNull()?.get("id") ?: return null

        // 2. Criar pedido
        val pedidoResponse = httpClient.post("$SUPABASE_URL/rest/v1/pedidos") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(CriarPedidoRequest(
                consulta_id     = consultaId,
                cliente_id      = clienteId,
                profissional_id = profId,
                valor           = valor,
                tipo            = tipo,
            ))
        }

        // 3. Rollback se pedido falhou
        if (pedidoResponse.status.value !in 200..299) {
            try {
                httpClient.delete("$SUPABASE_URL/rest/v1/consultas?id=eq.$consultaId") {
                    header("apikey", SUPABASE_KEY)
                    header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                }
            } catch (e: Exception) {
                AppLogger.erroRede("criarAgendamento_rollback", e, "consultaId=$consultaId")
            }
            return null
        }
        consultaId
    } catch (e: Exception) {
        AppLogger.erroRede("criarAgendamento", e, "clienteId=$clienteId profId=$profId")
        null
    }
}

// ── CHAT ──────────────────────────────────────────────────────────────
@Serializable
data class Mensagem(
    val id: String,
    val remetente_id: String,
    val destinatario_id: String,
    val texto: String,
    val created_at: String = "",
)

suspend fun enviarMensagem(remetenteId: String, destinoId: String, texto: String): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/mensagens") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(EnviarMensagemRequest(
                remetente_id    = remetenteId,
                destinatario_id = destinoId,
                texto           = texto,
            ))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("enviarMensagem", e, "de=$remetenteId para=$destinoId")
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
        AppLogger.erroRede("buscarMensagens", e, "eu=$meuId outro=$outroId")
        emptyList()
    }
}

// ── ACESSO AO CHAT ────────────────────────────────────────────────────
@Serializable
private data class PlanoUsuario(val plano_ativo: Boolean = false)

@Serializable
private data class LiberacaoChat(val id: String)

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
            parameter("select", "id")
            parameter("limit", "1")
        }.body<List<LiberacaoChat>>()
        liberacao.isNotEmpty()
    } catch (e: Exception) {
        AppLogger.erroRede("verificarAcessoChat", e, "clienteId=$clienteId profId=$profissionalId")
        false
    }
}

// ── EDITAR / EXCLUIR ITEM DO ESTÚDIO ─────────────────────────────────
// ✅ buildJsonObject em vez de buildString com escape manual
suspend fun editarItemEstudio(itemId: String, novosDados: Map<String, Any>): Boolean {
    return try {
        val body = buildJsonObject {
            novosDados.forEach { (key, value) ->
                when (value) {
                    is String  -> put(key, value)
                    is Boolean -> put(key, value)
                    is Number  -> put(key, value)
                    else       -> put(key, value.toString())
                }
            }
        }
        val response = httpClient.patch(
            "$SUPABASE_URL/rest/v1/estudio?id=eq.$itemId&profissional_id=eq.${currentUserId ?: ""}"
        ) {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(body.toString())
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("editarItemEstudio", e, "itemId=$itemId")
        false
    }
}

suspend fun excluirItemEstudio(itemId: String): Boolean {
    return try {
        val response = httpClient.delete(
            "$SUPABASE_URL/rest/v1/estudio?id=eq.$itemId&profissional_id=eq.${currentUserId ?: ""}"
        ) {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Prefer",        "return=minimal")
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("excluirItemEstudio", e, "itemId=$itemId")
        false
    }
}

// ── RESULTADO DE ACESSO ───────────────────────────────────────────────
@Serializable
data class ResultadoAcesso(
    val acesso: Boolean,
    val motivo: String = "",
    val plano_atual: String? = null,
    val limite: Int? = null,
    val profs_usados: Int? = null,
    val acao: String? = null,
)

// ✅ buildJsonObject em vez de interpolação manual de String
suspend fun verificarAcessoAgendamento(clienteId: String, profissionalId: String): ResultadoAcesso {
    return try {
        httpClient.post("$SUPABASE_URL/rest/v1/rpc/verificar_acesso_agendamento") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            setBody(buildJsonObject {
                put("p_cliente_id",      clienteId)
                put("p_profissional_id", profissionalId)
            }.toString())
        }.body<ResultadoAcesso>()
    } catch (e: Exception) {
        AppLogger.erroRede("verificarAcessoAgendamento", e, "clienteId=$clienteId")
        ResultadoAcesso(acesso = false, motivo = "erro")
    }
}

// ── PLANO DE ASSINATURA ───────────────────────────────────────────────
data class PlanoInfo(
    val id: String,
    val nome: String,
    val precoDecimal: Double,
    val limiteProfs: Int = 0,
)

// ── SALVAR DADOS PESSOAIS ─────────────────────────────────────────────
suspend fun salvarDadosPerfilAndroid(userId: String, nome: String, telefone: String): Boolean {
    return try {
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(SalvarDadosPerfilRequest(nome = nome, telefone = telefone))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("salvarDadosPerfilAndroid", e, "userId=$userId")
        false
    }
}

// ── REGISTRAR VENDA ───────────────────────────────────────────────────
// ✅ buildJsonObject em vez de interpolação manual
suspend fun registrarVendaEstudio(itemId: String): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/incrementar_vendas_estudio") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            setBody(buildJsonObject { put("p_item_id", itemId) }.toString())
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("registrarVendaEstudio", e, "itemId=$itemId")
        false
    }
}

// ── SALVAR BIO + CIDADE ───────────────────────────────────────────────
suspend fun salvarBioProfissionalAndroid(
    userId: String, bio: String, cidade: String, estado: String,
): Boolean {
    return try {
        httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(SalvarCidadeEstadoRequest(cidade = cidade, estado = estado))
        }
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/profissionais?id=eq.$userId") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(SalvarDescricaoProfissionalRequest(descricao = bio))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("salvarBioProfissionalAndroid", e, "userId=$userId")
        false
    }
}

// ── PERFIL PROFISSIONAL INDIVIDUAL ────────────────────────────────────
suspend fun getMeuPerfilProfissional(userId: String): ProfissionalComPerfil? {
    return try {
        httpClient.get(
            "$SUPABASE_URL/rest/v1/profissionais?id=eq.$userId&select=*,perfis(nome,email,cidade,estado,foto_url,capa_url)"
        ) {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept",        "application/json")
        }.body<List<ProfissionalComPerfil>>().firstOrNull()
    } catch (e: Exception) {
        AppLogger.erroRede("getMeuPerfilProfissional", e, "userId=$userId")
        null
    }
}

// ── SALVAR TOKEN FCM ──────────────────────────────────────────────────
suspend fun salvarFcmTokenAndroid(userId: String, token: String): Boolean {
    return try {
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(SalvarFcmTokenRequest(fcm_token = token))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("salvarFcmTokenAndroid", e, "userId=$userId")
        false
    }
}

// ── ASSINATURAS ───────────────────────────────────────────────────────
// ✅ DTO PlanoAtivoPatch em vez de string literal hardcoded
suspend fun criarAssinaturaAndroid(plano: PlanoInfo): Boolean {
    val userId = currentUserId ?: return false
    return try {
        val planoNoBanco = httpClient.get("$SUPABASE_URL/rest/v1/planos?tipo=eq.${plano.id}&select=id&limit=1") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept",        "application/json")
        }.body<List<Map<String, String?>>>().firstOrNull()

        val planoIdBanco = planoNoBanco?.get("id") ?: run {
            AppLogger.aviso("Assinatura", "Plano '${plano.id}' não encontrado no banco")
            return false
        }

        val resAssinatura = httpClient.post("$SUPABASE_URL/rest/v1/assinaturas") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(CriarAssinaturaRequest(
                usuario_id = userId,
                plano_id   = planoIdBanco,
                valor_pago = plano.precoDecimal,
            ))
        }
        if (resAssinatura.status.value !in 200..299) return false

        val resPerfil = httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(PlanoAtivoPatch(plano_ativo = true))
        }
        resPerfil.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("criarAssinaturaAndroid", e, "plano=${plano.id} userId=$userId")
        false
    }
}

// ── LGPD — CONSENTIMENTO ──────────────────────────────────────────────
suspend fun gravarConsentimento(
    userId: String, aceitoTermos: Boolean, aceitoPriv: Boolean, versaoTermos: String,
): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/user_consents") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(GravarConsentimentoRequest(
                user_id            = userId,
                aceito_termos      = aceitoTermos,
                aceito_privacidade = aceitoPriv,
                versao_termos      = versaoTermos,
                timestamp_ms       = System.currentTimeMillis(),
            ))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("gravarConsentimento", e, "userId=$userId")
        false
    }
}

@Serializable
private data class ConsentimentoId(val id: String)

suspend fun verificarConsentimentoExiste(userId: String): Boolean {
    if (userId.isEmpty()) return false
    return try {
        val result = httpClient.get("$SUPABASE_URL/rest/v1/user_consents") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept",        "application/json")
            parameter("user_id",       "eq.$userId")
            parameter("aceito_termos", "eq.true")
            parameter("select",        "id")
            parameter("limit",         "1")
        }.body<List<ConsentimentoId>>()
        result.isNotEmpty()
    } catch (e: Exception) {
        AppLogger.erroRede("verificarConsentimentoExiste", e, "userId=$userId")
        false
    }
}

// ── TERMOS DE URGÊNCIA ────────────────────────────────────────────────
private const val VERSAO_TERMOS_URGENCIA = "1.0"

suspend fun gravarAceiteTermosUrgencia(profissionalId: String): Boolean {
    return try {
        val token    = currentToken ?: return false
        val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/gravar_aceite_termos_urgencia") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer $token")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(GravarAceiteTermosRequest(
                profissional_id = profissionalId,
                versao_termo    = VERSAO_TERMOS_URGENCIA,
            ))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("gravarAceiteTermosUrgencia", e, "profId=$profissionalId")
        false
    }
}

suspend fun verificarAceiteTermosUrgencia(profissionalId: String): Boolean {
    return try {
        val token    = currentToken ?: return false
        val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/verificar_aceite_termos_urgencia") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer $token")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=representation")
            setBody(VerificarAceiteTermosRequest(
                profissional_id = profissionalId,
                versao_termo    = VERSAO_TERMOS_URGENCIA,
            ))
        }
        if (response.status.value !in 200..299) return false
        response.bodyAsText().trim() == "true"
    } catch (e: Exception) {
        AppLogger.aviso("TermosUrgencia", "verificar profId=$profissionalId: ${e.message}")
        false
    }
}

// ── KYC — DOCUMENTOS ──────────────────────────────────────────────────
@Serializable
data class KycDocumento(
    val id: String,
    val tipo_documento: String,
    val status: String,
    val motivo_rejeicao: String? = null,
    val criado_em: String,
    val atualizado_em: String? = null,
)

suspend fun gravarKycDocumento(userId: String, tipo: String, storagePath: String, mimeType: String): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/kyc_documents") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=minimal")
            setBody(GravarKycDocumentoRequest(
                user_id       = userId,
                tipo_documento = tipo,
                storage_path  = storagePath,
                mime_type     = mimeType,
            ))
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        AppLogger.erroRede("gravarKycDocumento", e, "userId=$userId tipo=$tipo")
        false
    }
}

suspend fun buscarKycDocumentos(userId: String): List<KycDocumento> {
    return try {
        httpClient.get("$SUPABASE_URL/rest/v1/kyc_documents") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept",        "application/json")
            parameter("user_id", "eq.$userId")
            parameter("select",  "id,tipo_documento,status,motivo_rejeicao,criado_em,atualizado_em")
            parameter("order",   "criado_em.desc")
        }.body<List<KycDocumento>>()
    } catch (e: Exception) {
        AppLogger.erroRede("buscarKycDocumentos", e, "userId=$userId")
        emptyList()
    }
}

suspend fun verificarKycAprovado(userId: String): Boolean {
    return try {
        buscarKycDocumentos(userId).any { it.status == "approved" }
    } catch (_: Exception) { false }
}

suspend fun uploadKycDocumento(userId: String, tipo: String, bytes: ByteArray, mimeType: String): String? {
    return try {
        val ext     = when (mimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; else -> "pdf" }
        val caminho = "profissionais/$userId/${tipo.lowercase()}_${System.currentTimeMillis()}.$ext"
        val response = httpClient.put("$SUPABASE_URL/storage/v1/object/kyc-documents/$caminho") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  mimeType)
            setBody(bytes)
        }
        if (response.status.value in 200..299) caminho else null
    } catch (e: Exception) {
        AppLogger.erroRede("uploadKycDocumento", e, "userId=$userId tipo=$tipo")
        null
    }
}

// ── EXCLUIR CONTA ─────────────────────────────────────────────────────
suspend fun excluirContaAndroid(): Boolean {
    val token = currentToken ?: return false
    return try {
        val response = httpClient.post("$SUPABASE_URL/functions/v1/excluir-conta") {
            header("Authorization", "Bearer $token")
            header("apikey",        SUPABASE_KEY)
            header("Content-Type",  "application/json")
            setBody("{}")
        }
        if (response.status.value in 200..299) {
            AppLogger.info("SupabaseClient", "Conta excluída com sucesso")
            true
        } else {
            AppLogger.erroAuth(operacao = "excluir_conta", mensagemExtra = "HTTP ${response.status.value}")
            false
        }
    } catch (e: Exception) {
        AppLogger.erroRede("functions/v1/excluir-conta", e, "excluir_conta")
        false
    }
}

// ── ACESSO A PRODUTOS DO ESTÚDIO ──────────────────────────────────────
// ✅ POST (correto para RPCs) + buildJsonObject em vez de GET com body e interpolação manual
suspend fun verificarAcessoProduto(clienteId: String, itemId: String): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/verificar_acesso_produto") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            setBody(buildJsonObject {
                put("p_cliente_id", clienteId)
                put("p_item_id",    itemId)
            }.toString())
        }
        if (response.status.value == 200) response.bodyAsText().trim() == "true" else false
    } catch (e: Exception) {
        AppLogger.erroRede("verificarAcessoProduto", e, "clienteId=$clienteId itemId=$itemId")
        false
    }
}

// ── REEMBOLSO DE PRODUTOS DO ESTÚDIO ──────────────────────────────────
// ✅ buildJsonObject em vez de escape manual da string motivo
suspend fun solicitarReembolsoEstudio(purchaseId: String, motivo: String): String {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/solicitar_reembolso_estudio") {
            header("apikey",        SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type",  "application/json")
            header("Prefer",        "return=representation")
            setBody(buildJsonObject {
                put("p_purchase_id", purchaseId)
                put("p_motivo",      motivo)
            }.toString())
        }
        if (response.status.value in 200..299)
            response.bodyAsText().trim().removeSurrounding("\"")
        else "erro_servidor"
    } catch (e: Exception) {
        AppLogger.erroRede("solicitarReembolsoEstudio", e, "purchaseId=$purchaseId")
        "erro_rede"
    }
}