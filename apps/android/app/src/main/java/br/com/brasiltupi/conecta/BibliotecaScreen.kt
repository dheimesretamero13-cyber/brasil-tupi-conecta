package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.*
import coil.compose.AsyncImage

// ═══════════════════════════════════════════════════════════════════════════
// BibliotecaScreen.kt  · Fase 3.3
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibliotecaScreen(
    onVoltar:    () -> Unit,
    onAbrirCurso: (produtoId: String, titulo: String) -> Unit,
    onAbrirPdf:   (produtoId: String, titulo: String, allowScreenshot: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val vm: BibliotecaViewModel = viewModel(
        factory = BibliotecaViewModelFactory(
            repository        = BibliotecaRepositoryFactory(context).create(),
            contentRepository = ContentRepositoryFactory.create(),
        )
    )

    val uiState       by vm.uiState.collectAsState()
    val downloadState by vm.downloadState.collectAsState()
    var tabSelecionada by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minha Biblioteca", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F7F4)),
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
                    text     = { Text("Meus Cursos") },
                )
                Tab(
                    selected = tabSelecionada == 1,
                    onClick  = { tabSelecionada = 1 },
                    text     = { Text("Meus Produtos") },
                )
            }

            // ── Conteúdo por estado ───────────────────────────────────────
            when (val estado = uiState) {
                is BibliotecaUiState.Carregando -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Verde)
                    }
                }

                is BibliotecaUiState.Erro -> {
                    ErrosBiblioteca(
                        mensagem = estado.mensagem,
                        onTentar = { vm.carregarBiblioteca() },
                    )
                }

                is BibliotecaUiState.Sucesso -> {
                    when (tabSelecionada) {
                        0 -> AbaCursos(
                            cursos      = estado.cursos,
                            onAbrirCurso = onAbrirCurso,
                        )
                        1 -> AbaProdutos(
                            produtos      = estado.produtos,
                            downloadState = downloadState,
                            onAbrirPdf    = onAbrirPdf,
                            onBaixar      = { vm.baixarPdfOffline(it) },
                            onRemover     = { vm.removerOffline(it) },
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ABA CURSOS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AbaCursos(
    cursos:       List<CursoComprado>,
    onAbrirCurso: (produtoId: String, titulo: String) -> Unit,
) {
    if (cursos.isEmpty()) {
        EstadoVazio(
            icone    = "🎓",
            titulo   = "Nenhum curso ainda",
            subtitulo = "Seus cursos comprados aparecerão aqui.",
        )
        return
    }

    LazyColumn(
        contentPadding     = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cursos) { curso ->
            CardCurso(
                curso        = curso,
                onAbrirCurso = { onAbrirCurso(curso.produtoId, curso.titulo) },
            )
        }
    }
}

@Composable
private fun CardCurso(
    curso:        CursoComprado,
    onAbrirCurso: () -> Unit,
) {
    Card(
        onClick   = onAbrirCurso,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Capa ou ícone padrão
            Box(
                modifier         = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center,
            ) {
                if (curso.capaUrl != null) {
                    AsyncImage(
                        model              = curso.capaUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                    )
                } else {
                    Text("📚", fontSize = 28.sp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = curso.titulo,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF111827),
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
                if (curso.autorNome.isNotEmpty()) {
                    Text(
                        text     = "por ${curso.autorNome}",
                        fontSize = 12.sp,
                        color    = Color(0xFF6B7280),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Barra de progresso
                LinearProgressIndicator(
                    progress       = { (curso.percentualConcluido / 100).toFloat() },
                    modifier       = Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color          = Verde,
                    trackColor     = Color(0xFFE5E7EB),
                    strokeCap      = StrokeCap.Round,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text     = "${curso.aulasAssistidas} de ${curso.totalAulas} aulas • " +
                            "${curso.percentualConcluido.toInt()}% concluído",
                    fontSize = 11.sp,
                    color    = Color(0xFF6B7280),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ABA PRODUTOS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AbaProdutos(
    produtos:      List<ProdutoComprado>,
    downloadState: Map<String, DownloadState>,
    onAbrirPdf:   (produtoId: String, titulo: String, allowScreenshot: Boolean) -> Unit,
    onBaixar:     (produtoId: String) -> Unit,
    onRemover:    (produtoId: String) -> Unit,
) {
    if (produtos.isEmpty()) {
        EstadoVazio(
            icone    = "📦",
            titulo   = "Nenhum produto ainda",
            subtitulo = "Seus produtos e PDFs comprados aparecerão aqui.",
        )
        return
    }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(produtos) { produto ->
            CardProduto(
                produto       = produto,
                dlState       = downloadState[produto.produtoId] ?: DownloadState.Idle,
                onAbrir       = {
                    onAbrirPdf(produto.produtoId, produto.titulo, produto.allowScreenshot)
                },
                onBaixar      = { onBaixar(produto.produtoId) },
                onRemover     = { onRemover(produto.produtoId) },
            )
        }
    }
}

@Composable
private fun CardProduto(
    produto:  ProdutoComprado,
    dlState:  DownloadState,
    onAbrir:  () -> Unit,
    onBaixar: () -> Unit,
    onRemover: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier         = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF0F4FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (produto.tipo) {
                            "pdf"             -> "📄"
                            "produto_digital" -> "💾"
                            else              -> "📦"
                        },
                        fontSize = 24.sp,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = produto.titulo,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF111827),
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    if (produto.autorNome.isNotEmpty()) {
                        Text(
                            text     = "por ${produto.autorNome}",
                            fontSize = 12.sp,
                            color    = Color(0xFF6B7280),
                        )
                    }
                    if (produto.disponivelOffline) {
                        Text(
                            text     = "✅ Disponível offline",
                            fontSize = 11.sp,
                            color    = Verde,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(12.dp))

            // ── Botões de ação ────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Abrir
                Button(
                    onClick  = onAbrir,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = Verde),
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    Text("Abrir", fontSize = 13.sp, color = Color.White)
                }

                // Download / Remover offline (só para PDF)
                if (produto.tipo == "pdf") {
                    when (dlState) {
                        is DownloadState.Idle -> {
                            if (produto.disponivelOffline) {
                                OutlinedButton(
                                    onClick = onRemover,
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Remover offline", fontSize = 12.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onBaixar,
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(8.dp),
                                ) {
                                    Text("⬇ Offline", fontSize = 13.sp)
                                }
                            }
                        }
                        is DownloadState.Progresso -> {
                            Box(
                                modifier         = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                LinearProgressIndicator(
                                    progress  = { dlState.pct },
                                    modifier  = Modifier.fillMaxWidth().height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color     = Verde,
                                    strokeCap = StrokeCap.Round,
                                )
                            }
                        }
                        is DownloadState.Concluido -> {
                            Text(
                                "✅ Salvo",
                                fontSize = 12.sp,
                                color    = Verde,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        is DownloadState.Erro -> {
                            TextButton(
                                onClick  = onBaixar,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Tentar novamente", fontSize = 12.sp, color = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ESTADO VAZIO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EstadoVazio(icone: String, titulo: String, subtitulo: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Text(icone, fontSize = 48.sp)
            Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            Text(
                subtitulo,
                fontSize  = 13.sp,
                color     = Color(0xFF6B7280),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ERRO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrosBiblioteca(mensagem: String, onTentar: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Text("⚠️", fontSize = 40.sp)
            Text(mensagem, fontSize = 14.sp, color = Color(0xFF374151), textAlign = TextAlign.Center)
            Button(
                onClick = onTentar,
                colors  = ButtonDefaults.buttonColors(containerColor = Verde),
            ) {
                Text("Tentar novamente", color = Color.White)
            }
        }
    }
}