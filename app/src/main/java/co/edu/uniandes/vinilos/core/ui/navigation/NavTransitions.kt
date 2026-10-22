package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

// Transiciones de navegación de 300 ms basadas en las pautas de movimiento de Material 3.
// Dos reglas:
// 1. Sin parpadeo: siempre hay una pantalla opaca debajo; nunca se desvanecen las dos a la vez,
//    porque el fondo del contenedor (carbón) asomaría entre ellas.
// 2. Sin textos superpuestos: el título y el contenido de cada pantalla (marcados con
//    Modifier.navContent()) hacen "fade through": los salientes se desvanecen en 0 a 150 ms y los
//    entrantes aparecen en 150 a 300 ms.
// - Entre pestañas: la pantalla nueva se coloca opaca encima a los 150 ms. Su cabecera y su hoja son
//   idénticas a las de la anterior, así que el cambio de fondo no se ve; solo cambian título y contenido.
// - Hacia un detalle o formulario: la nueva entra desde la derecha con fundido sobre la anterior;
//   al volver, la de arriba sale hacia la derecha y se desvanece descubriendo la de abajo.

internal const val TRANSITION_MILLIS = 300
private const val HALF_MILLIS = TRANSITION_MILLIS / 2
private const val SHARED_AXIS_OFFSET_FRACTION = 0.1f

private typealias NavTransitionScope = AnimatedContentTransitionScope<NavBackStackEntry>

private fun NavDestination.isTopLevel(): Boolean = TopLevelDestination.entries.any { hasRoute(it.routeClass) }

/** Cambio entre dos pestañas de la barra inferior, en cualquier sentido. */
private fun NavTransitionScope.isTabSwitch(): Boolean =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

/** La pantalla sigue opaca durante toda la transición y desaparece al final, ya tapada. */
private fun holdUntilFinished(): ExitTransition = fadeOut(snap(delayMillis = TRANSITION_MILLIS))

/** La pantalla está opaca desde el primer cuadro (queda debajo de la que sale). */
private fun visibleImmediately(): EnterTransition = fadeIn(snap())

/** La pantalla nueva se coloca opaca a mitad de la transición, cuando el contenido anterior ya se fue. */
private fun appearAtHalf(): EnterTransition = fadeIn(snap(delayMillis = HALF_MILLIS))

private fun slideInFromEnd(): EnterTransition =
    slideInHorizontally(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { width ->
        (width * SHARED_AXIS_OFFSET_FRACTION).toInt()
    } + fadeIn(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing))

private fun slideOutToEnd(): ExitTransition =
    slideOutHorizontally(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { width ->
        (width * SHARED_AXIS_OFFSET_FRACTION).toInt()
    } + fadeOut(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing))

internal val vinilosEnterTransition: NavTransitionScope.() -> EnterTransition = {
    if (isTabSwitch()) appearAtHalf() else slideInFromEnd()
}

internal val vinilosExitTransition: NavTransitionScope.() -> ExitTransition = {
    holdUntilFinished()
}

internal val vinilosPopEnterTransition: NavTransitionScope.() -> EnterTransition = {
    if (isTabSwitch()) appearAtHalf() else visibleImmediately()
}

internal val vinilosPopExitTransition: NavTransitionScope.() -> ExitTransition = {
    if (isTabSwitch()) holdUntilFinished() else slideOutToEnd()
}

/** Ámbito de animación del destino actual, para que el contenido de la pantalla haga su propio fade through. */
internal val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marca el título y el contenido de una pantalla: se desvanecen en la primera mitad de la transición
 * y aparecen en la segunda, así nunca se superponen los de dos pantallas. Fuera del NavHost (vistas
 * previas, pruebas de componentes) no hace nada.
 */
@Composable
fun Modifier.navContent(): Modifier {
    val scope = LocalNavAnimatedScope.current ?: return this
    return with(scope) {
        this@navContent.animateEnterExit(
            enter = fadeIn(tween(HALF_MILLIS, delayMillis = HALF_MILLIS, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(HALF_MILLIS, easing = FastOutLinearInEasing)),
        )
    }
}
