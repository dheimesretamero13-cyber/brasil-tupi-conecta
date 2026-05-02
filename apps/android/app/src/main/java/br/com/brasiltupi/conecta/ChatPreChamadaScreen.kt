package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// ChatPreChamadaScreen.kt  · Fase 3.5
//
// Chat volátil vinculado a um agendamento (session_id).
// Diferente do ChatScreen.kt existente (tabela mensagens, chat geral),
// este usa a tabela chats com Realtime nativo e auto-destruição via trigger.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPreChamadaScreen(
    sessionId:  String,   // agendamentos.id
    outroNome:  String,
    onVoltar:   () -> Unit,
) {
    val meuId     = currentUserId ?: ""
    val repo      = remember { ChatRepositoryFactory.create() }
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val mensagens     by repo.mensagens.collectAsState()
    val conectado     by repo.statusConexao.collectAsState()

    var texto         by remember { mutableStateOf("") }
    var enviando      by remember { mutableStateOf(false) }
    var erroEnvio     by remember { mutableStateOf(false) }

    // ── Inicializar: histórico + Realtime ─────────────────────────────────
    LaunchedEffect(sessionId) {
        repo.buscarHistorico(sessionId)
        repo.iniciarRealtime(sessionId)
    }

    // ── Auto-scroll ao receber mensagem ───────────────────────────────────
    LaunchedEffect(mensagens.size) {
        if (mensagens.isNotEmpty()) {
            listState.animateScrollToItem(mensagens.lastIndex)
        }
    }

    // ── Cleanup ao sair ───────────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose { repo.parar() }
    }

    fun enviar() {
        val textoTrimmed = texto.trim()
        if (textoTrimmed.isEmpty() || enviando || meuId.isEmpty()) return
        val textoEnviado = textoTrimmed
        texto    = ""
        enviando = true
        erroEnvio = false

        // Optimistic update – adiciona à lista imediatamente
        val tempId = "temp_${System.currentTimeMillis()}"
        val msgTemp = MensagemChat(
            id          = tempId,
            sessionId   = sessionId,
            remetenteId = meuId,
            texto       = textoEnviado,
        )
        repo.adicionarTemp(msgTemp)

        scope.launch {
            val ok = repo.enviar(sessionId, textoEnviado)
            enviando  = false
            if (!ok) {
                // Rollback da mensagem temporária
                repo.removerTemp(tempId)
                erroEnvio = true
            }
            // Se o envio teve sucesso, o Realtime confirmará com o id real
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            outroNome,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (conectado) "● Online" else "● Reconectando...",
                            fontSize = 11.sp,
                            color    = if (conectado) Verde else Color(0xFFF57F17),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Azul),
            )
        },
        containerColor = Color(0xFFF8F7F4),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {

            // Banner "chat temporário"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3CD))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "💬 Este chat é temporário e será apagado ao encerrar o atendimento.",
                    fontSize = 11.sp,
                    color    = Color(0xFF856404),
                )
            }

            // ── Lista de mensagens ────────────────────────────────────────
            LazyColumn(
                state           = listState,
                modifier        = Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding  = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (mensagens.isEmpty()) {
                    item {
                        Box(
                            modifier         = Modifier.fillParentMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💬", fontSize = 36.sp)
                                Spacer(Modifier.height(10.dp))
                                Text("Nenhuma mensagem ainda.", fontSize = 14.sp, color = InkMuted)
                                Text("Diga olá antes da consulta!", fontSize = 13.sp, color = InkMuted)
                            }
                        }
                    }
                }
                items(mensagens, key = { it.id }) { msg ->
                    val isMinha = msg.remetenteId == meuId
                    BubbleMensagem(
                        texto   = msg.texto,
                        isMinha = isMinha,
                        hora    = formatarHoraChatPre(msg.criadoEm),
                    )
                }
            }

            // ── Banner erro ───────────────────────────────────────────────
            if (erroEnvio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFDE8E8))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("⚠️ Falha ao enviar.", fontSize = 12.sp, color = Urgente)
                    TextButton(onClick = { erroEnvio = false }) {
                        Text("Ok", color = Urgente, fontSize = 12.sp)
                    }
                }
            }

            // ── Campo de texto ────────────────────────────────────────────
            HorizontalDivider(color = SurfaceOff)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value         = texto,
                    onValueChange = { texto = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Mensagem pré-consulta...", color = InkMuted, fontSize = 14.sp) },
                    shape         = RoundedCornerShape(24.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Verde,
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { enviar() }),
                    maxLines        = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = { enviar() },
                    enabled  = texto.trim().isNotEmpty() && !enviando,
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (texto.trim().isNotEmpty() && !enviando) Verde else SurfaceOff,
                            RoundedCornerShape(50),
                        ),
                ) {
                    if (enviando) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            color       = Verde,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            "➤",
                            fontSize = 18.sp,
                            color    = if (texto.trim().isNotEmpty()) Color.White else InkMuted,
                        )
                    }
                }
            }
        }
    }
}

private fun formatarHoraChatPre(criadoEm: String): String {
    if (criadoEm.isEmpty()) return ""
    return try {
        val semFuso  = criadoEm.substringBefore("+").substringBefore("Z").take(19)
        val timePart = semFuso.substringAfter("T", "")
        val partes   = timePart.split(":")
        "${partes.getOrNull(0) ?: ""}:${partes.getOrNull(1) ?: ""}"
    } catch (_: Exception) { "" }
}