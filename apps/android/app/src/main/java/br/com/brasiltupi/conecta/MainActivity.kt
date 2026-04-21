package br.com.brasiltupi.conecta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import br.com.brasiltupi.conecta.ui.theme.BrasilTupiConectaTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrasilTupiConectaTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }
}

@Composable
fun AppNavigation() {
    var tela by remember { mutableStateOf("welcome") }
    var estudioProfId  by remember { mutableStateOf("") }
    var chatOutroId    by remember { mutableStateOf("") }
    var chatOutroNome  by remember { mutableStateOf("") }
    var chatDestino    by remember { mutableStateOf("dashboard-cliente") }
    when (tela) {
        "chat" -> ChatScreen(
            outroId   = chatOutroId,
            outroNome = chatOutroNome,
            onVoltar  = { tela = chatDestino }
        )
        "welcome" -> WelcomeScreen(
            onEntrar   = { tela = "login" },
            onCadastro = { tela = "cadastro" },
            onBuscar   = { tela = "busca" },
        )
        "login" -> LoginScreen(
            onVoltar             = { tela = "welcome" },
            onEntrarProfissional = { tela = "dashboard-profissional" },
            onEntrarCliente      = { tela = "dashboard-cliente" },
            onCadastro           = { tela = "cadastro" },
        )
        "cadastro" -> CadastroScreen(
            onVoltar    = { tela = "welcome" },
            onConcluido = {
                tela = if (br.com.brasiltupi.conecta.currentUserId != null) "onboarding-check" else "welcome"
            },
        )
        "onboarding-check" -> {
            // Resolve destino correto via LaunchedEffect sem bloquear UI
            var destino by remember { mutableStateOf("") }
            LaunchedEffect(br.com.brasiltupi.conecta.currentUserId) {
                val uid = br.com.brasiltupi.conecta.currentUserId
                    ?: run { tela = "welcome"; return@LaunchedEffect }
                val perfil = getPerfilAndroid(uid)
                destino = when {
                    perfil?.tipo == "profissional_certificado" || perfil?.tipo == "profissional_liberal" -> "onboarding-profissional"
                    else -> "dashboard-cliente"
                }
            }
            if (destino.isNotEmpty()) {
                LaunchedEffect(destino) { tela = destino }
            } else {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = br.com.brasiltupi.conecta.ui.theme.Verde)
                }
            }
        }
        "onboarding-profissional" -> OnboardingProfissionalScreen(
            onConcluido = { tela = "dashboard-profissional" },
            onPular     = { tela = "dashboard-profissional" },

        )
        "busca" -> BuscaScreen(
            onVoltar  = { tela = "welcome" },
            onEstudio = { profId -> estudioProfId = profId; tela = "estudio-vitrine" },
            onPagar   = { tela = "pagamento" }
        )
        "perfil-profissional" -> PerfilProfissionalScreen(
            onVoltar = { tela = "dashboard-profissional" },
            userId   = br.com.brasiltupi.conecta.currentUserId ?: ""
        )
        "perfil-cliente" -> PerfilClienteScreen(
            onVoltar = { tela = "dashboard-cliente" },
            userId   = br.com.brasiltupi.conecta.currentUserId ?: ""
        )
        "dashboard-profissional" -> DashboardProfissionalScreen(
            onSair = { tela = "welcome" },
            onEstudio = { tela = "estudio-dashboard" },
            onPerfil = { tela = "perfil-profissional" }
        )
        "dashboard-cliente" -> DashboardClienteScreen(
            onSair    = { tela = "welcome" },
            onEstudio = { profId -> estudioProfId = profId; tela = "estudio-vitrine" },
            onPerfil  = { tela = "perfil-cliente" },
            onChat    = { outroId, outroNome ->
                chatOutroId   = outroId
                chatOutroNome = outroNome
                chatDestino   = "dashboard-cliente"
                tela          = "chat"
            }
        )
        "estudio-dashboard" -> EstudioDashboardScreen(
            userId   = br.com.brasiltupi.conecta.currentUserId ?: "",
            onVoltar = { tela = "dashboard-profissional" }
        )
        "estudio-busca" -> EstudioBuscaScreen(
            onVoltar = { tela = "welcome" }
        )
        "estudio-vitrine" -> EstudioVitrineScreen(
            profissionalId = estudioProfId,
            onVoltar = { tela = "busca" }
        )
        "pagamento" -> PagamentoScreen(
            onVoltar    = { tela = "busca" },
            onConcluido = { tela = "busca" }
        )
    }
}