package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// ModalidadesScreen.kt
//
// Tela do PROFISSIONAL para configurar:
//   - Quais modalidades de atendimento oferece (tipo, duração, valor)
//   - Horários de disponibilidade por dia da semana
//
// Navegação: dashboard-profissional → "modalidades" → volta para dashboard
// ═══════════════════════════════════════════════════════════════════════════

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ModalidadesScreen(
    userId:   String,
    onVoltar: () -> Unit,
) {
    var abaSelecionada by remember { mutableStateOf("modalidades") }
    val scope = rememberCoroutineScope()

    var modalidades     by remember { mutableStateOf<List<ModalidadeAtendimento>>(emptyList()) }
    var disponibilidade by remember { mutableStateOf<List<DisponibilidadeRegular>>(emptyList()) }
    var agendamentos    by remember { mutableStateOf<List<AgendamentoRegular>>(emptyList()) }
    var carregando      by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        if (userId.isEmpty()) { carregando = false; return@LaunchedEffect }
        carregando = true
        modalidades     = AtendimentosRepository.buscarMinhasModalidades(userId)
        disponibilidade = AtendimentosRepository.buscarMinhaDisponibilidade(userId)
        agendamentos    = AtendimentosRepository.buscarAgendamentosProfissional(userId)
        carregando      = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Azul)
                .padding(top = 44.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            TextButton(
                onClick  = onVoltar,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Text("← Voltar", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Text(
                "Atendimentos Regulares",
                modifier   = Modifier.align(Alignment.Center),
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
            )
        }

        // ── Tabs ──────────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = listOf("agendamentos","modalidades","horarios").indexOf(abaSelecionada),
            containerColor   = Surface,
            contentColor     = Verde,
        ) {
            listOf(
                "agendamentos" to "Agendamentos",
                "modalidades"  to "Modalidades",
                "horarios"     to "Horários",
            ).forEachIndexed { i, (id, label) ->
                Tab(
                    selected = abaSelecionada == id,
                    onClick  = { abaSelecionada = id },
                    text     = {
                        Text(
                            label,
                            fontSize   = 13.sp,
                            fontWeight = if (abaSelecionada == id) FontWeight.Bold else FontWeight.Normal,
                            color      = if (abaSelecionada == id) Verde else InkMuted,
                        )
                    }
                )
            }
        }
        HorizontalDivider(color = SurfaceOff)

        if (carregando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Verde)
            }
            return@Column
        }

        when (abaSelecionada) {
            "agendamentos" -> AbaAgendamentosRecebidos(
                agendamentos = agendamentos,
                modalidades  = modalidades,
                onAtualizar  = {
                    scope.launch {
                        agendamentos = AtendimentosRepository.buscarAgendamentosProfissional(userId)
                    }
                },
            )
            "modalidades" -> AbaModalidades(
                userId      = userId,
                modalidades = modalidades,
                onAtualizar = {
                    scope.launch {
                        modalidades = AtendimentosRepository.buscarMinhasModalidades(userId)
                    }
                },
            )
            "horarios" -> AbaHorarios(
                userId          = userId,
                modalidades     = modalidades,
                disponibilidade = disponibilidade,
                onAtualizar     = {
                    scope.launch {
                        disponibilidade = AtendimentosRepository.buscarMinhaDisponibilidade(userId)
                    }
                },
            )
        }
    }
}

