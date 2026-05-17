package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.webkit.MimeTypeMap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource

data class ConteudoItem(
    val id: String = "",
    val ordem: Int = 0,
    val titulo: String = "",
    val descricao: String = "",
    val tipo: String = "video",
    val storagePath: String? = null,
    val urlExterna: String? = null,
    val uploadUri: Uri? = null,
)

private data class FormState(
    val titulo: String = "",
    val descricao: String = "",
    val tipo: String = "aula",
    val preco: String = "",
    val precoOriginal: String = "",
    val arquivoUrl: String = "",
    val linkExterno: String = "",
    val capaUrl: String = "",
    val temEntrega: Boolean = false,
    val destaque: Boolean = false,
    val materia: String = "",
    val duracaoMinutos: String = "",
    val nivelAula: String = "",
    val cargaHorariaH: String = "",
    val numModulos: String = "",
    val certificado: Boolean = false,
    val nivelCurso: String = "",
    val autorLivro: String = "",
    val isbn: String = "",
    val numPaginas: String = "",
    val edicao: String = "",
    val plataforma: String = "",
    val versaoProduto: String = "",
    val suporteIncluido: Boolean = false,
    val linkAcessoDigital: String = "",
)
sealed class EstudioSalvandoState {
    object Idle : EstudioSalvandoState()
    data class FazendoUploadCapa(val progresso: Float) : EstudioSalvandoState()
    data class FazendoUploadArquivo(val progresso: Float) : EstudioSalvandoState()
    object SalvandoMetadados : EstudioSalvandoState()
    data class SalvandoConteudos(val atual: Int, val total: Int) : EstudioSalvandoState()
    object Sucesso : EstudioSalvandoState()
    data class Erro(val mensagem: String, val etapa: String) : EstudioSalvandoState()
}

