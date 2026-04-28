package br.com.brasiltupi.conecta

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun DashboardProfissionalScreen(
    onSair: () -> Unit,
    onEstudio: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    onRelatorios: (() -> Unit)? = null,
) {
    var abaSelecionada by remember { mutableStateOf("visao") }

    var nomeUsuario       by remember { mutableStateOf("") }
    var iniciais          by remember { mutableStateOf("") }
    var credibilidade     by remember { mutableStateOf(0) }
    var disponivelUrgente by remember { mutableStateOf(false) }

    // Dados reais de consultas — compartilhados entre abas
    var consultas         by remember { mutableStateOf<List<ConsultaProfissional>>(emptyList()) }
    var carregando        by remember { mutableStateOf(true) }

    LaunchedEffect(currentUserId) {
        val uid = currentUserId ?: return@LaunchedEffect

        val perfil = getPerfilAndroid(uid)
        if (perfil != null) {
            nomeUsuario = perfil.nome
            iniciais = perfil.nome.split(" ").map { it[0] }.joinToString("").take(2).uppercase()
        }

        val meuPerfil = getMeuPerfilProfissional(uid)
        if (meuPerfil != null) {
            credibilidade     = meuPerfil.credibilidade
            disponivelUrgente = meuPerfil.disponivel_urgente
        }

        consultas  = buscarConsultasProfissional(uid)
        carregando = false
    }

    val abas = listOf(
        "visao"         to "Visão Geral",
        "atendimentos"  to "Atendimentos",
        "credibilidade" to "Credibilidade",
        "urgente"       to "Urgente",
        "financeiro"    to "Financeiro",
        "relatorios"    to "Relatórios",
        "perfil"        to "Meu Perfil",
    )

    Column(modifier = Modifier.fillMaxSize().background(SurfaceWarm)) {
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
                Box(
                    modifier = Modifier.size(36.dp).background(Azul, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(iniciais.ifEmpty { "?" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                val scopeSair = rememberCoroutineScope()
                TextButton(onClick = {
                    scopeSair.launch {
                        signOutAndroid()
                        onSair()
                    }
                }) {
                    Text("Sair", color = InkMuted, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        ScrollableTabRow(
            selectedTabIndex = abas.indexOfFirst { it.first == abaSelecionada },
            containerColor = Surface, contentColor = Verde, edgePadding = 16.dp,
        ) {
            abas.forEach { (id, label) ->
                Tab(
                    selected = abaSelecionada == id,
                    onClick  = { abaSelecionada = id },
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

        when (abaSelecionada) {
            "visao"         -> AbaVisaoGeralDash(
                nomeUsuario       = nomeUsuario,
                credibilidade     = credibilidade,
                consultas         = consultas,
                carregando        = carregando,
                disponivelUrgente = disponivelUrgente,
                onRelatorios      = onRelatorios,
            )
            "atendimentos"  -> AbaAtendimentosDash(consultas = consultas, carregando = carregando)
            "credibilidade" -> AbaCredibilidadeDash(credibilidade = credibilidade)
            "urgente"       -> AbaUrgenteDash(
                onEstudio         = onEstudio,
                disponivelUrgente = disponivelUrgente,
                consultas         = consultas,
            )
            "financeiro"    -> AbaFinanceiroDash()
            "relatorios"    -> {
                if (onRelatorios != null) {
                    LaunchedEffect(Unit) { onRelatorios() }
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Relatórios indisponíveis", color = InkMuted)
                    }
                }
            }
            "perfil"        -> {
                if (onPerfil != null) {
                    LaunchedEffect(Unit) { onPerfil() }
                } else {
                    AbaPerfilProfissional()
                }
            }
        }
    }
}

// ── ABA: VISÃO GERAL ──────────────────────────────────
@Composable
fun AbaVisaoGeralDash(
    nomeUsuario: String = "",
    credibilidade: Int = 0,
    consultas: List<ConsultaProfissional> = emptyList(),
    carregando: Boolean = false,
    disponivelUrgente: Boolean = false,
    onRelatorios: (() -> Unit)? = null,
) {
    val concluidas = consultas.filter { it.status == "concluida" || it.status == "concluido" }
    val agendadas  = consultas.filter { it.status == "agendada" || it.status == "agendado" }

    val ganhosMes = concluidas.sumOf { it.valor }

    val notasValidas = concluidas.map { it.avaliacao }.filter { it > 0 }
    val avaliacaoMedia = if (notasValidas.isNotEmpty())
        "%.1f".format(notasValidas.average())
    else "--"

    val primeiroNome = nomeUsuario.split(" ").firstOrNull() ?: "Profissional"

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
                Text("Olá, $primeiroNome! 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (carregando) "Carregando dados..."
                    else "Você tem ${agendadas.size} atendimento(s) agendado(s).",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Métricas reais
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaCard(modifier = Modifier.weight(1f), icone = "📋",
                numero = "${consultas.size}", label = "Atendimentos", cor = Verde)
            MetricaCard(modifier = Modifier.weight(1f), icone = "⭐",
                numero = avaliacaoMedia, label = "Avaliação", cor = Dourado)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaCard(modifier = Modifier.weight(1f), icone = "💰",
                numero = "R$ $ganhosMes", label = "Ganhos total", cor = Azul)
            MetricaCard(modifier = Modifier.weight(1f), icone = "🎯",
                numero = "$credibilidade", label = "Credibilidade", cor = Verde)
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
                if (carregando) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Verde, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (agendadas.isEmpty()) {
                    Text("Nenhum atendimento agendado", fontSize = 13.sp, color = InkMuted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    agendadas.take(5).forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).background(AzulClaro, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(c.data.split("/").getOrElse(0) { "--" }, fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold, color = Azul)
                                    Text(
                                        c.data.split("/").getOrElse(1) { "" }.let { mes ->
                                            listOf("","jan","fev","mar","abr","mai","jun",
                                                "jul","ago","set","out","nov","dez")
                                                .getOrElse(mes.toIntOrNull() ?: 0) { mes }
                                        },
                                        fontSize = 9.sp, color = Azul
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.nomeCliente, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("${c.hora} · ${c.tipo}", fontSize = 12.sp, color = InkMuted)
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (c.tipo.contains("rgente", ignoreCase = true)) UrgenteClaro else VerdeClaro,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (c.tipo.contains("rgente", ignoreCase = true)) Urgente else Verde)
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
                val comAvaliacao = concluidas.filter { it.avaliacao > 0 }.take(3)
                if (!carregando && comAvaliacao.isEmpty()) {
                    Text("Nenhuma avaliação ainda", fontSize = 13.sp, color = InkMuted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    comAvaliacao.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(AzulClaro, RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    c.nomeCliente.split(" ").map { it[0] }.joinToString("").take(2),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Azul
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.nomeCliente, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("${c.data} · ${c.tipo}", fontSize = 11.sp, color = InkMuted)
                            }
                            Text("★".repeat(c.avaliacao), fontSize = 13.sp, color = DouradoMedio)
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                }
            }
        }

        // Status urgente
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (disponivelUrgente) VerdeClaro else UrgenteClaro
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (disponivelUrgente) "🟢" else "🔴", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (disponivelUrgente) "Disponível para urgências" else "Indisponível para urgências",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (disponivelUrgente) Verde else Urgente
                    )
                    Text(
                        if (disponivelUrgente) "Você aparece na área urgente" else "Ative na aba Urgente",
                        fontSize = 11.sp, color = InkMuted
                    )
                }
            }
        }

        // Botão de Relatórios — Fase 4.4
        if (onRelatorios != null) {
            OutlinedButton(
                onClick  = onRelatorios,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.4f)),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Azul),
            ) {
                Text("📊  Relatórios", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MetricaCard(
    modifier: Modifier = Modifier,
    icone: String,
    numero: String,
    label: String,
    cor: Color,
) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.size(36.dp).background(cor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
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
fun AbaAtendimentosDash(
    consultas: List<ConsultaProfissional> = emptyList(),
    carregando: Boolean = false,
) {
    var filtro by remember { mutableStateOf("todos") }

    val lista = when (filtro) {
        "concluido" -> consultas.filter { it.status in listOf("concluida", "concluido") }
        "agendado"  -> consultas.filter { it.status in listOf("agendada", "agendado") }
        else        -> consultas
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("todos" to "Todos", "concluido" to "Concluídos", "agendado" to "Agendados").forEach { (id, label) ->
                FilterChip(
                    selected = filtro == id,
                    onClick  = { filtro = id },
                    label    = { Text(label, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Verde, selectedLabelColor = Color.White
                    )
                )
            }
        }

        if (carregando) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Verde)
            }
        } else {
            lista.forEach { c ->
                val isConcluido = c.status in listOf("concluida", "concluido")
                val isUrgente   = c.tipo.contains("rgente", ignoreCase = true)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape  = RoundedCornerShape(12.dp),
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
                                    modifier = Modifier.size(40.dp).background(AzulClaro, RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        c.nomeCliente.split(" ").map { it[0] }.joinToString("").take(2),
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Azul
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(c.nomeCliente, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                    Text("${c.data} às ${c.hora}", fontSize = 12.sp, color = InkMuted)
                                }
                            }
                            Text("R$ ${c.valor}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
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
                                        .background(if (isUrgente) UrgenteClaro else VerdeClaro, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (isUrgente) Urgente else Verde)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (isConcluido) VerdeClaro else AzulClaro, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (isConcluido) "Concluído" else "Agendado",
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (isConcluido) Verde else Azul
                                    )
                                }
                            }
                            if (c.avaliacao > 0) {
                                Text("★".repeat(c.avaliacao), fontSize = 13.sp, color = DouradoMedio)
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
}

// ── ABA: CREDIBILIDADE ────────────────────────────────
@Composable
fun AbaCredibilidadeDash(credibilidade: Int = 0) {
    val cred = credibilidade

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sua pontuação", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier.size(120.dp)
                        .background(if (cred >= 80) Color(0xFFFDF3D8) else VerdeClaro, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$cred", fontSize = 36.sp, fontWeight = FontWeight.Black,
                            color = if (cred >= 80) Dourado else Verde)
                        Text("pontos", fontSize = 12.sp, color = InkMuted)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    when {
                        cred >= 80 -> "🏆 Elegível ao PMP"
                        cred >= 50 -> "📈 Crescendo"
                        else       -> "🌱 Iniciando"
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkSoft
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Como é calculado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                listOf(Triple("Atendimentos", 30, Verde), Triple("Avaliações", 28, Azul), Triple("Pontualidade", 20, Dourado)).forEach { (label, pts, cor) ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 13.sp, color = Ink)
                            Text("$pts pts", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cor)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { pts / 40f }, modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = cor, trackColor = SurfaceOff
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F0)),
            border = androidx.compose.foundation.BorderStroke(1.dp, DouradoMedio.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Programa PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                Text("Faltam ${(100 - cred).coerceAtLeast(0)} pontos para o PMP",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                LinearProgressIndicator(
                    progress = { (cred / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = DouradoMedio, trackColor = SurfaceOff
                )
                Text("$cred/100", fontSize = 12.sp, color = InkMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                Button(
                    onClick = {}, modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = cred >= 80,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Azul, contentColor = Color.White,
                        disabledContainerColor = SurfaceOff, disabledContentColor = InkMuted
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (cred >= 80) "Candidatar-me ao PMP" else "Disponível com 80 pontos",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── ABA: URGENTE ──────────────────────────────────────
// Toggle de disponibilidade + histórico calculado a partir de consultas reais.
// A videochamada é acionada pelo AlertDialogUrgencia no DashboardProfissionalComRealtime
// (MainActivity) e navega direto para VideoCallScreen — não passa por esta aba.
@Composable
fun AbaUrgenteDash(
    onEstudio: (() -> Unit)? = null,
    disponivelUrgente: Boolean = false,
    consultas: List<ConsultaProfissional> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    var ativo       by remember(disponivelUrgente) { mutableStateOf(disponivelUrgente) }
    var atualizando by remember { mutableStateOf(false) }
    var erroToggle  by remember { mutableStateOf(false) }

    // Calcula urgentes reais a partir das consultas recebidas
    val urgentesRealizadas = consultas.count {
        it.tipo.contains("rgente", ignoreCase = true) &&
                (it.status == "concluida" || it.status == "concluido")
    }
    val totalUrgentes = consultas.count { it.tipo.contains("rgente", ignoreCase = true) }
    val pontualidade = if (totalUrgentes > 0)
        "${(urgentesRealizadas * 100 / totalUrgentes)}%"
    else "—"
    val descumprimentos = totalUrgentes - urgentesRealizadas

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle de disponibilidade
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Área Urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Apareça para clientes que precisam de atendimento imediato.",
                            fontSize = 13.sp, color = InkMuted, lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    if (atualizando) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Verde, strokeWidth = 2.dp)
                    } else {
                        Switch(
                            checked = ativo,
                            onCheckedChange = { novoValor ->
                                val anteriorValor = ativo
                                ativo = novoValor
                                atualizando = true
                                erroToggle = false
                                scope.launch {
                                    val uid = currentUserId
                                    val sucesso = if (uid != null) atualizarDisponibilidadeUrgente(uid, novoValor) else false
                                    atualizando = false
                                    if (!sucesso) {
                                        ativo = anteriorValor
                                        erroToggle = true
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Verde)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (ativo) VerdeClaro else UrgenteClaro, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (ativo) "🟢" else "🔴", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (ativo) "Disponível agora" else "Indisponível",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (ativo) Verde else Urgente)
                }
                if (erroToggle) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Falha na conexão. Estado revertido.", fontSize = 12.sp, color = Urgente)
                    }
                }
            }
        }

        // Histórico urgente — dados reais
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Histórico urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                if (totalUrgentes == 0) {
                    Text("Nenhuma urgência registrada ainda.",
                        fontSize = 13.sp, color = InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple("$urgentesRealizadas", "Urgentes\nrealizadas", Verde),
                            Triple(pontualidade,           "Pontua-\nlidade",      Azul),
                            Triple("$descumprimentos",    "Descum-\nprimentos",   if (descumprimentos == 0) Dourado else Urgente),
                        ).forEach { (num, label, cor) ->
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(num, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cor)
                                Text(label, fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (descumprimentos == 0) VerdeClaro else UrgenteClaro, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (descumprimentos == 0) "✓" else "⚠️", fontSize = 14.sp,
                            color = if (descumprimentos == 0) Verde else Urgente,
                            fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (descumprimentos == 0) "Histórico limpo — acesso integral à área urgente."
                            else "$descumprimentos descumprimento(s) registrado(s).",
                            fontSize = 12.sp,
                            color = if (descumprimentos == 0) Verde else Urgente
                        )
                    }
                }

                if (onEstudio != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onEstudio, modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC49A2A), contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🎨 Acessar meu Estúdio", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── ABA: PERFIL PROFISSIONAL (inline) ─────────────────
@Composable
fun AbaPerfilProfissional() {
    var nome by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var bio  by remember { mutableStateOf("") }

    LaunchedEffect(currentUserId) {
        val uid = currentUserId ?: return@LaunchedEffect
        val perfil = getPerfilAndroid(uid)
        if (perfil != null) nome = perfil.nome
        val meu = getMeuPerfilProfissional(uid)
        if (meu != null) {
            area = meu.area
            bio  = meu.descricao ?: ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Meu Perfil", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                listOf(
                    "Nome"  to nome,
                    "Área"  to area,
                    "Sobre" to bio.ifEmpty { "—" },
                ).forEach { (label, valor) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, fontSize = 13.sp, color = InkMuted)
                        Text(valor,  fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Para editar seu perfil completo, acesse a tela de Perfil.",
                    fontSize = 12.sp, color = InkMuted)
            }
        }
    }
}