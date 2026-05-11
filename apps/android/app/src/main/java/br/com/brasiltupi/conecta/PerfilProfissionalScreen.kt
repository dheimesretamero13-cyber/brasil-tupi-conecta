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
import br.com.brasiltupi.conecta.AbaUrgenteCompartilhada
import br.com.brasiltupi.conecta.AbaMeuPmp
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun PerfilProfissionalScreen(
    onVoltar:        () -> Unit,
    userId:          String = "",
    onKyc:           () -> Unit = {},
    onContaExcluida: () -> Unit = {},
) {
    var carregando by remember { mutableStateOf(userId.isNotEmpty()) }
    var abaSelecionada by remember { mutableStateOf("perfil") }
    var mostrarMeuPmp by remember { mutableStateOf(false) }
    var fotoUrl by remember { mutableStateOf<String?>(null) }
    var capaUrl by remember { mutableStateOf<String?>(null) }
    var nomeReal by remember { mutableStateOf("") }
    var areaReal by remember { mutableStateOf("") }
    var cidadeReal by remember { mutableStateOf("") }
    var emailReal by remember { mutableStateOf("") }
    var telefoneReal by remember { mutableStateOf("") }
    var descricaoReal by remember { mutableStateOf("") }
    var conselhoReal by remember { mutableStateOf("") }
    var credReal by remember { mutableStateOf(0) }
    var isPMPReal by remember { mutableStateOf(false) }
    var dispUrgenteReal by remember { mutableStateOf(false) }
    var kycStatus by remember { mutableStateOf("") }
    var consultasUrgente by remember { mutableStateOf<List<ConsultaProfissional>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var valorUrgenteAtual by remember { mutableStateOf<Double?>(null) }
    var valorMinutoExtrapoladoAtual by remember { mutableStateOf<Double?>(null) }
    var membroDesde by remember { mutableStateOf("--") }
    var tipoConta by remember { mutableStateOf("Profissional") }
    var totalAtendimentos by remember { mutableIntStateOf(0) }
    var avaliacaoMedia by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(userId) {
        if (userId.isEmpty()) { carregando = false; return@LaunchedEffect }
        carregando = true
        try {
            val perfil = getPerfilAndroid(userId)
            if (perfil != null) {
                fotoUrl = perfil.foto_url
                capaUrl = perfil.capa_url
                nomeReal = perfil.nome
                emailReal = perfil.email
                telefoneReal = perfil.telefone.orEmpty()
                cidadeReal = if (!perfil.cidade.isNullOrEmpty() && !perfil.estado.isNullOrEmpty())
                    "${perfil.cidade}, ${perfil.estado}"
                else perfil.cidade.orEmpty()
            }
            val meu = getMeuPerfilProfissional(userId)
            if (meu != null) {
                areaReal = meu.area
                descricaoReal = meu.descricao.orEmpty()
                conselhoReal = meu.conselho.orEmpty()
                credReal = meu.credibilidade
                isPMPReal = meu.is_pmp
                dispUrgenteReal = meu.disponivel_urgente
                valorUrgenteAtual = meu?.valor_urgente?.toDouble()
                valorMinutoExtrapoladoAtual = meu?.valorMinutoExtrapolado
            }
            try {
                val docs = buscarKycDocumentos(userId)
                kycStatus = docs.firstOrNull()?.status ?: "not_submitted"
                consultasUrgente = buscarConsultasProfissional(userId)
                // TODO: Substituir "--" pelo campo real de data de criação quando disponível
                membroDesde = "--"
                tipoConta = if (meu?.is_pmp == true) "Profissional Certificado" else "Profissional Liberal"
                val concluidas = consultasUrgente.filter { it.status in listOf("concluida", "concluido") }
                totalAtendimentos = concluidas.size
                val notas = concluidas.map { it.avaliacao }.filter { it > 0 }
                avaliacaoMedia = if (notas.isNotEmpty()) notas.average() else 0.0
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

    if (mostrarMeuPmp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceWarm)
        ) {
            TextButton(
                onClick = { mostrarMeuPmp = false },
                modifier = Modifier.padding(12.dp)
            ) {
                Text("← Voltar para Perfil", color = Azul, fontWeight = FontWeight.Bold)
            }
            AbaMeuPmp(
                profissionalId = userId,
                onIniciarPagamento = { }
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceWarm)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                if (capaUrl != null) {
                    AsyncImage(
                        model = capaUrl,
                        contentDescription = "Capa",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
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
                    onClick = onVoltar,
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
                                model = fotoUrl,
                                contentDescription = "Foto de perfil",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(50))
                                    .background(Azul),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Azul, RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val iniciais = if (nomeReal.isNotBlank()) {
                                    nomeReal.split(" ")
                                        .filter { it.isNotEmpty() }
                                        .map { it.first() }
                                        .joinToString("")
                                        .take(2)
                                } else "?"

                                Text(
                                    text = iniciais.ifEmpty { "?" },
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = nomeReal,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = areaReal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = InkSoft,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("📍", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = cidadeReal,
                        fontSize = 13.sp,
                        color = InkMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F4E6)),
                    border = BorderStroke(1.dp, Color(0xFFC49A2A).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏅", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Certificação Profissional",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA67F2A)
                            )
                            Text(
                                "Este profissional possui certificação profissional verificada.",
                                fontSize = 12.sp,
                                color = Color(0xFFA67F2A).copy(alpha = 0.7f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (isPMPReal) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        border = BorderStroke(1.dp, Azul.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🏆", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Programa de Maestria Profissional (PMP)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Azul
                                )
                                Text(
                                    "Selo de excelência com benefícios exclusivos.",
                                    fontSize = 12.sp,
                                    color = Azul.copy(alpha = 0.75f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                if (kycStatus == "approved") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        border = BorderStroke(1.dp, DouradoMedio.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛡️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Confiança Verificada",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Dourado
                                )
                                Text(
                                    "Identidade e documentos verificados pela plataforma.",
                                    fontSize = 12.sp,
                                    color = Dourado.copy(alpha = 0.75f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                } else if (kycStatus == "pending") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        border = BorderStroke(1.dp, Color(0xFFF57F17).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏳", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Verificação em andamento",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF57F17)
                                )
                                Text(
                                    "Seus documentos estão sendo analisados.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFF57F17).copy(alpha = 0.75f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                } else if (kycStatus == "rejected" || kycStatus == "not_submitted" || kycStatus == "") {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onKyc() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceOff),
                        border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔓", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (kycStatus == "rejected") "Documento rejeitado" else "Verificação pendente",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkMuted
                                )
                                Text(
                                    "Toque para iniciar o processo de verificação.",
                                    fontSize = 12.sp,
                                    color = InkMuted,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        Triple("--", "Atendimentos", "📊"),
                        Triple("⭐ --", "Avaliação", "⭐"),
                        Triple("$credReal/100", "Credibilidade", "💎"),
                    ).forEach { (num, label, icon) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(icon, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(num, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(label, fontSize = 11.sp, color = InkMuted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SurfaceOff, thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    "perfil" to "Meu Perfil",
                    "seguranca" to "Segurança",
                    "urgente" to "Urgente",
                ).forEach { (id, label) ->
                    TextButton(
                        onClick = { abaSelecionada = id },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (abaSelecionada == id) Verde else InkMuted,
                        ),
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            HorizontalDivider(color = SurfaceOff)

            when (abaSelecionada) {
                "perfil" -> AbaPerfilProfissional(
                    nomeInicial = nomeReal,
                    areaInicial = areaReal,
                    cidadeInicial = cidadeReal,
                    descricaoInicial = descricaoReal,
                    conselho = conselhoReal,
                    credibilidade = credReal,
                    isPMP = isPMPReal,
                    userId = userId,
                    telefoneReal = telefoneReal,
                    onCandidatarPmp = { mostrarMeuPmp = true },
                    membroDesde = membroDesde,
                    tipoConta = tipoConta,
                    totalAtendimentos = totalAtendimentos,
                    avaliacaoMedia = avaliacaoMedia,
                )
                "seguranca" -> AbaSegurancaProfissional(
                    email = emailReal,
                    telefone = telefoneReal,
                    userId = userId,
                    onContaExcluida = onContaExcluida,
                    onEmailAtualizado = { emailReal = it },
                    onTelefoneAtualizado = { telefoneReal = it },
                )
                "urgente" -> {
                    val scopeUrgente = rememberCoroutineScope()
                    AbaUrgenteCompartilhada(
                        disponivelInicial = dispUrgenteReal,
                        userId = userId,
                        consultas = consultasUrgente,
                        kycAprovado = null,
                        onKyc = null,
                        mostrarGuiaChamada = false,
                        valorUrgenteAtual = valorUrgenteAtual,
                        valorMinutoExtrapoladoAtual = valorMinutoExtrapoladoAtual,
                        onSalvarValorUrgente = { novoValor ->
                            scopeUrgente.launch {
                                val perfil = getMeuPerfilProfissional(userId)
                                if (perfil != null) {
                                    val ok = atualizarPerfilProfissional(
                                        userId = userId,
                                        bio = perfil.descricao ?: "",
                                        area = perfil.area,
                                        conselho = perfil.conselho ?: "",
                                        numeroConselho = perfil.numero_conselho ?: "",
                                        precoNormal = perfil.valor_normal,
                                        precoUrgente = novoValor.toInt(),
                                    )
                                    if (ok) {
                                        valorUrgenteAtual = novoValor
                                    }
                                }
                            }
                        },
                        onSalvarValorMinutoExtrapolado = { novoValorMinuto ->
                            scopeUrgente.launch {
                                val resultado = atualizarValorMinutoUrgente(userId, novoValorMinuto)
                                resultado.onSuccess {
                                    valorMinutoExtrapoladoAtual = novoValorMinuto
                                }
                            }
                        },
                        mostrarRegrasFinanceiras = true,
                        enableVerticalScroll = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun AbaPerfilProfissional(
    nomeInicial: String = "",
    areaInicial: String = "",
    cidadeInicial: String = "",
    descricaoInicial: String = "",
    conselho: String = "",
    credibilidade: Int = 0,
    isPMP: Boolean = false,
    userId: String = "",
    telefoneReal: String = "",
    onCandidatarPmp: () -> Unit = {},
    membroDesde: String = "--",
    tipoConta: String = "Profissional",
    totalAtendimentos: Int = 0,
    avaliacaoMedia: Double = 0.0,
) {
    var editando by remember { mutableStateOf(false) }
    var nome by remember { mutableStateOf(nomeInicial) }
    var descricao by remember { mutableStateOf(descricaoInicial) }
    var cidade by remember { mutableStateOf(cidadeInicial) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
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
                            value = descricao,
                            onValueChange = { descricao = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Verde,
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
                                    bio = descricao,
                                    cidade = partesCidade.getOrNull(0)?.trim() ?: cidade,
                                    estado = partesCidade.getOrNull(1)?.trim() ?: "",
                                )
                            }
                        }
                        editando = false
                    }
                } else {
                    listOf(
                        "Nome" to nome,
                        "Área" to areaInicial,
                        "Cidade" to cidade,
                        "Membro desde" to membroDesde,
                    ).forEach { (label, valor) ->
                        Row(
                            modifier = Modifier
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
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Dados profissionais", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "Tipo de conta" to tipoConta,
                    "Conselho" to conselho,
                    "Atendimentos" to totalAtendimentos.toString(),
                    "Avaliação média" to if (avaliacaoMedia > 0) "⭐ %.1f".format(avaliacaoMedia) else "⭐ --",
                ).forEach { (label, valor) ->
                    Row(
                        modifier = Modifier
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
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCandidatarPmp() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F0)),
            border = BorderStroke(1.5.dp, DouradoMedio.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🏆", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Programa de Maestria Profissional",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Dourado,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Eleve sua carreira a outro nível. Conheça as vantagens exclusivas do selo PMP.",
                    fontSize = 14.sp,
                    color = InkSoft,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sua pontuação:", fontSize = 13.sp, color = InkMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "$credibilidade/100",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (credibilidade >= 80) Verde else DouradoMedio
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { credibilidade / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = DouradoMedio,
                    trackColor = SurfaceOff,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Toque aqui para visitar a aba Meu PMP →",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Azul,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AbaSegurancaProfissional(
    email: String = "",
    telefone: String = "",
    userId: String = "",
    onContaExcluida: () -> Unit = {},
    onEmailAtualizado: (String) -> Unit = {},
    onTelefoneAtualizado: (String) -> Unit = {},
    onSenhaAlterada: () -> Unit = {},
) {
    val isDonoDoPerfilAtual = remember(userId) {
        userId.isNotEmpty() && userId == AuthRepository.userId
    }

    var mostrarDialogConfirmacao1 by remember { mutableStateOf(false) }
    var mostrarDialogConfirmacao2 by remember { mutableStateOf(false) }
    var excluindo by remember { mutableStateOf(false) }
    var erroExclusao by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var mostrarDialogEmail by remember { mutableStateOf(false) }
    var mostrarDialogSenha by remember { mutableStateOf(false) }
    var mostrarDialogTelefone by remember { mutableStateOf(false) }
    var mostrarDialog2FA by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Segurança da conta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                // E-mail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("E-mail", fontSize = 12.sp, color = InkMuted)
                        Text(email, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    TextButton(onClick = { mostrarDialogEmail = true }) {
                        Text("Alterar", color = Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = SurfaceOff)
                // Senha
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Senha", fontSize = 12.sp, color = InkMuted)
                        Text("••••••••••", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    TextButton(onClick = { mostrarDialogSenha = true }) {
                        Text("Alterar", color = Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = SurfaceOff)
                // Telefone
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Telefone", fontSize = 12.sp, color = InkMuted)
                        Text(telefone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    TextButton(onClick = { mostrarDialogTelefone = true }) {
                        Text("Alterar", color = Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = SurfaceOff)
                // Autenticação 2FA
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Autenticação 2FA", fontSize = 12.sp, color = InkMuted)
                        Text("Desativado", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    TextButton(onClick = { mostrarDialog2FA = true }) {
                        Text("Ativar", color = Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = SurfaceOff)
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
                    "Todas as informações são criptografadas e nunca compartilhadas com terceiros.",
                    fontSize = 12.sp,
                    color = Verde.copy(alpha = 0.8f),
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (isDonoDoPerfilAtual) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = UrgenteClaro),
                border = BorderStroke(1.dp, Urgente.copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Zona de perigo",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Urgente,
                        )
                    }
                    Text(
                        "A exclusão da conta remove permanentemente seus dados pessoais conforme a " +
                                "Lei Geral de Proteção de Dados (LGPD, Art. 18). Esta ação não pode ser desfeita.",
                        fontSize = 13.sp,
                        color = Urgente.copy(alpha = 0.85f),
                        lineHeight = 19.sp,
                    )
                    OutlinedButton(
                        onClick = { mostrarDialogConfirmacao1 = true },
                        enabled = !excluindo,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Urgente),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Urgente,
                            disabledContentColor = Urgente.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            "Excluir minha conta",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }

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
                        color = Ink,
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
                    color = Ink,
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
                                mostrarDialogConfirmacao2 = false
                                onContaExcluida()
                            } else {
                                excluindo = false
                                erroExclusao =
                                    "Não foi possível excluir a conta. Tente novamente ou contate o suporte."
                                mostrarDialogConfirmacao2 = false
                            }
                        }
                    },
                    enabled = !excluindo,
                    colors = ButtonDefaults.buttonColors(containerColor = Urgente),
                ) {
                    if (excluindo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (excluindo) "Excluindo..." else "Sim, excluir minha conta",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!excluindo) mostrarDialogConfirmacao2 = false },
                    enabled = !excluindo,
                ) {
                    Text("Voltar", color = InkMuted)
                }
            },
            containerColor = Surface,
        )
    }

    // Diálogos de alteração (dentro de AbaSegurancaProfissional)
    if (mostrarDialogEmail) {
        var novoEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { mostrarDialogEmail = false },
            title = { Text("Alterar e-mail") },
            text = {
                OutlinedTextField(
                    value = novoEmail,
                    onValueChange = { novoEmail = it },
                    label = { Text("Novo e-mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                var emailErro by remember { mutableStateOf<String?>(null) }
                var emailSalvando by remember { mutableStateOf(false) }
                Button(onClick = {
                    emailSalvando = true
                    emailErro = null
                    scope.launch {
                        val result = updateUserEmail(novoEmail)
                        if (result.success) {
                            onEmailAtualizado(novoEmail)
                            mostrarDialogEmail = false
                        } else {
                            emailErro = result.errorMessage ?: "Falha ao atualizar e‑mail."
                        }
                        emailSalvando = false
                    }
                }, enabled = !emailSalvando) {
                    if (emailSalvando) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Salvar")
                }
                if (emailErro != null) {
                    Text(emailErro!!, color = Urgente, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            dismissButton = { TextButton(onClick = { mostrarDialogEmail = false }) { Text("Cancelar") } }
        )
    }

    if (mostrarDialogSenha) {
        var novaSenha by remember { mutableStateOf("") }
        var confirmarSenha by remember { mutableStateOf("") }
        var senhaErro by remember { mutableStateOf<String?>(null) }
        var senhaSalvando by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { mostrarDialogSenha = false },
            title = { Text("Alterar senha") },
            text = {
                Column {
                    OutlinedTextField(
                        value = novaSenha,
                        onValueChange = { novaSenha = it },
                        label = { Text("Nova senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmarSenha,
                        onValueChange = { confirmarSenha = it },
                        label = { Text("Confirmar senha") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (senhaErro != null) {
                        Text(
                            text = senhaErro!!,
                            color = Urgente,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (novaSenha != confirmarSenha) {
                            senhaErro = "As senhas não coincidem."
                            return@Button
                        }
                        if (novaSenha.length < 6) {
                            senhaErro = "A senha deve ter no mínimo 6 caracteres."
                            return@Button
                        }
                        senhaSalvando = true
                        senhaErro = null
                        scope.launch {
                            val ok = updateUserPassword(novaSenha)
                            if (ok) {
                                signOutAndroid()
                                mostrarDialogSenha = false
                                onSenhaAlterada()
                            } else {
                                senhaErro = "Falha ao alterar senha. Tente novamente."
                            }
                            senhaSalvando = false
                        }
                    },
                    enabled = !senhaSalvando
                ) {
                    if (senhaSalvando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Salvar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogSenha = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (mostrarDialogTelefone) {
        var novoTelefone by remember { mutableStateOf(telefone) }
        AlertDialog(
            onDismissRequest = { mostrarDialogTelefone = false },
            title = { Text("Alterar telefone") },
            text = {
                OutlinedTextField(
                    value = novoTelefone,
                    onValueChange = { novoTelefone = it },
                    label = { Text("Novo telefone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                var telefoneErro by remember { mutableStateOf<String?>(null) }
                var telefoneSalvando by remember { mutableStateOf(false) }
                Button(onClick = {
                    telefoneSalvando = true
                    telefoneErro = null
                    scope.launch {
                        val ok = updateUserPhone(userId, novoTelefone)
                        if (ok) {
                            onTelefoneAtualizado(novoTelefone)
                            mostrarDialogTelefone = false
                        } else {
                            telefoneErro = "Falha ao atualizar telefone. Verifique sua conexão."
                        }
                        telefoneSalvando = false
                    }
                }, enabled = !telefoneSalvando) {
                    if (telefoneSalvando) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Salvar")
                }
                if (telefoneErro != null) {
                    Text(telefoneErro!!, color = Urgente, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            dismissButton = { TextButton(onClick = { mostrarDialogTelefone = false }) { Text("Cancelar") } }
        )
    }

    if (mostrarDialog2FA) {
        AlertDialog(
            onDismissRequest = { mostrarDialog2FA = false },
            title = { Text("Autenticação em duas etapas") },
            text = { Text("Deseja ativar a autenticação de dois fatores? Você receberá instruções por e-mail.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        // TODO: chamar API para iniciar ativação 2FA
                        mostrarDialog2FA = false
                    }
                }) { Text("Ativar") }
            },
            dismissButton = { TextButton(onClick = { mostrarDialog2FA = false }) { Text("Cancelar") } }
        )
    }
}