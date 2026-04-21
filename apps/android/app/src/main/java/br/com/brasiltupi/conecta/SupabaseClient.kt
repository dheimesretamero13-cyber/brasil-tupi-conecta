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
)

@Serializable
data class AuthUser(val id: String)

// ── DTOs INTERNOS: CONSULTA COM JOINs ────────────────
// perfis nested dentro de profissionais
@Serializable
private data class PerfilNestedSimples(
    val nome: String? = null,
)

// profissionais nested dentro de consultas
@Serializable
private data class ProfissionalNested(
    val area: String? = null,
    @SerialName("valor_normal") val valorNormal: Int? = null,
    val perfis: PerfilNestedSimples? = null,
)

// avaliacoes nested dentro de consultas (left join -> lista de 0 ou 1 item)
@Serializable
private data class AvaliacaoNested(
    val nota: Int? = null,
)

// linha principal da tabela consultas com os JOINs
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

// Compatível com API 24+: parse manual do timestamptz do Supabase
// Formato esperado: "2025-04-20T14:30:00+00:00" ou "2025-04-20T14:30:00.000000+00:00"
private fun parseTimestamptz(raw: String?): Pair<String, String> {
    if (raw == null) return "--" to "--"
    return try {
        // Pega apenas a parte antes do fuso: "2025-04-20T14:30:00"
        val semFuso = raw.substringBefore("+").substringBefore("Z").take(19)
        val partes  = semFuso.split("T")
        val dateParts = partes[0].split("-") // [yyyy, MM, dd]
        val timeParts = partes.getOrNull(1)?.split(":")  // [HH, mm, ss]

        val data = "${dateParts[2]}/${dateParts[1]}/${dateParts[0]}"
        val hora = "${timeParts?.get(0) ?: "00"}:${timeParts?.get(1) ?: "00"}"
        data to hora
    } catch (e: Exception) {
        "--" to "--"
    }
}

private fun ConsultaSupabase.toConsultaCliente(): ConsultaCliente {
    val (data, hora) = parseTimestamptz(dataAgendada)

    // avaliada = existe pelo menos 1 registro em avaliacoes para essa consulta
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
suspend fun signInAndroid(email: String, senha: String): PerfilUsuario? {
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
    } catch (e: Exception) { null }
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
                id        = currentUserId!!,
                nome      = nome,
                email     = email,
                telefone  = telefone,
                tipo      = tipo,
                cpf       = cpf,
                cidade    = cidade,
                estado    = estado,
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
            // profissionais -> perfis traz o nome; avaliacoes traz a nota (0 ou 1 linha por consulta)
            parameter("select", "id,profissional_id,tipo,status,data_agendada,valor,profissionais(area,valor_normal,perfis(nome)),avaliacoes(nota)")
            parameter("order", "data_agendada.desc")
        }.body<List<ConsultaSupabase>>().map { it.toConsultaCliente() }
    } catch (e: Exception) { emptyList() }
}

