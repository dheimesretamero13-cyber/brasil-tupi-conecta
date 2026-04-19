package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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

// ── DADOS MOCK (fallback) ─────────────────────────────
data class ProfissionalPMP(
    val id: Int,
    val iniciais: String,
    val nome: String,
    val area: String,
    val cidade: String,
    val avaliacao: Double,
    val atendimentos: Int,
    val disponivelUrgente: Boolean,
    val valorNormal: Int,
    val valorUrgente: Int?,
    val conselho: String,
    val descricao: String,
    val especialidades: List<String>,
)

val profissionaisMock = listOf(
    ProfissionalPMP(1, "MC", "Dra. Mariana Costa", "Psicologia e Terapia", "São Paulo, SP", 5.0, 63, false, 120, null, "CRP 12.345/SP", "Psicóloga clínica com foco em ansiedade e burnout.", listOf("Ansiedade", "Burnout", "Terapia Cognitiva")),
    ProfissionalPMP(2, "RS", "Dr. Rafael Souza", "Direito e Jurídico", "Campinas, SP", 4.9, 31, true, 90, 150, "OAB 98.765/SP", "Advogado especialista em direito do consumidor e trabalhista.", listOf("Consumidor", "Trabalhista", "Contratos")),
    ProfissionalPMP(3, "SR", "Dra. Sandra Reis", "Finanças e Contabilidade", "Rio de Janeiro, RJ", 4.9, 55, true, 100, 160, "CFC 54.321/RJ", "Contadora especializada em MEI e planejamento tributário.", listOf("MEI", "Tributário", "Contabilidade")),
    ProfissionalPMP(4, "CH", "Dr. Carlos Henrique", "Saúde e Bem-estar", "São Paulo, SP", 4.8, 47, true, 80, 120, "CRM 45.231/SP", "Médico clínico geral com 12 anos de experiência.", listOf("Clínica Geral", "Preventiva", "Check-up")),
    ProfissionalPMP(5, "PL", "Eng. Patricia Lima", "Engenharia e Tecnologia", "Belo Horizonte, MG", 4.7, 28, false, 110, null, "CREA 33.210/MG", "Engenheira civil especializada em laudos e perícias.", listOf("Laudos", "Perícia", "Projetos")),
    ProfissionalPMP(6, "GT", "Dr. Gustavo Torres", "Educação e Tutoria", "Porto Alegre, RS", 4.8, 42, true, 70, 100, "", "Tutor especializado em matemática e vestibulares.", listOf("Matemática", "Física", "Vestibular")),
)

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun BuscaScreen(onVoltar: () -> Unit) {
    var busca by remember { mutableStateOf("") }
    var somenteUrgente by remember { mutableStateOf(false) }
    var profSelecionado by remember { mutableStateOf<ProfissionalPMP?>(null) }
    var agendando by remember { mutableStateOf<Pair<ProfissionalPMP, String>?>(null) }
    var profissionaisDB by remember { mutableStateOf<List<ProfissionalPMP>>(emptyList()) }
    var loadingDB by remember { mutableStateOf(true) }

    // Carregar do Supabase
    LaunchedEffect(somenteUrgente) {
        loadingDB = true
        val dados = getProfissionaisPMPAndroid(somenteUrgente, "")
        profissionaisDB = if (dados.isNotEmpty()) {
            dados.map { p ->
                ProfissionalPMP(
                    id = p.id.hashCode(),
                    iniciais = p.perfis?.nome?.split(" ")?.map { it[0] }?.joinToString("")?.take(2) ?: "XX",
                    nome = p.perfis?.nome ?: "Profissional",
                    area = p.area,
                    cidade = "${p.perfis?.cidade ?: ""}, ${p.perfis?.estado ?: ""}",
                    avaliacao = 5.0,
                    atendimentos = p.credibilidade / 2,
                    disponivelUrgente = p.disponivel_urgente,
                    valorNormal = p.valor_normal,
                    valorUrgente = if (p.disponivel_urgente) p.valor_urgente else null,
                    conselho = listOfNotNull(p.conselho, p.numero_conselho).joinToString(" "),
                    descricao = p.descricao ?: "",
                    especialidades = listOf(p.area),
                )
            }
        } else {
            profissionaisMock
        }
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
            .background(SurfaceWarm)
    ) {
        // Header
        Column(
            modifier = Modifier
                .background(Azul)
                .padding(horizontal = 24.dp)
                .padding(top = 52.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onVoltar) {
                    Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
            Text(
                "Profissionais PMP",
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Text(
                "Verificados · Avaliados · Confiáveis",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por nome, área ou cidade...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DouradoMedio,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = DouradoMedio
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .background(
                        if (somenteUrgente) Urgente.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { somenteUrgente = !somenteUrgente }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Disponível agora (urgente)",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (somenteUrgente) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = somenteUrgente,
                    onCheckedChange = { somenteUrgente = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Urgente
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VerdeClaro)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("✓ 10+ atendimentos", "✓ Zero negativos", "✓ Plano PMP ativo").forEach {
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Verde)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (loadingDB) "Carregando..."
                else "${resultado.size} profissional${if (resultado.size != 1) "is" else ""} encontrado${if (resultado.size != 1) "s" else ""}",
                fontSize = 13.sp, color = InkMuted
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
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🔍", fontSize = 48.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Nenhum profissional encontrado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Tente outros termos ou remova os filtros.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(resultado) { prof ->
                    CardProfissional(prof = prof, onClick = { profSelecionado = prof })
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ── CARD PROFISSIONAL ─────────────────────────────────
@Composable
fun CardProfissional(prof: ProfissionalPMP, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Azul, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(prof.iniciais, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(prof.nome, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(prof.area, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                    Text("📍 ${prof.cidade}", fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFDF3D8), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("🏆 PMP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC49A2A))
                    }
                    if (prof.disponivelUrgente) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .background(UrgenteClaro, RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("⚡ Urgente", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Urgente)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("⭐ ${prof.avaliacao}", fontSize = 12.sp, color = InkSoft, fontWeight = FontWeight.SemiBold)
                Text("·", color = InkMuted, fontSize = 12.sp)
                Text("${prof.atendimentos} atendimentos", fontSize = 12.sp, color = InkMuted)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                prof.especialidades.take(2).forEach { esp ->
                    Box(
                        modifier = Modifier
                            .background(AzulClaro, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(esp, fontSize = 10.sp, color = Azul, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Ver perfil →", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── PERFIL PÚBLICO ────────────────────────────────────
@Composable
fun PerfilPublicoScreen(
    prof: ProfissionalPMP,
    onVoltar: () -> Unit,
    onAgendar: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .background(Azul)
                    .padding(horizontal = 24.dp)
                    .padding(top = 52.dp, bottom = 28.dp)
            ) {
                TextButton(onClick = onVoltar) {
                    Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(vertical = 4.dp),
            ) {
                listOf(
                    "⭐ ${prof.avaliacao}" to "Avaliação",
                    "${prof.atendimentos}" to "Atendimentos",
                    "0" to "Negativos",
                ).forEach { (num, label) ->
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                        Box(
                            modifier = Modifier
                                .background(AzulClaro, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(esp, fontSize = 11.sp, color = Azul, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            HorizontalDivider(color = SurfaceOff)
        }

        item {
            Column(
                modifier = Modifier
                    .background(VerdeClaro)
                    .padding(20.dp)
            ) {
                Text("Por que este profissional aparece aqui?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "Mínimo de 10 atendimentos" to "${prof.atendimentos} realizados",
                    "Zero avaliações negativas" to "Histórico 100% positivo",
                    "Plano PMP ativo" to "Mensalidade + porcentagem"
                ).forEach { (titulo, detalhe) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Verde, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
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
                OpcaoConsulta(
                    titulo = "Consulta normal",
                    descricao = "Agendada com antecedência · 15 minutos",
                    preco = "R$ ${prof.valorNormal}",
                    corPreco = Verde,
                    onClick = { onAgendar("normal") }
                )
                if (prof.valorUrgente != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OpcaoConsulta(
                        titulo = "⚡ Consulta urgente",
                        descricao = "Resposta em até 45 minutos · 15 minutos",
                        preco = "R$ ${prof.valorUrgente}",
                        corPreco = Urgente,
                        urgente = true,
                        onClick = { onAgendar("urgente") }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VerdeClaro, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(texto, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cor)
    }
}

@Composable
fun OpcaoConsulta(
    titulo: String,
    descricao: String,
    preco: String,
    corPreco: Color,
    urgente: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (urgente) UrgenteClaro else SurfaceOff
        ),
        border = if (urgente) androidx.compose.foundation.BorderStroke(1.dp, Urgente.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
    prof: ProfissionalPMP,
    tipo: String,
    onVoltar: () -> Unit,
    onConcluido: () -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var etapa by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        TextButton(onClick = onVoltar) {
            Text("← Voltar", color = InkMuted, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Azul, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(prof.iniciais, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(prof.nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        if (tipo == "urgente") "⚡ Urgente · R$ ${prof.valorUrgente}" else "Normal · R$ ${prof.valorNormal}",
                        fontSize = 12.sp,
                        color = if (tipo == "urgente") Urgente else InkMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (etapa == 1) {
            Text("Criar conta gratuita", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "Você será cadastrado como cliente automaticamente.",
                fontSize = 13.sp, color = InkMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
            CampoTexto("Nome completo *", nome, { nome = it }, "Seu nome completo")
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("E-mail *", email, { email = it }, "seu@email.com", keyboardType = KeyboardType.Email)
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("Telefone *", telefone, { telefone = it }, "(00) 00000-0000", keyboardType = KeyboardType.Phone)
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("Criar senha *", senha, { senha = it }, "Mínimo 6 caracteres", senha = true)
            Spacer(modifier = Modifier.height(20.dp))
            BotaoProximo("Continuar →") { etapa = 2 }
        } else {
            Text("Confirmar agendamento", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    RevisaoItem("Profissional", prof.nome)
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Tipo", if (tipo == "urgente") "⚡ Urgente" else "Normal")
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Valor", if (tipo == "urgente") "R$ ${prof.valorUrgente}" else "R$ ${prof.valorNormal}")
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Sua conta", email.ifEmpty { "—" })
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
                Text("🔒", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sem cobranças antecipadas.", fontSize = 12.sp, color = Verde)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConcluido,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tipo == "urgente") Urgente else Verde,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    if (tipo == "urgente") "⚡ Confirmar agora" else "Confirmar agendamento",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}