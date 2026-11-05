package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * Repository with configurable answers: [localResult] for `forceRefresh = false` and [remoteResult] for `true`.
 * [remoteGate] suspends the forced calls until it completes. Counts calls per `forceRefresh` value.
 */
internal class FakeCollectorRepository(
    var hasCache: Boolean = false,
    var localResult: DataResult<List<Collector>, DataError> = DataResult.Success(emptyList()),
    var remoteResult: DataResult<List<Collector>, DataError> = DataResult.Success(emptyList()),
    var remoteGate: CompletableDeferred<Unit>? = null,
) : CollectorRepository {
    var localCalls = 0
        private set
    var remoteCalls = 0
        private set

    override suspend fun getCollectors(forceRefresh: Boolean): DataResult<List<Collector>, DataError> {
        if (!forceRefresh) {
            localCalls++
            return localResult
        }
        remoteCalls++
        remoteGate?.await()
        return remoteResult
    }

    override suspend fun hasCachedCollectors(): Boolean = hasCache
}
