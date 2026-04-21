package br.com.brasiltupi.conecta

// ─── Android / Compose ────────────────────────────────────────────────────────
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

// ─── Material 3 ───────────────────────────────────────────────────────────────
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults


// ─── Runtime ──────────────────────────────────────────────────────────────────
import androidx.compose.runtime.Composable

// ─── UI helpers ───────────────────────────────────────────────────────────────
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ─── App theme ────────────────────────────────────────────────────────────────
import br.com.brasiltupi.conecta.ui.theme.Verde
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
// ─────────────────────────────────────────────────────────────────────────────
// PagamentoScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagamentoScreen(
    onVoltar: () -> Unit,
    onConcluido: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Planos & Assinatura",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onVoltar) {
                        Text("← Voltar", fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Escolha seu plano",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            PlanoCard(
                titulo = "Plano Mensal",
                preco = "R$ 29,90/mês",
                onAssinar = onConcluido,
            )

            PlanoCard(
                titulo = "Plano Anual",
                preco = "R$ 249,90/ano  ·  economize 30%",
                onAssinar = onConcluido,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlanoCard  (componente privado reutilizável)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlanoCard(
    titulo: String,
    preco: String,
    onAssinar: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = preco,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAssinar,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Verde,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(text = "Assinar")
            }
        }
    }
}