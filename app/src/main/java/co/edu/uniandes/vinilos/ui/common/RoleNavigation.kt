package co.edu.uniandes.vinilos.ui.common

import androidx.navigation.NavController
import androidx.navigation.navOptions
import co.edu.uniandes.vinilos.R

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
