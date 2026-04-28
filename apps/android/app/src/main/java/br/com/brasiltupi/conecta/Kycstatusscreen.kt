package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// KycStatusScreen.kt
// Exibe status KYC — usa buscarKycDocumentos() do SupabaseClient.kt.
// KycDocumento data class também está definido em SupabaseClient.kt.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*

@Composable
fun KycStatusScreen(
    userId: String,
    onEnviarDocumento: () -> Unit,
    onVoltar: () -> Unit,
) {
    var documentos by remember { mutableStateOf<List<KycDocumento>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var erroRede   by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        if (userId.isEmpty()) { carregando = false; return@LaunchedEffect }
        // buscarKycDocumentos usa currentToken — mesmo padrão do projeto
        documentos = buscarKycDocumentos(userId)
        carregando = false
    }

    val statusGeral = when {
        documentos.any { it.status == "approved" } -> "approved"
        documentos.any { it.status == "pending" }  -> "pending"
        documentos.any { it.status == "rejected" } -> "rejected"
        else                                        -> "not_submitted"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().background(Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("←", fontSize = 20.sp,
                modifier = Modifier.clickable { onVoltar() }.padding(end = 12.dp), color = Ink)
            Text("Status da Verificação", fontSize = 16.sp,
                fontWeight = FontWeight.Bold, color = Ink)
        }
        HorizontalDivider(color = SurfaceOff)

        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            if (carregando) {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Verde)
                }
                return@Column
            }

            val (cor, icone, titulo, descricao) = when (statusGeral) {
                "approved" -> StatusKycUi(Color(0xFFE8F5E9), "✅", "Verificado com sucesso",
                    "Seu perfil exibe o badge de profissional verificado.")
                "pending"  -> StatusKycUi(Color(0xFFFFF8E1), "⏳", "Em análise",
                    "Seu documento foi recebido e está sendo analisado. Isso pode levar até 2 dias úteis.")
                "rejected" -> StatusKycUi(Color(0xFFFFEBEE), "❌", "Documento rejeitado",
                    documentos.firstOrNull { it.status == "rejected" }?.motivo_rejeicao
                        ?: "O documento enviado não atende aos requisitos. Por favor, envie novamente.")
                else       -> StatusKycUi(Color(0xFFF5F5F5), "📋", "Nenhum documento enviado",
                    "Envie seus documentos profissionais para obter o badge de verificação.")
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cor)) {
                Column(modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(icone, fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = Ink, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(descricao, fontSize = 13.sp, color = InkMuted, textAlign = TextAlign.Center)
                }
            }

            if (documentos.isNotEmpty()) {
                Text("Documentos enviados", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
                documentos.forEach { doc ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.tipo_documento, fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, color = Ink)
                                Text(doc.criado_em.take(10), fontSize = 11.sp, color = InkMuted)
                            }
                            BadgeStatusKyc(status = doc.status)
                        }
                    }
                }
            }

            if (statusGeral == "not_submitted" || statusGeral == "rejected") {
                Button(
                    onClick  = onEnviarDocumento,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                ) {
                    Text(
                        if (statusGeral == "rejected") "Reenviar documento" else "Enviar documento",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeStatusKyc(status: String) {
    val (bg, fg, label) = when (status) {
        "approved" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "✅ Aprovado")
        "pending"  -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "⏳ Em análise")
        "rejected" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "❌ Rejeitado")
        else       -> Triple(Color(0xFFF5F5F5), Color(0xFF757575), "Pendente")
    }
    Box(modifier = Modifier.background(bg, RoundedCornerShape(20.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

private data class StatusKycUi(
    val cor: Color, val icone: String, val titulo: String, val descricao: String,
)