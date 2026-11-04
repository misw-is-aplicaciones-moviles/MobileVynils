package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.repositories.PerformerRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * Repository with configurable answers: [localResult] for `forceRefresh = false` and [remoteResult] for `true`.
 * [remoteGate] suspends the forced calls until it completes. Counts calls per `forceRefresh` value.
 */
internal class FakePerformerRepository(
    var hasCache: Boolean = false,
    var localResult: DataResult<List<Performer>, DataError> = DataResult.Success(emptyList()),
    var remoteResult: DataResult<List<Performer>, DataError> = DataResult.Success(emptyList()),
    var remoteGate: CompletableDeferred<Unit>? = null,
) : PerformerRepository {
    var localCalls = 0
        private set
    var remoteCalls = 0
        private set

    override suspend fun getPerformers(forceRefresh: Boolean): DataResult<List<Performer>, DataError> {
        if (!forceRefresh) {
            localCalls++
            return localResult
        }
        remoteCalls++
        remoteGate?.await()
        return remoteResult
    }

    override suspend fun hasCachedPerformers(): Boolean = hasCache
}
