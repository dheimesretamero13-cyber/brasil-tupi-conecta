package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// AbaFinanceiroDash.kt  · Fase 2.4
//
// UI da aba "Financeiro" no DashboardProfissionalScreen.
// Agora recebe `isPmp` para que a taxa de retenção (30% / 10%) seja
// aplicada corretamente no ViewModel.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.*

@Composable
fun AbaFinanceiroDash(isPmp: Boolean = false) {
    val vm: FinanceiroViewModel = viewModel(
        factory = FinanceiroViewModelFactory(isPmp = isPmp)
    )

    val uiState            by vm.uiState.collectAsState()
    val mostrarDialogSaque by vm.mostrarDialogSaque.collectAsState()
    val saldoDisponivel    by vm.saldoDisponivelSaque.collectAsState()

    val animacaoValores = remember { Animatable(0f) }
    LaunchedEffect(uiState) {
        if (uiState is FinanceiroUiState.Sucesso) {
            animacaoValores.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val estado = uiState) {

            is FinanceiroUiState.Carregando -> {
                Box(
                    modifier         = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = Verde, strokeWidth = 3.dp)
                        Text("Carregando dados financeiros...", fontSize = 13.sp, color = InkMuted)
                    }
                }
            }

            is FinanceiroUiState.Erro -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFFFDE8E8)),
                ) {
                    Column(
                        modifier            = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("⚠️", fontSize = 28.sp)
                        Text(estado.mensagem, fontSize = 14.sp, color = Urgente, textAlign = TextAlign.Center)
                        Button(
                            onClick = { vm.carregarDados() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Verde),
                        ) {
                            Text("Tentar novamente", color = Color.White)
                        }
                    }
                }
            }

            is FinanceiroUiState.Sucesso -> {
                val resumo     = estado.resumo
                val transacoes = estado.transacoes
                val grafico    = estado.grafico

                val taxaReais  = resumo.totalBruto * (resumo.taxaPct / 100.0)
                val fator      = animacaoValores.value

                // ── CARD PRINCIPAL — Valor Líquido ────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = Azul),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Saldo líquido disponível", fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.75f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            formatarMoeda(resumo.totalLiquido * fator),
                            fontSize   = 32.sp,
                            fontWeight = FontWeight.Black,
                            color      = Color.White,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MiniMetrica("Bruto",    formatarMoeda(resumo.totalBruto * fator))
                            MiniMetrica("Taxa ${resumo.taxaPct.toInt()}%", "- ${formatarMoeda(taxaReais * fator)}")
                            MiniMetrica("Pendente", formatarMoeda(resumo.totalPendente * fator))
                        }
                    }
                }

                // ── 3 CARDS DE MÉTRICAS ───────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricaCard(
                        modifier = Modifier.weight(1f),
                        icone    = "💵",
                        numero   = formatarMoeda(resumo.totalBruto),
                        label    = "Total bruto",
                        cor      = Verde,
                    )
                    MetricaCard(
                        modifier = Modifier.weight(1f),
                        icone    = "📊",
                        numero   = "${resumo.taxaPct.toInt()}%",
                        label    = "Taxa plataforma",
                        cor      = Azul,
                    )
                    MetricaCard(
                        modifier = Modifier.weight(1f),
                        icone    = "✅",
                        numero   = "${transacoes.count { it.status == "approved" }}",
                        label    = "Aprovados",
                        cor      = Verde,
                    )
                }

                // ── GRÁFICO DE EVOLUÇÃO DIÁRIA ────────────────────────────
                if (grafico.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = CardDefaults.cardColors(containerColor = Surface),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Evolução dos últimos ${grafico.size} dias",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Ink,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            GraficoBarras(
                                pontos   = grafico,
                                corBarra = Verde,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                            )
                        }
                    }
                }

                // ── LISTA DE TRANSAÇÕES ───────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = Surface),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Transações recentes",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Ink,
                            )
                            Text(
                                "${transacoes.size} registros",
                                fontSize = 11.sp,
                                color    = InkMuted,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (transacoes.isEmpty()) {
                            Text(
                                "Nenhuma transação ainda",
                                fontSize  = 13.sp,
                                color     = InkMuted,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            )
                        } else {
                            transacoes.forEach { t ->
                                ItemTransacao(transacao = t)
                                HorizontalDivider(color = SurfaceOff)
                            }
                        }
                    }
                }

                // ── BOTÃO SOLICITAR SAQUE ─────────────────────────────────
                Button(
                    onClick  = { vm.solicitarSaque() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC49A2A),  // Dourado
                        contentColor   = Color.White,
                    ),
                ) {
                    Text(
                        "💳  Solicitar Saque",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // ── DIALOG DE SAQUE ───────────────────────────────────────────────────
    if (mostrarDialogSaque) {
        AlertDialog(
            onDismissRequest = { vm.dispensarDialogSaque() },
            title = { Text("Saldo disponível para saque", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    if (saldoDisponivel > 0.0)
                        "Você tem ${formatarMoeda(saldoDisponivel)} disponível para saque.\n\n" +
                                "O valor corresponde a transações aprovadas há mais de 15 dias.\n\n" +
                                "A transferência será processada em até 2 dias úteis."
                    else
                        "Nenhum saldo disponível para saque no momento.\n\n" +
                                "Os valores só ficam disponíveis 15 dias após a aprovação da venda.",
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.dispensarDialogSaque() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Entendi", color = Color.White)
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GRÁFICO DE BARRAS — Canvas nativo do Compose
// Zero dependência externa. Visual limpo e performático.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun GraficoBarras(
    pontos:   List<PontoGrafico>,
    corBarra: Color,
    modifier: Modifier = Modifier,
) {
    if (pontos.isEmpty()) return

    val maxValor = pontos.maxOf { it.valor }.takeIf { it > 0 } ?: 1.0
    val corTexto = InkMuted.toArgb()
    val corFundo = corBarra.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val larguraTotalBarra = size.width / pontos.size
        val espacamento       = larguraTotalBarra * 0.2f
        val larguraBarra      = larguraTotalBarra - espacamento
        val alturaMaxima      = size.height - 28.dp.toPx()   // reservar espaço para label

        pontos.forEachIndexed { i, ponto ->
            val x             = i * larguraTotalBarra + espacamento / 2f
            val alturaBarra   = (ponto.valor / maxValor * alturaMaxima).toFloat()
            val yTopo         = alturaMaxima - alturaBarra

            // Fundo da barra (coluna inteira)
            drawRoundRect(
                color        = corFundo,
                topLeft      = Offset(x, 0f),
                size         = Size(larguraBarra, alturaMaxima),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )

            // Barra de valor
            if (alturaBarra > 0) {
                drawRoundRect(
                    color        = corBarra,
                    topLeft      = Offset(x, yTopo),
                    size         = Size(larguraBarra, alturaBarra),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }

            // Label do dia
            drawContext.canvas.nativeCanvas.drawText(
                ponto.dia,
                x + larguraBarra / 2f,
                size.height,
                android.graphics.Paint().apply {
                    color     = corTexto
                    textSize  = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLES AUXILIARES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ItemTransacao(transacao: TransacaoFinanceira) {
    val (emoji, label, cor) = when (transacao.status) {
        "approved" -> Triple("✅", "Recebido",  Verde)
        "pending"  -> Triple("⏳", "Pendente",  Color(0xFFF57F17))
        "rejected" -> Triple("❌", "Recusado",  Urgente)
        "refunded" -> Triple("↩️", "Reembolsado", InkMuted)
        else       -> Triple("•",  transacao.status, InkMuted)
    }

    val dataFormatada = transacao.criadoEm
        ?.take(10)
        ?.split("-")
        ?.let { p -> if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else transacao.criadoEm }
        ?: "—"

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ícone de status
        Box(
            modifier         = Modifier
                .size(38.dp)
                .background(cor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = "Consulta urgente",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color    = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text     = dataFormatada,
                fontSize = 11.sp,
                color    = InkMuted,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text       = formatarMoeda(transacao.valor),
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                color      = if (transacao.status == "approved") Verde else InkMuted,
            )
            Text(
                text     = label,
                fontSize = 10.sp,
                color    = cor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MiniMetrica(label: String, valor: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f))
    }
}

// ── Formatação de moeda — sem BigDecimal (evitar crash em API < 26) ───────
internal fun formatarMoeda(valor: Double): String {
    val centavosInt = Math.round(valor * 100)
    val inteiro = centavosInt / 100
    val centavos = (centavosInt % 100).toInt()
    // Formata o inteiro com separador de milhar (.) – padrão pt-BR
    val inteiroFmt = String.format("%,d", inteiro).replace(",", ".")
    return "R$ $inteiroFmt,${"%02d".format(centavos)}"
}