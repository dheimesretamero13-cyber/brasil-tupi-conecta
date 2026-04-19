package br.com.brasiltupi.conecta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*

@Composable
fun WelcomeScreen(
    onEntrar: () -> Unit,
    onCadastro: () -> Unit,
    onBuscar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Azul, Color(0xFF0A2255), Color(0xFF081A3D))
                )
            )
    ) {
        // Grid decorativo de fundo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Verde.copy(alpha = 0.15f), Color.Transparent),
                        radius = 800f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Logo e tagline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Brasil Tupi",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "Conecta",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = DouradoMedio,
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Você é visto pelo que você é.",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Não pelo que vende.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            // Cards de números
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(modifier = Modifier.weight(1f), numero = "45min", label = "Atendimento\nurgente")
                StatCard(modifier = Modifier.weight(1f), numero = "PMP", label = "Profissionais\nverificados")
                StatCard(modifier = Modifier.weight(1f), numero = "100%", label = "Avaliações\nreais")
            }

            // Botões
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Buscar profissional
                Button(
                    onClick = onBuscar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DouradoMedio,
                        contentColor = Ink
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Buscar profissional",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Criar perfil
                Button(
                    onClick = onCadastro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Verde,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Criar meu perfil",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Já tenho conta
                OutlinedButton(
                    onClick = onEntrar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp, Color.White.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Já tenho conta",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "© 2025 Brasil Tupi Conecta",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, numero: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = numero,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = DouradoMedio
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}