// ── ABA: AGENDAMENTOS RECEBIDOS ───────────────────────────────────────────
@Composable
private fun AbaAgendamentosRecebidos(
    agendamentos: List<AgendamentoRegular>,
    modalidades:  List<ModalidadeAtendimento>,
    onAtualizar:  () -> Unit,
) {
    val statusLabel = mapOf(
        "pendente"               to ("Aguardando pagamento" to Color(0xFFF57F17)),
        "confirmado"             to ("Confirmado"           to Verde),
        "em_andamento"           to ("Em andamento"         to Azul),
        "concluido"              to ("Concluído"            to Verde),
        "cancelado_cliente"      to ("Cancelado pelo cliente"      to Urgente),
        "cancelado_profissional" to ("Cancelado por você"          to Urgente),
        "no_show"                to ("Não compareceu"      to InkMuted),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (agendamentos.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("📭", fontSize = 32.sp)
                    Text(
                        "Nenhum agendamento ainda",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Ink,
                    )
                    Text(
                        "Os agendamentos feitos por clientes aparecerão aqui.",
                        fontSize  = 13.sp,
                        color     = InkMuted,
                    )
                }
            }
        } else {
            // Separar por status
            val proximos  = agendamentos.filter { it.status in listOf("pendente", "confirmado", "em_andamento") }
            val historico = agendamentos.filter { it.status !in listOf("pendente", "confirmado", "em_andamento") }

            if (proximos.isNotEmpty()) {
                Text("Próximos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkMuted)
                proximos.forEach { ag ->
                    CardAgendamentoRecebido(ag, modalidades, statusLabel)
                }
            }
            if (historico.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Histórico", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkMuted)
                historico.forEach { ag ->
                    CardAgendamentoRecebido(ag, modalidades, statusLabel)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CardAgendamentoRecebido(
    ag:          AgendamentoRegular,
    modalidades: List<ModalidadeAtendimento>,
    statusLabel: Map<String, Pair<String, Color>>,
) {
    val titulo   = modalidades.find { it.id == ag.modalidadeId }?.titulo ?: "Atendimento"
    val (label, cor) = statusLabel[ag.status] ?: ("${ag.status}" to InkMuted)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${ag.dataAgendada}  ${ag.horaInicio} – ${ag.horaFim}",
                        fontSize = 13.sp,
                        color    = InkMuted,
                    )
                }
                Box(
                    modifier = Modifier
                        .background(cor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cor)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SurfaceOff)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "R$ ${"%.2f".format(ag.valorCobrado)}",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = Verde,
            )
        }
    }
}

