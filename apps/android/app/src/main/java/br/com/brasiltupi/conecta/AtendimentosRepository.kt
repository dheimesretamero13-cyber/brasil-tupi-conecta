package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AtendimentosRepository.kt
//
// Responsabilidades:
//  1. DTOs de Modalidade, Slot e Agendamento Regular
//  2. Buscar modalidades de um profissional (para o cliente escolher)
//  3. Buscar slots disponíveis via RPC `buscar_slots_disponiveis`
//  4. Criar agendamento regular com idempotency-key
//  5. Listar agendamentos regulares (cliente e profissional)
//  6. Cancelar agendamento (com regra de taxa: 10min após criação)
//  7. Operações do profissional: CRUD de modalidades e disponibilidade
// ═══════════════════════════════════════════════════════════════════════════

import android.util.Log
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "AtendimentosRepository"
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

// ═══════════════════════════════════════════════════════════════════════════
// DTOs PÚBLICOS
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
data class ModalidadeAtendimento(
    val id:                       String  = "",
    @SerialName("profissional_id") val profissionalId: String = "",
    val tipo:                     String  = "",   // "minutos" | "hora" | "semanal" | "mensal"
    val titulo:                   String  = "",
    val descricao:                String? = null,
    @SerialName("duracao_minutos") val duracaoMinutos: Int? = null,
    @SerialName("sessoes_por_semana") val sessoesPorSemana: Int? = null,
    @SerialName("sessoes_total") val sessoesTotal: Int? = null,
    val valor:                    Double  = 0.0,
    val ativo:                    Boolean = true,
)

@Serializable
data class SlotDisponivel(
    @SerialName("slot_id")        val slotId:         String = "",
    @SerialName("modalidade_id")  val modalidadeId:   String = "",
    @SerialName("modalidade_tipo") val modalidadeTipo: String = "",
    @SerialName("hora_inicio")    val horaInicio:     String = "",
    @SerialName("hora_fim")       val horaFim:        String = "",
    val valor:                    Double = 0.0,
)

@Serializable
data class AgendamentoRegular(
    val id:                    String  = "",
    @SerialName("cliente_id")       val clienteId:      String  = "",
    @SerialName("profissional_id")  val profissionalId: String  = "",
    @SerialName("modalidade_id")    val modalidadeId:   String  = "",
    @SerialName("data_agendada")    val dataAgendada:   String  = "",
    @SerialName("hora_inicio")      val horaInicio:     String  = "",
    @SerialName("hora_fim")         val horaFim:        String  = "",
    @SerialName("valor_cobrado")    val valorCobrado:   Double  = 0.0,
    val status:                String  = "pendente",
    val avaliado:              Boolean = false,
    @SerialName("criado_em")        val criadoEm:       String? = null,
    // Campos enriquecidos (JOIN) — opcionais
    val nomeProfissional:      String? = null,
    val nomeCliente:           String? = null,
    val tituloModalidade:      String? = null,
)

@Serializable
data class DisponibilidadeRegular(
    val id:               String  = "",
    @SerialName("profissional_id") val profissionalId: String = "",
    @SerialName("modalidade_id")   val modalidadeId:   String? = null,
    @SerialName("dia_semana")      val diaSemana:      String = "",
    @SerialName("hora_inicio")     val horaInicio:     String = "",
    @SerialName("hora_fim")        val horaFim:        String = "",
    val cancelado:        Boolean = false,
)

// ═══════════════════════════════════════════════════════════════════════════
// DTOs PRIVADOS (request bodies)
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
private data class BuscarSlotsRequest(
    @SerialName("p_profissional_id") val profissionalId: String,
    @SerialName("p_data")            val data:           String,
    @SerialName("p_modalidade_id")   val modalidadeId:   String? = null,
)

@Serializable
private data class CriarAgendamentoRegularRequest(
    @SerialName("cliente_id")       val clienteId:      String,
    @SerialName("profissional_id")  val profissionalId: String,
    @SerialName("modalidade_id")    val modalidadeId:   String,
    @SerialName("slot_id")          val slotId:         String? = null,
    @SerialName("data_agendada")    val dataAgendada:   String,
    @SerialName("hora_inicio")      val horaInicio:     String,
    @SerialName("hora_fim")         val horaFim:        String,
    @SerialName("valor_cobrado")    val valorCobrado:   Double,
    @SerialName("idempotency_key")  val idempotencyKey: String,
    val status:                     String = "pendente",
)

