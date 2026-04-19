package br.com.brasiltupi.conecta.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary          = Verde,
    onPrimary        = Color.White,
    secondary        = Azul,
    onSecondary      = Color.White,
    tertiary         = Dourado,
    onTertiary       = Color.White,
    error            = Urgente,
    onError          = Color.White,
    background       = SurfaceWarm,
    onBackground     = Ink,
    surface          = Surface,
    onSurface        = Ink,
    surfaceVariant   = SurfaceOff,
    onSurfaceVariant = InkSoft,
)

@Composable
fun BrasilTupiConectaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = Typography,
        content     = content
    )
}