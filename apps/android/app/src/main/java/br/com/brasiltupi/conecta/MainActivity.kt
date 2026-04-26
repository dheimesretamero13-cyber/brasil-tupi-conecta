package br.com.brasiltupi.conecta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.BrasilTupiConectaTheme
import br.com.brasiltupi.conecta.ui.theme.Verde
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrasilTupiConectaTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        UrgenciasRealtimeManager.parar()
        httpClient.close()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// APP NAVIGATION
//
// startDestination — lógica de decisão:
//   1. Lê DataStore via OnboardingViewModel
//   2. Se onboarding_completed == false → "onboarding" (Fase 1.6)
//   3. Se completed + role == "client"  → "dashboard-cliente"
//   4. Se completed + role == "professional" → "dashboard-profissional"
//   5. Enquanto lê → tela de loading
//
// A partir daí o when(tela) normal assume o controle de navegação.
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val onboardingVm: OnboardingViewModel = viewModel(
        factory = OnboardingViewModelFactory(context.onboardingDataStore)
    )
    val navState by onboardingVm.navState.collectAsState()

    var tela          by remember { mutableStateOf("") }   // vazio = aguardando DataStore
    var estudioProfId by remember { mutableStateOf("") }
    var chatOutroId   by remember { mutableStateOf("") }
    var chatOutroNome by remember { mutableStateOf("") }
    var chatDestino   by remember { mutableStateOf("dashboard-cliente") }
    var urgenciaIdAtiva by remember { mutableStateOf("") }   // urgencia_id da sala de chamada ativa
    var urgenciaIdPagamento by remember { mutableStateOf("") } // urgencia_id do pagamento pendente

    // ── Deep link FCM — ler extras do Intent ao abrir via notificação ─────
    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) {
        val intent = activity?.intent ?: return@LaunchedEffect
        val fcmTela       = intent.getStringExtra("fcm_tela")        ?: return@LaunchedEffect
        val fcmUrgenciaId = intent.getStringExtra("fcm_urgencia_id") ?: ""
        val fcmSlotId     = intent.getStringExtra("fcm_slot_id")     ?: ""
        // Aguardar DataStore resolver antes de redirecionar
        if (tela.isEmpty()) return@LaunchedEffect
        when (fcmTela) {
            "sala-chamada" -> {
                if (fcmUrgenciaId.isNotEmpty()) {
                    urgenciaIdAtiva = fcmUrgenciaId
                    tela = "sala-chamada"
                }
            }
            "pagamento", "dashboard-profissional" -> {
                if (fcmUrgenciaId.isNotEmpty()) {
                    urgenciaIdPagamento = fcmUrgenciaId
                    tela = "pagamento"
                } else {
                    tela = "dashboard-profissional"
                }
            }
            "agenda-profissional" -> tela = "dashboard-profissional"
            else -> Unit
        }
        // Limpar extras para não redirecionar novamente em recomposição
        intent.removeExtra("fcm_tela")
    }

    // ── Resolver startDestination via DataStore ───────────────────────────
    // Executado uma única vez quando navState sai de Carregando.
    // Não sobrescreve `tela` se o usuário já navegou para outra tela.
    LaunchedEffect(navState) {
        if (tela.isNotEmpty()) return@LaunchedEffect   // já navegou, não interferir
        when (navState) {
            is OnboardingNavState.Carregando        -> Unit   // aguarda
            is OnboardingNavState.MostrarOnboarding -> tela = "onboarding"
            is OnboardingNavState.IrParaCliente     -> {
                // 1. Verificar avaliação pendente
                val pendenteAvaliacao = AvaliacaoRepository.verificarPendencia()
                if (pendenteAvaliacao != null) {
                    urgenciaIdAtiva = pendenteAvaliacao.id
                    tela = "avaliacao"
                    return@LaunchedEffect
                }
                // 2. Verificar pagamento pendente
                val pendentePagamento = verificarPagamentoPendente()
                if (pendentePagamento != null) {
                    urgenciaIdPagamento = pendentePagamento
                    tela = "pagamento"
                    return@LaunchedEffect
                }
                tela = "dashboard-cliente"
            }
            is OnboardingNavState.IrParaProfissional -> {
                // Registrar token FCM ao iniciar sessão como profissional
                BrasilTupiMessagingService.registrarTokenSeLogado(this, context)
                // Verificar avaliação pendente antes de ir para o dashboard
                val pendente = AvaliacaoRepository.verificarPendencia()
                if (pendente != null) {
                    urgenciaIdAtiva = pendente.id
                    tela = "avaliacao"
                } else {
                    currentUserId?.let { uid ->
                        UrgenciasRealtimeManager.iniciar(profissionalId = uid, especialidade = null)
                    }
                    tela = "dashboard-profissional"
                }
            }
        }
    }

    // ── Loading enquanto DataStore é lido (~1 frame) ──────────────────────
    if (tela.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Verde)
        }
        return
    }

    // ── Roteador principal ────────────────────────────────────────────────
    when (tela) {

        // ── ONBOARDING FASE 1.6 ───────────────────────────────────────────
        "onboarding" -> OnboardingScreen(
            onIrParaCliente      = { tela = "welcome" },
            onIrParaProfissional = { tela = "welcome" },
            // Ambos vão para welcome → o usuário faz login/cadastro depois.
            // O role salvo no DataStore direciona na próxima abertura.
            // Para ir direto ao cadastro, substitua por: tela = "cadastro"
        )

        // ── FLUXO EXISTENTE (intocado) ────────────────────────────────────
        "chat" -> ChatScreen(
            outroId   = chatOutroId,
            outroNome = chatOutroNome,
            onVoltar  = { tela = chatDestino },
        )

        "sala-chamada" -> VideoCallScreen(
            urgenciaId  = urgenciaIdAtiva,
            onEncerrada = { tela = "avaliacao" },
            onVoltar    = { tela = "dashboard-profissional" },
        )

        "avaliacao" -> AvaliacaoScreen(
            urgenciaId    = urgenciaIdAtiva,
            onConcluida   = {
                // Após avaliação do cliente: ir para pagamento
                urgenciaIdPagamento = urgenciaIdAtiva
                tela = "pagamento"
            },
            onPularForced = { tela = "dashboard-cliente" },
        )

        "pagamento" -> PagamentoScreen(
            urgenciaId   = urgenciaIdPagamento,
            onConfirmado = { tela = "dashboard-cliente" },
            onVoltar     = { tela = "dashboard-cliente" },
        )

        "welcome" -> WelcomeScreen(
            onEntrar   = { tela = "login" },
            onCadastro = { tela = "cadastro" },
            onBuscar   = { tela = "busca" },
        )

        "login" -> LoginScreen(
            onVoltar             = { tela = "welcome" },
            onEntrarProfissional = { tela = "dashboard-profissional" },
            onEntrarCliente      = { tela = "dashboard-cliente" },
            onCadastro           = { tela = "cadastro" },
        )

        "cadastro" -> CadastroScreen(
            onVoltar    = { tela = "welcome" },
            onConcluido = {
                tela = if (currentUserId != null) "onboarding-check" else "welcome"
            },
        )

        "onboarding-check" -> {
            var destino by remember { mutableStateOf("") }
            LaunchedEffect(currentUserId) {
                val uid = currentUserId
                    ?: run { tela = "welcome"; return@LaunchedEffect }
                val perfil = getPerfilAndroid(uid)
                destino = when {
                    perfil?.tipo == "profissional_certificado" ||
                            perfil?.tipo == "profissional_liberal" -> {
                        UrgenciasRealtimeManager.iniciar(
                            profissionalId = uid,
                            especialidade  = null,
                        )
                        "onboarding-profissional"
                    }
                    else -> "dashboard-cliente"
                }
            }
            if (destino.isNotEmpty()) {
                LaunchedEffect(destino) { tela = destino }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Verde)
                }
            }
        }

        "onboarding-profissional" -> OnboardingProfissionalScreen(
            onConcluido = { tela = "dashboard-profissional" },
            onPular     = { tela = "dashboard-profissional" },
        )

        "dashboard-profissional" -> DashboardProfissionalComRealtime(
            onSair           = { UrgenciasRealtimeManager.parar(); tela = "welcome" },
            onEstudio        = { tela = "estudio-dashboard" },
            onPerfil         = { tela = "perfil-profissional" },
            onIniciarChamada = { urgenciaId ->
                urgenciaIdAtiva = urgenciaId
                StreamVideoRepository.solicitarToken(urgenciaId)
                tela = "sala-chamada"
            },
        )

        "busca" -> BuscaScreen(
            onVoltar  = { tela = "welcome" },
            onEstudio = { profId -> estudioProfId = profId; tela = "estudio-vitrine" },
            onPagar   = { tela = "pagamento" },
        )

        "perfil-profissional" -> PerfilProfissionalScreen(
            onVoltar = { tela = "dashboard-profissional" },
            userId   = currentUserId ?: "",
        )

        "perfil-cliente" -> PerfilClienteScreen(
            onVoltar = { tela = "dashboard-cliente" },
            userId   = currentUserId ?: "",
        )

        "dashboard-cliente" -> DashboardClienteScreen(
            onSair    = { tela = "welcome" },
            onEstudio = { profId -> estudioProfId = profId; tela = "estudio-vitrine" },
            onPerfil  = { tela = "perfil-cliente" },
            onChat    = { outroId, outroNome ->
                chatOutroId   = outroId
                chatOutroNome = outroNome
                chatDestino   = "dashboard-cliente"
                tela          = "chat"
            },
        )

        "estudio-dashboard" -> EstudioDashboardScreen(
            userId   = currentUserId ?: "",
            onVoltar = { tela = "dashboard-profissional" },
        )

        "estudio-busca" -> EstudioBuscaScreen(
            onVoltar = { tela = "welcome" },
        )

        "estudio-vitrine" -> EstudioVitrineScreen(
            profissionalId = estudioProfId,
            onVoltar       = { tela = "busca" },
        )


    }
}

