package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * Repository with configurable answers: [localResult] for `forceRefresh = false` and [remoteResult] for `true`.
 * [remoteGate] suspends the forced calls until it completes. Counts calls per `forceRefresh` value.
 */
internal class FakeAlbumRepository(
    var hasCache: Boolean = false,
    var localResult: DataResult<List<Album>, DataError> = DataResult.Success(emptyList()),
    var remoteResult: DataResult<List<Album>, DataError> = DataResult.Success(emptyList()),
    var remoteGate: CompletableDeferred<Unit>? = null,
) : AlbumRepository {
    var localCalls = 0
        private set
    var remoteCalls = 0
        private set

    override suspend fun getAlbums(forceRefresh: Boolean): DataResult<List<Album>, DataError> {
        if (!forceRefresh) {
            localCalls++
            return localResult
        }
        remoteCalls++
        remoteGate?.await()
        return remoteResult
    }

    override suspend fun hasCachedAlbums(): Boolean = hasCache
}
