package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.uniandes.vinilos.core.ui.theme.VinilosTheme

/**
 * Barra inferior del kit v2: tres pestañas con icono, etiqueta en mayúsculas y un punto bajo la activa.
 * Con [centerAction] dibuja la joroba central que aloja la acción principal (por ejemplo el "+" de HU07).
 */
@Composable
fun VinilosBottomBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    centerAction: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { BottomBarLabel(text = stringResource(destination.labelRes), selected = isSelected) },
                    alwaysShowLabel = true,
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSurface,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.outline,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent,
                        ),
                )
            }
        }
        if (centerAction != null) {
            Surface(
                modifier =
                    Modifier
                        .offset(y = -HumpHeight)
                        .width(HumpWidth)
                        .height(HumpHeight + 1.dp),
                shape = HumpShape,
                color = MaterialTheme.colorScheme.surface,
            ) {}
            Box(modifier = Modifier.offset(y = -HumpHeight - 6.dp)) { centerAction() }
        }
    }
}

@Composable
private fun BottomBarLabel(
    text: String,
    selected: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Kit v2: Montserrat 10/12, espaciado 1; SemiBold inactiva y Bold activa.
        Text(
            text = text.toUpperCase(Locale.current),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 1.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                ),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier =
                Modifier
                    .size(4.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    ),
        )
    }
}

private val HumpWidth = 160.dp
private val HumpHeight = 34.dp

/** Curva suave que sube desde los extremos hasta una meseta central, como la joroba del kit v2. */
private object HumpShape : Shape {
    private const val SHOULDER = 0.28f
    private const val TOP_START = 0.36f
    private const val TOP_END = 0.64f
    private const val SHOULDER_END = 0.72f

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val path =
            Path().apply {
                moveTo(0f, h)
                cubicTo(w * SHOULDER, h, w * SHOULDER, 0f, w * TOP_START, 0f)
                lineTo(w * TOP_END, 0f)
                cubicTo(w * SHOULDER_END, 0f, w * SHOULDER_END, h, w, h)
                close()
            }
        return Outline.Generic(path)
    }
}

@Preview(showBackground = true)
@Composable
private fun VinilosBottomBarPreview() {
    VinilosTheme { VinilosBottomBar(selected = TopLevelDestination.ARTISTS, onSelect = {}) }
}
