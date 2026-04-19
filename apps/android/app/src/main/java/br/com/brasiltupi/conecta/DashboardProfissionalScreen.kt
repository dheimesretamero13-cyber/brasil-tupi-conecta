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

// ── DADOS MOCK ────────────────────────────────────────
data class Atendimento(
    val id: Int,
    val cliente: String,
    val data: String,
    val hora: String,
    val tipo: String,
    val status: String,
    val avaliacao: Int,
    val valor: String,
)

val atendimentosMock = listOf(
    Atendimento(1, "Ana Souza", "18/04/2025", "14:30", "Urgente", "concluido", 5, "R$ 120"),
    Atendimento(2, "Marcos Lima", "17/04/2025", "10:00", "Normal", "concluido", 5, "R$ 80"),
    Atendimento(3, "Fernanda Costa", "16/04/2025", "16:00", "Normal", "concluido", 4, "R$ 80"),
    Atendimento(4, "Roberto Alves", "15/04/2025", "09:00", "Urgente", "concluido", 5, "R$ 120"),
    Atendimento(5, "Juliana Peres", "20/04/2025", "11:00", "Normal", "agendado", 0, "R$ 80"),
)

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun DashboardProfissionalScreen(onSair: () -> Unit) {
    var abaSelecionada by remember { mutableStateOf("visao") }

    val abas = listOf(
        "visao" to "Visão Geral",
        "atendimentos" to "Atendimentos",
        "credibilidade" to "Credibilidade",
        "urgente" to "Urgente",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
    ) {
        // Topbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Brasil Tupi", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Azul)
                Text("Conecta", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Verde, modifier = Modifier.offset(y = (-4).dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Azul, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                TextButton(onClick = onSair) {
                    Text("Sair", color = InkMuted, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        // Tabs navegação
        ScrollableTabRow(
            selectedTabIndex = abas.indexOfFirst { it.first == abaSelecionada },
            containerColor = Surface,
            contentColor = Verde,
            edgePadding = 16.dp,
        ) {
            abas.forEachIndexed { _, (id, label) ->
                Tab(
                    selected = abaSelecionada == id,
                    onClick = { abaSelecionada = id },
                    text = {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal,
                            color = if (abaSelecionada == id) Verde else InkMuted
                        )
                    }
                )
            }
        }
        // Conteúdo
        when (abaSelecionada) {
            "visao"         -> AbaVisaoGeralDash()
            "atendimentos"  -> AbaAtendimentosDash()
            "credibilidade" -> AbaCredibilidadeDash()
            "urgente"       -> AbaUrgenteDash()
        }
    }
}

// ── ABA: VISÃO GERAL ──────────────────────────────────
@Composable
fun AbaVisaoGeralDash() {
    val ganhosMes = atendimentosMock
        .filter { it.status == "concluido" }
        .sumOf { it.valor.replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Saudação
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Azul)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Olá, Carlos! 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Você tem ${atendimentosMock.count { it.status == "agendado" }} atendimento(s) agendado(s).",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Métricas
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaCard(modifier = Modifier.weight(1f), icone = "📋", numero = "${dadosProfissionalMock.atendimentosTotal}", label = "Atendimentos", cor = Verde)
            MetricaCard(modifier = Modifier.weight(1f), icone = "⭐", numero = "${dadosProfissionalMock.avaliacaoMedia}", label = "Avaliação", cor = Dourado)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaCard(modifier = Modifier.weight(1f), icone = "💰", numero = "R$ $ganhosMes", label = "Ganhos mês", cor = Azul)
            MetricaCard(modifier = Modifier.weight(1f), icone = "🎯", numero = "${dadosProfissionalMock.credibilidade}", label = "Credibilidade", cor = Verde)
        }

        // Próximos atendimentos
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Próximos atendimentos", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                val proximos = atendimentosMock.filter { it.status == "agendado" }
                if (proximos.isEmpty()) {
                    Text("Nenhum atendimento agendado", fontSize = 13.sp, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    proximos.forEach { a ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(AzulClaro, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(a.data.split("/")[0], fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Azul)
                                    Text("abr", fontSize = 9.sp, color = Azul)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(a.cliente, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("${a.hora} · ${a.tipo}", fontSize = 12.sp, color = InkMuted)
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (a.tipo == "Urgente") UrgenteClaro else VerdeClaro,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(a.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (a.tipo == "Urgente") Urgente else Verde)
                            }
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                }
            }
        }

        // Últimas avaliações
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Últimas avaliações", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                atendimentosMock.filter { it.avaliacao > 0 }.take(3).forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(AzulClaro, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                a.cliente.split(" ").map { it[0] }.joinToString("").take(2),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Azul
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(a.cliente, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("${a.data} · ${a.tipo}", fontSize = 11.sp, color = InkMuted)
                        }
                        Text("★".repeat(a.avaliacao), fontSize = 13.sp, color = DouradoMedio)
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }

        // Status urgente
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (dadosProfissionalMock.disponivelUrgente) VerdeClaro else UrgenteClaro
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (dadosProfissionalMock.disponivelUrgente) "🟢" else "🔴", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (dadosProfissionalMock.disponivelUrgente) "Disponível para urgências" else "Indisponível para urgências",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (dadosProfissionalMock.disponivelUrgente) Verde else Urgente
                    )
                    Text(
                        if (dadosProfissionalMock.disponivelUrgente) "Você aparece na área urgente" else "Ative na aba Urgente",
                        fontSize = 11.sp, color = InkMuted
                    )
                }
            }
        }
    }
}

