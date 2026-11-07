package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorAlbum
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.PerformerSummary

/** A collector for the tests; only what a test cares about needs to be given. */
internal fun collector(
    id: Int,
    name: String,
    email: String = "$id@example.com",
    albums: Int = 0,
    favorites: Int = 0,
) = Collector(id = id, name = name, email = email, albumCount = albums, favoriteCount = favorites)

/** A remote detail. Counts follow the collections, as the detail mapper does. */
internal fun collectorDetail(
    id: Int = 100,
    name: String = "Manolo Bellon",
    email: String = "manollo@caracol.com.co",
    telephone: String = "3502457896",
    favorites: List<PerformerSummary> = emptyList(),
    albums: List<CollectorAlbum> = emptyList(),
) = CollectorDetail(
    collector = collector(id, name, email, albums = albums.size, favorites = favorites.size),
    telephone = telephone,
    favorites = favorites,
    albums = albums,
)
