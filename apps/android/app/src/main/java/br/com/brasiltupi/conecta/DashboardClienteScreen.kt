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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues

// ── DADOS ─────────────────────────────────────────────
@Serializable
data class ConsultaCliente(
    val id:             String,
    val profissionalId: String = "",
    val profissional:   String,
    val area:           String,
    val data:           String,
    val hora:           String,
    val tipo:           String,
    val status:         String,
    val avaliada:       Boolean,
    val avaliacao:      Int,
    val valor:          String,
)

// ── TELA PRINCIPAL ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardClienteScreen(
    onSair:        () -> Unit,
    onEstudio:     ((String) -> Unit)?         = null,
    onPerfil:      (() -> Unit)?               = null,
    onAgendar:     ((String, String) -> Unit)? = null,
    onChat:        ((String, String) -> Unit)? = null,
    onSuporte:     ((String?) -> Unit)?        = null,
    onBiblioteca:  (() -> Unit)?               = null,
    // ── Fase 4.2: Sistema de Indicações ──────────────
    onReferral:    (() -> Unit)?               = null,
    // ── Fase 4.3: Disputas formais ───────────────────
    onDisputa:     ((String) -> Unit)?         = null,
    // ── Fase 3.4: Busca Avançada do Estúdio ──────────
    onBuscaEstudio: (() -> Unit)?              = null,
) {
    var abaSelecionada by remember { mutableStateOf("visao") }
    var menuExpandido  by remember { mutableStateOf(false) }
    var consultas      by remember { mutableStateOf<List<ConsultaCliente>>(emptyList()) }
    var loading        by remember { mutableStateOf(true) }
    var nomeUsuario    by remember { mutableStateOf("") }
    var iniciais       by remember { mutableStateOf("") }

    // ── Fase 3.3: último curso em andamento (preview na visão geral) ──
    var ultimoCursoNome     by remember { mutableStateOf("") }
    var ultimoCursoProgresso by remember { mutableStateOf(0f) }

    val userId = remember { currentUserId }

    LaunchedEffect(userId) {
        if (userId == null) return@LaunchedEffect
        val consultasDeferred = async { buscarConsultasCliente(userId) }
        val perfilDeferred    = async { getPerfilAndroid(userId) }
        val bibliotecaDeferred = async { buscarUltimoCursoEmAndamento(userId) }
        consultas = consultasDeferred.await()
        val perfil = perfilDeferred.await()
        if (perfil != null) {
            nomeUsuario = perfil.nome
            iniciais    = perfil.nome.split(" ").map { it[0] }.joinToString("").take(2).uppercase()
        }
        val ultimoCurso = bibliotecaDeferred.await()
        if (ultimoCurso != null) {
            ultimoCursoNome      = ultimoCurso.titulo
            ultimoCursoProgresso = ultimoCurso.progresso
        }
        loading = false
    }

    val pendentes    = consultas.count { it.status == "concluida" && !it.avaliada }
    val primeiroNome = nomeUsuario.split(" ").firstOrNull() ?: "Cliente"

    // ── Abas primárias ───────────────────────────────
    val abasPrimarias = listOf(
        Triple("visao",      "Início",    "🏠"),
        Triple("consultas",  "Consultas",  "📋"),
        Triple("busca",      "Buscar",     "🔍"),
        Triple("biblioteca", "Biblioteca", "📚"),
    )

    // ── Menu ⋮ ─ todas as funcionalidades expostas ───
    val menuItens = listOf(
        Triple("perfil",     "Meu Perfil",  "👤"),
        Triple("indicacoes", "Indicações",  "🎁"),
        Triple("suporte",    "Suporte",     "🛡️"),
        Triple("sair",       "Sair",        "🚪"),
    )

    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = SurfaceWarm,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier.size(34.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(iniciais.ifEmpty { "?" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                nomeUsuario.ifEmpty { "Brasil Tupi" },
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            )
                            Text("Painel do Cliente", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                },
                actions = {
                    if (pendentes > 0) {
                        Box {
                            IconButton(onClick = { abaSelecionada = "consultas" }) {
                                Text("🔔", fontSize = 20.sp)
                            }
                            Box(
                                modifier         = Modifier
                                    .size(16.dp)
                                    .background(Urgente, RoundedCornerShape(50))
                                    .align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$pendentes", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpandido = true }) {
                            Text("⋮", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded         = menuExpandido,
                            onDismissRequest = { menuExpandido = false },
                        ) {
                            menuItens.forEach { (id, label, icon) ->
                                if (id == "sair") HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = SurfaceOff)
                                DropdownMenuItem(
                                    leadingIcon = { Text(icon, fontSize = 16.sp) },
                                    text = {
                                        Text(
                                            label,
                                            fontSize   = 14.sp,
                                            color      = if (id == "sair") Urgente else Ink,
                                            fontWeight = if (id == "sair") FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        menuExpandido = false
                                        when (id) {
                                            "perfil"     -> onPerfil?.invoke()
                                            // Fase 4.2: Indicações
                                            "indicacoes" -> onReferral?.invoke()
                                            "suporte"    -> onSuporte?.invoke(null)
                                            "sair"       -> scope.launch { signOutAndroid(); onSair() }
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Verde),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface, tonalElevation = 4.dp) {
                abasPrimarias.forEach { (id, label, icon) ->
                    NavigationBarItem(
                        selected = abaSelecionada == id,
                        onClick  = {
                            when (id) {
                                "biblioteca" -> onBiblioteca?.invoke()
                                else         -> abaSelecionada = id
                            }
                        },
                        icon  = { Text(icon, fontSize = if (abaSelecionada == id) 22.sp else 20.sp) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = Verde,
                            selectedTextColor   = Verde,
                            unselectedIconColor = InkMuted,
                            unselectedTextColor = InkMuted,
                            indicatorColor      = Verde.copy(alpha = 0.10f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (loading) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Verde)
                }
            } else {
                when (abaSelecionada) {
                    "visao" -> AbaVisaoGeralCliente(
                        consultas            = consultas,
                        onNavegar            = { abaSelecionada = it },
                        primeiroNome         = primeiroNome,
                        onConsultaAvaliada   = { id, n ->
                            consultas = consultas.map { c ->
                                if (c.id == id) c.copy(avaliada = true, avaliacao = n) else c
                            }
                        },
                        // Fase 3.3: preview do último curso
                        ultimoCursoNome      = ultimoCursoNome,
                        ultimoCursoProgresso = ultimoCursoProgresso,
                        onBiblioteca         = onBiblioteca,
                        // Fase 4.2: atalho para indicações
                        onReferral           = onReferral,
                    )
                    "consultas" -> AbaConsultasCliente(
                        consultas          = consultas,
                        onConsultaAvaliada = { id, n ->
                            consultas = consultas.map { c ->
                                if (c.id == id) c.copy(avaliada = true, avaliacao = n) else c
                            }
                        },
                        onChat    = onChat,
                        onSuporte = onSuporte,
                        // Fase 4.3: disputa formal separada do suporte
                        onDisputa = onDisputa,
                    )
                    "busca" -> AbaBuscaCliente(
                        onEstudio        = onEstudio,
                        onAgendarRegular = onAgendar,
                        // Fase 3.4: busca avançada do Estúdio
                        onBuscaEstudio   = onBuscaEstudio,
                    )
                    "perfil" -> {
                        if (onPerfil != null) {
                            LaunchedEffect(Unit) { onPerfil() }
                        }
                    }
                }
            }
        }
    }
}

// ── ABA: VISÃO GERAL ──────────────────────────────────
@Composable
fun AbaVisaoGeralCliente(
    consultas:            List<ConsultaCliente>,
    onNavegar:            (String) -> Unit,
    primeiroNome:         String = "Cliente",
    onConsultaAvaliada:   (consultaId: String, nota: Int) -> Unit = { _, _ -> },
    // Fase 3.3
    ultimoCursoNome:      String  = "",
    ultimoCursoProgresso: Float   = 0f,
    onBiblioteca:         (() -> Unit)? = null,
    // Fase 4.2
    onReferral:           (() -> Unit)? = null,
) {
    val pendentes = consultas.filter { it.status == "concluida" && !it.avaliada }
    val proximas  = consultas.filter { it.status == "agendada" }
    var avaliarConsulta by remember { mutableStateOf<ConsultaCliente?>(null) }

    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card de boas-vindas
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Verde),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Olá, $primeiroNome! 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Você tem ${proximas.size} consulta(s) agendada(s).",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Métricas
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaCard(
                modifier = Modifier.weight(1f),
                icone    = "📋",
                numero   = "${consultas.count { it.status == "concluida" }}",
                label    = "Realizadas",
                cor      = Azul,
            )
            MetricaCard(
                modifier = Modifier.weight(1f),
                icone    = "📅",
                numero   = "${proximas.size}",
                label    = "Agendadas",
                cor      = Verde,
            )
        }

        // Card de urgência
        Card(
            onClick  = { onNavegar("busca") },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A0808)),
        ) {
            Row(
                modifier          = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Ver →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }

        // ── Fase 3.3: Preview do último curso em andamento ───────────────
        if (ultimoCursoNome.isNotEmpty()) {
            Card(
                onClick  = { onBiblioteca?.invoke() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.2f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📚", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Continuar aprendendo", fontSize = 11.sp, color = InkMuted)
                                Text(ultimoCursoNome, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            }
                        }
                        Text(
                            "${(ultimoCursoProgresso * 100).toInt()}%",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Azul,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress  = { ultimoCursoProgresso },
                        modifier  = Modifier.fillMaxWidth().height(6.dp),
                        color     = Azul,
                        trackColor = SurfaceOff,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Toque para continuar →", fontSize = 11.sp, color = Azul)
                }
            }
        }

        // ── Fase 4.2: Card de Indicações ─────────────────────────────────
        if (onReferral != null) {
            Card(
                onClick  = onReferral,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFFF0FBF4)),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Verde.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎁", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Indique e ganhe créditos", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                        Text("Compartilhe seu código e acumule benefícios.", fontSize = 12.sp, color = InkMuted)
                    }
                    Text("→", fontSize = 16.sp, color = Verde, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Avaliações pendentes
        if (pendentes.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Urgente.copy(alpha = 0.2f)),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Avaliações pendentes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Box(
                            modifier         = Modifier.size(22.dp).background(Urgente, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${pendentes.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    pendentes.forEach { c ->
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier         = Modifier.size(36.dp).background(UrgenteClaro, RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    c.profissional.split(" ").map { it[0] }.joinToString("").take(2),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Urgente,
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.profissional, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("${c.data} · ${c.tipo}", fontSize = 11.sp, color = InkMuted)
                            }
                            Button(
                                onClick        = { avaliarConsulta = c },
                                colors         = ButtonDefaults.buttonColors(containerColor = Urgente, contentColor = Color.White),
                                shape          = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Próximas consultas", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    TextButton(onClick = { onNavegar("consultas") }) {
                        Text("Ver todas →", color = Verde, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (proximas.isEmpty()) {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Nenhuma consulta agendada", fontSize = 13.sp, color = InkMuted)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onNavegar("busca") },
                            colors  = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                            shape   = RoundedCornerShape(8.dp),
                        ) {
                            Text("+ Agendar consulta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    proximas.forEach { c ->
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isHojeLocal = run {
                                try {
                                    val p   = c.data.split("/")
                                    val cal = java.util.Calendar.getInstance()
                                    p[0].toInt() == cal.get(java.util.Calendar.DAY_OF_MONTH) &&
                                            p[1].toInt() == cal.get(java.util.Calendar.MONTH) + 1
                                } catch (e: Exception) { false }
                            }
                            val corBox  = if (isHojeLocal) UrgenteClaro else VerdeClaro
                            val corNum  = if (isHojeLocal) Urgente else Verde
                            val mesAbbr = listOf("","jan","fev","mar","abr","mai","jun","jul","ago","set","out","nov","dez")
                                .getOrElse(try { c.data.split("/").getOrElse(1){"0"}.toInt() } catch (e: Exception){ 0 }) { "" }
                            Box(
                                modifier         = Modifier.size(44.dp).background(corBox, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(c.data.split("/").getOrElse(0){"--"}, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = corNum)
                                    Text(mesAbbr, fontSize = 9.sp, color = corNum)
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
                                        RoundedCornerShape(20.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (c.tipo == "Urgente") Urgente else Verde,
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
            consulta   = avaliarConsulta!!,
            onFechar   = { avaliarConsulta = null },
            onAvaliada = { id, n -> onConsultaAvaliada(id, n); avaliarConsulta = null },
        )
    }
}

// ── MODAL AVALIAÇÃO ───────────────────────────────────
@Composable
fun ModalAvaliacao(
    consulta:   ConsultaCliente,
    onFechar:   () -> Unit,
    onAvaliada: ((consultaId: String, nota: Int) -> Unit)? = null,
) {
    var nota     by remember { mutableIntStateOf(0) }
    var enviando by remember { mutableStateOf(false) }
    var enviado  by remember { mutableStateOf(false) }
    var erro     by remember { mutableStateOf(false) }
    val scope    = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!enviando) onFechar() },
        containerColor   = Surface,
        shape            = RoundedCornerShape(16.dp),
        title = {
            Text(
                when { enviado -> "Avaliação enviada!"; erro -> "Erro ao enviar"; else -> "Avaliar atendimento" },
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink,
            )
        },
        text = {
            when {
                enviado -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier         = Modifier.size(56.dp).background(Verde, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✓", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Obrigada por contribuir com a credibilidade da plataforma.", fontSize = 13.sp, color = InkMuted, textAlign = TextAlign.Center)
                    }
                }
                erro -> {
                    Text("Não foi possível enviar sua avaliação. Tente novamente.", fontSize = 13.sp, color = Urgente, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    Column {
                        Text("Como foi sua consulta com ${consulta.profissional}?", fontSize = 13.sp, color = InkMuted)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            (1..5).forEach { i ->
                                TextButton(onClick = { if (!enviando) nota = i }) {
                                    Text("★", fontSize = 32.sp, color = if (i <= nota) DouradoMedio else Color(0xFFE5E7EB))
                                }
                            }
                        }
                        if (nota > 0) {
                            Text(
                                listOf("", "Muito ruim", "Ruim", "Regular", "Bom", "Excelente")[nota],
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
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
                                if (okAvaliacoes) atualizarAvaliacaoConsulta(consulta.id, nota)
                                enviando = false
                                if (okAvaliacoes) { enviado = true; onAvaliada?.invoke(consulta.id, nota) }
                                else erro = true
                            }
                        }
                    }
                },
                enabled = enviado || erro || (nota > 0 && !enviando),
                colors  = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                shape   = RoundedCornerShape(8.dp),
            ) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(when { enviado -> "Fechar"; erro -> "Tentar novamente"; else -> "Enviar avaliação" }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!enviado && !enviando) {
                TextButton(onClick = onFechar) { Text("Cancelar", color = InkMuted, fontSize = 13.sp) }
            }
        },
    )
}

// ── ABA: CONSULTAS ────────────────────────────────────
@Composable
fun AbaConsultasCliente(
    consultas:          List<ConsultaCliente>,
    onConsultaAvaliada: (consultaId: String, nota: Int) -> Unit = { _, _ -> },
    onChat:             ((String, String) -> Unit)? = null,
    onSuporte:          ((String?) -> Unit)? = null,
    // Fase 4.3: disputa formal, separada do suporte genérico
    onDisputa:          ((String) -> Unit)? = null,
) {
    var filtro              by remember { mutableStateOf("todas") }
    var avaliarConsulta     by remember { mutableStateOf<ConsultaCliente?>(null) }
    val scope               = rememberCoroutineScope()
    var verificandoChat     by remember { mutableStateOf(false) }
    var mostrarBloqueioChat by remember { mutableStateOf(false) }
    var consultaParaChat    by remember { mutableStateOf<ConsultaCliente?>(null) }

    val lista = when (filtro) {
        "concluida" -> consultas.filter { it.status == "concluida" }
        "agendada"  -> consultas.filter { it.status == "agendada" }
        else        -> consultas
    }

    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("todas" to "Todas", "concluida" to "Concluídas", "agendada" to "Agendadas").forEach { (id, label) ->
                FilterChip(
                    selected = filtro == id,
                    onClick  = { filtro = id },
                    label    = { Text(label, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = Verde, selectedLabelColor = Color.White),
                )
            }
        }

        lista.forEach { c ->
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier.size(44.dp).background(AzulClaro, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(c.profissional.split(" ").map { it[0] }.joinToString("").take(2), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Azul)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.profissional, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(c.area, fontSize = 12.sp, color = InkMuted)
                            Text("${c.data} às ${c.hora} · ${c.valor}", fontSize = 11.sp, color = InkMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .background(if (c.tipo == "Urgente") UrgenteClaro else VerdeClaro, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (c.tipo == "Urgente") Urgente else Verde)
                        }
                        Box(
                            modifier = Modifier
                                .background(if (c.status == "concluida") VerdeClaro else AzulClaro, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                if (c.status == "concluida") "Concluída" else "Agendada",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color    = if (c.status == "concluida") Verde else Azul,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Botão chat
                        if (c.status == "concluida" && onChat != null) {
                            OutlinedButton(
                                onClick = {
                                    consultaParaChat = c
                                    verificandoChat  = true
                                    scope.launch {
                                        val temAcesso = verificarAcessoChat(currentUserId ?: "", c.profissionalId)
                                        verificandoChat = false
                                        if (temAcesso) onChat(c.profissionalId, c.profissional)
                                        else mostrarBloqueioChat = true
                                    }
                                },
                                enabled  = !verificandoChat,
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(8.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Azul),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.4f)),
                            ) {
                                if (verificandoChat && consultaParaChat?.id == c.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Azul, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("💬 Conversar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Botão avaliação
                        if (c.status == "concluida") {
                            if (c.avaliada) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    Text("★".repeat(c.avaliacao), fontSize = 16.sp, color = DouradoMedio)
                                }
                            } else {
                                Button(
                                    onClick  = { avaliarConsulta = c },
                                    colors   = ButtonDefaults.buttonColors(containerColor = Urgente, contentColor = Color.White),
                                    shape    = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Avaliar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // ── Fase 4.3: Botão de disputa formal (consultas concluídas) ──
                        // Separado do suporte genérico — abre DisputaViewModel.abrirDisputa()
                        if (c.status == "concluida" && onDisputa != null) {
                            OutlinedButton(
                                onClick  = { onDisputa(c.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(8.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Urgente),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, Urgente.copy(alpha = 0.4f)),
                            ) {
                                Text("⚖️ Abrir disputa formal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Botão Suporte genérico (não-formal) — mantido separado
                        if (onSuporte != null) {
                            TextButton(
                                onClick  = { onSuporte(c.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.textButtonColors(contentColor = InkMuted),
                            ) {
                                Text("⚠️ Reportar problema ao suporte", fontSize = 11.sp)
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

    // Modal bloqueio de chat
    if (mostrarBloqueioChat) {
        AlertDialog(
            onDismissRequest = { mostrarBloqueioChat = false },
            containerColor   = Surface,
            shape            = RoundedCornerShape(16.dp),
            title = { Text("Acesso ao chat", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Column {
                    Row(
                        modifier          = Modifier.fillMaxWidth().background(UrgenteClaro, RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔒", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("O chat requer um plano ativo ou liberação avulsa.", fontSize = 13.sp, color = Urgente, lineHeight = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Escolha uma das opções para desbloquear:", fontSize = 13.sp, color = InkMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors   = CardDefaults.cardColors(containerColor = AzulClaro),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.2f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("⭐ Assinar Plano", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Azul)
                            Text("Chat ilimitado com todos os profissionais.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors   = CardDefaults.cardColors(containerColor = VerdeClaro),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Verde.copy(alpha = 0.2f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("💳 Liberação avulsa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                            Text("Pague uma taxa única para conversar com este profissional.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mostrarBloqueioChat = false }, colors = ButtonDefaults.buttonColors(containerColor = Azul, contentColor = Color.White), shape = RoundedCornerShape(8.dp)) {
                    Text("Ver opções", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarBloqueioChat = false }) { Text("Agora não", color = InkMuted, fontSize = 13.sp) }
            },
        )
    }

    if (avaliarConsulta != null) {
        ModalAvaliacao(
            consulta   = avaliarConsulta!!,
            onFechar   = { avaliarConsulta = null },
            onAvaliada = { id, n -> onConsultaAvaliada(id, n); avaliarConsulta = null },
        )
    }
}

// ── ABA: BUSCA ─────────────────────────────────────────
@Composable
fun AbaBuscaCliente(
    onEstudio:        ((String) -> Unit)?         = null,
    onAgendarRegular: ((String, String) -> Unit)? = null,
    // Fase 3.4: SearchScreen com debounce, filtros e ranking_score
    onBuscaEstudio:   (() -> Unit)?               = null,
) {
    var profSelecionado by remember { mutableStateOf<ProfissionalPMP?>(null) }
    var agendando       by remember { mutableStateOf<Pair<ProfissionalPMP, String>?>(null) }

    if (profSelecionado != null) {
        PerfilPublicoScreen(
            prof      = profSelecionado!!,
            onVoltar  = { profSelecionado = null },
            onAgendar = { tipo -> val prof = profSelecionado; if (prof != null) agendando = prof to tipo },
        )
        return
    }
    if (agendando != null) {
        AgendarScreen(
            prof        = agendando!!.first,
            tipo        = agendando!!.second,
            onVoltar    = { agendando = null },
            onConcluido = { agendando = null; profSelecionado = null },
        )
        return
    }

    var abaAtiva by remember { mutableStateOf(0) }  // 0=Urgente 1=PMP 2=Geral

    var listaUrgente  by remember { mutableStateOf<List<ProfissionalComPerfil>>(emptyList()) }
    var loadUrgente   by remember { mutableStateOf(true) }
    var filtroUrgente by remember { mutableStateOf("") }

    var listaPMP  by remember { mutableStateOf<List<ProfissionalComPerfil>>(emptyList()) }
    var loadPMP   by remember { mutableStateOf(true) }
    var filtroPMP by remember { mutableStateOf("") }

    var listaGeral        by remember { mutableStateOf<List<ProfissionalComPerfil>>(emptyList()) }
    var listaGeralBase    by remember { mutableStateOf<List<ProfissionalComPerfil>>(emptyList()) }
    var loadGeral         by remember { mutableStateOf(true) }
    var filtroGeralArea   by remember { mutableStateOf("") }
    var filtroSomentePMP  by remember { mutableStateOf(false) }
    var filtroSomenteUrg  by remember { mutableStateOf(false) }
    var filtroSomenteVer  by remember { mutableStateOf(false) }

    LaunchedEffect(abaAtiva) {
        when (abaAtiva) {
            0 -> if (loadUrgente) {
                listaUrgente = getProfissionaisPMPAndroid(somenteUrgente = true, busca = "", aplicarFiltroPMP = false)
                loadUrgente = false
            }
            1 -> if (loadPMP) {
                listaPMP = getProfissionaisPMPAndroid(somenteUrgente = false, busca = "", aplicarFiltroPMP = true)
                loadPMP = false
            }
            2 -> if (loadGeral) {
                listaGeralBase = getProfissionaisPMPAndroid(somenteUrgente = false, busca = "", aplicarFiltroPMP = false)
                listaGeral = listaGeralBase
                loadGeral  = false
            }
        }
    }

    LaunchedEffect(filtroGeralArea, filtroSomentePMP, filtroSomenteUrg, filtroSomenteVer) {
        listaGeral = listaGeralBase.filter { p ->
            val matchArea = filtroGeralArea.isBlank() ||
                    p.area.contains(filtroGeralArea, ignoreCase = true) ||
                    (p.perfis?.nome ?: "").contains(filtroGeralArea, ignoreCase = true)
            val matchPMP = !filtroSomentePMP || p.is_pmp
            val matchUrg = !filtroSomenteUrg || p.disponivel_urgente
            val matchVer = !filtroSomenteVer || p.verificado
            matchArea && matchPMP && matchUrg && matchVer
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Fase 3.4: Banner de acesso à Busca Avançada do Estúdio ───────
        if (onBuscaEstudio != null) {
            Card(
                onClick  = onBuscaEstudio,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(0.dp),
                colors   = CardDefaults.cardColors(containerColor = AzulClaro),
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎓", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Buscar no Estúdio", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Azul)
                        Text("Cursos, PDFs e produtos digitais dos profissionais", fontSize = 11.sp, color = InkMuted)
                    }
                    Text("→", fontSize = 14.sp, color = Azul, fontWeight = FontWeight.Bold)
                }
            }
        }

        TabRow(
            selectedTabIndex = abaAtiva,
            containerColor   = Surface,
            contentColor     = Verde,
        ) {
            listOf(
                Triple(0, "⚡ Urgente", UrgenteClaro),
                Triple(1, "🏆 PMP",     DouradoClaro),
                Triple(2, "🔍 Geral",   SurfaceWarm),
            ).forEach { (idx, label, _) ->
                Tab(
                    selected = abaAtiva == idx,
                    onClick  = { abaAtiva = idx },
                    text     = {
                        Text(
                            label,
                            fontSize   = 13.sp,
                            fontWeight = if (abaAtiva == idx) FontWeight.Bold else FontWeight.Normal,
                            color      = if (abaAtiva == idx) Verde else InkMuted,
                        )
                    },
                )
            }
        }

        when (abaAtiva) {
            0 -> {
                AbaInternaBusca(
                    descricao = "Profissionais disponíveis agora · respondem em até 45 min",
                    corBanner = Color(0xFF1A0808),
                    corTexto  = Color(0xFFFF8A80),
                    icone     = "⚡",
                    loading   = loadUrgente,
                    lista     = listaUrgente.filter { p ->
                        filtroUrgente.isBlank() ||
                                p.area.contains(filtroUrgente, ignoreCase = true) ||
                                (p.perfis?.nome ?: "").contains(filtroUrgente, ignoreCase = true)
                    },
                    filtroTexto       = filtroUrgente,
                    onFiltroChange    = { filtroUrgente = it },
                    placeholderFiltro = "Tipo de profissional (ex: psicólogo)",
                    onClickProf       = { profSelecionado = it.toProfissionalPMP() },
                    onEstudio         = onEstudio,
                    onAgendar         = onAgendarRegular,
                    mostrarBotaoUrg   = true,
                )
            }
            1 -> {
                AbaInternaBusca(
                    descricao = "Profissionais com Selo de Maestria — avaliação 4★ ou superior",
                    corBanner = DouradoClaro,
                    corTexto  = Dourado,
                    icone     = "🏆",
                    loading   = loadPMP,
                    lista     = listaPMP.filter { p ->
                        filtroPMP.isBlank() ||
                                p.area.contains(filtroPMP, ignoreCase = true) ||
                                (p.perfis?.nome ?: "").contains(filtroPMP, ignoreCase = true)
                    },
                    filtroTexto       = filtroPMP,
                    onFiltroChange    = { filtroPMP = it },
                    placeholderFiltro = "Tipo de profissional (ex: médico)",
                    onClickProf       = { profSelecionado = it.toProfissionalPMP() },
                    onEstudio         = onEstudio,
                    onAgendar         = onAgendarRegular,
                    mostrarBotaoUrg   = false,
                )
            }
            2 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value         = filtroGeralArea,
                            onValueChange = { filtroGeralArea = it },
                            placeholder   = { Text("Médico, psicólogo, nutricionista...", fontSize = 12.sp) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(8.dp),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = filtroSomentePMP,
                                onClick  = { filtroSomentePMP = !filtroSomentePMP },
                                label    = { Text("🏆 PMP", fontSize = 11.sp) },
                                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = DouradoClaro, selectedLabelColor = Dourado),
                            )
                            FilterChip(
                                selected = filtroSomenteVer,
                                onClick  = { filtroSomenteVer = !filtroSomenteVer },
                                label    = { Text("✅ Verificado", fontSize = 11.sp) },
                                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = Verde.copy(alpha = 0.15f), selectedLabelColor = Verde),
                            )
                            FilterChip(
                                selected = filtroSomenteUrg,
                                onClick  = { filtroSomenteUrg = !filtroSomenteUrg },
                                label    = { Text("⚡ Urgente", fontSize = 11.sp) },
                                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = UrgenteClaro, selectedLabelColor = Urgente),
                            )
                        }
                    }
                    HorizontalDivider(color = SurfaceOff)

                    if (loadGeral) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Verde)
                        }
                    } else if (listaGeral.isEmpty()) {
                        EmptyBusca("Nenhum profissional encontrado", "Tente remover alguns filtros.")
                    } else {
                        LazyColumn(
                            modifier            = Modifier.fillMaxSize(),
                            contentPadding      = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item {
                                Text(
                                    "${listaGeral.size} profissional${if (listaGeral.size != 1) "is" else ""} encontrado${if (listaGeral.size != 1) "s" else ""}",
                                    fontSize = 12.sp, color = InkMuted,
                                )
                            }
                            items(listaGeral) { prof ->
                                CardProfissionalBusca(
                                    prof            = prof,
                                    mostrarBotaoUrg = false,
                                    onClickProf     = { profSelecionado = prof.toProfissionalPMP() },
                                    onEstudio       = onEstudio,
                                    onAgendar       = onAgendarRegular,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── SUB-COMPOSABLE: abas Urgente e PMP ─────────────────
@Composable
private fun AbaInternaBusca(
    descricao:         String,
    corBanner:         Color,
    corTexto:          Color,
    icone:             String,
    loading:           Boolean,
    lista:             List<ProfissionalComPerfil>,
    filtroTexto:       String,
    onFiltroChange:    (String) -> Unit,
    placeholderFiltro: String,
    onClickProf:       (ProfissionalComPerfil) -> Unit,
    onEstudio:         ((String) -> Unit)?,
    onAgendar:         ((String, String) -> Unit)?,
    mostrarBotaoUrg:   Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(corBanner)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icone, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(descricao, fontSize = 12.sp, color = corTexto, lineHeight = 16.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value         = filtroTexto,
                onValueChange = onFiltroChange,
                placeholder   = { Text(placeholderFiltro, fontSize = 12.sp) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(8.dp),
                trailingIcon  = {
                    if (filtroTexto.isNotBlank()) {
                        TextButton(onClick = { onFiltroChange("") }) {
                            Text("✕", fontSize = 12.sp, color = InkMuted)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
            )
        }
        HorizontalDivider(color = SurfaceOff)

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Verde)
            }
        } else if (lista.isEmpty()) {
            EmptyBusca(
                titulo    = if (mostrarBotaoUrg) "Nenhum profissional urgente agora" else "Nenhum profissional PMP encontrado",
                subtitulo = "Tente outro termo de busca.",
            )
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "${lista.size} profissional${if (lista.size != 1) "is" else ""} encontrado${if (lista.size != 1) "s" else ""}",
                        fontSize = 12.sp, color = InkMuted,
                    )
                }
                items(lista) { prof ->
                    CardProfissionalBusca(
                        prof            = prof,
                        mostrarBotaoUrg = mostrarBotaoUrg,
                        onClickProf     = { onClickProf(prof) },
                        onEstudio       = onEstudio,
                        onAgendar       = onAgendar,
                    )
                }
            }
        }
    }
}

// ── CARD DE PROFISSIONAL ──────────────────────────────
@Composable
private fun CardProfissionalBusca(
    prof:            ProfissionalComPerfil,
    mostrarBotaoUrg: Boolean,
    onClickProf:     () -> Unit,
    onEstudio:       ((String) -> Unit)?,
    onAgendar:       ((String, String) -> Unit)?,
) {
    val nome     = prof.perfis?.nome ?: "Profissional"
    val cidade   = prof.perfis?.cidade ?: ""
    val iniciais = nome.split(" ").mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("").take(2).uppercase()

    Card(
        onClick   = onClickProf,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(48.dp).background(AzulClaro, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(iniciais, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Azul)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        if (prof.is_pmp) {
                            Box(
                                modifier = Modifier
                                    .background(DouradoClaro, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("🏆 PMP", fontSize = 9.sp, color = Dourado, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(prof.area, fontSize = 12.sp, color = InkMuted)
                    if (cidade.isNotEmpty()) Text("📍 $cidade", fontSize = 11.sp, color = InkMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("R$ ${prof.valor_normal}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                    if (prof.disponivel_urgente) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .background(UrgenteClaro, RoundedCornerShape(20.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text("⚡ Urgente", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Urgente)
                        }
                    }
                }
            }

            val temAcoes = onAgendar != null || onEstudio != null || mostrarBotaoUrg
            if (temAcoes) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SurfaceOff)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (mostrarBotaoUrg && prof.disponivel_urgente) {
                        Button(
                            onClick        = onClickProf,
                            colors         = ButtonDefaults.buttonColors(containerColor = Urgente),
                            shape          = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("⚡ Chamar agora", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    if (onAgendar != null && !mostrarBotaoUrg) {
                        TextButton(
                            onClick = { onAgendar(prof.id, nome) },
                            colors  = ButtonDefaults.textButtonColors(contentColor = Verde),
                        ) {
                            Text("📅 Agendar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (onEstudio != null) {
                        TextButton(
                            onClick = { onEstudio(prof.id) },
                            colors  = ButtonDefaults.textButtonColors(contentColor = Azul),
                        ) {
                            Text("Ver Estúdio →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardProfissionalReal(
    prof:      ProfissionalComPerfil,
    onClick:   () -> Unit,
    onEstudio: ((String) -> Unit)? = null,
    onAgendar: ((String, String) -> Unit)? = null,
) = CardProfissionalBusca(
    prof            = prof,
    mostrarBotaoUrg = false,
    onClickProf     = onClick,
    onEstudio       = onEstudio,
    onAgendar       = onAgendar,
)

@Composable
private fun EmptyBusca(titulo: String, subtitulo: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("😕", fontSize = 36.sp)
            Text(titulo,    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(subtitulo, fontSize = 13.sp, color = InkMuted)
        }
    }
}