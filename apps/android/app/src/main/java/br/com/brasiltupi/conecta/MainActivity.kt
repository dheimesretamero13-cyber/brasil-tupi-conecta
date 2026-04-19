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

    when (tela) {
        "welcome" -> WelcomeScreen(
            onEntrar   = { tela = "welcome" },
            onCadastro = { tela = "welcome" },
            onBuscar   = { tela = "welcome" },
        )
    }
}