// ── AVALIAÇÃO ─────────────────────────────────────────
// Insere registro em `avaliacoes`. Idempotente via constraint unique em consulta_id.
suspend fun gravarAvaliacaoAndroid(
    consultaId: String,
    clienteId: String,
    profissionalId: String,
    nota: Int,
    comentario: String? = null,
): Boolean {
    return try {
        val body = buildString {
            append("{")
            append("\"consulta_id\":\"$consultaId\",")
            append("\"cliente_id\":\"$clienteId\",")
            append("\"profissional_id\":\"$profissionalId\",")
            append("\"nota\":$nota")
            if (comentario != null) append(",\"comentario\":\"$comentario\"")
            append("}")
        }
        val response = httpClient.post("$SUPABASE_URL/rest/v1/avaliacoes") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            // on conflict (consulta_id unique) → atualiza nota em vez de duplicar
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(body)
        }
        response.status.value in 200..299
    } catch (e: Exception) { false }
}
// ── AVALIAÇÃO DIRETA NA TABELA CONSULTAS ─────────────
suspend fun atualizarAvaliacaoConsulta(consultaId: String, nota: Int): Boolean {
    return try {
        val response = httpClient.patch("$SUPABASE_URL/rest/v1/consultas?id=eq.$consultaId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody("{\"avaliacao\":$nota,\"avaliada\":true}")
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Avaliacao", "Erro ao atualizar consulta: ${e.message}")
        false
    }
}
// ── PROFISSIONAIS PMP ─────────────────────────────────
suspend fun getProfissionaisPMPAndroid(
    somenteUrgente: Boolean = false,
    busca: String = "",
): List<ProfissionalComPerfil> {
    return try {
        val result = httpClient.get("$SUPABASE_URL/rest/v1/profissionais") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("select", "*, perfis(nome,email,cidade,estado,foto_url,capa_url)")
            parameter("is_pmp", "eq.true")
            parameter("verificado", "eq.true")
            parameter("credibilidade", "gte.80")
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

suspend fun criarItemEstudioAndroid(
    profissionalId: String, titulo: String, descricao: String, tipo: String,
    preco: Double, precoOriginal: Double? = null, videoUrl: String? = null,
    arquivoUrl: String? = null, linkExterno: String? = null,
    temEntrega: Boolean = false, destaque: Boolean = false,
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
            if (videoUrl != null)      append("\"video_url\":\"$videoUrl\",")
            if (arquivoUrl != null)    append("\"arquivo_url\":\"$arquivoUrl\",")
            if (linkExterno != null)   append("\"link_externo\":\"$linkExterno\",")
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
    } catch (e: Exception) { false }
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
    return try {
        val campos = mutableListOf<String>()
        if (fotoUrl != null) campos.add("\"foto_url\":\"$fotoUrl\"")
        if (capaUrl != null) campos.add("\"capa_url\":\"$capaUrl\"")
        val body = "{${campos.joinToString(",")}}"
        httpClient.patch("$SUPABASE_URL/rest/v1/perfis?id=eq.$userId") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(body)
        }
        true
    } catch (e: Exception) { false }
}

suspend fun resetSenhaAndroid(email: String): Boolean {
    return try {
        httpClient.post("$SUPABASE_URL/auth/v1/recover") {
            header("apikey", SUPABASE_KEY)
            header("Content-Type", "application/json")
            setBody("{\"email\":\"$email\"}")
        }
        true
    } catch (e: Exception) { false }
}
// ── ONBOARDING PROFISSIONAL ───────────────────────────
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
        val body = buildString {
            append("{")
            append("\"id\":\"$userId\",")
            append("\"descricao\":\"${bio.replace("\"", "\\\"")}\",")
            append("\"area\":\"${area.replace("\"", "\\\"")}\",")
            append("\"conselho\":\"${conselho.replace("\"", "\\\"")}\",")
            append("\"numero_conselho\":\"${numeroConselho.replace("\"", "\\\"")}\",")
            append("\"valor_normal\":$precoNormal,")
            append("\"valor_urgente\":$precoUrgente")
            append("}")
        }
        val response = httpClient.post("$SUPABASE_URL/rest/v1/profissionais") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(body)
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
            setBody("{\"disponivel_urgente\":$disponivel}")
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Urgente", "Erro ao atualizar disponibilidade: ${e.message}")
        false
    }
}
// ── AGENDAMENTO + PEDIDO ──────────────────────────────
suspend fun criarAgendamento(
    clienteId: String,
    profId: String,
    data: String,
    hora: String,
    tipo: String,
    valor: Double,
): String? {
    return try {
        // 1. Montar timestamp ISO 8601 esperado pelo Supabase: "2025-04-20T14:30:00"
        // data no formato "dd/MM/yyyy", hora "HH:mm"
        val partes = data.split("/")
        val dataIso = if (partes.size == 3) "${partes[2]}-${partes[1]}-${partes[0]}T${hora}:00" else "${data}T${hora}:00"

        val consultaBody = buildString {
            append("{")
            append("\"cliente_id\":\"$clienteId\",")
            append("\"profissional_id\":\"$profId\",")
            append("\"tipo\":\"$tipo\",")
            append("\"status\":\"agendada\",")
            append("\"data_agendada\":\"$dataIso\",")
            append("\"valor\":${valor.toInt()}")
            append("}")
        }

        // 2. Inserir consulta e recuperar o id gerado
        val consultaResponse = httpClient.post("$SUPABASE_URL/rest/v1/consultas") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=representation")
            setBody(consultaBody)
        }.body<List<Map<String, String?>>>()

        val consultaId = consultaResponse.firstOrNull()?.get("id") ?: return null

        // 3. Inserir pedido vinculado à consulta
        val pedidoBody = buildString {
            append("{")
            append("\"consulta_id\":\"$consultaId\",")
            append("\"cliente_id\":\"$clienteId\",")
            append("\"profissional_id\":\"$profId\",")
            append("\"valor\":$valor,")
            append("\"status\":\"pendente\",")
            append("\"tipo\":\"$tipo\"")
            append("}")
        }

        val pedidoResponse = httpClient.post("$SUPABASE_URL/rest/v1/pedidos") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody(pedidoBody)
        }

        // 4. Se o pedido falhou, reverter a consulta criada
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

