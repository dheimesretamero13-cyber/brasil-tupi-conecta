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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun PerfilClienteScreen(
    onVoltar:   () -> Unit,
    userId:     String = "",
    onReferral: () -> Unit = {},   // ← Fase 4.2
) {
    var abaSelecionada      by remember { mutableStateOf("perfil") }
    var fotoUrl             by remember { mutableStateOf<String?>(null) }
    var nomeReal            by remember { mutableStateOf("") }
    var emailReal           by remember { mutableStateOf("") }
    var cpfReal             by remember { mutableStateOf("") }
    var telefoneReal        by remember { mutableStateOf("") }
    var cidadeReal          by remember { mutableStateOf("") }
    var estadoReal          by remember { mutableStateOf("") }
    var membroDesde         by remember { mutableStateOf("--") }
    var consultasRealizadas by remember { mutableStateOf(0) }
    var consultasAgendadas  by remember { mutableStateOf(0) }
    var avaliacaoMedia      by remember { mutableStateOf<Double?>(null) }
    var carregando          by remember { mutableStateOf(true) }

    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            val perfil = getPerfilAndroid(userId)
            if (perfil != null) {
                fotoUrl      = perfil.foto_url
                nomeReal     = perfil.nome
                emailReal    = perfil.email
                cpfReal      = perfil.cpf      ?: ""
                telefoneReal = perfil.telefone ?: ""
                cidadeReal   = perfil.cidade   ?: ""
                estadoReal   = perfil.estado   ?: ""
                membroDesde  = formatarMembroDesde(perfil.criadoEm)
            }
            val todasConsultas  = buscarConsultasCliente(userId)
            consultasRealizadas = todasConsultas.count { it.status in listOf("concluida", "concluido") }
            consultasAgendadas  = todasConsultas.count { it.status in listOf("agendada", "agendado") }
            val notasValidas    = todasConsultas.map { it.avaliacao }.filter { it > 0 }
            avaliacaoMedia      = if (notasValidas.isNotEmpty()) notasValidas.average() else null
        }
        carregando = false
    }

    val launcherFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val url = uploadImagemSupabase(context, it, "avatares", userId)
                if (url != null) {
                    fotoUrl = url
                    salvarFotoPerfilAndroid(userId, fotoUrl = url)
                }
            }
        }
    }

    val iniciais = if (nomeReal.isNotEmpty())
        nomeReal.split(" ").map { it[0] }.joinToString("").take(2)
    else "..."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Verde)
                .padding(horizontal = 24.dp)
                .padding(top = 52.dp, bottom = 28.dp)
        ) {
            Column {
                TextButton(onClick = onVoltar) {
                    Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(72.dp)) {
                        if (fotoUrl != null) {
                            AsyncImage(
                                model              = fotoUrl,
                                contentDescription = "Foto de perfil",
                                modifier           = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(50))
                                    .background(Verde),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier         = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(iniciais, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                                .background(Color.White, RoundedCornerShape(50))
                                .clickable { launcherFoto.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✏", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            nomeReal.ifEmpty { "Carregando..." },
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                        )
                        Text("Cliente", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                        if (cidadeReal.isNotEmpty()) {
                            Text(
                                "📍 $cidadeReal${if (estadoReal.isNotEmpty()) ", $estadoReal" else ""}",
                                fontSize = 12.sp,
                                color    = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        if (membroDesde == "--") "Carregando..." else "Membro desde $membroDesde",
                        fontSize   = 11.sp,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // ── Stats ─────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(vertical = 4.dp)) {
            Column(
                modifier            = Modifier.weight(1f).padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (carregando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Verde, strokeWidth = 2.dp)
                } else {
                    Text("$consultasRealizadas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                Text("Consultas\nrealizadas", fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp)
            }
            Column(
                modifier            = Modifier.weight(1f).padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$consultasAgendadas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Consultas\nagendadas", fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp)
            }
            Column(
                modifier            = Modifier.weight(1f).padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (avaliacaoMedia != null) "⭐ ${"%.1f".format(avaliacaoMedia)}" else "⭐ --",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Ink,
                )
                Text("Média das\nconsultas", fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp)
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
                "endereco"  to "Endereço",
                "seguranca" to "Segurança",
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
            "perfil" -> AbaPerfilCliente(
                fotoUrl         = fotoUrl,
                onEditarFoto    = { launcherFoto.launch("image/*") },
                nomeInicial     = nomeReal,
                email           = emailReal,
                cpf             = cpfReal,
                telefoneInicial = telefoneReal,
                membroDesde     = membroDesde,
                userId          = userId,
                onReferral      = onReferral,   // ← Fase 4.2
            )
            "endereco"  -> AbaEnderecoCliente(
                cidadeInicial = cidadeReal,
                estadoInicial = estadoReal,
                userId        = userId,
            )
            "seguranca" -> AbaSegurancaCliente(email = emailReal, telefone = telefoneReal)
        }
    }
}

// ── ABA: PERFIL ───────────────────────────────────────
@Composable
fun AbaPerfilCliente(
    fotoUrl:         String?  = null,
    onEditarFoto:    () -> Unit = {},
    nomeInicial:     String   = "",
    email:           String   = "",
    cpf:             String   = "",
    telefoneInicial: String   = "",
    membroDesde:     String   = "--",
    userId:          String   = "",
    onReferral:      () -> Unit = {},   // ← Fase 4.2
) {
    var editando by remember { mutableStateOf(false) }
    var nome     by remember(nomeInicial) { mutableStateOf(nomeInicial) }
    var telefone by remember(telefoneInicial) { mutableStateOf(telefoneInicial) }
    val scope    = rememberCoroutineScope()
    val iniciais = nomeInicial.split(" ").mapNotNull { it.firstOrNull()?.toString() }.joinToString("").take(2)

    Column(
        modifier            = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Card dados pessoais ───────────────────────────────────────────
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
                    Text("Dados pessoais", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    TextButton(onClick = { editando = !editando }) {
                        Text(if (editando) "Cancelar" else "✏ Editar", color = Verde, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (editando) {
                    CampoTexto("Nome completo", nome, { nome = it }, "Seu nome")
                    Spacer(modifier = Modifier.height(12.dp))
                    CampoTexto("Telefone / WhatsApp", telefone, { telefone = it }, "(00) 00000-0000")
                    Spacer(modifier = Modifier.height(16.dp))
                    BotaoProximo("Salvar alterações") {
                        scope.launch {
                            if (userId.isNotEmpty()) salvarDadosPerfilAndroid(userId, nome, telefone)
                        }
                        editando = false
                    }
                } else {
                    listOf(
                        "Nome"         to nome.ifEmpty { "--" },
                        "E-mail"       to email.ifEmpty { "--" },
                        "CPF"          to cpf.ifEmpty { "--" },
                        "Telefone"     to telefone.ifEmpty { "--" },
                        "Membro desde" to membroDesde,
                    ).forEach { (label, valor) ->
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, fontSize = 13.sp, color = InkMuted)
                            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                }
            }
        }

        // ── Card foto de perfil ───────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp)) {
                    if (fotoUrl != null) {
                        AsyncImage(
                            model              = fotoUrl,
                            contentDescription = null,
                            modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(50)),
                            contentScale       = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier         = Modifier
                                .fillMaxSize()
                                .background(VerdeClaro, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                iniciais.ifEmpty { "?" },
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Verde,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Foto de perfil", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("JPG ou PNG, máximo 5MB", fontSize = 12.sp, color = InkMuted)
                }
                TextButton(onClick = onEditarFoto) {
                    Text("Alterar", color = Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Card Indique e Ganhe — Fase 4.2 ──────────────────────────────
        Card(
            onClick   = onReferral,
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(12.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border    = androidx.compose.foundation.BorderStroke(
                1.dp, Color(0xFFC49A2A).copy(alpha = 0.4f)
            ),
        ) {
            Row(
                modifier          = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("🎁", fontSize = 32.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Indique e Ganhe",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF856404),
                    )
                    Text(
                        "Ganhe R\$20 por amigo indicado após a primeira consulta.",
                        fontSize    = 12.sp,
                        color       = Color(0xFF856404).copy(alpha = 0.8f),
                        lineHeight  = 17.sp,
                    )
                }
                Text("→", fontSize = 18.sp, color = Color(0xFFC49A2A), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── ABA: ENDEREÇO ─────────────────────────────────────
@Composable
fun AbaEnderecoCliente(
    cidadeInicial: String = "",
    estadoInicial: String = "",
    userId:        String = "",
) {
    var editando by remember { mutableStateOf(false) }
    var cidade   by remember(cidadeInicial) { mutableStateOf(cidadeInicial) }
    var estado   by remember(estadoInicial) { mutableStateOf(estadoInicial) }
    var salvando by remember { mutableStateOf(false) }
    var erro     by remember { mutableStateOf(false) }
    val scope    = rememberCoroutineScope()

    Column(
        modifier            = Modifier.fillMaxWidth().padding(20.dp),
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
                    Text("Localização", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    TextButton(onClick = { editando = !editando; erro = false }) {
                        Text(if (editando) "Cancelar" else "✏ Editar", color = Verde, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (editando) {
                    CampoTexto("Cidade", cidade, { cidade = it }, "Sua cidade")
                    Spacer(modifier = Modifier.height(12.dp))
                    CampoTexto("Estado (UF)", estado, { estado = it }, "Ex: SP")
                    if (erro) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Erro ao salvar. Tente novamente.", fontSize = 12.sp, color = Urgente)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                salvando = true
                                erro     = false
                                val ok   = if (userId.isNotEmpty()) {
                                    salvarBioProfissionalAndroid(userId, "", cidade, estado)
                                } else false
                                salvando = false
                                if (ok) editando = false else erro = true
                            }
                        },
                        enabled  = !salvando,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                        shape    = RoundedCornerShape(8.dp),
                    ) {
                        if (salvando) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Salvar localização", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    listOf(
                        "Cidade" to cidade.ifEmpty { "--" },
                        "Estado" to estado.ifEmpty { "--" },
                    ).forEach { (label, valor) ->
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, fontSize = 13.sp, color = InkMuted)
                            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                }
            }
        }
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(AzulClaro, RoundedCornerShape(10.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("ℹ", fontSize = 16.sp, color = Azul)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Sua localização é usada para encontrar profissionais próximos a você. Não é exibida publicamente.",
                fontSize   = 12.sp,
                color      = Azul,
                lineHeight = 17.sp,
            )
        }
    }
}

// ── ABA: SEGURANÇA ────────────────────────────────────
@Composable
fun AbaSegurancaCliente(
    email:           String = "",
    telefone:        String = "",
    userId:          String = "",
    onContaExcluida: () -> Unit = {},
) {
    val isDonoDoPerfilAtual = remember(userId) {
        userId.isNotEmpty() && userId == AuthRepository.userId
    }

    var mostrarDialog1 by remember { mutableStateOf(false) }
    var mostrarDialog2 by remember { mutableStateOf(false) }
    var excluindo by remember { mutableStateOf(false) }
    var erroExclusao by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Segurança da conta",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                    Triple("E-mail", email.ifEmpty { "--" }, "Alterar"),
                    Triple("Senha", "••••••••••", "Alterar"),
                    Triple("Telefone", telefone.ifEmpty { "--" }, "Alterar"),
                    Triple("Autenticação 2FA", "Desativado", "Ativar"),
                ).forEach { (label, valor, acao) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(label, fontSize = 12.sp, color = InkMuted)
                            Text(
                                valor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink
                            )
                        }
                        TextButton(onClick = {}) {
                            Text(
                                acao,
                                color = Verde,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }

        Row(
            modifier = Modifier
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Verde,
                )
                Text(
                    "Criptografados e nunca compartilhados com terceiros.",
                    fontSize = 12.sp,
                    color = Verde.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }


        // ── Zona de perigo (visível somente para o dono da conta) ──
        if (isDonoDoPerfilAtual) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = UrgenteClaro),
                border = BorderStroke(1.dp, Urgente.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Zona de perigo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Urgente)
                    }
                    Text(
                        "A exclusão da conta remove permanentemente seus dados pessoais conforme a LGPD (Art. 18). Esta ação não pode ser desfeita.",
                        fontSize = 13.sp,
                        color = Urgente.copy(alpha = 0.85f),
                        lineHeight = 19.sp
                    )

                    OutlinedButton(
                        onClick = { mostrarDialog1 = true },
                        enabled = !excluindo,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Urgente),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Urgente,
                            disabledContentColor = Urgente.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Excluir minha conta", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    // Mensagem de erro inline
                    if (erroExclusao != null) {
                        LaunchedEffect(erroExclusao) {
                            kotlinx.coroutines.delay(5_000)
                            erroExclusao = null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(erroExclusao!!, fontSize = 12.sp, color = Urgente, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
    }

    // ── Diálogo passo 1 – aviso ────────────────────────
    if (mostrarDialog1) {
        AlertDialog(
            onDismissRequest = { mostrarDialog1 = false },
            title = { Text("Excluir conta?", fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Esta ação é permanente e não pode ser desfeita. Ao confirmar:")
                    listOf(
                        "• Seu perfil será desativado imediatamente",
                        "• Todos os seus dados pessoais serão anonimizados em até 30 dias",
                        "• Atendimentos pendentes serão cancelados",
                        "• Saldos disponíveis serão processados conforme nossos Termos"
                    ).forEach { Text(it, fontSize = 13.sp, color = InkMuted) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    mostrarDialog1 = false
                    mostrarDialog2 = true
                }) {
                    Text("Entendi, continuar", color = Urgente, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialog1 = false }) {
                    Text("Cancelar", color = InkMuted)
                }
            },
            containerColor = Surface
        )
    }

    // ── Diálogo passo 2 – confirmação final ────────────
    if (mostrarDialog2) {
        AlertDialog(
            onDismissRequest = { if (!excluindo) mostrarDialog2 = false },
            title = { Text("Tem certeza absoluta?", fontWeight = FontWeight.Bold, color = Urgente) },
            text = {
                Text(
                    "Esta é a última etapa. Ao confirmar, sua conta será excluída de forma permanente " +
                            "e seus dados serão removidos conforme a LGPD (Art. 18).",
                    fontSize = 14.sp,
                    color = Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        excluindo = true
                        erroExclusao = null
                        scope.launch {
                            val ok = excluirContaAndroid()
                            if (ok) {
                                signOutAndroid()
                                mostrarDialog2 = false
                                onContaExcluida()    // navega de volta (welcome)
                            } else {
                                excluindo = false
                                erroExclusao = "Não foi possível excluir a conta. Tente novamente ou contate o suporte."
                                mostrarDialog2 = false
                            }
                        }
                    },
                    enabled = !excluindo,
                    colors = ButtonDefaults.buttonColors(containerColor = Urgente)
                ) {
                    if (excluindo) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (excluindo) "Excluindo..." else "Sim, excluir minha conta",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!excluindo) mostrarDialog2 = false },
                    enabled = !excluindo
                ) {
                    Text("Voltar", color = InkMuted)
                }
            },
            containerColor = Surface
        )
    }
}
