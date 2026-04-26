package br.com.brasiltupi.conecta

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// VideoPlayerScreen.kt  · Fase 3.1
//
// Contrato:
//  • Recebe aulaId + cursoId — nunca a URL direta (sempre via Storage seguro)
//  • onDispose salva progresso final antes de destruir o player
//  • Ticker de 30s salva progresso em background durante reproducao
//  • Sem botao de download — WindowManager.LayoutParams nao e alterado aqui
//    (bloqueio de screenshot fica na Activity, configurado por produto)
//  • Velocidades: 0.5x, 1x, 1.5x, 2x
// ═══════════════════════════════════════════════════════════════════════════

private val VELOCIDADES = listOf(0.5f, 1.0f, 1.5f, 2.0f)
private const val INTERVALO_SALVAR_MS = 30_000L

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    aulaId:     String,
    cursoId:    String,
    tituloAula: String,
    repository: ContentRepository,
    onVoltar:   () -> Unit,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val vm: VideoPlayerViewModel = viewModel(
        factory = VideoPlayerViewModelFactory(
            aulaId     = aulaId,
            cursoId    = cursoId,
            repository = repository,
        )
    )

    val uiState by vm.uiState.collectAsState()

    // ── ExoPlayer — criado uma unica vez, destruido no onDispose ──────────
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var velocidadeAtual by remember { mutableFloatStateOf(1.0f) }

    // ── Configurar MediaItem quando URL estiver pronta ────────────────────
    LaunchedEffect(uiState.videoUrl) {
        val url = uiState.videoUrl
        if (url.isBlank()) return@LaunchedEffect

        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // Retomar da posicao salva
        if (uiState.posicaoInicialMs > 0L) {
            exoPlayer.seekTo(uiState.posicaoInicialMs)
        }
    }

    // ── Ticker de 30s — salvar progresso em background ───────────────────
    LaunchedEffect(uiState.videoUrl) {
        if (uiState.videoUrl.isBlank()) return@LaunchedEffect
        while (true) {
            delay(INTERVALO_SALVAR_MS)
            val posicao = exoPlayer.currentPosition
            val duracao = exoPlayer.duration.takeIf { it > 0 } ?: continue
            vm.salvarProgresso(posicaoMs = posicao, duracaoMs = duracao)
        }
    }

    // ── Cleanup: salvar posicao final e liberar player ────────────────────
    DisposableEffect(Unit) {
        onDispose {
            val posicao = exoPlayer.currentPosition
            val duracao = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            if (duracao > 0L) {
                scope.launch {
                    vm.salvarProgresso(posicaoMs = posicao, duracaoMs = duracao)
                }
            }
            exoPlayer.release()
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            uiState.carregando -> {
                CircularProgressIndicator(
                    color    = Verde,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            uiState.erro != null -> {
                ErroPlayer(
                    mensagem = uiState.erro!!,
                    onTentar = { vm.tentarNovamente() },
                    onVoltar = onVoltar,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                // ── PlayerView do Media3 ──────────────────────────────────
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = { ctx ->
                        PlayerView(ctx).apply {
                            player       = exoPlayer
                            useController = true
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowFastForwardButton(true)
                            setShowRewindButton(true)
                        }
                    },
                )

                // ── Barra superior: voltar + titulo ───────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onVoltar) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint               = Color.White,
                        )
                    }
                    Text(
                        text       = tituloAula,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        maxLines   = 1,
                        modifier   = Modifier.weight(1f),
                    )
                }

                // ── Seletor de velocidade — canto inferior direito ────────
                SeletorVelocidade(
                    velocidadeAtual = velocidadeAtual,
                    modifier        = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 12.dp, bottom = 64.dp),
                    onSelecionada   = { nova ->
                        velocidadeAtual = nova
                        exoPlayer.playbackParameters = PlaybackParameters(nova)
                    },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SELETOR DE VELOCIDADE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SeletorVelocidade(
    velocidadeAtual: Float,
    onSelecionada:   (Float) -> Unit,
    modifier:        Modifier = Modifier,
) {
    var expandido by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick  = { expandido = true },
            shape    = RoundedCornerShape(6.dp),
            color    = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier.padding(4.dp),
        ) {
            Text(
                text       = formatarVelocidade(velocidadeAtual),
                color      = Color.White,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        DropdownMenu(
            expanded         = expandido,
            onDismissRequest = { expandido = false },
        ) {
            VELOCIDADES.forEach { vel ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text       = formatarVelocidade(vel),
                            fontWeight = if (vel == velocidadeAtual) FontWeight.Bold else FontWeight.Normal,
                            color      = if (vel == velocidadeAtual) Verde else Color.Unspecified,
                        )
                    },
                    onClick = {
                        onSelecionada(vel)
                        expandido = false
                    },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TELA DE ERRO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ErroPlayer(
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
        Text("⚠️", fontSize = 40.sp)
        Text(
            text      = mensagem,
            color     = Color.White,
            fontSize  = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

// ── Formatar label da velocidade ──────────────────────────────────────────
private fun formatarVelocidade(vel: Float): String = when (vel) {
    0.5f -> "0.5×"
    1.0f -> "1×"
    1.5f -> "1.5×"
    2.0f -> "2×"
    else -> "${vel}×"
}