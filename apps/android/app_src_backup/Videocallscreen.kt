package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// VideoCallScreen.kt
//
// Responsabilidades deste Composable:
//  1. Verificar e solicitar permissões CAMERA + RECORD_AUDIO antes de tudo
//  2. Observar VideoCallState do repositório e orquestrar o Stream SDK
//  3. Inicializar StreamVideoClient quando TokenObtido chegar
//  4. Executar join() e notificar o repositório do resultado
//  5. Renderizar UI de vídeo via componentes do Stream SDK
//  6. Garantir leave() + cleanup() no DisposableEffect (saída/destruição)
//  7. Instrumentar todos os pontos via AppLogger
//
// CONTRATO COM O REPOSITÓRIO:
//  • Repositório emite estados → esta tela reage
//  • Esta tela executa SDK → notifica repositório via notificar*()
//
// IMPORTANTE — IMPORTS DO STREAM SDK:
//  Os imports abaixo usam o package oficial io.getstream.video.android.*
//  documentado em https://getstream.io/video/docs/android/
//  Se algum import não resolver após Sync, verifique se a dependência
//  stream-video-android-ui-compose:1.0.14 está no build.gradle.kts.
// ═══════════════════════════════════════════════════════════════════════════

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import br.com.brasiltupi.conecta.ui.theme.*
import io.getstream.video.android.compose.ui.components.call.activecall.CallContent
import io.getstream.video.android.compose.ui.components.call.controls.actions.DefaultOnCallActionHandler
import io.getstream.video.android.core.Call
import io.getstream.video.android.core.StreamVideo
import io.getstream.video.android.core.StreamVideoBuilder
import io.getstream.video.android.model.User
import kotlinx.coroutines.launch

private const val TAG_SCREEN = "VideoCallScreen"

private val STREAM_API_KEY get() = BuildConfig.STREAM_API_KEY

