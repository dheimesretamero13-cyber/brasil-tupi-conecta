package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AvaliacaoScreen.kt  · Fase 2.1.1
//
// Tela de avaliação pós-chamada — obrigatória para fechar o ciclo financeiro.
// Regras de Ouro nº 5 e 6 do CONEXOES.md:
//  • Avaliação sempre vinculada ao urgencia_id (nunca genérica)
//  • Ciclo financeiro só fecha com avaliação confirmada
//
// UX:
//  • Escala de 1–5 estrelas interativa
//  • Campo de comentário opcional (máx 400 chars)
//  • Botão de envio desabilitado até selecionar nota
//  • Guard de fechamento: alerta se usuário tentar sair sem avaliar
//  • Loading durante envio — botão desabilitado para evitar duplo envio
// ═══════════════════════════════════════════════════════════════════════════

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*

private const val TAG_AVALIACAO = "AvaliacaoScreen"

// ═══════════════════════════════════════════════════════════════════════════
// TELA PRINCIPAL
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun AvaliacaoScreen(
    urgenciaId:   String,
    onConcluida:  () -> Unit,   // navegar após avaliação enviada com sucesso
    onPularForced: () -> Unit,  // escape de emergência (ex: erro fatal repetido)
) {
    val avaliacaoState by AvaliacaoRepository.state.collectAsState()

    var notaSelecionada    by remember { mutableStateOf(0) }
    var comentario         by remember { mutableStateOf("") }
    var mostrarGuardSaida  by remember { mutableStateOf(false) }
    var mostrarJaAvaliada  by remember { mutableStateOf(false) }
    var erroMensagem       by remember { mutableStateOf("") }

    val isEnviando = avaliacaoState is AvaliacaoState.Enviando

    // ── Reagir ao resultado do repositório ────────────────────────────────
    LaunchedEffect(avaliacaoState) {
        when (val estado = avaliacaoState) {
            is AvaliacaoState.Resultado -> {
                when (val resultado = estado.resultado) {
                    is ResultadoAvaliacao.Sucesso -> {
                        AvaliacaoRepository.resetar()
                        onConcluida()
                    }
                    is ResultadoAvaliacao.JaAvaliada -> {
                        mostrarJaAvaliada = true
                    }
                    is ResultadoAvaliacao.Erro -> {
                        erroMensagem = resultado.mensagem
                    }
                }
            }
            else -> Unit
        }
    }

    // ── Guard de fechamento via botão Voltar do Android ───────────────────
    BackHandler(enabled = !isEnviando) {
        if (notaSelecionada == 0) {
            mostrarGuardSaida = true
        } else {
            // Tem nota mas não enviou — confirmar saída
            mostrarGuardSaida = true
        }
    }

    // ── Layout principal ──────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Ícone de conclusão
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(VerdeClaro, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✅", fontSize = 32.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text       = "Atendimento encerrado",
            fontSize   = 22.sp,
            fontWeight = FontWeight.Black,
            color      = Ink,
            textAlign  = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text      = "Sua avaliação é importante para manter a qualidade da plataforma e liberar o pagamento do profissional.",
            fontSize  = 13.sp,
            color     = InkMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Seletor de estrelas ───────────────────────────────────────────
        Text(
            text       = "Como foi o atendimento?",
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Ink,
        )

        Spacer(modifier = Modifier.height(16.dp))

        SeletorEstrelas(
            notaAtual       = notaSelecionada,
            onNotaSelecionada = { notaSelecionada = it },
            habilitado      = !isEnviando,
        )

        // Label descritivo da nota selecionada
        if (notaSelecionada > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = descricaoNota(notaSelecionada),
                fontSize = 13.sp,
                color    = Verde,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Campo de comentário ───────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text       = "Comentário (opcional)",
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Ink,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value         = comentario,
                onValueChange = { if (it.length <= 400) comentario = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder   = { Text("Conte como foi sua experiência...", color = InkMuted, fontSize = 13.sp) },
                shape         = RoundedCornerShape(10.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Verde,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                ),
                maxLines = 5,
                enabled  = !isEnviando,
            )
            Text(
                text     = "${comentario.length}/400",
                fontSize = 11.sp,
                color    = InkMuted,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Mensagem de erro ──────────────────────────────────────────────
        if (erroMensagem.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = Color(0xFFFDE8E8),
                shape    = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text     = erroMensagem,
                        color    = Urgente,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { erroMensagem = "" }) {
                        Text("Ok", color = Urgente, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Botão enviar ──────────────────────────────────────────────────
        Button(
            onClick = {
                erroMensagem = ""
                AvaliacaoRepository.submeter(
                    urgenciaId = urgenciaId,
                    nota       = notaSelecionada,
                    comentario = comentario.trim().ifBlank { null },
                )
            },
            enabled  = notaSelecionada > 0 && !isEnviando,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor         = Verde,
                contentColor           = Color.White,
                disabledContainerColor = SurfaceOff,
                disabledContentColor   = InkMuted,
            ),
        ) {
            if (isEnviando) {
                CircularProgressIndicator(
                    color       = Color.White,
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text       = "Enviar avaliação",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (notaSelecionada == 0 && !isEnviando) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = "Selecione uma nota para continuar",
                fontSize = 12.sp,
                color    = InkMuted,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIÁLOGOS
    // ═══════════════════════════════════════════════════════════════════════

    // Guard de saída sem avaliação
    if (mostrarGuardSaida) {
        AlertDialog(
            onDismissRequest = { mostrarGuardSaida = false },
            title = {
                Text(
                    text       = "Avaliação pendente",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Avalie o atendimento para finalizar seu ciclo.\n\n" +
                            "Sem a avaliação, o pagamento do profissional não pode ser processado.",
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { mostrarGuardSaida = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Avaliar agora", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    mostrarGuardSaida = false
                    AvaliacaoRepository.resetar()
                    onPularForced()
                }) {
                    Text("Pular mesmo assim", color = InkMuted)
                }
            },
        )
    }

    // Diálogo de avaliação já existente
    if (mostrarJaAvaliada) {
        AlertDialog(
            onDismissRequest = { mostrarJaAvaliada = false },
            title = { Text("Avaliação já registrada", fontWeight = FontWeight.Bold) },
            text  = { Text("Este atendimento já foi avaliado anteriormente.") },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarJaAvaliada = false
                        AvaliacaoRepository.resetar()
                        onConcluida()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Ok", color = Color.White)
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SELETOR DE ESTRELAS — interativo com animação de escala
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SeletorEstrelas(
    notaAtual:        Int,
    onNotaSelecionada: (Int) -> Unit,
    habilitado:       Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        for (i in 1..5) {
            val ativa   = i <= notaAtual
            val cor by animateColorAsState(
                targetValue   = if (ativa) Color(0xFFFFC107) else Color(0xFFE0E0E0),
                animationSpec = tween(durationMillis = 200),
                label         = "star_color_$i",
            )
            val escala by animateFloatAsState(
                targetValue   = if (ativa) 1.2f else 1.0f,
                animationSpec = tween(durationMillis = 150),
                label         = "star_scale_$i",
            )
            Text(
                text     = "★",
                fontSize = 40.sp,
                color    = cor,
                modifier = Modifier
                    .scale(escala)
                    .then(
                        if (habilitado) Modifier.clickable { onNotaSelecionada(i) }
                        else Modifier
                    ),
            )
        }
    }
}

// ── Descrição textual da nota ─────────────────────────────────────────────
private fun descricaoNota(nota: Int): String = when (nota) {
    1 -> "Muito ruim"
    2 -> "Ruim"
    3 -> "Regular"
    4 -> "Bom"
    5 -> "Excelente!"
    else -> ""
}