package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult

/** Source of album data; the catalog is cached in Room and expected remote failures are returned as values. */
interface AlbumRepository {
    /** Returns the albums from Room, or from the backend when Room is empty or [forceRefresh] is true. */
    suspend fun getAlbums(forceRefresh: Boolean = false): DataResult<List<Album>, DataError>

    /** Tells whether Room already holds albums, so the screen can show them before the network answers. */
    suspend fun hasCachedAlbums(): Boolean

    /** Returns complete detail from the backend, revalidating the HTTP cache when [forceRefresh] is true. */
    suspend fun getAlbum(
        id: Int,
        forceRefresh: Boolean = false,
    ): DataResult<Album, DataError>
}
