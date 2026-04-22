package br.com.brasiltupi.conecta

import kotlinx.serialization.Serializable
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// ── DADOS ─────────────────────────────────────────────
@Serializable
data class ConsultaCliente(
    val id: String,
    val profissionalId: String = "",   // necessário para gravar avaliação
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

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun DashboardClienteScreen(
    onSair: () -> Unit,
    onEstudio: ((String) -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    onChat: ((String, String) -> Unit)? = null
){
    var abaSelecionada by remember { mutableStateOf("visao") }

    var consultas by remember { mutableStateOf<List<ConsultaCliente>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var nomeUsuario by remember { mutableStateOf("") }
    var iniciais by remember { mutableStateOf("") }

    // currentUserId é a variável global do SupabaseClient, populada no signIn
    val userId = remember { currentUserId }

    LaunchedEffect(userId) {
        if (userId == null) return@LaunchedEffect
        coroutineScope {
            val consultasDeferred = async { buscarConsultasCliente(userId) }
            val perfilDeferred    = async { getPerfilAndroid(userId) }
            consultas = consultasDeferred.await()
            val perfil = perfilDeferred.await()
            if (perfil != null) {
                nomeUsuario = perfil.nome
                iniciais    = perfil.nome.split(" ").map { it[0] }.joinToString("").take(2).uppercase()
            }
        }
        loading = false
    }

    val pendentes = consultas.count { it.status == "concluida" && !it.avaliada }
    val primeiroNome = nomeUsuario.split(" ").firstOrNull() ?: "Cliente"

    val abas = listOf(
        "visao"     to "Visão Geral",
        "consultas" to "Consultas",
        "busca"     to "Buscar",
        "perfil"    to "Meu Perfil",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
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
                Text(
                    "Conecta", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Verde,
                    modifier = Modifier.offset(y = (-4).dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            Text(
                                "$pendentes",
                                fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Verde, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        iniciais.ifEmpty { "?" },
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
                val scope = rememberCoroutineScope()
                TextButton(onClick = {
                    scope.launch {
                        signOutAndroid()
                        onSair()
                    }
                }) {
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
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Verde)
            }
        } else {
            when (abaSelecionada) {
                "visao"     -> AbaVisaoGeralCliente(
                    consultas = consultas,
                    onNavegar = { abaSelecionada = it },
                    primeiroNome = primeiroNome,
                    onConsultaAvaliada = { id, n ->
                        consultas = consultas.map { c ->
                            if (c.id == id) c.copy(avaliada = true, avaliacao = n) else c
                        }
                    },
                )
                "consultas" -> AbaConsultasCliente(
                    consultas = consultas,
                    onConsultaAvaliada = { id, n ->
                        consultas = consultas.map { c ->
                            if (c.id == id) c.copy(avaliada = true, avaliacao = n) else c
                        }
                    },
                    onChat = onChat
                )
                "busca"     -> AbaBuscaCliente(onEstudio = onEstudio)
                "perfil"    -> {
                    if (onPerfil != null) {
                        LaunchedEffect(Unit) { onPerfil() }
                    }
                }
            }
        }
    }
}

// ── ABA: VISÃO GERAL ──────────────────────────────────
@Composable
fun AbaVisaoGeralCliente(
    consultas: List<ConsultaCliente>,
    onNavegar: (String) -> Unit,
    primeiroNome: String = "Cliente",
    onConsultaAvaliada: (consultaId: String, nota: Int) -> Unit = { _, _ -> },
) {
    val pendentes = consultas.filter { it.status == "concluida" && !it.avaliada }
    val proximas  = consultas.filter { it.status == "agendada" }
    var avaliarConsulta by remember { mutableStateOf<ConsultaCliente?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                Text(
                    "Olá, $primeiroNome! 👋",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
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
                numero = "${consultas.count { it.status == "concluida" }}",
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
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Precisa de atendimento agora?",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                    Text(
                        "Resposta em até 45 minutos",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)
                    )
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
                        Text(
                            "Avaliações pendentes",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Urgente, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${pendentes.size}",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    pendentes.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Urgente,
                                    contentColor = Color.White
                                ),
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
                    Text(
                        "Próximas consultas",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink
                    )
                    TextButton(onClick = { onNavegar("consultas") }) {
                        Text("Ver todas →", color = Verde, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (proximas.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nenhuma consulta agendada", fontSize = 13.sp, color = InkMuted)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onNavegar("busca") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Verde,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ Agendar consulta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    proximas.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(VerdeClaro, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        c.data.split("/")[0],
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Verde
                                    )
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
                                Text(
                                    c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (c.tipo == "Urgente") Urgente else Verde
                                )
                            }
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                }
            }
        }
    }

    if (avaliarConsulta != null) {
        ModalAvaliacao(
            consulta    = avaliarConsulta!!,
            onFechar    = { avaliarConsulta = null },
            onAvaliada  = { id, n -> onConsultaAvaliada(id, n); avaliarConsulta = null },
        )
    }
}

// ── MODAL AVALIAÇÃO ───────────────────────────────────
@Composable
fun ModalAvaliacao(
    consulta: ConsultaCliente,
    onFechar: () -> Unit,
    // callback chamado após gravação bem-sucedida para atualizar a lista local
    onAvaliada: ((consultaId: String, nota: Int) -> Unit)? = null,
) {
    var nota by remember { mutableStateOf(0) }
    var enviando by remember { mutableStateOf(false) }
    var enviado by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!enviando) onFechar() },
        containerColor = Surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                when {
                    enviado  -> "Avaliação enviada!"
                    erro     -> "Erro ao enviar"
                    else     -> "Avaliar atendimento"
                },
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink
            )
        },
        text = {
            when {
                enviado -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                }
                erro -> {
                    Text(
                        "Não foi possível enviar sua avaliação. Tente novamente.",
                        fontSize = 13.sp, color = Urgente, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
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
                                TextButton(onClick = { if (!enviando) nota = i }) {
                                    Text(
                                        "★", fontSize = 32.sp,
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        enviado -> onFechar()
                        erro    -> { erro = false }
                        nota > 0 && !enviando -> {
                            enviando = true
                            scope.launch {
                                val okAvaliacoes = gravarAvaliacaoAndroid(
                                    consultaId     = consulta.id,
                                    clienteId      = currentUserId ?: "",
                                    profissionalId = consulta.profissionalId,
                                    nota           = nota,
                                )
                                val okConsulta = if (okAvaliacoes) {
                                    atualizarAvaliacaoConsulta(consulta.id, nota)
                                } else false

                                enviando = false
                                if (okAvaliacoes) {          // sucesso mesmo se o patch secundário falhar
                                    enviado = true
                                    onAvaliada?.invoke(consulta.id, nota)
                                } else {
                                    erro = true
                                }
                            }
                        }
                    }
                },
                enabled = enviado || erro || (nota > 0 && !enviando),
                colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (enviando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        when {
                            enviado -> "Fechar"
                            erro    -> "Tentar novamente"
                            else    -> "Enviar avaliação"
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            if (!enviado && !enviando) {
                TextButton(onClick = onFechar) {
                    Text("Cancelar", color = InkMuted, fontSize = 13.sp)
                }
            }
        }
    )
}

// ── ABA: CONSULTAS ────────────────────────────────────
@Composable
fun AbaConsultasCliente(
    consultas: List<ConsultaCliente>,
    onConsultaAvaliada: (consultaId: String, nota: Int) -> Unit = { _, _ -> },
    onChat: ((String, String) -> Unit)? = null // <--- Adicione esta linha
) {
    var filtro by remember { mutableStateOf("todas") }
    var avaliarConsulta by remember { mutableStateOf<ConsultaCliente?>(null) }
    val scope = rememberCoroutineScope()
    var verificandoChat by remember { mutableStateOf(false) }
    var mostrarBloqueioChat by remember { mutableStateOf(false) }
    var consultaParaChat by remember { mutableStateOf<ConsultaCliente?>(null) }
    val lista = when (filtro) {
        "concluida" -> consultas.filter { it.status == "concluida" }
        "agendada"  -> consultas.filter { it.status == "agendada" }
        else        -> consultas
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "todas"     to "Todas",
                "concluida" to "Concluídas",
                "agendada"  to "Agendadas"
            ).forEach { (id, label) ->
                FilterChip(
                    selected = filtro == id,
                    onClick = { filtro = id },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Verde,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

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
                                Text(
                                    c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (c.tipo == "Urgente") Urgente else Verde
                                )
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
                        // Dentro do Row de ações, após o bloco de avaliação:
                        if (c.status == "concluida" && onChat != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    consultaParaChat = c
                                    verificandoChat  = true
                                    scope.launch {
                                        val temAcesso = verificarAcessoChat(
                                            clienteId      = currentUserId ?: "",
                                            profissionalId = c.profissionalId,
                                        )
                                        verificandoChat = false
                                        if (temAcesso) {
                                            onChat(c.profissionalId, c.profissional)
                                        } else {
                                            mostrarBloqueioChat = true
                                        }
                                    }
                                },
                                enabled  = !verificandoChat,
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(8.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Azul),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.4f))
                            ) {
                                if (verificandoChat && consultaParaChat?.id == c.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Azul, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("💬 Conversar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (c.status == "concluida") {
                            if (c.avaliada) {
                                Text("★".repeat(c.avaliacao), fontSize = 13.sp, color = DouradoMedio)
                            } else {
                                Button(
                                    onClick = { avaliarConsulta = c },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Urgente,
                                        contentColor = Color.White
                                    ),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhuma consulta encontrada",
                    fontSize = 14.sp, color = InkMuted, textAlign = TextAlign.Center
                )
            }
        }
    }
    // ── Modal de bloqueio de chat ─────────────────────
    if (mostrarBloqueioChat) {
        AlertDialog(
            onDismissRequest = { mostrarBloqueioChat = false },
            containerColor   = Surface,
            shape            = RoundedCornerShape(16.dp),
            title = {
                Text("Acesso ao chat", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(UrgenteClaro, RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔒", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "O chat requer um plano ativo ou liberação avulsa.",
                            fontSize = 13.sp, color = Urgente, lineHeight = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Escolha uma das opções para desbloquear:", fontSize = 13.sp, color = InkMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    // Opção 1: Plano
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = CardDefaults.cardColors(containerColor = AzulClaro),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("⭐ Assinar Plano", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Azul)
                            Text("Chat ilimitado com todos os profissionais.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Opção 2: Avulso
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = CardDefaults.cardColors(containerColor = VerdeClaro),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Verde.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("💳 Liberação avulsa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                            Text("Pague uma taxa única para conversar com este profissional.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { mostrarBloqueioChat = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Azul, contentColor = Color.White),
                    shape   = RoundedCornerShape(8.dp)
                ) {
                    Text("Ver opções", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarBloqueioChat = false }) {
                    Text("Agora não", color = InkMuted, fontSize = 13.sp)
                }
            }
        )
    }

    if (avaliarConsulta != null) {
        ModalAvaliacao(
            consulta    = avaliarConsulta!!,
            onFechar    = { avaliarConsulta = null },
            onAvaliada  = { id, n -> onConsultaAvaliada(id, n); avaliarConsulta = null },
        )
    }
}

// ── ABA: BUSCA ────────────────────────────────────────
@Composable
fun AbaBuscaCliente(onEstudio: ((String) -> Unit)? = null) {
    var profSelecionado by remember { mutableStateOf<ProfissionalPMP?>(null) }
    var agendando       by remember { mutableStateOf<Pair<ProfissionalPMP, String>?>(null) }
    var profissionais   by remember { mutableStateOf<List<ProfissionalComPerfil>>(emptyList()) }
    var loadingProfs    by remember { mutableStateOf(true) }
    var urgentesCount   by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        profissionais = getProfissionaisPMPAndroid(false, "")
        urgentesCount = profissionais.count { it.disponivel_urgente }
        loadingProfs  = false
    }

    if (profSelecionado != null) {
        PerfilPublicoScreen(
            prof = profSelecionado!!,
            onVoltar = { profSelecionado = null },
            onAgendar = { tipo ->
                val prof = profSelecionado
                if (prof != null) agendando = prof to tipo
            }
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
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A0808))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Atendimento urgente",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                    Text(
                        "$urgentesCount disponíveis agora · 45min",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Text("Profissionais PMP", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)

        if (loadingProfs) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Verde)
            }
        } else {
            profissionais.forEach { prof ->
                CardProfissionalReal(
                    prof      = prof,
                    onClick   = {
                        profSelecionado = prof.toProfissionalPMP()

                    },
                    onEstudio = onEstudio
                )
            }
        }
    }
}

@Composable
private fun CardProfissionalReal(
    prof:      ProfissionalComPerfil,
    onClick:   () -> Unit,
    onEstudio: ((String) -> Unit)? = null,
) {
    val nome      = prof.perfis?.nome ?: "Profissional"
    val cidade    = prof.perfis?.cidade ?: ""
    val iniciais  = nome.split(" ").map { it[0] }.joinToString("").take(2).uppercase()

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(AzulClaro, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(iniciais, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Azul)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(prof.area, fontSize = 12.sp, color = InkMuted)
                    if (cidade.isNotEmpty()) Text("📍 $cidade", fontSize = 11.sp, color = InkMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("R$ ${prof.valor_normal}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                    if (prof.disponivel_urgente) {
                        Box(modifier = Modifier.background(UrgenteClaro, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("⚡ Urgente", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Urgente)
                        }
                    }
                }
            }
            if (onEstudio != null) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = { onEstudio(prof.id) },
                    colors  = ButtonDefaults.textButtonColors(contentColor = Azul)
                ) {
                    Text("Ver Estúdio →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}