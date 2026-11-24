package co.edu.uniandes.vinilos.ui.common

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.navOptions
import co.edu.uniandes.vinilos.R

/**
 * Navega con una acción de [originId] solo si esa pantalla sigue siendo el destino.
 * Un toque encolado al cambiar de pestaña llega cuando el destino ya es otro y esa acción no existe.
 */
internal fun NavController.navigateIfOn(
    originId: Int,
    actionId: Int,
    args: Bundle? = null,
) {
    if (currentDestination?.id != originId) return
    navigate(actionId, args)
}

/** Vacía la pila y abre el selector. Lo usan el chip de rol y la restauración sin rol. */
internal fun NavController.navigateToRoleSelector() {
    navigate(
        R.id.roleSelectFragment,
        null,
        navOptions {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        },
    )
}
