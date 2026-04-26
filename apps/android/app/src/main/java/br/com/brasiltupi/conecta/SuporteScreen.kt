package br.com.brasiltupi.conecta

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════
// SuporteScreen.kt  · Fase 4.3
//
// Duas seções:
//  1. Formulário "Reportar Problema" — abre nova disputa
//  2. "Minhas Disputas" — lista com status em tempo real (pull-to-refresh)
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuporteScreen(
    agendamentoId: String? = null,   // pré-selecionado se vindo do histórico
    onVoltar:      () -> Unit,
) {
    val vm: DisputaViewModel = viewModel(
        factory = DisputaViewModelFactory(DisputaRepositoryFactory.create())
    )

    val listaState by vm.listaState.collectAsState()
    val abrirState by vm.abrirState.collectAsState()

    var tabSelecionada   by remember { mutableIntStateOf(if (agendamentoId != null) 0 else 1) }
    var categoriaSel     by remember { mutableStateOf<CategoriaDisputa?>(null) }
    var descricao        by remember { mutableStateOf("") }
    var mostrarSucesso   by remember { mutableStateOf(false) }

    // Ao abrir disputa com sucesso — mostrar feedback e ir para lista
    LaunchedEffect(abrirState) {
        if (abrirState is AbrirDisputaState.Sucesso) {
            mostrarSucesso   = true
            descricao        = ""
            categoriaSel     = null
            tabSelecionada   = 1
            vm.resetarAbrirState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suporte", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = Color(0xFFF8F7F4),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // ── Tabs ──────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = tabSelecionada,
                containerColor   = Color.White,
                contentColor     = Verde,
            ) {
                Tab(
                    selected = tabSelecionada == 0,
                    onClick  = { tabSelecionada = 0 },
                    text     = { Text("Reportar Problema") },
                )
                Tab(
                    selected = tabSelecionada == 1,
                    onClick  = { tabSelecionada = 1 },
                    text     = { Text("Minhas Disputas") },
                )
            }

            // ── Banner sucesso ────────────────────────────────────────────
            AnimatedVisibility(visible = mostrarSucesso) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "✅ Disputa aberta! Nossa equipe entrará em contato em até 48h.",
                        fontSize = 12.sp,
                        color    = Verde,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { mostrarSucesso = false }) {
                        Text("Ok", color = Verde)
                    }
                }
            }

            when (tabSelecionada) {
                0 -> FormularioReportar(
                    agendamentoId = agendamentoId,
                    categoriaSel  = categoriaSel,
                    descricao     = descricao,
                    abrirState    = abrirState,
                    onCategoria   = { categoriaSel = it; vm.resetarAbrirState() },
                    onDescricao   = { descricao = it; vm.resetarAbrirState() },
                    onEnviar      = {
                        vm.abrirDisputa(
                            agendamentoId = agendamentoId,
                            categoria     = categoriaSel!!,
                            descricao     = descricao,
                        )
                    },
                )
                1 -> ListaDisputas(
                    state      = listaState,
                    onRecarregar = { vm.carregarDisputas() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FORMULÁRIO — REPORTAR PROBLEMA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FormularioReportar(
    agendamentoId: String?,
    categoriaSel:  CategoriaDisputa?,
    descricao:     String,
    abrirState:    AbrirDisputaState,
    onCategoria:   (CategoriaDisputa) -> Unit,
    onDescricao:   (String) -> Unit,
    onEnviar:      () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Agendamento vinculado
        if (agendamentoId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF0F4FF), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("📋", fontSize = 16.sp)
                Text(
                    "Disputa vinculada ao agendamento: ${agendamentoId.take(8)}...",
                    fontSize = 12.sp,
                    color    = Azul,
                )
            }
        }

        // Categoria
        Text("Qual é o problema?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
        CategoriaDisputa.entries.forEach { cat ->
            val selecionada = categoriaSel == cat
            Card(
                onClick   = { onCategoria(cat) },
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(10.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = if (selecionada) Verde.copy(alpha = 0.08f) else Color.White,
                ),
                border    = androidx.compose.foundation.BorderStroke(
                    width = if (selecionada) 2.dp else 1.dp,
                    color = if (selecionada) Verde else Color(0xFFE0E0E0),
                ),
            ) {
                Row(
                    modifier          = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(cat.icone, fontSize = 22.sp)
                    Text(
                        cat.label,
                        fontSize   = 14.sp,
                        fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Normal,
                        color      = if (selecionada) Verde else Ink,
                        modifier   = Modifier.weight(1f),
                    )
                    if (selecionada) {
                        Text("✓", fontSize = 16.sp, color = Verde, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Descrição
        Text("Descreva o problema", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
        OutlinedTextField(
            value         = descricao,
            onValueChange = { if (it.length <= 2000) onDescricao(it) },
            modifier      = Modifier.fillMaxWidth().height(140.dp),
            placeholder   = {
                Text(
                    "Descreva com detalhes o que aconteceu (mínimo 20 caracteres)...",
                    fontSize = 13.sp,
                )
            },
            shape  = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Verde,
                unfocusedBorderColor = Color(0xFFE0E0E0),
            ),
        )
        Text(
            "${descricao.length}/2000",
            fontSize = 11.sp,
            color    = if (descricao.length < 20) Urgente else InkMuted,
            modifier = Modifier.align(Alignment.End),
        )

        // Erro
        if (abrirState is AbrirDisputaState.Erro) {
            Text(
                "⚠️ ${(abrirState as AbrirDisputaState.Erro).mensagem}",
                fontSize = 12.sp,
                color    = Urgente,
            )
        }

        // Prazo informativo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF3CD), RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⏱", fontSize = 14.sp)
            Text(
                "Nossa equipe responde em até 48 horas úteis.",
                fontSize = 12.sp,
                color    = Color(0xFF856404),
            )
        }

        // Botão enviar
        Button(
            onClick  = onEnviar,
            enabled  = categoriaSel != null
                    && descricao.length >= 20
                    && abrirState !is AbrirDisputaState.Enviando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Verde),
            shape    = RoundedCornerShape(10.dp),
        ) {
            if (abrirState is AbrirDisputaState.Enviando) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Enviar solicitação", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LISTA DE DISPUTAS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ListaDisputas(
    state:       DisputaListaState,
    onRecarregar: () -> Unit,
) {
    when (state) {
        is DisputaListaState.Carregando -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Verde)
            }
        }
        is DisputaListaState.Erro -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("⚠️", fontSize = 36.sp)
                    Text(state.mensagem, color = InkMuted, textAlign = TextAlign.Center)
                    Button(onClick = onRecarregar, colors = ButtonDefaults.buttonColors(containerColor = Verde)) {
                        Text("Tentar novamente", color = Color.White)
                    }
                }
            }
        }
        is DisputaListaState.Sucesso -> {
            if (state.disputas.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier            = Modifier.padding(32.dp),
                    ) {
                        Text("🎉", fontSize = 48.sp)
                        Text(
                            "Nenhuma disputa aberta",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Ink,
                        )
                        Text(
                            "Tudo certo por aqui! Se precisar de ajuda, use a aba ao lado.",
                            fontSize  = 13.sp,
                            color     = InkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${state.disputas.size} disputa(s)",
                                fontSize = 13.sp,
                                color    = InkMuted,
                            )
                            TextButton(onClick = onRecarregar) {
                                Text("↻ Atualizar", fontSize = 12.sp, color = Verde)
                            }
                        }
                    }
                    items(state.disputas, key = { it.id }) { disputa ->
                        CardDisputa(disputa = disputa)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CARD DE DISPUTA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CardDisputa(disputa: Disputa) {
    val (corStatus, labelStatus, iconeStatus) = when (disputa.status) {
        "aberta"      -> Triple(Color(0xFFF57F17), "Aberta",      "🟡")
        "em_analise"  -> Triple(Azul,              "Em análise",  "🔵")
        "resolvida"   -> Triple(Verde,             "Resolvida",   "🟢")
        "encerrada"   -> Triple(InkMuted,          "Encerrada",   "⚫")
        else          -> Triple(InkMuted,          disputa.status, "•")
    }

    val cat = CategoriaDisputa.fromId(disputa.categoria)
    val dataFormatada = disputa.criadoEm.take(10).split("-")
        .let { p -> if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else disputa.criadoEm.take(10) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(cat?.icone ?: "📝", fontSize = 18.sp)
                    Text(
                        cat?.label ?: disputa.categoria,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Ink,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(iconeStatus, fontSize = 10.sp)
                    Text(labelStatus, fontSize = 11.sp, color = corStatus, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Descrição
            Text(
                disputa.descricao.take(120) + if (disputa.descricao.length > 120) "..." else "",
                fontSize   = 13.sp,
                color      = InkMuted,
                lineHeight = 18.sp,
            )

            // Resolução (se houver)
            if (!disputa.resolucao.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("💬", fontSize = 13.sp)
                    Column {
                        Text("Resposta do suporte", fontSize = 11.sp, color = InkMuted)
                        Text(disputa.resolucao, fontSize = 13.sp, color = Ink, lineHeight = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Aberta em $dataFormatada", fontSize = 11.sp, color = InkMuted)
                Text("#${disputa.id.take(8)}", fontSize = 10.sp, color = InkMuted)
            }
        }
    }
}