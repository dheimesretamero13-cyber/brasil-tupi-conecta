package br.com.brasiltupi.conecta

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════
// PdfViewerScreen.kt  · Fase 3.2
//
// Contrato:
//  • Recebe produtoId + allowScreenshot — nunca a URL direta
//  • Se allowScreenshot == false → FLAG_SECURE aplicada via Activity
//  • Páginas renderizadas como Bitmap via PdfRenderer nativo (sem lib externa)
//  • LazyColumn para scroll vertical entre páginas
//  • Zoom via gestos (transformable) na página tocada
//  • Sem opção de compartilhamento ou exportação
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun PdfViewerScreen(
    produtoId:       String,
    tituloProduto:   String,
    allowScreenshot: Boolean,
    repository:      ContentRepository,
    onVoltar:        () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // ── Aplicar FLAG_SECURE se screenshot bloqueado ───────────────────────
    DisposableEffect(allowScreenshot) {
        if (!allowScreenshot) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            // Remover flag ao sair da tela — não afeta outras telas
            if (!allowScreenshot) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val vm: PdfViewerViewModel = viewModel(
        factory = PdfViewerViewModelFactory(
            produtoId       = produtoId,
            allowScreenshot = allowScreenshot,
            repository      = repository,
        )
    )

    val uiState  by vm.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Página visível atualmente (para exibir "Página X de Y")
    val paginaAtual by remember {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                titulo       = tituloProduto,
                paginaAtual  = if (uiState.paginas.isNotEmpty()) paginaAtual else 0,
                totalPaginas = uiState.totalPaginas,
                onVoltar     = onVoltar,
            )
        },
        containerColor = Color(0xFF1A1A1A),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.carregando -> {
                    Column(
                        modifier            = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(color = Verde, strokeWidth = 3.dp)
                        Text(
                            "Carregando documento...",
                            color    = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                    }
                }

                uiState.erro != null -> {
                    ErroPdf(
                        mensagem = uiState.erro!!,
                        onTentar = { vm.carregarPdf() },
                        onVoltar = onVoltar,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        state           = listState,
                        modifier        = Modifier.fillMaxSize(),
                        contentPadding  = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(uiState.paginas) { index, bitmap ->
                            PaginaPdf(
                                bitmap     = bitmap,
                                numeroPagina = index + 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PÁGINA PDF COM ZOOM
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PaginaPdf(
    bitmap:       android.graphics.Bitmap,
    numeroPagina: Int,
) {
    // Estado de zoom — independente por página
    var scale      by remember { mutableFloatStateOf(1f) }
    var offset     by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale  = (scale * zoomChange).coerceIn(1f, 4f)
        // Limitar pan proporcional ao zoom atual
        val maxOffset = 500f * (scale - 1f)
        offset = Offset(
            x = (offset.x + panChange.x).coerceIn(-maxOffset, maxOffset),
            y = (offset.y + panChange.y).coerceIn(-maxOffset, maxOffset),
        )
    }

    // Resetar zoom ao soltar (duplo toque seria ideal mas requer GestureDetector custom)
    // Para MVP: zoom reseta quando escala volta a 1f
    LaunchedEffect(scale) {
        if (scale <= 1f) offset = Offset.Zero
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape     = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White),
        ) {
            Image(
                bitmap      = bitmap.asImageBitmap(),
                contentDescription = "Página $numeroPagina",
                contentScale = ContentScale.FillWidth,
                modifier    = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX         = scale,
                        scaleY         = scale,
                        translationX   = offset.x,
                        translationY   = offset.y,
                    )
                    .transformable(state = transformState),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfTopBar(
    titulo:       String,
    paginaAtual:  Int,
    totalPaginas: Int,
    onVoltar:     () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text       = titulo,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    color      = Color.White,
                )
                if (totalPaginas > 0) {
                    Text(
                        text     = "Página $paginaAtual de $totalPaginas",
                        fontSize = 11.sp,
                        color    = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onVoltar) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint               = Color.White,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF0D0D0D),
        ),
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// TELA DE ERRO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ErroPdf(
    mensagem: String,
    onTentar: () -> Unit,
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("📄", fontSize = 40.sp)
        Text(
            text      = mensagem,
            color     = Color.White,
            fontSize  = 14.sp,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onTentar,
            colors  = ButtonDefaults.buttonColors(containerColor = Verde),
        ) {
            Text("Tentar novamente", color = Color.White)
        }
        TextButton(onClick = onVoltar) {
            Text("Voltar", color = Color.White.copy(alpha = 0.7f))
        }
    }
}