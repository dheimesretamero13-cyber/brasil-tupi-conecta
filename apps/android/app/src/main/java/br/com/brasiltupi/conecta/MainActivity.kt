package br.com.brasiltupi.conecta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
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

    // Referência ao ViewModel de onboarding — necessária para onResume
    // poder gravar o timestamp sem depender do escopo Compose.
    // Lazy: inicializado apenas quando o Compose sobe e passa a referência.
    private var onboardingVmRef: OnboardingViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrasilTupiConectaTheme {
                AppNavigation(onVmReady = { vm -> onboardingVmRef = vm })
            }
        }
    }

    // ── Fase 5: gravação do timestamp aqui garante persistência mesmo em ──
    // process kill. O LaunchedEffect do Compose pode não terminar se o SO
    // matar o processo antes do DataStore.edit() completar.
    // onResume é chamado sempre que o app volta ao foreground — seguro e
    // confiável independente do estado do Compose.
    override fun onResume() {
        super.onResume()
        onboardingVmRef?.let { vm ->
            lifecycleScope.launch {
                vm.salvarUltimoAcesso(System.currentTimeMillis())
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
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun AppNavigation(onVmReady: (OnboardingViewModel) -> Unit = {}) {
    val context = LocalContext.current
    val onboardingVm: OnboardingViewModel = viewModel(
        factory = OnboardingViewModelFactory(context.onboardingDataStore)
    )
    val navState by onboardingVm.navState.collectAsState()

    // Expõe o ViewModel para a Activity logo que o Composable sobe.
    // A Activity usa a referência em onResume() para salvar o timestamp.
    LaunchedEffect(onboardingVm) { onVmReady(onboardingVm) }

    // ── Estado de navegação ───────────────────────────────────────────────
    var tela                      by remember { mutableStateOf("") }
    var estudioProfId             by remember { mutableStateOf("") }
    var chatOutroId               by remember { mutableStateOf("") }
    var chatOutroNome             by remember { mutableStateOf("") }
    var chatDestino               by remember { mutableStateOf("dashboard-cliente") }
    var urgenciaIdAtiva           by remember { mutableStateOf("") }
    var urgenciaIdPagamento       by remember { mutableStateOf("") }
    // Fase 3.1 — Player
    var aulaIdAtiva               by remember { mutableStateOf("") }
    var cursoIdAtivo              by remember { mutableStateOf("") }
    var tituloAulaAtiva           by remember { mutableStateOf("") }
    // Fase 3.2 — PDF
    var produtoIdAtivo            by remember { mutableStateOf("") }
    var tituloProdutoAtivo        by remember { mutableStateOf("") }
    var allowScreenshotAtivo      by remember { mutableStateOf(true) }
    // Fase 3.3 — Biblioteca
    var bibliotecaProdutoId       by remember { mutableStateOf("") }
    var bibliotecaTitulo          by remember { mutableStateOf("") }
    var bibliotecaAllowScreenshot by remember { mutableStateOf(true) }
    // Fase 3.4 — Busca
    var searchItemSelecionado     by remember { mutableStateOf<ResultadoBusca?>(null) }
    // Fase 3.5 — Chat pré-chamada
    var chatSessionId             by remember { mutableStateOf("") }
    var chatOutroNomePre          by remember { mutableStateOf("") }
    // Fase 4.2 — Referral
    var suporteAgendamentoId      by remember { mutableStateOf<String?>(null) }
    // Fase 4.3 — Suporte e disputas
    // Fase 1 — LGPD: dashboard destino após consentimento gravado
    var destinoAposLegal          by remember { mutableStateOf("") }

    // ── Deep link FCM ─────────────────────────────────────────────────────
    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) {
        val intent        = activity?.intent ?: return@LaunchedEffect
        val fcmTela       = intent.getStringExtra("fcm_tela")        ?: return@LaunchedEffect
        val fcmUrgenciaId = intent.getStringExtra("fcm_urgencia_id") ?: ""
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
        intent.removeExtra("fcm_tela")
    }

    // ── Resolver startDestination via DataStore ───────────────────────────
    LaunchedEffect(navState) {
        if (tela.isNotEmpty()) return@LaunchedEffect
        when (navState) {
            is OnboardingNavState.Carregando        -> Unit

            is OnboardingNavState.MostrarOnboarding -> {
                AnalyticsTracker.appInstall()
                tela = "onboarding"
            }

            is OnboardingNavState.IrParaCliente     -> {
                // Fase 4.5 — Retenção
                val agora       = System.currentTimeMillis()
                val ultimoAces  = onboardingVm.ultimoAcesso()
                val diasAusente = if (ultimoAces > 0L) (agora - ultimoAces) / 86_400_000L else 0L
                AnalyticsTracker.setUserType("cliente")
                when {
                    diasAusente >= 30 -> AnalyticsTracker.retention30d("cliente")
                    diasAusente >= 7  -> AnalyticsTracker.retention7d("cliente")
                }
                // Fase 5: gravação do timestamp movida para MainActivity.onResume()

                val pendenteAvaliacao = AvaliacaoRepository.verificarPendencia()
                if (pendenteAvaliacao != null) {
                    urgenciaIdAtiva = pendenteAvaliacao.id
                    tela = "avaliacao"
                    return@LaunchedEffect
                }
                val pendentePagamento = verificarPagamentoPendente()
                if (pendentePagamento != null) {
                    urgenciaIdPagamento = pendentePagamento
                    tela = "pagamento"
                    return@LaunchedEffect
                }

                // Fase 1 — LGPD: só executado se já logado
                val uid = currentUserId
                if (uid.isNullOrEmpty()) {
                    tela = "welcome"
                    return@LaunchedEffect
                }
                val jaAceitou = verificarConsentimentoExiste(uid)
                if (!jaAceitou) {
                    destinoAposLegal = "dashboard-cliente"
                    tela = "legal-onboarding"
                } else {
                    tela = "dashboard-cliente"
                }
            }

            is OnboardingNavState.IrParaProfissional -> {
                // Fase 4.5 — Retenção
                val agora       = System.currentTimeMillis()
                val ultimoAces  = onboardingVm.ultimoAcesso()
                val diasAusente = if (ultimoAces > 0L) (agora - ultimoAces) / 86_400_000L else 0L
                AnalyticsTracker.setUserType("profissional")
                when {
                    diasAusente >= 30 -> AnalyticsTracker.retention30d("profissional")
                    diasAusente >= 7  -> AnalyticsTracker.retention7d("profissional")
                }
                // Fase 5: gravação do timestamp movida para MainActivity.onResume()

                BrasilTupiMessagingService.registrarTokenSeLogado(this, context)

                val pendente = AvaliacaoRepository.verificarPendencia()
                if (pendente != null) {
                    urgenciaIdAtiva = pendente.id
                    tela = "avaliacao"
                    return@LaunchedEffect
                }

                // Fase 1 — LGPD: só executado se já logado
                val uidProf = currentUserId
                if (uidProf.isNullOrEmpty()) {
                    tela = "welcome"
                    return@LaunchedEffect
                }
                val jaAceitouProf = verificarConsentimentoExiste(uidProf)
                if (!jaAceitouProf) {
                    destinoAposLegal = "dashboard-profissional"
                    tela = "legal-onboarding"
                } else {
                    UrgenciasRealtimeManager.iniciar(profissionalId = uidProf, especialidade = null)
                    tela = "dashboard-profissional"
                }
            }
        }
    }

    // ── Loading ───────────────────────────────────────────────────────────
    if (tela.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Verde)
        }
        return
    }

    // ── Roteador principal ────────────────────────────────────────────────
    when (tela) {

        // ── FASE 1 — LGPD ─────────────────────────────────────────────────
        // Exibida APÓS login — userId e currentToken sempre preenchidos aqui.
        "legal-onboarding" -> LegalOnboardingScreen(
            userId                   = currentUserId ?: "",
            onConsentimentoConcluido = {
                val destino = destinoAposLegal.ifEmpty { "welcome" }
                if (destino == "dashboard-profissional") {
                    currentUserId?.let { uid ->
                        UrgenciasRealtimeManager.iniciar(profissionalId = uid, especialidade = null)
                    }
                }
                tela = destino
            },
            onVerTermos   = { tela = "termos-uso" },
            onVerPolitica = { tela = "politica-privacidade" },
        )

        "termos-uso" -> TermosUsoScreen(
            onVoltar = { tela = "legal-onboarding" },
        )

        "politica-privacidade" -> PoliticaPrivacidadeScreen(
            onVoltar = { tela = "legal-onboarding" },
        )

        // ── FASE 1 — KYC ──────────────────────────────────────────────────
        "kyc-upload" -> KycUploadScreen(
            userId            = currentUserId ?: "",
            onUploadConcluido = { tela = "kyc-status" },
            onVoltar          = { tela = "kyc-status" },
        )

        "kyc-status" -> KycStatusScreen(
            userId            = currentUserId ?: "",
            onEnviarDocumento = { tela = "kyc-upload" },
            onVoltar          = { tela = "perfil-profissional" },
        )

        // ── ONBOARDING ────────────────────────────────────────────────────
        // Vai direto para welcome — LGPD só é exibida após login no LaunchedEffect acima.
        "onboarding" -> OnboardingScreen(
            onIrParaCliente      = {
                onboardingVm.selecionarCliente()
                tela = "welcome"
            },
            onIrParaProfissional = {
                onboardingVm.selecionarProfissional()
                tela = "welcome"
            },
        )

        // ── CHAT GERAL (tabela mensagens) ─────────────────────────────────
        "chat" -> ChatScreen(
            outroId   = chatOutroId,
            outroNome = chatOutroNome,
            onVoltar  = { tela = chatDestino },
        )

        // ── VIDEOCHAMADA ──────────────────────────────────────────────────
        "sala-chamada" -> VideoCallScreen(
            urgenciaId  = urgenciaIdAtiva,
            onEncerrada = { tela = "avaliacao" },
            onVoltar    = { tela = "dashboard-profissional" },
        )

        // ── AVALIAÇÃO ─────────────────────────────────────────────────────
        "avaliacao" -> AvaliacaoScreen(
            urgenciaId    = urgenciaIdAtiva,
            onConcluida   = {
                urgenciaIdPagamento = urgenciaIdAtiva
                tela = "pagamento"
            },
            onPularForced = { tela = "dashboard-cliente" },
        )

        // ── PAGAMENTO ─────────────────────────────────────────────────────
        "pagamento" -> PagamentoScreen(
            urgenciaId   = urgenciaIdPagamento,
            onConfirmado = { tela = "dashboard-cliente" },
            onVoltar     = { tela = "dashboard-cliente" },
        )

        // ── AUTH ──────────────────────────────────────────────────────────
        "welcome" -> WelcomeScreen(
            onEntrar   = { tela = "login" },
            onCadastro = { tela = "cadastro" },
            onBuscar   = { tela = "busca" },
        )

        "login" -> LoginScreen(
            onVoltar             = { tela = "welcome" },
            onEntrarProfissional = {
                destinoAposLegal = "dashboard-profissional"
                tela = "legal-onboarding"
            },
            onEntrarCliente      = {
                destinoAposLegal = "dashboard-cliente"
                tela = "legal-onboarding"
            },
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
                        UrgenciasRealtimeManager.iniciar(profissionalId = uid, especialidade = null)
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

        // ── DASHBOARDS ────────────────────────────────────────────────────
        "dashboard-profissional" -> DashboardProfissionalComRealtime(
            onSair           = { UrgenciasRealtimeManager.parar(); tela = "welcome" },
            onEstudio        = { tela = "estudio-dashboard" },
            onPerfil         = { tela = "perfil-profissional" },
            onRelatorios     = { tela = "relatorios" },
            onIniciarChamada = { urgenciaId ->
                urgenciaIdAtiva = urgenciaId
                StreamVideoRepository.solicitarToken(urgenciaId)
                tela = "sala-chamada"
            },
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
            onSuporte = { agendamentoId ->
                suporteAgendamentoId = agendamentoId
                tela = "suporte"
            },
        )

        // ── PERFIS ────────────────────────────────────────────────────────
        "perfil-profissional" -> PerfilProfissionalScreen(
            onVoltar = { tela = "dashboard-profissional" },
            userId   = currentUserId ?: "",
            onKyc    = { tela = "kyc-status" },
        )

        "perfil-cliente" -> PerfilClienteScreen(
            onVoltar   = { tela = "dashboard-cliente" },
            userId     = currentUserId ?: "",
            onReferral = { tela = "referral" },
        )

        // ── BUSCA DE PROFISSIONAIS ─────────────────────────────────────────
        "busca" -> BuscaScreen(
            onVoltar  = { tela = "welcome" },
            onEstudio = { profId -> estudioProfId = profId; tela = "estudio-vitrine" },
            onPagar   = { tela = "pagamento" },
        )

        // ── ESTÚDIO ───────────────────────────────────────────────────────
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

        // ── FASE 3.1 — PLAYER DE VÍDEO ────────────────────────────────────
        "player-video" -> VideoPlayerScreen(
            aulaId     = aulaIdAtiva,
            cursoId    = cursoIdAtivo,
            tituloAula = tituloAulaAtiva,
            repository = ContentRepositoryFactory.create(),
            onVoltar   = { tela = "estudio-vitrine" },
        )

        // ── FASE 3.2 — VISUALIZADOR DE PDF ───────────────────────────────
        "pdf-viewer" -> PdfViewerScreen(
            produtoId       = produtoIdAtivo,
            tituloProduto   = tituloProdutoAtivo,
            allowScreenshot = allowScreenshotAtivo,
            repository      = ContentRepositoryFactory.create(),
            onVoltar        = { tela = "biblioteca" },
        )

        // ── FASE 3.3 — BIBLIOTECA DO CLIENTE ─────────────────────────────
        "biblioteca" -> BibliotecaScreen(
            onVoltar     = { tela = "dashboard-cliente" },
            onAbrirCurso = { produtoId, titulo ->
                aulaIdAtiva     = produtoId
                tituloAulaAtiva = titulo
                tela            = "player-video"
            },
            onAbrirPdf = { produtoId, titulo, allowScreenshot ->
                produtoIdAtivo       = produtoId
                tituloProdutoAtivo   = titulo
                allowScreenshotAtivo = allowScreenshot
                tela                 = "pdf-viewer"
            },
        )

        // ── FASE 3.4 — BUSCA DE CONTEÚDO ─────────────────────────────────
        "busca-conteudo" -> SearchScreen(
            onVoltar    = { tela = "dashboard-cliente" },
            onAbrirItem = { item ->
                searchItemSelecionado = item
                tela = "estudio-detalhe-busca"
            },
        )

        "estudio-detalhe-busca" -> searchItemSelecionado?.let { item ->
            EstudioDetalheScreen(
                item     = item.toItemEstudio(),
                onVoltar = { tela = "busca-conteudo" },
                onPagar  = { tela = "pagamento" },
            )
        }

        // ── FASE 3.5 — CHAT PRÉ-CHAMADA ──────────────────────────────────
        "chat-pre-chamada" -> ChatPreChamadaScreen(
            sessionId = chatSessionId,
            outroNome = chatOutroNomePre,
            onVoltar  = { tela = "dashboard-cliente" },
        )

        // ── FASE 4.2 — PROGRAMA DE INDICAÇÃO ─────────────────────────────
        "referral" -> ReferralScreen(
            onVoltar = { tela = "perfil-cliente" },
        )

        // ── FASE 4.3 — SUPORTE E DISPUTAS ────────────────────────────────
        "suporte" -> SuporteScreen(
            agendamentoId = suporteAgendamentoId,
            onVoltar      = { tela = "dashboard-cliente" },
        )

        // ── FASE 4.4 — RELATÓRIOS AVANÇADOS ──────────────────────────────
        "relatorios" -> RelatoriosProfissionalScreen(
            onVoltar = { tela = "dashboard-profissional" },
        )
    }
}

// ── Wrapper suspend para verificar pagamento pendente ────────────────────
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
    onRelatorios:     () -> Unit = {},
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
            onSair       = onSair,
            onEstudio    = onEstudio,
            onPerfil     = onPerfil,
            onRelatorios = onRelatorios,
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