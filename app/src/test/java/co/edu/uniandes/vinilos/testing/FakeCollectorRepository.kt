package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.CommentAuthor
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewCollectorAlbum
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.repositories.CollectorRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * Repository with configurable answers: [localResult] for `forceRefresh = false` and [remoteResult] for `true`.
 * [remoteGate] suspends the forced calls until it completes. Counts calls per `forceRefresh` value.
 * [getCommentAuthors] answers [authorsResult] (Room only) or, with `revalidate`, [refreshedAuthorsResult] after
 * waiting for [refreshGate] if set; a successful map is narrowed to the requested ids, like the real one. Every call
 * is recorded in [authorsRequests].
 */
internal class FakeCollectorRepository(
    var hasCache: Boolean = false,
    var localResult: DataResult<List<Collector>, DataError> = DataResult.Success(emptyList()),
    var remoteResult: DataResult<List<Collector>, DataError> = DataResult.Success(emptyList()),
    var remoteGate: CompletableDeferred<Unit>? = null,
    var authorsResult: DataResult<Map<Int, CommentAuthor>, DataError> = DataResult.Success(emptyMap()),
    var refreshedAuthorsResult: DataResult<Map<Int, CommentAuthor>, DataError> = DataResult.Success(emptyMap()),
    var refreshGate: CompletableDeferred<Unit>? = null,
) : CollectorRepository {
    var localCalls = 0
        private set
    var remoteCalls = 0
        private set
    var detailCalls = 0
        private set
    var lastDetailId: Int? = null
        private set
    var lastDetailRefresh: Boolean? = null
        private set
    var detailResult: DataResult<CollectorDetail, DataError> = DataResult.Failure(DataError.Network)
    var detailGate: CompletableDeferred<Unit>? = null
    val authorsRequests = mutableListOf<Pair<Set<Int>, Boolean>>()

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

    override suspend fun getCommentAuthors(
        commentIds: Set<Int>,
        revalidate: Boolean,
    ): DataResult<Map<Int, CommentAuthor>, DataError> {
        authorsRequests += commentIds to revalidate
        if (revalidate) refreshGate?.await()
        return when (val result = if (revalidate) refreshedAuthorsResult else authorsResult) {
            is DataResult.Success -> DataResult.Success(result.value.filterKeys(commentIds::contains))
            is DataResult.Failure -> result
        }
    }

    override suspend fun getCollector(
        id: Int,
        forceRefresh: Boolean,
    ): DataResult<CollectorDetail, DataError> {
        detailCalls++
        lastDetailId = id
        lastDetailRefresh = forceRefresh
        detailGate?.await()
        return detailResult
    }

    override suspend fun addAlbum(
        collectorId: Int,
        albumId: Int,
        draft: NewCollectorAlbum,
    ): DataResult<Unit, DataError> {
        addAlbumCalls++
        lastAddAlbumRequest = Triple(collectorId, albumId, draft)
        return addAlbumResult
    }

    var addAlbumCalls = 0
        private set
    var lastAddAlbumRequest: Triple<Int, Int, NewCollectorAlbum>? = null
        private set
    var addAlbumResult: DataResult<Unit, DataError> = DataResult.Success(Unit)

    override suspend fun addFavorite(
        collectorId: Int,
        performerId: Int,
        type: PerformerType,
    ): DataResult<Unit, DataError> {
        addFavoriteCalls++
        lastAddFavorite = Triple(collectorId, performerId, type)
        addFavoriteGate?.await()
        return addFavoriteResult
    }

    var addFavoriteCalls = 0
        private set
    var lastAddFavorite: Triple<Int, Int, PerformerType>? = null
        private set
    var addFavoriteResult: DataResult<Unit, DataError> = DataResult.Success(Unit)
    var addFavoriteGate: CompletableDeferred<Unit>? = null
}
