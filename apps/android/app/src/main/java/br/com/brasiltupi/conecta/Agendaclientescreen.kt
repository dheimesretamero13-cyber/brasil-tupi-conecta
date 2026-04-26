package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AgendaClienteScreen.kt  · Fase 2.3
//
// "Burra por princípio" — renderiza apenas o estado do AgendaRepository.
// Nenhuma lógica de disponibilidade ou validação aqui.
//
// Layout:
//  1. Header com nome do profissional
//  2. Seletor de semana — mostra apenas dias com slots disponíveis
//  3. LazyVerticalGrid de slots do dia (apenas is_booked = false via RLS)
//  4. Dialog de confirmação antes de chamar reservarSlot()
//  5. Feedback visual via SlotCard(isReservando=true)
//  6. Dialog de sucesso / já reservado
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun AgendaClienteScreen(
    professionalId:   String,
    nomeProfissional: String = "Profissional",
    onVoltar:         () -> Unit,
    onAgendado:       () -> Unit,   // navegar após reserva confirmada
) {
    val clienteState    by AgendaRepository.clienteState.collectAsState()
    val resultadoReserva by AgendaRepository.resultadoReserva.collectAsState()

    // ── Estado local da UI ────────────────────────────────────────────────
    var diaSelecionado       by remember { mutableStateOf(hojeChaveCliente()) }
    var slotParaConfirmar    by remember { mutableStateOf<SlotDisponibilidade?>(null) }
    var slotReservando       by remember { mutableStateOf<String?>(null) }  // id do slot em loading
    var mostrarSucesso       by remember { mutableStateOf(false) }
    var mostrarJaReservado   by remember { mutableStateOf(false) }
    var mostrarErroReserva   by remember { mutableStateOf("") }

    // ── Carregar slots ao entrar ──────────────────────────────────────────
    LaunchedEffect(professionalId) {
        val inicio = System.currentTimeMillis()
        AgendaRepository.carregarSlotsDisponiveis(professionalId)
        val fim = System.currentTimeMillis()
        AppLogger.info("AgendaClienteScreen", "Slots carregados: ${fim - inicio}ms")
    }

    // ── Reagir ao resultado da reserva ────────────────────────────────────
    LaunchedEffect(resultadoReserva) {
        // Captura local necessária — delegated property não permite smart cast
        val resultado = resultadoReserva
        when (resultado) {
            is AgendaClienteState.ResultadoReserva.Sucesso -> {
                slotReservando = null
                mostrarSucesso = true
                AgendaRepository.resetarResultados()
                // Realtime recarregará a lista automaticamente
            }
            is AgendaClienteState.ResultadoReserva.JaReservado -> {
                slotReservando     = null
                mostrarJaReservado = true
                AgendaRepository.resetarResultados()
            }
            is AgendaClienteState.ResultadoReserva.Erro -> {
                slotReservando     = null
                mostrarErroReserva = resultado.mensagem   // smart cast agora funciona
                AppLogger.erro("AgendaClienteScreen",
                    "Falha na reserva: ${resultado.mensagem}")
                AgendaRepository.resetarResultados()
            }
            is AgendaClienteState.ResultadoReserva.Reservando -> {
                // já tratado via slotReservando
            }
            null -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm),
    ) {
        // ── TopBar ────────────────────────────────────────────────────────
        TopBarAgenda(
            titulo   = "Agendar com $nomeProfissional",
            onVoltar = {
                AgendaRepository.parar()
                onVoltar()
            },
        )

        HorizontalDivider(color = SurfaceOff)

        when (val estado = clienteState) {

            // ── Loading ───────────────────────────────────────────────────
            is AgendaClienteState.Carregando,
            AgendaClienteState.Idle -> {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Verde)
                }
            }

            // ── Erro ──────────────────────────────────────────────────────
            is AgendaClienteState.Erro -> {
                ErroAgenda(
                    mensagem = estado.mensagem,
                    onRetry  = {
                        AgendaRepository.carregarSlotsDisponiveis(professionalId)
                    },
                )
            }

            // ── Slots carregados ──────────────────────────────────────────
            is AgendaClienteState.Carregado -> {
                val diasComSlots = estado.slotsPorDia.keys.sorted()

                if (diasComSlots.isEmpty()) {
                    // Sem horários disponíveis
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("📅", fontSize = 40.sp)
                            Text(
                                "Nenhum horário disponível\nneste momento.",
                                fontSize  = 15.sp,
                                color     = InkMuted,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "Tente novamente mais tarde ou\ncontate o profissional.",
                                fontSize  = 13.sp,
                                color     = InkMuted.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    // Garantir que diaSelecionado é válido
                    val diaValido = if (diaSelecionado in estado.slotsPorDia)
                        diaSelecionado
                    else
                        diasComSlots.first()

                    // ── Seletor de semana (apenas dias com slots) ─────────
                    SeletorDiasDisponiveis(
                        dias           = diasComSlots,
                        diaSelecionado = diaValido,
                        onDiaClick     = { diaSelecionado = it },
                    )

                    HorizontalDivider(color = SurfaceOff)

                    // ── Grid de slots ─────────────────────────────────────
                    val slotsHoje = estado.slotsPorDia[diaValido] ?: emptyList()

                    Column(
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical   = 12.dp,
                        ),
                    ) {
                        Text(
                            "${slotsHoje.size} horário(s) disponível(is) em ${formatarDataCurtaCliente(diaValido)}",
                            fontSize = 12.sp,
                            color    = InkMuted,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )

                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(2),
                            verticalArrangement   = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier              = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = slotsHoje,
                                key   = { it.id },
                            ) { slot ->
                                SlotCard(
                                    slot         = slot,
                                    modo         = SlotCardModo.CLIENTE,
                                    isReservando = slotReservando == slot.id,
                                    onClick      = {
                                        if (slotReservando == null) {
                                            slotParaConfirmar = slot
                                        }
                                    },
                                    modifier     = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIÁLOGOS
    // ═══════════════════════════════════════════════════════════════════════

    // Confirmação antes de reservar
    slotParaConfirmar?.let { slot ->
        AlertDialog(
            onDismissRequest = { slotParaConfirmar = null },
            title = { Text("Confirmar agendamento", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Horário selecionado:",
                        fontSize = 12.sp,
                        color    = InkMuted,
                    )
                    Surface(
                        color = AzulClaro,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            formatarHorarioSlot(slot),
                            modifier   = Modifier.padding(12.dp),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = Azul,
                        )
                    }
                    Text(
                        "Deseja confirmar este agendamento?",
                        fontSize = 13.sp,
                        color    = Ink,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        slotReservando    = slot.id
                        slotParaConfirmar = null
                        AppLogger.info("AgendaClienteScreen",
                            "Iniciando reserva slot=${slot.id}")
                        AgendaRepository.reservarSlot(slotId = slot.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Confirmar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { slotParaConfirmar = null }) {
                    Text("Cancelar", color = InkMuted)
                }
            },
        )
    }

    // Sucesso
    if (mostrarSucesso) {
        AlertDialog(
            onDismissRequest = { mostrarSucesso = false; onAgendado() },
            title = { Text("✅ Agendado!", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "Seu horário foi confirmado.\nVocê receberá uma notificação com os detalhes.",
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { mostrarSucesso = false; onAgendado() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Ótimo!", color = Color.White)
                }
            },
        )
    }

    // Já reservado por outro cliente
    if (mostrarJaReservado) {
        AlertDialog(
            onDismissRequest = { mostrarJaReservado = false },
            title = { Text("Horário indisponível", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "Este horário acabou de ser reservado por outro cliente.\nEscolha outro horário disponível.",
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { mostrarJaReservado = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Escolher outro", color = Color.White)
                }
            },
        )
    }

    // Erro na reserva
    if (mostrarErroReserva.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { mostrarErroReserva = "" },
            title = { Text("Erro no agendamento", fontWeight = FontWeight.Bold) },
            text  = { Text(mostrarErroReserva, textAlign = TextAlign.Center) },
            confirmButton = {
                Button(
                    onClick = { mostrarErroReserva = "" },
                    colors  = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Entendi", color = Color.White)
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SELETOR DE DIAS DISPONÍVEIS (cliente — apenas dias com slots)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SeletorDiasDisponiveis(
    dias:           List<String>,
    diaSelecionado: String,
    onDiaClick:     (String) -> Unit,
) {
    val diasVisiveis = dias.take(7)   // mostrar máx 7 dias com slots

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        diasVisiveis.forEach { chave ->
            val selecionado = chave == diaSelecionado
            val partes      = chave.split("-")
            val diaN        = partes.getOrElse(2) { "?" }
            val label       = abreviarDia(chave)

            Column(
                modifier            = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selecionado) Azul else AzulClaro.copy(alpha = 0.4f))
                    .clickable { onDiaClick(chave) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text     = label,
                    fontSize = 10.sp,
                    color    = if (selecionado) Color.White else Azul,
                )
                Text(
                    text       = diaN,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (selecionado) Color.White else Azul,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════

private fun hojeChaveCliente(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date())
}

private fun formatarDataCurtaCliente(chave: String): String {
    val p = chave.split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}" else chave
}

private fun abreviarDia(isoDate: String): String {
    val abrevs = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    return try {
        val p   = isoDate.take(10).split("-")
        val ano = p[0].toInt(); val mes = p[1].toInt(); val dia = p[2].toInt()
        val m   = if (mes < 3) mes + 12 else mes
        val a   = if (mes < 3) ano - 1 else ano
        val k   = a % 100; val j = a / 100
        val h   = (dia + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 - 2 * j) % 7
        abrevs.getOrElse(h) { "?" }
    } catch (_: Exception) { "?" }
}