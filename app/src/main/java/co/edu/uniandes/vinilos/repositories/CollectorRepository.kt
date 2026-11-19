package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.CommentAuthor
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewCollectorAlbum

/** Source of collector data; the list is cached in Room and expected remote failures are returned as values. */
interface CollectorRepository {
    /**
     * Returns the collectors by name, from Room or, when Room is empty or [forceRefresh] is true, from the backend.
     * If the backend call fails the result is a failure and Room keeps what it had.
     */
    suspend fun getCollectors(forceRefresh: Boolean = false): DataResult<List<Collector>, DataError>

    /** Tells whether Room already holds collectors, so the screen can show them before the network answers. */
    suspend fun hasCachedCollectors(): Boolean

    /**
     * Returns who wrote the comments in [commentIds] that are known, by comment id, from Room. With [revalidate], it asks
     * `GET /collectors` (which also refreshes the list) when any of them is missing or when this session has not yet
     * checked Room against the backend, whose recreated database reuses comment ids; the backend answer wins. Without
     * [revalidate] it never makes a request. A failed request leaves Room as it was.
     */
    suspend fun getCommentAuthors(
        commentIds: Set<Int>,
        revalidate: Boolean,
    ): DataResult<Map<Int, CommentAuthor>, DataError>

    /**
     * Returns one collector from the backend, joining `GET /collectors/:id` and `GET /collectors/:id/albums`.
     * Does not read or write the list cache. A failed call leaves that cache as it was.
     */
    suspend fun getCollector(
        id: Int,
        forceRefresh: Boolean = false,
    ): DataResult<CollectorDetail, DataError>

    /** Adds an album to a collector's collection. Returns the saved association. */
    suspend fun addAlbum(
        collectorId: Int,
        albumId: Int,
        draft: NewCollectorAlbum,
    ): DataResult<Unit, DataError>
}