// ── Wrapper suspend para verificar pagamento pendente ────────────────────
// Usado no LaunchedEffect(navState) para redirecionar ao abrir o app
private suspend fun verificarPagamentoPendente(): String? =
    PagamentoRepository.verificarPendencia()

// ═════════════════════════════════════════════════════════════════════════════
// DASHBOARD PROFISSIONAL COM REALTIME  · v2
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DashboardProfissionalComRealtime(
    onSair:           () -> Unit,
    onEstudio:        () -> Unit,
    onPerfil:         () -> Unit,
    onIniciarChamada: (urgenciaId: String) -> Unit = {},
) {
    var urgenciaAtiva           by remember { mutableStateOf<Urgencia?>(null) }
    var feedbackMsg             by remember { mutableStateOf("") }
    var mostrarDialogJaAtendida by remember { mutableStateOf(false) }

    val scope          = rememberCoroutineScope()
    val statusRealtime by UrgenciasRealtimeManager.status.collectAsState()
    val aceitacaoState by UrgenciasRealtimeManager.aceitacaoState.collectAsState()

    val isCarregando = aceitacaoState is AceitacaoState.Carregando

    LaunchedEffect(Unit) {
        UrgenciasRealtimeManager.eventos.collect { evento ->
            when (evento) {
                is EventoUrgencia.NovoChamado -> {
                    urgenciaAtiva = evento.urgencia
                    feedbackMsg   = ""
                }
                is EventoUrgencia.ChamadoAceito -> {
                    if (urgenciaAtiva?.id == evento.urgenciaId) {
                        urgenciaAtiva = null
                        feedbackMsg   = "Chamado já foi atendido por outro profissional"
                    }
                }
                is EventoUrgencia.ChamadoEncerrado -> {
                    if (urgenciaAtiva?.id == evento.urgenciaId) {
                        urgenciaAtiva = null
                        feedbackMsg   = when (evento.motivo) {
                            "expired"   -> "Chamado expirado"
                            "cancelled" -> "Chamado cancelado pelo cliente"
                            else        -> "Chamado encerrado"
                        }
                    }
                }
                is EventoUrgencia.ChamadaIniciada -> {
                    urgenciaAtiva = null
                }
            }
        }
    }

    LaunchedEffect(aceitacaoState) {
        val estado = aceitacaoState
        if (estado !is AceitacaoState.Resultado) return@LaunchedEffect
        when (val resultado = estado.resultado) {
            is ResultadoAceitacao.Sucesso -> {
                val id = urgenciaAtiva?.id ?: return@LaunchedEffect
                urgenciaAtiva = null
                onIniciarChamada(id)
            }
            is ResultadoAceitacao.JaAtendida -> {
                urgenciaAtiva           = null
                mostrarDialogJaAtendida = true
            }
            is ResultadoAceitacao.ErroRede -> {
                feedbackMsg = "Erro de conexão: ${resultado.mensagem}"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DashboardProfissionalScreen(
            onSair    = onSair,
            onEstudio = onEstudio,
            onPerfil  = onPerfil,
        )
        when (statusRealtime) {
            StatusRealtime.INSTAVEL -> BannerRealtimeInstavel(offline = false)
            StatusRealtime.OFFLINE  -> BannerRealtimeInstavel(offline = true)
            else                    -> Unit
        }
        if (feedbackMsg.isNotEmpty()) {
            LaunchedEffect(feedbackMsg) {
                delay(3_500)
                feedbackMsg = ""
            }
            Box(
                modifier         = Modifier.fillMaxSize().padding(bottom = 28.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape           = RoundedCornerShape(8.dp),
                    color           = Color(0xFF323232),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text     = feedbackMsg,
                        color    = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    urgenciaAtiva?.let { urgencia ->
        AlertDialogUrgencia(
            urgencia     = urgencia,
            isCarregando = isCarregando,
            onAceitar    = { UrgenciasRealtimeManager.aceitarViaRpc(urgencia.id) },
            onRecusar    = {
                urgenciaAtiva = null
                scope.launch { recusarUrgencia(urgencia.id) }
            },
        )
    }

    if (mostrarDialogJaAtendida) {
        AlertDialog(
            onDismissRequest = { mostrarDialogJaAtendida = false },
            title = { Text("Chamado já atendido") },
            text  = {
                Text(
                    text      = "Esta urgência já foi atendida por outro profissional.",
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Button(
                    onClick = { mostrarDialogJaAtendida = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Verde),
                ) { Text("Entendi", color = Color.White) }
            },
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// MODAL DE URGÊNCIA
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun AlertDialogUrgencia(
    urgencia:     Urgencia,
    isCarregando: Boolean,
    onAceitar:    () -> Unit,
    onRecusar:    () -> Unit,
) {
    var segundosRestantes by remember(urgencia.id) { mutableIntStateOf(30) }

    LaunchedEffect(urgencia.id) {
        while (segundosRestantes > 0) {
            delay(1_000)
            segundosRestantes--
        }
        onRecusar()
    }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text  = "🚨 Chamado Urgente",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Text("Cliente: ${urgencia.nomeCliente ?: "—"}")
                Text("Especialidade: ${urgencia.especialidade ?: "—"}")
                if (isCarregando) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color       = Verde,
                            modifier    = Modifier.padding(8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    Text(
                        text  = "⏱ $segundosRestantes s",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = if (segundosRestantes <= 10) Color.Red else Color.Unspecified,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = onAceitar,
                enabled  = !isCarregando,
                colors   = ButtonDefaults.buttonColors(containerColor = Verde),
            ) { Text("✅ Aceitar", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onRecusar, enabled = !isCarregando) {
                Text("❌ Recusar")
            }
        },
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// BANNER DE STATUS DO REALTIME
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun BannerRealtimeInstavel(offline: Boolean) {
    val (cor, msg) = if (offline)
        Color(0xFFB71C1C) to "Sem conexão — você pode perder chamados"
    else
        Color(0xFFF57F17) to "Conexão instável — atualizando..."

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(modifier = Modifier.fillMaxWidth(), color = cor) {
            Text(
                text      = msg,
                color     = Color.White,
                style     = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
            )
        }
    }
}