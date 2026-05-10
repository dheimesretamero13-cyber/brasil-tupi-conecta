package br.com.brasiltupi.conecta

import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke


sealed class BuscaUiState {
    object Lista : BuscaUiState()
    data class Perfil(val prof: ProfissionalPMP) : BuscaUiState()
    data class Agendamento(val prof: ProfissionalPMP, val tipo: String) : BuscaUiState()
    data class Estudio(val prof: ProfissionalPMP) : BuscaUiState()
    data class AgendamentoModalidade(val prof: ProfissionalPMP, val modalidadeId: String) : BuscaUiState()
}

// TELA PRINCIPAL
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuscaScreen(
    onVoltar: () -> Unit,
    onEstudio: (String) -> Unit,
    onPagar: () -> Unit,
    onIniciarChamadaUrgente: (String) -> Unit,
    onAgendarModalidade: (profissionalId: String, nome: String, modalidadeId: String) -> Unit = { _, _, _ -> },
    onPerfil: () -> Unit = {},
    onReferral: () -> Unit = {},
    onSuporte: () -> Unit = {},
    onSair: () -> Unit = {},
) {
    var busca by remember { mutableStateOf("") }
    var somenteUrgente by remember { mutableStateOf(false) }
    var uiState by remember { mutableStateOf<BuscaUiState>(BuscaUiState.Lista) }
    var exibindoEstudio by remember { mutableStateOf(false) }

    // Dados de profissionais (usando função já testada no Dashboard)
    var profissionaisDB by remember { mutableStateOf<List<ProfissionalPMP>>(emptyList()) }
    var loadingDB by remember { mutableStateOf(true) }

    // Dados do estúdio (todos os itens, depois filtramos por PMP)
    var itensEstudioBrutos by remember { mutableStateOf<List<ItemEstudio>>(emptyList()) }
    var idsPMP by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loadingEstudio by remember { mutableStateOf(true) }

    // Carrega profissionais PMP (identificadores para filtro)
    LaunchedEffect(Unit) {
        // Obtém todos os profissionais e extrai os IDs dos que são PMP
        val pmpProfiles = getProfissionaisPMPAndroid(somenteUrgente = false, busca = "", aplicarFiltroPMP = false)
        idsPMP = pmpProfiles.filter { it.is_pmp }.map { it.id }.toSet()
    }

    // Carrega profissionais da busca (usando a função real)
    LaunchedEffect(somenteUrgente) {
        loadingDB = true
        // Carrega todos os profissionais (sem filtro extra), como no Dashboard
        val dados = getProfissionaisPMPAndroid(
            somenteUrgente = somenteUrgente,
            busca = "",
            aplicarFiltroPMP = false
        )
        // Filtra apenas os que possuem o selo PMP (is_pmp = true)
        profissionaisDB = dados.filter { it.is_pmp }.map { it.toProfissionalPMP() }
        loadingDB = false
    }

    // Carrega todos os itens do estúdio e filtra apenas os de profissionais PMP
    LaunchedEffect(exibindoEstudio, idsPMP) {
        if (exibindoEstudio && idsPMP.isNotEmpty()) {
            loadingEstudio = true
            val todos = getProfissionaisEstudioAndroid()
            itensEstudioBrutos = todos.filter { it.profissionalId in idsPMP }
            loadingEstudio = false
        }
    }

    // Lista final de profissionais (filtrada pela busca textual)
    val resultado = profissionaisDB.filter { p ->
        val matchBusca = busca.isEmpty() ||
                p.nome.contains(busca, ignoreCase = true) ||
                p.area.contains(busca, ignoreCase = true) ||
                p.cidade.contains(busca, ignoreCase = true) ||
                p.especialidades.any { it.contains(busca, ignoreCase = true) }
        val matchUrgente = !somenteUrgente || p.disponivelUrgente
        matchBusca && matchUrgente
    }

    // Lista final do estúdio (filtro textual)
    val estúdioFiltrado = itensEstudioBrutos.filter { item ->
        busca.isEmpty() ||
                item.titulo.contains(busca, ignoreCase = true) ||
                item.autorNome.contains(busca, ignoreCase = true)
    }

    BackHandler(enabled = uiState !is BuscaUiState.Lista) {
        when (uiState) {
            is BuscaUiState.Perfil -> uiState = BuscaUiState.Lista
            is BuscaUiState.Agendamento -> uiState = BuscaUiState.Perfil((uiState as BuscaUiState.Agendamento).prof)
            else -> {}
        }
    }

    when (val state = uiState) {

        is BuscaUiState.Lista -> {
            val scrollState = rememberLazyListState()
            var filtrosExpandidos by remember { mutableStateOf(true) }
            var ultimoOffset by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(50)
                    val offset = scrollState.firstVisibleItemScrollOffset
                    if (offset > ultimoOffset + 20 && filtrosExpandidos) {
                        filtrosExpandidos = false
                    }
                    ultimoOffset = offset
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            if (exibindoEstudio) "Produtos no Estúdio" else "Buscar Profissionais",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Azul),
                    actions = {
                        var menuExpandido by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpandido = true }) {
                                Text("⋮", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(
                                expanded = menuExpandido,
                                onDismissRequest = { menuExpandido = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (!exibindoEstudio) "✓ " else "", color = Color.White)
                                            Text("Buscar Profissionais")
                                        }
                                    },
                                    onClick = {
                                        menuExpandido = false
                                        exibindoEstudio = false
                                        busca = ""
                                        somenteUrgente = false
                                    },
                                    leadingIcon = { Text("👥") }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (exibindoEstudio) "✓ " else "", color = Color.White)
                                            Text("Buscar Produtos no Estúdio")
                                        }
                                    },
                                    onClick = {
                                        menuExpandido = false
                                        exibindoEstudio = true
                                        busca = ""
                                    },
                                    leadingIcon = { Text("🎨") }
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onVoltar) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }
                    },
                )

                Box(modifier = Modifier.fillMaxSize().background(SurfaceWarm)) {
                    if (exibindoEstudio) {
                        LazyColumn(
                            state = scrollState,
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            item {
                                Column {
                                    CabecalhoBuscaColapsado(
                                        busca = busca,
                                        onBuscaChange = { busca = it },
                                        onExpandir = { filtrosExpandidos = true },
                                        onVoltar = { }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        if (loadingEstudio) "Carregando..."
                                        else "${estúdioFiltrado.size} produto(s) encontrado(s)",
                                        fontSize = 13.sp,
                                        color = InkMuted,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            if (loadingEstudio) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = DouradoMedio)
                                    }
                                }
                            } else if (estúdioFiltrado.isEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🎨", fontSize = 48.sp)
                                        Text("Nenhum produto encontrado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                                    }
                                }
                            } else {
                                items(estúdioFiltrado) { item ->
                                    CardEstudioPMP(
                                        item = item,
                                        onClick = { onEstudio(item.profissionalId) }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = scrollState,
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            item {
                                Column {
                                    if (filtrosExpandidos) {
                                        CabecalhoBuscaExpandido(
                                            busca = busca,
                                            onBuscaChange = { busca = it },
                                            somenteUrgente = somenteUrgente,
                                            onSomenteUrgenteChange = { somenteUrgente = it },
                                            onVoltar = { }
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth().background(VerdeClaro).padding(horizontal = 16.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            listOf("✓ 10+ atendimentos", "✓ Zero negativos", "✓ Plano PMP ativo").forEach {
                                                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Verde)
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                if (loadingDB) "Carregando..."
                                                else "${resultado.size} profissional(is) encontrado(s)",
                                                fontSize = 13.sp, color = InkMuted,
                                            )
                                            if ((busca.isNotEmpty() || somenteUrgente) && !loadingDB) {
                                                TextButton(onClick = { busca = ""; somenteUrgente = false }) {
                                                    Text("Limpar", color = Verde, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    } else {
                                        CabecalhoBuscaColapsado(
                                            busca = busca,
                                            onBuscaChange = { busca = it },
                                            onExpandir = { filtrosExpandidos = true },
                                            onVoltar = { }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            if (loadingDB) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = DouradoMedio)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("Carregando profissionais...", fontSize = 13.sp, color = InkMuted)
                                        }
                                    }
                                }
                            } else if (resultado.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text("🔍", fontSize = 48.sp, textAlign = TextAlign.Center)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Nenhum profissional encontrado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                                        Text("Tente outros termos ou remova os filtros.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            } else {
                                items(resultado) { prof ->
                                    CardProfissional(
                                        prof = prof,
                                        onClick = { uiState = BuscaUiState.Perfil(prof) },
                                        onEstudio = onEstudio,
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }
                    }
                }
            }
        }

        is BuscaUiState.Perfil -> {
            val currentProf = state.prof
            PerfilPublicoScreen(
                prof = currentProf,
                onVoltar = { uiState = BuscaUiState.Lista },
                onAgendar = { tipo ->
                    Log.d("BuscaScreen", "onAgendar chamado com tipo=$tipo, prof=${currentProf.nome}")
                    uiState = BuscaUiState.Agendamento(currentProf, tipo)
                },
                onAgendarModalidade = { modalidadeId ->
                    Log.d("BuscaScreen", "onAgendarModalidade chamado com modalidadeId=$modalidadeId")
                    onAgendarModalidade(currentProf.supabaseId, currentProf.nome, modalidadeId)
                    uiState = BuscaUiState.Lista
                },
                onEstudio = onEstudio,
            )
        }

        is BuscaUiState.Agendamento -> {
            val prof = state.prof
            val tipo = state.tipo
            val etapaInicial = if (currentUserId != null) 2 else 1
            Log.d("BuscaScreen", "Exibindo AgendarScreen para tipo=$tipo, etapaInicial=$etapaInicial")
            AgendarScreen(
                prof = prof,
                tipo = tipo,
                etapaInicial = etapaInicial,
                onVoltar = {
                    Log.d("BuscaScreen", "AgendarScreen onVoltar")
                    uiState = BuscaUiState.Perfil(prof)
                },
                onConcluido = {
                    Log.d("BuscaScreen", "AgendarScreen onConcluido")
                    uiState = BuscaUiState.Lista
                },
                onPagar = onPagar,
                onIniciarChamadaUrgente = onIniciarChamadaUrgente,
            )
        }

        is BuscaUiState.Estudio -> {
            onEstudio(state.prof.supabaseId)
            uiState = BuscaUiState.Lista
        }

        is BuscaUiState.AgendamentoModalidade -> {
            onAgendarModalidade(state.prof.supabaseId, state.prof.nome, state.modalidadeId)
            uiState = BuscaUiState.Lista
        }
    }
}

// ── CARD PROFISSIONAL (mantido igual ao original) ────
@Composable
fun CardProfissional(prof: ProfissionalPMP, onClick: () -> Unit, onEstudio: ((String) -> Unit)? = null) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(52.dp).background(Azul, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text(prof.iniciais, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(prof.nome, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(prof.area, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                    Text("📍 ${prof.cidade}", fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(modifier = Modifier.background(Color(0xFFFDF3D8), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("🏆 PMP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC49A2A))
                    }
                    if (prof.disponivelUrgente) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.background(UrgenteClaro, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("⚡ Urgente", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Urgente)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("⭐ ${prof.avaliacao}", fontSize = 12.sp, color = InkSoft, fontWeight = FontWeight.SemiBold)
                Text("·", color = InkMuted, fontSize = 12.sp)
                Text("${prof.atendimentos} atendimentos", fontSize = 12.sp, color = InkMuted)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                prof.especialidades.take(2).forEach { esp ->
                    Box(modifier = Modifier.background(AzulClaro, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(esp, fontSize = 10.sp, color = Azul, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Ver perfil →", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onEstudio != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onEstudio(prof.supabaseId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB07D00)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC49A2A)),
                ) {
                    Text("🎨 Ver Estúdio", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── CARD DO ESTÚDIO (produto individual) ─────────────
@Composable
fun CardEstudioPMP(item: ItemEstudio, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.capaUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
                Text(item.autorNome.ifEmpty { "Profissional PMP" }, fontSize = 12.sp, color = InkMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R$ %.2f".format(item.preco), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde)
                    if (item.precoOriginal != null && item.precoOriginal > item.preco) {
                        Text(
                            " R$ %.2f".format(item.precoOriginal),
                            fontSize = 12.sp,
                            color = InkMuted,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }
            }
            IconButton(onClick = onClick) {
                Text("→", fontSize = 20.sp, color = Verde)
            }
        }
    }
}


// ── PERFIL PÚBLICO ────────────────────────────────────
@Composable
fun PerfilPublicoScreen(
    prof:                  ProfissionalPMP,
    onVoltar:              () -> Unit,
    onAgendar:             (String) -> Unit,
    onAgendarModalidade:   (modalidadeId: String) -> Unit = {},
    onEstudio:             ((String) -> Unit)? = null,
) {
    var modalidades by remember { mutableStateOf<List<ModalidadeAtendimento>>(emptyList()) }
    var itensEstudio by remember { mutableStateOf<List<ItemEstudio>>(emptyList()) }
    var loadingModalidades by remember { mutableStateOf(true) }
    var loadingEstudio by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(prof.supabaseId) {
        modalidades = AtendimentosRepository.buscarModalidades(prof.supabaseId)
        loadingModalidades = false
    }
    LaunchedEffect(prof.supabaseId) {
        itensEstudio = getEstudioProfissionalAndroid(prof.supabaseId)
        loadingEstudio = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SurfaceWarm),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                if (prof.capaUrl != null) {
                    AsyncImage(
                        model = prof.capaUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Brush.linearGradient(listOf(Azul, Verde)))
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.5f))
                    )
                )
                TextButton(
                    onClick = onVoltar,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 36.dp)
                ) {
                    Text("← Voltar", color = Color.White, fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (prof.fotoUrl != null) {
                            AsyncImage(
                                model = prof.fotoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(prof.iniciais, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(prof.nome, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            if (prof.verificado) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("✅", fontSize = 16.sp)
                            }
                            if (prof.pmp) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🏆", fontSize = 16.sp)
                            }
                        }
                        Text(prof.area, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                        Text("📍 ${prof.cidade}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // ── Badges de prestígio ─────────────────────────────────────
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badge de Certificação Profissional (sempre presente)
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

                // Badge PMP (se aplicável)
                if (prof.pmp) {
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

                // Badge de Confiança Verificada (quando verificado)
                if (prof.verificado) {
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
                }

                // Badge de urgente (se disponível)
                if (prof.disponivelUrgente) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = UrgenteClaro),
                        border = BorderStroke(1.dp, Urgente.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Atendimento Urgente",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Urgente
                                )
                                Text(
                                    "Disponível para consultas imediatas (resposta em até 45 min).",
                                    fontSize = 12.sp,
                                    color = Urgente.copy(alpha = 0.8f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
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
                        Triple("⭐ ${prof.avaliacao}", "Avaliação", "⭐"),
                        Triple("${prof.atendimentos}", "Atendimentos", "💼"),
                        Triple("0", "Negativos", "🛡️")
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
                            modifier = Modifier.background(AzulClaro, RoundedCornerShape(20.dp))
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
            Column(modifier = Modifier.background(Surface).padding(20.dp)) {
                Text("Agendar consulta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 14.dp))
                if (prof.valorUrgente != null) {
                    Card(
                        onClick = { onAgendar("urgente") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Urgente),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("⚡", fontSize = 40.sp)
                            Text("ATENDIMENTO URGENTE", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.White, textAlign = TextAlign.Center)
                            Text("Resposta em até 45 minutos", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text("Valor", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                Text("R$ ${prof.valorUrgente}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onAgendar("urgente") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Urgente),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("AGENDAR AGORA", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(8.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sem cobranças antecipadas. Pague só após a consulta.", fontSize = 12.sp, color = Verde)
                }
            }
        }

        if (!loadingEstudio && onEstudio != null) {
            item {
                Card(
                    onClick = { onEstudio(prof.supabaseId) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Azul),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("🎨", fontSize = 44.sp)
                        Text("ESTÚDIO DIGITAL", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = Color.White, textAlign = TextAlign.Center)
                        Text("Cursos, materiais e produtos exclusivos", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onEstudio(prof.supabaseId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Azul),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("EXPLORAR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                HorizontalDivider(color = SurfaceOff)
            }
        }

        if (!loadingModalidades) {
            item {
                Column(modifier = Modifier.background(Surface).padding(20.dp)) {
                    Text("Modalidades de Atendimento", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (modalidades.isEmpty()) {
                        Text("Nenhuma modalidade configurada.", fontSize = 13.sp, color = InkMuted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            modalidades.forEach { mod ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { onAgendarModalidade(mod.id) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            text = when (mod.tipo) {
                                                "hora" -> "🕐"
                                                "semanal" -> "📆"
                                                "mensal" -> "🗓"
                                                else -> "⏱"
                                            },
                                            fontSize = 36.sp,
                                        )
                                        Text(
                                            text = mod.titulo.uppercase(),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Ink,
                                            textAlign = TextAlign.Center,
                                        )
                                        if (!mod.descricao.isNullOrBlank()) {
                                            Text(
                                                text = mod.descricao,
                                                fontSize = 13.sp,
                                                color = InkMuted,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 18.sp,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Bottom,
                                        ) {
                                            Text(
                                                text = when (mod.tipo) {
                                                    "minutos" -> "Sessão avulsa"
                                                    "hora" -> "Por hora"
                                                    "semanal" -> "Pacote semanal"
                                                    "mensal" -> "Pacote mensal"
                                                    else -> ""
                                                },
                                                fontSize = 13.sp,
                                                color = InkMuted,
                                            )
                                            Text(
                                                text = "R$ ${"%.2f".format(mod.valor)}",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Verde,
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { onAgendarModalidade(mod.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("AGENDAR", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = SurfaceOff)
            }
        }
    }
}

@Composable
fun BadgePerfil(texto: String, bg: Color, cor: Color) {
    Box(modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(texto, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cor)
    }
}

@Composable
fun OpcaoConsulta(
    titulo:    String,
    descricao: String,
    preco:     String,
    corPreco:  Color,
    urgente:   Boolean = false,
    onClick:   () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = if (urgente) UrgenteClaro else SurfaceOff),
        border   = if (urgente) androidx.compose.foundation.BorderStroke(1.dp, Urgente.copy(alpha = 0.3f)) else null,
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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

@Composable
fun CabecalhoBuscaExpandido(
    busca: String,
    onBuscaChange: (String) -> Unit,
    somenteUrgente: Boolean,
    onSomenteUrgenteChange: (Boolean) -> Unit,
    onVoltar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(Azul)
            .padding(horizontal = 24.dp)
            .padding(top = 52.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVoltar) {
                Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
        Text("Profissionais PMP", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Verificados · Avaliados · Confiáveis", fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        OutlinedTextField(
            value = busca,
            onValueChange = onBuscaChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por nome, área ou cidade...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DouradoMedio,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = DouradoMedio,
            ),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .background(if (somenteUrgente) Urgente.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .clickable { onSomenteUrgenteChange(!somenteUrgente) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚡", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Disponível agora (urgente)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (somenteUrgente) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = somenteUrgente,
                onCheckedChange = onSomenteUrgenteChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Urgente),
            )
        }
    }
}

@Composable
fun CabecalhoBuscaColapsado(
    busca: String,
    onBuscaChange: (String) -> Unit,
    onExpandir: () -> Unit,
    onVoltar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Azul)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onVoltar) {
            Text("←", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = busca,
            onValueChange = onBuscaChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Buscar...", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp) },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DouradoMedio,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = DouradoMedio,
            ),
            singleLine = true,
        )
        IconButton(onClick = onExpandir) {
            Text("▼", color = Color.White, fontSize = 16.sp)
        }
    }
}

// ── TELA DE AGENDAMENTO ───────────────────────────────
@Composable
fun AgendarScreen(
    prof:        ProfissionalPMP,
    tipo:        String,
    onVoltar:    () -> Unit,
    onConcluido: () -> Unit,
    onPagar:     () -> Unit = {},
    onIniciarChamadaUrgente: (String) -> Unit = {},
    etapaInicial: Int = 1,
) {
    val scope = rememberCoroutineScope()

    var nome     by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var senha    by remember { mutableStateOf("") }
    var etapa    by remember { mutableIntStateOf(etapaInicial) }
    var resultadoAcesso  by remember { mutableStateOf<ResultadoAcesso?>(null) }
    var verificandoAcesso by remember { mutableStateOf(false) }
    var loading  by remember { mutableStateOf(false) }
    var erro     by remember { mutableStateOf("") }
    var sucesso  by remember { mutableStateOf(false) }
    var consultaIdGerado by remember { mutableStateOf<String?>(null) }

    val dataHoje = remember {
        val c = java.util.Calendar.getInstance()
        "%02d/%02d/%04d".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR))
    }
    val horaAtual = remember {
        val c = java.util.Calendar.getInstance()
        "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }

    if (sucesso) {
        Column(
            modifier = Modifier.fillMaxSize().background(SurfaceWarm).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(72.dp).background(VerdeClaro, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Text("✓", fontSize = 32.sp, color = Verde, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Agendamento confirmado!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
            Text("Sua consulta com ${prof.nome} foi registrada com sucesso.", fontSize = 14.sp, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            if (consultaIdGerado != null) Text("ID: $consultaIdGerado", fontSize = 11.sp, color = InkMuted)
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onConcluido, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                Text("Voltar ao início", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(SurfaceWarm).verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(modifier = Modifier.height(48.dp))
        TextButton(onClick = onVoltar) { Text("← Voltar", color = InkMuted, fontSize = 13.sp) }
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).background(Azul, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text(prof.iniciais, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(prof.nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(
                        if (tipo == "urgente") "⚡ Urgente · R$ ${prof.valorUrgente}" else "Normal · R$ ${prof.valorNormal}",
                        fontSize = 12.sp, color = if (tipo == "urgente") Urgente else InkMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (etapa == 1) {
            Text("Criar conta gratuita", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Você será cadastrado como cliente automaticamente.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            CampoTexto("Nome completo *", nome, { nome = it }, "Seu nome completo")
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("E-mail *", email, { email = it }, "seu@email.com", keyboardType = KeyboardType.Email)
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("Telefone *", telefone, { telefone = it }, "(00) 00000-0000", keyboardType = KeyboardType.Phone)
            Spacer(modifier = Modifier.height(12.dp))
            CampoTexto("Criar senha *", senha, { senha = it }, "Mínimo 6 caracteres", senha = true)
            Spacer(modifier = Modifier.height(20.dp))
            BotaoProximo("Continuar →") {
                if (nome.isBlank() || email.isBlank() || senha.length < 6) {
                    erro = "Preencha todos os campos. Senha mínima: 6 caracteres."
                    return@BotaoProximo
                }
                etapa = 2
            }
        } else {
            Text("Confirmar agendamento", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    RevisaoItem("Profissional", prof.nome)
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Tipo", if (tipo == "urgente") "⚡ Urgente" else "Normal")
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Valor", if (tipo == "urgente") "R$ ${prof.valorUrgente}" else "R$ ${prof.valorNormal}")
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Data", "$dataHoje às $horaAtual")
                    HorizontalDivider(color = SurfaceOff)
                    RevisaoItem("Sua conta", email.ifEmpty { "—" })
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔒", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sem cobranças antecipadas.", fontSize = 12.sp, color = Verde)
            }
            if (erro.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(erro, color = Urgente, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    verificandoAcesso = true; erro = ""
                    scope.launch {
                        try {
                            val uid: String = if (currentUserId != null) {
                                currentUserId!!
                            } else {
                                val ok = signUpAndroid(email = email, senha = senha, nome = nome, telefone = telefone, tipo = "cliente")
                                if (!ok || currentUserId == null) { erro = "Erro ao criar conta. Tente novamente."; verificandoAcesso = false; return@launch }
                                currentUserId!!
                            }

                            if (tipo == "urgente") {
                                loading = true
                                val valor = prof.valorUrgente?.toDouble() ?: 0.0
                                val id = criarAgendamento(
                                    clienteId = uid,
                                    profId = prof.supabaseId,
                                    data = dataHoje,
                                    hora = horaAtual,
                                    tipo = tipo,
                                    valor = valor,
                                )
                                loading = false
                                verificandoAcesso = false
                                if (id != null) {
                                    consultaIdGerado = id
                                    onIniciarChamadaUrgente(id)
                                    return@launch
                                } else {
                                    erro = "Erro ao iniciar chamada urgente. Tente novamente."
                                    verificandoAcesso = false
                                }
                            } else {
                                val resultado = verificarAcessoAgendamento(uid, prof.supabaseId)
                                verificandoAcesso = false
                                if (!resultado.acesso) { resultadoAcesso = resultado; return@launch }
                                loading = true
                                val valor = prof.valorNormal?.toDouble() ?: 0.0
                                val id = criarAgendamento(clienteId = uid, profId = prof.supabaseId, data = dataHoje, hora = horaAtual, tipo = tipo, valor = valor)
                                loading = false
                                if (id != null) { consultaIdGerado = id; sucesso = true }
                                else erro = "Erro ao confirmar agendamento. Tente novamente."
                            }
                        } catch (e: Exception) {
                            loading = false
                            verificandoAcesso = false
                            erro = "Erro inesperado: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = !loading && !verificandoAcesso,
                colors   = ButtonDefaults.buttonColors(containerColor = if (tipo == "urgente") Urgente else Verde, contentColor = Color.White),
                shape    = RoundedCornerShape(10.dp),
            ) {
                if (loading || verificandoAcesso) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (tipo == "urgente") "⚡ Chamar agora" else "Confirmar agendamento", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    resultadoAcesso?.let { resultado ->
        val (titulo, descricao) = when (resultado.motivo) {
            "sem_plano"       -> "Sem plano ativo" to "Assine um plano para agendar consultas ou pague uma taxa avulsa."
            "limite_atingido" -> "Limite atingido" to "Você usou ${resultado.profs_usados}/${resultado.limite} profissionais do plano ${resultado.plano_atual?.replaceFirstChar { it.uppercase() }}."
            else              -> "Acesso bloqueado" to "Não foi possível verificar seu acesso."
        }
        AlertDialog(
            onDismissRequest = { resultadoAcesso = null },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(16.dp),
            title = { Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().background(UrgenteClaro, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(descricao, fontSize = 13.sp, color = Urgente, lineHeight = 18.sp)
                    }
                    if (resultado.motivo == "limite_atingido") {
                        Spacer(modifier = Modifier.height(14.dp))
                        listOf(Triple("Bronze", "Até 10 profissionais/mês", "R$ 59,90"), Triple("Ouro", "Ilimitado", "R$ 99,90")).forEach { (nome, desc, preco) ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = VerdeClaro), border = androidx.compose.foundation.BorderStroke(1.dp, Verde.copy(alpha = 0.3f))) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("⭐ Plano $nome", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                                        Text(desc, fontSize = 11.sp, color = InkMuted)
                                    }
                                    Text(preco, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = AzulClaro), border = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.2f))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("💳 Pagamento avulso", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Azul)
                            Text("Taxa única para esta consulta específica.", fontSize = 11.sp, color = InkMuted)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { resultadoAcesso = null; onPagar() }, colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White), shape = RoundedCornerShape(8.dp)) {
                    Text("Ver planos", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { resultadoAcesso = null }) { Text("Agora não", color = InkMuted, fontSize = 13.sp) }
            },
        )
    }
}