@Serializable
private data class CriarModalidadeRequest(
    @SerialName("profissional_id")    val profissionalId:   String,
    val tipo:                         String,
    val titulo:                       String,
    val descricao:                    String? = null,
    @SerialName("duracao_minutos")    val duracaoMinutos:   Int? = null,
    @SerialName("sessoes_por_semana") val sessoesPorSemana: Int? = null,
    @SerialName("sessoes_total")      val sessoesTotal:     Int? = null,
    val valor:                        Double,
    val ativo:                        Boolean = true,
)

@Serializable
private data class AtualizarModalidadeRequest(
    val titulo:                          String,
    val descricao:                       String? = null,
    @SerialName("duracao_minutos")       val duracaoMinutos:   Int? = null,
    @SerialName("sessoes_por_semana")    val sessoesPorSemana: Int? = null,
    @SerialName("sessoes_total")         val sessoesTotal:     Int? = null,
    val valor:                           Double,
    val ativo:                           Boolean,
)

@Serializable
private data class CriarDisponibilidadeRequest(
    @SerialName("profissional_id") val profissionalId: String,
    @SerialName("modalidade_id")   val modalidadeId:   String? = null,
    @SerialName("dia_semana")      val diaSemana:      String,
    @SerialName("hora_inicio")     val horaInicio:     String,
    @SerialName("hora_fim")        val horaFim:        String,
)

@Serializable
private data class CancelarAgendamentoRegularRequest(
    val status:                  String,
    @SerialName("cancelado_em")  val canceladoEm:         String,
    @SerialName("taxa_cancelamento") val taxaCancelamento: Double,
)

// ═══════════════════════════════════════════════════════════════════════════
// REPOSITÓRIO
// ═══════════════════════════════════════════════════════════════════════════

object AtendimentosRepository {

    // ── 1. MODALIDADES — buscar de um profissional (cliente vê) ──────────

