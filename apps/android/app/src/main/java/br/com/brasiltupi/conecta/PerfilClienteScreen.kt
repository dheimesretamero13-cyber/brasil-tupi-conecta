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

// ── DADOS MOCK ────────────────────────────────────────
data class DadosCliente(
    val nome: String,
    val email: String,
    val cpf: String,
    val telefone: String,
    val cidade: String,
    val estado: String,
    val endereco: String,
    val membroDesde: String,
    val consultasRealizadas: Int,
    val consultasAgendadas: Int,
)

val dadosClienteMock = DadosCliente(
    nome = "Juliana Ferreira",
    email = "juliana@email.com",
    cpf = "123.456.789-00",
    telefone = "(11) 98765-4321",
    cidade = "São Paulo",
    estado = "SP",
    endereco = "Rua das Flores, 123 — Jardim Paulista",
    membroDesde = "Março 2025",
    consultasRealizadas = 3,
    consultasAgendadas = 1,
)

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun PerfilClienteScreen(onVoltar: () -> Unit, userId: String = "") {
    var abaSelecionada by remember { mutableStateOf("perfil") }
    var fotoUrl      by remember { mutableStateOf<String?>(null) }
    var nomeReal     by remember { mutableStateOf("") }
    var emailReal    by remember { mutableStateOf("") }
    var cpfReal      by remember { mutableStateOf("") }
    var telefoneReal by remember { mutableStateOf("") }
    var cidadeReal   by remember { mutableStateOf("") }
    var estadoReal   by remember { mutableStateOf("") }
    var consultasRealizadas by remember { mutableStateOf(0) }
    var consultasAgendadas  by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            val perfil = getPerfilAndroid(userId)
            if (perfil != null) {
                fotoUrl      = perfil.foto_url
                nomeReal     = perfil.nome
                emailReal    = perfil.email
                cpfReal      = perfil.cpf      ?: dadosClienteMock.cpf
                telefoneReal = perfil.telefone ?: dadosClienteMock.telefone
                cidadeReal   = perfil.cidade   ?: dadosClienteMock.cidade
                estadoReal   = perfil.estado   ?: dadosClienteMock.estado
            }
            val todasConsultas  = buscarConsultasCliente(userId)
            consultasRealizadas = todasConsultas.count { it.status == "concluida" }
            consultasAgendadas  = todasConsultas.count { it.status == "agendada" }
        }

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
        // Header com avatar clicável
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
                    // Avatar com botão de editar
                    Box(modifier = Modifier.size(72.dp)) {
                        if (fotoUrl != null) {
                            AsyncImage(
                                model = fotoUrl,
                                contentDescription = "Foto de perfil",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(50))
                                    .background(Verde),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(iniciais, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        // Botão editar foto
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                                .background(Color.White, RoundedCornerShape(50))
                                .clickable { launcherFoto.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✏", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(nomeReal, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Cliente", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                        Text("📍 $cidadeReal, $estadoReal", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text("Membro desde ${dadosClienteMock.membroDesde}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Stats
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(vertical = 4.dp)) {
            listOf(
                "$consultasRealizadas" to "Consultas\nrealizadas",
                "$consultasAgendadas"  to "Consultas\nagendadas",
                "⭐ --"                to "Média das\nconsultas",
            ).forEach { (num, label) ->
                Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(num, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(label, fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        // Tabs
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("perfil" to "Meu Perfil", "endereco" to "Endereço", "seguranca" to "Segurança").forEach { (id, label) ->
                TextButton(onClick = { abaSelecionada = id }, colors = ButtonDefaults.textButtonColors(contentColor = if (abaSelecionada == id) Verde else InkMuted)) {
                    Text(label, fontSize = 13.sp, fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        // Conteúdo das abas — sem scroll interno
        when (abaSelecionada) {
            "perfil" -> AbaPerfilCliente(
    fotoUrl      = fotoUrl,
    onEditarFoto = { launcherFoto.launch("image/*") },
    nomeInicial  = nomeReal,
    email        = emailReal,
    cpf          = cpfReal,
    telefoneInicial = telefoneReal,
    membroDesde  = dadosClienteMock.membroDesde,
    userId       = userId,
)
            "endereco"  -> AbaEnderecoCliente()
            "seguranca" -> AbaSegurancaCliente(email = emailReal, telefone = telefoneReal)
        }
    }
}

// ── ABA: PERFIL ───────────────────────────────────────
@Composable
fun AbaPerfilCliente(
    fotoUrl:         String? = null,
    onEditarFoto:    () -> Unit = {},
    nomeInicial:     String = dadosClienteMock.nome,
    email:           String = dadosClienteMock.email,
    cpf:             String = dadosClienteMock.cpf,
    telefoneInicial: String = dadosClienteMock.telefone,
    membroDesde:     String = dadosClienteMock.membroDesde,
    userId:          String = "",
) {
    var editando by remember { mutableStateOf(false) }
    var nome     by remember { mutableStateOf(nomeInicial) }
    var telefone by remember { mutableStateOf(telefoneInicial) }
    val scope    = rememberCoroutineScope()
    val iniciais = nomeInicial.split(" ").map { it[0] }.joinToString("").take(2)

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                    listOf("Nome" to nome, "E-mail" to email, "CPF" to cpf, "Telefone" to telefone, "Membro desde" to membroDesde).forEach { (label, valor) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 13.sp, color = InkMuted)
                            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                }
            }
        }

        // Foto de perfil
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp)) {
                    if (fotoUrl != null) {
                        AsyncImage(model = fotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(VerdeClaro, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text(iniciais, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Verde)
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
    }
}

// ── ABA: ENDEREÇO ─────────────────────────────────────
@Composable
fun AbaEnderecoCliente() {
    var editando by remember { mutableStateOf(false) }
    var cep by remember { mutableStateOf("01310-100") }
    var endereco by remember { mutableStateOf(dadosClienteMock.endereco) }
    var cidade by remember { mutableStateOf(dadosClienteMock.cidade) }
    var estado by remember { mutableStateOf(dadosClienteMock.estado) }

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Endereço", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    TextButton(onClick = { editando = !editando }) {
                        Text(if (editando) "Cancelar" else "✏ Editar", color = Verde, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (editando) {
                    CampoTexto("CEP", cep, { cep = it }, "00000-000")
                    Spacer(modifier = Modifier.height(12.dp))
                    CampoTexto("Endereço", endereco, { endereco = it }, "Rua, Avenida...")
                    Spacer(modifier = Modifier.height(12.dp))
                    CampoTexto("Cidade", cidade, { cidade = it }, "Sua cidade")
                    Spacer(modifier = Modifier.height(12.dp))
                    CampoTexto("Estado", estado, { estado = it }, "UF")
                    Spacer(modifier = Modifier.height(16.dp))
                    BotaoProximo("Salvar endereço") { editando = false }
                } else {
                    listOf("CEP" to cep, "Endereço" to endereco, "Cidade" to cidade, "Estado" to estado).forEach { (label, valor) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 13.sp, color = InkMuted)
                            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        }
                        HorizontalDivider(color = SurfaceOff)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().background(AzulClaro, RoundedCornerShape(10.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
            Text("ℹ", fontSize = 16.sp, color = Azul)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Seu endereço é usado para encontrar profissionais próximos a você. Não é exibido publicamente.", fontSize = 12.sp, color = Azul, lineHeight = 17.sp)
        }
    }
}

// ── ABA: SEGURANÇA ────────────────────────────────────
@Composable
fun AbaSegurancaCliente(
    email:    String = dadosClienteMock.email,
    telefone: String = dadosClienteMock.telefone,
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

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = UrgenteClaro), border = androidx.compose.foundation.BorderStroke(1.dp, Urgente.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Zona de perigo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Urgente)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ao excluir sua conta, todos os seus dados serão removidos permanentemente.", fontSize = 13.sp, color = InkSoft, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Urgente), border = androidx.compose.foundation.BorderStroke(1.dp, Urgente), shape = RoundedCornerShape(8.dp)) {
                    Text("Excluir minha conta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(10.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
            Text("🔒", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Seus dados estão protegidos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                Text("Criptografados e nunca compartilhados com terceiros.", fontSize = 12.sp, color = Verde.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}