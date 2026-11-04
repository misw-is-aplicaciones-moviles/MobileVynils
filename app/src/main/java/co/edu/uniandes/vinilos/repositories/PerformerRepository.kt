package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerType

/** Source of artist data; the list is cached in Room and expected remote failures are returned as values. */
interface PerformerRepository {
    /**
     * Returns the musicians and bands by name, from Room or, when Room is empty or [forceRefresh] is true, from the
     * backend. If either backend call fails the result is a failure and Room keeps what it had.
     */
    suspend fun getPerformers(forceRefresh: Boolean = false): DataResult<List<Performer>, DataError>

    /** Tells whether Room already holds artists, so the screen can show them before the network answers. */
    suspend fun hasCachedPerformers(): Boolean

    /** Reads one remote detail, without replacing the cached list; retries revalidate HTTP. */
    suspend fun getPerformer(
        id: Int,
        type: PerformerType,
        forceRefresh: Boolean = false,
    ): DataResult<PerformerDetail, DataError>
}
