package br.com.brasiltupi.conecta

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.Verde
import kotlinx.coroutines.launch

@Composable
fun PagamentoEstudioScreen(
    itemId: String,
    onConfirmado: () -> Unit,
    onVoltar: () -> Unit,
) {
    // TODO: buscar detalhes do item (título, preço) via SupabaseClient
    // Por enquanto, use variáveis simuladas
    var titulo by remember { mutableStateOf("Produto do Estúdio") }
    var preco by remember { mutableStateOf(0.0) }
    var processando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💳 Pagamento via PIX", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Item: $titulo", fontSize = 16.sp)
        Text("Valor: R$ ${"%.2f".format(preco)}", fontSize = 24.sp, color = Verde)
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // Simular integração com gateway de pagamento
                processando = true
                scope.launch {
                    // Aqui você chamará sua função de pagamento real (PIX, cartão, etc.)
                    // Após confirmar pagamento, registrar a compra:
                    // val ok = registrarCompraEstudio(itemId, currentUserId)
                    // if (ok) onConfirmado()
                    kotlinx.coroutines.delay(2000) // simulando processamento
                    processando = false
                    onConfirmado()
                }
            },
            enabled = !processando,
            colors = ButtonDefaults.buttonColors(containerColor = Verde),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (processando) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Pagar agora", fontSize = 16.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onVoltar) {
            Text("Cancelar", fontSize = 14.sp)
        }
    }
}