// ── ABA: MODALIDADES ──────────────────────────────────────────────────────
@Composable
private fun AbaModalidades(
    userId:      String,
    modalidades: List<ModalidadeAtendimento>,
    onAtualizar: () -> Unit,
) {
    var mostrarFormNova by remember { mutableStateOf(false) }
    var editando        by remember { mutableStateOf<ModalidadeAtendimento?>(null) }
    val scope           = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Banner informativo
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE3F2FD), RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("📋", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Seus serviços",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF1565C0),
                )
                Text(
                    "Crie pacotes, consultas avulsas ou programas recorrentes. " +
                    "Tudo que você configurar aqui aparecerá para seus clientes no momento do agendamento.",
                    fontSize   = 13.sp,
                    color      = Color(0xFF1565C0).copy(alpha = 0.8f),
                    lineHeight = 19.sp,
                )
            }
        }

        // Lista de modalidades existentes
        if (modalidades.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("📭", fontSize = 32.sp)
                    Text(
                        "Nenhuma modalidade configurada",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Ink,
                    )
                    Text(
                        "Adicione seu primeiro tipo de atendimento abaixo.",
                        fontSize = 13.sp,
                        color    = InkMuted,
                    )
                }
            }
        } else {
            modalidades.forEach { mod ->
                CardModalidade(
                    modalidade  = mod,
                    onEditar    = { editando = mod },
                    onDesativar = {
                        scope.launch {
                            AtendimentosRepository.atualizarModalidade(
                                id             = mod.id,
                                titulo         = mod.titulo,
                                descricao      = mod.descricao,
                                duracaoMinutos = mod.duracaoMinutos,
                                valor          = mod.valor,
                                ativo          = !mod.ativo,
                            )
                            onAtualizar()
                        }
                    },
                )
            }
        }

        // Botão adicionar nova
        Button(
            onClick  = { mostrarFormNova = true; editando = null },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Verde),
        ) {
            Text("+ Adicionar modalidade", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Dialog: Nova / Editar modalidade
    var salvandoModal   by remember { mutableStateOf(false) }
    var erroModal       by remember { mutableStateOf<String?>(null) }

    if (mostrarFormNova || editando != null) {
        DialogModalidade(
            inicial   = editando,
            salvando  = salvandoModal,
            erro      = erroModal,
            onSalvar  = { tipo, titulo, descricao, duracao, sessoesSemana, sessoesTotal, valor, modeloCobranca, duracaoMeses, horasPorSemana ->
                salvandoModal = true
                erroModal     = null
                scope.launch {
                    val ok = if (editando != null) {
                        AtendimentosRepository.atualizarModalidade(
                            id               = editando!!.id,
                            titulo           = titulo,
                            descricao        = descricao.ifBlank { null },
                            duracaoMinutos   = duracao,
                            sessoesPorSemana = sessoesSemana,
                            sessoesTotal     = sessoesTotal,
                            valor            = valor,
                            modeloCobranca   = modeloCobranca,
                            duracaoMeses     = duracaoMeses,
                            horasPorSemana   = horasPorSemana,
                            ativo            = true,
                        )
                    } else {
                        AtendimentosRepository.criarModalidade(
                            profissionalId   = userId,
                            tipo             = tipo,
                            titulo           = titulo,
                            descricao        = descricao.ifBlank { null },
                            duracaoMinutos   = duracao,
                            sessoesPorSemana = sessoesSemana,
                            sessoesTotal     = sessoesTotal,
                            valor            = valor,
                            modeloCobranca   = modeloCobranca,
                            duracaoMeses     = duracaoMeses,
                            horasPorSemana   = horasPorSemana,
                        )
                    }
                    salvandoModal = false
                    if (ok) {
                        onAtualizar()
                        mostrarFormNova = false
                        editando        = null
                        erroModal       = null
                    } else {
                        erroModal = "Não foi possível salvar. Verifique sua conexão e tente novamente."
                    }
                }
            },
            onDismiss = {
                if (!salvandoModal) {
                    mostrarFormNova = false
                    editando        = null
                    erroModal       = null
                }
            },
        )
    }
}

@Composable
private fun CardModalidade(
    modalidade:  ModalidadeAtendimento,
    onEditar:    () -> Unit,
    onDesativar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (modalidade.ativo) Surface else SurfaceOff.copy(alpha = 0.5f)
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            modalidade.titulo,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color      = if (modalidade.ativo) Ink else InkMuted,
                        )
                        // Badge tipo
                        Box(
                            modifier = Modifier
                                .background(
                                    when (modalidade.tipo) {
                                        "hora"    -> Color(0xFFE3F2FD)
                                        "semanal" -> Color(0xFFE8F5E9)
                                        "mensal"  -> Color(0xFFF3E5F5)
                                        else      -> Color(0xFFFFF8E1)
                                    },
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                AtendimentosRepository.tipoLabel(modalidade.tipo),
                                fontSize = 10.sp,
                                color    = when (modalidade.tipo) {
                                    "hora"    -> Color(0xFF1565C0)
                                    "semanal" -> Color(0xFF2E7D32)
                                    "mensal"  -> Color(0xFF6A1B9A)
                                    else      -> Color(0xFFF57F17)
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (!modalidade.ativo) {
                            Box(
                                modifier = Modifier
                                    .background(SurfaceOff, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Inativo", fontSize = 10.sp, color = InkMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Badge modelo de cobrança
                    Box(
                        modifier = Modifier
                            .background(
                                when (modalidade.modeloCobranca) {
                                    "integral" -> Color(0xFFE8F5E9)
                                    "fidelidade" -> Color(0xFFF3E5F5)
                                    else -> SurfaceOff
                                },
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            when (modalidade.modeloCobranca) {
                                "integral" -> "Pacote"
                                "fidelidade" -> "Fidelidade"
                                else -> "Avulso"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (modalidade.modeloCobranca) {
                                "integral" -> Color(0xFF2E7D32)
                                "fidelidade" -> Color(0xFF6A1B9A)
                                else -> InkMuted
                            }
                        )
                    }
                    if (!modalidade.descricao.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(modalidade.descricao, fontSize = 12.sp, color = InkMuted, lineHeight = 17.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        // Duração / sessões conforme tipo
                        when (modalidade.tipo) {
                            "minutos" -> if (modalidade.duracaoMinutos != null)
                                Text("⏱ ${modalidade.duracaoMinutos}min", fontSize = 12.sp, color = InkMuted)
                            "hora"    -> Text("⏱ 60min", fontSize = 12.sp, color = InkMuted)
                            "semanal" -> if (modalidade.sessoesPorSemana != null) {
                                Text("📆 ${modalidade.sessoesPorSemana}x/sem", fontSize = 12.sp, color = InkMuted)
                                if (modalidade.duracaoMinutos != null)
                                    Text("· ${modalidade.duracaoMinutos}min", fontSize = 12.sp, color = InkMuted)
                            }
                            "mensal"  -> if (modalidade.sessoesTotal != null) {
                                Text("🗓 ${modalidade.sessoesTotal} sessões", fontSize = 12.sp, color = InkMuted)
                                if (modalidade.duracaoMinutos != null)
                                    Text("· ${modalidade.duracaoMinutos}min", fontSize = 12.sp, color = InkMuted)
                            }
                        }
                        Text(
                            "R$ ${"%.2f".format(modalidade.valor)}",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Verde,
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SurfaceOff)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEditar) {
                    Text("✏ Editar", color = Azul, fontSize = 12.sp)
                }
                TextButton(onClick = onDesativar) {
                    Text(
                        if (modalidade.ativo) "Desativar" else "Reativar",
                        color    = if (modalidade.ativo) Urgente else Verde,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogModalidade(
    inicial:   ModalidadeAtendimento?,
    salvando:  Boolean,
    erro:      String?,
    isPmp:     Boolean = false,
    verificado: Boolean = false,
    onSalvar:  (
        tipo:            String,
        titulo:          String,
        descricao:       String,
        duracaoMinutos:  Int?,
        sessoesPorSemana: Int?,
        sessoesTotal:    Int?,
        valor:           Double,
        modeloCobranca:  String,
        duracaoMeses:    Int?,
        horasPorSemana:  Int?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var tipo             by remember { mutableStateOf(inicial?.tipo ?: "minutos") }
    var titulo           by remember { mutableStateOf(inicial?.titulo ?: "") }
    var descricao        by remember { mutableStateOf(inicial?.descricao ?: "") }
    var duracaoStr       by remember { mutableStateOf(inicial?.duracaoMinutos?.toString() ?: "") }
    var sessoesSemanaStr by remember { mutableStateOf(inicial?.sessoesPorSemana?.toString() ?: "") }
    var sessoesTotalStr  by remember { mutableStateOf(inicial?.sessoesTotal?.toString() ?: "") }
    var valorStr         by remember { mutableStateOf(inicial?.valor?.let { "%.2f".format(it) } ?: "") }
    var modeloCobranca     by remember { mutableStateOf(inicial?.modeloCobranca ?: "avulso") }
    var duracaoMesesStr    by remember { mutableStateOf(inicial?.duracaoMeses?.toString() ?: "") }
    var horasPorSemanaStr  by remember { mutableStateOf(inicial?.horasPorSemana?.toString() ?: "") }
    var isPmpDialog by remember { mutableStateOf(false) }
    var verificadoDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val perfil = getMeuPerfilProfissional(currentUserId ?: "")
        isPmpDialog = perfil?.is_pmp ?: false
        verificadoDialog = perfil?.verificado ?: false
    }
    val duracaoMinutos   = duracaoStr.toIntOrNull()
    val sessoesSemana    = sessoesSemanaStr.toIntOrNull()
    val sessoesTotal     = sessoesTotalStr.toIntOrNull()
    val valor            = valorStr.replace(",", ".").toDoubleOrNull()

    val formValido = titulo.isNotBlank() && valor != null && when (tipo) {
        "minutos" -> duracaoMinutos != null && duracaoMinutos > 0
        "hora"    -> true
        "semanal" -> sessoesSemana != null && sessoesSemana > 0
        "mensal"  -> sessoesTotal  != null && sessoesTotal  > 0
        else      -> true
    }

    Dialog(onDismissRequest = { if (!salvando) onDismiss() }) {
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (inicial != null) "Editar modalidade" else "Nova modalidade",
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Ink,
                )

                if (inicial == null) {
                    Text("Tipo de atendimento", fontSize = 12.sp, color = InkMuted)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "minutos" to "⏱ Sessão (min)",
                                "hora" to "🕐 Sessão (hora)"
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = tipo == id,
                                    onClick = { tipo = id },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Azul.copy(alpha = 0.12f),
                                        selectedLabelColor = Azul,
                                    ),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "semanal" to "📆 Pacote semanal",
                                "mensal" to "🗓 Pacote mensal"
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = tipo == id,
                                    onClick = { tipo = id },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Verde.copy(alpha = 0.12f),
                                        selectedLabelColor = Verde,
                                    ),
                                )
                            }
                        }
                    }

                    val bannerTexto = when (tipo) {
                        "minutos" -> "O cliente agenda uma sessão avulsa. Você define a duração em minutos e o horário disponível."
                        "hora" -> "Sessão de 60 minutos. Você define o horário disponível."
                        "semanal" -> "Pacote recorrente. Você define quantas sessões por semana e o valor semanal."
                        "mensal" -> "Pacote fechado. Você define o total de sessões e o valor único do pacote."
                        else -> ""
                    }
                    if (bannerTexto.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceWarm, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("💡", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(bannerTexto, fontSize = 12.sp, color = InkSoft, lineHeight = 17.sp)
                        }
                    }
                }
                    // ── Modelo de cobrança ─────────────────────────
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Modelo de cobrança", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "avulso" to "Avulso",
                            "integral" to "Pacote integral",
                            "fidelidade" to "Fidelidade mensal"
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = modeloCobranca == id,
                                onClick = { modeloCobranca = id },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Azul.copy(alpha = 0.15f),
                                    selectedLabelColor = Azul,
                                )
                            )
                        }
                    }

                    if (modeloCobranca == "integral" || modeloCobranca == "fidelidade") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = duracaoMesesStr,
                            onValueChange = { duracaoMesesStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Duração (meses)") },
                            placeholder = { Text("Ex: 3") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                        )
                    }

                    if (modeloCobranca == "fidelidade") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = horasPorSemanaStr,
                            onValueChange = { horasPorSemanaStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Horas por semana") },
                            placeholder = { Text("Ex: 2") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                        )
                    }


                OutlinedTextField(
                    value         = titulo,
                    onValueChange = { titulo = it },
                    label         = { Text("Título do pacote") },
                    placeholder   = {
                        Text(when (tipo) {
                            "minutos" -> "Ex: Consulta de 30 minutos"
                            "hora"    -> "Ex: Sessão completa 1h"
                            "semanal" -> "Ex: Acompanhamento semanal"
                            "mensal"  -> "Ex: Pacote mensal 4 sessões"
                            else      -> "Título"
                        })
                    },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                )

                OutlinedTextField(
                    value         = descricao,
                    onValueChange = { descricao = it },
                    label         = { Text("Descrição (opcional)") },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 2,
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                )

                when (tipo) {
                    "minutos" -> {
                        OutlinedTextField(
                            value         = duracaoStr,
                            onValueChange = { duracaoStr = it.filter { c -> c.isDigit() } },
                            label         = { Text("Duração da sessão (minutos)") },
                            placeholder   = { Text("Ex: 30") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                            supportingText = { Text("Duração de cada sessão agendada.", fontSize = 11.sp) },
                        )
                    }
                    "hora" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceWarm, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🕐", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Duração fixa: 60 minutos por sessão.", fontSize = 12.sp, color = InkSoft)
                        }
                    }
                    "semanal" -> {
                        OutlinedTextField(
                            value         = sessoesSemanaStr,
                            onValueChange = { sessoesSemanaStr = it.filter { c -> c.isDigit() } },
                            label         = { Text("Sessões por semana") },
                            placeholder   = { Text("Ex: 2") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                            supportingText = { Text("Quantas vezes por semana o cliente terá sessão.", fontSize = 11.sp) },
                        )
                        OutlinedTextField(
                            value         = duracaoStr,
                            onValueChange = { duracaoStr = it.filter { c -> c.isDigit() } },
                            label         = { Text("Duração de cada sessão (minutos)") },
                            placeholder   = { Text("Ex: 60") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                        )
                    }
                    "mensal" -> {
                        OutlinedTextField(
                            value         = sessoesTotalStr,
                            onValueChange = { sessoesTotalStr = it.filter { c -> c.isDigit() } },
                            label         = { Text("Total de sessões no pacote") },
                            placeholder   = { Text("Ex: 4") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                            supportingText = { Text("Total de sessões incluídas no pacote mensal.", fontSize = 11.sp) },
                        )
                        OutlinedTextField(
                            value         = duracaoStr,
                            onValueChange = { duracaoStr = it.filter { c -> c.isDigit() } },
                            label         = { Text("Duração de cada sessão (minutos)") },
                            placeholder   = { Text("Ex: 60") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                        )
                    }
                }

                OutlinedTextField(
                    value         = valorStr,
                    onValueChange = { valorStr = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = {
                        Text(when (tipo) {
                            "semanal" -> "Valor semanal (R$)"
                            "mensal"  -> "Valor total do pacote (R$)"
                            else      -> "Valor da sessão (R$)"
                        })
                    },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                )

                    if (valor != null && valor > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        IndicadorTaxaPlataforma(
                            valorBruto = valor,
                            isPmp = isPmpDialog,
                            verificado = verificadoDialog,
                            modifier = Modifier
                        )
                    }

                if (formValido && valor != null) {
                    val resumo = when (tipo) {
                        "minutos" -> "Sessão de ${duracaoMinutos}min · R$ ${"%.2f".format(valor)}"
                        "hora"    -> "Sessão de 60min · R$ ${"%.2f".format(valor)}"
                        "semanal" -> "${sessoesSemana}x/semana · ${duracaoMinutos ?: 60}min · R$ ${"%.2f".format(valor)}/semana"
                        "mensal"  -> "${sessoesTotal} sessões · ${duracaoMinutos ?: 60}min cada · R$ ${"%.2f".format(valor)} total"
                        else      -> ""
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Verde.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("✓", fontSize = 14.sp, color = Verde, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(resumo, fontSize = 12.sp, color = Verde, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (erro != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrgenteClaro, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚠️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(erro, fontSize = 12.sp, color = Urgente, lineHeight = 16.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = { if (!salvando) onDismiss() },
                        enabled  = !salvando,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                    ) {
                        Text("Cancelar", color = InkMuted)
                    }
                    Button(
                        onClick = {
                            if (!formValido || valor == null) return@Button
                            val dur = when (tipo) {
                                "hora"  -> 60
                                else    -> duracaoMinutos
                            }
                            val durMeses = duracaoMesesStr.toIntOrNull()
                            val hrsSemana = horasPorSemanaStr.toIntOrNull()
                            onSalvar(
                                tipo,
                                titulo.trim(),
                                descricao.trim(),
                                dur,
                                sessoesSemana,
                                sessoesTotal,
                                valor,
                                modeloCobranca,
                                durMeses,
                                hrsSemana
                            )
                        },
                        enabled  = formValido && !salvando,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Verde),
                    ) {
                        if (salvando) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (salvando) "Salvando..." else "Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── ABA: HORÁRIOS ─────────────────────────────────────────────────────────
@Composable
private fun AbaHorarios(
    userId:          String,
    modalidades:     List<ModalidadeAtendimento>,
    disponibilidade: List<DisponibilidadeRegular>,
    onAtualizar:     () -> Unit,
) {
    var mostrarFormNovo by remember { mutableStateOf(false) }
    var disponibilidadeEditando by remember { mutableStateOf<DisponibilidadeRegular?>(null) }
    val scope = rememberCoroutineScope()

    val diasSemana = listOf(
        "segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Banner informativo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3E5F5), RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("🕐", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Sua agenda semanal",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF6A1B9A),
                )
                Text(
                    "Defina seus horários de atendimento para cada dia da semana. " +
                            "Vincule cada faixa a uma modalidade ou deixe livre para todas.",
                    fontSize   = 13.sp,
                    color      = Color(0xFF6A1B9A).copy(alpha = 0.8f),
                    lineHeight = 19.sp,
                )
            }
        }

        // Agrupar por dia da semana
        diasSemana.forEach { dia ->
            val slotsDia = disponibilidade.filter { it.diaSemana == dia }
            if (slotsDia.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = Surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            AtendimentosRepository.diaSemanaLabel(dia),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Ink,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        slotsDia.forEach { slot ->
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        "${slot.horaInicio} – ${slot.horaFim}",
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = Ink,
                                    )
                                    val modNome = modalidades.find { it.id == slot.modalidadeId }?.titulo
                                    if (modNome != null) {
                                        Text(modNome, fontSize = 11.sp, color = InkMuted)
                                    } else {
                                        Text("Todas as modalidades", fontSize = 11.sp, color = InkMuted)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = {
                                        disponibilidadeEditando = slot
                                        mostrarFormNovo = true
                                    }) {
                                        Text("✏️", fontSize = 16.sp)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            AtendimentosRepository.removerDisponibilidade(slot.id)
                                            onAtualizar()
                                        }
                                    }) {
                                        Text("🗑", fontSize = 16.sp)
                                    }
                                }
                            }
                            HorizontalDivider(color = SurfaceOff)
                        }
                    }
                }
            }
        }

        if (disponibilidade.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("📅", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sua agenda está vazia",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Ink,
                    )
                    Text(
                        "Organize seus horários semanais para começar a receber agendamentos.",
                        fontSize = 13.sp,
                        color    = InkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Button(
            onClick  = { mostrarFormNovo = true; disponibilidadeEditando = null },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Azul),
        ) {
            Text("+ Adicionar horário", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (mostrarFormNovo) {
        DialogHorario(
            modalidades = modalidades,
            disponibilidadeEdicao = disponibilidadeEditando,
            onSalvar = { dia, modalidadeId, horaInicio, horaFim ->
                val ok = if (disponibilidadeEditando != null) {
                    AtendimentosRepository.atualizarDisponibilidade(
                        id = disponibilidadeEditando!!.id,
                        diaSemana = dia,
                        modalidadeId = modalidadeId,
                        horaInicio = horaInicio,
                        horaFim = horaFim,
                    )
                } else {
                    AtendimentosRepository.criarDisponibilidade(
                        profissionalId = userId,
                        modalidadeId = modalidadeId.ifBlank { null },
                        diaSemana = dia,
                        horaInicio = horaInicio,
                        horaFim = horaFim,
                    )
                }
                ok   // retorno explícito para o DialogHorario
            },
            onDismiss = {
                mostrarFormNovo = false
                disponibilidadeEditando = null
                scope.launch { onAtualizar() }
            },
            onDelete = if (disponibilidadeEditando != null) {{
                AtendimentosRepository.removerDisponibilidade(disponibilidadeEditando!!.id)
            }} else null,
        )
    }
}
@Composable
private fun DialogHorario(
    modalidades:            List<ModalidadeAtendimento>,
    disponibilidadeEdicao:  DisponibilidadeRegular? = null,
    onSalvar:               suspend (dia: String, modalidadeId: String, horaInicio: String, horaFim: String) -> Boolean,
    onDismiss:              () -> Unit,
    onDelete:               (suspend () -> Unit)? = null,
) {
    val diasSemana = listOf(
        "segunda" to "Segunda", "terca" to "Terça", "quarta" to "Quarta",
        "quinta" to "Quinta", "sexta" to "Sexta", "sabado" to "Sábado", "domingo" to "Domingo",
    )

    var diaSelecionado  by remember { mutableStateOf(disponibilidadeEdicao?.diaSemana ?: "segunda") }
    var modalidadeId    by remember { mutableStateOf(disponibilidadeEdicao?.modalidadeId ?: "") }
    var horaInicio      by remember { mutableStateOf(disponibilidadeEdicao?.horaInicio ?: "09:00") }
    var horaFim         by remember { mutableStateOf(disponibilidadeEdicao?.horaFim ?: "10:00") }
    var salvando        by remember { mutableStateOf(false) }
    var erro            by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!salvando) onDismiss() }) {
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier.heightIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (disponibilidadeEdicao == null) "Novo horário" else "Editar horário",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink
                )

                Text("Dia da semana", fontSize = 12.sp, color = InkMuted)
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    diasSemana.chunked(4).forEach { grupo ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            grupo.forEach { (id, label) ->
                                FilterChip(
                                    selected = diaSelecionado == id,
                                    onClick  = { diaSelecionado = id },
                                    label    = { Text(label, fontSize = 11.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Azul.copy(alpha = 0.15f),
                                        selectedLabelColor     = Azul,
                                    ),
                                )
                            }
                        }
                    }
                }

                if (modalidades.isNotEmpty()) {
                    Text("Modalidade (opcional)", fontSize = 12.sp, color = InkMuted)
                    Text("Deixe em branco para aplicar a todas.", fontSize = 11.sp, color = InkMuted)
                    var expandido by remember { mutableStateOf(false) }
                    val nomeSelecionado = modalidades.find { it.id == modalidadeId }?.titulo ?: "Todas as modalidades"
                    Box {
                        OutlinedButton(
                            onClick = { expandido = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(nomeSelecionado, fontSize = 13.sp, color = Ink) }
                        DropdownMenu(
                            expanded         = expandido,
                            onDismissRequest = { expandido = false },
                        ) {
                            DropdownMenuItem(
                                text    = { Text("Todas as modalidades") },
                                onClick = { modalidadeId = ""; expandido = false },
                            )
                            modalidades.forEach { mod ->
                                DropdownMenuItem(
                                    text    = { Text(mod.titulo) },
                                    onClick = { modalidadeId = mod.id; expandido = false },
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = horaInicio,
                        onValueChange = { horaInicio = it },
                        label         = { Text("Início") },
                        placeholder   = { Text("09:00") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                    )
                    OutlinedTextField(
                        value         = horaFim,
                        onValueChange = { horaFim = it },
                        label         = { Text("Fim") },
                        placeholder   = { Text("10:00") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde),
                    )
                }

                if (erro != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrgenteClaro, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚠️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(erro!!, fontSize = 12.sp, color = Urgente, lineHeight = 16.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = { if (!salvando) onDismiss() },
                        enabled  = !salvando,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                    ) { Text("Cancelar", color = InkMuted) }

                    Button(
                        onClick = {
                            if (horaInicio.isBlank() || horaFim.isBlank()) return@Button
                            salvando = true
                            erro = null
                            scope.launch {
                                val ok = onSalvar(diaSelecionado, modalidadeId, horaInicio, horaFim)
                                salvando = false
                                if (ok) {
                                    onDismiss()
                                } else {
                                    erro = "Falha ao salvar horário. Verifique sua conexão."
                                }
                            }
                        },
                        enabled  = !salvando && horaInicio.isNotBlank() && horaFim.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Verde),
                    ) {
                        if (salvando) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                if (disponibilidadeEdicao != null && onDelete != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (!salvando) {
                                scope.launch {
                                    salvando = true
                                    onDelete()
                                    salvando = false
                                    onDismiss()
                                }
                            }
                        },
                        enabled  = !salvando,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Urgente),
                        border   = BorderStroke(1.dp, Urgente.copy(alpha = 0.4f)),
                    ) {
                        Text("Excluir horário", color = Urgente, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}