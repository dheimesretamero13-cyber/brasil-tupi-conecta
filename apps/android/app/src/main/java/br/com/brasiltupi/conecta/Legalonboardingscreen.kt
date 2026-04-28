package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// LegalOnboardingScreen.kt
// Tela de consentimento LGPD — exibida UMA VEZ antes do primeiro dashboard.
// Usa gravarConsentimento() do SupabaseClient.kt — mesmo padrão do projeto.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

const val VERSAO_TERMOS = "1.0"

@Composable
fun LegalOnboardingScreen(
    userId: String,
    onConsentimentoConcluido: () -> Unit,
    onVerTermos:   () -> Unit = {},
    onVerPolitica: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var aceitoTermos      by remember { mutableStateOf(false) }
    var aceitoPrivacidade by remember { mutableStateOf(false) }
    var verificando       by remember { mutableStateOf(true) }  // true = checando consentimento
    var carregando        by remember { mutableStateOf(false) }
    var erro              by remember { mutableStateOf("") }
    android.util.Log.d("LGPD_FLUXO", "=== LegalOnboardingScreen ABRIU === userId='$userId' token='${currentToken?.take(30)}'")

    LaunchedEffect(userId) {
        android.util.Log.d("LGPD_FLUXO", "LaunchedEffect iniciou — verificando Supabase...")
        if (userId.isNotEmpty() && verificarConsentimentoExiste(userId)) {
            android.util.Log.d("LGPD_FLUXO", "Consentimento JA EXISTE — redirecionando")
            onConsentimentoConcluido()
            return@LaunchedEffect
        }
        android.util.Log.d("LGPD_FLUXO", "Consentimento NAO EXISTE — mostrando tela")
        verificando = false
    }
    val podeProsseguir = aceitoTermos && aceitoPrivacidade && !carregando

    LaunchedEffect(userId) {
        if (userId.isNotEmpty() && verificarConsentimentoExiste(userId)) {
            onConsentimentoConcluido()
            return@LaunchedEffect
        }
        verificando = false   // so concluiu check — mostrar tela
    }

    // Enquanto verifica consentimento existente, mostrar loading em vez da tela
    if (verificando) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Verde)
        }
        return
    }

    Scaffold(
        containerColor = SurfaceWarm,
        topBar = {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🔐", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text       = "Antes de continuar",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Ink,
                )
                Text(
                    text      = "Precisamos do seu consentimento para usar a plataforma",
                    fontSize  = 13.sp,
                    color     = InkMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SurfaceOff)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                HorizontalDivider(color = SurfaceOff, modifier = Modifier.padding(bottom = 12.dp))

                if (erro.isNotEmpty()) {
                    Text(
                        "❌ $erro",
                        color    = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            carregando = true
                            erro       = ""
                            // Chama a função do SupabaseClient — usa currentToken automaticamente
                            val ok = gravarConsentimento(
                                userId       = userId,
                                aceitoTermos = aceitoTermos,
                                aceitoPriv   = aceitoPrivacidade,
                                versaoTermos = VERSAO_TERMOS,
                            )
                            carregando = false
                            if (ok) {
                                onConsentimentoConcluido()
                            } else {
                                erro = "Falha ao salvar. Verifique sua conexão e tente novamente."
                            }
                        }
                    },
                    enabled  = podeProsseguir,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Verde,
                        contentColor           = Color.White,
                        disabledContainerColor = SurfaceOff,
                        disabledContentColor   = InkMuted,
                    ),
                ) {
                    if (carregando) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(22.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text       = "Aceitar e continuar",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text      = "Ao continuar, você concorda com os termos acima. Versão: $VERSAO_TERMOS",
                    fontSize  = 10.sp,
                    color     = InkMuted,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            SecaoLegal(
                emoji  = "📋",
                titulo = "O que coletamos",
                itens  = listOf(
                    "Nome, e-mail e foto de perfil para identificação",
                    "Histórico de consultas para emissão de recibos",
                    "Documentos profissionais (apenas para profissionais verificados)",
                    "Dados de pagamento processados pelo Mercado Pago — não armazenamos dados de cartão",
                ),
            )

            SecaoLegal(
                emoji  = "🎯",
                titulo = "Como usamos",
                itens  = listOf(
                    "Conectar clientes a profissionais qualificados",
                    "Processar pagamentos e emitir comprovantes",
                    "Enviar notificações sobre suas consultas",
                    "Melhorar a plataforma com dados agregados e anônimos",
                ),
            )

            SecaoLegal(
                emoji  = "⚖️",
                titulo = "Seus direitos (LGPD)",
                itens  = listOf(
                    "Acesso: você pode ver todos os dados que temos sobre você",
                    "Correção: pode corrigir dados incorretos no seu perfil",
                    "Exclusão: pode solicitar a exclusão da sua conta e dados a qualquer momento",
                    "Portabilidade: pode solicitar seus dados em formato legível",
                ),
            )

            SecaoLegal(
                emoji  = "🤝",
                titulo = "Compartilhamento",
                itens  = listOf(
                    "Não vendemos seus dados a terceiros",
                    "Dados de pagamento são processados pelo Mercado Pago (política própria)",
                    "Dados de vídeo processados pelo Stream SDK durante a chamada — não gravados",
                    "Podemos compartilhar dados mediante ordem judicial",
                ),
            )

            HorizontalDivider(color = SurfaceOff)

            Text(
                text       = "Role para baixo, leia e confirme os itens para continuar:",
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = Ink,
            )

            CheckboxConsentimentoComLink(
                checked    = aceitoTermos,
                onChange   = { aceitoTermos = it },
                prefixo    = "Li e aceito os ",
                labelLink  = "Termos de Uso",
                sufixo     = " da plataforma Brasil Tupi Conecta",
                onAbrirDoc = onVerTermos,
            )

            CheckboxConsentimentoComLink(
                checked    = aceitoPrivacidade,
                onChange   = { aceitoPrivacidade = it },
                prefixo    = "Li e aceito a ",
                labelLink  = "Política de Privacidade",
                sufixo     = " e o tratamento dos meus dados conforme a LGPD",
                onAbrirDoc = onVerPolitica,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── Checkbox com link e botão "Ler documento" ─────────────────────────────
@Composable
private fun CheckboxConsentimentoComLink(
    checked:    Boolean,
    onChange:   (Boolean) -> Unit,
    prefixo:    String,
    labelLink:  String,
    sufixo:     String,
    onAbrirDoc: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked         = checked,
            onCheckedChange = onChange,
            colors          = CheckboxDefaults.colors(
                checkedColor   = Verde,
                uncheckedColor = InkMuted,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = buildAnnotatedString {
                    append(prefixo)
                    withStyle(SpanStyle(color = Azul, fontWeight = FontWeight.Bold)) {
                        append(labelLink)
                    }
                    append(sufixo)
                },
                fontSize = 13.sp,
                color    = Ink,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick        = onAbrirDoc,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text       = "👁 Ler documento completo",
                    fontSize   = 11.sp,
                    color      = Azul,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Seção informativa ─────────────────────────────────────────────────────
@Composable
private fun SecaoLegal(
    emoji:  String,
    titulo: String,
    itens:  List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
            }
            Spacer(modifier = Modifier.height(8.dp))
            itens.forEach { item ->
                Row(
                    modifier          = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("•  ", fontSize = 13.sp, color = Verde, fontWeight = FontWeight.Bold)
                    Text(item, fontSize = 13.sp, color = InkMuted)
                }
            }
        }
    }
}