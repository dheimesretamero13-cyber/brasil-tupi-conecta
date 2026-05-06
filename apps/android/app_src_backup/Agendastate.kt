package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AgendaState.kt  · Fase 2.3
//
// Modelos, DTOs e estados tipados para a Agenda do Profissional.
// Tudo que é compartilhado entre AgendaRepository, AgendaProfissionalScreen
// e AgendaClienteScreen vive aqui.
// ═══════════════════════════════════════════════════════════════════════════

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════════════
// MODELOS DE DOMÍNIO
// ═══════════════════════════════════════════════════════════════════════════

// Slot de disponibilidade — espelha a tabela availability
@Serializable
data class SlotDisponibilidade(
    val id:             String,
    @SerialName("professional_id") val professionalId: String,
    @SerialName("start_time")      val startTime:      String,  // ISO 8601 UTC
    @SerialName("end_time")        val endTime:        String,
    @SerialName("is_booked")       val isBooked:       Boolean = false,
    @SerialName("criado_em")       val criadoEm:       String? = null,
) {
    // Helpers de exibição — parse manual compatível com API 24+
    val dataFormatada: String get() = startTime.take(10).split("-").let { p ->
        if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else startTime
    }
    val horaInicio: String get() = startTime.drop(11).take(5)  // "HH:mm"
    val horaFim:    String get() = endTime.drop(11).take(5)
    val diaSemana:  String get() = calcularDiaSemana(startTime)
    val chaveData:  String get() = startTime.take(10)           // "yyyy-MM-dd"
}

// Agendamento criado após reserva de slot
@Serializable
data class Agendamento(
    val id:             String,
    @SerialName("slot_id")         val slotId:         String,
    @SerialName("client_id")       val clientId:       String,
    @SerialName("professional_id") val professionalId: String,
    val status:                    String,
    val observacao:                String? = null,
    @SerialName("criado_em")       val criadoEm:       String? = null,
    // JOIN com availability para exibir horário
    val availability:              SlotDisponibilidade? = null,
)

// ═══════════════════════════════════════════════════════════════════════════
// DTOs PARA RPCs
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
data class ValidarCriarSlotRequest(
    @SerialName("p_prof_id") val profId:  String,
    @SerialName("p_start")   val start:   String,  // ISO 8601 UTC
    @SerialName("p_end")     val end:     String,
)

@Serializable
data class ReservarSlotRequest(
    @SerialName("p_slot_id")    val slotId:      String,
    @SerialName("p_client_id")  val clientId:    String,
    @SerialName("p_observacao") val observacao:  String? = null,
)

@Serializable
data class CancelarAgendamentoRequest(
    @SerialName("p_agendamento_id") val agendamentoId: String,
    @SerialName("p_cancelador_id")  val canceladorId:  String,
)

// DTO para DELETE de slot pelo profissional
@Serializable
data class SlotParaInserir(
    @SerialName("professional_id") val professionalId: String,
    @SerialName("start_time")      val startTime:      String,
    @SerialName("end_time")        val endTime:        String,
)

// ═══════════════════════════════════════════════════════════════════════════
// ESTADOS DA AGENDA DO PROFISSIONAL
// ═══════════════════════════════════════════════════════════════════════════

sealed class AgendaProfState {
    object Idle       : AgendaProfState()
    object Carregando : AgendaProfState()

    data class Carregado(
        val slots:           List<SlotDisponibilidade>,
        val slotsPorDia:     Map<String, List<SlotDisponibilidade>>,
        val agendamentos:    List<Agendamento>,
    ) : AgendaProfState()

    // Resultado de criar novo slot
    sealed class ResultadoCriarSlot {
        object Sucesso        : ResultadoCriarSlot()
        object Sobreposicao   : ResultadoCriarSlot()  // overlap com slot existente
        object Criando        : ResultadoCriarSlot()  // loading
        data class Erro(val mensagem: String) : ResultadoCriarSlot()
    }

    data class Erro(val mensagem: String) : AgendaProfState()
}

// ═══════════════════════════════════════════════════════════════════════════
// ESTADOS DA AGENDA DO CLIENTE
// ═══════════════════════════════════════════════════════════════════════════

sealed class AgendaClienteState {
    object Idle       : AgendaClienteState()
    object Carregando : AgendaClienteState()

    data class Carregado(
        val slots:       List<SlotDisponibilidade>,
        val slotsPorDia: Map<String, List<SlotDisponibilidade>>,
    ) : AgendaClienteState()

    // Resultado de reservar slot
    sealed class ResultadoReserva {
        object Sucesso      : ResultadoReserva()
        object JaReservado  : ResultadoReserva()   // outro cliente foi mais rápido
        object Reservando   : ResultadoReserva()   // loading
        data class Erro(val mensagem: String) : ResultadoReserva()
    }

    data class Erro(val mensagem: String) : AgendaClienteState()
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPER — calcular dia da semana (sem java.time — compatível API 24+)
// ═══════════════════════════════════════════════════════════════════════════

fun calcularDiaSemana(isoDate: String): String {
    return try {
        // "yyyy-MM-dd" → Zeller's congruence simplificado
        val data   = isoDate.take(10).split("-")
        val ano    = data[0].toInt()
        val mes    = data[1].toInt()
        val dia    = data[2].toInt()
        // Ajuste para Zeller (jan/fev como mês 13/14 do ano anterior)
        val m = if (mes < 3) mes + 12 else mes
        val a = if (mes < 3) ano - 1 else ano
        val k = a % 100
        val j = a / 100
        val h = (dia + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 - 2 * j) % 7
        // h: 0=Sáb, 1=Dom, 2=Seg, 3=Ter, 4=Qua, 5=Qui, 6=Sex
        listOf("Sábado", "Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta")
            .getOrElse(h) { "?" }
    } catch (_: Exception) { "?" }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPER — formatar ISO UTC para exibição local
// Compatível API 24+ (sem java.time.ZonedDateTime)
// ═══════════════════════════════════════════════════════════════════════════

fun formatarHorarioSlot(slot: SlotDisponibilidade): String =
    "${slot.diaSemana}, ${slot.dataFormatada} · ${slot.horaInicio}–${slot.horaFim}"