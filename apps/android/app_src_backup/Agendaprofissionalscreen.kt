package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AgendaProfissionalScreen.kt  · Fase 2.3
//
// "Burra por princípio" — renderiza o estado do AgendaRepository.
// Nenhum cálculo financeiro ou de disponibilidade aqui.
//
// Layout:
//  1. Seletor de semana (7 dias deslizando)
//  2. LazyVerticalGrid de slots do dia selecionado
//  3. FAB para adicionar novo slot
//  4. BottomSheet / Dialog para criar slot (hora início + hora fim)
//  5. Dialog de opções ao clicar em slot existente
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AgendaProfissionalScreen(
    onVoltar: () -> Unit,
) {
    val profState         by AgendaRepository.profState.collectAsState()
    val resultadoCriarSlot by AgendaRepository.resultadoCriarSlot.collectAsState()

    // ── Estado local da UI ────────────────────────────────────────────────
    var diaSelecionado      by remember { mutableStateOf(hojeChave()) }
    var mostrarDialogCriar  by remember { mutableStateOf(false) }
    var slotSelecionado     by remember { mutableStateOf<SlotDisponibilidade?>(null) }
    var mostrarDialogSlot   by remember { mutableStateOf(false) }

    // ── Carregar ao entrar ────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val inicio = System.currentTimeMillis()
        AgendaRepository.carregarAgendaProfissional()
        val fim = System.currentTimeMillis()
        AppLogger.info("AgendaProfScreen", "Carregamento: ${fim - inicio}ms")
    }

    // ── Reagir ao resultado de criar slot ─────────────────────────────────
    LaunchedEffect(resultadoCriarSlot) {
        when (resultadoCriarSlot) {
            is AgendaProfState.ResultadoCriarSlot.Sucesso -> {
                mostrarDialogCriar = false
                AgendaRepository.resetarResultados()
            }
            is AgendaProfState.ResultadoCriarSlot.Sobreposicao,
            is AgendaProfState.ResultadoCriarSlot.Erro -> {
                // mantém o dialog aberto — a UI mostra o erro internamente
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopBarAgenda(titulo = "Minha Agenda", onVoltar = onVoltar)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick           = { mostrarDialogCriar = true },
                containerColor    = Verde,
                contentColor      = Color.White,
                shape             = CircleShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar horário")
            }
        },
        containerColor = SurfaceWarm,
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {

            // ── Seletor de semana ─────────────────────────────────────────
            SeletorSemana(
                diaSelecionado = diaSelecionado,
                onDiaClick     = { diaSelecionado = it },
            )

            HorizontalDivider(color = SurfaceOff)

            // ── Conteúdo principal ────────────────────────────────────────
            when (val estado = profState) {

                is AgendaProfState.Carregando, AgendaProfState.Idle -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Verde)
                    }
                }

                is AgendaProfState.Erro -> {
                    ErroAgenda(
                        mensagem  = estado.mensagem,
                        onRetry   = { AgendaRepository.carregarAgendaProfissional() },
                    )
                }

                is AgendaProfState.Carregado -> {
                    val slotsHoje = estado.slotsPorDia[diaSelecionado] ?: emptyList()

                    if (slotsHoje.isEmpty()) {
                        // Estado vazio para o dia selecionado
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
                                    "Nenhum horário para este dia",
                                    fontSize  = 15.sp,
                                    color     = InkMuted,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    "Toque em + para adicionar",
                                    fontSize = 13.sp,
                                    color    = InkMuted.copy(alpha = 0.7f),
                                )
                            }
                        }
                    } else {
                        // Grid de slots
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "${slotsHoje.size} horário(s) em ${formatarDataCurta(diaSelecionado)}",
                                fontSize = 12.sp,
                                color    = InkMuted,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            LazyVerticalGrid(
                                columns             = GridCells.Fixed(2),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier            = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    items = slotsHoje,
                                    key   = { it.id },
                                ) { slot ->
                                    SlotCard(
                                        slot     = slot,
                                        modo     = SlotCardModo.PROFISSIONAL,
                                        onClick  = {
                                            slotSelecionado   = slot
                                            mostrarDialogSlot = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialog: criar novo slot ───────────────────────────────────────────
    if (mostrarDialogCriar) {
        DialogCriarSlot(
            diaPadrao    = diaSelecionado,
            resultado    = resultadoCriarSlot,
            onConfirmar  = { startIso, endIso ->
                AgendaRepository.criarSlot(startIso, endIso)
            },
            onDismiss    = {
                mostrarDialogCriar = false
                AgendaRepository.resetarResultados()
            },
        )
    }

    // ── Dialog: opções do slot existente ─────────────────────────────────
    slotSelecionado?.let { slot ->
        if (mostrarDialogSlot) {
            DialogOpcoeSlot(
                slot      = slot,
                onDeletar = {
                    mostrarDialogSlot = false
                    AgendaRepository.deletarSlot(slot.id)
                },
                onDismiss = { mostrarDialogSlot = false },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SELETOR DE SEMANA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SeletorSemana(
    diaSelecionado: String,
    onDiaClick:     (String) -> Unit,
) {
    val dias = remember { gerarProximos14Dias() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dias.forEach { (chave, label, diaN) ->
            val selecionado = chave == diaSelecionado
            Column(
                modifier            = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selecionado) Verde else Color.Transparent)
                    .clickable { onDiaClick(chave) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text     = label,   // "Seg", "Ter"...
                    fontSize = 10.sp,
                    color    = if (selecionado) Color.White else InkMuted,
                )
                Text(
                    text       = diaN,  // "25", "26"...
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (selecionado) Color.White else Ink,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DIALOG: CRIAR SLOT
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DialogCriarSlot(
    diaPadrao:   String,
    resultado:   AgendaProfState.ResultadoCriarSlot?,
    onConfirmar: (startIso: String, endIso: String) -> Unit,
    onDismiss:   () -> Unit,
) {
    var horaInicio by remember { mutableStateOf("08:00") }
    var horaFim    by remember { mutableStateOf("09:00") }
    var erroLocal  by remember { mutableStateOf("") }

    val isCriando = resultado is AgendaProfState.ResultadoCriarSlot.Criando

    // Atualizar erro conforme resultado
    LaunchedEffect(resultado) {
        erroLocal = when (resultado) {
            is AgendaProfState.ResultadoCriarSlot.Sobreposicao ->
                "Este horário já existe ou se sobrepõe a outro slot."
            is AgendaProfState.ResultadoCriarSlot.Erro ->
                resultado.mensagem
            else -> ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCriando) onDismiss() },
        title = { Text("Adicionar horário", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Data: ${formatarDataCurta(diaPadrao)}",
                    fontSize = 13.sp,
                    color    = InkMuted,
                )

                // Campos de hora
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    CampoHora(
                        label    = "Início",
                        valor    = horaInicio,
                        onChange = { horaInicio = it; erroLocal = "" },
                        modifier = Modifier.weight(1f),
                    )
                    Text("→", color = InkMuted, fontSize = 16.sp)
                    CampoHora(
                        label    = "Fim",
                        valor    = horaFim,
                        onChange = { horaFim = it; erroLocal = "" },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (erroLocal.isNotEmpty()) {
                    Text(erroLocal, color = Urgente, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validação básica de formato — lógica de sobreposição é do backend
                    if (!horaInicio.matches(Regex("\\d{2}:\\d{2}")) ||
                        !horaFim.matches(Regex("\\d{2}:\\d{2}"))) {
                        erroLocal = "Use o formato HH:mm (ex: 08:00)"
                        return@Button
                    }
                    if (horaInicio >= horaFim) {
                        erroLocal = "O início deve ser antes do fim."
                        return@Button
                    }
                    val startIso = "${diaPadrao}T${horaInicio}:00+00:00"
                    val endIso   = "${diaPadrao}T${horaFim}:00+00:00"
                    onConfirmar(startIso, endIso)
                },
                enabled = !isCriando,
                colors  = ButtonDefaults.buttonColors(containerColor = Verde),
            ) {
                if (isCriando) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Adicionar", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCriando) {
                Text("Cancelar", color = InkMuted)
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// DIALOG: OPÇÕES DO SLOT (profissional)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DialogOpcoeSlot(
    slot:      SlotDisponibilidade,
    onDeletar: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(formatarHorarioSlot(slot), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (slot.isBooked) {
                    Surface(
                        color = VerdeClaro,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "🔒 Este horário foi reservado por um cliente.",
                            modifier  = Modifier.padding(10.dp),
                            fontSize  = 13.sp,
                            color     = Verde,
                        )
                    }
                } else {
                    Text(
                        "Status: Disponível para agendamento",
                        fontSize = 13.sp,
                        color    = InkMuted,
                    )
                }
            }
        },
        confirmButton = {
            if (!slot.isBooked) {
                Button(
                    onClick = onDeletar,
                    colors  = ButtonDefaults.buttonColors(containerColor = Urgente),
                ) {
                    Text("🗑 Remover horário", color = Color.White)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors  = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Fechar", color = Color.White)
                }
            }
        },
        dismissButton = {
            if (!slot.isBooked) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = InkMuted)
                }
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLES AUXILIARES COMPARTILHADOS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun TopBarAgenda(titulo: String, onVoltar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 16.dp)
            .padding(top = 48.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onVoltar) {
            Text("←", fontSize = 20.sp, color = Azul, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
fun ErroAgenda(mensagem: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("⚠️", fontSize = 32.sp)
            Text(mensagem, fontSize = 14.sp, color = Urgente, textAlign = TextAlign.Center)
            Button(
                onClick = onRetry,
                colors  = ButtonDefaults.buttonColors(containerColor = Verde),
            ) {
                Text("Tentar novamente", color = Color.White)
            }
        }
    }
}

@Composable
private fun CampoHora(
    label:    String,
    valor:    String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = InkMuted, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value         = valor,
            onValueChange = { if (it.length <= 5) onChange(it) },
            singleLine    = true,
            placeholder   = { Text("HH:mm", fontSize = 13.sp, color = InkMuted) },
            shape         = RoundedCornerShape(8.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Verde,
                unfocusedBorderColor = Color(0xFFE0E0E0),
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = Ink,
            ),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPERS DE DATA — compatíveis com API 24+
// ═══════════════════════════════════════════════════════════════════════════

private fun hojeChave(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

// Retorna lista de (chave="yyyy-MM-dd", label="Seg", diaN="25") para 14 dias
private fun gerarProximos14Dias(): List<Triple<String, String, String>> {
    val sdf   = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sDia  = SimpleDateFormat("dd",          Locale.US)
    sdf.timeZone  = TimeZone.getTimeZone("UTC")
    sDia.timeZone = TimeZone.getTimeZone("UTC")

    val diasSemana = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    val cal        = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    return (0 until 14).map {
        val chave = sdf.format(cal.time)
        val diaN  = sDia.format(cal.time)
        val label = diasSemana[cal.get(Calendar.DAY_OF_WEEK) - 1]
        cal.add(Calendar.DAY_OF_MONTH, 1)
        Triple(chave, label, diaN)
    }
}

private fun formatarDataCurta(chave: String): String {
    val p = chave.split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else chave
}