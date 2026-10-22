package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.People
import androidx.compose.ui.graphics.vector.ImageVector
import co.edu.uniandes.vinilos.R
import kotlin.reflect.KClass

/** Las tres pestañas de la barra inferior, en el orden y con los iconos del kit v2 (disco, micrófono, personas). */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    ALBUMS(AlbumList, AlbumList::class, R.string.nav_albums, Icons.Filled.Album, Icons.Filled.Album),
    ARTISTS(ArtistList, ArtistList::class, R.string.nav_artists, Icons.Filled.Mic, Icons.Outlined.MicNone),
    COLLECTORS(
        CollectorList,
        CollectorList::class,
        R.string.nav_collectors,
        Icons.Filled.People,
        Icons.Outlined.People,
    ),
}
