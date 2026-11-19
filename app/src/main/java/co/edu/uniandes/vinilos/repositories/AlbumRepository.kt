package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.NewComment
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.Track

/** Source of album data; the catalog is cached in Room and expected remote failures are returned as values. */
interface AlbumRepository {
    /** Returns Room summaries, revalidating when empty, invalidated by a write, or [forceRefresh] is true. */
    suspend fun getAlbums(forceRefresh: Boolean = false): DataResult<List<Album>, DataError>

    /** Marks summaries stale after a relationship write, including an uncertain response. */
    suspend fun invalidateCatalog()

    /** Reads a revalidated catalog with complete relationships; missing relationships are a serialization error. */
    suspend fun getAlbumsForAssociation(): DataResult<List<Album>, DataError>

    /** Revalidates the requested album and its relationships; never uses the Room summary. */
    suspend fun getAlbumForAssociation(albumId: Int): DataResult<Album, DataError>

    /** Tells whether Room holds usable summaries, so the screen can show them before the network answers. */
    suspend fun hasCachedAlbums(): Boolean

    /** Returns complete detail from the backend, revalidating the HTTP cache when [forceRefresh] is true. */
    suspend fun getAlbum(
        id: Int,
        forceRefresh: Boolean = false,
    ): DataResult<Album, DataError>

    /** Creates [draft] in the backend and keeps the saved album in Room. */
    suspend fun createAlbum(draft: NewAlbum): DataResult<Album, DataError>

    /** Adds [draft] to [albumId] in the backend and caches the returned track in Room. */
    suspend fun addTrack(
        albumId: Int,
        draft: NewTrack,
    ): DataResult<Track, DataError>

    /** Publishes [draft] on [albumId] and remembers its author, so the detail can name it without another request. */
    suspend fun addComment(
        albumId: Int,
        draft: NewComment,
    ): DataResult<Comment, DataError>
}