@Composable
fun MetricaCard(modifier: Modifier = Modifier, icone: String, numero: String, label: String, cor: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(cor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(icone, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(numero, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(label, fontSize = 11.sp, color = InkMuted)
        }
    }
}

// ── ABA: ATENDIMENTOS ─────────────────────────────────
@Composable
fun AbaAtendimentosDash() {
    var filtro by remember { mutableStateOf("todos") }
    val lista = when (filtro) {
        "concluido" -> atendimentosMock.filter { it.status == "concluido" }
        "agendado"  -> atendimentosMock.filter { it.status == "agendado" }
        else        -> atendimentosMock
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Filtros
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("todos" to "Todos", "concluido" to "Concluídos", "agendado" to "Agendados").forEach { (id, label) ->
                FilterChip(
                    selected = filtro == id,
                    onClick = { filtro = id },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Verde,
                        selectedLabelColor = Color.White,
                    )
                )
            }
        }

        // Lista
        lista.forEach { a ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AzulClaro, RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    a.cliente.split(" ").map { it[0] }.joinToString("").take(2),
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Azul
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(a.cliente, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("${a.data} às ${a.hora}", fontSize = 12.sp, color = InkMuted)
                            }
                        }
                        Text(a.valor, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (a.tipo == "Urgente") UrgenteClaro else VerdeClaro,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(a.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (a.tipo == "Urgente") Urgente else Verde)
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (a.status == "concluido") VerdeClaro else AzulClaro,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (a.status == "concluido") "Concluído" else "Agendado",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (a.status == "concluido") Verde else Azul
                                )
                            }
                        }
                        if (a.avaliacao > 0) {
                            Text("★".repeat(a.avaliacao), fontSize = 13.sp, color = DouradoMedio)
                        }
                    }
                }
            }
        }

        if (lista.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("Nenhum atendimento encontrado", fontSize = 14.sp, color = InkMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

// ── ABA: CREDIBILIDADE ────────────────────────────────
@Composable
fun AbaCredibilidadeDash() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pontuação
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sua pontuação", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            if (dadosProfissionalMock.credibilidade >= 80) Color(0xFFFDF3D8)
                            else VerdeClaro,
                            RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${dadosProfissionalMock.credibilidade}",
                            fontSize = 36.sp, fontWeight = FontWeight.Black,
                            color = if (dadosProfissionalMock.credibilidade >= 80) Dourado else Verde
                        )
                        Text("pontos", fontSize = 12.sp, color = InkMuted)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    if (dadosProfissionalMock.credibilidade >= 80) "🏆 Elegível ao PMP"
                    else if (dadosProfissionalMock.credibilidade >= 50) "📈 Crescendo"
                    else "🌱 Iniciando",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkSoft
                )
            }
        }

        // Fatores
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Como é calculado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                listOf(
                    Triple("Atendimentos", 30, Verde),
                    Triple("Avaliações", 28, Azul),
                    Triple("Pontualidade", 20, Dourado),
                ).forEach { (label, pts, cor) ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, fontSize = 13.sp, color = Ink)
                            Text("$pts pts", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cor)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { pts / 40f },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = cor,
                            trackColor = SurfaceOff,
                        )
                    }
                }
            }
        }

        // PMP
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F0)),
            border = androidx.compose.foundation.BorderStroke(1.dp, DouradoMedio.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Programa PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                Text(
                    "Faltam ${100 - dadosProfissionalMock.credibilidade} pontos para o PMP",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                LinearProgressIndicator(
                    progress = { dadosProfissionalMock.credibilidade / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = DouradoMedio,
                    trackColor = SurfaceOff,
                )
                Text(
                    "${dadosProfissionalMock.credibilidade}/100",
                    fontSize = 12.sp, color = InkMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = dadosProfissionalMock.credibilidade >= 80,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Azul, contentColor = Color.White,
                        disabledContainerColor = SurfaceOff, disabledContentColor = InkMuted
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (dadosProfissionalMock.credibilidade >= 80) "Candidatar-me ao PMP"
                        else "Disponível com 80 pontos",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── ABA: URGENTE ──────────────────────────────────────
@Composable
fun AbaUrgenteDash() {
    var ativo by remember { mutableStateOf(dadosProfissionalMock.disponivelUrgente) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Área Urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "Apareça para clientes que precisam de atendimento imediato.",
                            fontSize = 13.sp, color = InkMuted, lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = ativo,
                        onCheckedChange = { ativo = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Verde
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (ativo) VerdeClaro else UrgenteClaro,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (ativo) "🟢" else "🔴", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (ativo) "Disponível agora" else "Indisponível",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (ativo) Verde else Urgente
                    )
                }
            }
        }

        // Timers
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = UrgenteClaro),
                border = androidx.compose.foundation.BorderStroke(1.dp, Urgente.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("45", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Urgente)
                    Text("minutos", fontSize = 11.sp, color = InkMuted)
                    Text("Para iniciar", fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF3D8)),
                border = androidx.compose.foundation.BorderStroke(1.dp, DouradoMedio.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("15", fontSize = 36.sp, fontWeight = FontWeight.Black, color = DouradoMedio)
                    Text("minutos", fontSize = 11.sp, color = InkMuted)
                    Text("Duração", fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // Histórico urgente
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Histórico urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        Triple("3", "Urgentes\nrealizadas", Verde),
                        Triple("100%", "Pontua-\nlidade", Azul),
                        Triple("0", "Descum-\nprimenetos", Dourado),
                    ).forEach { (num, label, cor) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(num, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cor)
                            Text(label, fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VerdeClaro, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✓", fontSize = 14.sp, color = Verde, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Histórico limpo — acesso integral à área urgente.", fontSize = 12.sp, color = Verde)
                }
            }
        }
    }
}