    suspend fun buscarModalidades(profissionalId: String): List<ModalidadeAtendimento> {
        return try {
            val response = httpClient.get("$SUPABASE_URL/rest/v1/modalidades_atendimento") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Accept",        "application/json")
                parameter("profissional_id", "eq.$profissionalId")
                parameter("ativo",           "eq.true")
                parameter("select",          "id,profissional_id,tipo,titulo,descricao,duracao_minutos,sessoes_por_semana,sessoes_total,valor,ativo")
                parameter("order",           "valor.asc")
            }
            if (response.status.value !in 200..299) return emptyList()
            json.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar modalidades: ${e.message}")
            emptyList()
        }
    }

    // ── 2. SLOTS DISPONÍVEIS via RPC ──────────────────────────────────────

    /**
     * Chama a RPC `buscar_slots_disponiveis(p_profissional_id, p_data, p_modalidade_id?)`
     * e retorna os slots livres para a data informada.
     * @param data formato ISO: "2026-05-15"
     */
    suspend fun buscarSlots(
        profissionalId: String,
        data:           String,
        modalidadeId:   String? = null,
    ): List<SlotDisponivel> {
        return try {
            val response = httpClient.post("$SUPABASE_URL/rest/v1/rpc/buscar_slots_disponiveis") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                contentType(ContentType.Application.Json)
                setBody(BuscarSlotsRequest(
                    profissionalId = profissionalId,
                    data           = data,
                    modalidadeId   = modalidadeId,
                ))
            }
            if (response.status.value !in 200..299) {
                Log.w(TAG, "buscar_slots_disponiveis HTTP ${response.status.value}")
                return emptyList()
            }
            json.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar slots: ${e.message}")
            emptyList()
        }
    }

    // ── 3. CRIAR AGENDAMENTO REGULAR ──────────────────────────────────────

    /**
     * Cria um agendamento regular com idempotency-key para evitar duplicatas.
     * Retorna o ID do agendamento criado ou null em caso de falha.
     */
    suspend fun criarAgendamento(
        clienteId:      String,
        profissionalId: String,
        modalidadeId:   String,
        slotId:         String?,
        dataAgendada:   String,   // "2026-05-15"
        horaInicio:     String,   // "09:00"
        horaFim:        String,   // "10:00"
        valorCobrado:   Double,
    ): String? {
        return try {
            val idempotencyKey = "regular_${clienteId}_${profissionalId}_${dataAgendada}_${horaInicio}"
            val response = httpClient.post("$SUPABASE_URL/rest/v1/agendamentos_regulares") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=representation")
                setBody(CriarAgendamentoRegularRequest(
                    clienteId      = clienteId,
                    profissionalId = profissionalId,
                    modalidadeId   = modalidadeId,
                    slotId         = slotId,
                    dataAgendada   = dataAgendada,
                    horaInicio     = horaInicio,
                    horaFim        = horaFim,
                    valorCobrado   = valorCobrado,
                    idempotencyKey = idempotencyKey,
                ))
            }
            if (response.status.value !in 200..299) {
                Log.e(TAG, "Erro ao criar agendamento: HTTP ${response.status.value}")
                return null
            }
            val lista = json.decodeFromString<List<AgendamentoRegular>>(response.bodyAsText())
            lista.firstOrNull()?.id
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar agendamento: ${e.message}")
            null
        }
    }

    // ── 4. LISTAR AGENDAMENTOS DO CLIENTE ─────────────────────────────────

    suspend fun buscarAgendamentosCliente(clienteId: String): List<AgendamentoRegular> {
        return try {
            val response = httpClient.get("$SUPABASE_URL/rest/v1/agendamentos_regulares") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Accept",        "application/json")
                parameter("cliente_id", "eq.$clienteId")
                parameter("select",     "id,cliente_id,profissional_id,modalidade_id,data_agendada,hora_inicio,hora_fim,valor_cobrado,status,avaliado,criado_em")
                parameter("order",      "data_agendada.desc")
                parameter("limit",      "50")
            }
            if (response.status.value !in 200..299) return emptyList()
            json.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar agendamentos do cliente: ${e.message}")
            emptyList()
        }
    }

    // ── 5. LISTAR AGENDAMENTOS DO PROFISSIONAL ───────────────────────────

    suspend fun buscarAgendamentosProfissional(profissionalId: String): List<AgendamentoRegular> {
        return try {
            val response = httpClient.get("$SUPABASE_URL/rest/v1/agendamentos_regulares") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Accept",        "application/json")
                parameter("profissional_id", "eq.$profissionalId")
                parameter("select",          "id,cliente_id,profissional_id,modalidade_id,data_agendada,hora_inicio,hora_fim,valor_cobrado,status,avaliado,criado_em")
                parameter("order",           "data_agendada.desc")
                parameter("limit",           "50")
            }
            if (response.status.value !in 200..299) return emptyList()
            json.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar agendamentos do profissional: ${e.message}")
            emptyList()
        }
    }

    // ── 6. CANCELAR AGENDAMENTO ───────────────────────────────────────────

    /**
     * Regra de Ouro nº 10 vigente:
     *   - Até 10 minutos após criação: sem taxa
     *   - Após 10 minutos:             30% do valor
     * @param canceladoPorCliente true = cliente cancelou; false = profissional cancelou
     */
    suspend fun cancelarAgendamento(
        agendamentoId:       String,
        valorCobrado:        Double,
        criadoEm:            String?,
        canceladoPorCliente: Boolean,
    ): Boolean {
        return try {
            val agora       = System.currentTimeMillis()
            val criadoMs    = criadoEm?.let { parseCriadoEmMs(it) } ?: 0L
            val minutosDiff = (agora - criadoMs) / 60_000
            val taxa        = if (minutosDiff <= 10) 0.0 else valorCobrado * 0.30
            val novoStatus  = if (canceladoPorCliente) "cancelado_cliente" else "cancelado_profissional"

            // Formatar timestamp ISO 8601 sem java.time (API 24 compatível)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val canceladoEm = sdf.format(Date(agora))

            val response = httpClient.patch(
                "$SUPABASE_URL/rest/v1/agendamentos_regulares?id=eq.$agendamentoId"
            ) {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody(CancelarAgendamentoRegularRequest(
                    status           = novoStatus,
                    canceladoEm      = canceladoEm,
                    taxaCancelamento = taxa,
                ))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao cancelar agendamento: ${e.message}")
            false
        }
    }

    // ── 7. CRUD MODALIDADES (profissional) ───────────────────────────────

    suspend fun criarModalidade(
        profissionalId:  String,
        tipo:            String,
        titulo:          String,
        descricao:       String?,
        duracaoMinutos:  Int?,
        sessoesPorSemana: Int?   = null,
        sessoesTotal:    Int?    = null,
        valor:           Double,
    ): Boolean {
        return try {
            val response = httpClient.post("$SUPABASE_URL/rest/v1/modalidades_atendimento") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody(CriarModalidadeRequest(
                    profissionalId   = profissionalId,
                    tipo             = tipo,
                    titulo           = titulo,
                    descricao        = descricao,
                    duracaoMinutos   = duracaoMinutos,
                    sessoesPorSemana = sessoesPorSemana,
                    sessoesTotal     = sessoesTotal,
                    valor            = valor,
                ))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar modalidade: ${e.message}")
            false
        }
    }

    suspend fun atualizarModalidade(
        id:              String,
        titulo:          String,
        descricao:       String?,
        duracaoMinutos:  Int?,
        sessoesPorSemana: Int?  = null,
        sessoesTotal:    Int?   = null,
        valor:           Double,
        ativo:           Boolean,
    ): Boolean {
        return try {
            val response = httpClient.patch(
                "$SUPABASE_URL/rest/v1/modalidades_atendimento?id=eq.$id"
            ) {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody(AtualizarModalidadeRequest(
                    titulo           = titulo,
                    descricao        = descricao,
                    duracaoMinutos   = duracaoMinutos,
                    sessoesPorSemana = sessoesPorSemana,
                    sessoesTotal     = sessoesTotal,
                    valor            = valor,
                    ativo            = ativo,
                ))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar modalidade: ${e.message}")
            false
        }
    }

    suspend fun buscarMinhasModalidades(profissionalId: String): List<ModalidadeAtendimento> {
        return try {
            val response = httpClient.get("$SUPABASE_URL/rest/v1/modalidades_atendimento") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Accept",        "application/json")
                parameter("profissional_id", "eq.$profissionalId")
                parameter("select",          "id,profissional_id,tipo,titulo,descricao,duracao_minutos,sessoes_por_semana,sessoes_total,valor,ativo")
                parameter("order",           "tipo.asc,valor.asc")
            }
            if (response.status.value !in 200..299) return emptyList()
            json.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar modalidades do profissional: ${e.message}")
            emptyList()
        }
    }

    // ── 8. CRUD DISPONIBILIDADE (profissional) ───────────────────────────

    suspend fun criarDisponibilidade(
        profissionalId: String,
        modalidadeId:   String?,
        diaSemana:      String,
        horaInicio:     String,
        horaFim:        String,
    ): Boolean {
        return try {
            val response = httpClient.post("$SUPABASE_URL/rest/v1/disponibilidade_regular") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody(CriarDisponibilidadeRequest(
                    profissionalId = profissionalId,
                    modalidadeId   = modalidadeId,
                    diaSemana      = diaSemana,
                    horaInicio     = horaInicio,
                    horaFim        = horaFim,
                ))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar disponibilidade: ${e.message}")
            false
        }
    }

    suspend fun buscarMinhaDisponibilidade(profissionalId: String): List<DisponibilidadeRegular> {
        return try {
            val response = httpClient.get("$SUPABASE_URL/rest/v1/disponibilidade_regular") {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Accept",        "application/json")
                parameter("profissional_id", "eq.$profissionalId")
                parameter("cancelado",       "eq.false")
                parameter("select",          "id,profissional_id,modalidade_id,dia_semana,hora_inicio,hora_fim,cancelado")
                parameter("order",           "dia_semana.asc,hora_inicio.asc")
            }
            if (response.status.value !in 200..299) return emptyList()
            json.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar disponibilidade: ${e.message}")
            emptyList()
        }
    }

    suspend fun removerDisponibilidade(slotId: String): Boolean {
        return try {
            val response = httpClient.patch(
                "$SUPABASE_URL/rest/v1/disponibilidade_regular?id=eq.$slotId"
            ) {
                header("apikey",        SUPABASE_KEY)
                header("Authorization", "Bearer ${currentToken ?: SUPABASE_KEY}")
                header("Content-Type",  "application/json")
                header("Prefer",        "return=minimal")
                setBody("{\"cancelado\":true}")
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover disponibilidade: ${e.message}")
            false
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    // Parsear timestamp ISO 8601 sem java.time (API 24 compatível)
    private fun parseCriadoEmMs(criadoEm: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            // Remover sufixo de fuso horário antes de parsear
            val limpo = criadoEm
                .substringBefore("+")
                .substringBefore("Z")
                .take(19)
            sdf.parse(limpo)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    /** Converte "segunda" | "terca" ... para label legível */
    fun diaSemanaLabel(dia: String): String = when (dia) {
        "segunda"  -> "Segunda"
        "terca"    -> "Terça"
        "quarta"   -> "Quarta"
        "quinta"   -> "Quinta"
        "sexta"    -> "Sexta"
        "sabado"   -> "Sábado"
        "domingo"  -> "Domingo"
        else       -> dia
    }

    /** Label de tipo de modalidade */
    fun tipoLabel(tipo: String): String = when (tipo) {
        "minutos" -> "Por minutos"
        "hora"    -> "Por hora"
        "semanal" -> "Pacote semanal"
        "mensal"  -> "Pacote mensal"
        "urgente" -> "Urgente"
        else      -> tipo
    }
}