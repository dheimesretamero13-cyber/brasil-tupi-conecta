package br.com.brasiltupi.conecta

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ── DADOS MOCK ────────────────────────────────────────
data class DadosProfissional(
    val nome: String,
    val area: String,
    val tipo: String,
    val conselho: String,
    val cidade: String,
    val credibilidade: Int,
    val isPMP: Boolean,
    val atendimentosTotal: Int,
    val avaliacaoMedia: Double,
    val disponivelUrgente: Boolean,
    val membroDesde: String,
    val descricao: String,
    val email: String,
    val telefone: String,
)
// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun PerfilProfissionalScreen(onVoltar: () -> Unit, userId: String = "") {
    var carregando      by remember { mutableStateOf(userId.isNotEmpty()) }
    var abaSelecionada  by remember { mutableStateOf("perfil") }
    var fotoUrl         by remember { mutableStateOf<String?>(null) }
    var capaUrl         by remember { mutableStateOf<String?>(null) }
    var nomeReal        by remember { mutableStateOf("") }
    var areaReal        by remember { mutableStateOf("") }
    var cidadeReal      by remember { mutableStateOf("") }
    var emailReal       by remember { mutableStateOf("") }
    var telefoneReal    by remember { mutableStateOf("") }
    var descricaoReal   by remember { mutableStateOf("") }
    var conselhoReal    by remember { mutableStateOf("") }
    var credReal        by remember { mutableStateOf(0) }
    var isPMPReal       by remember { mutableStateOf(false) }
    var dispUrgenteReal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        if (userId.isEmpty()) { carregando = false; return@LaunchedEffect }
        carregando = true
        try {
            val perfil = getPerfilAndroid(userId)
            if (perfil != null) {
                fotoUrl      = perfil.foto_url
                capaUrl      = perfil.capa_url
                nomeReal     = perfil.nome
                emailReal    = perfil.email
                telefoneReal = perfil.telefone.orEmpty()
                cidadeReal   = if (!perfil.cidade.isNullOrEmpty() && !perfil.estado.isNullOrEmpty())
                    "${perfil.cidade}, ${perfil.estado}"
                else perfil.cidade.orEmpty()
            }
            val meu = getMeuPerfilProfissional(userId)
            if (meu != null) {
                areaReal        = meu.area
                descricaoReal   = meu.descricao.orEmpty()
                conselhoReal    = meu.conselho.orEmpty()
                credReal        = meu.credibilidade
                isPMPReal       = meu.is_pmp
                dispUrgenteReal = meu.disponivel_urgente
            }
        } finally {
            carregando = false
        }
    }

    if (userId.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Usuário não identificado.", color = InkMuted, fontSize = 14.sp)
        }
        return
    }
    if (carregando) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Verde)
        }
        return
    }

    val launcherFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val url = uploadImagemSupabase(context, it, "avatares", userId)
                if (url != null) {
                    fotoUrl = url
                    salvarFotoPerfilAndroid(userId, fotoUrl = url, capaUrl = capaUrl)
                }
            }
        }
    }

    val launcherCapa = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val url = uploadImagemSupabase(context, it, "capas", userId)
                if (url != null) {
                    capaUrl = url
                    salvarFotoPerfilAndroid(userId, fotoUrl = fotoUrl, capaUrl = url)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(SurfaceWarm).verticalScroll(rememberScrollState())) {
        // Capa + Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Capa
            if (capaUrl != null) {
                AsyncImage(
                    model = capaUrl,
                    contentDescription = "Capa",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Azul, Verde)))
                )
            }

            // Botão editar capa
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable { launcherCapa.launch("image/*") }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("📷 Editar capa", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            // Voltar
            TextButton(
                onClick = onVoltar,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 36.dp)
            ) {
                Text("← Voltar", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }

            // Avatar sobre a capa
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp)
                    .offset(y = 32.dp)
            ) {
                Box(modifier = Modifier.size(72.dp)) {
                    if (fotoUrl != null) {
                        AsyncImage(
                            model = fotoUrl,
                            contentDescription = "Foto de perfil",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(50))
                                .background(Azul),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Azul, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                nomeReal.split(" ").map { it[0] }.joinToString("").take(2),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }
                    // Botão editar foto
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                            .background(Verde, RoundedCornerShape(50))
                            .clickable { launcherFoto.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✏", fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Nome e info
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(nomeReal, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(areaReal, fontSize = 13.sp, color = InkMuted)
            Text("📍 $cidadeReal", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.background(Color(0xFFFDF3D8), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("Profissional Certificado", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC49A2A))
                }
                if (isPMPReal) {
                    Box(modifier = Modifier.background(DouradoClaro, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text("🏆 PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stats
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(vertical = 4.dp)) {
            listOf(
                "--" to "Atendimentos",
                "⭐ --" to "Avaliação",
                "$credReal/100" to "Credibilidade",
            ).forEach { (num, label) ->
                Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(num, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(label, fontSize = 10.sp, color = InkMuted)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        // Tabs
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("perfil" to "Meu Perfil", "seguranca" to "Segurança", "urgente" to "Urgente").forEach { (id, label) ->
                TextButton(onClick = { abaSelecionada = id }, colors = ButtonDefaults.textButtonColors(contentColor = if (abaSelecionada == id) Verde else InkMuted)) {
                    Text(label, fontSize = 13.sp, fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        when (abaSelecionada) {
            "perfil"    -> AbaPerfilProfissional(
                nomeInicial      = nomeReal,
                areaInicial      = areaReal,
                cidadeInicial    = cidadeReal,
                descricaoInicial = descricaoReal,
                conselho         = conselhoReal,
                credibilidade    = credReal,
                isPMP            = isPMPReal,
                userId           = userId,
                telefoneReal     = telefoneReal,
            )
            "seguranca" -> AbaSegurancaProfissional(email = emailReal, telefone = telefoneReal)
            "urgente"   -> AbaUrgenteProfissional(disponivelInicial = dispUrgenteReal, userId = userId)
        }
    }
}

// ── ABA: PERFIL ───────────────────────────────────────
@Composable
fun AbaPerfilProfissional(
    nomeInicial:      String  = "",
    areaInicial:      String  = "",
    cidadeInicial:    String  = "",
    descricaoInicial: String  = "",
    conselho:         String  = "",
    credibilidade:    Int     = 0,
    isPMP:            Boolean = false,
    userId:           String  = "",
    telefoneReal:     String  = "",
)
{
    var editando  by remember { mutableStateOf(false) }
    var nome      by remember { mutableStateOf(nomeInicial) }
    var descricao by remember { mutableStateOf(descricaoInicial) }
    var cidade    by remember { mutableStateOf(cidadeInicial) }
    val scope     = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dados do perfil", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    TextButton(onClick = { editando = !editando }) {
                        Text(if (editando) "Cancelar" else "✏ Editar", color = Verde, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (editando) {
                    CampoTexto("Nome de exibição", nome, { nome = it }, "Seu nome")
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Descrição profissional", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(modifier = Modifier.height(5.dp))
                        OutlinedTextField(value = descricao, onValueChange = { descricao = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), minLines = 3, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    CampoTexto("Cidade / Estado", cidade, { cidade = it }, "Sua cidade")
                    Spacer(modifier = Modifier.height(16.dp))
                    BotaoProximo("Salvar alterações") {
                        scope.launch {
                            if (userId.isNotEmpty()) {
                                salvarDadosPerfilAndroid(userId, nome, telefoneReal)
                                val partesCidade = cidade.split(",")
                                salvarBioProfissionalAndroid(
                                    userId  = userId,
                                    bio     = descricao,
                                    cidade  = partesCidade.getOrNull(0)?.trim() ?: cidade,
                                    estado  = partesCidade.getOrNull(1)?.trim() ?: "",
                                )
                            }
                        }
                        editando = false
                    }
                } else {
                    listOf("Nome" to nome, "Área" to areaInicial, "Cidade" to cidade, "Membro desde" to "--").forEach { (label, valor) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 13.sp, color = InkMuted)
                            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Descrição", fontSize = 13.sp, color = InkMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(descricao, fontSize = 13.sp, color = InkSoft, lineHeight = 19.sp)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Dados profissionais", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf("Tipo de conta" to "Profissional", "Conselho" to conselho, "Atendimentos" to "--", "Avaliação média" to "⭐ --").forEach { (label, valor) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 13.sp, color = InkMuted)
                        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F0)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8B832).copy(alpha = 0.4f))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Programa PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                        Text("Faltam ${100 - credibilidade} pontos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Box(modifier = Modifier.size(48.dp).background(Azul, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Text("✓", fontSize = 20.sp, color = DouradoMedio, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(progress = { credibilidade / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp), color = DouradoMedio, trackColor = SurfaceOff)
                Spacer(modifier = Modifier.height(6.dp))
                Text("$credibilidade/100 pontos de credibilidade", fontSize = 12.sp, color = InkMuted)
                Spacer(modifier = Modifier.height(14.dp))
                listOf("Prioridade nas buscas", "Melhores comissões", "Acesso antecipado", "Selo visível").forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text("→", fontSize = 12.sp, color = Dourado)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(b, fontSize = 13.sp, color = InkSoft)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(46.dp), enabled = credibilidade >= 80, colors = ButtonDefaults.buttonColors(containerColor = Azul, contentColor = Color.White, disabledContainerColor = SurfaceOff, disabledContentColor = InkMuted), shape = RoundedCornerShape(8.dp)) {
                    Text(if (credibilidade >= 80) "Candidatar-me ao PMP" else "Disponível com 80 pontos ($credibilidade/80)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── ABA: SEGURANÇA ────────────────────────────────────
@Composable
fun AbaSegurancaProfissional(
    email:    String = "",
    telefone: String = "",
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Segurança da conta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(Triple("E-mail", email, "Alterar"), Triple("Senha", "••••••••••", "Alterar"), Triple("Telefone", telefone, "Alterar"), Triple("Autenticação 2FA", "Desativado", "Ativar")).forEach { (label, valor, acao) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(label, fontSize = 12.sp, color = InkMuted)
                            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                        TextButton(onClick = {}) { Text(acao, color = Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(10.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
            Text("🔒", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Seus dados estão protegidos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                Text("Todas as informações são criptografadas e nunca compartilhadas com terceiros.", fontSize = 12.sp, color = Verde.copy(alpha = 0.8f), lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ── ABA: URGENTE ──────────────────────────────────────
@Composable
fun AbaUrgenteProfissional(
    disponivelInicial: Boolean = false,
    userId:            String  = "",
) {
    var ativo      by remember { mutableStateOf(disponivelInicial) }
    var atualizando by remember { mutableStateOf(false) }
    val scope      = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Área Urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Quando ativo, você aparece para clientes que precisam de atendimento imediato.", fontSize = 13.sp, color = InkMuted, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (atualizando) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Verde, strokeWidth = 2.dp)
                    } else {
                        Switch(
                            checked = ativo,
                            onCheckedChange = { novoValor ->
                                val anterior = ativo
                                ativo = novoValor
                                atualizando = true
                                scope.launch {
                                    val ok = if (userId.isNotEmpty())
                                        atualizarDisponibilidadeUrgente(userId, novoValor)
                                    else false
                                    atualizando = false
                                    if (!ok) ativo = anterior
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Verde)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().background(if (ativo) VerdeClaro else UrgenteClaro, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ativo) "🟢" else "🔴", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (ativo) "Você está disponível para consultas urgentes" else "Você está indisponível para consultas urgentes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (ativo) Verde else Urgente)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Regras do Acordo de Prontidão", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                listOf("⏱" to "45 minutos" to "Tempo máximo para iniciar o atendimento.", "📋" to "15 minutos" to "Duração máxima da consulta.", "⚠" to "Descumprimento" to "Atrasos resultam em perda de credibilidade.", "🚫" to "Reincidência" to "Suspensão do acesso à área urgente.").forEach { (iconTitulo, desc) ->
                    val (icon, titulo) = iconTitulo
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text(icon, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(desc, fontSize = 12.sp, color = InkMuted, lineHeight = 17.sp)
                        }
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Seu histórico urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("3" to "Urgentes\nrealizadas" to Verde, "100%" to "Taxa de\npontualidade" to Azul, "0" to "Descum-\nprimenetos" to Dourado).forEach { (numLabel, cor) ->
                        val (num, label) = numLabel
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(num, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cor)
                            Text(label, fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", fontSize = 14.sp, color = Verde, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Histórico limpo — acesso integral à área urgente.", fontSize = 12.sp, color = Verde)
                }
            }
        }
    }
}