@Composable
fun EstudioDashboardScreen(
    userId: String,
    onVoltar: () -> Unit,
    kycAprovado: Boolean = false,
    onKyc: (() -> Unit)? = null,
) {
    if (!kycAprovado) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F7F4))) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔒", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Perfil não verificado", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
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
                        onClick = { onKyc?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17), contentColor = Color.White),
                    ) {
                        Text("Verificar meu perfil", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onVoltar,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Voltar ao painel", fontSize = 14.sp, color = Color(0xFF6B7280))
                    }
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var itens by remember { mutableStateOf<List<ItemEstudio>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filtroTipo by remember { mutableStateOf("todos") }
    var tipoCriando by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var nomeUsuario by remember { mutableStateOf("EU") }
    var form by remember { mutableStateOf(FormState()) }
    var capaUri by remember { mutableStateOf<Uri?>(null) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var conteudosList by remember { mutableStateOf<List<ConteudoItem>>(emptyList()) }
    var isPmpEstudio by remember { mutableStateOf(false) }
    var verificadoEstudio by remember { mutableStateOf(false) }
    var itemParaExcluir by remember { mutableStateOf<ItemEstudio?>(null) }
    var itemParaEditar by remember { mutableStateOf<ItemEstudio?>(null) }
    var excluindo by remember { mutableStateOf(false) }
    var salvandoState by remember { mutableStateOf<EstudioSalvandoState>(EstudioSalvandoState.Idle) }

    LaunchedEffect(userId) {
        val perfil = getMeuPerfilProfissional(userId)
        isPmpEstudio = perfil?.is_pmp ?: false
        verificadoEstudio = perfil?.verificado ?: false
    }

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

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(3500)
            toast = null
        }
    }

    LaunchedEffect(userId) {
        val perfil = getPerfilAndroid(userId)
        nomeUsuario = perfil?.nome
            ?.split(" ")
            ?.filter { it.isNotEmpty() }
            ?.map { it[0] }
            ?.joinToString("")
            ?.take(2)
            ?.uppercase()
            ?: "EU"
    }

    fun resetForm() {
        form = FormState()
        capaUri = null
        pdfUri = null
        videoUri = null
        conteudosList = emptyList()
        itemParaEditar = null
        tipoCriando = null
    }

    suspend fun uploadCapa(userId: String, uri: Uri, fallback: String): String? {
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
            val rawMime = context.contentResolver.getType(uri)
            val mimeType = when (rawMime) {
                "image/jpg", "image/jpeg" -> "image/jpeg"
                "image/png" -> "image/png"
                "image/webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val ext = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val path = "$userId/${UUID.randomUUID()}.$ext"
            uploadArquivoEstudio(
                bytes = bytes,
                mimeType = mimeType,
                caminho = path,
                bucket = "estudio-capas",
                returnPublicUrl = true
            )
        }
    }
    suspend fun uploadVideo(userId: String, uri: Uri, fallback: String): String? {
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
            val path = "$userId/${UUID.randomUUID()}.mp4"
            uploadArquivoEstudio(bytes, "video/mp4", path)
        }
    }

    suspend fun uploadPdf(userId: String, uri: Uri, fallback: String): String? {
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
            val path = "$userId/${UUID.randomUUID()}.pdf"
            uploadArquivoEstudio(bytes, "application/pdf", path)
        }
    }

    suspend fun salvarConteudos(estudioId: String) {
        if (estudioId.isBlank() || conteudosList.isEmpty()) return
        conteudosList.forEachIndexed { index, conteudo ->
            inserirConteudoEstudio(
                ConteudoEstudioRequest(
                    estudioId = estudioId,
                    ordem = index + 1,
                    titulo = conteudo.titulo,
                    descricao = conteudo.descricao,
                    tipo = conteudo.tipo,
                    storagePath = conteudo.storagePath,
                    urlExterna = conteudo.urlExterna,
                )
            )
        }
    }
    suspend fun uploadEInserirConteudos(estudioId: String, conteudos: List<ConteudoItem>) {
        if (estudioId.isBlank() || conteudos.isEmpty()) return
        for (conteudo in conteudos) {
            val uploadedPath = if (conteudo.uploadUri != null) {
                when (conteudo.tipo) {
                    "video" -> uploadVideo(userId, conteudo.uploadUri, conteudo.storagePath ?: "")
                    "pdf" -> uploadPdf(userId, conteudo.uploadUri, conteudo.storagePath ?: "")
                    else -> withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(conteudo.uploadUri)?.readBytes() ?: return@withContext null
                        uploadArquivoEstudio(bytes, "application/octet-stream", "${userId}/${UUID.randomUUID()}")
                    }
                }
            } else conteudo.storagePath
            if (uploadedPath == null && conteudo.uploadUri != null) continue // pula conteúdo cujo upload falhou
            inserirConteudoEstudio(
                ConteudoEstudioRequest(
                    estudioId = estudioId,
                    ordem = conteudo.ordem,
                    titulo = conteudo.titulo,
                    descricao = conteudo.descricao,
                    tipo = conteudo.tipo,
                    storagePath = uploadedPath,
                    urlExterna = conteudo.urlExterna,
                )
            )
        }
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFFC49A2A), Color(0xFFE8B832))),
                                        RoundedCornerShape(50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(nomeUsuario, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Column {
                                Text("Meu Estúdio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Gerencie seus cursos, aulas e produtos", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp),
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

            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filtrosDashboard = remember { TipoEstudio.filtros.filter { it.first != "consulta_avulsa" } }
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtrosDashboard) { (tipo, label) ->
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
                if (filtroTipo != "todos" && filtroTipo != "consulta_avulsa") {
                    Spacer(modifier = Modifier.width(8.dp))
                    var expandirMenuAba by remember { mutableStateOf(false) }
                    Box {
                        SmallFloatingActionButton(
                            onClick = { tipoCriando = filtroTipo },
                            containerColor = Verde,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
                        Text("🎓", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Seu Estúdio está vazio", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text(
                            "Publique seu primeiro item e comece a vender.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                        )
                        var expandirMenuCriar by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { expandirMenuCriar = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Novo item", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(expanded = expandirMenuCriar, onDismissRequest = { expandirMenuCriar = false }) {
                                listOf("aula" to "🎬 Aula", "curso" to "📚 Curso", "livro" to "📖 Livro", "saas_digital" to "💻 SaaS / Digital").forEach { (tipo, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 14.sp) },
                                        onClick = {
                                            expandirMenuCriar = false
                                            tipoCriando = tipo
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        var expandirMenuLista by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = { expandirMenuLista = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Novo item", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(expanded = expandirMenuLista, onDismissRequest = { expandirMenuLista = false }) {
                                listOf("aula" to "🎬 Aula", "curso" to "📚 Curso", "livro" to "📖 Livro", "saas_digital" to "💻 SaaS / Digital").forEach { (tipo, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 14.sp) },
                                        onClick = {
                                            expandirMenuLista = false
                                            tipoCriando = tipo
                                        }
                                    )
                                }
                            }
                        }
                    }
                    items(itens, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                                    // ── THUMBNAIL: imagem real ou emoji fallback ──────────────
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color(0xFFF0F4FF), RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp)),        // ← clip antes do AsyncImage
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!item.capaUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = item.capaUrl,
                                                contentDescription = "Capa de ${item.titulo}",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                // fallback e erro mostram o emoji enquanto carrega ou se falhar
                                                error   = painterResource(android.R.drawable.ic_menu_gallery),
                                                placeholder = null,
                                            )
                                        } else {
                                            Text(TipoEstudio.fromId(item.tipo)?.icon ?: "📦", fontSize = 26.sp)
                                        }
                                    }
                                    // ─────────────────────────────────────────────────────────

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
                                        Text(TipoEstudio.fromId(item.tipo)?.label ?: item.tipo, fontSize = 11.sp, color = Color(0xFF6B7280))
                                        Text(
                                            "${item.totalVendas} vendas · R$ ${"%.2f".format(item.preco)}${if (item.destaque) " · ⭐" else ""}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF9CA3AF)
                                        )
                                    }
                                    if (item.tipo != "saas_digital") {
                                        Text("R$ ${"%.0f".format(item.preco)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Verde)
                                    } else {
                                        Text("Gratuito", fontSize = 12.sp, color = Verde, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(vertical = 10.dp))

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
                                                arquivoUrl = item.linkExterno ?: "",
                                                linkExterno = item.linkExterno ?: "",
                                                temEntrega = item.temEntrega,
                                                destaque = item.destaque,
                                            )
                                            itemParaEditar = item
                                            tipoCriando = item.tipo
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Azul),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Azul.copy(alpha = 0.4f)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("✏️ Editar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { itemParaExcluir = item },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFDE8E8), contentColor = Urgente),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        elevation = ButtonDefaults.buttonElevation(0.dp)
                                    ) {
                                        Text("🗑 Excluir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

    when (tipoCriando) {
        "aula" -> {
            ModalAulaScreen(
                isEditando = itemParaEditar != null,
                onCancelar = { resetForm() },
                onPublicar = {
                    if (form.titulo.isEmpty() || form.preco.isEmpty()) {
                        toast = "Preencha título e preço."
                        return@ModalAulaScreen
                    }
                    val editando = itemParaEditar
                    scope.launch {
                        try {
                            salvandoState = EstudioSalvandoState.FazendoUploadCapa(0f)
                            var capaPath: String? = form.capaUrl
                            if (capaUri != null) {
                                capaPath = uploadCapa(userId, capaUri!!, capaPath ?: "")
                                if (capaPath == null) {
                                    toast = "❌ Erro ao fazer upload da capa."
                                    salvandoState = EstudioSalvandoState.Idle
                                    return@launch
                                }
                            }
                            salvandoState = EstudioSalvandoState.SalvandoMetadados
                            val ok: Boolean
                            val estudioIdCriado: String?
                            if (editando == null) {
                                val (success, newId) = criarItemEstudioAndroid(
                                    profissionalId = userId,
                                    titulo = form.titulo,
                                    descricao = form.descricao,
                                    tipo = "aula",
                                    preco = form.preco.toDoubleOrNull() ?: 0.0,
                                    precoOriginal = form.precoOriginal.toDoubleOrNull(),
                                    capaUrl = capaPath,
                                    materia = form.materia.ifBlank { null },
                                    duracaoMinutos = form.duracaoMinutos.toIntOrNull(),
                                    nivelAula = form.nivelAula.ifBlank { null },
                                    destaque = form.destaque,
                                )
                                ok = success
                                estudioIdCriado = newId
                            } else {
                                ok = editarItemEstudio(editando.id, EditarItemEstudioRequest(
                                    titulo = form.titulo,
                                    descricao = form.descricao.ifBlank { null },
                                    tipo = "aula",
                                    preco = form.preco.toDoubleOrNull() ?: 0.0,
                                    precoOriginal = form.precoOriginal.toDoubleOrNull(),
                                    capaUrl = capaPath,
                                    materia = form.materia.ifBlank { null },
                                    duracaoMinutos = form.duracaoMinutos.toIntOrNull(),
                                    nivelAula = form.nivelAula.ifBlank { null },
                                    destaque = form.destaque,
                                ))
                                estudioIdCriado = null
                            }
                            if (ok && conteudosList.isNotEmpty()) {
                                val estudioId = estudioIdCriado ?: editando?.id ?: ""
                                salvandoState = EstudioSalvandoState.SalvandoConteudos(0, conteudosList.size)
                                uploadEInserirConteudos(estudioId, conteudosList)
                            }
                            if (ok) {
                                salvandoState = EstudioSalvandoState.Sucesso
                                itens = getEstudioProfissionalAndroid(userId, filtroTipo)
                                resetForm()
                                toast = "✅ Aula salva!"
                            } else {
                                salvandoState = EstudioSalvandoState.Idle
                                toast = "❌ Erro ao salvar."
                            }
                        } catch (e: Exception) {
                            salvandoState = EstudioSalvandoState.Erro(e.message ?: "Erro", "aula")
                            toast = "❌ Erro inesperado."
                        } finally {
                            if (salvandoState !is EstudioSalvandoState.Erro) {
                                kotlinx.coroutines.delay(500)
                                salvandoState = EstudioSalvandoState.Idle
                            }
                        }
                    }
                },
                titulo = form.titulo, onTitulo = { form = form.copy(titulo = it) },
                descricao = form.descricao, onDescricao = { form = form.copy(descricao = it) },
                materia = form.materia, onMateria = { form = form.copy(materia = it) },
                duracaoMinutos = form.duracaoMinutos, onDuracaoMinutos = { form = form.copy(duracaoMinutos = it) },
                nivelAula = form.nivelAula, onNivelAula = { form = form.copy(nivelAula = it) },
                preco = form.preco, onPreco = { form = form.copy(preco = it) },
                precoOriginal = form.precoOriginal, onPrecoOriginal = { form = form.copy(precoOriginal = it) },
                capaUri = capaUri, onCapaChanged = { capaUri = it },
                destaque = form.destaque, onDestaque = { form = form.copy(destaque = it) },
                conteudos = conteudosList, onConteudosChanged = { conteudosList = it },
                isPmp = isPmpEstudio, verificado = verificadoEstudio,
                salvandoState = salvandoState,
            )
        }
        "curso" -> {
            ModalCursoScreen(
                isEditando = itemParaEditar != null,
                onCancelar = { resetForm() },
                onPublicar = {
                    if (form.titulo.isEmpty() || form.preco.isEmpty()) {
                        toast = "Preencha título e preço."
                        return@ModalCursoScreen
                    }
                    val editando = itemParaEditar
                    scope.launch {
                        try {
                            salvandoState = EstudioSalvandoState.FazendoUploadCapa(0f)
                            var capaPath: String? = form.capaUrl
                            if (capaUri != null) {
                                capaPath = uploadCapa(userId, capaUri!!, capaPath ?: "")
                                if (capaPath == null) {
                                    toast = "❌ Erro ao fazer upload da capa."
                                    salvandoState = EstudioSalvandoState.Idle
                                    return@launch
                                }
                            }
                            salvandoState = EstudioSalvandoState.SalvandoMetadados
                            val ok: Boolean
                            val estudioIdCriado: String?
                            if (editando == null) {
                                val (success, newId) = criarItemEstudioAndroid(
                                    profissionalId = userId,
                                    titulo = form.titulo,
                                    descricao = form.descricao,
                                    tipo = "curso",
                                    preco = form.preco.toDoubleOrNull() ?: 0.0,
                                    precoOriginal = form.precoOriginal.toDoubleOrNull(),
                                    capaUrl = capaPath,
                                    cargaHorariaH = form.cargaHorariaH.toIntOrNull(),
                                    numModulos = form.numModulos.toIntOrNull(),
                                    nivelCurso = form.nivelCurso.ifBlank { null },
                                    certificado = form.certificado,
                                    materia = form.materia.ifBlank { null },
                                    destaque = form.destaque,
                                )
                                ok = success
                                estudioIdCriado = newId
                            } else {
                                ok = editarItemEstudio(editando.id, EditarItemEstudioRequest(
                                    titulo = form.titulo,
                                    descricao = form.descricao.ifBlank { null },
                                    tipo = "curso",
                                    preco = form.preco.toDoubleOrNull() ?: 0.0,
                                    precoOriginal = form.precoOriginal.toDoubleOrNull(),
                                    capaUrl = capaPath,
                                    cargaHorariaH = form.cargaHorariaH.toIntOrNull(),
                                    numModulos = form.numModulos.toIntOrNull(),
                                    nivelCurso = form.nivelCurso.ifBlank { null },
                                    certificado = form.certificado,
                                    materia = form.materia.ifBlank { null },
                                    destaque = form.destaque,
                                ))
                                estudioIdCriado = null
                            }
                            if (ok && conteudosList.isNotEmpty()) {
                                val estudioId = estudioIdCriado ?: editando?.id ?: ""
                                salvandoState = EstudioSalvandoState.SalvandoConteudos(0, conteudosList.size)
                                uploadEInserirConteudos(estudioId, conteudosList)
                            }
                            if (ok) {
                                salvandoState = EstudioSalvandoState.Sucesso
                                itens = getEstudioProfissionalAndroid(userId, filtroTipo)
                                resetForm()
                                toast = "✅ Curso salvo!"
                            } else {
                                salvandoState = EstudioSalvandoState.Idle
                                toast = "❌ Erro ao salvar."
                            }
                        } catch (e: Exception) {
                            salvandoState = EstudioSalvandoState.Erro(e.message ?: "Erro", "curso")
                            toast = "❌ Erro inesperado."
                        } finally {
                            if (salvandoState !is EstudioSalvandoState.Erro) {
                                kotlinx.coroutines.delay(500)
                                salvandoState = EstudioSalvandoState.Idle
                            }
                        }
                    }
                },
                titulo = form.titulo, onTitulo = { form = form.copy(titulo = it) },
                descricao = form.descricao, onDescricao = { form = form.copy(descricao = it) },
                materia = form.materia, onMateria = { form = form.copy(materia = it) },
                cargaHorariaH = form.cargaHorariaH, onCargaHorariaH = { form = form.copy(cargaHorariaH = it) },
                numModulos = form.numModulos, onNumModulos = { form = form.copy(numModulos = it) },
                nivelCurso = form.nivelCurso, onNivelCurso = { form = form.copy(nivelCurso = it) },
                certificado = form.certificado, onCertificado = { form = form.copy(certificado = it) },
                preco = form.preco, onPreco = { form = form.copy(preco = it) },
                precoOriginal = form.precoOriginal, onPrecoOriginal = { form = form.copy(precoOriginal = it) },
                capaUri = capaUri, onCapaChanged = { capaUri = it },
                destaque = form.destaque, onDestaque = { form = form.copy(destaque = it) },
                conteudos = conteudosList, onConteudosChanged = { conteudosList = it },
                isPmp = isPmpEstudio, verificado = verificadoEstudio,
                salvandoState = salvandoState,
            )
        }
        "livro" -> {
            ModalLivroScreen(
                isEditando = itemParaEditar != null,
                onCancelar = { resetForm() },
                onPublicar = {
                    if (form.titulo.isEmpty() || form.preco.isEmpty()) {
                        toast = "Preencha título e preço."
                        return@ModalLivroScreen
                    }
                    val editando = itemParaEditar
                    scope.launch {
                        try {
                            salvandoState = EstudioSalvandoState.FazendoUploadCapa(0f)
                            var capaPath: String? = form.capaUrl
                            if (capaUri != null) {
                                capaPath = uploadCapa(userId, capaUri!!, capaPath ?: "")
                                if (capaPath == null) {
                                    toast = "❌ Erro ao fazer upload da capa."
                                    salvandoState = EstudioSalvandoState.Idle
                                    return@launch
                                }
                            }
                            var pdfPath: String? = form.arquivoUrl
                            if (pdfUri != null) {
                                pdfPath = uploadPdf(userId, pdfUri!!, pdfPath ?: "")
                                if (pdfPath == null) {
                                    toast = "❌ Erro ao fazer upload do PDF."
                                    salvandoState = EstudioSalvandoState.Idle
                                    return@launch
                                }
                            }
                            salvandoState = EstudioSalvandoState.SalvandoMetadados
                            val ok: Boolean
                            val estudioIdCriado: String?
                            if (editando == null) {
                                val (success, newId) = criarItemEstudioAndroid(
                                    profissionalId = userId,
                                    titulo = form.titulo,
                                    descricao = form.descricao,
                                    tipo = "livro",
                                    preco = form.preco.toDoubleOrNull() ?: 0.0,
                                    precoOriginal = form.precoOriginal.toDoubleOrNull(),
                                    capaUrl = capaPath,
                                    arquivoUrl = pdfPath,
                                    autorLivro = form.autorLivro.ifBlank { null },
                                    isbn = form.isbn.ifBlank { null },
                                    numPaginas = form.numPaginas.toIntOrNull(),
                                    edicao = form.edicao.ifBlank { null },
                                    destaque = form.destaque,
                                )
                                ok = success
                                estudioIdCriado = newId
                            } else {
                                ok = editarItemEstudio(editando.id, EditarItemEstudioRequest(
                                    titulo = form.titulo,
                                    descricao = form.descricao.ifBlank { null },
                                    tipo = "livro",
                                    preco = form.preco.toDoubleOrNull() ?: 0.0,
                                    precoOriginal = form.precoOriginal.toDoubleOrNull(),
                                    capaUrl = capaPath,
                                    arquivoUrl = pdfPath,
                                    autorLivro = form.autorLivro.ifBlank { null },
                                    isbn = form.isbn.ifBlank { null },
                                    numPaginas = form.numPaginas.toIntOrNull(),
                                    edicao = form.edicao.ifBlank { null },
                                    destaque = form.destaque,
                                ))
                                estudioIdCriado = null
                            }
                            if (ok) {
                                salvandoState = EstudioSalvandoState.Sucesso
                                itens = getEstudioProfissionalAndroid(userId, filtroTipo)
                                resetForm()
                                toast = "✅ Livro salvo!"
                            } else {
                                salvandoState = EstudioSalvandoState.Idle
                                toast = "❌ Erro ao salvar."
                            }
                        } catch (e: Exception) {
                            salvandoState = EstudioSalvandoState.Erro(e.message ?: "Erro", "livro")
                            toast = "❌ Erro inesperado."
                        } finally {
                            if (salvandoState !is EstudioSalvandoState.Erro) {
                                kotlinx.coroutines.delay(500)
                                salvandoState = EstudioSalvandoState.Idle
                            }
                        }
                    }
                },
                titulo = form.titulo, onTitulo = { form = form.copy(titulo = it) },
                descricao = form.descricao, onDescricao = { form = form.copy(descricao = it) },
                autorLivro = form.autorLivro, onAutorLivro = { form = form.copy(autorLivro = it) },
                isbn = form.isbn, onIsbn = { form = form.copy(isbn = it) },
                numPaginas = form.numPaginas, onNumPaginas = { form = form.copy(numPaginas = it) },
                edicao = form.edicao, onEdicao = { form = form.copy(edicao = it) },
                preco = form.preco, onPreco = { form = form.copy(preco = it) },
                precoOriginal = form.precoOriginal, onPrecoOriginal = { form = form.copy(precoOriginal = it) },
                capaUri = capaUri, onCapaChanged = { capaUri = it },
                pdfUri = pdfUri, onPdfChanged = { pdfUri = it },
                destaque = form.destaque, onDestaque = { form = form.copy(destaque = it) },
                isPmp = isPmpEstudio, verificado = verificadoEstudio,
                salvandoState = salvandoState,
            )
        }
        "saas_digital" -> {
            ModalSaaSDigitalScreen(
                isEditando = itemParaEditar != null,
                onCancelar = { resetForm() },
                onPublicar = {
                    if (form.titulo.isEmpty()) {
                        toast = "Preencha o nome do produto."
                        return@ModalSaaSDigitalScreen
                    }
                    val editando = itemParaEditar
                    scope.launch {
                        try {
                            salvandoState = EstudioSalvandoState.FazendoUploadCapa(0f)
                            var capaPath: String? = form.capaUrl
                            if (capaUri != null) {
                                capaPath = uploadCapa(userId, capaUri!!, capaPath ?: "")
                                if (capaPath == null) {
                                    toast = "❌ Erro ao fazer upload da capa."
                                    salvandoState = EstudioSalvandoState.Idle
                                    return@launch
                                }
                            }
                            salvandoState = EstudioSalvandoState.SalvandoMetadados
                            val ok: Boolean
                            if (editando == null) {
                                val (success, _) = criarItemEstudioAndroid(
                                    profissionalId = userId,
                                    titulo = form.titulo,
                                    descricao = form.descricao,
                                    tipo = "saas_digital",
                                    preco = 0.0,
                                    capaUrl = capaPath,
                                    plataforma = form.plataforma.ifBlank { null },
                                    versaoProduto = form.versaoProduto.ifBlank { null },
                                    suporteIncluido = form.suporteIncluido,
                                    linkAcessoDigital = form.linkAcessoDigital.ifBlank { null },
                                    destaque = form.destaque,
                                )
                                ok = success
                            } else {
                                ok = editarItemEstudio(editando.id, EditarItemEstudioRequest(
                                    titulo = form.titulo,
                                    descricao = form.descricao.ifBlank { null },
                                    tipo = "saas_digital",
                                    preco = 0.0,
                                    capaUrl = capaPath,
                                    plataforma = form.plataforma.ifBlank { null },
                                    versaoProduto = form.versaoProduto.ifBlank { null },
                                    suporteIncluido = form.suporteIncluido,
                                    linkAcessoDigital = form.linkAcessoDigital.ifBlank { null },
                                    destaque = form.destaque,
                                ))
                            }
                            if (ok) {
                                salvandoState = EstudioSalvandoState.Sucesso
                                itens = getEstudioProfissionalAndroid(userId, filtroTipo)
                                resetForm()
                                toast = "✅ Produto salvo!"
                            } else {
                                salvandoState = EstudioSalvandoState.Idle
                                toast = "❌ Erro ao salvar."
                            }
                        } catch (e: Exception) {
                            salvandoState = EstudioSalvandoState.Erro(e.message ?: "Erro", "saas")
                            toast = "❌ Erro inesperado."
                        } finally {
                            if (salvandoState !is EstudioSalvandoState.Erro) {
                                kotlinx.coroutines.delay(500)
                                salvandoState = EstudioSalvandoState.Idle
                            }
                        }
                    }
                },
                titulo = form.titulo, onTitulo = { form = form.copy(titulo = it) },
                descricao = form.descricao, onDescricao = { form = form.copy(descricao = it) },
                plataforma = form.plataforma, onPlataforma = { form = form.copy(plataforma = it) },
                versaoProduto = form.versaoProduto, onVersaoProduto = { form = form.copy(versaoProduto = it) },
                suporteIncluido = form.suporteIncluido, onSuporteIncluido = { form = form.copy(suporteIncluido = it) },
                linkAcessoDigital = form.linkAcessoDigital, onLinkAcessoDigital = { form = form.copy(linkAcessoDigital = it) },
                capaUri = capaUri, onCapaChanged = { capaUri = it },
                destaque = form.destaque, onDestaque = { form = form.copy(destaque = it) },
                salvandoState = salvandoState,
            )
        }
    }

    itemParaExcluir?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!excluindo) itemParaExcluir = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Excluir item", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)) },
            text = {
                Column {
                    Text("Tem certeza que deseja excluir:", fontSize = 13.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("\"${item.titulo}\"", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp)).padding(10.dp),
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
                    colors = ButtonDefaults.buttonColors(containerColor = Urgente, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (excluindo) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
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

// ═══════════════════════════════════════════════════════════════════════
// MODAL AULA (com lista de conteúdos)
// ═══════════════════════════════════════════════════════════════════════
@Composable
fun ModalAulaScreen(
    isEditando: Boolean,
    onCancelar: () -> Unit,
    onPublicar: () -> Unit,
    titulo: String, onTitulo: (String) -> Unit,
    descricao: String, onDescricao: (String) -> Unit,
    materia: String, onMateria: (String) -> Unit,
    duracaoMinutos: String, onDuracaoMinutos: (String) -> Unit,
    nivelAula: String, onNivelAula: (String) -> Unit,
    preco: String, onPreco: (String) -> Unit,
    precoOriginal: String, onPrecoOriginal: (String) -> Unit,
    capaUri: Uri?, onCapaChanged: (Uri?) -> Unit,
    destaque: Boolean, onDestaque: (Boolean) -> Unit,
    conteudos: List<ConteudoItem>, onConteudosChanged: (List<ConteudoItem>) -> Unit,
    isPmp: Boolean, verificado: Boolean,
    salvandoState: EstudioSalvandoState,
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { onCapaChanged(it) }
    var mostrarAdicionarConteudo by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF0C2D6B)).padding(horizontal = 20.dp).padding(top = 52.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEditando) "Editar Aula" else "Nova Aula", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        TextButton(onClick = onCancelar) { Text("✕", color = Color.White, fontSize = 18.sp) }
                    }
                }
                item {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(titulo, onTitulo, label = { Text("Título da aula *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)), singleLine = true)
                        OutlinedTextField(descricao, onDescricao, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)), minLines = 3)
                        OutlinedTextField(materia, onMateria, label = { Text("Matéria / Disciplina") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)), singleLine = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(duracaoMinutos, onDuracaoMinutos, label = { Text("Duração (min)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)), singleLine = true)
                            OutlinedTextField(nivelAula, onNivelAula, label = { Text("Nível") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)), singleLine = true)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(preco, onPreco, label = { Text("Preço R$ *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)), singleLine = true)
                            OutlinedTextField(precoOriginal, onPrecoOriginal, label = { Text("Preço original") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)), singleLine = true)
                        }
                        preco.toDoubleOrNull()?.let { p -> if (p > 0) IndicadorTaxaPlataforma(p, isPmp, verificado) }
                        Text("Capa da aula", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { imagePicker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C2D6B), contentColor = Color.White), shape = RoundedCornerShape(8.dp)) { Text("Selecionar imagem") }
                            Spacer(Modifier.width(10.dp))
                            Text(if (capaUri != null) "✔ Imagem selecionada" else "Nenhuma", color = if (capaUri != null) Verde else Color(0xFF9CA3AF), fontSize = 12.sp)
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Vídeos da aula", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Text("${conteudos.size} vídeo(s) adicionado(s)", fontSize = 11.sp, color = Color(0xFF6B7280))
                            }
                            OutlinedButton(onClick = { mostrarAdicionarConteudo = true }, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Verde)) {
                                Text("+ Adicionar vídeo", color = Verde, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        conteudos.forEachIndexed { index, conteudo ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${index + 1}. ${conteudo.titulo.ifBlank { "(sem título)" }}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF111827))
                                        if (conteudo.descricao.isNotBlank()) Text(conteudo.descricao, fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 2)
                                        Text(if (conteudo.uploadUri != null) "🎬 Novo vídeo" else "🎬 Vídeo", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                                    }
                                    TextButton(onClick = { onConteudosChanged(conteudos.toMutableList().apply { removeAt(index) }) }) { Text("Remover", color = Urgente, fontSize = 11.sp) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Destacar no Estúdio", fontSize = 13.sp, color = Color(0xFF374151))
                            Switch(destaque, onDestaque, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFC49A2A)))
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancelar", fontSize = 14.sp) }
                Button(
                    onClick = onPublicar,
                    modifier = Modifier.weight(2f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    enabled = salvandoState == EstudioSalvandoState.Idle
                ) {
                    if (salvandoState != EstudioSalvandoState.Idle) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (isEditando) "Salvar alterações" else "Publicar Aula", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (mostrarAdicionarConteudo) {
        AdicionarConteudoAulaDialog(
            onConfirmar = { novo ->
                onConteudosChanged(conteudos + novo.copy(ordem = conteudos.size + 1))
                mostrarAdicionarConteudo = false
            },
            onDismiss = { mostrarAdicionarConteudo = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MODAL CURSO
// ═══════════════════════════════════════════════════════════════════════
@Composable
fun ModalCursoScreen(
    isEditando: Boolean,
    onCancelar: () -> Unit,
    onPublicar: () -> Unit,
    titulo: String, onTitulo: (String) -> Unit,
    descricao: String, onDescricao: (String) -> Unit,
    materia: String, onMateria: (String) -> Unit,
    cargaHorariaH: String, onCargaHorariaH: (String) -> Unit,
    numModulos: String, onNumModulos: (String) -> Unit,
    nivelCurso: String, onNivelCurso: (String) -> Unit,
    certificado: Boolean, onCertificado: (Boolean) -> Unit,
    preco: String, onPreco: (String) -> Unit,
    precoOriginal: String, onPrecoOriginal: (String) -> Unit,
    capaUri: Uri?, onCapaChanged: (Uri?) -> Unit,
    destaque: Boolean, onDestaque: (Boolean) -> Unit,
    conteudos: List<ConteudoItem>, onConteudosChanged: (List<ConteudoItem>) -> Unit,
    isPmp: Boolean, verificado: Boolean,
    salvandoState: EstudioSalvandoState,
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { onCapaChanged(it) }
    var mostrarAdicionarConteudo by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF0C2D6B)).padding(horizontal = 20.dp).padding(top = 52.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEditando) "Editar Curso" else "Novo Curso", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        TextButton(onClick = onCancelar) { Text("✕", color = Color.White, fontSize = 18.sp) }
                    }
                }
                item {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            titulo, onTitulo, label = { Text("Nome do curso *") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        OutlinedTextField(
                            descricao, onDescricao, label = { Text("Descrição do curso") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            minLines = 3
                        )
                        OutlinedTextField(
                            materia, onMateria, label = { Text("Área / Matéria") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                cargaHorariaH, onCargaHorariaH, label = { Text("Carga horária (h)") },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true
                            )
                            OutlinedTextField(
                                numModulos, onNumModulos, label = { Text("Nº de módulos") },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            nivelCurso, onNivelCurso, label = { Text("Nível do curso (Iniciante, Intermediário...)") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Emite certificado", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                                Text("O aluno recebe certificado ao concluir", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            }
                            Switch(certificado, onCertificado, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Verde))
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                preco, onPreco, label = { Text("Preço R$ *") },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true
                            )
                            OutlinedTextField(
                                precoOriginal, onPrecoOriginal, label = { Text("Preço original") },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true
                            )
                        }
                        preco.toDoubleOrNull()?.let { p ->
                            if (p > 0) IndicadorTaxaPlataforma(p, isPmp, verificado)
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Text("Capa do curso", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { imagePicker.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C2D6B), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Selecionar imagem") }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (capaUri != null) "✔ Imagem selecionada" else "Nenhuma",
                                color = if (capaUri != null) Verde else Color(0xFF9CA3AF), fontSize = 12.sp
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Conteúdos do curso", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Text("${conteudos.size} arquivo(s) adicionado(s)", fontSize = 11.sp, color = Color(0xFF6B7280))
                            }
                            OutlinedButton(
                                onClick = { mostrarAdicionarConteudo = true },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Verde)
                            ) {
                                Text("+ Adicionar", color = Verde, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        conteudos.forEachIndexed { index, conteudo ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${index + 1}. ${conteudo.titulo.ifBlank { "(sem título)" }}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF111827)
                                        )
                                        if (conteudo.descricao.isNotBlank()) {
                                            Text(conteudo.descricao, fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 2)
                                        }
                                        Text(
                                            when (conteudo.tipo) {
                                                "video" -> "🎬 Vídeo"
                                                "pdf" -> "📄 PDF"
                                                "documento" -> "📝 Documento"
                                                "link" -> "🔗 Link"
                                                else -> conteudo.tipo
                                            },
                                            fontSize = 11.sp,
                                            color = Color(0xFF9CA3AF)
                                        )
                                    }
                                    TextButton(onClick = {
                                        onConteudosChanged(conteudos.toMutableList().apply { removeAt(index) })
                                    }) {
                                        Text("Remover", color = Urgente, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Destacar no Estúdio", fontSize = 13.sp, color = Color(0xFF374151))
                            Switch(destaque, onDestaque, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFC49A2A)))
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(10.dp)) {
                    Text("Cancelar", fontSize = 14.sp)
                }
                Button(
                    onClick = onPublicar, modifier = Modifier.weight(2f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isEditando) "Salvar alterações" else "Publicar Curso", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (mostrarAdicionarConteudo) {
        AdicionarConteudoCursoDialog(
            onConfirmar = { novo ->
                onConteudosChanged(conteudos + novo.copy(ordem = conteudos.size + 1))
                mostrarAdicionarConteudo = false
            },
            onDismiss = { mostrarAdicionarConteudo = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MODAL LIVRO
// ═══════════════════════════════════════════════════════════════════════
@Composable
fun ModalLivroScreen(
    isEditando: Boolean,
    onCancelar: () -> Unit,
    onPublicar: () -> Unit,
    titulo: String, onTitulo: (String) -> Unit,
    descricao: String, onDescricao: (String) -> Unit,
    autorLivro: String, onAutorLivro: (String) -> Unit,
    isbn: String, onIsbn: (String) -> Unit,
    numPaginas: String, onNumPaginas: (String) -> Unit,
    edicao: String, onEdicao: (String) -> Unit,
    preco: String, onPreco: (String) -> Unit,
    precoOriginal: String, onPrecoOriginal: (String) -> Unit,
    capaUri: Uri?, onCapaChanged: (Uri?) -> Unit,
    pdfUri: Uri?, onPdfChanged: (Uri?) -> Unit,
    destaque: Boolean, onDestaque: (Boolean) -> Unit,
    isPmp: Boolean, verificado: Boolean,
    salvandoState: EstudioSalvandoState,
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { onCapaChanged(it) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { onPdfChanged(it) }

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF0C2D6B)).padding(horizontal = 20.dp).padding(top = 52.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEditando) "Editar Livro" else "Novo Livro", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        TextButton(onClick = onCancelar) { Text("✕", color = Color.White, fontSize = 18.sp) }
                    }
                }
                item {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            titulo, onTitulo, label = { Text("Título do livro *") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        OutlinedTextField(
                            descricao, onDescricao, label = { Text("Sinopse / Descrição") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            minLines = 4
                        )
                        OutlinedTextField(
                            autorLivro, onAutorLivro, label = { Text("Autor(a)") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                isbn, onIsbn, label = { Text("ISBN") },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true
                            )
                            OutlinedTextField(
                                edicao, onEdicao, label = { Text("Edição") },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            numPaginas, onNumPaginas, label = { Text("Número de páginas") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                preco, onPreco, label = { Text("Preço R$ *") },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true
                            )
                            OutlinedTextField(
                                precoOriginal, onPrecoOriginal, label = { Text("Preço original") },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                                singleLine = true
                            )
                        }
                        preco.toDoubleOrNull()?.let { p ->
                            if (p > 0) IndicadorTaxaPlataforma(p, isPmp, verificado)
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Text("Capa do livro", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { imagePicker.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C2D6B), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Selecionar capa") }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (capaUri != null) "✔ Capa selecionada" else "Nenhuma",
                                color = if (capaUri != null) Verde else Color(0xFF9CA3AF), fontSize = 12.sp
                            )
                        }
                        Text("Arquivo PDF do livro *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { pdfPicker.launch("application/pdf") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C2D6B), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Selecionar PDF") }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (pdfUri != null) "✔ PDF selecionado" else "Nenhum",
                                color = if (pdfUri != null) Verde else Color(0xFF9CA3AF), fontSize = 12.sp
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Destacar no Estúdio", fontSize = 13.sp, color = Color(0xFF374151))
                            Switch(destaque, onDestaque, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFC49A2A)))
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(10.dp)) {
                    Text("Cancelar", fontSize = 14.sp)
                }
                Button(
                    onClick = onPublicar, modifier = Modifier.weight(2f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isEditando) "Salvar alterações" else "Publicar Livro", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MODAL SaaS / Digital
// ═══════════════════════════════════════════════════════════════════════
@Composable
fun ModalSaaSDigitalScreen(
    isEditando: Boolean,
    onCancelar: () -> Unit,
    onPublicar: () -> Unit,
    titulo: String, onTitulo: (String) -> Unit,
    descricao: String, onDescricao: (String) -> Unit,
    plataforma: String, onPlataforma: (String) -> Unit,
    versaoProduto: String, onVersaoProduto: (String) -> Unit,
    suporteIncluido: Boolean, onSuporteIncluido: (Boolean) -> Unit,
    linkAcessoDigital: String, onLinkAcessoDigital: (String) -> Unit,
    capaUri: Uri?, onCapaChanged: (Uri?) -> Unit,
    destaque: Boolean, onDestaque: (Boolean) -> Unit,
    salvandoState: EstudioSalvandoState,
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { onCapaChanged(it) }

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF0C2D6B)).padding(horizontal = 20.dp).padding(top = 52.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isEditando) "Editar SaaS / Digital" else "Novo SaaS / Digital", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        TextButton(onClick = onCancelar) { Text("✕", color = Color.White, fontSize = 18.sp) }
                    }
                }
                item {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            titulo, onTitulo, label = { Text("Nome do produto *") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        OutlinedTextField(
                            descricao, onDescricao, label = { Text("Descrição") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            minLines = 3
                        )
                        OutlinedTextField(
                            plataforma, onPlataforma, label = { Text("Plataforma (ex: Web, Android, iOS)") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        OutlinedTextField(
                            versaoProduto, onVersaoProduto, label = { Text("Versão") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Suporte incluso", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                                Text("Atendimento ao usuário incluído", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            }
                            Switch(suporteIncluido, onSuporteIncluido, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Verde))
                        }
                        OutlinedTextField(
                            linkAcessoDigital, onLinkAcessoDigital, label = { Text("Link de acesso ao produto *") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                            singleLine = true
                        )
                        Text("Capa / Logo do produto", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { imagePicker.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C2D6B), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Selecionar imagem") }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (capaUri != null) "✔ Imagem selecionada" else "Nenhuma",
                                color = if (capaUri != null) Verde else Color(0xFF9CA3AF), fontSize = 12.sp
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("💡", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Produtos SaaS / Digital são disponibilizados gratuitamente pelo link informado.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF374151),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Destacar no Estúdio", fontSize = 13.sp, color = Color(0xFF374151))
                            Switch(destaque, onDestaque, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFC49A2A)))
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(10.dp)) {
                    Text("Cancelar", fontSize = 14.sp)
                }
                Button(
                    onClick = onPublicar, modifier = Modifier.weight(2f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isEditando) "Salvar alterações" else "Publicar Produto", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// DIALOG DE ADICIONAR CONTEÚDO AO CURSO
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun AdicionarConteudoCursoDialog(
    onConfirmar: (ConteudoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var titulo by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("video") }
    var urlExterna by remember { mutableStateOf("") }
    var uploadUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uploadUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Adicionar conteúdo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titulo, onValueChange = { titulo = it },
                    label = { Text("Título do conteúdo *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                    singleLine = true
                )
                OutlinedTextField(
                    value = descricao, onValueChange = { descricao = it },
                    label = { Text("Descrição (aula 1, módulo 2...)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                    minLines = 2
                )
                Text("Tipo de conteúdo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("video" to "🎬 Vídeo", "pdf" to "📄 PDF", "documento" to "📝 Doc", "link" to "🔗 Link")) { (t, label) ->
                        FilterChip(
                            selected = tipo == t,
                            onClick = { tipo = t },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Verde,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                if (tipo != "link") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                when (tipo) {
                                    "video" -> filePicker.launch("video/*")
                                    "pdf" -> filePicker.launch("application/pdf")
                                    else -> filePicker.launch("*/*")
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0C2D6B))
                        ) {
                            Text("Selecionar arquivo", fontSize = 12.sp, color = Color(0xFF0C2D6B))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (uploadUri != null) "✔ Selecionado" else "Nenhum",
                            fontSize = 11.sp,
                            color = if (uploadUri != null) Verde else Color(0xFF9CA3AF)
                        )
                    }
                }
                OutlinedTextField(
                    value = urlExterna, onValueChange = { urlExterna = it },
                    label = { Text(if (tipo == "link") "URL do link *" else "URL externa (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titulo.isNotBlank()) {
                        onConfirmar(
                            ConteudoItem(
                                titulo = titulo,
                                descricao = descricao,
                                tipo = tipo,
                                urlExterna = urlExterna.ifBlank { null },
                                uploadUri = uploadUri,
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Adicionar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFF6B7280), fontSize = 13.sp) }
        }
    )
}
@Composable
private fun AdicionarConteudoAulaDialog(
    onConfirmar: (ConteudoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var titulo by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var uploadUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uploadUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Adicionar vídeo à aula", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titulo, onValueChange = { titulo = it },
                    label = { Text("Título do vídeo *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                    singleLine = true
                )
                OutlinedTextField(
                    value = descricao, onValueChange = { descricao = it },
                    label = { Text("Descrição (ex: Introdução, Aula 2...)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Verde, unfocusedBorderColor = Color(0xFFE0E0E0)),
                    minLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { filePicker.launch("video/*") },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0C2D6B))
                    ) { Text("Selecionar vídeo", fontSize = 12.sp, color = Color(0xFF0C2D6B)) }
                    Spacer(Modifier.width(8.dp))
                    Text(if (uploadUri != null) "✔ Vídeo selecionado" else "Nenhum", fontSize = 11.sp, color = if (uploadUri != null) Verde else Color(0xFF9CA3AF))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (titulo.isNotBlank()) {
                    onConfirmar(ConteudoItem(titulo = titulo, descricao = descricao, tipo = "video", uploadUri = uploadUri))
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color.White), shape = RoundedCornerShape(8.dp)) { Text("Adicionar", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFF6B7280), fontSize = 13.sp) } }
    )
}