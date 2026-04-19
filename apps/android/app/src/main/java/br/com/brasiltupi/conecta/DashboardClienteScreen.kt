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
data class ConsultaCliente(
    val id: Int,
    val profissional: String,
    val area: String,
    val data: String,
    val hora: String,
    val tipo: String,
    val status: String,
    val avaliada: Boolean,
    val avaliacao: Int,
    val valor: String,
)

val consultasMock = listOf(
    ConsultaCliente(1, "Dr. Carlos Henrique", "Saúde e Bem-estar", "18/04/2025", "14:30", "Urgente", "concluida", true, 5, "R$ 120"),
    ConsultaCliente(2, "Dra. Mariana Costa", "Psicologia e Terapia", "15/04/2025", "10:00", "Normal", "concluida", false, 0, "R$ 80"),
    ConsultaCliente(3, "Dr. Rafael Souza", "Direito e Jurídico", "20/04/2025", "16:00", "Normal", "agendada", false, 0, "R$ 90"),
    ConsultaCliente(4, "Dr. Carlos Henrique", "Saúde e Bem-estar", "10/04/2025", "09:00", "Normal", "concluida", true, 5, "R$ 80"),
)

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun DashboardClienteScreen(onSair: () -> Unit) {
    var abaSelecionada by remember { mutableStateOf("visao") }
    val pendentes = consultasMock.count { it.status == "concluida" && !it.avaliada }

    val abas = listOf(
        "visao" to "Visão Geral",
        "consultas" to "Consultas",
        "busca" to "Buscar",
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
                Text("Conecta", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Verde,
                    modifier = Modifier.offset(y = (-4).dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pendentes > 0) {
                    Box {
                        IconButton(onClick = { abaSelecionada = "consultas" }) {
                            Text("🔔", fontSize = 20.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Urgente, RoundedCornerShape(50))
                                .align(Alignment.TopEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$pendentes", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Verde, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("JF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                TextButton(onClick = onSair) {
                    Text("Sair", color = InkMuted, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        // Tabs
        TabRow(
            selectedTabIndex = abas.indexOfFirst { it.first == abaSelecionada },
            containerColor = Surface,
            contentColor = Verde,
        ) {
            abas.forEach { (id, label) ->
                Tab(
                    selected = abaSelecionada == id,
                    onClick = { abaSelecionada = id },
                    text = {
                        Text(
                            label, fontSize = 13.sp,
                            fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal,
                            color = if (abaSelecionada == id) Verde else InkMuted
                        )
                    }
                )
            }
        }

        // Conteúdo
        when (abaSelecionada) {
            "visao"    -> AbaVisaoGeralCliente(onNavegar = { abaSelecionada = it })
            "consultas" -> AbaConsultasCliente()
            "busca"    -> AbaBuscaCliente()
        }
    }
}

// ── ABA: VISÃO GERAL ──────────────────────────────────
@Composable
fun AbaVisaoGeralCliente(onNavegar: (String) -> Unit) {
    val pendentes = consultasMock.filter { it.status == "concluida" && !it.avaliada }
    val proximas  = consultasMock.filter { it.status == "agendada" }
    var avaliarConsulta by remember { mutableStateOf<ConsultaCliente?>(null) }

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
            colors = CardDefaults.cardColors(containerColor = Verde)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Olá, Juliana! 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Você tem ${proximas.size} consulta(s) agendada(s).",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Métricas
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaCard(
                modifier = Modifier.weight(1f),
                icone = "📋",
                numero = "${consultasMock.count { it.status == "concluida" }}",
                label = "Realizadas",
                cor = Azul
            )
            MetricaCard(
                modifier = Modifier.weight(1f),
                icone = "📅",
                numero = "${proximas.size}",
                label = "Agendadas",
                cor = Verde
            )
        }

        // Banner urgente
        Card(
            onClick = { onNavegar("busca") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0808))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Precisa de atendimento agora?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Resposta em até 45 minutos", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
                Box(
                    modifier = Modifier
                        .background(DouradoMedio, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Ver →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }

        // Avaliações pendentes
        if (pendentes.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Urgente.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Avaliações pendentes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Urgente, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${pendentes.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    pendentes.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(UrgenteClaro, RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    c.profissional.split(" ").map { it[0] }.joinToString("").take(2),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Urgente
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.profissional, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("${c.data} · ${c.tipo}", fontSize = 11.sp, color = InkMuted)
                            }
                            Button(
                                onClick = { avaliarConsulta = c },
                                colors = ButtonDefaults.buttonColors(containerColor = Urgente, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Avaliar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Próximas consultas
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
                    Text("Próximas consultas", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    TextButton(onClick = { onNavegar("consultas") }) {
                        Text("Ver todas →", color = Verde, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (proximas.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nenhuma consulta agendada", fontSize = 13.sp, color = InkMuted)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onNavegar("busca") },
                            colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ Agendar consulta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    proximas.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(VerdeClaro, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(c.data.split("/")[0], fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Verde)
                                    Text("abr", fontSize = 9.sp, color = Verde)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.profissional, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("${c.hora} · ${c.area}", fontSize = 11.sp, color = InkMuted)
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (c.tipo == "Urgente") UrgenteClaro else VerdeClaro,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (c.tipo == "Urgente") Urgente else Verde)
                            }
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                }
            }
        }
    }

    // Modal avaliação
    if (avaliarConsulta != null) {
        ModalAvaliacao(
            consulta = avaliarConsulta!!,
            onFechar = { avaliarConsulta = null }
        )
    }
}

// ── MODAL AVALIAÇÃO ───────────────────────────────────
@Composable
fun ModalAvaliacao(consulta: ConsultaCliente, onFechar: () -> Unit) {
    var nota by remember { mutableStateOf(0) }
    var enviado by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                if (enviado) "Avaliação enviada!" else "Avaliar atendimento",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink
            )
        },
        text = {
            if (enviado) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Verde, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Obrigada por contribuir com a credibilidade da plataforma.",
                        fontSize = 13.sp, color = InkMuted, textAlign = TextAlign.Center
                    )
                }
            } else {
                Column {
                    Text(
                        "Como foi sua consulta com ${consulta.profissional}?",
                        fontSize = 13.sp, color = InkMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        (1..5).forEach { i ->
                            TextButton(onClick = { nota = i }) {
                                Text(
                                    "★",
                                    fontSize = 32.sp,
                                    color = if (i <= nota) DouradoMedio else Color(0xFFE5E7EB)
                                )
                            }
                        }
                    }
                    if (nota > 0) {
                        Text(
                            listOf("", "Muito ruim", "Ruim", "Regular", "Bom", "Excelente")[nota],
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (enviado) onFechar() else if (nota > 0) enviado = true },
                enabled = enviado || nota > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (enviado) "Fechar" else "Enviar avaliação", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!enviado) {
                TextButton(onClick = onFechar) {
                    Text("Cancelar", color = InkMuted, fontSize = 13.sp)
                }
            }
        }
    )
}

// ── ABA: CONSULTAS ────────────────────────────────────
@Composable
fun AbaConsultasCliente() {
    var filtro by remember { mutableStateOf("todas") }
    var avaliarConsulta by remember { mutableStateOf<ConsultaCliente?>(null) }

    val lista = when (filtro) {
        "concluida" -> consultasMock.filter { it.status == "concluida" }
        "agendada"  -> consultasMock.filter { it.status == "agendada" }
        else        -> consultasMock
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
            listOf("todas" to "Todas", "concluida" to "Concluídas", "agendada" to "Agendadas").forEach { (id, label) ->
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
        lista.forEach { c ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(AzulClaro, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                c.profissional.split(" ").map { it[0] }.joinToString("").take(2),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Azul
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.profissional, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(c.area, fontSize = 12.sp, color = InkMuted)
                            Text("${c.data} às ${c.hora} · ${c.valor}", fontSize = 11.sp, color = InkMuted)
                        }
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
                                        if (c.tipo == "Urgente") UrgenteClaro else VerdeClaro,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (c.tipo == "Urgente") Urgente else Verde)
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (c.status == "concluida") VerdeClaro else AzulClaro,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (c.status == "concluida") "Concluída" else "Agendada",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (c.status == "concluida") Verde else Azul
                                )
                            }
                        }
                        if (c.status == "concluida") {
                            if (c.avaliada) {
                                Text("★".repeat(c.avaliacao), fontSize = 13.sp, color = DouradoMedio)
                            } else {
                                Button(
                                    onClick = { avaliarConsulta = c },
                                    colors = ButtonDefaults.buttonColors(containerColor = Urgente, contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Avaliar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (lista.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("Nenhuma consulta encontrada", fontSize = 14.sp, color = InkMuted, textAlign = TextAlign.Center)
            }
        }
    }

    if (avaliarConsulta != null) {
        ModalAvaliacao(
            consulta = avaliarConsulta!!,
            onFechar = { avaliarConsulta = null }
        )
    }
}

// ── ABA: BUSCA ────────────────────────────────────────
@Composable
fun AbaBuscaCliente() {
    var profSelecionado by remember { mutableStateOf<ProfissionalPMP?>(null) }
    var agendando by remember { mutableStateOf<Pair<ProfissionalPMP, String>?>(null) }

    if (profSelecionado != null) {
        PerfilPublicoScreen(
            prof = profSelecionado!!,
            onVoltar = { profSelecionado = null },
            onAgendar = { tipo -> agendando = profSelecionado!! to tipo }
        )
        return
    }

    if (agendando != null) {
        AgendarScreen(
            prof = agendando!!.first,
            tipo = agendando!!.second,
            onVoltar = { agendando = null },
            onConcluido = { agendando = null; profSelecionado = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner urgente
        Card(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0808))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Atendimento urgente", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${profissionaisMock.count { it.disponivelUrgente }} disponíveis agora · 45min", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }

        // Lista de PMPs
        Text("Profissionais PMP", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)

        profissionaisMock.forEach { prof ->
            CardProfissional(prof = prof, onClick = { profSelecionado = prof })
        }
    }
}