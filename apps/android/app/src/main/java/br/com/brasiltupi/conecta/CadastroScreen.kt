package br.com.brasiltupi.conecta
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*

// ── TIPOS ────────────────────────────────────────────
enum class TipoCadastro { NENHUM, CERTIFICADO, LIBERAL, CLIENTE }

// ── TELA PRINCIPAL ───────────────────────────────────
@Composable
fun CadastroScreen(
    onVoltar: () -> Unit,
    onConcluido: () -> Unit,
) {
    var tipo by remember { mutableStateOf(TipoCadastro.NENHUM) }
    var etapa by remember { mutableStateOf(1) }

    when {
        tipo == TipoCadastro.NENHUM -> SelecionarTipo(
            onVoltar = onVoltar,
            onSelecionar = { tipo = it; etapa = 1 }
        )
        tipo == TipoCadastro.CERTIFICADO -> CadastroFluxo(
            tipo = tipo,
            etapa = etapa,
            totalEtapas = 4,
            onVoltar = { if (etapa == 1) tipo = TipoCadastro.NENHUM else etapa-- },
            onProximo = { if (etapa < 4) etapa++ else onConcluido() },
            onTrocarTipo = { tipo = TipoCadastro.NENHUM }
        )
        tipo == TipoCadastro.LIBERAL -> CadastroFluxo(
            tipo = tipo,
            etapa = etapa,
            totalEtapas = 4,
            onVoltar = { if (etapa == 1) tipo = TipoCadastro.NENHUM else etapa-- },
            onProximo = { if (etapa < 4) etapa++ else onConcluido() },
            onTrocarTipo = { tipo = TipoCadastro.NENHUM }
        )
        tipo == TipoCadastro.CLIENTE -> CadastroFluxo(
            tipo = tipo,
            etapa = etapa,
            totalEtapas = 4,
            onVoltar = { if (etapa == 1) tipo = TipoCadastro.NENHUM else etapa-- },
            onProximo = { if (etapa < 4) etapa++ else onConcluido() },
            onTrocarTipo = { tipo = TipoCadastro.NENHUM }
        )
    }
}

