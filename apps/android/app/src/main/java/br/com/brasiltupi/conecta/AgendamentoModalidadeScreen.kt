package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
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
import java.util.*

@Composable
fun AgendamentoModalidadeScreen(
    profissionalId: String,
    modalidadeId:   String,
    onVoltar:       () -> Unit,
    onAgendado:     () -> Unit,
    onPagar:        (agendamentoId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var modalidade    by remember { mutableStateOf<ModalidadeAtendimento?>(null) }
    var slots         by remember { mutableStateOf<List<SlotDisponivel>>(emptyList()) }
    var slotSelecionado by remember { mutableStateOf<SlotDisponivel?>(null) }
    var carregando    by remember { mutableStateOf(true) }
    var carregandoSlots by remember { mutableStateOf(false) }
    var confirmando   by remember { mutableStateOf(false) }
    var erro          by remember { mutableStateOf("") }
    var sucesso       by remember { mutableStateOf(false) }
    var agendamentoId by remember { mutableStateOf<String?>(null) }

    val dataHoje = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val dataExibicao = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
    }

    // Carregar modalidade e slots ao iniciar
    LaunchedEffect(modalidadeId) {
        carregando = true
        val todas = AtendimentosRepository.buscarModalidades(profissionalId)
        modalidade = todas.firstOrNull { it.id == modalidadeId }
        carregando = false

        carregandoSlots = true
        slots = AtendimentosRepository.buscarSlots(
            profissionalId = profissionalId,
            data           = dataHoje,
            modalidadeId   = modalidadeId,
        )
        carregandoSlots = false
    }

    if (sucesso && agendamentoId != null) {
        Column(
            modifier            = Modifier.fillMaxSize().background(SurfaceWarm).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier         = Modifier.size(80.dp).background(VerdeClaro, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", fontSize = 36.sp, color = Verde, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Agendamento confirmado!",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = Ink,
                textAlign  = TextAlign.Center,
            )
            Text(
                "Seu atendimento foi registrado com sucesso.",
                fontSize  = 14.sp,
                color     = InkMuted,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(top = 8.dp, bottom = 28.dp),
            )
            Button(
                onClick  = onAgendado,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Text("Voltar ao início", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick  = { onPagar(agendamentoId!!) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Azul),
            ) {
                Text("Ir para pagamento", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(52.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVoltar) {
                Text("← Voltar", color = InkMuted, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Agendar Atendimento", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Escolha um horário disponível", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp))

        Spacer(modifier = Modifier.height(24.dp))

        if (carregando) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Verde)
            }
        } else if (modalidade == null) {
            Text("Modalidade não encontrada.", fontSize = 14.sp, color = Urgente, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            val mod = modalidade!!

            // Card da modalidade selecionada
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mod.titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                AtendimentosRepository.tipoLabel(mod.tipo),
                                fontSize = 13.sp,
                                color    = InkMuted,
                            )
                            mod.duracaoMinutos?.let {
                                Text("⏱ $it minutos por sessão", fontSize = 12.sp, color = InkMuted)
                            }
                            mod.descricao?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, fontSize = 13.sp, color = InkSoft, lineHeight = 18.sp)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "R$ ${"%.2f".format(mod.valor)}",
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Verde,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Slots disponíveis
            Text(
                "Horários disponíveis — $dataExibicao",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Ink,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (carregandoSlots) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Verde, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
                        Text("📅", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Sem horários hoje", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Urgente)
                            Text("Tente em outro dia ou entre em contato com o profissional.", fontSize = 12.sp, color = Urgente.copy(alpha = 0.8f))
                        }
                    }
                }
            } else {
                slots.forEach { slot ->
                    val selecionado = slotSelecionado?.slotId == slot.slotId
                    Card(
                        onClick   = { slotSelecionado = if (selecionado) null else slot },
                        modifier  = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        shape     = RoundedCornerShape(12.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = if (selecionado) Verde else Surface,
                        ),
                        border    = androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            color = if (selecionado) Verde else SurfaceOff,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (selecionado) 4.dp else 1.dp),
                    ) {
                        Row(
                            modifier          = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (selecionado) "✓" else "🕐",
                                    fontSize = 18.sp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "${slot.horaInicio} – ${slot.horaFim}",
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (selecionado) Color.White else Ink,
                                )
                            }
                            Text(
                                "R$ ${"%.2f".format(slot.valor)}",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color      = if (selecionado) Color.White else Verde,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Erro
            if (erro.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(erro, color = Urgente, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Botão confirmar
            Button(
                onClick = {
                    val slot = slotSelecionado
                    val uid  = currentUserId
                    if (slot == null) { erro = "Selecione um horário para continuar."; return@Button }
                    if (uid == null)  { erro = "Sessão expirada. Faça login novamente."; return@Button }
                    erro = ""
                    confirmando = true
                    scope.launch {
                        try {
                            val id = AtendimentosRepository.criarAgendamento(
                                clienteId      = uid,
                                profissionalId = profissionalId,
                                modalidadeId   = modalidadeId,
                                slotId         = slot.slotId,
                                dataAgendada   = dataHoje,
                                horaInicio     = slot.horaInicio,
                                horaFim        = slot.horaFim,
                                valorCobrado   = slot.valor,
                            )
                            confirmando = false
                            if (id != null) {
                                agendamentoId = id
                                sucesso = true
                            } else {
                                erro = "Erro ao confirmar agendamento. Tente novamente."
                            }
                        } catch (e: Exception) {
                            AppLogger.erroRede("AgendamentoModalidade", e, "mod=$modalidadeId")
                            confirmando = false
                            erro = "Erro inesperado. Verifique sua conexão."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled  = slotSelecionado != null && !confirmando,
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Verde,
                    contentColor           = Color.White,
                    disabledContainerColor = SurfaceOff,
                    disabledContentColor   = InkMuted,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (confirmando) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirmar agendamento", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}