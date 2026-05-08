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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG_SCREEN = "VideoCallScreen"

private val STREAM_API_KEY get() = BuildConfig.STREAM_API_KEY

// ═══════════════════════════════════════════════════════════════════════════
// TELA PRINCIPAL
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun VideoCallScreen(
    urgenciaId:              String,
    onEncerrada:             () -> Unit,
    onVoltar:                () -> Unit,
    onboardingVm:            OnboardingViewModel? = null,   // PA-05 — guard first_call
    agendamentoRegularId:    String = "",                   // Agendamento regular (sem urgência)
) {
    val context        = LocalContext.current
    val scope          = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Determinar qual ID usar para token ─────────────────────────────────
    val callIdParaToken = if (agendamentoRegularId.isNotEmpty()) agendamentoRegularId else urgenciaId

    // ── Estado observado do repositório ──────────────────────────────────
    val callState by StreamVideoRepository.callState.collectAsState()

    // ── Referências ao SDK — gerenciadas pelo DisposableEffect ────────────
    var streamVideo by remember { mutableStateOf<StreamVideo?>(null) }
    var activeCall  by remember { mutableStateOf<Call?>(null) }

    // ── Estado de permissões ──────────────────────────────────────────────
    var permissoesConcedidas    by remember { mutableStateOf(false) }
    var permissoesNegadas       by remember { mutableStateOf(false) }
    var mostrarExplicacaoPermissao by remember { mutableStateOf(false) }

    // ── Estados do cronômetro (apenas para chamadas urgentes) ────────────
    val isUrgente = agendamentoRegularId.isEmpty() && urgenciaId.isNotEmpty()
    var tempoRestanteSegundos by remember { mutableIntStateOf(900) }   // 15 minutos
    var minutosExcedentes by remember { mutableIntStateOf(0) }
    var alerta12MinMostrado by remember { mutableStateOf(false) }
    var dialogoProlongamentoCliente by remember { mutableStateOf(false) }
    var dialogoAutorizacaoProfissional by remember { mutableStateOf(false) }
    var prolongamentoAutorizado by remember { mutableStateOf(false) }
    var valorMinutoExtra by remember { mutableStateOf<Double?>(null) }
    var cronometroAtivo by remember { mutableStateOf(false) }

    // ── Launcher de permissões ────────────────────────────────────────────
    val permissaoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val cameraOk = resultado[Manifest.permission.CAMERA] == true
        val audioOk  = resultado[Manifest.permission.RECORD_AUDIO] == true
        when {
            cameraOk && audioOk -> {
                permissoesConcedidas = true
                if (agendamentoRegularId.isNotEmpty()) {
                    StreamVideoRepository.solicitarTokenRegular(agendamentoRegularId)
                } else {
                    StreamVideoRepository.solicitarToken(urgenciaId)
                }
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

    // ── Orquestrador de estados do repositório + cronômetro ──────────────
    LaunchedEffect(callState) {
        when (val estado = callState) {

            is VideoCallState.TokenObtido -> {
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
                AppLogger.aviso(TAG_SCREEN, "Token expirado. Solicitando renovacao.")
                if (agendamentoRegularId.isNotEmpty()) {
                    StreamVideoRepository.solicitarTokenRegular(agendamentoRegularId)
                } else {
                    StreamVideoRepository.renovarToken(urgenciaId)
                }
            }

            is VideoCallState.Encerrada -> {
                onEncerrada()
            }

            is VideoCallState.EmChamada -> {
                // Buscar valor por minuto extra para urgências
                if (isUrgente && valorMinutoExtra == null) {
                    scope.launch {
                        valorMinutoExtra = obterValorMinutoExtrapolado(urgenciaId)
                    }
                }
                // PA-05 — Analytics: first_call
                scope.launch {
                    if (onboardingVm?.registrarPrimeiraChamada() == true) {
                        AnalyticsTracker.firstCall(urgenciaId)
                    }
                }

                // Iniciar cronômetro da chamada urgente
                if (isUrgente && !cronometroAtivo) {
                    cronometroAtivo = true
                    scope.launch {
                        tempoRestanteSegundos = 900
                        minutosExcedentes = 0
                        alerta12MinMostrado = false
                        prolongamentoAutorizado = false
                        dialogoProlongamentoCliente = false
                        dialogoAutorizacaoProfissional = false

                        while (tempoRestanteSegundos > 0) {
                            delay(1000L)
                            tempoRestanteSegundos--

                            // Alerta aos 12 minutos (180 segundos restantes)
                            if (tempoRestanteSegundos == 180 && !alerta12MinMostrado) {
                                alerta12MinMostrado = true
                            }
                        }
                        // Tempo esgotado
                        if (!prolongamentoAutorizado) {
                            dialogoProlongamentoCliente = true
                        }
                    }
                }
            }

            else -> Unit
        }
    }

    // ── Contagem de minutos excedentes após prolongamento autorizado ────
    LaunchedEffect(prolongamentoAutorizado) {
        if (prolongamentoAutorizado) {
            dialogoProlongamentoCliente = false
            dialogoAutorizacaoProfissional = false
            while (prolongamentoAutorizado) {
                delay(60_000L) // 1 minuto
                minutosExcedentes++
            }
        }
    }

    // ── DisposableEffect — garantia de cleanup no ciclo de vida ──────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
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
                                DefaultOnCallActionHandler.onCallAction(call, action)
                            }
                        },
                    )
                } ?: LoadingChamada("Carregando vídeo...")

                // Indicador de tempo para chamadas urgentes
                if (isUrgente) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                    ) {
                        Text(
                            text = formatarTempo(tempoRestanteSegundos + minutosExcedentes * 60),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            is VideoCallState.TokenExpirado -> {
                LoadingChamada("Renovando sessão...")
            }

            is VideoCallState.Encerrada -> {
                LoadingChamada("Encerrando chamada...")
            }

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

        // Diálogo de permissão negada
        if (mostrarExplicacaoPermissao) {
            PermissaoNegadaDialog(
                onConfirmar = {
                    mostrarExplicacaoPermissao = false
                    onVoltar()
                }
            )
        }

        // ── Alerta 12 minutos ──────────────────────────────────────────
        if (alerta12MinMostrado && tempoRestanteSegundos <= 180 && tempoRestanteSegundos > 0) {
            LaunchedEffect(Unit) {
                delay(5_000)
                alerta12MinMostrado = false
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 140.dp)
                    .background(Color(0xFFFFA000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "⏰ Faltam ${tempoRestanteSegundos / 60} min!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // ── Diálogo de prolongamento (cliente) ─────────────────────────
        if (dialogoProlongamentoCliente) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Tempo esgotado", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Os 15 minutos da consulta urgente acabaram.")
                        Spacer(modifier = Modifier.height(8.dp))
                        if (valorMinutoExtra != null && valorMinutoExtra!! > 0.0) {
                            Text(
                                "Valor por minuto adicional: R$ ${"%.2f".format(valorMinutoExtra!!)}",
                                fontWeight = FontWeight.SemiBold,
                                color = Urgente
                            )
                            Text("Deseja continuar?")
                        } else {
                            Text("O profissional não definiu valor para minuto extra. Entre em contato.")
                        }
                    }
                },
                confirmButton = {
                    if (valorMinutoExtra != null && valorMinutoExtra!! > 0.0) {
                        Button(
                            onClick = {
                                dialogoProlongamentoCliente = false
                                scope.launch {
                                    try {
                                        activeCall?.sendCustomEvent(
                                            mapOf(
                                                "type" to "solicitar_prolongamento",
                                                "valorMinutoExtra" to valorMinutoExtra.toString()
                                            )
                                        )
                                    } catch (e: Exception) {
                                        AppLogger.aviso(TAG_SCREEN, "Falha ao enviar solicitação de prolongamento: ${e.message}")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Verde)
                        ) {
                            Text("Continuar (cobrado)", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialogoProlongamentoCliente = false
                        scope.launch {
                            encerrarChamadaSeguro(streamVideo, activeCall, urgenciaId)
                        }
                    }) {
                        Text("Encerrar chamada")
                    }
                }
            )
        }

        // ── Diálogo de autorização do profissional ──────────────────────
        if (dialogoAutorizacaoProfissional) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Solicitação de prolongamento", fontWeight = FontWeight.Bold) },
                text = {
                    Text("O cliente deseja continuar a consulta. Será cobrado por minuto adicional.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            dialogoAutorizacaoProfissional = false
                            prolongamentoAutorizado = true
                            scope.launch {
                                try {
                                    activeCall?.sendCustomEvent(
                                        mapOf("type" to "autorizar_prolongamento")
                                    )
                                } catch (e: Exception) {
                                    AppLogger.aviso(TAG_SCREEN, "Falha ao autorizar prolongamento: ${e.message}")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Verde)
                    ) {
                        Text("Autorizar", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialogoAutorizacaoProfissional = false
                        scope.launch {
                            try {
                                activeCall?.sendCustomEvent(
                                    mapOf("type" to "recusar_prolongamento")
                                )
                            } catch (e: Exception) {
                                AppLogger.aviso(TAG_SCREEN, "Falha ao recusar prolongamento: ${e.message}")
                            }
                            encerrarChamadaSeguro(streamVideo, activeCall, urgenciaId)
                        }
                    }) {
                        Text("Recusar")
                    }
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// INICIALIZAR SDK E ENTRAR NA CHAMADA
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

        if (StreamVideo.isInstalled) {
            StreamVideo.instance().cleanup()
        }

        val sv = StreamVideoBuilder(
            context = context,
            apiKey  = STREAM_API_KEY,
            user    = User(id = userId),
            token   = token,
        ).build()

        onStreamVideo(sv)
        AppLogger.chave("stream_user_id", userId)
        AppLogger.chave("stream_call_id", callId)

        val call = sv.call(type = "default", id = callId)
        onCall(call)

        StreamVideoRepository.notificarConectando(callId)

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

            val isTokenExpirado = mensagem.contains("401") ||
                    mensagem.contains("unauthorized", ignoreCase = true)

            if (isTokenExpirado) {
                AppLogger.aviso(TAG_SCREEN, "Token expirado detectado no join()")
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

// ── Formatar tempo (mm:ss) ────────────────────────────────────────────────
private fun formatarTempo(totalSegundos: Int): String {
    val minutos = totalSegundos / 60
    val segundos = totalSegundos % 60
    return "%02d:%02d".format(minutos, segundos)
}