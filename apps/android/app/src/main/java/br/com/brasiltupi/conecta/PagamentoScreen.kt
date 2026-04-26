package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// PagamentoScreen.kt  · Fase 2.2
//
// Responsabilidades:
//  1. Observar PagamentoState e orquestrar WebView conforme cada estado
//  2. Ao entrar com Idle → chama criarPreferencia(urgenciaId)
//  3. Ao receber CheckoutAberto → carrega init_point na WebView
//  4. WebViewClient valida URLs — só permite mercadopago.com e brasiltupi.com.br
//  5. Intercepta URLs de retorno → notifica repositório
//  6. Ao receber Confirmado (via Realtime) → navega automaticamente
//  7. Ao receber Cancelado → dialog com retry sem nova chamada ao backend
//  8. DisposableEffect limpa a WebView ao sair (sem memory leak)
//  9. AppLogger em carregamento, erros SSL e detecção de retorno
// ═══════════════════════════════════════════════════════════════════════════

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import br.com.brasiltupi.conecta.ui.theme.*

private const val TAG_PAG = "PagamentoScreen"

// ── Domínios permitidos na WebView ────────────────────────────────────────
// Qualquer URL fora desta lista é bloqueada — segurança contra redirect malicioso
private val DOMINIOS_PERMITIDOS = listOf(
    "mercadopago.com",
    "mercadolibre.com",   // MP usa subdomínios do ML em alguns fluxos
    "brasiltupi.com.br",  // domínio de retorno configurado na Edge Function
    "qfzdchrlbqcvewjivaqz.supabase.co",  // fallback de retorno interno
)

// ── URLs de retorno configuradas na Edge Function ─────────────────────────
private const val URL_SUCESSO  = "brasiltupi.com.br/pagamento/sucesso"
private const val URL_PENDENTE = "brasiltupi.com.br/pagamento/pendente"
private const val URL_FALHA    = "brasiltupi.com.br/pagamento/falha"

