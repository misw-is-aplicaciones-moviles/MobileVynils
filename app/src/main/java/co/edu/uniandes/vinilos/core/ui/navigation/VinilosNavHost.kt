package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
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
        screen<AlbumList> {
            PendingTabScreen(titleRes = R.string.album_list_title, messageRes = R.string.pending_this_sprint)
        }
        screen<ArtistList> {
            PendingTabScreen(titleRes = R.string.artist_list_title, messageRes = R.string.pending_next_sprint)
        }
        screen<CollectorList> {
            PendingTabScreen(titleRes = R.string.collector_list_title, messageRes = R.string.pending_next_sprint)
        }
    }
}

/**
 * Registra un destino y comparte su ámbito de animación con la pantalla, para que su título y su
 * contenido (Modifier.navContent) hagan fade through sin superponerse con los de la pantalla anterior.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable () -> Unit) {
    composable<T> {
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content() }
    }
}
