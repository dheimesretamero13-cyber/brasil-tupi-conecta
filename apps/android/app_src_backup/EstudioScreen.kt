package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

// ── REGRA DE OURO: retenção padrão 30% / PMP 10% ─────────────────────────
// Exibir esse aviso em TODOS os pontos de venda da plataforma.
const val TAXA_RETENCAO_PADRAO = 0.30
const val TAXA_RETENCAO_PMP    = 0.10

enum class TipoEstudio(
    val id: String,
    val label: String,
    val icon: String,
    val labelFiltro: String,
    val descricaoTipo: String,
) {
    AULA(
        id            = "aula",
        label         = "Aula",
        icon          = "🎓",
        labelFiltro   = "🎓 Aulas",
        descricaoTipo = "Aula individual ao vivo ou gravada",
    ),
    CURSO(
        id            = "curso",
        label         = "Curso",
        icon          = "📚",
        labelFiltro   = "📚 Cursos",
        descricaoTipo = "Curso completo com módulos e aulas",
    ),
    LIVRO(
        id            = "livro",
        label         = "Livro",
        icon          = "📖",
        labelFiltro   = "📖 Livros",
        descricaoTipo = "E-book ou livro digital em PDF",
    ),
    SAAS_DIGITAL(
        id            = "saas_digital",
        label         = "SaaS / Digital",
        icon          = "💾",
        labelFiltro   = "💾 SaaS/Digital",
        descricaoTipo = "Software, SaaS, template ou produto digital",
    ),
    CONSULTA_AVULSA(
        id            = "consulta_avulsa",
        label         = "Consulta avulsa",
        icon          = "💬",
        labelFiltro   = "💬 Consultas",
        descricaoTipo = "Sessão de consultoria individual",
    );

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
            ?: if (id == "pdf") LIVRO else null

        val filtros: List<Pair<String, String>> =
            listOf("todos" to "Todos") + entries.map { it.id to it.labelFiltro }
    }
}

// ── TELA BUSCA GLOBAL ─────────────────────────────────
@Composable
fun EstudioBuscaScreen(onVoltar: () -> Unit) {
    var itens by remember { mutableStateOf<List<ItemEstudio>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busca by remember { mutableStateOf("") }
    var filtroTipo by remember { mutableStateOf("todos") }
    var itemSelecionado by remember { mutableStateOf<ItemEstudio?>(null) }
    var itemParaPagar by remember { mutableStateOf<ItemEstudio?>(null) }

    LaunchedEffect(filtroTipo) {
        loading = true
        try {
            itens = getProfissionaisEstudioAndroid(filtroTipo)
        } catch (e: Exception) {
        } finally {
            loading = false
        }
    }

    when {
        itemParaPagar != null -> {
            PagamentoScreen(
                urgenciaId   = itemParaPagar?.id ?: "",
                onConfirmado = { itemParaPagar = null },
                onVoltar     = { itemParaPagar = null },
            )
        }
        itemSelecionado != null -> {
            EstudioDetalheScreen(
                item     = itemSelecionado!!,
                onVoltar = { itemSelecionado = null },
                onPagar  = { itemSelecionado = null; itemParaPagar = it },
            )
        }
        else -> {
            val itensFiltrados by remember {
                derivedStateOf {
                    itens.filter { item ->
                        busca.isEmpty() ||
                                item.titulo.contains(busca, ignoreCase = true) ||
                                item.descricao.contains(busca, ignoreCase = true) ||
                                item.autorNome.contains(busca, ignoreCase = true)
                    }
                }
            }
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F7F4))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF0C2D6B), Color(0xFF1A5C3A))))
                        .padding(horizontal = 20.dp)
                        .padding(top = 52.dp, bottom = 24.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onVoltar) {
                                Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                        }
                        Text(
                            "🎨 Estúdio",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Cursos, aulas e produtos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Criados por profissionais verificados", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                        OutlinedTextField(
                            value         = busca,
                            onValueChange = { busca = it },
                            modifier      = Modifier.fillMaxWidth(),
                            placeholder   = { Text("Buscar cursos, aulas, produtos...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                            shape         = RoundedCornerShape(10.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Color(0xFFC49A2A),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedTextColor     = Color.White,
                                unfocusedTextColor   = Color.White,
                                cursorColor          = Color(0xFFC49A2A),
                            ),
                            singleLine = true,
                        )
                    }
                }

                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(TipoEstudio.filtros) { (tipo, label) ->
                        FilterChip(
                            selected = filtroTipo == tipo,
                            onClick  = { filtroTipo = tipo },
                            label    = { Text(label, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Verde,
                                selectedLabelColor     = Color.White,
                            ),
                        )
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFC49A2A))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Carregando Estúdios...", fontSize = 13.sp, color = Color(0xFF6B7280))
                        }
                    }
                } else if (itensFiltrados.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎨", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Nenhum item encontrado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            Text("Tente outros termos ou remova os filtros.", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding      = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(itensFiltrados) { item ->
                            CardEstudio(item = item, onClick = { itemSelecionado = item })
                        }
                    }
                }
            }
        }
    }
}

