package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.NewComment
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.repositories.AlbumRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * Repository with configurable answers: [localResult] for `forceRefresh = false` and [remoteResult] for `true`.
 * [remoteGate] suspends the forced calls until it completes. Counts calls per `forceRefresh` value.
 * [createAlbum] answers [createResult] and records every draft in [createdDrafts].
 * [addComment] answers [addCommentResult] after waiting for [addCommentGate] if set, and records every request.
 */
internal class FakeAlbumRepository(
    var hasCache: Boolean = false,
    var localResult: DataResult<List<Album>, DataError> = DataResult.Success(emptyList()),
    var remoteResult: DataResult<List<Album>, DataError> = DataResult.Success(emptyList()),
    var remoteGate: CompletableDeferred<Unit>? = null,
    var createResult: DataResult<Album, DataError> = DataResult.Failure(DataError.Network),
    var addTrackResult: DataResult<Track, DataError> = DataResult.Failure(DataError.Network),
) : AlbumRepository {
    var addCommentResult: DataResult<Comment, DataError> = DataResult.Failure(DataError.Network)
    var addCommentGate: CompletableDeferred<Unit>? = null
    val createdDrafts = mutableListOf<NewAlbum>()
    val addedTrackRequests = mutableListOf<Pair<Int, NewTrack>>()
    val addedCommentRequests = mutableListOf<Pair<Int, NewComment>>()

    var localCalls = 0
        private set
    var remoteCalls = 0
        private set
    var detailResult: DataResult<Album, DataError> = DataResult.Failure(DataError.Http(404))
    var detailGate: CompletableDeferred<Unit>? = null
    val detailRequests = mutableListOf<Pair<Int, Boolean>>()

    override suspend fun getAlbums(forceRefresh: Boolean): DataResult<List<Album>, DataError> {
        if (!forceRefresh) {
            localCalls++
            return localResult
        }
        remoteCalls++
        remoteGate?.await()
        return remoteResult
    }

    var associationCatalogResult: DataResult<List<Album>, DataError> = DataResult.Success(emptyList())
    var associationDetailResult: DataResult<Album, DataError> = DataResult.Failure(DataError.Http(404))
    var associationCatalogCalls = 0
        private set
    val associationDetailRequests = mutableListOf<Int>()
    val associationDetailResults = ArrayDeque<DataResult<Album, DataError>>()

    override suspend fun getAlbumsForAssociation(): DataResult<List<Album>, DataError> {
        associationCatalogCalls++
        remoteGate?.await()
        return associationCatalogResult
    }

    override suspend fun getAlbumForAssociation(albumId: Int): DataResult<Album, DataError> {
        associationDetailRequests += albumId
        detailGate?.await()
        return associationDetailResults.removeFirstOrNull() ?: associationDetailResult
    }

    var invalidationCount = 0
        private set

    override suspend fun invalidateCatalog() {
        invalidationCount++
        hasCache = false
    }

    override suspend fun hasCachedAlbums(): Boolean = hasCache

    override suspend fun getAlbum(
        id: Int,
        forceRefresh: Boolean,
    ): DataResult<Album, DataError> {
        detailRequests.add(id to forceRefresh)
        detailGate?.await()
        return detailResult
    }

    override suspend fun createAlbum(draft: NewAlbum): DataResult<Album, DataError> {
        createdDrafts += draft
        return createResult
    }

    override suspend fun addTrack(
        albumId: Int,
        draft: NewTrack,
    ): DataResult<Track, DataError> {
        addedTrackRequests += albumId to draft
        return addTrackResult
    }

    override suspend fun addComment(
        albumId: Int,
        draft: NewComment,
    ): DataResult<Comment, DataError> {
        addedCommentRequests += albumId to draft
        addCommentGate?.await()
        return addCommentResult
    }
}
