package br.com.brasiltupi.conecta


import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.window.Dialog
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.AbaUrgenteCompartilhada
// ── TELA PRINCIPAL ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardProfissionalScreen(
    onSair:                  () -> Unit,
    onEstudio:               (() -> Unit)?                                                       = null,
    onPerfil:                (() -> Unit)?                                                       = null,
    onRelatorios:            (() -> Unit)?                                                       = null,
    onModalidades:           (() -> Unit)?                                                       = null,
    onKyc:                   (() -> Unit)?                                                       = null,
    onIniciarChamadaRegular: ((consultaId: String) -> Unit)? = null,    onReferral:              (() -> Unit)?                                                       = null,
    onDisputa:               (() -> Unit)? = null,
    onKycStatusChanged:      ((Boolean) -> Unit)? = null,
    ) {
    var abaSelecionada by remember { mutableStateOf("visao") }
    var menuExpandido  by remember { mutableStateOf(false) }

    var nomeUsuario       by remember { mutableStateOf("") }
    var iniciais          by remember { mutableStateOf("") }
    var credibilidade     by remember { mutableIntStateOf(0) }
    var disponivelUrgente by remember { mutableStateOf(false) }
    var consultas         by remember { mutableStateOf<List<ConsultaProfissional>>(emptyList()) }
    var carregando        by remember { mutableStateOf(true) }
    var isPmp             by remember { mutableStateOf(false) }
    var kycAprovado       by remember { mutableStateOf(false) }

    var resumoSemanas   by remember { mutableIntStateOf(0) }
    var resumoGanhoMes  by remember { mutableDoubleStateOf(0.0) }
    var resumoNotaMedia by remember { mutableDoubleStateOf(0.0) }
    var totalCreditos   by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentUserId) {
        val uid = currentUserId ?: return@LaunchedEffect
        val perfil = getPerfilAndroid(uid)
        if (perfil != null) {
            nomeUsuario = perfil.nome
            iniciais    = perfil.nome.split(" ").map { it[0] }.joinToString("").take(2).uppercase()
        }
        val meuPerfil = getMeuPerfilProfissional(uid)
        if (meuPerfil != null) {
            credibilidade = meuPerfil.credibilidade
            disponivelUrgente = meuPerfil.disponivel_urgente
            isPmp = meuPerfil.is_pmp
            kycAprovado = verificarKycAprovado(uid)
            onKycStatusChanged?.invoke(kycAprovado)
        }
        consultas = buscarConsultasProfissional(uid)

        val concluidas4s = consultas.filter { it.status in listOf("concluida", "concluido") }
        resumoSemanas   = concluidas4s.size
        resumoGanhoMes  = concluidas4s.sumOf { it.valor.toDouble() }
        val notas       = concluidas4s.map { it.avaliacao }.filter { it > 0 }
        resumoNotaMedia = if (notas.isNotEmpty()) notas.average() else 0.0

        carregando = false
    }

    // Abas primárias
    val abasPrimarias = listOf(
        Triple("visao",        "Visão Geral", "🏠"),
        Triple("atendimentos", "Consultas",   "📋"),
        Triple("regular",      "Regular",     "📅"),
        Triple("urgente",      "Urgente",     "⚡"),
        Triple("estudio",      "Estúdio",     "🎓"),
    )

    // Menu ⋮ — rótulo "Verificação da Conta" em vez de "Documentos KYC"
    val menuItens = listOf(
        Triple("financeiro",    "Financeiro",        "💰"),
        Triple("credibilidade", "Credibilidade",     "⭐"),
        Triple("relatorios",    "Relatórios",        "📊"),
        Triple("indicacoes",    "Indicações",        "🎁"),
        Triple("disputas",      "Disputas",          "⚖️"),
        Triple("kyc",           "Verificação da Conta", "🔒"),
        Triple("perfil",        "Meu Perfil",        "👤"),
        Triple("sair",          "Sair",              "🚪"),
    )

    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = SurfaceWarm,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(34.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(iniciais.ifEmpty { "?" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(nomeUsuario.ifEmpty { "Brasil Tupi" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Painel do Profissional", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                },
                actions = {
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
                                            "financeiro"    -> abaSelecionada = "financeiro"
                                            "credibilidade" -> abaSelecionada = "credibilidade"
                                            "relatorios"    -> if (onRelatorios != null) onRelatorios() else abaSelecionada = "relatorios"
                                            "indicacoes"    -> onReferral?.invoke()
                                            "disputas"      -> onDisputa?.invoke()
                                            "kyc"           -> onKyc?.invoke()
                                            "perfil"        -> if (onPerfil != null) onPerfil() else abaSelecionada = "perfil"
                                            "sair"          -> scope.launch { signOutAndroid(); onSair() }
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Azul),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface, tonalElevation = 4.dp) {
                abasPrimarias.forEach { (id, label, icon) ->
                    val selecionado = abaSelecionada == id
                    NavigationBarItem(
                        selected = selecionado,
                        onClick  = {
                            when (id) {
                                "estudio" -> {
                                    if (!kycAprovado) {
                                        if (onKyc != null) onKyc() else abaSelecionada = id
                                    } else {
                                        if (onEstudio != null) onEstudio() else abaSelecionada = id
                                    }
                                }
                                "regular" -> {
                                    if (!kycAprovado) {
                                        if (onKyc != null) onKyc() else abaSelecionada = id
                                    } else {
                                        if (onModalidades != null) onModalidades() else abaSelecionada = id
                                    }
                                }
                                else -> abaSelecionada = id
                            }
                        },
                        icon  = { Text(icon, fontSize = if (selecionado) 22.sp else 20.sp) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
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
            when (abaSelecionada) {
                "visao"         -> {
                    AbaVisaoGeralDash(
                        nomeUsuario       = nomeUsuario,
                        credibilidade     = credibilidade,
                        consultas          = consultas,
                        carregando         = carregando,
                        disponivelUrgente  = disponivelUrgente,
                        onRelatorios       = onRelatorios,
                        resumoSemanas      = resumoSemanas,
                        resumoGanhoMes     = resumoGanhoMes,
                        resumoNotaMedia    = resumoNotaMedia,
                        totalCreditos      = totalCreditos,
                        onReferral         = onReferral,
                        onKyc              = onKyc,
                        kycAprovado        = kycAprovado,
                    )
                }
                "atendimentos"  -> {
                    AbaAtendimentosDash(
                        consultas               = consultas,
                        carregando              = carregando,
                        onIniciarChamadaRegular = onIniciarChamadaRegular,
                    )
                }
                "urgente"       -> AbaUrgenteCompartilhada(
                    disponivelInicial = disponivelUrgente,
                    userId            = currentUserId ?: "",
                    consultas         = consultas,
                    kycAprovado       = kycAprovado,
                    onKyc             = onKyc,
                    mostrarGuiaChamada = true,
                )

                "financeiro"    -> {
                    AbaFinanceiroDash(isPmp = isPmp)
                }
                "credibilidade" -> {
                    AbaCredibilidadeDash(
                        credibilidade = credibilidade,
                        isPmp         = isPmp,
                    )
                }
                "relatorios"    -> {
                    if (onRelatorios != null) { LaunchedEffect(Unit) { onRelatorios() } }
                    else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Relatórios indisponíveis", color = InkMuted) }
                }
                "perfil" -> {
                    if (onPerfil != null) { LaunchedEffect(Unit) { onPerfil() } }
                    else AbaPerfilProfissional()
                }
            }
        }
    }
}

// ── ABA: VISÃO GERAL ──────────────────────────────────
@Composable
fun AbaVisaoGeralDash(
    nomeUsuario:       String  = "",
    credibilidade:     Int     = 0,
    consultas:         List<ConsultaProfissional> = emptyList(),
    carregando:        Boolean = false,
    disponivelUrgente: Boolean = false,
    onRelatorios:      (() -> Unit)? = null,
    resumoSemanas:     Int    = 0,
    resumoGanhoMes:    Double = 0.0,
    resumoNotaMedia:   Double = 0.0,
    totalCreditos:     Int    = 0,
    onReferral:        (() -> Unit)? = null,
    onKyc:             (() -> Unit)? = null,
    kycAprovado:       Boolean = false,
) {
    val concluidas = consultas.filter { it.status == "concluida" || it.status == "concluido" }
    val agendadas  = consultas.filter { it.status == "agendada"  || it.status == "agendado"  }

    val ganhosMes = concluidas.sumOf { it.valor }

    val notasValidas   = concluidas.map { it.avaliacao }.filter { it > 0 }
    val avaliacaoMedia = if (notasValidas.isNotEmpty()) "%.1f".format(notasValidas.average()) else "--"

    val primeiroNome = nomeUsuario.split(" ").firstOrNull() ?: "Profissional"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Saudação
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Azul),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Olá, $primeiroNome! 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (carregando) "Carregando dados..."
                    else "Você tem ${agendadas.size} atendimento(s) agendado(s).",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Métricas
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaCard(modifier = Modifier.weight(1f), icone = "📋", numero = "${consultas.size}", label = "Atendimentos", cor = Verde)
            MetricaCard(modifier = Modifier.weight(1f), icone = "⭐", numero = avaliacaoMedia,      label = "Avaliação",    cor = Dourado)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricaCard(modifier = Modifier.weight(1f), icone = "💰", numero = "R$ $ganhosMes", label = "Ganhos total",  cor = Azul)
            MetricaCard(modifier = Modifier.weight(1f), icone = "🎯", numero = "$credibilidade", label = "Credibilidade", cor = Verde)
        }

        // ── Fase 4.4: Preview de relatório inline ─────────────────────────
        if (resumoSemanas > 0 || resumoGanhoMes > 0.0) {
            Card(
                onClick  = { onRelatorios?.invoke() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
                border   = BorderStroke(1.dp, Azul.copy(alpha = 0.2f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Últimas 4 semanas", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Ver relatório →", fontSize = 12.sp, color = Azul)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$resumoSemanas", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Verde)
                            Text("Atendimentos", fontSize = 10.sp, color = InkMuted, textAlign = TextAlign.Center)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("R$ ${"%.0f".format(resumoGanhoMes)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Azul)
                            Text("Ganhos", fontSize = 10.sp, color = InkMuted, textAlign = TextAlign.Center)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%.1f★".format(resumoNotaMedia), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Dourado)
                            Text("Nota média", fontSize = 10.sp, color = InkMuted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // Próximos atendimentos
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Próximos atendimentos", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                if (carregando) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Verde, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (agendadas.isEmpty()) {
                    Text("Nenhum atendimento agendado", fontSize = 13.sp, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    agendadas.take(5).forEach { c ->
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier         = Modifier.size(44.dp).background(AzulClaro, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(c.data.split("/").getOrElse(0) { "--" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Azul)
                                    Text(
                                        c.data.split("/").getOrElse(1) { "" }.let { mes ->
                                            listOf("","jan","fev","mar","abr","mai","jun","jul","ago","set","out","nov","dez")
                                                .getOrElse(mes.toIntOrNull() ?: 0) { mes }
                                        },
                                        fontSize = 9.sp, color = Azul,
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
                                        RoundedCornerShape(20.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
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
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Últimas avaliações", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                val comAvaliacao = concluidas.filter { it.avaliacao > 0 }.take(3)
                if (!carregando && comAvaliacao.isEmpty()) {
                    Text("Nenhuma avaliação ainda", fontSize = 13.sp, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    comAvaliacao.forEach { c ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).background(AzulClaro, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                Text(c.nomeCliente.split(" ").map { it[0] }.joinToString("").take(2), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Azul)
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
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = if (disponivelUrgente) VerdeClaro else UrgenteClaro),
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (disponivelUrgente) "🟢" else "🔴", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (disponivelUrgente) "Disponível para urgências" else "Indisponível para urgências",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color    = if (disponivelUrgente) Verde else Urgente,
                    )
                    Text(
                        if (disponivelUrgente) "Você aparece na área urgente" else "Ative na aba Urgente",
                        fontSize = 11.sp, color = InkMuted,
                    )
                }
            }
        }

        // ── Card de status KYC com rótulo "Verificação da Conta"
        if (onKyc != null) {
            Card(
                onClick  = onKyc,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = if (kycAprovado) Color(0xFFF0FBF4) else Color(0xFFFFF8E1),
                ),
                border = BorderStroke(1.dp, if (kycAprovado) Verde.copy(alpha = 0.3f) else Color(0xFFF57F17).copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (kycAprovado) "✅" else "🔒", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (kycAprovado) "Verificação da Conta aprovada" else "Verificação da Conta pendente",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color    = if (kycAprovado) Verde else Color(0xFF6D4C00),
                        )
                        Text(
                            if (kycAprovado) "Ver ou atualizar documentos enviados" else "Envie seus documentos para desbloquear recursos",
                            fontSize = 12.sp, color = InkMuted, lineHeight = 17.sp,
                        )
                    }
                    Text("→", fontSize = 16.sp, color = if (kycAprovado) Verde else Color(0xFFF57F17), fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Card de indicações com preview de créditos
        if (onReferral != null) {
            Card(
                onClick  = onReferral,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFFF0FBF4)),
                border   = BorderStroke(1.dp, Verde.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎁", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Programa de Indicações", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                        if (totalCreditos > 0) {
                            Text("Você tem $totalCreditos crédito(s) disponível(is).", fontSize = 12.sp, color = InkMuted)
                        } else {
                            Text("Indique colegas e ganhe benefícios na plataforma.", fontSize = 12.sp, color = InkMuted)
                        }
                    }
                    Text("→", fontSize = 16.sp, color = Verde, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Botão de Relatórios completos
        if (onRelatorios != null) {
            OutlinedButton(
                onClick  = onRelatorios,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, Azul.copy(alpha = 0.4f)),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Azul),
            ) {
                Text("📊  Ver relatórios completos", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── CARDS DE MÉTRICA ─────────────────────────────────
@Composable
fun MetricaCard(
    modifier: Modifier = Modifier,
    icone:    String,
    numero:   String,
    label:    String,
    cor:      Color,
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier         = Modifier.size(36.dp).background(cor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(icone, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(numero, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(label,  fontSize = 11.sp, color = InkMuted)
        }
    }
}

// ── ABA: ATENDIMENTOS ────────────────────────────────
@Composable
fun AbaAtendimentosDash(
    consultas:               List<ConsultaProfissional> = emptyList(),
    carregando:              Boolean = false,
    onIniciarChamadaRegular: ((consultaId: String) -> Unit)? = null,) {
    var agendamentos by remember { mutableStateOf<List<AgendamentoRegular>>(emptyList()) }
    var loadingAg    by remember { mutableStateOf(true) }
    var tickMs       by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope        = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val uid = currentUserId
        if (uid != null) {
            agendamentos = AtendimentosRepository.buscarAgendamentosProfissional(uid)
                .filter { it.status in listOf("pendente", "confirmado", "pago") }
                .sortedWith(compareBy({ it.dataAgendada }, { it.horaInicio }))
        }
        loadingAg = false
        while (true) { delay(30_000L); tickMs = System.currentTimeMillis() }
    }

    val hojeStr = remember {
        val cal = java.util.Calendar.getInstance()
        "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    var filtro by remember { mutableStateOf("todos") }
    val lista = when (filtro) {
        "concluido" -> consultas.filter { it.status in listOf("concluida", "concluido") }
        "agendado"  -> consultas.filter { it.status in listOf("agendada",  "agendado")  }
        else        -> consultas
    }

    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (loadingAg) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Verde, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (agendamentos.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceWarm, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).background(Urgente, RoundedCornerShape(50)))
                    Text("Hoje", fontSize = 11.sp, color = InkMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).background(Verde, RoundedCornerShape(50)))
                    Text("Futuro", fontSize = 11.sp, color = InkMuted)
                }
                Text("Botão ativo 5min antes", fontSize = 10.sp, color = InkMuted)
            }

            agendamentos.forEach { ag ->
                val isHoje   = ag.dataAgendada == hojeStr
                val corBorda = if (isHoje) Urgente else Verde
                val corFundo = if (isHoje) UrgenteClaro else VerdeClaro
                val corTexto = if (isHoje) Urgente else Verde

                val horaLiberada = remember(ag.id, tickMs) {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        val ms  = sdf.parse("${ag.dataAgendada} ${ag.horaInicio}")?.time ?: 0L
                        tickMs >= (ms - 5 * 60 * 1000L)
                    } catch (e: Exception) { false }
                }
                var iniciando by remember(ag.id) { mutableStateOf(false) }

                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(containerColor = Surface),
                    border    = BorderStroke(1.5.dp, corBorda.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isHoje) 3.dp else 1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val nomeExib = ag.nomeCliente ?: "Cliente"
                                Box(
                                    modifier         = Modifier.size(42.dp).background(corFundo, RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        nomeExib.split(" ").mapNotNull { it.firstOrNull()?.toString() }.joinToString("").take(2).uppercase(),
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = corTexto,
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(nomeExib, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                                    Text(ag.tituloModalidade ?: "Consulta regular", fontSize = 11.sp, color = InkMuted)
                                }
                            }
                            Text("R$ ${"%.0f".format(ag.valorCobrado)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.background(corFundo, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(if (isHoje) "🚨 HOJE" else ag.dataAgendada.split("-").reversed().take(2).joinToString("/"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = corTexto)
                                }
                                Text("${ag.horaInicio} – ${ag.horaFim}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
                            }
                            if (!isHoje) {
                                Text("📅 ${ag.dataAgendada.split("-").reversed().take(2).joinToString("/")}", fontSize = 11.sp, color = InkMuted)
                            } else if (horaLiberada) {
                                Button(
                                    onClick = {
                                        if (!iniciando) {
                                            iniciando = true
                                            scope.launch {
                                                onIniciarChamadaRegular?.invoke(ag.id)
                                                iniciando = false
                                            }
                                        }
                                    },
                                    enabled        = !iniciando,
                                    colors         = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                                    shape          = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    if (iniciando) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                    else Text("📞 Ligar agora", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Box(modifier = Modifier.background(SurfaceOff, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("⏰ Aguardar ${ag.horaInicio}", fontSize = 11.sp, color = InkMuted, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = SurfaceOff, modifier = Modifier.padding(vertical = 4.dp))
        }

        Text("Histórico de consultas", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkMuted)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("todos" to "Todos", "concluido" to "Concluídos", "agendado" to "Agendados").forEach { (id, label) ->
                FilterChip(
                    selected = filtro == id,
                    onClick  = { filtro = id },
                    label    = { Text(label, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = Verde, selectedLabelColor = Color.White),
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
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(40.dp).background(AzulClaro, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                    Text(c.nomeCliente.split(" ").map { it[0] }.joinToString("").take(2), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Azul)
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.background(if (isUrgente) UrgenteClaro else VerdeClaro, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(c.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isUrgente) Urgente else Verde)
                                }
                                Box(modifier = Modifier.background(if (isConcluido) VerdeClaro else AzulClaro, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(if (isConcluido) "Concluído" else "Agendado", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isConcluido) Verde else Azul)
                                }
                            }
                            if (c.avaliacao > 0) Text("★".repeat(c.avaliacao), fontSize = 13.sp, color = DouradoMedio)
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

// ════════════════════════════════════════════════════════════════
// ABA CREDIBILIDADE — CORRIGIDA
// ════════════════════════════════════════════════════════════════
@Composable
fun AbaCredibilidadeDash(credibilidade: Int = 0, isPmp: Boolean = false) {
    val mediaEstrelas = credibilidade / 20.0
    val pontosFaltantes = (80 - credibilidade).coerceAtLeast(0)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sua pontuação", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier         = Modifier.size(120.dp).background(if (credibilidade >= 80) Color(0xFFFDF3D8) else VerdeClaro, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$credibilidade", fontSize = 36.sp, fontWeight = FontWeight.Black, color = if (credibilidade >= 80) Dourado else Verde)
                        Text("pontos", fontSize = 12.sp, color = InkMuted)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    when {
                        credibilidade >= 80 -> "🏆 Elegível ao PMP"
                        credibilidade >= 50 -> "📈 Crescendo"
                        else                -> "🌱 Iniciando"
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkSoft,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Como é calculado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth().background(Color(0xFFF3F8FF), RoundedCornerShape(10.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⭐", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Média das estrelas recebidas × 20", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Azul)
                        Text("Sua pontuação atual é ${"%.1f".format(mediaEstrelas)} estrelas = $credibilidade pontos.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().background(UrgenteClaro, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.Top) {
                    Text("⚠️", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Para obter e manter o Selo PMP são necessários 80 pontos (média 4.0). Se a média cair abaixo de 4.0, o selo será revogado.", fontSize = 12.sp, color = Urgente, lineHeight = 17.sp)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F0)),
            border   = BorderStroke(1.dp, DouradoMedio.copy(alpha = 0.4f)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Programa PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                Text("Faltam $pontosFaltantes pontos para o PMP", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                LinearProgressIndicator(progress = { (credibilidade / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = DouradoMedio, trackColor = SurfaceOff)
                Text("$credibilidade/100", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                Button(
                    onClick  = { /* TODO: solicitar PMP quando elegível */ },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled  = credibilidade >= 80,
                    colors   = ButtonDefaults.buttonColors(containerColor = Azul, contentColor = Color.White, disabledContainerColor = SurfaceOff, disabledContentColor = InkMuted),
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    Text(if (credibilidade >= 80) "Candidatar-me ao PMP" else "Disponível com 80 pontos", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── ABA: URGENTE ──────────────────────────────────────

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
        if (meu != null) { area = meu.area; bio = meu.descricao ?: "" }
    }

    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Meu Perfil", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                listOf("Nome" to nome, "Área" to area, "Sobre" to bio.ifEmpty { "—" }).forEach { (label, valor) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 13.sp, color = InkMuted)
                        Text(valor,  fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Para editar seu perfil completo, acesse a tela de Perfil.", fontSize = 12.sp, color = InkMuted)
            }
        }
    }
}