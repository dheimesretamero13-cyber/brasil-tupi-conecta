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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
internal fun IndicadorTaxaPlataforma(
    valorBruto: Double,
    isPmp: Boolean,
    verificado: Boolean,
    modifier: Modifier = Modifier,
) {
    val taxa = if (isPmp && verificado) 0.10 else 0.30
    val rotuloTaxa = if (isPmp && verificado) "PMP (10%)" else "Plataforma (30%)"
    val valorTaxa = valorBruto * taxa
    val valorLiquido = valorBruto - valorTaxa

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Valor bruto", fontSize = 12.sp, color = InkMuted)
                Text(
                    "R$ ${"%.2f".format(valorBruto)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Taxa $rotuloTaxa", fontSize = 12.sp, color = InkMuted)
                Text(
                    "− R$ ${"%.2f".format(valorTaxa)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Urgente,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = SurfaceOff)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Você receberá", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "R$ ${"%.2f".format(valorLiquido)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Verde,
                )
            }
        }
    }
}
@Composable
fun AbaUrgenteCompartilhada(
    disponivelInicial: Boolean = false,
    userId:            String  = "",
    consultas:         List<ConsultaProfissional> = emptyList(),
    kycAprovado:       Boolean? = null,
    onKyc:             (() -> Unit)? = null,
    mostrarGuiaChamada: Boolean = false,
    valorUrgenteAtual: Double? = null,
    valorMinutoExtrapoladoAtual: Double? = null,      // NOVO: valor por minuto extra
    onSalvarValorUrgente: ((Double) -> Unit)? = null,
    onSalvarValorMinutoExtrapolado: ((Double) -> Unit)? = null,   // NOVO
    mostrarRegrasFinanceiras: Boolean = false,
    enableVerticalScroll: Boolean = true,
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
    var isPmpUrgente by remember { mutableStateOf(false) }
    var verificadoUrgente by remember { mutableStateOf(false) }

    // ── verifica aceite dos termos ao iniciar ─────────────────────
    LaunchedEffect(userId) {
        if (userId.isEmpty()) { verificandoTermos = false; return@LaunchedEffect }
        try {
            withContext(Dispatchers.IO) {
                jaAceitouTermos = verificarAceiteTermosUrgencia(userId)
                val perfil = getMeuPerfilProfissional(userId)
                isPmpUrgente = perfil?.is_pmp ?: false
                verificadoUrgente = perfil?.verificado ?: false
            }
        } catch (e: Exception) {
            AppLogger.erroRede("AbaUrgenteCompartilhada", e, "userId=$userId")
            // mantém os valores padrão (false) e não bloqueia a UI
        } finally {
            verificandoTermos = false
        }
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
            .then(
                if (enableVerticalScroll) Modifier.verticalScroll(rememberScrollState())
                else Modifier
            )
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

        // ── Regras Financeiras e Prazos (visível apenas no perfil) ──────
        if (mostrarRegrasFinanceiras) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Informações de Pagamento e Taxas", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text("💰", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Taxa da plataforma", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(
                                "A plataforma retém 30% do valor da chamada urgente. " +
                                        "Profissionais com selo PMP verificado pagam apenas 10%.",
                                fontSize = 12.sp,
                                color = InkMuted,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                    HorizontalDivider(color = SurfaceOff)
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text("📅", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Atualização do valor", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(
                                "Você pode alterar o valor da chamada urgente somente 1 vez a cada 30 dias. " +
                                        "Após a mudança, o novo valor ficará bloqueado por esse período.",
                                fontSize = 12.sp,
                                color = InkMuted,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                    HorizontalDivider(color = SurfaceOff)
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text("⏲️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Limite por minuto extra", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(
                                "O valor cobrado por minuto adicional não pode ultrapassar R$ 6,00. " +
                                        "Esse limite é fixo e busca proteger os clientes.",
                                fontSize = 12.sp,
                                color = InkMuted,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                    HorizontalDivider(color = SurfaceOff)
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text("✅", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Liberação do pagamento", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(
                                "O pagamento só é confirmado e liberado para você após 15 dias corridos, " +
                                        "desde que não haja pedidos de reembolso, reclamações ou registros de negatividade direta. " +
                                        "Toda ocorrência é avaliada pela plataforma.",
                                fontSize = 12.sp,
                                color = InkMuted,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }
        }

        if (onSalvarValorUrgente != null && kycAprovado != false) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = Surface),
            ) {
                var valorEdit by remember { mutableStateOf(valorUrgenteAtual?.toString() ?: "") }
                var valorMinutoEdit by remember { mutableStateOf(valorMinutoExtrapoladoAtual?.toString() ?: "") }
                var salvandoConf by remember { mutableStateOf(false) }
                var erroConf by remember { mutableStateOf<String?>(null) }
                var podeEditar by remember { mutableStateOf(true) }
                var mensagemBloqueio by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(userId) {
                    if (userId.isNotEmpty()) {
                        try {
                            val status = withContext(Dispatchers.IO) {
                                verificarBloqueioValorMinuto(userId)
                            }
                            podeEditar = !status.bloqueado
                            mensagemBloqueio = if (status.bloqueado && status.proximaLiberacao != null) {
                                "Alteração bloqueada até ${status.proximaLiberacao}. Você só pode alterar o valor uma vez a cada 30 dias."
                            } else null
                        } catch (e: Exception) {
                            AppLogger.erroRede("AbaUrgente-verificarBloqueio", e, "userId=$userId")
                            // mantém os valores originais; a UI não quebra
                        }
                    }
                }

                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚙️ Configuração do atendimento urgente", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Duração fixa: 15 minutos. O cliente poderá solicitar tempo extra, cobrado por minuto.", fontSize = 12.sp, color = InkMuted)

                    OutlinedTextField(
                        value = valorEdit,
                        onValueChange = { valorEdit = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                        label = { Text("Valor da consulta urgente (R$)") },
                        placeholder = { Text("Ex: 49,90") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Urgente),
                        isError = erroConf != null,
                        supportingText = { if (erroConf != null) Text(erroConf!!, fontSize = 11.sp, color = Urgente) }
                    )

                    OutlinedTextField(
                        value = valorMinutoEdit,
                        onValueChange = { if (podeEditar) valorMinutoEdit = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                        label = { Text("Valor por minuto adicional (R$)") },
                        placeholder = { Text("Ex: 2,50") },
                        singleLine = true,
                        enabled = podeEditar,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Urgente,
                            disabledBorderColor = SurfaceOff,
                            disabledLabelColor = InkMuted
                        ),
                    )
                    if (mensagemBloqueio != null) {
                        Text(
                            mensagemBloqueio!!,
                            fontSize = 11.sp,
                            color = Urgente,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (erroConf != null) {
                        Text(erroConf!!, fontSize = 11.sp, color = Urgente)
                    }
                    if (valorUrgenteAtual != null && valorUrgenteAtual > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        IndicadorTaxaPlataforma(
                            valorBruto = valorUrgenteAtual!!,
                            isPmp = isPmpUrgente,
                            verificado = verificadoUrgente,
                        )
                    }

                    Button(
                        onClick = {
                            erroConf = null
                            mensagemBloqueio = null
                            val novoValor = valorEdit.replace(",", ".").toDoubleOrNull()
                            if (novoValor == null || novoValor <= 0.0) {
                                erroConf = "Digite um valor válido maior que zero."
                                return@Button
                            }
                            val novoValorMinuto = valorMinutoEdit.replace(",", ".").toDoubleOrNull()
                            if (novoValorMinuto == null || novoValorMinuto <= 0.0) {
                                erroConf = "Digite um valor válido para o minuto extra."
                                return@Button
                            }
                            if (novoValorMinuto > 6.0) {
                                erroConf = "O valor por minuto não pode ultrapassar R$ 6,00."
                                return@Button
                            }
                            salvandoConf = true
                            scope.launch {
                                val resultado = withContext(Dispatchers.IO) {
                                    atualizarValorMinutoUrgente(userId, novoValorMinuto)
                                }
                                salvandoConf = false
                                resultado.onSuccess {
                                    onSalvarValorUrgente(novoValor)
                                    onSalvarValorMinutoExtrapolado?.invoke(novoValorMinuto)
                                }.onFailure { ex ->
                                    val msg = ex.message ?: "Erro ao salvar."
                                    if (msg.contains("30 dias")) {
                                        podeEditar = false
                                        mensagemBloqueio = msg
                                    } else {
                                        erroConf = msg
                                    }
                                }
                            }
                        },
                        enabled = !salvandoConf && podeEditar,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (podeEditar) Urgente else SurfaceOff,
                            disabledContainerColor = SurfaceOff,
                            disabledContentColor = InkMuted
                        ),
                    ) {
                        if (salvandoConf) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Salvar configurações", color = if (podeEditar) Color.White else InkMuted, fontWeight = FontWeight.Bold)
                    }

                    if (valorUrgenteAtual != null && valorUrgenteAtual > 0.0) {
                        Text("Atual: R$ ${"%.2f".format(valorUrgenteAtual)} / +R$ ${"%.2f".format(valorMinutoExtrapoladoAtual ?: 0.0)}/min",
                            fontSize = 11.sp, color = Verde, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
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