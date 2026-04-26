package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// OnboardingScreen.kt  · Fase 1.6
//
// 4 telas via HorizontalPager (Compose Foundation):
//   1. Promessa central   — ⚡ Conecte-se em segundos
//   2. Confiança          — 🛡️ Seguro, verificado e confiável
//   3. Como funciona      — 🔄 Simples do início ao fim
//   4. Decisão de papel   — dois cards clicáveis (Cliente / Profissional)
//
// UI segue o design system do projeto:
//   Fundo  → SurfaceWarm  (padrão de todas as telas)
//   CTA    → Verde        (botão primário padrão)
//   Título → Azul / Ink
//   Dots   → Verde ativo, SurfaceOff inativo
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// MODELO DE PÁGINA (telas 1–3)
// ═══════════════════════════════════════════════════════════════════════════

private data class PaginaOnboarding(
    val emoji:     String,
    val titulo:    String,
    val descricao: String,
    val ctaLabel:  String,
)

private val paginas = listOf(
    PaginaOnboarding(
        emoji     = "⚡",
        titulo    = "Conecte-se em segundos",
        descricao = "Encontre profissionais qualificados ou atenda clientes imediatamente. Tudo em tempo real, sem complicação.",
        ctaLabel  = "Começar",
    ),
    PaginaOnboarding(
        emoji     = "🛡️",
        titulo    = "Seguro, verificado e confiável",
        descricao = "Profissionais passam por verificação antes de atender. Você escolhe com base em avaliações reais.",
        ctaLabel  = "Continuar",
    ),
    PaginaOnboarding(
        emoji     = "🔄",
        titulo    = "Simples do início ao fim",
        descricao = "Solicite atendimento ou fique disponível para receber chamados. Aceite, conecte-se e resolva — tudo no app.",
        ctaLabel  = "Avançar",
    ),
)

// ═══════════════════════════════════════════════════════════════════════════
// SCREEN PRINCIPAL
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingScreen(
    onIrParaCliente:       () -> Unit,
    onIrParaProfissional:  () -> Unit,
) {
    val context   = LocalContext.current
    val factory   = remember { OnboardingViewModelFactory(context.onboardingDataStore) }
    val vm: OnboardingViewModel = viewModel(factory = factory)
    val navState  by vm.navState.collectAsState()

    // Reagir a decisão da Tela 4
    LaunchedEffect(navState) {
        when (navState) {
            is OnboardingNavState.IrParaCliente      -> onIrParaCliente()
            is OnboardingNavState.IrParaProfissional -> onIrParaProfissional()
            else                                     -> Unit
        }
    }

    val totalPaginas = paginas.size + 1   // 3 info + 1 decisão
    val pagerState   = rememberPagerState(pageCount = { totalPaginas })
    val scope        = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Pager ─────────────────────────────────────────────────────
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                if (page < paginas.size) {
                    // Telas 1–3: layout informativo
                    PaginaInformativa(pagina = paginas[page])
                } else {
                    // Tela 4: decisão de papel
                    PaginaDecisao(
                        onCliente       = { vm.selecionarCliente() },
                        onProfissional  = { vm.selecionarProfissional() },
                    )
                }
            }

            // ── Dots + CTA ────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Indicador de página
                DotsIndicator(
                    total   = totalPaginas,
                    current = pagerState.currentPage,
                )

                // CTA só nas telas 1–3; Tela 4 tem seus próprios cards
                if (pagerState.currentPage < paginas.size) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape  = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Verde,
                            contentColor   = Color.White,
                        ),
                    ) {
                        Text(
                            text       = paginas[pagerState.currentPage].ctaLabel,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    // Placeholder de altura para manter layout consistente na Tela 4
                    Spacer(modifier = Modifier.height(52.dp))
                }

                // Pular (apenas telas 1–3)
                if (pagerState.currentPage < paginas.size) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(totalPaginas - 1)
                            }
                        }
                    ) {
                        Text(
                            text     = "Pular",
                            color    = InkMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TELA INFORMATIVA (páginas 1–3)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PaginaInformativa(pagina: PaginaOnboarding) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Ícone em círculo com gradiente sutil
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Verde.copy(alpha = 0.18f),
                            Verde.copy(alpha = 0.04f),
                        )
                    )
                )
                .border(1.5.dp, Verde.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(pagina.emoji, fontSize = 48.sp)
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text       = pagina.titulo,
            fontSize   = 26.sp,
            fontWeight = FontWeight.Black,
            color      = Ink,
            textAlign  = TextAlign.Center,
            lineHeight = 32.sp,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text       = pagina.descricao,
            fontSize   = 15.sp,
            color      = InkMuted,
            textAlign  = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TELA 4 — DECISÃO DE PAPEL
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PaginaDecisao(
    onCliente:      () -> Unit,
    onProfissional: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text       = "Como você quer\nusar o app?",
            fontSize   = 26.sp,
            fontWeight = FontWeight.Black,
            color      = Ink,
            textAlign  = TextAlign.Center,
            lineHeight = 32.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text      = "Você poderá alterar isso depois.",
            fontSize  = 13.sp,
            color     = InkMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Card Cliente
        CardDecisao(
            emoji      = "🔍",
            titulo     = "Sou Cliente",
            subtexto   = "Quero encontrar um profissional agora",
            corBorda   = Azul,
            corFundo   = Azul.copy(alpha = 0.06f),
            onClick    = onCliente,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card Profissional
        CardDecisao(
            emoji      = "💼",
            titulo     = "Sou Profissional",
            subtexto   = "Quero atender clientes e receber chamados",
            corBorda   = Verde,
            corFundo   = Verde.copy(alpha = 0.06f),
            onClick    = onProfissional,
        )
    }
}

@Composable
private fun CardDecisao(
    emoji:    String,
    titulo:   String,
    subtexto: String,
    corBorda: Color,
    corFundo: Color,
    onClick:  () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, corBorda.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = corFundo,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier            = Modifier.padding(20.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Ícone
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(corBorda.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 26.sp)
            }

            // Textos
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = titulo,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Ink,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text     = subtexto,
                    fontSize = 13.sp,
                    color    = InkMuted,
                    lineHeight = 18.sp,
                )
            }

            // Seta indicativa
            Text(
                text      = "→",
                fontSize  = 20.sp,
                color     = corBorda,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// INDICADOR DE PÁGINAS (dots)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DotsIndicator(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            val isActive = index == current
            val width by animateDpAsState(
                targetValue = if (isActive) 24.dp else 8.dp,
                animationSpec = tween(durationMillis = 250),
                label = "dot_width_$index",
            )
            val cor by animateColorAsState(
                targetValue = if (isActive) Verde else SurfaceOff,
                animationSpec = tween(durationMillis = 250),
                label = "dot_color_$index",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(cor),
            )
        }
    }
}