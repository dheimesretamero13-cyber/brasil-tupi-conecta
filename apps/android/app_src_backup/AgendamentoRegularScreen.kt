package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AgendamentoRegularScreen.kt
//
// Tela do CLIENTE para agendar um atendimento regular com um profissional.
// Fluxo em 3 etapas:
//   1. Escolher modalidade
//   2. Escolher data e slot disponível
//   3. Confirmar e criar agendamento
//
// Navegação: dashboard-cliente → "agendamento-regular" → volta para dashboard
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AgendamentoRegularScreen(
    profissionalId:   String,
    nomeProfissional: String = "",
    onVoltar:         () -> Unit,
    onAgendado:       (agendamentoId: String) -> Unit,
    onPagar:          (agendamentoId: String) -> Unit = {},
) {
    val scope        = rememberCoroutineScope()
    val clienteId    = currentUserId ?: ""

    // Estado do fluxo
    var etapa        by remember { mutableStateOf(1) }  // 1 = modalidade, 2 = slot, 3 = confirmar

    // Dados carregados
    var modalidades  by remember { mutableStateOf<List<ModalidadeAtendimento>>(emptyList()) }
    var slots        by remember { mutableStateOf<List<SlotDisponivel>>(emptyList()) }
    var carregando   by remember { mutableStateOf(true) }
    var carregandoSlots by remember { mutableStateOf(false) }

    // Seleções do usuário
    var modalidadeSelecionada by remember { mutableStateOf<ModalidadeAtendimento?>(null) }
    var dataSelecionada       by remember { mutableStateOf<Calendar?>(null) }
    var slotSelecionado       by remember { mutableStateOf<SlotDisponivel?>(null) }

    // Confirmação
    var agendando    by remember { mutableStateOf(false) }
    var erroMsg      by remember { mutableStateOf<String?>(null) }

    // Carregar modalidades na abertura
    LaunchedEffect(profissionalId) {
        carregando  = true
        modalidades = AtendimentosRepository.buscarModalidades(profissionalId)
        carregando  = false
    }

    // Carregar slots quando data + modalidade selecionadas
    LaunchedEffect(dataSelecionada, modalidadeSelecionada) {
        val cal = dataSelecionada ?: return@LaunchedEffect
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        carregandoSlots = true
        slots = AtendimentosRepository.buscarSlots(
            profissionalId = profissionalId,
            data           = sdf.format(cal.time),
            modalidadeId   = modalidadeSelecionada?.id,
        )
        slotSelecionado = null
        carregandoSlots = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Verde)
                .padding(top = 44.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            TextButton(
                onClick  = {
                    if (etapa > 1) etapa-- else onVoltar()
                },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text("← ${if (etapa > 1) "Voltar" else "Cancelar"}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Agendar atendimento",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                )
                if (nomeProfissional.isNotBlank()) {
                    Text(
                        "com $nomeProfissional",
                        fontSize = 12.sp,
                        color    = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // ── Indicador de etapas ───────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            listOf(1 to "Modalidade", 2 to "Data e horário", 3 to "Confirmar").forEach { (n, label) ->
                val ativo    = etapa == n
                val concluido = etapa > n
                Row(
                    verticalAlignment  = Alignment.CenterVertically,
                    modifier           = Modifier.weight(1f),
                ) {
                    Box(
                        modifier         = Modifier
                            .size(24.dp)
                            .background(
                                when {
                                    concluido -> Verde
                                    ativo     -> Azul
                                    else      -> SurfaceOff
                                },
                                RoundedCornerShape(50),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (concluido) "✓" else "$n",
                            fontSize   = 11.sp,
                            color      = if (ativo || concluido) Color.White else InkMuted,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        label,
                        fontSize   = 10.sp,
                        color      = if (ativo) Azul else InkMuted,
                        fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal,
                        modifier   = Modifier.weight(1f),
                    )
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        if (carregando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Verde)
            }
            return@Column
        }

        // ── Conteúdo por etapa ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (etapa) {

                // ─── ETAPA 1: ESCOLHER MODALIDADE ─────────────────────────
                1 -> {
                    Text(
                        "Escolha o tipo de atendimento",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Ink,
                    )
                    if (modalidades.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = CardDefaults.cardColors(containerColor = Surface),
                        ) {
                            Column(
                                modifier            = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("😕", fontSize = 32.sp)
                                Text(
                                    "Nenhuma modalidade disponível",
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = Ink,
                                )
                                Text(
                                    "Este profissional ainda não configurou seus tipos de atendimento regular.",
                                    fontSize   = 13.sp,
                                    color      = InkMuted,
                                    textAlign  = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        modalidades.forEach { mod ->
                            val selecionada = modalidadeSelecionada?.id == mod.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        modalidadeSelecionada = mod
                                        etapa = 2
                                    }
                                    .then(
                                        if (selecionada) Modifier.border(2.dp, Verde, RoundedCornerShape(12.dp))
                                        else Modifier
                                    ),
                                shape  = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selecionada) Verde.copy(alpha = 0.06f) else Surface
                                ),
                            ) {
                                Row(
                                    modifier          = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Ícone do tipo
                                    Box(
                                        modifier         = Modifier
                                            .size(44.dp)
                                            .background(
                                                when (mod.tipo) {
                                                    "hora"    -> Color(0xFFE3F2FD)
                                                    "semanal" -> Color(0xFFE8F5E9)
                                                    "mensal"  -> Color(0xFFF3E5F5)
                                                    else      -> Color(0xFFFFF8E1)
                                                },
                                                RoundedCornerShape(10.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            when (mod.tipo) {
                                                "hora"    -> "🕐"
                                                "semanal" -> "📆"
                                                "mensal"  -> "🗓"
                                                else      -> "⏱"
                                            },
                                            fontSize = 20.sp,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mod.titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                                        if (!mod.descricao.isNullOrBlank()) {
                                            Text(mod.descricao, fontSize = 12.sp, color = InkMuted, lineHeight = 17.sp)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            if (mod.duracaoMinutos != null) {
                                                Text("⏱ ${mod.duracaoMinutos}min", fontSize = 12.sp, color = InkMuted)
                                            }
                                            Text(
                                                "R$ ${"%.2f".format(mod.valor)}",
                                                fontSize   = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color      = Verde,
                                            )
                                        }
                                    }
                                    Text("→", fontSize = 18.sp, color = InkMuted)
                                }
                            }
                        }
                    }
                }

                // ─── ETAPA 2: ESCOLHER DATA E SLOT ────────────────────────
                2 -> {
                    // Resumo da modalidade escolhida
                    modalidadeSelecionada?.let { mod ->
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .background(Verde.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✓", fontSize = 14.sp, color = Verde, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(mod.titulo, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                                Text("R$ ${"%.2f".format(mod.valor)}", fontSize = 12.sp, color = Verde.copy(alpha = 0.8f))
                            }
                        }
                    }

                    Text(
                        "Escolha a data",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Ink,
                    )

                    // Seletor de data — próximos 14 dias
                    val hoje     = Calendar.getInstance()
                    val sdfLabel = SimpleDateFormat("dd/MM", Locale.US)
                    val sdfIso   = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val diasSemLabel = mapOf(
                        Calendar.MONDAY    to "Seg",
                        Calendar.TUESDAY   to "Ter",
                        Calendar.WEDNESDAY to "Qua",
                        Calendar.THURSDAY  to "Qui",
                        Calendar.FRIDAY    to "Sex",
                        Calendar.SATURDAY  to "Sáb",
                        Calendar.SUNDAY    to "Dom",
                    )
                    val proxDias = (0..13).map { offset ->
                        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
                    }

                    // Grade de dias — 7 por linha
                    proxDias.chunked(7).forEach { semana ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            semana.forEach { dia ->
                                val selecionado = dataSelecionada?.let {
                                    sdfIso.format(it.time) == sdfIso.format(dia.time)
                                } ?: false
                                Column(
                                    modifier            = Modifier
                                        .weight(1f)
                                        .background(
                                            if (selecionado) Azul else Surface,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .border(
                                            1.dp,
                                            if (selecionado) Azul else SurfaceOff,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable { dataSelecionada = dia }
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        diasSemLabel[dia.get(Calendar.DAY_OF_WEEK)] ?: "",
                                        fontSize = 10.sp,
                                        color    = if (selecionado) Color.White else InkMuted,
                                    )
                                    Text(
                                        sdfLabel.format(dia.time),
                                        fontSize   = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (selecionado) Color.White else Ink,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Slots do dia selecionado
                    if (dataSelecionada != null) {
                        Text(
                            "Horários disponíveis",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Ink,
                            modifier   = Modifier.padding(top = 8.dp),
                        )

                        if (carregandoSlots) {
                            Box(
                                modifier         = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Verde, modifier = Modifier.size(28.dp))
                            }
                        } else if (slots.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = CardDefaults.cardColors(containerColor = UrgenteClaro),
                            ) {
                                Row(
                                    modifier          = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("📭", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Nenhum horário disponível nesta data. Tente outro dia.",
                                        fontSize = 13.sp,
                                        color    = Urgente,
                                    )
                                }
                            }
                        } else {
                            // Grade de slots — 3 por linha
                            slots.chunked(3).forEach { grupo ->
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    grupo.forEach { slot ->
                                        val selecionado = slotSelecionado?.slotId == slot.slotId
                                        Box(
                                            modifier         = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (selecionado) Verde else Surface,
                                                    RoundedCornerShape(8.dp),
                                                )
                                                .border(
                                                    1.dp,
                                                    if (selecionado) Verde else SurfaceOff,
                                                    RoundedCornerShape(8.dp),
                                                )
                                                .clickable { slotSelecionado = slot }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                slot.horaInicio,
                                                fontSize   = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color      = if (selecionado) Color.White else Ink,
                                            )
                                        }
                                    }
                                    // Preencher espaços vazios na última linha
                                    repeat(3 - grupo.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        // Botão avançar
                        if (slotSelecionado != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick  = { etapa = 3 },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Verde),
                            ) {
                                Text(
                                    "Confirmar horário ${slotSelecionado!!.horaInicio}",
                                    color      = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 15.sp,
                                )
                            }
                        }
                    }
                }

                // ─── ETAPA 3: CONFIRMAR ────────────────────────────────────
                3 -> {
                    val mod  = modalidadeSelecionada!!
                    val slot = slotSelecionado!!
                    val cal  = dataSelecionada!!
                    val sdfExib = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                    val sdfIso  = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                    Text(
                        "Confirme seu agendamento",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Ink,
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = CardDefaults.cardColors(containerColor = Surface),
                    ) {
                        Column(
                            modifier            = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (nomeProfissional.isNotBlank()) {
                                FilhoResumo("Profissional", nomeProfissional)
                            }
                            FilhoResumo("Tipo",     mod.titulo)
                            FilhoResumo("Data",     sdfExib.format(cal.time))
                            FilhoResumo("Horário",  "${slot.horaInicio} – ${slot.horaFim}")
                            if (mod.duracaoMinutos != null) {
                                FilhoResumo("Duração",  "${mod.duracaoMinutos} minutos")
                            }
                            HorizontalDivider(color = SurfaceOff)
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text("Total", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text(
                                    "R$ ${"%.2f".format(mod.valor)}",
                                    fontSize   = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Verde,
                                )
                            }
                        }
                    }

                    // Aviso de política de cancelamento (Regra de Ouro nº 10)
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF8E1), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("⚠️", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Cancelamento até 10 minutos após confirmação: sem taxa. " +
                                    "Após 10 minutos: taxa de 30% do valor.",
                            fontSize   = 12.sp,
                            color      = Color(0xFFF57F17),
                            lineHeight = 18.sp,
                        )
                    }

                    if (erroMsg != null) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .background(UrgenteClaro, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("❌", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(erroMsg!!, fontSize = 12.sp, color = Urgente)
                        }
                    }

                    Button(
                        onClick = {
                            erroMsg  = null
                            agendando = true
                            scope.launch {
                                val id = AtendimentosRepository.criarAgendamento(
                                    clienteId      = clienteId,
                                    profissionalId = profissionalId,
                                    modalidadeId   = mod.id,
                                    slotId         = slot.slotId,
                                    dataAgendada   = sdfIso.format(cal.time),
                                    horaInicio     = slot.horaInicio,
                                    horaFim        = slot.horaFim,
                                    valorCobrado   = mod.valor,
                                )
                                agendando = false
                                if (id != null) {
                                    onPagar(id)   // navega para pagamento imediatamente
                                } else {
                                    erroMsg = "Não foi possível criar o agendamento. " +
                                            "O horário pode ter sido ocupado. Tente outro."
                                }
                            }
                        },
                        enabled  = !agendando,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Verde),
                    ) {
                        if (agendando) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            if (agendando) "Agendando..." else "Confirmar agendamento",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FilhoResumo(label: String, valor: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = InkMuted)
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}