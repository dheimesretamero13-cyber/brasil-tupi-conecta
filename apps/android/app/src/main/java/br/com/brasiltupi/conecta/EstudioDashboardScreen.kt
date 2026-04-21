package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
@Composable
fun EstudioDashboardScreen(userId: String, onVoltar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var itens by remember { mutableStateOf<List<ItemEstudio>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filtroTipo by remember { mutableStateOf("todos") }
    var criando by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var nomeUsuario by remember { mutableStateOf("EU") }
    var formTitulo by remember { mutableStateOf("") }
    var formDescricao by remember { mutableStateOf("") }
    var formTipo by remember { mutableStateOf("aula") }
    var formPreco by remember { mutableStateOf("") }
    var formPrecoOriginal by remember { mutableStateOf("") }
    var formVideoUrl by remember { mutableStateOf("") }
    var formArquivoUrl by remember { mutableStateOf("") }
    var formLinkExterno by remember { mutableStateOf("") }
    var formTemEntrega by remember { mutableStateOf(false) }
    var formDestaque by remember { mutableStateOf(false) }
    var itemParaExcluir by remember { mutableStateOf<ItemEstudio?>(null) }
    var itemParaEditar by remember { mutableStateOf<ItemEstudio?>(null) }
    var excluindo by remember { mutableStateOf(false) }

// Espelha a lista para remoção otimista
    val itensVisiveis = remember { mutableStateListOf<ItemEstudio>() }

    LaunchedEffect(itens) {
        itensVisiveis.clear()
        itensVisiveis.addAll(itens)
    }
    fun resetForm() {
        formTitulo = ""; formDescricao = ""; formTipo = "aula"
        formPreco = ""; formPrecoOriginal = ""; formVideoUrl = ""
        formArquivoUrl = ""; formLinkExterno = ""
        formTemEntrega = false; formDestaque = false
    }

    LaunchedEffect(filtroTipo) {
        loading = true
        itens = getEstudioProfissionalAndroid(userId, filtroTipo)
        loading = false
    }

    fun salvarItem() {
        if (formTitulo.isEmpty() || formPreco.isEmpty()) {
            toast = "Preencha título e preço."
            return
        }
        scope.launch {
            val preco = formPreco.toDoubleOrNull() ?: 0.0
            val precoOrig = formPrecoOriginal.toDoubleOrNull()
            val sucesso = criarItemEstudioAndroid(
                profissionalId = userId,
                titulo = formTitulo,
                descricao = formDescricao,
                tipo = formTipo,
                preco = preco,
                precoOriginal = precoOrig,
                videoUrl = formVideoUrl.ifEmpty { null },
                arquivoUrl = formArquivoUrl.ifEmpty { null },
                linkExterno = formLinkExterno.ifEmpty { null },
                temEntrega = formTemEntrega,
                destaque = formDestaque,
            )
            if (sucesso) {
                toast = "✅ Item publicado no Estúdio!"
                criando = false
                resetForm()
                itens = getEstudioProfissionalAndroid(userId, filtroTipo)
            } else {
                toast = "❌ Erro ao salvar. Tente novamente."
            }
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(3500)
            toast = null
        }
    }
    LaunchedEffect(userId) {
        val perfil = getPerfilAndroid(userId)
        nomeUsuario =
            perfil?.nome?.split(" ")?.map { it[0] }?.joinToString("")?.take(2)?.uppercase() ?: "EU"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F7F4))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF0C2D6B), Color(0xFF1A5C3A))))
                    .padding(horizontal = 20.dp)
                    .padding(top = 52.dp, bottom = 24.dp)
            ) {
                Column {
                    TextButton(onClick = onVoltar) {
                        Text("← Voltar", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(0xFFC49A2A),
                                                    Color(0xFFE8B832)
                                                )
                                            ),
                                            RoundedCornerShape(50)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        nomeUsuario,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column {
                                    Text(
                                        "Meu Estúdio",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Gerencie seus cursos, aulas e produtos",
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    Triple("${itens.size}", "Itens", Color(0xFF111827)),
                    Triple("${itens.sumOf { it.totalVendas }}", "Vendas", Verde),
                    Triple("${itens.count { it.destaque }}", "Destaque", Color(0xFFC49A2A)),
                ).forEach { (num, label, cor) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(num, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cor)
                        Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F0F0))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tiposFiltro) { (tipo, label) ->
                    FilterChip(
                        selected = filtroTipo == tipo,
                        onClick = { filtroTipo = tipo },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Verde,
                            selectedLabelColor = Color.White,
                        )
                    )
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFC49A2A))
                }
            } else if (itens.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("+", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Seu Estúdio está vazio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        Text(
                            "Publique seu primeiro item e comece a vender.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                        )
                        Button(
                            onClick = { criando = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Verde,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "+ Criar primeiro item",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(itensVisiveis, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(56.dp).background(
                                            Color(0xFFF0F4FF),
                                            RoundedCornerShape(10.dp)
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(tipoIconMap[item.tipo] ?: "📦", fontSize = 26.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.titulo,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            tipoLabelMap[item.tipo] ?: item.tipo,
                                            fontSize = 11.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                        Text(
                                            "${item.totalVendas} vendas · R$ ${"%.2f".format(item.preco)}${if (item.destaque) " · ⭐" else ""}",
                                            fontSize = 11.sp, color = Color(0xFF9CA3AF)
                                        )
                                    }
                                    Text(
                                        "R$ ${"%.0f".format(item.preco)}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Verde
                                    )
                                }

                                // ── Botões de ação ────────────────────────
                                HorizontalDivider(
                                    color = Color(0xFFF3F4F6),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Editar
                                    OutlinedButton(
                                        onClick = {
                                            // Pré-preenche form com dados do item
                                            formTitulo = item.titulo
                                            formDescricao = item.descricao
                                            formTipo = item.tipo
                                            formPreco = item.preco.toInt().toString()
                                            formPrecoOriginal =
                                                item.precoOriginal?.toInt()?.toString() ?: ""
                                            formVideoUrl = item.videoUrl ?: ""
                                            formLinkExterno = item.linkExterno ?: ""
                                            formTemEntrega = item.temEntrega
                                            formDestaque = item.destaque
                                            itemParaEditar = item
                                            criando = true   // reutiliza o mesmo modal
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Azul),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            Azul.copy(alpha = 0.4f)
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = 14.dp,
                                            vertical = 6.dp
                                        )
                                    ) {
                                        Text(
                                            "✏️ Editar",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Excluir
                                    Button(
                                        onClick = { itemParaExcluir = item },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(
                                                0xFFFDE8E8
                                            ), contentColor = Urgente
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = 14.dp,
                                            vertical = 6.dp
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(0.dp)
                                    ) {
                                        Text(
                                            "🗑 Excluir",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        toast?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .background(Color(0xFF111827), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (criando) {
        EstudioNovoItemScreen(
            onCancelar = { criando = false; itemParaEditar = null; resetForm() },
            onPublicar = {
                val editando = itemParaEditar
                if (editando != null) {
                    // ── Salvar edição ──
                    if (formTitulo.isEmpty() || formPreco.isEmpty()) {
                        toast = "Preencha título e preço."; return@EstudioNovoItemScreen
                    }
                    scope.launch {
                        val dados = buildMap<String, Any> {
                            put("titulo", formTitulo)
                            put("descricao", formDescricao)
                            put("tipo", formTipo)
                            put("preco", formPreco.toDoubleOrNull() ?: 0.0)
                            if (formPrecoOriginal.isNotEmpty()) put(
                                "preco_original",
                                formPrecoOriginal.toDoubleOrNull() ?: 0.0
                            )
                            if (formVideoUrl.isNotEmpty()) put("video_url", formVideoUrl)
                            if (formLinkExterno.isNotEmpty()) put("link_externo", formLinkExterno)
                            put("tem_entrega", formTemEntrega)
                            put("destaque", formDestaque)
                        }
                        val ok = editarItemEstudio(editando.id, dados)
                        if (ok) {
                            // Atualiza lista local imediatamente
                            val idx = itensVisiveis.indexOfFirst { it.id == editando.id }
                            if (idx >= 0) itensVisiveis[idx] = editando.copy(
                                titulo = formTitulo,
                                descricao = formDescricao,
                                tipo = formTipo,
                                preco = formPreco.toDoubleOrNull() ?: editando.preco,
                                temEntrega = formTemEntrega,
                                destaque = formDestaque,
                            )
                            toast = "✅ Item atualizado!"
                        } else {
                            toast = "❌ Erro ao editar. Tente novamente."
                        }
                        criando = false; itemParaEditar = null; resetForm()
                    }
                } else {
                    salvarItem()
                }
            },
            formTitulo = formTitulo, onTitulo = { formTitulo = it },
            formDescricao = formDescricao, onDescricao = { formDescricao = it },
            formTipo = formTipo, onTipo = { formTipo = it },
            formPreco = formPreco, onPreco = { formPreco = it },
            formPrecoOriginal = formPrecoOriginal, onPrecoOriginal = { formPrecoOriginal = it },
            formVideoUrl = formVideoUrl, onVideoUrl = { formVideoUrl = it },
            formLinkExterno = formLinkExterno, onLinkExterno = { formLinkExterno = it },
            formTemEntrega = formTemEntrega, onTemEntrega = { formTemEntrega = it },
            formDestaque = formDestaque, onDestaque = { formDestaque = it },
        )
    }
    itemParaExcluir?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!excluindo) itemParaExcluir = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    "Excluir item",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            },
            text = {
                Column {
                    Text(
                        "Tem certeza que deseja excluir:",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "\"${item.titulo}\"",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Esta ação não pode ser desfeita.", fontSize = 12.sp, color = Urgente)
                    }
                }

            },
            confirmButton = {
                Button(
                    onClick = {
                        excluindo = true
                        scope.launch {
                            val ok = excluirItemEstudio(item.id)
                            excluindo = false
                            if (ok) {
                                itensVisiveis.removeIf { it.id == item.id }
                                toast = "🗑 Item excluído."
                            } else {
                                toast = "❌ Erro ao excluir. Tente novamente."
                            }
                            itemParaExcluir = null
                        }
                    },
                    enabled = !excluindo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Urgente,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (excluindo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Excluir", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { itemParaExcluir = null }, enabled = !excluindo) {
                    Text("Cancelar", color = Color(0xFF6B7280), fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
fun EstudioNovoItemScreen(
    onCancelar: () -> Unit,
    onPublicar: () -> Unit,
    formTitulo: String, onTitulo: (String) -> Unit,
    formDescricao: String, onDescricao: (String) -> Unit,
    formTipo: String, onTipo: (String) -> Unit,
    formPreco: String, onPreco: (String) -> Unit,
    formPrecoOriginal: String, onPrecoOriginal: (String) -> Unit,
    formVideoUrl: String, onVideoUrl: (String) -> Unit,
    formLinkExterno: String, onLinkExterno: (String) -> Unit,
    formTemEntrega: Boolean, onTemEntrega: (Boolean) -> Unit,
    formDestaque: Boolean, onDestaque: (Boolean) -> Unit,
) {
    val itemCompleto = formTitulo.isNotEmpty() && formDescricao.isNotEmpty() && formPreco.isNotEmpty()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancelar,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Row(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C2D6B))
                            .padding(horizontal = 20.dp)
                            .padding(top = 52.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Novo item no Estúdio", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        TextButton(onClick = onCancelar) {
                            Text("✕", color = Color.White, fontSize = 18.sp)
                        }
                    }
                }

                item {
                    Column(
                        modifier = androidx.compose.ui.Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Tipo *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                        Row(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tiposFiltro.filter { it.first != "todos" }.forEach { (tipo, label) ->
                                FilterChip(
                                    selected = formTipo == tipo,
                                    onClick = { onTipo(tipo) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Verde,
                                        selectedLabelColor = Color.White,
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = formTitulo,
                            onValueChange = onTitulo,
                            label = { Text("Título *") },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = formDescricao,
                            onValueChange = onDescricao,
                            label = { Text("Descrição") },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            minLines = 3
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = formPreco,
                                onValueChange = onPreco,
                                label = { Text("Preço R$ *") },
                                modifier = androidx.compose.ui.Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true,
                                placeholder = { Text("0,00") }
                            )
                            OutlinedTextField(
                                value = formPrecoOriginal,
                                onValueChange = onPrecoOriginal,
                                label = { Text("Preço original") },
                                modifier = androidx.compose.ui.Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true,
                                placeholder = { Text("Para desconto") }
                            )
                        }

                        if (formTipo == "aula" || formTipo == "curso") {
                            OutlinedTextField(
                                value = formVideoUrl,
                                onValueChange = onVideoUrl,
                                label = { Text("URL do vídeo") },
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true,
                                placeholder = { Text("https://...") }
                            )
                        }

                        OutlinedTextField(
                            value = formLinkExterno,
                            onValueChange = onLinkExterno,
                            label = { Text("Link externo (opcional)") },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true,
                            placeholder = { Text("Hotmart, Kiwify, Amazon...") }
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = formTemEntrega, onCheckedChange = onTemEntrega, colors = CheckboxDefaults.colors(checkedColor = Verde))
                            Text("Tem entrega física", fontSize = 13.sp, color = Color(0xFF374151))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = formDestaque, onCheckedChange = onDestaque, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC49A2A)))
                            Text("Marcar como destaque", fontSize = 13.sp, color = Color(0xFF374151))
                        }

                        Row(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .background(
                                    if (itemCompleto) Color(0xFFF0FDF4) else Color(0xFFFFF9E6),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                Text("💡 Sua comissão", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = if (itemCompleto) Verde else Color(0xFFB07D00))
                                Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                                Text(
                                    if (itemCompleto)
                                        "✅ Item completo — comissão reduzida de 8%"
                                    else
                                        "⚠️ Complete título, descrição e preço para obter a melhor comissão (8%)",
                                    fontSize = 12.sp,
                                    color = if (itemCompleto) Verde else Color(0xFF374151),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = androidx.compose.ui.Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelar,
                    modifier = androidx.compose.ui.Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancelar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onPublicar,
                    modifier = androidx.compose.ui.Modifier.weight(2f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Publicar no Estúdio", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}