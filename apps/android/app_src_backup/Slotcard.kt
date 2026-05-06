package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// SlotCard.kt  · Fase 2.3
//
// Componente único reutilizável por:
//  • AgendaProfissionalScreen — exibe com opções de deletar/ver detalhes
//  • AgendaClienteScreen     — exibe com botão de reservar
//
// Variantes controladas pelo parâmetro [modo]:
//  • Modo.PROFISSIONAL — slot proprio, clicavel para deletar/detalhes
//  • Modo.CLIENTE      — slot disponivel, clicavel para reservar
//  • Modo.RESERVADO    — slot já ocupado (ambos os papeis)
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.brasiltupi.conecta.ui.theme.*

enum class SlotCardModo { PROFISSIONAL, CLIENTE, RESERVADO }

@Composable
fun SlotCard(
    slot:         SlotDisponibilidade,
    modo:         SlotCardModo,
    isReservando: Boolean   = false,  // loading durante chamada RPC de reserva
    onClick:      () -> Unit = {},
    modifier:     Modifier   = Modifier,
) {
    // Cor de fundo animada conforme estado do slot
    val corFundo by animateColorAsState(
        targetValue = when {
            isReservando    -> Azul.copy(alpha = 0.12f)
            slot.isBooked   -> Color(0xFFF3F3F3)
            modo == SlotCardModo.PROFISSIONAL -> VerdeClaro
            else            -> AzulClaro
        },
        animationSpec = tween(durationMillis = 300),
        label = "slot_bg",
    )

    val corBorda by animateColorAsState(
        targetValue = when {
            isReservando  -> Azul
            slot.isBooked -> Color(0xFFE0E0E0)
            modo == SlotCardModo.PROFISSIONAL -> Verde
            else          -> Azul
        },
        animationSpec = tween(durationMillis = 300),
        label = "slot_border",
    )

    val clickable = !slot.isBooked || modo == SlotCardModo.PROFISSIONAL

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(corFundo)
            .then(
                if (clickable) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Horário — destaque principal
                Text(
                    text       = "${slot.horaInicio}–${slot.horaFim}",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = when {
                        slot.isBooked -> InkMuted
                        modo == SlotCardModo.PROFISSIONAL -> Verde
                        else          -> Azul
                    },
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Status
                Text(
                    text     = when {
                        isReservando  -> "Reservando..."
                        slot.isBooked -> if (modo == SlotCardModo.PROFISSIONAL) "Reservado" else "Indisponível"
                        else          -> if (modo == SlotCardModo.PROFISSIONAL) "Disponível" else "Disponível"
                    },
                    fontSize = 11.sp,
                    color    = when {
                        isReservando  -> Azul
                        slot.isBooked -> InkMuted
                        else          -> if (modo == SlotCardModo.PROFISSIONAL) Verde else Azul
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Indicador visual direito
            when {
                isReservando -> {
                    CircularProgressIndicator(
                        color       = Azul,
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                slot.isBooked && modo == SlotCardModo.PROFISSIONAL -> {
                    Text("🔒", fontSize = 14.sp)
                }
                slot.isBooked -> {
                    Text("✗", fontSize = 14.sp, color = InkMuted, fontWeight = FontWeight.Bold)
                }
                modo == SlotCardModo.PROFISSIONAL -> {
                    Text("⋯", fontSize = 18.sp, color = Verde, fontWeight = FontWeight.Bold)
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .background(Azul, RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("Agendar", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Borda colorida à esquerda
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-10).dp)
                .width(3.dp)
                .fillMaxHeight()
                .background(corBorda, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
        )
    }
}