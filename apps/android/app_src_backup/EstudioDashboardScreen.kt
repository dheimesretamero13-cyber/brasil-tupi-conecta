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

private data class FormState(
    val titulo: String = "",
    val descricao: String = "",
    val tipo: String = "aula",
    val preco: String = "",
    val precoOriginal: String = "",
    val videoUrl: String = "",
    val arquivoUrl: String = "",
    val linkExterno: String = "",
    val temEntrega: Boolean = false,
    val destaque: Boolean = false,
)

@Composable
fun EstudioDashboardScreen(
    userId: String,
    onVoltar: () -> Unit,
    kycAprovado: Boolean = false,
    onKyc: (() -> Unit)? = null,
) {
    // ── GUARD KYC — bloqueia todo o Estúdio se não verificado ────────────
    if (!kycAprovado) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F7F4))) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header mínimo com botão voltar
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
                        Text("Meu Estúdio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Verificação necessária", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                // Card de bloqueio
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔒", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Perfil não verificado",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Para publicar e vender no Estúdio você precisa ter seus documentos aprovados na verificação de identidade (KYC).",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Após a aprovação, o acesso ao Estúdio é liberado automaticamente.",
                        fontSize = 13.sp,
                        color = Color(0xFF9CA3AF),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(
                        onClick  = { onKyc?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF57F17),
                            contentColor   = Color.White,
                        ),
                    ) {
                        Text("Verificar meu perfil", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick  = onVoltar,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape    = RoundedCornerShape(10.dp),
                    ) {
                        Text("Voltar ao painel", fontSize = 14.sp, color = Color(0xFF6B7280))
                    }
                }
            }
        }
        return
    }

    // ── Conteúdo normal do Estúdio (apenas se KYC aprovado) ──────────────
    val scope = rememberCoroutineScope()
    var itens by remember { mutableStateOf<List<ItemEstudio>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filtroTipo by remember { mutableStateOf("todos") }
    var criando by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var nomeUsuario by remember { mutableStateOf("EU") }
    var form by remember { mutableStateOf(FormState()) }
    var itemParaExcluir by remember { mutableStateOf<ItemEstudio?>(null) }
    var itemParaEditar by remember { mutableStateOf<ItemEstudio?>(null) }
    var excluindo by remember { mutableStateOf(false) }

    LaunchedEffect(filtroTipo) {
        loading = true
        try {
            itens = getEstudioProfissionalAndroid(userId, filtroTipo)
        } catch (e: Exception) {
            toast = "❌ Erro ao carregar itens."
        } finally {
            loading = false
        }
    }

    fun salvarItem() {
        if (form.titulo.isEmpty() || form.preco.isEmpty()) {
            toast = "Preencha título e preço."
            return
        }
        scope.launch {
            try {
                val sucesso = criarItemEstudioAndroid(
                    profissionalId = userId,
                    titulo = form.titulo,
                    descricao = form.descricao,
                    tipo = form.tipo,
                    preco = form.preco.toDoubleOrNull() ?: 0.0,
                    precoOriginal = form.precoOriginal.toDoubleOrNull(),
                    videoUrl = form.videoUrl.ifEmpty { null },
                    arquivoUrl = form.arquivoUrl.ifEmpty { null },
                    linkExterno = form.linkExterno.ifEmpty { null },
                    temEntrega = form.temEntrega,
                    destaque = form.destaque,
                )
                if (sucesso) {
                    toast = "✅ Item publicado no Estúdio!"
                    criando = false
                    form = FormState()
                    itens = getEstudioProfissionalAndroid(userId, filtroTipo)
                } else {
                    toast = "❌ Erro ao salvar. Tente novamente."
                }
            } catch (e: Exception) {
                toast = "❌ Erro inesperado. Tente novamente."
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
            perfil?.nome?.split(" ")?.map { it[0] }?.joinToString("")?.take(2)?.uppercase()
                ?: "EU"
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
                items(TipoEstudio.filtros) { (tipo, label) ->
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
                    items(itens, key = { it.id }) { item ->
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
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                Color(0xFFF0F4FF),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(TipoEstudio.fromId(item.tipo)?.icon ?: "📦", fontSize = 26.sp)
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
                                            TipoEstudio.fromId(item.tipo)?.label ?: item.tipo,
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

                                HorizontalDivider(
                                    color = Color(0xFFF3F4F6),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            form = form.copy(
                                                titulo = item.titulo,
                                                descricao = item.descricao,
                                                tipo = item.tipo,
                                                preco = item.preco.toInt().toString(),
                                                precoOriginal = item.precoOriginal?.toInt()?.toString() ?: "",
                                                videoUrl = item.videoUrl ?: "",
                                                arquivoUrl = item.linkExterno ?: "",
                                                linkExterno = item.linkExterno ?: "",
                                                temEntrega = item.temEntrega,
                                                destaque = item.destaque,
                                            )
                                            itemParaEditar = item
                                            criando = true
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

                                    Button(
                                        onClick = { itemParaExcluir = item },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFDE8E8),
                                            contentColor = Urgente
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
            onCancelar = {
                criando = false
                itemParaEditar = null
                form = FormState()
            },
            onPublicar = {
                val editando = itemParaEditar
                if (editando != null) {
                    if (form.titulo.isEmpty() || form.preco.isEmpty()) {
                        toast = "Preencha título e preço."
                        return@EstudioNovoItemScreen
                    }
                    val request = EditarItemEstudioRequest(
                        titulo        = form.titulo,
                        descricao     = form.descricao.ifBlank { null },
                        tipo          = form.tipo,
                        preco         = form.preco.toDoubleOrNull() ?: 0.0,
                        precoOriginal = form.precoOriginal.toDoubleOrNull(),
                        videoUrl      = form.videoUrl.ifBlank { null },
                        arquivoUrl    = form.arquivoUrl.ifBlank { null },
                        linkExterno   = form.linkExterno.ifBlank { null },
                        temEntrega    = form.temEntrega,
                        destaque      = form.destaque,
                    )
                    scope.launch {
                        try {
                            val ok = editarItemEstudio(editando.id, request)
                            if (ok) {
                                itens = itens.map { i ->
                                    if (i.id == editando.id) i.copy(
                                        titulo        = form.titulo,
                                        descricao     = form.descricao,
                                        tipo          = form.tipo,
                                        preco         = form.preco.toDoubleOrNull() ?: i.preco,
                                        precoOriginal = form.precoOriginal.toDoubleOrNull(),
                                        videoUrl      = form.videoUrl.ifEmpty { null },
                                        linkExterno   = form.linkExterno.ifEmpty { null },
                                        temEntrega    = form.temEntrega,
                                        destaque      = form.destaque,
                                    ) else i
                                }
                                toast = "✅ Item atualizado!"
                            } else {
                                toast = "❌ Erro ao editar. Tente novamente."
                            }
                        } catch (e: Exception) {
                            toast = "❌ Erro inesperado. Tente novamente."
                        } finally {
                            criando = false
                            itemParaEditar = null
                            form = FormState()
                        }
                    }
                } else {
                    salvarItem()
                }
            },
            titulo = form.titulo,
            onTitulo = { form = form.copy(titulo = it) },
            descricao = form.descricao,
            onDescricao = { form = form.copy(descricao = it) },
            tipo = form.tipo,
            onTipo = { form = form.copy(tipo = it) },
            preco = form.preco,
            onPreco = { form = form.copy(preco = it) },
            precoOriginal = form.precoOriginal,
            onPrecoOriginal = { form = form.copy(precoOriginal = it) },
            videoUrl = form.videoUrl,
            onVideoUrl = { form = form.copy(videoUrl = it) },
            linkExterno = form.linkExterno,
            onLinkExterno = { form = form.copy(linkExterno = it) },
            temEntrega = form.temEntrega,
            onTemEntrega = { form = form.copy(temEntrega = it) },
            destaque = form.destaque,
            onDestaque = { form = form.copy(destaque = it) },
            isEditando = itemParaEditar != null,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp))
                            .padding(10.dp),
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
                            try {
                                val ok = excluirItemEstudio(item.id)
                                if (ok) {
                                    itens = itens.filterNot { it.id == item.id }
                                    toast = "🗑 Item excluído."
                                } else {
                                    toast = "❌ Erro ao excluir. Tente novamente."
                                }
                            } catch (e: Exception) {
                                toast = "❌ Erro inesperado. Tente novamente."
                            } finally {
                                excluindo = false
                                itemParaExcluir = null
                            }
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
    isEditando: Boolean = false,
    titulo: String,
    onTitulo: (String) -> Unit,
    descricao: String,
    onDescricao: (String) -> Unit,
    tipo: String,
    onTipo: (String) -> Unit,
    preco: String,
    onPreco: (String) -> Unit,
    precoOriginal: String,
    onPrecoOriginal: (String) -> Unit,
    videoUrl: String,
    onVideoUrl: (String) -> Unit,
    linkExterno: String,
    onLinkExterno: (String) -> Unit,
    temEntrega: Boolean,
    onTemEntrega: (Boolean) -> Unit,
    destaque: Boolean,
    onDestaque: (Boolean) -> Unit,
) {
    val itemCompleto = titulo.isNotEmpty() && descricao.isNotEmpty() && preco.isNotEmpty()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancelar,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C2D6B))
                            .padding(horizontal = 20.dp)
                            .padding(top = 52.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isEditando) "Editar item do Estúdio" else "Novo item no Estúdio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        TextButton(onClick = onCancelar) {
                            Text("✕", color = Color.White, fontSize = 18.sp)
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Tipo *",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TipoEstudio.filtros.filter { it.first != "todos" }.forEach { (tipoOpt, label) ->
                                FilterChip(
                                    selected = tipo == tipoOpt,
                                    onClick = { onTipo(tipoOpt) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Verde,
                                        selectedLabelColor = Color.White,
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = titulo,
                            onValueChange = onTitulo,
                            label = { Text("Título *") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Verde,
                                unfocusedBorderColor = Color(0xFFE0E0E0)
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = descricao,
                            onValueChange = onDescricao,
                            label = { Text("Descrição") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Verde,
                                unfocusedBorderColor = Color(0xFFE0E0E0)
                            ),
                            minLines = 3
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = preco,
                                onValueChange = onPreco,
                                label = { Text("Preço R$ *") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Verde,
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                ),
                                singleLine = true,
                                placeholder = { Text("0,00") }
                            )
                            OutlinedTextField(
                                value = precoOriginal,
                                onValueChange = onPrecoOriginal,
                                label = { Text("Preço original") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Verde,
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                ),
                                singleLine = true,
                                placeholder = { Text("Para desconto") }
                            )
                        }

                        if (tipo == "aula" || tipo == "curso") {
                            OutlinedTextField(
                                value = videoUrl,
                                onValueChange = onVideoUrl,
                                label = { Text("URL do vídeo") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Verde,
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                ),
                                singleLine = true,
                                placeholder = { Text("https://...") }
                            )
                        }

                        OutlinedTextField(
                            value = linkExterno,
                            onValueChange = onLinkExterno,
                            label = { Text("Link externo (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Verde,
                                unfocusedBorderColor = Color(0xFFE0E0E0)
                            ),
                            singleLine = true,
                            placeholder = { Text("Hotmart, Kiwify, Amazon...") }
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = temEntrega,
                                onCheckedChange = onTemEntrega,
                                colors = CheckboxDefaults.colors(checkedColor = Verde)
                            )
                            Text("Tem entrega física", fontSize = 13.sp, color = Color(0xFF374151))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = destaque,
                                onCheckedChange = onDestaque,
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFC49A2A))
                            )
                            Text(
                                "Marcar como destaque",
                                fontSize = 13.sp,
                                color = Color(0xFF374151)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (itemCompleto) Color(0xFFF0FDF4) else Color(0xFFFFF9E6),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    "💡 Sua comissão",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (itemCompleto) Verde else Color(0xFFB07D00)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelar,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancelar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onPublicar,
                    modifier = Modifier.weight(2f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Verde,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isEditando) "Salvar alterações" else "Publicar no Estúdio", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}