suspend fun enviarMensagem(remetenteId: String, destinoId: String, texto: String): Boolean {
    return try {
        val response = httpClient.post("$SUPABASE_URL/rest/v1/mensagens") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Content-Type", "application/json")
            header("Prefer", "return=minimal")
            setBody("{\"remetente_id\":\"$remetenteId\",\"destinatario_id\":\"$destinoId\",\"texto\":\"${texto.replace("\"", "\\\"")}\"}")
        }
        response.status.value in 200..299
    } catch (e: Exception) {
        android.util.Log.e("Chat", "Erro ao enviar: ${e.message}")
        false
    }
}

suspend fun buscarMensagens(meuId: String, outroId: String): List<Mensagem> {
    return try {
        // Busca mensagens onde eu enviei OU recebi (conversa bilateral)
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
        // 1. Verificar plano ativo do cliente
        val planoCliente = httpClient.get("$SUPABASE_URL/rest/v1/perfis?id=eq.$clienteId&select=plano_ativo") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<PlanoUsuario>>().firstOrNull()

        if (planoCliente?.plano_ativo == true) return true

        // 2. Verificar plano ativo do profissional
        val planoProfissional = httpClient.get("$SUPABASE_URL/rest/v1/perfis?id=eq.$profissionalId&select=plano_ativo") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
        }.body<List<PlanoUsuario>>().firstOrNull()

        if (planoProfissional?.plano_ativo == true) return true

        // 3. Verificar liberação avulsa
        val liberacao = httpClient.get("$SUPABASE_URL/rest/v1/liberacoes_chat") {
            header("apikey", SUPABASE_KEY)
            header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
            header("Accept", "application/json")
            parameter("cliente_id",       "eq.$clienteId")
            parameter("profissional_id",  "eq.$profissionalId")
            parameter("select",           "id")
            parameter("limit",            "1")
        }.body<List<LiberacaoChat>>()

        liberacao.isNotEmpty()
    } catch (e: Exception) {
        android.util.Log.e("Chat", "Erro ao verificar acesso: ${e.message}")
        false
    }
}
// ── EDITAR / EXCLUIR ITEM DO ESTÚDIO (apenas dono) ───
suspend fun editarItemEstudio(itemId: String, novosDados: Map<String, Any>): Boolean {
    return try {
        // Verifica propriedade antes de atualizar: filtra por id E profissional_id = auth.uid()
        val body = buildString {
            append("{")
            novosDados.entries.forEachIndexed { i, (k, v) ->
                val valor = when (v) {
                    is String  -> "\"$k\":\"${v.replace("\"", "\\\"")}\""
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
        // Filtro duplo: id + profissional_id garante que só o dono exclui
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