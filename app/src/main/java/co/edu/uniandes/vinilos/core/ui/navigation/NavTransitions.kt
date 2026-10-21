package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

// Transiciones de navegación de 300 ms basadas en las pautas de movimiento de Material 3.
// Regla para evitar parpadeos: siempre hay una pantalla opaca debajo. La pantalla que queda
// detrás se mantiene visible mientras la de arriba se anima; nunca se desvanecen las dos a la vez,
// porque el fondo del contenedor (carbón) asomaría entre ellas.
// - Entre pestañas de la barra inferior: la nueva aparece por fundido sobre la anterior. Cabecera y
//   hoja son iguales en ambas, así que solo se funden el título y el contenido.
// - Hacia un detalle o formulario: la nueva entra desde la derecha con fundido sobre la anterior;
//   al volver, la de arriba sale hacia la derecha y se desvanece descubriendo la de abajo.

internal const val TRANSITION_MILLIS = 300
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

private fun fadeIntoView(): EnterTransition = fadeIn(tween(TRANSITION_MILLIS, easing = LinearOutSlowInEasing))

private fun slideInFromEnd(): EnterTransition =
    slideInHorizontally(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { width ->
        (width * SHARED_AXIS_OFFSET_FRACTION).toInt()
    } + fadeIn(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing))

private fun slideOutToEnd(): ExitTransition =
    slideOutHorizontally(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { width ->
        (width * SHARED_AXIS_OFFSET_FRACTION).toInt()
    } + fadeOut(tween(TRANSITION_MILLIS, easing = FastOutSlowInEasing))

internal val vinilosEnterTransition: NavTransitionScope.() -> EnterTransition = {
    if (isTabSwitch()) fadeIntoView() else slideInFromEnd()
}

internal val vinilosExitTransition: NavTransitionScope.() -> ExitTransition = {
    holdUntilFinished()
}

internal val vinilosPopEnterTransition: NavTransitionScope.() -> EnterTransition = {
    if (isTabSwitch()) fadeIntoView() else visibleImmediately()
}

internal val vinilosPopExitTransition: NavTransitionScope.() -> ExitTransition = {
    if (isTabSwitch()) holdUntilFinished() else slideOutToEnd()
}
