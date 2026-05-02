package br.com.brasiltupi.conecta

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun PerfilProfissionalScreen(
    onVoltar:        () -> Unit,
    userId:          String = "",
    onKyc:           () -> Unit = {},
    // [FIX-UX] Callback de exclusão de conta — necessário para navegar após exclusão
    onContaExcluida: () -> Unit = {},
) {
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
    var kycStatus       by remember { mutableStateOf("") }
    val scope   = rememberCoroutineScope()
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
            try {
                val docs = buscarKycDocumentos(userId)
                kycStatus = docs.firstOrNull()?.status ?: "not_submitted"
            } catch (_: Exception) { }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
    ) {

        // ── Capa + Header ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            if (capaUrl != null) {
                AsyncImage(
                    model              = capaUrl,
                    contentDescription = "Capa",
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Azul, Verde)))
                )
            }

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

            TextButton(
                onClick  = onVoltar,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 36.dp),
            ) {
                Text("← Voltar", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp)
                    .offset(y = 32.dp)
            ) {
                Box(modifier = Modifier.size(72.dp)) {
                    if (fotoUrl != null) {
                        AsyncImage(
                            model              = fotoUrl,
                            contentDescription = "Foto de perfil",
                            modifier           = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(50))
                                .background(Azul),
                            contentScale       = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier         = Modifier
                                .fillMaxSize()
                                .background(Azul, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                nomeReal.split(" ").map { it[0] }.joinToString("").take(2),
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White,
                            )
                        }
                    }
                    Box(
                        modifier         = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                            .background(Verde, RoundedCornerShape(50))
                            .clickable { launcherFoto.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✏", fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // ── Nome, área, cidade e badges ───────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(nomeReal, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(areaReal, fontSize = 13.sp, color = InkMuted)
            Text("📍 $cidadeReal", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFDF3D8), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        "Profissional Certificado",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFFC49A2A),
                    )
                }

                if (isPMPReal) {
                    Box(
                        modifier = Modifier
                            .background(DouradoClaro, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("🏆 PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                    }
                }

                when (kycStatus) {
                    "approved" -> Box(
                        modifier = Modifier
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("✅ Verificado", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    "pending" -> Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF8E1), RoundedCornerShape(20.dp))
                            .clickable { onKyc() }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("⏳ Em análise", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                    }
                    "rejected", "not_submitted", "" -> Box(
                        modifier = Modifier
                            .background(SurfaceOff, RoundedCornerShape(20.dp))
                            .clickable { onKyc() }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text       = if (kycStatus == "rejected") "❌ Doc. rejeitado" else "🔓 Verificar",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color      = InkMuted,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Stats ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(vertical = 4.dp)
        ) {
            listOf(
                "--"            to "Atendimentos",
                "⭐ --"         to "Avaliação",
                "$credReal/100" to "Credibilidade",
            ).forEach { (num, label) ->
                Column(
                    modifier            = Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(num, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(label, fontSize = 10.sp, color = InkMuted)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        // ── Tabs ──────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                "perfil"    to "Meu Perfil",
                "seguranca" to "Segurança",
                "urgente"   to "Urgente",
            ).forEach { (id, label) ->
                TextButton(
                    onClick = { abaSelecionada = id },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = if (abaSelecionada == id) Verde else InkMuted,
                    ),
                ) {
                    Text(
                        label,
                        fontSize   = 13.sp,
                        fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        when (abaSelecionada) {
            "perfil" -> AbaPerfilProfissional(
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
            // [FIX-UX] Passar userId e onContaExcluida — guard de dono de conta
            //           e zona de perigo agora vivem dentro da aba
            "seguranca" -> AbaSegurancaProfissional(
                email           = emailReal,
                telefone        = telefoneReal,
                userId          = userId,
                onContaExcluida = onContaExcluida,
            )
            "urgente" -> AbaUrgenteProfissional(
                disponivelInicial = dispUrgenteReal,
                userId            = userId,
            )
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
) {
    var editando  by remember { mutableStateOf(false) }
    var nome      by remember { mutableStateOf(nomeInicial) }
    var descricao by remember { mutableStateOf(descricaoInicial) }
    var cidade    by remember { mutableStateOf(cidadeInicial) }
    val scope     = rememberCoroutineScope()

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                        OutlinedTextField(
                            value         = descricao,
                            onValueChange = { descricao = it },
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(8.dp),
                            minLines      = 3,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Verde,
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                            ),
                        )
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
                                    userId = userId,
                                    bio    = descricao,
                                    cidade = partesCidade.getOrNull(0)?.trim() ?: cidade,
                                    estado = partesCidade.getOrNull(1)?.trim() ?: "",
                                )
                            }
                        }
                        editando = false
                    }
                } else {
                    listOf(
                        "Nome"         to nome,
                        "Área"         to areaInicial,
                        "Cidade"       to cidade,
                        "Membro desde" to "--",
                    ).forEach { (label, valor) ->
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Dados profissionais", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "Tipo de conta"   to "Profissional",
                    "Conselho"        to conselho,
                    "Atendimentos"    to "--",
                    "Avaliação média" to "⭐ --",
                ).forEach { (label, valor) ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, fontSize = 13.sp, color = InkMuted)
                        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F0)),
            border   = BorderStroke(1.dp, Color(0xFFE8B832).copy(alpha = 0.4f)),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Programa PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                        Text("Faltam ${100 - credibilidade} pontos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Box(
                        modifier         = Modifier
                            .size(48.dp)
                            .background(Azul, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", fontSize = 20.sp, color = DouradoMedio, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress   = { credibilidade / 100f },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color      = DouradoMedio,
                    trackColor = SurfaceOff,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("$credibilidade/100 pontos de credibilidade", fontSize = 12.sp, color = InkMuted)
                Spacer(modifier = Modifier.height(14.dp))
                listOf(
                    "Prioridade nas buscas",
                    "Melhores comissões",
                    "Acesso antecipado",
                    "Selo visível",
                ).forEach { b ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(vertical = 3.dp),
                    ) {
                        Text("→", fontSize = 12.sp, color = Dourado)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(b, fontSize = 13.sp, color = InkSoft)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick  = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    enabled  = credibilidade >= 80,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Azul,
                        contentColor           = Color.White,
                        disabledContainerColor = SurfaceOff,
                        disabledContentColor   = InkMuted,
                    ),
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        if (credibilidade >= 80) "Candidatar-me ao PMP"
                        else "Disponível com 80 pontos ($credibilidade/80)",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── ABA: SEGURANÇA ────────────────────────────────────
// [FIX-UX] Assinatura atualizada: recebe userId e onContaExcluida.
//           Guard isDonoDoPerfilAtual controla visibilidade da zona de perigo.
@Composable
fun AbaSegurancaProfissional(
    email:           String  = "",
    telefone:        String  = "",
    // [FIX-UX] userId do perfil exibido — comparado com AuthRepository.userId
    userId:          String  = "",
    onContaExcluida: () -> Unit = {},
) {
    // [FIX-UX] Guard: botão só aparece quando o usuário logado é o dono do perfil.
    // Isso impede que admins ou visualizações externas exponham a opção de exclusão.
    val isDonoDoPerfilAtual = remember(userId) {
        userId.isNotEmpty() && userId == AuthRepository.userId
    }

    var mostrarDialogConfirmacao1 by remember { mutableStateOf(false) }
    var mostrarDialogConfirmacao2 by remember { mutableStateOf(false) }
    var excluindo                 by remember { mutableStateOf(false) }
    var erroExclusao              by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Card: Segurança da conta ──────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Segurança da conta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    Triple("E-mail",           email,        "Alterar"),
                    Triple("Senha",            "••••••••••", "Alterar"),
                    Triple("Telefone",         telefone,     "Alterar"),
                    Triple("Autenticação 2FA", "Desativado", "Ativar"),
                ).forEach { (label, valor, acao) ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(label, fontSize = 12.sp, color = InkMuted)
                            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                        TextButton(onClick = {}) {
                            Text(acao, color = Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }

        // ── Banner: dados protegidos ──────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(VerdeClaro, RoundedCornerShape(10.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("🔒", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Seus dados estão protegidos",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Verde,
                )
                Text(
                    "Todas as informações são criptografadas e nunca compartilhadas com terceiros.",
                    fontSize   = 12.sp,
                    color      = Verde.copy(alpha = 0.8f),
                    lineHeight = 17.sp,
                    modifier   = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ── [FIX-UX] Zona de perigo — visível SOMENTE para o dono da conta ──
        if (isDonoDoPerfilAtual) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = UrgenteClaro),
                border   = BorderStroke(1.dp, Urgente.copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier            = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Zona de perigo",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Urgente,
                        )
                    }
                    Text(
                        "A exclusão da conta remove permanentemente seus dados pessoais conforme a " +
                                "Lei Geral de Proteção de Dados (LGPD, Art. 18). Esta ação não pode ser desfeita.",
                        fontSize   = 13.sp,
                        color      = Urgente.copy(alpha = 0.85f),
                        lineHeight = 19.sp,
                    )
                    OutlinedButton(
                        onClick  = { mostrarDialogConfirmacao1 = true },
                        enabled  = !excluindo,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(8.dp),
                        border   = BorderStroke(1.dp, Urgente),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor         = Urgente,
                            disabledContentColor = Urgente.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            "Excluir minha conta",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                        )
                    }

                    // Mensagem de erro (exibida inline, abaixo do botão)
                    if (erroExclusao != null) {
                        LaunchedEffect(erroExclusao) {
                            kotlinx.coroutines.delay(5_000)
                            erroExclusao = null
                        }
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(erroExclusao!!, fontSize = 12.sp, color = Urgente, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
    }

    // ── [FIX-UX] Dialog passo 1 — aviso sobre consequências ──────────────
    if (mostrarDialogConfirmacao1) {
        AlertDialog(
            onDismissRequest = { mostrarDialogConfirmacao1 = false },
            title = {
                Text("Excluir conta?", fontWeight = FontWeight.Bold, color = Ink)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Esta ação é permanente e não pode ser desfeita. Ao confirmar:",
                        fontSize = 14.sp,
                        color    = Ink,
                    )
                    listOf(
                        "• Seu perfil será desativado imediatamente",
                        "• Todos os seus dados pessoais serão anonimizados em até 30 dias",
                        "• Atendimentos pendentes serão cancelados",
                        "• Saldos disponíveis serão processados conforme nossos Termos",
                    ).forEach { item ->
                        Text(item, fontSize = 13.sp, color = InkMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    mostrarDialogConfirmacao1 = false
                    mostrarDialogConfirmacao2 = true
                }) {
                    Text("Entendi, continuar", color = Urgente, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogConfirmacao1 = false }) {
                    Text("Cancelar", color = InkMuted)
                }
            },
            containerColor = Surface,
        )
    }

    // ── [FIX-UX] Dialog passo 2 — confirmação final (duplo opt-in LGPD) ──
    if (mostrarDialogConfirmacao2) {
        AlertDialog(
            onDismissRequest = { if (!excluindo) mostrarDialogConfirmacao2 = false },
            title = {
                Text("Tem certeza absoluta?", fontWeight = FontWeight.Bold, color = Urgente)
            },
            text = {
                Text(
                    "Esta é a última etapa. Ao confirmar, sua conta será excluída de forma permanente " +
                            "e seus dados serão removidos conforme a LGPD (Art. 18).",
                    fontSize = 14.sp,
                    color    = Ink,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        excluindo    = true
                        erroExclusao = null
                        scope.launch {
                            val ok = excluirContaAndroid()
                            if (ok) {
                                signOutAndroid()
                                mostrarDialogConfirmacao2 = false
                                onContaExcluida()
                            } else {
                                excluindo    = false
                                erroExclusao = "Não foi possível excluir a conta. Tente novamente ou contate o suporte."
                                mostrarDialogConfirmacao2 = false
                            }
                        }
                    },
                    enabled = !excluindo,
                    colors  = ButtonDefaults.buttonColors(containerColor = Urgente),
                ) {
                    if (excluindo) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (excluindo) "Excluindo..." else "Sim, excluir minha conta",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick  = { if (!excluindo) mostrarDialogConfirmacao2 = false },
                    enabled  = !excluindo,
                ) {
                    Text("Voltar", color = InkMuted)
                }
            },
            containerColor = Surface,
        )
    }
}

// ── ABA: URGENTE ──────────────────────────────────────
// Espelha a mesma lógica de AbaUrgenteDash:
//   — verifica aceite de termos ao carregar (verificarAceiteTermosUrgencia)
//   — se nunca aceitou: abre modal antes de ativar o switch
//   — se já aceitou: ativa direto
//   — dados de histórico: calculados a partir de consultas reais (sem mock)
@Composable
fun AbaUrgenteProfissional(
    disponivelInicial: Boolean = false,
    userId:            String  = "",
) {
    val scope = rememberCoroutineScope()

    var ativo             by remember { mutableStateOf(disponivelInicial) }
    var atualizando       by remember { mutableStateOf(false) }
    var erroToggle        by remember { mutableStateOf(false) }
    var mostrarTermos     by remember { mutableStateOf(false) }
    // jaAceitouTermos: false enquanto carrega, setado pelo LaunchedEffect
    var jaAceitouTermos   by remember { mutableStateOf(false) }
    var verificandoTermos by remember { mutableStateOf(true) }

    // Consultas reais para o histórico urgente
    var consultas         by remember { mutableStateOf<List<ConsultaProfissional>>(emptyList()) }

    LaunchedEffect(userId) {
        if (userId.isEmpty()) { verificandoTermos = false; return@LaunchedEffect }
        jaAceitouTermos   = verificarAceiteTermosUrgencia(userId)
        consultas         = buscarConsultasProfissional(userId)
        verificandoTermos = false
    }

    // Métricas de histórico urgente calculadas a partir de dados reais
    val urgentesTotais    = consultas.count { it.tipo.contains("rgente", ignoreCase = true) }
    val urgentesRealizadas = consultas.count {
        it.tipo.contains("rgente", ignoreCase = true) &&
                (it.status == "concluida" || it.status == "concluido")
    }
    val pontualidade   = if (urgentesTotais > 0) "${urgentesRealizadas * 100 / urgentesTotais}%" else "—"
    val descumprimentos = urgentesTotais - urgentesRealizadas

    // ── Modal de Termos (mesmo conteúdo da AbaUrgenteDash) ────────────
    // var aceite no escopo do Dialog (não dentro do Column filho) para
    // evitar reset por recomposição.
    var aceiteTermos by remember(mostrarTermos) { mutableStateOf(false) }

    if (mostrarTermos) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { mostrarTermos = false }) {
            Card(
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
                modifier = Modifier.heightIn(max = 560.dp),
            ) {
                Column(
                    modifier            = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("⚡ Termos de Prontidão Urgente", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        "Ao ativar a Área Urgente, você declara estar ciente de que:",
                        fontSize = 13.sp, color = InkMuted,
                    )
                    listOf(
                        "Devo responder ao chamado em até 45 minutos após receber a notificação.",
                        "A consulta urgente tem duração máxima de 15 minutos.",
                        "Não escolho horários — fico disponível de forma contínua enquanto o switch estiver ativo.",
                        "Atrasos ou descumprimentos reduzem minha credibilidade e podem suspender meu acesso à área urgente.",
                        "Sou o único responsável pelo serviço prestado. A plataforma é apenas o canal de conexão.",
                    ).forEach { texto ->
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .background(SurfaceWarm, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("•", fontSize = 14.sp, color = Azul, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(texto, fontSize = 12.sp, color = Ink, lineHeight = 17.sp)
                        }
                    }
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .background(
                                if (aceiteTermos) Verde.copy(alpha = 0.08f) else SurfaceOff,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked         = aceiteTermos,
                            onCheckedChange = { aceiteTermos = it },
                            colors          = CheckboxDefaults.colors(checkedColor = Verde),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Li, compreendi e aceito todos os termos acima.",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (aceiteTermos) Verde else Ink,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick  = { mostrarTermos = false },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                        ) {
                            Text("Cancelar", color = InkMuted)
                        }
                        Button(
                            onClick = {
                                if (!aceiteTermos) return@Button
                                mostrarTermos = false
                                ativo         = true
                                atualizando   = true
                                scope.launch {
                                    if (userId.isNotEmpty()) {
                                        gravarAceiteTermosUrgencia(userId)
                                        jaAceitouTermos = true
                                        val sucesso = atualizarDisponibilidadeUrgente(userId, true)
                                        atualizando = false
                                        if (!sucesso) { ativo = false; erroToggle = true }
                                    } else {
                                        atualizando = false
                                        ativo = false
                                    }
                                }
                            },
                            enabled  = aceiteTermos,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Verde),
                        ) {
                            Text("Ativar agora", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Card: Toggle de disponibilidade ───────────────────────────
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Área Urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "Quando ativo, você aparece para clientes que precisam de atendimento imediato.",
                            fontSize   = 13.sp,
                            color      = InkMuted,
                            lineHeight = 18.sp,
                            modifier   = Modifier.padding(top = 4.dp),
                        )
                    }
                    when {
                        verificandoTermos || atualizando -> CircularProgressIndicator(
                            modifier    = Modifier.size(24.dp),
                            color       = Verde,
                            strokeWidth = 2.dp,
                        )
                        else -> Switch(
                            checked         = ativo,
                            onCheckedChange = { novoValor ->
                                erroToggle = false
                                if (novoValor && !ativo) {
                                    // Ativar: exige termos se ainda não aceitou
                                    if (jaAceitouTermos) {
                                        val anterior = ativo
                                        ativo        = true
                                        atualizando  = true
                                        scope.launch {
                                            val ok = if (userId.isNotEmpty())
                                                atualizarDisponibilidadeUrgente(userId, true)
                                            else false
                                            atualizando = false
                                            if (!ok) { ativo = anterior; erroToggle = true }
                                        }
                                    } else {
                                        mostrarTermos = true
                                    }
                                } else if (!novoValor) {
                                    // Desativar: direto, sem modal
                                    val anterior = ativo
                                    ativo        = false
                                    atualizando  = true
                                    scope.launch {
                                        val ok = if (userId.isNotEmpty())
                                            atualizarDisponibilidadeUrgente(userId, false)
                                        else false
                                        atualizando = false
                                        if (!ok) { ativo = anterior; erroToggle = true }
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Verde,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .background(
                            if (ativo) VerdeClaro else UrgenteClaro,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (ativo) "🟢" else "🔴", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (ativo) "Você está disponível para consultas urgentes"
                        else "Você está indisponível para consultas urgentes",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (ativo) Verde else Urgente,
                    )
                }
                // Badge de conformidade: confirma que os termos foram aceitos
                if (jaAceitouTermos) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .background(Verde.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("✅", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Termos de prontidão aceitos neste dispositivo.",
                            fontSize = 11.sp, color = Verde,
                        )
                    }
                }
                if (erroToggle) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚠️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Falha na conexão. Estado revertido.", fontSize = 12.sp, color = Urgente)
                    }
                }
            }
        }

        // ── Card: Regras do Acordo ─────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Regras do Acordo de Prontidão", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                listOf(
                    Triple("⏱", "45 minutos",     "Tempo máximo para iniciar o atendimento."),
                    Triple("📋", "15 minutos",     "Duração máxima da consulta."),
                    Triple("⚠",  "Descumprimento", "Atrasos resultam em perda de credibilidade."),
                    Triple("🚫", "Reincidência",   "Suspensão do acesso à área urgente."),
                ).forEach { (icon, titulo, desc) ->
                    Row(
                        modifier          = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
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

        // ── Card: Histórico urgente (dados reais) ─────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Seu histórico urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                if (urgentesTotais == 0) {
                    Text(
                        "Nenhuma urgência registrada ainda.",
                        fontSize  = 13.sp, color = InkMuted,
                        modifier  = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple("$urgentesRealizadas", "Urgentes\nrealizadas",  Verde),
                            Triple(pontualidade,           "Taxa de\npontualidade", Azul),
                            Triple("$descumprimentos",    "Descum-\nprimentos",    if (descumprimentos == 0) Dourado else Urgente),
                        ).forEach { (num, label, cor) ->
                            Column(
                                modifier            = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(num, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cor)
                                Text(
                                    label,
                                    fontSize   = 10.sp,
                                    color      = InkMuted,
                                    lineHeight = 14.sp,
                                    modifier   = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .background(
                                if (descumprimentos == 0) VerdeClaro else UrgenteClaro,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (descumprimentos == 0) "✓" else "⚠️",
                            fontSize   = 14.sp,
                            color      = if (descumprimentos == 0) Verde else Urgente,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (descumprimentos == 0) "Histórico limpo — acesso integral à área urgente."
                            else "$descumprimentos descumprimento(s) registrado(s).",
                            fontSize = 12.sp,
                            color    = if (descumprimentos == 0) Verde else Urgente,
                        )
                    }
                }
            }
        }
    }
}