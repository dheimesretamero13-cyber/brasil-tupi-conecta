package br.com.brasiltupi.conecta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import br.com.brasiltupi.conecta.ui.theme.BrasilTupiConectaTheme

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
}

@Composable
fun AppNavigation() {
    var tela by remember { mutableStateOf("welcome") }
    var estudioProfId by remember { mutableStateOf("") }
    var currentUserId by remember { mutableStateOf("") }

    when (tela) {
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
            onConcluido = { tela = "welcome" },
        )
        "busca" -> BuscaScreen(
            onVoltar = { tela = "welcome" },
            onEstudio = { profId -> estudioProfId = profId; tela = "estudio-vitrine" }
        )
        "perfil-profissional" -> PerfilProfissionalScreen(
            onVoltar = { tela = "welcome" }
        )
        "perfil-cliente" -> PerfilClienteScreen(
            onVoltar = { tela = "welcome" }
        )
        "dashboard-profissional" -> DashboardProfissionalScreen(
            onSair = { tela = "welcome" },
            onEstudio = { tela = "estudio-dashboard" }
        )
        "dashboard-cliente" -> DashboardClienteScreen(
            onSair = { tela = "welcome" },
            onEstudio = { profId -> estudioProfId = profId; tela = "estudio-vitrine" }
        )
        "estudio-dashboard" -> EstudioDashboardScreen(
        userId = currentUserId ?: "",
        onVoltar = { tela = "dashboard-profissional" }
    )
        "estudio-busca" -> EstudioBuscaScreen(
            onVoltar = { tela = "welcome" }
        )
        "estudio-vitrine" -> EstudioVitrineScreen(
            profissionalId = estudioProfId,
            onVoltar = { tela = "busca" }
        )
    }
}