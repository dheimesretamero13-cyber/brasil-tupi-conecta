package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConteudoCursoScreen(
    estudioId: String,
    tituloItem: String,
    repository: ContentRepository,
    onVoltar: () -> Unit,
    onAbrirVideo: (url: String, titulo: String) -> Unit,
    onAbrirPdf: (url: String, titulo: String) -> Unit,
) {
    var conteudos by remember { mutableStateOf<List<ConteudoEstudioResponse>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(estudioId) {
        try {
            // Verificar se o usuário tem acesso (opcional, mas desejável)
            if (currentUserId != null && !verificarAcessoProduto(currentUserId!!, estudioId)) {
                erro = "Você não tem acesso a este conteúdo."
                carregando = false
                return@LaunchedEffect
            }
            val lista = buscarConteudosEstudio(estudioId)
            conteudos = lista
            carregando = false
        } catch (e: Exception) {
            erro = "Erro ao carregar conteúdos."
            carregando = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tituloItem, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8F7F4))) {
            when {
                carregando -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Verde)
                    }
                }
                erro != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⚠️", fontSize = 40.sp)
                        Text(erro!!, fontSize = 14.sp, color = Color(0xFF374151))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { /* tentar novamente */ }) {
                            Text("Tentar novamente")
                        }
                    }
                }
                conteudos.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum conteúdo disponível.", fontSize = 14.sp, color = Color(0xFF6B7280))
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(conteudos) { index, conteudo ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        val urlAssinada = if (conteudo.storagePath != null) {
                                            repository.gerarUrlTemporaria("estudio-assets", conteudo.storagePath)
                                        } else conteudo.urlExterna

                                        if (urlAssinada == null) return@launch

                                        when (conteudo.tipo) {
                                            "video" -> onAbrirVideo(urlAssinada, conteudo.titulo)
                                            "pdf" -> onAbrirPdf(urlAssinada, conteudo.titulo)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        when (conteudo.tipo) {
                                            "video" -> "🎬"
                                            "pdf" -> "📄"
                                            "documento" -> "📝"
                                            "link" -> "🔗"
                                            else -> "📁"
                                        },
                                        fontSize = 24.sp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${index + 1}. ${conteudo.titulo}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF111827)
                                        )
                                        if (!conteudo.descricao.isNullOrBlank()) {
                                            Text(
                                                conteudo.descricao,
                                                fontSize = 12.sp,
                                                color = Color(0xFF6B7280),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text("→", color = Verde, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}