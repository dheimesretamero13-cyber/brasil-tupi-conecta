package br.com.brasiltupi.conecta

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*

// ProfissionalPMP é declarado em SupabaseClient.kt — não redeclarar aqui.

val profissionaisMock = emptyList<ProfissionalPMP>()

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun BuscaScreen(
    onVoltar:  () -> Unit,
    onEstudio: (String) -> Unit,
    onPagar:   () -> Unit,
) {
    var busca by remember { mutableStateOf("") }
    var somenteUrgente by remember { mutableStateOf(false) }
    var profSelecionado by remember { mutableStateOf<ProfissionalPMP?>(null) }
    var agendando by remember { mutableStateOf<Pair<ProfissionalPMP, String>?>(null) }
    var profissionaisDB by remember { mutableStateOf<List<ProfissionalPMP>>(emptyList()) }
    var loadingDB by remember { mutableStateOf(true) }

    LaunchedEffect(somenteUrgente) {
        loadingDB = true
        val dados = getProfissionaisPMPAndroid(somenteUrgente, "")
        profissionaisDB = dados.map { it.toProfissionalPMP() }
        loadingDB = false
    }

    val resultado = profissionaisDB.filter { p ->
        val matchBusca = busca.isEmpty() ||
                p.nome.contains(busca, ignoreCase = true) ||
                p.area.contains(busca, ignoreCase = true) ||
                p.cidade.contains(busca, ignoreCase = true) ||
                p.especialidades.any { it.contains(busca, ignoreCase = true) }
        val matchUrgente = !somenteUrgente || p.disponivelUrgente
        matchBusca && matchUrgente
    }

    if (profSelecionado != null) {
        PerfilPublicoScreen(
            prof      = profSelecionado!!,
            onVoltar  = { profSelecionado = null },
            onAgendar = { tipo ->
                val prof = profSelecionado ?: return@PerfilPublicoScreen
                agendando = prof to tipo
            },
            onEstudio = onEstudio,
        )
        return
    }

    if (agendando != null) {
        AgendarScreen(
            prof        = agendando!!.first,
            tipo        = agendando!!.second,
            onVoltar    = { agendando = null },
            onConcluido = { agendando = null; profSelecionado = null },
            onPagar     = onPagar,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(SurfaceWarm)) {
        Column(
            modifier = Modifier
                .background(Azul)
                .padding(horizontal = 24.dp)
                .padding(top = 52.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onVoltar) {
                    Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
            Text("Profissionais PMP", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Verificados · Avaliados · Confiáveis", fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            OutlinedTextField(
                value         = busca,
                onValueChange = { busca = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Buscar por nome, área ou cidade...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                shape         = RoundedCornerShape(10.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = DouradoMedio,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    cursorColor          = DouradoMedio,
                ),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .background(if (somenteUrgente) Urgente.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .clickable { somenteUrgente = !somenteUrgente }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚡", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disponível agora (urgente)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (somenteUrgente) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked         = somenteUrgente,
                    onCheckedChange = { somenteUrgente = it },
                    colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Urgente),
                )
            }
        }

        Row(
            modifier              = Modifier.fillMaxWidth().background(VerdeClaro).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf("✓ 10+ atendimentos", "✓ Zero negativos", "✓ Plano PMP ativo").forEach {
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Verde)
            }
        }

        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                if (loadingDB) "Carregando..."
                else "${resultado.size} profissional${if (resultado.size != 1) "is" else ""} encontrado${if (resultado.size != 1) "s" else ""}",
                fontSize = 13.sp, color = InkMuted,
            )
            if ((busca.isNotEmpty() || somenteUrgente) && !loadingDB) {
                TextButton(onClick = { busca = ""; somenteUrgente = false }) {
                    Text("Limpar", color = Verde, fontSize = 12.sp)
                }
            }
        }

        if (loadingDB) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DouradoMedio)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Carregando profissionais...", fontSize = 13.sp, color = InkMuted)
                }
            }
        } else if (resultado.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("🔍", fontSize = 48.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Nenhum profissional encontrado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Tente outros termos ou remova os filtros.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(resultado) { prof ->
                    CardProfissional(prof = prof, onClick = { profSelecionado = prof }, onEstudio = onEstudio)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ── CARD PROFISSIONAL ─────────────────────────────────
@Composable
fun CardProfissional(prof: ProfissionalPMP, onClick: () -> Unit, onEstudio: ((String) -> Unit)? = null) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(52.dp).background(Azul, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text(prof.iniciais, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(prof.nome, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(prof.area, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                    Text("📍 ${prof.cidade}", fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(modifier = Modifier.background(Color(0xFFFDF3D8), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("🏆 PMP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC49A2A))
                    }
                    if (prof.disponivelUrgente) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.background(UrgenteClaro, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("⚡ Urgente", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Urgente)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("⭐ ${prof.avaliacao}", fontSize = 12.sp, color = InkSoft, fontWeight = FontWeight.SemiBold)
                Text("·", color = InkMuted, fontSize = 12.sp)
                Text("${prof.atendimentos} atendimentos", fontSize = 12.sp, color = InkMuted)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                prof.especialidades.take(2).forEach { esp ->
                    Box(modifier = Modifier.background(AzulClaro, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(esp, fontSize = 10.sp, color = Azul, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Normal", fontSize = 10.sp, color = InkMuted)
                        Text("R$ ${prof.valorNormal}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    if (prof.valorUrgente != null) {
                        Column {
                            Text("⚡ Urgente", fontSize = 10.sp, color = Urgente)
                            Text("R$ ${prof.valorUrgente}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Urgente)
                        }
                    }
                }
                Button(
                    onClick        = onClick,
                    colors         = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                    shape          = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Ver perfil →", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onEstudio != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onEstudio(prof.supabaseId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB07D00)),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC49A2A)),
                ) {
                    Text("🎨 Ver Estúdio", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── PERFIL PÚBLICO ────────────────────────────────────
@Composable
fun PerfilPublicoScreen(
    prof:      ProfissionalPMP,
    onVoltar:  () -> Unit,
    onAgendar: (String) -> Unit,
    onEstudio: ((String) -> Unit)? = null,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(SurfaceWarm), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(modifier = Modifier.background(Azul).padding(horizontal = 24.dp).padding(top = 52.dp, bottom = 28.dp)) {
                TextButton(onClick = onVoltar) {
                    Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(68.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Text(prof.iniciais, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(prof.nome, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(prof.area, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                        Text("📍 ${prof.cidade}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgePerfil("🏆 PMP Verificado", Color(0xFFFDF3D8), Color(0xFFC49A2A))
                    if (prof.disponivelUrgente) BadgePerfil("⚡ Disponível agora", UrgenteClaro, Urgente)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(vertical = 4.dp)) {
                listOf("⭐ ${prof.avaliacao}" to "Avaliação", "${prof.atendimentos}" to "Atendimentos", "0" to "Negativos").forEach { (num, label) ->
                    Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(num, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(label, fontSize = 11.sp, color = InkMuted)
                    }
                }
            }
            HorizontalDivider(color = SurfaceOff)
        }

        item {
            Column(modifier = Modifier.background(Surface).padding(20.dp)) {
                Text("Sobre", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 8.dp))
                Text(prof.descricao, fontSize = 14.sp, color = InkSoft, lineHeight = 20.sp)
                if (prof.conselho.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("🏛 ${prof.conselho}", fontSize = 13.sp, color = InkMuted)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    prof.especialidades.forEach { esp ->
                        Box(modifier = Modifier.background(AzulClaro, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text(esp, fontSize = 11.sp, color = Azul, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            HorizontalDivider(color = SurfaceOff)
        }

        item {
            Column(modifier = Modifier.background(VerdeClaro).padding(20.dp)) {
                Text("Por que este profissional aparece aqui?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "Mínimo de 10 atendimentos" to "${prof.atendimentos} realizados",
                    "Zero avaliações negativas"  to "Histórico 100% positivo",
                    "Plano PMP ativo"            to "Mensalidade + porcentagem",
                ).forEach { (titulo, detalhe) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                        Box(modifier = Modifier.size(22.dp).background(Verde, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text("✓", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(detalhe, fontSize = 11.sp, color = InkMuted)
                        }
                    }
                }
            }
            HorizontalDivider(color = SurfaceOff)
        }

        item {
            Column(modifier = Modifier.background(Surface).padding(20.dp)) {
                Text("Agendar consulta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 14.dp))
                if (onEstudio != null) {
                    OutlinedButton(
                        onClick  = { onEstudio(prof.supabaseId) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB07D00)),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC49A2A)),
                    ) {
                        Text("🎨 Ver Estúdio deste profissional", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OpcaoConsulta(titulo = "Consulta normal", descricao = "Agendada com antecedência · 15 minutos", preco = "R$ ${prof.valorNormal}", corPreco = Verde, onClick = { onAgendar("normal") })
                if (prof.valorUrgente != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OpcaoConsulta(titulo = "⚡ Consulta urgente", descricao = "Resposta em até 45 minutos · 15 minutos", preco = "R$ ${prof.valorUrgente}", corPreco = Urgente, urgente = true, onClick = { onAgendar("urgente") })
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sem cobranças antecipadas. Pague só após a consulta.", fontSize = 12.sp, color = Verde)
                }
            }
        }
    }
}

@Composable
fun BadgePerfil(texto: String, bg: Color, cor: Color) {
    Box(modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(texto, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cor)
    }
}

@Composable
fun OpcaoConsulta(
    titulo:    String,
    descricao: String,
    preco:     String,
    corPreco:  Color,
    urgente:   Boolean = false,
    onClick:   () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = if (urgente) UrgenteClaro else SurfaceOff),
        border   = if (urgente) androidx.compose.foundation.BorderStroke(1.dp, Urgente.copy(alpha = 0.3f)) else null,
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(descricao, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(preco, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = corPreco)
                Text("→", fontSize = 14.sp, color = corPreco)
            }
        }
    }
}

// ── TELA DE AGENDAMENTO ───────────────────────────────
@Composable
fun AgendarScreen(
    prof:        ProfissionalPMP,
    tipo:        String,
    onVoltar:    () -> Unit,
    onConcluido: () -> Unit,
    onPagar:     () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var nome     by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var senha    by remember { mutableStateOf("") }
    var etapa    by remember { mutableStateOf(1) }
    var resultadoAcesso  by remember { mutableStateOf<ResultadoAcesso?>(null) }
    var verificandoAcesso by remember { mutableStateOf(false) }
    var loading  by remember { mutableStateOf(false) }
    var erro     by remember { mutableStateOf("") }
    var sucesso  by remember { mutableStateOf(false) }
    var consultaIdGerado by remember { mutableStateOf<String?>(null) }

    val dataHoje = remember {
        val c = java.util.Calendar.getInstance()
        "%02d/%02d/%04d".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR))
    }
    val horaAtual = remember {
        val c = java.util.Calendar.getInstance()
        "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }

    if (sucesso) {
        Column(
            modifier            = Modifier.fillMaxSize().background(SurfaceWarm).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(72.dp).background(VerdeClaro, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Text("✓", fontSize = 32.sp, color = Verde, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Agendamento confirmado!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
            Text("Sua consulta com ${prof.nome} foi registrada com sucesso.", fontSize = 14.sp, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            if (consultaIdGerado != null) Text("ID: $consultaIdGerado", fontSize = 11.sp, color = InkMuted)
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onConcluido, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                Text("Voltar ao início", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(SurfaceWarm).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(modifier = Modifier.height(48.dp))
        TextButton(onClick = onVoltar) { Text("← Voltar", color = InkMuted, fontSize = 13.sp) }
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).background(Azul, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text(prof.iniciais, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(prof.nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        if (tipo == "urgente") "⚡ Urgente · R$ ${prof.valorUrgente}" else "Normal · R$ ${prof.valorNormal}",
                        fontSize = 12.sp, color = if (tipo == "urgente") Urgente else InkMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (etapa == 1) {
            Text("Criar conta gratuita", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Você será cadastrado como cliente automaticamente.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            CampoTexto("Nome completo *", nome, { nome = it }, "Seu nome completo")
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("E-mail *", email, { email = it }, "seu@email.com", keyboardType = KeyboardType.Email)
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("Telefone *", telefone, { telefone = it }, "(00) 00000-0000", keyboardType = KeyboardType.Phone)
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("Criar senha *", senha, { senha = it }, "Mínimo 6 caracteres", senha = true)
            Spacer(modifier = Modifier.height(20.dp))
            BotaoProximo("Continuar →") {
                if (nome.isBlank() || email.isBlank() || senha.length < 6) {
                    erro = "Preencha todos os campos. Senha mínima: 6 caracteres."
                    return@BotaoProximo
                }
                etapa = 2
            }
        } else {
            Text("Confirmar agendamento", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    RevisaoItem("Profissional", prof.nome)
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Tipo", if (tipo == "urgente") "⚡ Urgente" else "Normal")
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Valor", if (tipo == "urgente") "R$ ${prof.valorUrgente}" else "R$ ${prof.valorNormal}")
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Data", "$dataHoje às $horaAtual")
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Sua conta", email.ifEmpty { "—" })
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔒", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sem cobranças antecipadas.", fontSize = 12.sp, color = Verde)
            }
            if (erro.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(erro, color = Urgente, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    verificandoAcesso = true; erro = ""
                    scope.launch {
                        try {
                            val uid: String = if (currentUserId != null) {
                                currentUserId!!
                            } else {
                                val ok = signUpAndroid(email = email, senha = senha, nome = nome, telefone = telefone, tipo = "cliente")
                                if (!ok || currentUserId == null) { erro = "Erro ao criar conta. Tente novamente."; verificandoAcesso = false; return@launch }
                                currentUserId!!
                            }
                            val resultado = verificarAcessoAgendamento(uid, prof.supabaseId)
                            verificandoAcesso = false
                            if (!resultado.acesso) { resultadoAcesso = resultado; return@launch }
                            loading = true
                            val valor = (if (tipo == "urgente") prof.valorUrgente else prof.valorNormal)?.toDouble() ?: 0.0
                            val id = criarAgendamento(clienteId = uid, profId = prof.supabaseId, data = dataHoje, hora = horaAtual, tipo = tipo, valor = valor)
                            loading = false
                            if (id != null) { consultaIdGerado = id; sucesso = true }
                            else erro = "Erro ao confirmar agendamento. Tente novamente."
                        } catch (e: Exception) { loading = false; verificandoAcesso = false; erro = "Erro inesperado: ${e.message}" }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = !loading && !verificandoAcesso,
                colors   = ButtonDefaults.buttonColors(containerColor = if (tipo == "urgente") Urgente else Verde, contentColor = Color.White),
                shape    = RoundedCornerShape(10.dp),
            ) {
                if (loading || verificandoAcesso) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (tipo == "urgente") "⚡ Confirmar agora" else "Confirmar agendamento", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    resultadoAcesso?.let { resultado ->
        val (titulo, descricao) = when (resultado.motivo) {
            "sem_plano"       -> "Sem plano ativo" to "Assine um plano para agendar consultas ou pague uma taxa avulsa."
            "limite_atingido" -> "Limite atingido" to "Você usou ${resultado.profs_usados}/${resultado.limite} profissionais do plano ${resultado.plano_atual?.replaceFirstChar { it.uppercase() }}."
            else              -> "Acesso bloqueado" to "Não foi possível verificar seu acesso."
        }
        AlertDialog(
            onDismissRequest = { resultadoAcesso = null },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(16.dp),
            title = { Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().background(UrgenteClaro, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(descricao, fontSize = 13.sp, color = Urgente, lineHeight = 18.sp)
                    }
                    if (resultado.motivo == "limite_atingido") {
                        Spacer(modifier = Modifier.height(14.dp))
                        listOf(Triple("Bronze", "Até 10 profissionais/mês", "R$ 59,90"), Triple("Ouro", "Ilimitado", "R$ 99,90")).forEach { (nome, desc, preco) ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = VerdeClaro), border = androidx.compose.foundation.BorderStroke(1.dp, Verde.copy(alpha = 0.3f))) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("⭐ Plano $nome", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                                        Text(desc, fontSize = 11.sp, color = InkMuted)
                                    }
                                    Text(preco, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = AzulClaro), border = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.2f))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("💳 Pagamento avulso", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Azul)
                            Text("Taxa única para esta consulta específica.", fontSize = 11.sp, color = InkMuted)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { resultadoAcesso = null; onPagar() }, colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White), shape = RoundedCornerShape(8.dp)) {
                    Text("Ver planos", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { resultadoAcesso = null }) { Text("Agora não", color = InkMuted, fontSize = 13.sp) }
            },
        )
    }
}