// ── SELEÇÃO DE TIPO ───────────────────────────────────
@Composable
fun SelecionarTipo(
    onVoltar: () -> Unit,
    onSelecionar: (TipoCadastro) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        TextButton(onClick = onVoltar) {
            Text("← Voltar", color = InkMuted, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Brasil Tupi", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Azul)
        Text("Conecta", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Verde)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Como você quer se cadastrar?",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink
        )
        Text(
            "Sua escolha define o processo de verificação.",
            fontSize = 14.sp, color = InkMuted,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
        )

        // Card Certificado
        TipoCard(
            emoji = "🎓",
            titulo = "Profissional Certificado",
            descricao = "Possui diploma ou registro em conselho de classe (CRM, OAB, CREA, CRP...)",
            badge = "Verificação completa",
            badgeColor = Azul,
            itens = listOf("Diploma ou certificado", "Registro em conselho", "CNPJ ou CPF profissional", "Portfólio ou experiência"),
            onClick = { onSelecionar(TipoCadastro.CERTIFICADO) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Card Liberal
        TipoCard(
            emoji = "💼",
            titulo = "Profissional Liberal",
            descricao = "Atua com base em experiência prática e cursos livres, sem diploma formal.",
            badge = "Verificação por experiência",
            badgeColor = Color(0xFFB07D00),
            itens = listOf("CNPJ ou CPF profissional", "Cursos livres e certificados", "Portfólio ou redes profissionais"),
            onClick = { onSelecionar(TipoCadastro.LIBERAL) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Card Cliente
        TipoCard(
            emoji = "🔍",
            titulo = "Cliente",
            descricao = "Quero encontrar profissionais verificados e agendar consultas com segurança.",
            badge = "Acesso imediato",
            badgeColor = Verde,
            itens = listOf("Dados pessoais verificados", "CPF e telefone confirmados", "Endereço e foto de perfil"),
            onClick = { onSelecionar(TipoCadastro.CLIENTE) }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TipoCard(
    emoji: String,
    titulo: String,
    descricao: String,
    badge: String,
    badgeColor: Color,
    itens: List<String>,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(SurfaceOff, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = badge,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(descricao, fontSize = 13.sp, color = InkMuted, lineHeight = 19.sp)
            Spacer(modifier = Modifier.height(12.dp))

            itens.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(VerdeClaro, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", fontSize = 10.sp, color = Verde, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item, fontSize = 13.sp, color = InkSoft)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Começar cadastro →",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde
            )
        }
    }
}

// ── FLUXO DE CADASTRO ─────────────────────────────────
@Composable
fun CadastroFluxo(
    tipo: TipoCadastro,
    etapa: Int,
    totalEtapas: Int,
    onVoltar: () -> Unit,
    onProximo: () -> Unit,
    onTrocarTipo: () -> Unit,
) {
    val labels = when (tipo) {
        TipoCadastro.CERTIFICADO -> listOf("Dados", "Formação", "Documentos", "Revisão")
        TipoCadastro.LIBERAL     -> listOf("Dados", "Experiência", "Documentos", "Revisão")
        TipoCadastro.CLIENTE     -> listOf("Conta", "Verificação", "Endereço", "Revisão")
        else -> listOf()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
    ) {
        // Header
        Column(
            modifier = Modifier
                .background(Surface)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onVoltar) {
                    Text("← Voltar", color = InkMuted, fontSize = 13.sp)
                }
                TextButton(onClick = onTrocarTipo) {
                    Text("Trocar tipo", color = InkMuted, fontSize = 13.sp)
                }
            }

            // Badge tipo
            val (tipoLabel, tipoColor) = when (tipo) {
                TipoCadastro.CERTIFICADO -> "🎓 Profissional Certificado" to Azul
                TipoCadastro.LIBERAL     -> "💼 Profissional Liberal" to Color(0xFFB07D00)
                TipoCadastro.CLIENTE     -> "🔍 Cliente" to Verde
                else -> "" to Verde
            }
            Text(
                text = tipoLabel,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tipoColor,
                modifier = Modifier
                    .background(tipoColor.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { etapa.toFloat() / totalEtapas.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Verde,
                trackColor = SurfaceOff,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Labels etapas
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEachIndexed { i, label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (i + 1 == etapa) FontWeight.Bold else FontWeight.Normal,
                        color = if (i + 1 == etapa) Verde else if (i + 1 < etapa) InkSoft else InkMuted
                    )
                }
            }
        }

        // Conteúdo da etapa
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (tipo) {
                TipoCadastro.CERTIFICADO -> EtapasCertificado(etapa = etapa, onProximo = onProximo)
                TipoCadastro.LIBERAL     -> EtapasLiberal(etapa = etapa, onProximo = onProximo)
                TipoCadastro.CLIENTE     -> EtapasCliente(etapa = etapa, onProximo = onProximo)
                else -> {}
            }
        }
    }
}

// ── CAMPOS REUTILIZÁVEIS ──────────────────────────────
@Composable
fun CampoTexto(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    erro: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    senha: Boolean = false,
) {
    var mostrarSenha by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(modifier = Modifier.height(5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = InkMuted, fontSize = 14.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (senha && !mostrarSenha) PasswordVisualTransformation() else VisualTransformation.None,
            isError = erro.isNotEmpty(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Verde,
                unfocusedBorderColor = Color(0xFFE0E0E0),
                errorBorderColor = Urgente
            ),
            trailingIcon = if (senha) ({
                TextButton(onClick = { mostrarSenha = !mostrarSenha }) {
                    Text(if (mostrarSenha) "Ocultar" else "Ver", color = InkMuted, fontSize = 11.sp)
                }
            }) else null,
            singleLine = true
        )
        if (erro.isNotEmpty()) {
            Text(erro, color = Urgente, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
fun BotaoProximo(texto: String = "Continuar →", onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoBox(texto: String, tipo: String = "info") {
    val cor = if (tipo == "aviso") Color(0xFFB07D00) else Azul
    val bg  = if (tipo == "aviso") Color(0xFFFDF3D8) else AzulClaro
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(if (tipo == "aviso") "⚠" else "ℹ", fontSize = 14.sp, color = cor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(texto, fontSize = 13.sp, color = cor, lineHeight = 18.sp)
    }
}

// ── ETAPAS CERTIFICADO ────────────────────────────────
@Composable
fun EtapasCertificado(etapa: Int, onProximo: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var instituicao by remember { mutableStateOf("") }
    var conselho by remember { mutableStateOf("") }
    var numeroConselho by remember { mutableStateOf("") }
    var cnpj by remember { mutableStateOf("") }
    var portfolio by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (etapa) {
            1 -> {
                Text("Dados pessoais", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Informações básicas do seu perfil.", fontSize = 13.sp, color = InkMuted)
                CampoTexto("Nome completo *", nome, { nome = it }, "Seu nome completo")
                CampoTexto("E-mail profissional *", email, { email = it }, "seu@email.com", keyboardType = KeyboardType.Email)
                CampoTexto("Telefone / WhatsApp *", telefone, { telefone = it }, "(00) 00000-0000", keyboardType = KeyboardType.Phone)
                CampoTexto("Área de atuação *", area, { area = it }, "Ex: Saúde, Direito, Engenharia...")
                CampoTexto("Senha *", senha, { senha = it }, "Mínimo 8 caracteres", senha = true)
                BotaoProximo(onClick = onProximo)
            }
            2 -> {
                Text("Formação e registro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Comprove sua qualificação.", fontSize = 13.sp, color = InkMuted)
                CampoTexto("Instituição de ensino *", instituicao, { instituicao = it }, "Nome da universidade")
                CampoTexto("Conselho profissional *", conselho, { conselho = it }, "Ex: CRM, OAB, CREA, CRP...")
                CampoTexto("Número de registro *", numeroConselho, { numeroConselho = it }, "Ex: 12345/SP")
                InfoBox("Seu diploma e registro serão verificados pela nossa equipe em até 48 horas.")
                BotaoProximo(onClick = onProximo)
            }
            3 -> {
                Text("Documentos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Informações fiscais e portfólio.", fontSize = 13.sp, color = InkMuted)
                CampoTexto("CNPJ (opcional)", cnpj, { cnpj = it }, "00.000.000/0000-00", keyboardType = KeyboardType.Number)
                CampoTexto("Portfólio ou LinkedIn (opcional)", portfolio, { portfolio = it }, "https://")
                InfoBox("O CNPJ não é obrigatório, mas profissionais com CNPJ ativo têm prioridade de exibição.", tipo = "aviso")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VerdeClaro, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seus documentos são criptografados e usados apenas para verificação.", fontSize = 12.sp, color = Verde)
                }
                BotaoProximo(onClick = onProximo)
            }
            4 -> EtapaRevisao(tipo = "Profissional Certificado", nome = nome, email = email, senha = senha, onProximo = onProximo)
        }
    }
}

// ── ETAPAS LIBERAL ────────────────────────────────────
@Composable
fun EtapasLiberal(etapa: Int, onProximo: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var experiencia by remember { mutableStateOf("") }
    var curso by remember { mutableStateOf("") }
    var portfolio by remember { mutableStateOf("") }
    var cnpj by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (etapa) {
            1 -> {
                Text("Dados pessoais", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Informações básicas do seu perfil.", fontSize = 13.sp, color = InkMuted)
                CampoTexto("Nome completo *", nome, { nome = it }, "Seu nome completo")
                CampoTexto("E-mail *", email, { email = it }, "seu@email.com", keyboardType = KeyboardType.Email)
                CampoTexto("Telefone / WhatsApp *", telefone, { telefone = it }, "(00) 00000-0000", keyboardType = KeyboardType.Phone)
                CampoTexto("Área de atuação *", area, { area = it }, "Ex: Marketing, Design, TI...")
                CampoTexto("Senha *", senha, { senha = it }, "Mínimo 8 caracteres", senha = true)
                BotaoProximo(onClick = onProximo)
            }
            2 -> {
                Text("Cursos e experiência", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Liste seus cursos e experiência prática.", fontSize = 13.sp, color = InkMuted)
                CampoTexto("Tempo de experiência na área", experiencia, { experiencia = it }, "Ex: 5 anos")
                CampoTexto("Principal curso ou certificação", curso, { curso = it }, "Ex: Google Ads, Photoshop...")
                CampoTexto("Portfólio, LinkedIn ou Instagram", portfolio, { portfolio = it }, "https://")
                InfoBox("Quanto mais completo seu perfil, maior sua credibilidade na plataforma.")
                BotaoProximo(onClick = onProximo)
            }
            3 -> {
                Text("Documento fiscal", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Necessário para garantia de pagamentos.", fontSize = 13.sp, color = InkMuted)
                InfoBox("Para profissionais liberais, o CNPJ ou MEI é obrigatório para emissão de recibos.", tipo = "aviso")
                CampoTexto("CNPJ / MEI *", cnpj, { cnpj = it }, "00.000.000/0000-00", keyboardType = KeyboardType.Number)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VerdeClaro, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dados criptografados e usados apenas para verificação.", fontSize = 12.sp, color = Verde)
                }
                BotaoProximo(onClick = onProximo)
            }
            4 -> EtapaRevisao(tipo = "Profissional Liberal", nome = nome, email = email, senha = senha, onProximo = onProximo)
        }
    }
}

// ── ETAPAS CLIENTE ────────────────────────────────────
@Composable
fun EtapasCliente(etapa: Int, onProximo: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var cep by remember { mutableStateOf("") }
    var endereco by remember { mutableStateOf("") }
    var cidade by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (etapa) {
            1 -> {
                Text("Criar conta", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Seus dados de acesso à plataforma.", fontSize = 13.sp, color = InkMuted)
                CampoTexto("Nome completo *", nome, { nome = it }, "Seu nome completo")
                CampoTexto("E-mail *", email, { email = it }, "seu@email.com", keyboardType = KeyboardType.Email)
                CampoTexto("Senha *", senha, { senha = it }, "Mínimo 8 caracteres", senha = true)
                BotaoProximo(onClick = onProximo)
            }
            2 -> {
                Text("Verificação de identidade", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Para garantir a segurança de todos.", fontSize = 13.sp, color = InkMuted)
                CampoTexto("CPF *", cpf, { cpf = it }, "000.000.000-00", keyboardType = KeyboardType.Number)
                CampoTexto("Telefone / WhatsApp *", telefone, { telefone = it }, "(00) 00000-0000", keyboardType = KeyboardType.Phone)
                InfoBox("Seu telefone será usado para autenticação em dois fatores.")
                BotaoProximo(onClick = onProximo)
            }
            3 -> {
                Text("Endereço", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Para encontrar profissionais próximos a você.", fontSize = 13.sp, color = InkMuted)
                CampoTexto("CEP", cep, { cep = it }, "00000-000", keyboardType = KeyboardType.Number)
                CampoTexto("Endereço *", endereco, { endereco = it }, "Rua, Avenida...")
                CampoTexto("Cidade *", cidade, { cidade = it }, "Sua cidade")
                BotaoProximo(onClick = onProximo)
            }
            4 -> EtapaRevisao(tipo = "Cliente", nome = nome, email = email, senha = senha, onProximo = onProximo)
        }
    }
}

// ── ETAPA REVISÃO ─────────────────────────────────────
@Composable
fun EtapaRevisao(tipo: String, nome: String, email: String, senha: String = "", onProximo: () -> Unit) {
    var aceito by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Revisão do cadastro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Confirme seus dados antes de enviar.", fontSize = 13.sp, color = InkMuted)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                RevisaoItem("Tipo de conta", tipo)
                HorizontalDivider(color = SurfaceOff)
                RevisaoItem("Nome", nome.ifEmpty { "—" })
                HorizontalDivider(color = SurfaceOff)
                RevisaoItem("E-mail", email.ifEmpty { "—" })
                HorizontalDivider(color = SurfaceOff)
                RevisaoItem("Status", "Aguardando verificação")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = aceito,
                onCheckedChange = { aceito = it },
                colors = CheckboxDefaults.colors(checkedColor = Verde)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Li e aceito os Termos de Uso e a Política de Privacidade.",
                fontSize = 13.sp, color = InkSoft, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (erro.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(erro, color = Urgente, fontSize = 13.sp)
            }
        }

        Button(
            onClick = {
                if (!aceito || email.isEmpty()) return@Button
                loading = true
                erro = ""
                scope.launch {
                    val tipoSupabase = when {
                        tipo.contains("Certificado") -> "profissional_certificado"
                        tipo.contains("Liberal") -> "profissional_liberal"
                        else -> "cliente"
                    }
                    if (senha.isEmpty()) {
                        erro = "A senha é obrigatória"
                        loading = false
                        return@launch
                    }
                    val sucesso = signUpAndroid(
                        email = email,
                        senha = senha,
                        nome = nome,
                        telefone = "",
                        tipo = tipoSupabase
                    )

// 4. Finaliza o estado de carregamento
                    loading = false

// 5. Trata o resultado
                    if (sucesso) {
                        AnalyticsTracker.signUp(tipoSupabase) // PA-05
                        onProximo()
                    } else {
                        erro = "Erro ao criar conta. Tente novamente."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = aceito && !loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Azul, contentColor = Color.White,
                disabledContainerColor = SurfaceOff, disabledContentColor = InkMuted
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Enviar cadastro para verificação", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RevisaoItem(label: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = InkMuted)
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}