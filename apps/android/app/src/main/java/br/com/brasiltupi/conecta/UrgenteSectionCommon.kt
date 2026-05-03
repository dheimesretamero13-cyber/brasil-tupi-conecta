package br.com.brasiltupi.conecta

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Seção "Urgente" compartilhada entre Dashboard e Perfil Profissional.
 *
 * @param disponivelInicial  Estado inicial do toggle de disponibilidade urgente.
 * @param userId             UUID do profissional autenticado.
 * @param consultas          Lista de consultas (já carregada pelo chamador).
 * @param kycAprovado        Se true, exibe a seção normal; se false, exibe bloqueio de verificação.
 *                           Se null, o bloqueio de KYC é omitido (não aparece).
 * @param onKyc              Callback quando o profissional clica para verificar seu perfil (KYC).
 * @param modifier           Modifier opcional para a Column raiz.
 */
@Composable
fun AbaUrgenteCompartilhada(
    disponivelInicial: Boolean = false,
    userId:            String  = "",
    consultas:         List<ConsultaProfissional> = emptyList(),
    kycAprovado:       Boolean? = null,   // null = sem guard KYC
    onKyc:             (() -> Unit)? = null,
    mostrarGuiaChamada: Boolean = false,
    modifier:          Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // ── estado do toggle ──────────────────────────────────────────
    var ativo             by remember { mutableStateOf(disponivelInicial) }
    var atualizando       by remember { mutableStateOf(false) }
    var erroToggle        by remember { mutableStateOf(false) }
    var mostrarTermos     by remember { mutableStateOf(false) }
    var jaAceitouTermos   by remember { mutableStateOf(false) }
    var verificandoTermos by remember { mutableStateOf(true) }

    // ── verifica aceite dos termos ao iniciar ─────────────────────
    LaunchedEffect(userId) {
        if (userId.isEmpty()) { verificandoTermos = false; return@LaunchedEffect }
        jaAceitouTermos  = verificarAceiteTermosUrgencia(userId)
        verificandoTermos = false
    }

    // ── métricas de histórico ─────────────────────────────────────
    val urgentesTotais       = consultas.count { it.tipo.contains("rgente", ignoreCase = true) }
    val urgentesRealizadas   = consultas.count {
        it.tipo.contains("rgente", ignoreCase = true) &&
                (it.status == "concluida" || it.status == "concluido")
    }
    val pontualidade         = if (urgentesTotais > 0) "${urgentesRealizadas * 100 / urgentesTotais}%" else "—"
    val descumprimentos      = urgentesTotais - urgentesRealizadas

    // ── modal de termos ───────────────────────────────────────────
    var aceiteTermos by remember(mostrarTermos) { mutableStateOf(false) }

    if (mostrarTermos) {
        Dialog(onDismissRequest = { mostrarTermos = false }) {
            Card(
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
                modifier = Modifier.heightIn(max = 560.dp),
            ) {
                Column(
                    modifier            = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("⚡ Termos de Prontidão Urgente", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Ao ativar a Área Urgente, você declara estar ciente de que:", fontSize = 13.sp, color = InkMuted)

                    listOf(
                        "Devo responder ao chamado em até 45 minutos após receber a notificação.",
                        "A consulta urgente tem duração máxima de 15 minutos.",
                        "Não escolho horários — fico disponível de forma contínua enquanto o switch estiver ativo.",
                        "Atrasos ou descumprimentos reduzem minha credibilidade e podem suspender meu acesso à área urgente.",
                        "Sou o único responsável pelo serviço prestado. A plataforma é apenas o canal de conexão.",
                    ).forEach { texto ->
                        Row(
                            modifier          = Modifier.fillMaxWidth().background(SurfaceWarm, RoundedCornerShape(8.dp)).padding(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("•", fontSize = 14.sp, color = Azul, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(texto, fontSize = 12.sp, color = Ink, lineHeight = 17.sp)
                        }
                    }

                    Row(
                        modifier          = Modifier.fillMaxWidth()
                            .background(if (aceiteTermos) Verde.copy(alpha = 0.08f) else SurfaceOff, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = aceiteTermos, onCheckedChange = { aceiteTermos = it }, colors = CheckboxDefaults.colors(checkedColor = Verde))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Li, compreendi e aceito todos os termos acima.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (aceiteTermos) Verde else Ink)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { mostrarTermos = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                            Text("Cancelar", color = InkMuted)
                        }
                        Button(
                            onClick = {
                                if (!aceiteTermos) return@Button
                                mostrarTermos = false
                                ativo = true; atualizando = true
                                scope.launch {
                                    if (userId.isNotEmpty()) {
                                        gravarAceiteTermosUrgencia(userId)
                                        jaAceitouTermos = true
                                        val sucesso = atualizarDisponibilidadeUrgente(userId, true)
                                        atualizando = false
                                        if (!sucesso) { ativo = false; erroToggle = true }
                                    } else { atualizando = false; ativo = false }
                                }
                            },
                            enabled  = aceiteTermos,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Verde),
                        ) {
                            Text("Ativar agora", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ── layout principal ──────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 🔒 Bloqueio KYC (se configurado)
        if (kycAprovado != null && !kycAprovado) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                border   = BorderStroke(1.dp, Color(0xFFF57F17).copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🔒", fontSize = 22.sp)
                        Column {
                            Text("Verificação obrigatória", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D4C00))
                            Text("A área urgente exige que seu perfil esteja verificado.", fontSize = 12.sp, color = Color(0xFF795548), lineHeight = 17.sp)
                        }
                    }
                    Text(
                        "Para ativar consultas urgentes você precisa enviar seus documentos e passar pela verificação de identidade (KYC). Após aprovação, o acesso é liberado automaticamente.",
                        fontSize = 12.sp, color = Color(0xFF795548), lineHeight = 17.sp,
                    )
                    Button(
                        onClick = { onKyc?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17), contentColor = Color.White),
                    ) {
                        Text("Verificar meu perfil agora", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            // Switch desabilitado
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = SurfaceOff),
            ) {
                Row(
                    modifier          = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Área Urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkMuted)
                        Text("Disponível após verificação.", fontSize = 13.sp, color = InkMuted)
                    }
                    Switch(checked = false, onCheckedChange = {}, enabled = false, colors = SwitchDefaults.colors(disabledUncheckedThumbColor = InkMuted, disabledUncheckedTrackColor = SurfaceOff))
                }
            }
            return@Column   // interrompe o restante da seção normal
        }

        // ── Toggle normal ─────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Área Urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Apareça para clientes que precisam de atendimento imediato.", fontSize = 13.sp, color = InkMuted, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (verificandoTermos || atualizando) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Verde, strokeWidth = 2.dp)
                    } else {
                        Switch(
                            checked = ativo,
                            onCheckedChange = { novoValor ->
                                erroToggle = false
                                if (novoValor && !ativo) {
                                    if (jaAceitouTermos) {
                                        val anterior = ativo; ativo = true; atualizando = true
                                        scope.launch {
                                            val sucesso = if (userId.isNotEmpty()) atualizarDisponibilidadeUrgente(userId, true) else false
                                            atualizando = false
                                            if (!sucesso) { ativo = anterior; erroToggle = true }
                                        }
                                    } else {
                                        mostrarTermos = true
                                    }
                                } else if (!novoValor) {
                                    val anterior = ativo; ativo = false; atualizando = true
                                    scope.launch {
                                        val sucesso = if (userId.isNotEmpty()) atualizarDisponibilidadeUrgente(userId, false) else false
                                        atualizando = false
                                        if (!sucesso) { ativo = anterior; erroToggle = true }
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Verde),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier          = Modifier.fillMaxWidth().background(if (ativo) VerdeClaro else UrgenteClaro, RoundedCornerShape(8.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (ativo) "🟢" else "🔴", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (ativo) "Você está disponível para consultas urgentes" else "Você está indisponível para consultas urgentes",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (ativo) Verde else Urgente,
                    )
                }

                if (jaAceitouTermos) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth().background(Verde.copy(alpha = 0.07f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("✅", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Termos de prontidão aceitos neste dispositivo.", fontSize = 11.sp, color = Verde)
                    }
                }
                if (erroToggle) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Falha na conexão. Estado revertido.", fontSize = 12.sp, color = Urgente)
                    }
                }
            }
        }

        // ── Regras do Acordo ──────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Regras do Acordo de Prontidão", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                listOf(
                    Triple("⏱", "45 minutos",     "Tempo máximo para iniciar o atendimento."),
                    Triple("📋", "15 minutos",     "Duração máxima da consulta."),
                    Triple("⚠",  "Descumprimento", "Atrasos resultam em perda de credibilidade."),
                    Triple("🚫", "Reincidência",   "Suspensão do acesso à área urgente."),
                ).forEach { (icon, titulo, desc) ->
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text(icon, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(desc, fontSize = 12.sp, color = InkMuted, lineHeight = 17.sp)
                        }
                    }
                    HorizontalDivider(color = SurfaceOff)
                }
            }
        }

        // ── Histórico urgente ────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Seu histórico urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(modifier = Modifier.height(14.dp))
                if (urgentesTotais == 0) {
                    Text("Nenhuma urgência registrada ainda.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple("$urgentesRealizadas", "Urgentes\nrealizadas", Verde),
                            Triple(pontualidade,           "Taxa de\npontualidade", Azul),
                            Triple("$descumprimentos",    "Descum-\nprimentos",    if (descumprimentos == 0) Dourado else Urgente),
                        ).forEach { (num, label, cor) ->
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(num, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cor)
                                Text(label, fontSize = 10.sp, color = InkMuted, lineHeight = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth().background(if (descumprimentos == 0) VerdeClaro else UrgenteClaro, RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (descumprimentos == 0) "✓" else "⚠️", fontSize = 14.sp, color = if (descumprimentos == 0) Verde else Urgente, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (descumprimentos == 0) "Histórico limpo — acesso integral à área urgente." else "$descumprimentos descumprimento(s) registrado(s).", fontSize = 12.sp, color = if (descumprimentos == 0) Verde else Urgente)
                    }
                }
            }
        }

        // ── Guia da Chamada Urgente (condicional) ─────────────────────
        if (mostrarGuiaChamada) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("📘 Guia da Chamada Urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "• Você receberá uma notificação push quando um cliente solicitar uma urgência.\n" +
                                "• Tem até 45 minutos para aceitar ou recusar.\n" +
                                "• A consulta tem duração máxima de 15 minutos.\n" +
                                "• Ao aceitar, o chat com o cliente será aberto imediatamente.\n" +
                                "• Se não responder a tempo, a urgência expira e impacta sua credibilidade.",
                        fontSize = 13.sp,
                        color = InkMuted,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}