package co.edu.uniandes.vinilos.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Esquema de color propio de la marca (mockups v2). Sin color dinámico: la identidad crema y carbón
// es parte del diseño aprobado. La cabecera oscura usa inverseSurface / inverseOnSurface.
private val LightColors =
    lightColorScheme(
        primary = Charcoal,
        onPrimary = Cream,
        primaryContainer = Stone,
        onPrimaryContainer = Charcoal,
        secondary = Graphite,
        onSecondary = Cream,
        secondaryContainer = Stone,
        onSecondaryContainer = Charcoal,
        tertiary = Ash,
        onTertiary = Cream,
        background = Cream,
        onBackground = Charcoal,
        surface = Cream,
        onSurface = Charcoal,
        surfaceVariant = Stone,
        onSurfaceVariant = Graphite,
        surfaceContainer = Cream,
        surfaceContainerHigh = Stone,
        outline = Ash,
        outlineVariant = Line,
        inverseSurface = Charcoal,
        inverseOnSurface = Cream,
        inversePrimary = Cream,
        // Los mockups marcan los errores con el icono de alerta y borde carbón, no con rojo.
        error = Charcoal,
        onError = Cream,
        errorContainer = Stone,
        onErrorContainer = Charcoal,
    )

private val DarkColors =
    darkColorScheme(
        primary = Cream,
        onPrimary = Charcoal,
        primaryContainer = CharcoalHigh,
        onPrimaryContainer = Cream,
        secondary = Stone,
        onSecondary = Charcoal,
        secondaryContainer = CharcoalHigh,
        onSecondaryContainer = Cream,
        tertiary = Ash,
        onTertiary = Charcoal,
        background = Charcoal,
        onBackground = Cream,
        surface = Charcoal,
        onSurface = Cream,
        surfaceVariant = CharcoalHigh,
        onSurfaceVariant = Line,
        surfaceContainer = CharcoalRaised,
        surfaceContainerHigh = CharcoalHigh,
        outline = Ash,
        outlineVariant = Graphite,
        inverseSurface = CharcoalRaised,
        inverseOnSurface = Cream,
        inversePrimary = Charcoal,
        error = Cream,
        onError = Charcoal,
        errorContainer = CharcoalHigh,
        onErrorContainer = Cream,
    )

// Radios del kit: campos y tarjetas 14 a 22 dp, hojas y cabecera 26 a 32 dp.
private val VinilosShapes =
    Shapes(
        extraSmall = RoundedCornerShape(5.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

@Composable
fun VinilosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VinilosTypography,
        shapes = VinilosShapes,
        content = content,
    )
}
