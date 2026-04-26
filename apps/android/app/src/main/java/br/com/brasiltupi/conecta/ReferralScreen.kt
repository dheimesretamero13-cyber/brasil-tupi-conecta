package br.com.brasiltupi.conecta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.brasiltupi.conecta.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════
// ReferralScreen.kt  · Fase 4.2
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralScreen(onVoltar: () -> Unit) {
    val context = LocalContext.current
    val vm: ReferralViewModel = viewModel(
        factory = ReferralViewModelFactory(ReferralRepositoryFactory.create())
    )

    val uiState      by vm.uiState.collectAsState()
    val aplicarState by vm.aplicarState.collectAsState()

    var codigoInput  by remember { mutableStateOf("") }
    var mostrarAplicar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Indique e Ganhe", fontWeight = FontWeight.Bold) },
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
        containerColor = Color(0xFFF8F7F4),
    ) { padding ->

        if (uiState.carregando) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Verde)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding),
            contentPadding  = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Card hero — seu código ────────────────────────────────────
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Azul),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(
                        modifier            = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🎁", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Indique amigos e ganhe créditos",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                            textAlign  = TextAlign.Center,
                        )
                        Text(
                            "Você ganha R$20 e seu amigo ganha R$10\napós a primeira consulta concluída.",
                            fontSize  = 13.sp,
                            color     = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(top = 6.dp),
                        )

                        Spacer(Modifier.height(20.dp))

                        // Caixa do código
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(vertical = 16.dp, horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text       = uiState.codigo.ifEmpty { "Gerando..." },
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Black,
                                color      = Color.White,
                                letterSpacing = 3.sp,
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Botões copiar e compartilhar
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick  = { copiarCodigo(context, uiState.codigo) },
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color.White.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("📋 Copiar", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { compartilharCodigo(context, uiState.codigo) },
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor   = Azul,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("↗ Compartilhar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Saldo de créditos ─────────────────────────────────────────
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier          = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                "Seus créditos disponíveis",
                                fontSize = 13.sp,
                                color    = InkMuted,
                            )
                            Text(
                                "R$ ${"%.2f".format(uiState.totalCreditos)}",
                                fontSize   = 28.sp,
                                fontWeight = FontWeight.Black,
                                color      = Verde,
                            )
                        }
                        Text("💰", fontSize = 36.sp)
                    }

                    if (uiState.creditos.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Detalhes",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = Ink,
                            )
                            Spacer(Modifier.height(8.dp))
                            uiState.creditos.forEach { credito ->
                                ItemCredito(credito = credito)
                            }
                        }
                    }
                }
            }

            // ── Usar código de indicação ──────────────────────────────────
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Tem um código de indicação?",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Ink,
                        )
                        Text(
                            "Insira o código de quem te indicou e ganhe R$10 na primeira consulta.",
                            fontSize = 13.sp,
                            color    = InkMuted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )

                        if (!mostrarAplicar) {
                            OutlinedButton(
                                onClick = { mostrarAplicar = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(8.dp),
                            ) {
                                Text("Inserir código", color = Verde)
                            }
                        } else {
                            OutlinedTextField(
                                value         = codigoInput,
                                onValueChange = {
                                    codigoInput = it.uppercase().take(13)
                                    vm.resetarAplicar()
                                },
                                modifier      = Modifier.fillMaxWidth(),
                                placeholder   = { Text("Ex: BRASIL-AB12CD") },
                                shape         = RoundedCornerShape(8.dp),
                                singleLine    = true,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Verde,
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))

                            // Feedback do estado
                            when (val estado = aplicarState) {
                                is AplicarCodigoState.Erro -> {
                                    Text(
                                        "⚠️ ${estado.mensagem}",
                                        fontSize = 12.sp,
                                        color    = Urgente,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                                is AplicarCodigoState.Sucesso -> {
                                    Text(
                                        "✅ Código aplicado! Crédito liberado após sua primeira consulta.",
                                        fontSize = 12.sp,
                                        color    = Verde,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                                else -> Unit
                            }

                            Button(
                                onClick  = { vm.aplicarCodigo(codigoInput) },
                                enabled  = codigoInput.length >= 7
                                        && aplicarState !is AplicarCodigoState.Carregando
                                        && aplicarState !is AplicarCodigoState.Sucesso,
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(containerColor = Verde),
                                shape    = RoundedCornerShape(8.dp),
                            ) {
                                if (aplicarState is AplicarCodigoState.Carregando) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(18.dp),
                                        color       = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text("Aplicar código", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // ── Histórico de indicações ───────────────────────────────────
            if (uiState.indicacoes.isNotEmpty()) {
                item {
                    Text(
                        "Suas indicações (${uiState.indicacoes.size})",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Ink,
                    )
                }
                items(uiState.indicacoes) { referral ->
                    CardIndicacao(referral = referral)
                }
            }

            // ── Como funciona ─────────────────────────────────────────────
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Como funciona",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Verde,
                            modifier   = Modifier.padding(bottom = 12.dp),
                        )
                        listOf(
                            "1️⃣" to "Compartilhe seu código com amigos",
                            "2️⃣" to "Seu amigo se cadastra e usa o código",
                            "3️⃣" to "Após a primeira consulta concluída, ambos recebem créditos",
                            "4️⃣" to "Créditos válidos por 90 dias — use na próxima consulta",
                        ).forEach { (icone, texto) ->
                            Row(
                                modifier              = Modifier.padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(icone, fontSize = 16.sp)
                                Text(texto, fontSize = 13.sp, color = Color(0xFF374151))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLES AUXILIARES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ItemCredito(credito: CreditoUsuario) {
    val label = when (credito.reason) {
        "referral_referrer" -> "Você indicou um amigo"
        "referral_referee"  -> "Você foi indicado"
        else                -> credito.reason
    }
    val validade = credito.expiresAt
        ?.take(10)?.split("-")
        ?.let { p -> if (p.size == 3) "Válido até ${p[2]}/${p[1]}/${p[0]}" else null }
        ?: "Sem expiração"

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
            Text(validade, fontSize = 11.sp, color = InkMuted)
        }
        Text(
            "+ R$ ${"%.2f".format(credito.amount)}",
            fontSize   = 14.sp,
            fontWeight = FontWeight.Bold,
            color      = Verde,
        )
    }
}

@Composable
private fun CardIndicacao(referral: ReferralInfo) {
    val (icone, cor, label) = when (referral.status) {
        "creditado" -> Triple("✅", Verde,              "Crédito liberado")
        "pendente"  -> Triple("⏳", Color(0xFFF57F17),  "Aguardando primeira consulta")
        "expirado"  -> Triple("❌", InkMuted,           "Expirado")
        else        -> Triple("•",  InkMuted,           referral.status)
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(icone, fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Indicação",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Ink,
                )
                Text(label, fontSize = 11.sp, color = cor)
            }
            val data = referral.criadoEm.take(10).split("-")
                .let { p -> if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else "" }
            Text(data, fontSize = 11.sp, color = InkMuted)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

private fun copiarCodigo(context: Context, codigo: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Código de indicação", codigo))
}

private fun compartilharCodigo(context: Context, codigo: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT,
            "Use meu código $codigo no Brasil Tupi Conecta e ganhe R\$10 na primeira consulta! " +
                    "Baixe o app: https://brasiltupi.com.br"
        )
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar código"))
}