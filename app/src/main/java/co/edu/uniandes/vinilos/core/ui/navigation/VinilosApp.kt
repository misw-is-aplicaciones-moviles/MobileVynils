package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Contenedor de la app: barra inferior de tres pestañas y el grafo de navegación.
 * Cada pestaña conserva su pila y su estado al cambiar entre ellas (saveState / restoreState).
 */
@Composable
fun VinilosApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = backStackEntry?.destination.toTopLevelDestination()

    Scaffold(
        modifier = modifier,
        // La cabecera oscura de cada pantalla se extiende bajo la barra de estado.
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            VinilosBottomBar(
                selected = selected,
                onSelect = { destination -> navController.navigateToTopLevel(destination) },
            )
        },
    ) { innerPadding ->
        VinilosNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}

private fun NavDestination?.toTopLevelDestination(): TopLevelDestination =
    TopLevelDestination.entries.firstOrNull { destination ->
        this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
    } ?: TopLevelDestination.ALBUMS

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
