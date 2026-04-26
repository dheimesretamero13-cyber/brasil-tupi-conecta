package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf

// ═══════════════════════════════════════════════════════════════════════════
// RelatoriosProfissionalScreen.kt  · Fase 4.4
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatoriosProfissionalScreen(onVoltar: () -> Unit) {
    val vm: RelatoriosViewModel = viewModel(
        factory = RelatoriosViewModelFactory(RelatoriosRepositoryFactory.create())
    )
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatórios", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = Color(0xFFF8F7F4),
    ) { padding ->
        when (val estado = uiState) {
            is RelatoriosUiState.Carregando -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator(color = Verde)
                }
            }
            is RelatoriosUiState.SemDados -> {
                EstadoSemDados(modifier = Modifier.fillMaxSize().padding(padding))
            }
            is RelatoriosUiState.Erro -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier            = Modifier.padding(32.dp),
                    ) {
                        Text("⚠️", fontSize = 40.sp)
                        Text(estado.mensagem, color = InkMuted, textAlign = TextAlign.Center)
                        Button(onClick = { vm.carregarRelatorios() },
                            colors = ButtonDefaults.buttonColors(containerColor = Verde)) {
                            Text("Tentar novamente", color = Color.White)
                        }
                    }
                }
            }
            is RelatoriosUiState.Sucesso -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Cards de resumo ───────────────────────────────────
                    CardResumoRelatorio(resumo = estado.resumo)

                    // ── Gráfico de linha: taxa de conversão ───────────────
                    if (estado.semanas.size >= 2) {
                        CardGraficoConversao(semanas = estado.semanas)
                    }

                    // ── Gráfico de linha: nota média ──────────────────────
                    if (estado.semanas.any { it.notaMedia > 0 }) {
                        CardGraficoNota(semanas = estado.semanas)
                    }

                    // ── Gráfico de barras: horários de pico ───────────────
                    if (estado.horarios.isNotEmpty()) {
                        CardGraficoHorarios(horarios = estado.horarios)
                    }

                    // ── Tabela semanal ────────────────────────────────────
                    CardTabelaSemanal(semanas = estado.semanas)

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CARDS DE RESUMO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CardResumoRelatorio(resumo: ResumoRelatorio) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Azul),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Últimas 4 semanas",
                fontSize = 13.sp,
                color    = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "R$ ${"%.2f".format(resumo.totalGanhoMes)}",
                fontSize   = 30.sp,
                fontWeight = FontWeight.Black,
                color      = Color.White,
            )
            Text(
                "Total recebido",
                fontSize = 12.sp,
                color    = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiniStatCard("${resumo.totalAtendMes}", "Atendimentos", Color.White)
                MiniStatCard("${"%.1f".format(resumo.taxaConversaoMedia)}%", "Conversão", Color.White)
                MiniStatCard("⭐ ${"%.1f".format(resumo.notaMediaGeral)}", "Nota média", Color.White)
                MiniStatCard("${resumo.tempoMedioResposta.toInt()}min", "Resp. média", Color.White)
            }
        }
    }
}

