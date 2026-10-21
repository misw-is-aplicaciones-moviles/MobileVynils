package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.edu.uniandes.vinilos.R

/** Grafo de navegación único de la app. Cada historia registra aquí sus destinos. */
@Composable
fun VinilosNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AlbumList,
        modifier = modifier,
        enterTransition = vinilosEnterTransition,
        exitTransition = vinilosExitTransition,
        popEnterTransition = vinilosPopEnterTransition,
        popExitTransition = vinilosPopExitTransition,
    ) {
        composable<AlbumList> {
            PendingTabScreen(titleRes = R.string.album_list_title, messageRes = R.string.pending_this_sprint)
        }
        composable<ArtistList> {
            PendingTabScreen(titleRes = R.string.artist_list_title, messageRes = R.string.pending_next_sprint)
        }
        composable<CollectorList> {
            PendingTabScreen(titleRes = R.string.collector_list_title, messageRes = R.string.pending_next_sprint)
        }
    }
}
