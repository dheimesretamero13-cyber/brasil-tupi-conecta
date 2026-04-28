package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// KycUploadScreen.kt
// Upload de documentos KYC — usa uploadKycDocumento() e gravarKycDocumento()
// do SupabaseClient.kt (mesmo padrão do projeto: currentToken no Auth).
// ═══════════════════════════════════════════════════════════════════════════

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch
import java.io.InputStream

private const val MAX_BYTES = 10 * 1024 * 1024L

private val TIPOS_DOCUMENTO = listOf(
    "CRM"          to "Conselho Regional de Medicina",
    "CRP"          to "Conselho Regional de Psicologia",
    "OAB"          to "Ordem dos Advogados do Brasil",
    "CNH"          to "Carteira Nacional de Habilitação",
    "Certificação" to "Certificado profissional",
    "Outro"        to "Outro documento",
)

@Composable
fun KycUploadScreen(
    userId: String,
    onUploadConcluido: () -> Unit,
    onVoltar: () -> Unit,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var tipoSelecionado  by remember { mutableStateOf<String?>(null) }
    var uriSelecionada   by remember { mutableStateOf<Uri?>(null) }
    var nomeArquivo      by remember { mutableStateOf("") }
    var tamanhoArquivo   by remember { mutableStateOf(0L) }
    var erroTamanho      by remember { mutableStateOf(false) }
    var erroTipo         by remember { mutableStateOf(false) }
    var carregando       by remember { mutableStateOf(false) }
    var erroUpload       by remember { mutableStateOf("") }
    var expandirDropdown by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        erroTamanho = false
        erroUpload  = ""
        val mimeType = context.contentResolver.getType(uri) ?: ""
        if (mimeType !in listOf("application/pdf", "image/jpeg", "image/png")) {
            erroTipo = true; return@rememberLauncherForActivityResult
        }
        erroTipo = false
        val tamanho = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        if (tamanho > MAX_BYTES) { erroTamanho = true; return@rememberLauncherForActivityResult }
        uriSelecionada = uri
        tamanhoArquivo = tamanho
        nomeArquivo    = uri.lastPathSegment?.substringAfterLast('/') ?: "documento"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().background(Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("←", fontSize = 20.sp,
                modifier = Modifier.clickable { onVoltar() }.padding(end = 12.dp), color = Ink)
            Text("Verificação de Identidade", fontSize = 16.sp,
                fontWeight = FontWeight.Bold, color = Ink)
        }
        HorizontalDivider(color = SurfaceOff)

        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔐 Por que precisamos verificar você?",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("A verificação garante que nossos clientes recebam atendimento de profissionais habilitados. Seus documentos são armazenados com criptografia.",
                        fontSize = 13.sp, color = Color(0xFF388E3C))
                }
            }

            Text("1. Tipo do documento", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)

            Box {
                OutlinedButton(onClick = { expandirDropdown = true },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text(tipoSelecionado ?: "Selecionar tipo...",
                        color = if (tipoSelecionado != null) Ink else InkMuted,
                        modifier = Modifier.weight(1f))
                    Text("▾", color = InkMuted)
                }
                DropdownMenu(expanded = expandirDropdown,
                    onDismissRequest = { expandirDropdown = false }) {
                    TIPOS_DOCUMENTO.forEach { (tipo, descricao) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(tipo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(descricao, fontSize = 12.sp, color = InkMuted)
                                }
                            },
                            onClick = { tipoSelecionado = tipo; expandirDropdown = false },
                        )
                    }
                }
            }

            Text("2. Arquivo do documento", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)

            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp)
                    .border(1.dp, if (uriSelecionada != null) Verde else SurfaceOff, RoundedCornerShape(12.dp))
                    .background(Surface, RoundedCornerShape(12.dp))
                    .clickable { pickerLauncher.launch("*/*") },
                contentAlignment = Alignment.Center,
            ) {
                if (uriSelecionada != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", fontSize = 28.sp)
                        Text(nomeArquivo, fontSize = 12.sp, color = Verde, fontWeight = FontWeight.SemiBold)
                        Text("${tamanhoArquivo / 1024} KB", fontSize = 11.sp, color = InkMuted)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📎", fontSize = 28.sp)
                        Text("Toque para selecionar", fontSize = 13.sp, color = InkMuted)
                        Text("PDF, JPEG ou PNG · Máx. 10 MB", fontSize = 11.sp, color = InkMuted)
                    }
                }
            }

            if (erroTipo)    Text("⚠️ Formato inválido. Use PDF, JPEG ou PNG.", color = Color.Red, fontSize = 12.sp)
            if (erroTamanho) Text("⚠️ Arquivo muito grande. Máximo: 10 MB.", color = Color.Red, fontSize = 12.sp)
            if (erroUpload.isNotEmpty()) Text("❌ $erroUpload", color = Color.Red, fontSize = 12.sp)

            Button(
                onClick = {
                    scope.launch {
                        carregando = true
                        erroUpload = ""
                        val uri  = uriSelecionada ?: return@launch
                        val tipo = tipoSelecionado ?: return@launch
                        val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                        val bytes = context.contentResolver.openInputStream(uri)
                            ?.use(InputStream::readBytes)
                        if (bytes == null) {
                            erroUpload = "Não foi possível ler o arquivo."
                            carregando = false
                            return@launch
                        }
                        // uploadKycDocumento usa currentToken — mesmo padrão do projeto
                        val caminho = uploadKycDocumento(
                            userId   = userId,
                            tipo     = tipo,
                            bytes    = bytes,
                            mimeType = mimeType,
                        )
                        if (caminho == null) {
                            erroUpload = "Falha no upload. Tente novamente."
                            carregando = false
                            return@launch
                        }
                        val ok = gravarKycDocumento(
                            userId      = userId,
                            tipo        = tipo,
                            storagePath = caminho,
                            mimeType    = mimeType,
                        )
                        carregando = false
                        if (ok) onUploadConcluido()
                        else erroUpload = "Documento enviado, mas falhou ao registrar. Tente novamente."
                    }
                },
                enabled  = tipoSelecionado != null && uriSelecionada != null && !carregando,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Verde,
                    contentColor           = Color.White,
                    disabledContainerColor = SurfaceOff,
                    disabledContentColor   = InkMuted,
                ),
            ) {
                if (carregando) CircularProgressIndicator(modifier = Modifier.size(20.dp),
                    color = Color.White, strokeWidth = 2.dp)
                else Text("Enviar documento", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Text(
                "🔒 Seus dados são criptografados e não serão compartilhados com terceiros.",
                fontSize = 11.sp, color = InkMuted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}