// ═══════════════════════════════════════════════════════════════════════════
// TELA PRINCIPAL
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun VideoCallScreen(
    urgenciaId:   String,
    onEncerrada:  () -> Unit,
    onVoltar:     () -> Unit,
    onboardingVm: OnboardingViewModel? = null,   // PA-05 — guard first_call
) {
    val context        = LocalContext.current
    val scope          = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Estado observado do repositório ──────────────────────────────────
    val callState by StreamVideoRepository.callState.collectAsState()

    // ── Referências ao SDK — gerenciadas pelo DisposableEffect ────────────
    var streamVideo by remember { mutableStateOf<StreamVideo?>(null) }
    var activeCall  by remember { mutableStateOf<Call?>(null) }

    // ── Estado de permissões ──────────────────────────────────────────────
    var permissoesConcedidas    by remember { mutableStateOf(false) }
    var permissoesNegadas       by remember { mutableStateOf(false) }
    var mostrarExplicacaoPermissao by remember { mutableStateOf(false) }

    // ── Launcher de permissões ────────────────────────────────────────────
    val permissaoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val cameraOk = resultado[Manifest.permission.CAMERA] == true
        val audioOk  = resultado[Manifest.permission.RECORD_AUDIO] == true
        when {
            cameraOk && audioOk -> {
                permissoesConcedidas = true
                // Permissões concedidas — iniciar fluxo de token
                StreamVideoRepository.solicitarToken(urgenciaId)
            }
            else -> {
                permissoesNegadas       = true
                mostrarExplicacaoPermissao = true
            }
        }
    }

    // ── Solicitar permissões ao entrar na tela ────────────────────────────
    LaunchedEffect(Unit) {
        permissaoLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )
        )
    }

    // ── Orquestrador de estados do repositório ────────────────────────────
    LaunchedEffect(callState) {
        when (val estado = callState) {

            is VideoCallState.TokenObtido -> {
                // Inicializar StreamVideoClient e executar join()
                inicializarEEntrar(
                    context     = context,
                    token       = estado.token,
                    userId      = estado.userId,
                    callId      = estado.callId,
                    onStreamVideo = { sv -> streamVideo = sv },
                    onCall        = { call -> activeCall = call },
                )
            }

            is VideoCallState.TokenExpirado -> {
                // Token expirou durante chamada ativa — renovar automaticamente
                AppLogger.aviso(TAG_SCREEN, "Token expirado. Solicitando renovacao.")
                StreamVideoRepository.renovarToken(urgenciaId)
            }

            is VideoCallState.Encerrada -> {
                onEncerrada()
            }

            is VideoCallState.EmChamada -> {
                // PA-05 — Analytics: first_call com guard DataStore
                scope.launch {
                    if (onboardingVm?.registrarPrimeiraChamada() == true) {
                        AnalyticsTracker.firstCall(urgenciaId)
                    }
                }
            }

            else -> Unit
        }
    }

    // ── DisposableEffect — garantia de cleanup no ciclo de vida ──────────
    // Executa leave() + cleanup() quando:
    //   • O Composable sair da composição (navegação)
    //   • O app for para background (ON_STOP)
    //   • A Activity for destruída
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // App em background — encerrar chamada
                scope.launch {
                    encerrarChamadaSeguro(
                        streamVideo = streamVideo,
                        activeCall  = activeCall,
                        callId      = (callState as? VideoCallState.EmChamada)?.callId ?: urgenciaId,
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Cleanup ao sair do Composable
            scope.launch {
                encerrarChamadaSeguro(
                    streamVideo = streamVideo,
                    activeCall  = activeCall,
                    callId      = (callState as? VideoCallState.EmChamada)?.callId ?: urgenciaId,
                )
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val estado = callState) {

            // Permissão solicitada / token sendo obtido / SDK inicializando
            is VideoCallState.Idle,
            is VideoCallState.SolicitandoToken,
            is VideoCallState.TokenObtido,
            is VideoCallState.Conectando -> {
                LoadingChamada(
                    mensagem = when (estado) {
                        is VideoCallState.SolicitandoToken -> "Autenticando chamada..."
                        is VideoCallState.TokenObtido      -> "Inicializando vídeo..."
                        is VideoCallState.Conectando       -> "Conectando à sala..."
                        else                               -> "Aguardando..."
                    }
                )
            }

            // Chamada ativa — renderizar vídeo do Stream SDK
            is VideoCallState.EmChamada -> {
                activeCall?.let { call ->
                    CallContent(
                        call     = call,
                        modifier = Modifier.fillMaxSize(),
                        onCallAction = { action ->
                            // O Stream SDK trata mute, câmera, flip via DefaultOnCallActionHandler
                            // internamente quando não sobrescrevemos. Aqui interceptamos apenas
                            // LeaveCall para garantir nosso cleanup + notificação ao repositório.
                            val isLeave = action.javaClass.simpleName
                                .contains("Leave", ignoreCase = true)
                            if (isLeave) {
                                scope.launch {
                                    encerrarChamadaSeguro(
                                        streamVideo = streamVideo,
                                        activeCall  = call,
                                        callId      = estado.callId,
                                    )
                                }
                            } else {
                                // Delegar para o handler padrão do SDK
                                DefaultOnCallActionHandler.onCallAction(call, action)
                            }
                        },
                    )
                } ?: LoadingChamada("Carregando vídeo...")
            }

            // Reconectando após token expirado
            is VideoCallState.TokenExpirado -> {
                LoadingChamada("Renovando sessão...")
            }

            // Chamada encerrada — tratado no LaunchedEffect acima (onEncerrada())
            is VideoCallState.Encerrada -> {
                LoadingChamada("Encerrando chamada...")
            }

            // Erro — exibir diálogo e voltar
            is VideoCallState.Erro -> {
                ErroCallDialog(
                    motivo   = estado.motivo,
                    onDismiss = {
                        StreamVideoRepository.resetar()
                        onVoltar()
                    }
                )
            }
        }

        // Diálogo de permissão negada — sobrepõe qualquer estado
        if (mostrarExplicacaoPermissao) {
            PermissaoNegadaDialog(
                onConfirmar = {
                    mostrarExplicacaoPermissao = false
                    onVoltar()
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// INICIALIZAR SDK E ENTRAR NA CHAMADA
// Separado do Composable para manter o LaunchedEffect limpo.
// ═══════════════════════════════════════════════════════════════════════════

private suspend fun inicializarEEntrar(
    context:       android.content.Context,
    token:         String,
    userId:        String,
    callId:        String,
    onStreamVideo: (StreamVideo) -> Unit,
    onCall:        (Call) -> Unit,
) {
    try {
        AppLogger.info(TAG_SCREEN, "Inicializando StreamVideo para user=$userId call=$callId")

        // Encerrar instância anterior se existir (troca de token)
        if (StreamVideo.isInstalled) {
            StreamVideo.instance().cleanup()
        }

        // Inicializar cliente com token do backend — sem segredo no app
        val sv = StreamVideoBuilder(
            context = context,
            apiKey  = STREAM_API_KEY,
            user    = User(id = userId),
            token   = token,
        ).build()

        onStreamVideo(sv)
        AppLogger.chave("stream_user_id", userId)
        AppLogger.chave("stream_call_id", callId)

        // Obter referência à call — o callId é o urgencia_id
        val call = sv.call(type = "default", id = callId)
        onCall(call)

        // Notificar repositório que estamos conectando (atualiza StateFlow)
        StreamVideoRepository.notificarConectando(callId)

        // Executar join() — create=false pois a sala é criada pelo backend
        val resultado = call.join(create = false)

        resultado.onSuccess {
            AppLogger.info(TAG_SCREEN, "join() bem-sucedido para call=$callId")
            StreamVideoRepository.notificarChamadaAtiva(
                callId = callId,
                userId = userId,
            )
        }.onError { erro ->
            val mensagem = erro.message ?: "Erro desconhecido no join()"
            AppLogger.erro(
                TAG_SCREEN,
                "join() falhou: $mensagem",
                RuntimeException(mensagem),
            )

            // Detectar expiração de token (erro 401 do Stream)
            val isTokenExpirado = mensagem.contains("401") ||
                    mensagem.contains("unauthorized", ignoreCase = true)

            if (isTokenExpirado) {
                AppLogger.aviso(TAG_SCREEN, "Token expirado detectado no join()")
                // Repositório emite TokenExpirado → LaunchedEffect solicita renovação
                StreamVideoRepository.notificarErroChamada(
                    motivo     = "Sessão expirada. Renovando...",
                    tipo       = TipoErroVideo.TOKEN_NEGADO,
                    throwable  = RuntimeException(mensagem),
                )
            } else {
                StreamVideoRepository.notificarErroChamada(
                    motivo    = "Falha ao entrar na chamada. Verifique sua conexão.",
                    tipo      = TipoErroVideo.STREAM_SDK,
                    throwable = RuntimeException(mensagem),
                )
            }
        }

    } catch (e: Exception) {
        AppLogger.erro(TAG_SCREEN, "Falha ao inicializar StreamVideo", e)
        StreamVideoRepository.notificarErroChamada(
            motivo    = "Falha ao inicializar o cliente de vídeo.",
            tipo      = TipoErroVideo.STREAM_SDK,
            throwable = e,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ENCERRAR CHAMADA COM SEGURANÇA
// Chamado tanto pelo DisposableEffect quanto pelo botão de encerrar.
// Idempotente — seguro chamar múltiplas vezes.
// ═══════════════════════════════════════════════════════════════════════════

private suspend fun encerrarChamadaSeguro(
    streamVideo: StreamVideo?,
    activeCall:  Call?,
    callId:      String,
) {
    try {
        activeCall?.leave()
        AppLogger.info(TAG_SCREEN, "leave() executado para call=$callId")
    } catch (e: Exception) {
        AppLogger.aviso(TAG_SCREEN, "leave() falhou (provavelmente já encerrada): ${e.message}")
    }

    try {
        streamVideo?.cleanup()
        AppLogger.info(TAG_SCREEN, "StreamVideo cleanup() concluído")
    } catch (e: Exception) {
        AppLogger.aviso(TAG_SCREEN, "cleanup() falhou: ${e.message}")
    }

    StreamVideoRepository.notificarChamadaEncerrada(callId)
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLES DE UI AUXILIARES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingChamada(mensagem: String) {
    Box(
        modifier         = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CircularProgressIndicator(
                color       = Verde,
                strokeWidth = 3.dp,
                modifier    = Modifier.size(48.dp),
            )
            Text(
                text      = mensagem,
                fontSize  = 15.sp,
                color     = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErroCallDialog(
    motivo:    String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text       = "Chamada indisponível",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text      = motivo,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors  = ButtonDefaults.buttonColors(containerColor = Verde),
            ) {
                Text("Entendi", color = Color.White)
            }
        },
    )
}

@Composable
private fun PermissaoNegadaDialog(onConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirmar,
        title = {
            Text(
                text       = "Permissões necessárias",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "Para realizar videochamadas, o app precisa de acesso à câmera e ao microfone.\n\n" +
                        "Acesse Configurações → Aplicativos → Brasil Tupi Conecta → Permissões para habilitá-las.",
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors  = ButtonDefaults.buttonColors(containerColor = Verde),
            ) {
                Text("Entendi", color = Color.White)
            }
        },
    )
}