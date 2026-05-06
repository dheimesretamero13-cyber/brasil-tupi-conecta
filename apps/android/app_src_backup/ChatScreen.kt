package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    outroId: String,
    outroNome: String,
    onVoltar: () -> Unit,
) {
    val meuId = currentUserId ?: ""
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val repo = remember(meuId, outroId) {
        if (meuId.isNotEmpty()) ChatGeralRepository(meuId, outroId) else null
    }

    val mensagensState by repo?.mensagens?.collectAsState()
        ?: remember { mutableStateOf(emptyList<MensagemGeral>()) }
    val statusConexao by repo?.statusConexao?.collectAsState()
        ?: remember { mutableStateOf(false) }

    var texto by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var erroEnvio by remember { mutableStateOf(false) }

    LaunchedEffect(repo) {
        repo?.carregarHistorico()
        repo?.iniciarRealtime()
    }

    LaunchedEffect(mensagensState.size) {
        if (mensagensState.isNotEmpty()) {
            listState.animateScrollToItem(mensagensState.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose { repo?.parar() }
    }

    fun enviar() {
        val trimmed = texto.trim()
        if (trimmed.isEmpty() || enviando || meuId.isEmpty() || repo == null) return

        texto = ""
        enviando = true
        erroEnvio = false

        val tempId = "temp_${System.currentTimeMillis()}"
        val msgTemp = MensagemGeral(
            id             = tempId,
            remetenteId    = meuId,
            destinatarioId = outroId,
            texto          = trimmed,
            createdAt      = "",
        )

        repo.adicionarTemp(msgTemp)

        scope.launch {
            val ok = repo.enviarMensagem(trimmed)
            enviando = false
            if (!ok) {
                repo.removerTemp(tempId)
                erroEnvio = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(SurfaceWarm)) {

        // ── Topbar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Azul)
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onVoltar) {
                Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    outroNome.split(" ").map { it[0] }.joinToString("").take(2).uppercase(),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(outroNome, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (statusConexao) "● Online" else "● Reconectando...",
                    fontSize = 11.sp,
                    color = if (statusConexao) Verde else Color(0xFFF57F17)
                )
            }
        }

        // ── Lista de mensagens ───────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (mensagensState.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💬", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Nenhuma mensagem ainda.", fontSize = 14.sp, color = InkMuted)
                            Text("Diga olá!", fontSize = 13.sp, color = InkMuted)
                        }
                    }
                }
            }

            items(mensagensState, key = { it.id }) { msg ->
                val isMinha = msg.remetenteId == meuId
                val isTemp = msg.id.startsWith("temp_")
                BubbleMensagem(
                    texto   = msg.texto,
                    isMinha = isMinha,
                    hora    = formatarHoraChat(msg.createdAt),
                    opaco   = isTemp,
                )
            }
        }

        // ── Banner erro envio ────────────────────────────────────────────
        if (erroEnvio) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFDE8E8))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("⚠️ Falha ao enviar a mensagem.", fontSize = 12.sp, color = Urgente)
                TextButton(onClick = { erroEnvio = false }) {
                    Text("Ok", color = Urgente, fontSize = 12.sp)
                }
            }
        }

        // ── Campo de texto ───────────────────────────────────────────────
        HorizontalDivider(color = SurfaceOff)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = texto,
                onValueChange = { texto = it },
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Digite uma mensagem...", color = InkMuted, fontSize = 14.sp) },
                shape         = RoundedCornerShape(24.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Verde,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { enviar() }),
                maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick  = { enviar() },
                enabled  = texto.trim().isNotEmpty() && !enviando,
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (texto.trim().isNotEmpty() && !enviando) Verde else SurfaceOff,
                        RoundedCornerShape(50)
                    )
            ) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Verde, strokeWidth = 2.dp)
                } else {
                    Text("➤", fontSize = 18.sp, color = if (texto.trim().isNotEmpty()) Color.White else InkMuted)
                }
            }
        }
    }
}

@Composable
fun BubbleMensagem(texto: String, isMinha: Boolean, hora: String, opaco: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMinha) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isMinha) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = if (isMinha) Verde.copy(alpha = if (opaco) 0.5f else 1f)
                        else Surface,
                        shape = RoundedCornerShape(
                            topStart    = 16.dp,
                            topEnd      = 16.dp,
                            bottomStart = if (isMinha) 16.dp else 4.dp,
                            bottomEnd   = if (isMinha) 4.dp  else 16.dp
                        )
                    )
                    .then(
                        if (!isMinha) Modifier.background(
                            Surface,
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
                        ) else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    text       = texto,
                    fontSize   = 14.sp,
                    color      = if (isMinha) Color.White else Ink,
                    lineHeight = 20.sp
                )
            }
            if (hora.isNotEmpty()) {
                Text(hora, fontSize = 10.sp, color = InkMuted, modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp))
            }
        }
    }
}

private fun formatarHoraChat(createdAt: String): String {
    if (createdAt.isEmpty()) return ""
    return try {
        val semFuso = createdAt.substringBefore("+").substringBefore("Z").take(19)
        val timePart = semFuso.substringAfter("T", "")
        val partes = timePart.split(":")
        "${partes.getOrNull(0) ?: ""}:${partes.getOrNull(1) ?: ""}"
    } catch (_: Exception) { "" }
}