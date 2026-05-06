package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.*
import coil.compose.AsyncImage

// ═══════════════════════════════════════════════════════════════════════════
// SearchScreen.kt  · Fase 3.4
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVoltar:   () -> Unit,
    onAbrirItem: (item: ResultadoBusca) -> Unit,
) {
    val vm: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(SearchRepositoryFactory.create())
    )

    val uiState by vm.uiState.collectAsState()
    val filtro  by vm.filtro.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value         = filtro.query,
                        onValueChange = { vm.atualizarQuery(it) },
                        placeholder   = { Text("Buscar cursos, PDFs, produtos...", fontSize = 13.sp) },
                        modifier      = Modifier.fillMaxWidth().padding(end = 8.dp),
                        shape         = RoundedCornerShape(24.dp),
                        singleLine    = true,
                        leadingIcon   = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = InkMuted)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Verde,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    )
                },
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
            // ── Filtro de tipo ────────────────────────────────────────────
            LazyRow(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TipoEstudio.filtros) { (tipo, label) ->
                    FilterChip(
                        selected = filtro.tipo == tipo,
                        onClick  = { vm.atualizarTipo(tipo) },
                        label    = { Text(label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Verde,
                            selectedLabelColor     = Color.White,
                        ),
                    )
                }
            }

            // ── Filtros de preço e avaliação ──────────────────────────────
            FiltrosAvancados(
                filtro            = filtro,
                onPreco           = { min, max -> vm.atualizarPreco(min, max) },
                onAvaliacao       = { vm.atualizarAvaliacao(it) },
                onOrdenacao       = { vm.atualizarOrdenacao(it) },
                onLimpar          = { vm.limparFiltros() },
            )

            HorizontalDivider(color = Color(0xFFF0F0F0))

            // ── Resultados ────────────────────────────────────────────────
            when (val estado = uiState) {
                is SearchUiState.Idle -> {
                    EstadoIdle()
                }
                is SearchUiState.Carregando -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = Verde)
                    }
                }
                is SearchUiState.Sucesso -> {
                    if (estado.itens.isEmpty()) {
                        EstadoSemResultado(filtro.query)
                    } else {
                        Text(
                            "${estado.itens.size} resultado(s)",
                            fontSize = 12.sp,
                            color    = InkMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        LazyColumn(
                            contentPadding      = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(estado.itens, key = { it.id }) { item ->
                                CardResultadoBusca(
                                    item    = item,
                                    onClick = { onAbrirItem(item) },
                                )
                            }
                        }
                    }
                }
                is SearchUiState.Erro -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(estado.mensagem, color = InkMuted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FILTROS AVANÇADOS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FiltrosAvancados(
    filtro:      FiltroBusca,
    onPreco:     (Double?, Double?) -> Unit,
    onAvaliacao: (Double?) -> Unit,
    onOrdenacao: (OrdenacaoBusca) -> Unit,
    onLimpar:    () -> Unit,
) {
    var expandido by remember { mutableStateOf(false) }

    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { expandido = !expandido }) {
            Text(
                if (expandido) "▲ Fechar filtros" else "▼ Mais filtros",
                fontSize = 12.sp,
                color    = Verde,
            )
        }
        val filtrosAtivos = listOfNotNull(
            filtro.precoMin, filtro.precoMax, filtro.avaliacaoMin
        ).size
        if (filtrosAtivos > 0) {
            TextButton(onClick = onLimpar) {
                Text("Limpar ($filtrosAtivos)", fontSize = 12.sp, color = InkMuted)
            }
        }
    }

    if (expandido) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Faixa de preço
            Text("Faixa de preço", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Até R$50"    to Pair(null, 50.0),
                    "R$50–150"    to Pair(50.0, 150.0),
                    "Acima R$150" to Pair(150.0, null),
                ).forEach { (label, range) ->
                    val sel = filtro.precoMin == range.first && filtro.precoMax == range.second
                    FilterChip(
                        selected = sel,
                        onClick  = { if (sel) onPreco(null, null) else onPreco(range.first, range.second) },
                        label    = { Text(label, fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Verde,
                            selectedLabelColor     = Color.White,
                        ),
                    )
                }
            }

            // Avaliação mínima
            Text("Avaliação mínima", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3.0, 4.0, 4.5).forEach { nota ->
                    val sel = filtro.avaliacaoMin == nota
                    FilterChip(
                        selected = sel,
                        onClick  = { onAvaliacao(if (sel) null else nota) },
                        label    = { Text("⭐ $nota+", fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Verde,
                            selectedLabelColor     = Color.White,
                        ),
                    )
                }
            }

            // Ordenação
            Text("Ordenar por", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrdenacaoBusca.entries.forEach { ord ->
                    FilterChip(
                        selected = filtro.ordenacao == ord,
                        onClick  = { onOrdenacao(ord) },
                        label    = { Text(ord.label, fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Verde,
                            selectedLabelColor     = Color.White,
                        ),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CARD DE RESULTADO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CardResultadoBusca(item: ResultadoBusca, onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Capa
            Box(
                modifier         = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF0F4FF)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.capaUrl != null) {
                    AsyncImage(
                        model              = item.capaUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                    )
                } else {
                    Text(TipoEstudio.fromId(item.tipo)?.icon ?: "📦", fontSize = 28.sp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            TipoEstudio.fromId(item.tipo)?.label ?: item.tipo,
                            fontSize = 10.sp,
                            color    = Verde,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (item.destaque) {
                        Spacer(Modifier.width(4.dp))
                        Text("⭐", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.titulo,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF111827),
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
                item.perfis?.nome?.let { nome ->
                    Text("por $nome", fontSize = 11.sp, color = InkMuted)
                }
                if (item.avaliacaoMedia > 0) {
                    Text("⭐ ${"%.1f".format(item.avaliacaoMedia)}", fontSize = 11.sp, color = InkMuted)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.precoOriginal != null) {
                        Text(
                            "R$ ${"%.2f".format(item.precoOriginal)}",
                            fontSize         = 11.sp,
                            color            = InkMuted,
                            textDecoration   = TextDecoration.LineThrough,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        "R$ ${"%.2f".format(item.preco)}",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Verde,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ESTADOS AUXILIARES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EstadoIdle() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Text("🔍", fontSize = 48.sp)
            Text(
                "Busque cursos, aulas, PDFs e produtos\nde profissionais verificados.",
                fontSize  = 14.sp,
                color     = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EstadoSemResultado(query: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Text("😕", fontSize = 40.sp)
            Text(
                "Nenhum resultado para \"$query\"",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF111827),
                textAlign  = TextAlign.Center,
            )
            Text(
                "Tente outros termos ou remova os filtros.",
                fontSize  = 13.sp,
                color     = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}