// ═══════════════════════════════════════════════════════════════════════════
// TELA PRINCIPAL
// ═══════════════════════════════════════════════════════════════════════════

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PagamentoScreen(
    urgenciaId:      String,
    onConfirmado:    () -> Unit,  // navegar para dashboard após aprovação
    onVoltar:        () -> Unit,  // voltar (cancelamento definitivo ou erro)
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagamentoState by PagamentoRepository.state.collectAsState()

    // ── Referência à WebView — gerenciada pelo DisposableEffect ──────────
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // ── Estado local de UI ────────────────────────────────────────────────
    var mostrarDialogCancelado  by remember { mutableStateOf(false) }
    var mostrarDialogPendente   by remember { mutableStateOf(false) }
    var mostrarDialogErro       by remember { mutableStateOf(false) }
    var mensagemErro            by remember { mutableStateOf("") }
    var initPointParaRetry      by remember { mutableStateOf("") }
    var webViewCarregando       by remember { mutableStateOf(false) }

    // ── Iniciar preferência se Idle ───────────────────────────────────────
    LaunchedEffect(Unit) {
        if (pagamentoState is PagamentoState.Idle) {
            AppLogger.infoPagamento(
                etapa      = "tela_iniciada",
                urgenciaId = urgenciaId,
                detalhe    = "Estado Idle — criando preferencia",
            )
            PagamentoRepository.criarPreferencia(urgenciaId)
        }
    }

    // ── Orquestrador de estados ───────────────────────────────────────────
    LaunchedEffect(pagamentoState) {
        when (val estado = pagamentoState) {

            is PagamentoState.CheckoutAberto -> {
                AppLogger.infoPagamento(
                    etapa      = "webview_carregando",
                    urgenciaId = urgenciaId,
                    detalhe    = "init_point=${estado.initPoint.take(60)}",
                )
                webViewRef?.loadUrl(estado.initPoint)
                PagamentoRepository.notificarCheckoutAberto(urgenciaId)
            }

            is PagamentoState.Confirmado -> {
                AppLogger.infoPagamento(
                    etapa      = "pagamento_confirmado_tela",
                    urgenciaId = urgenciaId,
                    detalhe    = "valor=${estado.valor}",
                )
                PagamentoRepository.resetar()
                onConfirmado()
            }

            is PagamentoState.Pendente -> {
                mostrarDialogPendente = true
            }

            is PagamentoState.Cancelado -> {
                initPointParaRetry     = estado.initPoint
                mostrarDialogCancelado = true
            }

            is PagamentoState.Erro -> {
                mensagemErro     = estado.motivo
                mostrarDialogErro = true
            }

            else -> Unit
        }
    }

    // ── Guard de navegação — BackHandler ──────────────────────────────────
    BackHandler {
        val estado = pagamentoState
        when {
            // WebView pode navegar para trás no histórico interno
            webViewRef?.canGoBack() == true &&
                    estado is PagamentoState.CheckoutAberto -> {
                webViewRef?.goBack()
            }
            else -> {
                // Sair da tela — tratar como cancelamento
                initPointParaRetry = when (estado) {
                    is PagamentoState.CheckoutAberto -> estado.initPoint
                    is PagamentoState.Cancelado      -> estado.initPoint
                    else                             -> ""
                }
                if (initPointParaRetry.isNotEmpty()) {
                    mostrarDialogCancelado = true
                } else {
                    PagamentoRepository.resetar()
                    onVoltar()
                }
            }
        }
    }

    // ── DisposableEffect — cleanup da WebView ao sair ─────────────────────
    // Previne memory leak: WebView segura referência à Activity se não limpa
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                webViewRef?.apply {
                    stopLoading()
                    clearHistory()
                    clearCache(true)
                    loadUrl("about:blank")
                    onPause()
                    removeAllViews()
                    destroy()
                }
                webViewRef = null
                AppLogger.infoPagamento(
                    etapa      = "webview_destruida",
                    urgenciaId = urgenciaId,
                    detalhe    = "ON_DESTROY — cleanup executado",
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                loadUrl("about:blank")
                onPause()
                removeAllViews()
                destroy()
            }
            webViewRef = null
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm),
    ) {
        when (val estado = pagamentoState) {

            // Loading enquanto cria preferência
            is PagamentoState.Idle,
            is PagamentoState.CriandoPreferencia -> {
                LoadingPagamento("Preparando checkout...")
            }

            // WebView do checkout
            is PagamentoState.CheckoutAberto -> {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Topbar mínima com botão voltar
                    TopBarPagamento(
                        descricao = estado.descricao,
                        valor     = estado.valor,
                        onVoltar  = {
                            initPointParaRetry = estado.initPoint
                            mostrarDialogCancelado = true
                        },
                    )

                    // WebView
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.apply {
                                        javaScriptEnabled       = true
                                        domStorageEnabled       = true
                                        loadWithOverviewMode    = true
                                        useWideViewPort         = true
                                        builtInZoomControls     = false
                                        displayZoomControls     = false
                                        setSupportZoom(false)
                                    }

                                    webViewClient = PagamentoWebViewClient(
                                        urgenciaId     = urgenciaId,
                                        onPageStarted  = { webViewCarregando = true },
                                        onPageFinished = { webViewCarregando = false },
                                        onRetornoDetectado = { url ->
                                            PagamentoRepository.notificarRetornoCheckout(
                                                url        = url,
                                                urgenciaId = urgenciaId,
                                            )
                                        },
                                    )

                                    webViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Overlay de loading sobre a WebView durante navegação
                        if (webViewCarregando) {
                            Box(
                                modifier         = Modifier
                                    .fillMaxSize()
                                    .background(SurfaceWarm.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Verde)
                            }
                        }
                    }
                }
            }

            // Aguardando confirmação do Realtime
            is PagamentoState.Processando -> {
                LoadingPagamento("Confirmando pagamento...")
            }

            // Confirmado — tratado no LaunchedEffect (onConfirmado())
            is PagamentoState.Confirmado -> {
                LoadingPagamento("Pagamento confirmado! Redirecionando...")
            }

            // Estados de erro/cancelamento — tratados via dialogs
            else -> {
                LoadingPagamento("Aguardando...")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIÁLOGOS
    // ═══════════════════════════════════════════════════════════════════════

    // Cancelamento — oferecer retry
    if (mostrarDialogCancelado) {
        AlertDialog(
            onDismissRequest = { mostrarDialogCancelado = false },
            title = {
                Text("Pagamento não concluído", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Você saiu sem finalizar o pagamento.\n\nO serviço prestado ainda aguarda confirmação de pagamento.",
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarDialogCancelado = false
                        if (initPointParaRetry.isNotEmpty()) {
                            PagamentoRepository.tentarNovamente(urgenciaId, initPointParaRetry)
                        } else {
                            PagamentoRepository.criarPreferencia(urgenciaId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Tentar novamente", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    mostrarDialogCancelado = false
                    PagamentoRepository.resetar()
                    onVoltar()
                }) {
                    Text("Sair", color = InkMuted)
                }
            },
        )
    }

    // Pendente — boleto/PIX em análise
    if (mostrarDialogPendente) {
        AlertDialog(
            onDismissRequest = { mostrarDialogPendente = false },
            title = { Text("Pagamento em análise", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "Seu pagamento foi recebido e está em processamento.\n\nVocê receberá uma notificação quando for confirmado.",
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarDialogPendente = false
                        PagamentoRepository.resetar()
                        onVoltar()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Ok, entendi", color = Color.White)
                }
            },
        )
    }

    // Erro
    if (mostrarDialogErro) {
        AlertDialog(
            onDismissRequest = { mostrarDialogErro = false },
            title = { Text("Erro no pagamento", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    mensagemErro,
                    textAlign  = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarDialogErro = false
                        PagamentoRepository.criarPreferencia(urgenciaId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) {
                    Text("Tentar novamente", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    mostrarDialogErro = false
                    PagamentoRepository.resetar()
                    onVoltar()
                }) {
                    Text("Cancelar", color = InkMuted)
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// WEBVIEWCLIENT SEGURO
//
// Responsabilidades:
//  • shouldOverrideUrlLoading — bloqueia domínios não autorizados
//  • onPageStarted / onPageFinished — controla indicador de loading
//  • onReceivedSslError — loga e cancela (nunca aceitar SSL inválido)
//  • onReceivedError — loga falhas de carregamento
// ═══════════════════════════════════════════════════════════════════════════

private class PagamentoWebViewClient(
    private val urgenciaId:        String,
    private val onPageStarted:     () -> Unit,
    private val onPageFinished:    () -> Unit,
    private val onRetornoDetectado: (String) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view:    WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url.toString()
        android.util.Log.d(TAG_PAG, "WebView navegando: $url")

        // ── Detectar URLs de retorno ANTES da validação de domínio ────────
        when {
            url.contains(URL_SUCESSO,  ignoreCase = true) -> {
                AppLogger.infoPagamento(
                    etapa      = "retorno_sucesso",
                    urgenciaId = urgenciaId,
                    detalhe    = url.take(80),
                )
                onRetornoDetectado(url)
                return true   // bloquear carregamento — já tratamos
            }
            url.contains(URL_PENDENTE, ignoreCase = true) -> {
                AppLogger.infoPagamento(
                    etapa      = "retorno_pendente",
                    urgenciaId = urgenciaId,
                    detalhe    = url.take(80),
                )
                onRetornoDetectado(url)
                return true
            }
            url.contains(URL_FALHA,    ignoreCase = true) -> {
                AppLogger.infoPagamento(
                    etapa      = "retorno_falha",
                    urgenciaId = urgenciaId,
                    detalhe    = url.take(80),
                )
                onRetornoDetectado(url)
                return true
            }
        }

        // ── Validar domínio — bloquear qualquer URL fora da lista ─────────
        val dominioPermitido = DOMINIOS_PERMITIDOS.any { dominio ->
            url.contains(dominio, ignoreCase = true)
        }

        if (!dominioPermitido) {
            AppLogger.aviso(
                TAG_PAG,
                "WebView bloqueou navegacao para dominio nao autorizado: ${url.take(80)}"
            )
            return true   // bloquear — não navegar
        }

        return false   // permitir — WebView carrega normalmente
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished()
        AppLogger.infoPagamento(
            etapa      = "webview_pagina_carregada",
            urgenciaId = urgenciaId,
            detalhe    = url?.take(80) ?: "",
        )
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(
        view:    WebView,
        handler: SslErrorHandler,
        error:   SslError,
    ) {
        // NUNCA chamar handler.proceed() — isso aceitaria certificados inválidos
        handler.cancel()
        AppLogger.erroPagamento(
            etapa      = "webview_ssl_error",
            urgenciaId = urgenciaId,
            corpo      = "SSL error code=${error.primaryError} url=${error.url?.take(60)}",
        )
    }

    override fun onReceivedError(
        view:     WebView,
        request:  WebResourceRequest,
        error:    WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        // Logar apenas erros do frame principal (não recursos secundários)
        if (request.isForMainFrame) {
            AppLogger.erroPagamento(
                etapa      = "webview_erro_carregamento",
                urgenciaId = urgenciaId,
                corpo      = "code=${error.errorCode} desc=${error.description}",
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLES AUXILIARES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TopBarPagamento(
    descricao: String,
    valor:     Double,
    onVoltar:  () -> Unit,
) {
    val valorFormatado = "R$ %.2f".format(valor).replace(".", ",")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Azul)
            .padding(horizontal = 16.dp)
            .padding(top = 48.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onVoltar) {
            Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = descricao,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White,
            )
            Text(
                text     = valorFormatado,
                fontSize = 12.sp,
                color    = Color.White.copy(alpha = 0.8f),
            )
        }
        // Ícone de cadeado — indica conexão segura
        Text("🔒", fontSize = 16.sp)
    }
}

@Composable
private fun LoadingPagamento(mensagem: String) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(SurfaceWarm),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CircularProgressIndicator(color = Verde, strokeWidth = 3.dp)
            Text(
                text      = mensagem,
                fontSize  = 14.sp,
                color     = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}