@Composable
private fun MiniStatCard(valor: String, label: String, cor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cor)
        Text(label, fontSize = 9.sp, color = cor.copy(alpha = 0.65f), textAlign = TextAlign.Center)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GRÁFICO DE LINHA — TAXA DE CONVERSÃO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CardGraficoConversao(semanas: List<StatsSemanal>) {
    val entries = semanas.mapIndexed { i, s ->
        entryOf(i.toFloat(), s.taxaConversao.toFloat())
    }
    val model = entryModelOf(entries)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Taxa de Conversão", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "Solicitações → Atendimentos concluídos (%)",
                fontSize = 12.sp,
                color    = InkMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )
            Chart(
                chart       = lineChart(
                    lines = listOf(
                        LineChart.LineSpec(
                            lineColor         = Verde.toArgb(),
                            lineBackgroundShader = DynamicShaders.fromBrush(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Verde.copy(alpha = 0.3f), Color.Transparent)
                                )
                            ),
                        )
                    )
                ),
                model       = model,
                startAxis   = rememberStartAxis(
                    valueFormatter = { value, _ -> "${value.toInt()}%" },
                    itemPlacer     = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                ),
                bottomAxis  = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        semanas.getOrNull(value.toInt())
                            ?.semanaReferencia?.takeLast(5)?.replace("-", "/")
                            ?: ""
                    },
                ),
                modifier    = Modifier.fillMaxWidth().height(180.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GRÁFICO DE LINHA — NOTA MÉDIA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CardGraficoNota(semanas: List<StatsSemanal>) {
    val entries = semanas.mapIndexed { i, s ->
        entryOf(i.toFloat(), s.notaMedia.toFloat())
    }
    val model = entryModelOf(entries)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Evolução da Nota Média", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "Avaliação média das consultas por semana",
                fontSize = 12.sp,
                color    = InkMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )
            Chart(
                chart      = lineChart(
                    lines = listOf(
                        LineChart.LineSpec(
                            lineColor            = DouradoMedio.toArgb(),
                            lineBackgroundShader = DynamicShaders.fromBrush(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(DouradoMedio.copy(alpha = 0.25f), Color.Transparent)
                                )
                            ),
                        )
                    )
                ),
                model      = model,
                startAxis  = rememberStartAxis(
                    valueFormatter = { value, _ -> "${"%.1f".format(value)}★" },
                    itemPlacer     = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        semanas.getOrNull(value.toInt())
                            ?.semanaReferencia?.takeLast(5)?.replace("-", "/")
                            ?: ""
                    },
                ),
                modifier   = Modifier.fillMaxWidth().height(160.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GRÁFICO DE BARRAS — HORÁRIOS DE PICO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CardGraficoHorarios(horarios: List<HorarioPico>) {
    // Preencher todas as 24 horas (com 0 onde não há dados)
    val todosHorarios = (0..23).map { hora ->
        horarios.firstOrNull { it.hora == hora }?.total?.toFloat() ?: 0f
    }
    val entries = todosHorarios.mapIndexed { i, v -> entryOf(i.toFloat(), v) }
    val model   = entryModelOf(entries)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Horários de Pico", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "Atendimentos por hora — semana mais recente",
                fontSize = 12.sp,
                color    = InkMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )
            Chart(
                chart      = columnChart(
                    columns = listOf(
                        ShapeComponent(
                            shape = Shapes.roundedCornerShape(topLeftPercent = 40, topRightPercent = 40),
                            color = Azul.toArgb(),
                        )
                    )
                ),
                model      = model,
                startAxis  = rememberStartAxis(
                    valueFormatter = { value, _ -> "${value.toInt()}" },
                    itemPlacer     = AxisItemPlacer.Vertical.default(maxItemCount = 4),
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        val h = value.toInt()
                        if (h % 6 == 0) "${h}h" else ""
                    },
                    itemPlacer = AxisItemPlacer.Horizontal.default(spacing = 1),
                ),
                modifier   = Modifier.fillMaxWidth().height(160.dp),
            )

            // Legenda da hora de pico
            val horaPico = horarios.maxByOrNull { it.total }
            if (horaPico != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AzulClaro, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "⏰ Hora de pico: ${horaPico.hora}h–${horaPico.hora + 1}h (${horaPico.total} atend.)",
                        fontSize   = 12.sp,
                        color      = Azul,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TABELA SEMANAL
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CardTabelaSemanal(semanas: List<StatsSemanal>) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Histórico Semanal",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = Ink,
                modifier   = Modifier.padding(bottom = 12.dp),
            )

            // Header
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Semana",   fontSize = 11.sp, color = InkMuted, modifier = Modifier.weight(2f))
                Text("Atend.",   fontSize = 11.sp, color = InkMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Conv.",    fontSize = 11.sp, color = InkMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Nota",     fontSize = 11.sp, color = InkMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Ganho",    fontSize = 11.sp, color = InkMuted, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
            }

            HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(vertical = 8.dp))

            semanas.reversed().forEach { s ->
                val data = s.semanaReferencia.takeLast(5).replace("-", "/")
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(data,                                     fontSize = 12.sp, color = Ink, modifier = Modifier.weight(2f))
                    Text("${s.totalAtendimentos}",                  fontSize = 12.sp, color = Ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("${"%.0f".format(s.taxaConversao)}%",      fontSize = 12.sp, color = if (s.taxaConversao >= 50) Verde else Urgente, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("${"%.1f".format(s.notaMedia)}★",          fontSize = 12.sp, color = DouradoMedio, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("R$ ${"%.0f".format(s.totalGanho)}",       fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Verde, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
                }
                HorizontalDivider(color = Color(0xFFF8F8F8))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ESTADO SEM DADOS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EstadoSemDados(modifier: Modifier = Modifier) {
    Box(modifier, Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Text("📊", fontSize = 48.sp)
            Text(
                "Nenhum dado ainda",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = Ink,
            )
            Text(
                "Seus relatórios aparecem aqui após a primeira semana de atendimentos. " +
                        "Os dados são calculados toda segunda-feira às 04h.",
                fontSize  = 13.sp,
                color     = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}