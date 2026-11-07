package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult

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
     * Returns one collector from the backend, joining `GET /collectors/:id` and `GET /collectors/:id/albums`.
     * Does not read or write the list cache. A failed call leaves that cache as it was.
     */
    suspend fun getCollector(
        id: Int,
        forceRefresh: Boolean = false,
    ): DataResult<CollectorDetail, DataError>
}