// ── CARD ITEM ─────────────────────────────────────────
@Composable
fun CardEstudio(item: ItemEstudio, onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box(
                modifier         = Modifier.fillMaxWidth().height(140.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFFF0F4FF), Color(0xFFE8F5E9)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(TipoEstudio.fromId(item.tipo)?.icon ?: "📦", fontSize = 48.sp)
                if (item.destaque) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                            .background(Color(0xFFC49A2A), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("⭐ Destaque", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(TipoEstudio.fromId(item.tipo)?.label ?: item.tipo, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(item.titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.autorNome.isNotEmpty()) {
                    Text("por ${item.autorNome}", fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
                }
                if (item.descricao.isNotEmpty()) {
                    Text(
                        item.descricao.take(80) + if (item.descricao.length > 80) "..." else "",
                        fontSize = 13.sp, color = Color(0xFF4B5563), lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column {
                        if (item.precoOriginal != null) {
                            Text(
                                "R$ ${"%.2f".format(item.precoOriginal)}",
                                fontSize = 11.sp, color = Color(0xFF9CA3AF),
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                        Text("R$ ${"%.2f".format(item.preco)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Verde)
                    }
                    Text("Ver →", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                }
            }
        }
    }
}

// ── TELA DETALHE ──────────────────────────────────────
@Composable
fun EstudioDetalheScreen(
    item:     ItemEstudio,
    onVoltar: () -> Unit,
    onPagar:  ((ItemEstudio) -> Unit)? = null,
) {
    val scope  = rememberCoroutineScope()
    val userId = currentUserId ?: ""

    var temAcesso            by remember { mutableStateOf(false) }
    var verificandoAcesso    by remember { mutableStateOf(true) }
    var mostrarReembolso     by remember { mutableStateOf(false) }
    var motivoReembolso      by remember { mutableStateOf("") }
    var processandoReembolso by remember { mutableStateOf(false) }
    var reembolsoResultado   by remember { mutableStateOf<String?>(null) }
    var purchaseId           by remember { mutableStateOf("") }

    LaunchedEffect(userId, item.id) {
        if (userId.isNotEmpty()) temAcesso = verificarAcessoProduto(userId, item.id)
        verificandoAcesso = false
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(Color(0xFFF8F7F4)),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Box(
                modifier         = Modifier.fillMaxWidth().height(220.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFFF0F4FF), Color(0xFFE8F5E9)))),
                contentAlignment = Alignment.Center,
            ) {
                if (item.capaUrl != null) {
                    AsyncImage(
                        model              = item.capaUrl,
                        contentDescription = "Capa do produto",
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                    )
                } else {
                    Text(TipoEstudio.fromId(item.tipo)?.icon ?: "📦", fontSize = 72.sp)
                }
                Row(modifier = Modifier.align(Alignment.TopStart).padding(16.dp).padding(top = 36.dp)) {
                    TextButton(onClick = onVoltar) {
                        Text("← Voltar", color = Color(0xFF374151), fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.background(Color.White).padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(TipoEstudio.fromId(item.tipo)?.label ?: item.tipo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Verde)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(item.titulo, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                if (item.autorNome.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier         = Modifier.size(32.dp).background(Azul, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item.autorNome.split(" ").map { it[0] }.joinToString("").take(2), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column {
                            Text("Criado por", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                            Text(item.autorNome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F0F0))
        }

        item {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(16.dp),
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (item.precoOriginal != null) {
                        Text(
                            "R$ ${"%.2f".format(item.precoOriginal)}",
                            fontSize = 14.sp, color = Color(0xFF9CA3AF),
                            textDecoration = TextDecoration.LineThrough,
                        )
                        val desconto = ((1 - item.preco / item.precoOriginal) * 100).toInt()
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text("$desconto% de desconto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text("R$ ${"%.2f".format(item.preco)}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Verde)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("ℹ️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "A plataforma retém 30% do valor (10% para profissionais PMP). " +
                                    "O repasse ocorre após o prazo de cancelamento de 7 dias.",
                            fontSize = 11.sp, color = Color(0xFF92400E), lineHeight = 16.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        verificandoAcesso -> {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Verde, strokeWidth = 2.dp)
                            }
                        }
                        temAcesso -> {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("✅", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Você já tem acesso a este produto.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Verde)
                                    Text("Acesse pelo seu histórico de compras.", fontSize = 11.sp, color = InkMuted)
                                }
                            }
                        }
                        else -> {
                            Button(
                                onClick  = { onPagar?.invoke(item) },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                                shape    = RoundedCornerShape(10.dp),
                            ) {
                                Text("💳 Adquirir agora", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("✓ PIX com desconto", "✓ Cartão em até 12x", "✓ Boleto bancário").forEach {
                                    Text(it, fontSize = 12.sp, color = Color(0xFF6B7280))
                                }
                            }
                        }
                    }

                    if (temAcesso && !reembolsoResultado.equals("ok")) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(onClick = { mostrarReembolso = !mostrarReembolso }, modifier = Modifier.fillMaxWidth()) {
                            Text("Solicitar reembolso (disponível por 7 dias)", fontSize = 12.sp, color = Urgente)
                        }
                        if (mostrarReembolso) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value         = motivoReembolso,
                                onValueChange = { motivoReembolso = it },
                                label         = { Text("Motivo do reembolso *") },
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = RoundedCornerShape(8.dp),
                                minLines      = 2,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Urgente,
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                ),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp)).padding(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text("⚠️", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Reembolsos por arrependimento deduzem do saldo do cliente. " +
                                            "Reembolsos por problema no produto deduzem do profissional.",
                                    fontSize = 11.sp, color = Urgente, lineHeight = 15.sp,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (motivoReembolso.isBlank()) return@Button
                                    processandoReembolso = true
                                    scope.launch {
                                        val resultado = solicitarReembolsoEstudio(purchaseId, motivoReembolso)
                                        reembolsoResultado   = resultado
                                        processandoReembolso = false
                                        mostrarReembolso     = false
                                    }
                                },
                                enabled  = motivoReembolso.isNotBlank() && !processandoReembolso,
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(containerColor = Urgente),
                                shape    = RoundedCornerShape(8.dp),
                            ) {
                                if (processandoReembolso) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Confirmar solicitação", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    reembolsoResultado?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        val (cor, msg) = when (res) {
                            "ok"                    -> Verde    to "✅ Reembolso solicitado. Acesso bloqueado até análise."
                            "prazo_expirado"        -> Urgente  to "⏰ Prazo de 7 dias expirado. Reembolso indisponível."
                            "ja_reembolsado"        -> InkMuted to "Reembolso já foi solicitado anteriormente."
                            "compra_nao_encontrada" -> Urgente  to "Compra não encontrada. Contacte o suporte."
                            else                    -> Urgente  to "Erro ao solicitar reembolso. Tente novamente."
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(cor.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(10.dp),
                        ) {
                            Text(msg, fontSize = 12.sp, color = cor, lineHeight = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth().background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("🔒 Pagamento 100% seguro", fontSize = 12.sp, color = Verde, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            val tipoEnum = TipoEstudio.fromId(item.tipo)
            val campos = mutableListOf<Pair<String, String>>()
            when (tipoEnum) {
                TipoEstudio.AULA -> {
                    item.materia?.let       { campos += "Matéria" to it }
                    item.duracaoMinutos?.let { campos += "Duração" to "$it minutos" }
                    item.nivelAula?.let     { campos += "Nível" to it }
                }
                TipoEstudio.CURSO -> {
                    item.cargaHorariaH?.let { campos += "Carga horária" to "$it horas" }
                    item.numModulos?.let    { campos += "Módulos" to "$it módulos" }
                    item.nivelCurso?.let    { campos += "Nível" to it }
                    if (item.certificado)   campos += "Certificado" to "Incluso"
                    item.duracaoMinutos?.let { campos += "Aula modelo" to "$it min" }
                }
                TipoEstudio.LIVRO -> {
                    item.autorLivro?.let { campos += "Autor" to it }
                    item.isbn?.let       { campos += "ISBN" to it }
                    item.numPaginas?.let { campos += "Páginas" to "$it" }
                    item.edicao?.let     { campos += "Edição" to it }
                }
                TipoEstudio.SAAS_DIGITAL -> {
                    item.plataforma?.let      { campos += "Plataforma" to it }
                    item.versaoProduto?.let   { campos += "Versão" to it }
                    if (item.suporteIncluido) campos += "Suporte" to "Incluso"
                    item.linkAcessoDigital?.let { campos += "Acesso" to it }
                }
                else -> {}
            }
            if (campos.isNotEmpty()) {
                Column(modifier = Modifier.background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Informações do produto", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                    campos.forEach { (label, valor) ->
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, fontSize = 13.sp, color = Color(0xFF6B7280))
                            Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                        }
                        HorizontalDivider(color = Color(0xFFF3F4F6))
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }
        }

        item {
            Column(modifier = Modifier.background(Color.White).padding(20.dp)) {
                Text("Sobre este produto", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 10.dp))
                Text(item.descricao, fontSize = 14.sp, color = Color(0xFF4B5563), lineHeight = 22.sp)
                if (!item.linkExterno.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth().background(Color(0xFFF0F4FF), RoundedCornerShape(8.dp)).padding(10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🔗", fontSize = 16.sp)
                        Column {
                            Text("Produto físico", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Azul)
                            Text(item.linkExterno, fontSize = 11.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F0F0))
        }

        item {
            Column(modifier = Modifier.background(Color(0xFFF0FDF4)).padding(20.dp)) {
                Text("Por que comprar aqui?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Verde, modifier = Modifier.padding(bottom = 12.dp))
                listOf(
                    "Profissional verificado pela Brasil Tupi Conecta",
                    "Pagamento seguro e criptografado",
                    "Acesso imediato após confirmação",
                    "Reembolso disponível em até 7 dias",
                ).forEach { texto ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        modifier              = Modifier.padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(modifier = Modifier.size(22.dp).background(Verde, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text("✓", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(texto, fontSize = 13.sp, color = Color(0xFF374151))
                    }
                }
            }
        }
    }
}

// ── VITRINE DO PROFISSIONAL ───────────────────────────
@Composable
fun EstudioVitrineScreen(profissionalId: String, onVoltar: () -> Unit) {
    var itens by remember { mutableStateOf<List<ItemEstudio>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filtroTipo by remember { mutableStateOf("todos") }
    var itemSelecionado by remember { mutableStateOf<ItemEstudio?>(null) }
    var itemParaPagar by remember { mutableStateOf<ItemEstudio?>(null) }
    var nomeProf by remember { mutableStateOf("") }
    var fotoProf by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profissionalId, filtroTipo) {
        loading = true
        try {
            itens = getEstudioProfissionalAndroid(profissionalId, filtroTipo)
        } catch (e: Exception) {
            itens = emptyList()
        } finally {
            loading = false
        }
    }
    LaunchedEffect(profissionalId) {
        try {
            val perfil = getPerfilAndroid(profissionalId)
            nomeProf = perfil?.nome ?: ""
            fotoProf = perfil?.foto_url
            if (profissionalId.isNotEmpty()) {
                AnalyticsTracker.profileView(profissionalId = profissionalId, area = perfil?.tipo ?: "profissional")
            }
        } catch (e: Exception) {}
    }

    when {
        itemParaPagar != null -> {
            PagamentoScreen(
                urgenciaId   = itemParaPagar?.id ?: "",
                onConfirmado = { itemParaPagar = null },
                onVoltar     = { itemParaPagar = null },
            )
        }
        itemSelecionado != null -> {
            EstudioDetalheScreen(
                item     = itemSelecionado!!,
                onVoltar = { itemSelecionado = null },
                onPagar  = { itemSelecionado = null; itemParaPagar = it },
            )
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F7F4))) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF0C2D6B), Color(0xFF1A5C3A))))
                        .padding(horizontal = 20.dp).padding(top = 52.dp, bottom = 24.dp),
                ) {
                    Column {
                        TextButton(onClick = onVoltar) {
                            Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier              = Modifier.padding(top = 8.dp),
                        ) {
                            Box(
                                modifier         = Modifier.size(52.dp).background(Color(0xFFC49A2A), RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (fotoProf != null) {
                                    AsyncImage(
                                        model = fotoProf, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Text(
                                        if (nomeProf.isNotEmpty()) nomeProf.split(" ").map { it[0] }.joinToString("").take(2).uppercase() else "?",
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    )
                                }
                            }
                            Column {
                                Text("Estúdio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                if (nomeProf.isNotEmpty())
                                    Text("de $nomeProf", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                else
                                    Text("do profissional", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(TipoEstudio.filtros) { (tipo, label) ->
                        FilterChip(
                            selected = filtroTipo == tipo,
                            onClick  = { filtroTipo = tipo },
                            label    = { Text(label, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Verde,
                                selectedLabelColor     = Color.White,
                            ),
                        )
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFC49A2A))
                    }
                } else if (itens.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Estúdio vazio", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            Text("Este profissional ainda não publicou itens.", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding      = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(itens) { item ->
                            CardEstudio(item = item, onClick = { itemSelecionado = item })
                        }
                    }
                }
            }
        }
    }
}