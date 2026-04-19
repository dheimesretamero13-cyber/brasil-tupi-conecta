package br.com.brasiltupi.conecta

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*

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

val dadosProfissionalMock = DadosProfissional(
    nome = "Dr. Carlos Henrique",
    area = "Saúde e Bem-estar",
    tipo = "Profissional Certificado",
    conselho = "CRM 45.231/SP",
    cidade = "São Paulo, SP",
    credibilidade = 78,
    isPMP = false,
    atendimentosTotal = 47,
    avaliacaoMedia = 4.8,
    disponivelUrgente = true,
    membroDesde = "Janeiro 2025",
    descricao = "Médico especialista com 12 anos de experiência em clínica geral e medicina preventiva.",
    email = "carlos@email.com",
    telefone = "(11) 98765-4321",
)

// ── TELA PRINCIPAL ────────────────────────────────────
@Composable
fun PerfilProfissionalScreen(onVoltar: () -> Unit) {
    var abaSelecionada by remember { mutableStateOf("perfil") }

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
            TextButton(onClick = onVoltar) {
                Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dadosProfissionalMock.nome.split(" ").map { it[0] }.joinToString("").take(2),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(dadosProfissionalMock.nome, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(dadosProfissionalMock.area, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    Text("📍 ${dadosProfissionalMock.cidade}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFDF3D8), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(dadosProfissionalMock.tipo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC49A2A))
                }
                if (dadosProfissionalMock.isPMP) {
                    Box(
                        modifier = Modifier
                            .background(DouradoClaro, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("🏆 PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                    }
                }
            }
        }

        // Stats rápidas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(vertical = 4.dp)
        ) {
            listOf(
                "${dadosProfissionalMock.atendimentosTotal}" to "Atendimentos",
                "⭐ ${dadosProfissionalMock.avaliacaoMedia}" to "Avaliação",
                "${dadosProfissionalMock.credibilidade}/100" to "Credibilidade",
            ).forEach { (num, label) ->
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(num, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(label, fontSize = 10.sp, color = InkMuted)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("perfil" to "Meu Perfil", "seguranca" to "Segurança", "urgente" to "Urgente").forEach { (id, label) ->
                TextButton(
                    onClick = { abaSelecionada = id },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (abaSelecionada == id) Verde else InkMuted
                    )
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = SurfaceOff)

        // Conteúdo da aba
        when (abaSelecionada) {
            "perfil"    -> AbaPerfilProfissional()
            "seguranca" -> AbaSegurancaProfissional()
            "urgente"   -> AbaUrgenteProfissional()
        }
    }
}

// ── ABA: PERFIL ───────────────────────────────────────
@Composable
fun AbaPerfilProfissional() {
    var editando by remember { mutableStateOf(false) }
    var nome by remember { mutableStateOf(dadosProfissionalMock.nome) }
    var descricao by remember { mutableStateOf(dadosProfissionalMock.descricao) }
    var cidade by remember { mutableStateOf(dadosProfissionalMock.cidade) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card principal
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                                unfocusedBorderColor = Color(0xFFE0E0E0)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    CampoTexto("Cidade / Estado", cidade, { cidade = it }, "Sua cidade")
                    Spacer(modifier = Modifier.height(16.dp))
                    BotaoProximo("Salvar alterações") { editando = false }
                } else {
                    listOf(
                        "Nome" to nome,
                        "Área" to dadosProfissionalMock.area,
                        "Cidade" to cidade,
                        "Membro desde" to dadosProfissionalMock.membroDesde,
                    ).forEach { (label, valor) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
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

        // Dados profissionais
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Dados profissionais", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "Tipo de conta" to dadosProfissionalMock.tipo,
                    "Conselho" to dadosProfissionalMock.conselho,
                    "Atendimentos" to "${dadosProfissionalMock.atendimentosTotal} realizados",
                    "Avaliação média" to "⭐ ${dadosProfissionalMock.avaliacaoMedia}",
                ).forEach { (label, valor) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, fontSize = 13.sp, color = InkMuted)
                        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }

        // Progresso PMP
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F0)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8B832).copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Programa PMP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dourado)
                        Text(
                            "Faltam ${100 - dadosProfissionalMock.credibilidade} pontos",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Azul, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", fontSize = 20.sp, color = DouradoMedio, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { dadosProfissionalMock.credibilidade / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = DouradoMedio,
                    trackColor = SurfaceOff,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${dadosProfissionalMock.credibilidade}/100 pontos de credibilidade",
                    fontSize = 12.sp, color = InkMuted
                )
                Spacer(modifier = Modifier.height(14.dp))
                listOf("Prioridade nas buscas", "Melhores comissões", "Acesso antecipado", "Selo visível").forEach { b ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Text("→", fontSize = 12.sp, color = Dourado)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(b, fontSize = 13.sp, color = InkSoft)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = dadosProfissionalMock.credibilidade >= 80,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Azul,
                        contentColor = Color.White,
                        disabledContainerColor = SurfaceOff,
                        disabledContentColor = InkMuted
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (dadosProfissionalMock.credibilidade >= 80) "Candidatar-me ao PMP"
                        else "Disponível com 80 pontos (${dadosProfissionalMock.credibilidade}/80)",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── ABA: SEGURANÇA ────────────────────────────────────
@Composable
fun AbaSegurancaProfissional() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Segurança da conta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    Triple("E-mail", dadosProfissionalMock.email, "Alterar"),
                    Triple("Senha", "••••••••••", "Alterar"),
                    Triple("Telefone", dadosProfissionalMock.telefone, "Alterar"),
                    Triple("Autenticação 2FA", "Desativado", "Ativar"),
                ).forEach { (label, valor, acao) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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

        // Info segurança
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VerdeClaro, RoundedCornerShape(10.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("🔒", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Seus dados estão protegidos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                Text(
                    "Todas as informações são criptografadas e nunca compartilhadas com terceiros.",
                    fontSize = 12.sp, color = Verde.copy(alpha = 0.8f), lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ── ABA: URGENTE ──────────────────────────────────────
@Composable
fun AbaUrgenteProfissional() {
    var ativo by remember { mutableStateOf(dadosProfissionalMock.disponivelUrgente) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Área Urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "Quando ativo, você aparece para clientes que precisam de atendimento imediato.",
                            fontSize = 13.sp, color = InkMuted, lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = ativo,
                        onCheckedChange = { ativo = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Verde
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (ativo) VerdeClaro else UrgenteClaro,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (ativo) "🟢" else "🔴", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (ativo) "Você está disponível para consultas urgentes"
                        else "Você está indisponível para consultas urgentes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ativo) Verde else Urgente
                    )
                }
            }
        }

        // Regras
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Regras do Acordo de Prontidão", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                listOf(
                    "⏱" to "45 minutos" to "Tempo máximo para iniciar o atendimento.",
                    "📋" to "15 minutos" to "Duração máxima da consulta.",
                    "⚠" to "Descumprimento" to "Atrasos resultam em perda de credibilidade.",
                    "🚫" to "Reincidência" to "Suspensão do acesso à área urgente.",
                ).forEach { (iconTitulo, desc) ->
                    val (icon, titulo) = iconTitulo
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
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

        // Histórico
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Seu histórico urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "3" to "Urgentes\nrealizadas" to Verde,
                        "100%" to "Taxa de\npontualidade" to Azul,
                        "0" to "Descum-\nprimenetos" to Dourado,
                    ).forEach { (numLabel, cor) ->
                        val (num, label) = numLabel
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(num, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cor)
                            Text(label, fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 4.dp))
                        }
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
                    Text("✓", fontSize = 14.sp, color = Verde, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Histórico limpo — acesso integral à área urgente.", fontSize = 12.sp, color = Verde)
                }
            }
        }
    }
}