package co.edu.uniandes.vinilos.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

private const val DOT_ALPHA = 0.06f
private val DotSpacing = 7.dp
private val DotRadius = 0.6.dp

/**
 * Retícula de puntos sutil del fondo crema de los mockups v2 ("papel"). Se dibuja detrás del
 * contenido con el color de texto al 6 %, así que respeta el tema claro y oscuro.
 */
@Composable
fun Modifier.paperTexture(): Modifier {
    val dotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = DOT_ALPHA)
    return drawBehind {
        val step = DotSpacing.toPx()
        val radius = DotRadius.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}
