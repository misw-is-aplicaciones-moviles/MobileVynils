package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.NewComment
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.network.dto.toAssociationModel
import co.edu.uniandes.vinilos.network.dto.toCreatedCommentModel
import co.edu.uniandes.vinilos.network.dto.toCreatedTrackModel
import co.edu.uniandes.vinilos.network.dto.toModel
import co.edu.uniandes.vinilos.network.dto.toRequest
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException

internal interface AlbumServiceAdapter {
    /** Returns the albums from the backend; throws the Retrofit, IO or JSON exceptions that the Repository converts. */
    suspend fun getAlbums(forceRefresh: Boolean = false): List<Album>

    /** Revalidates full album relationships, without falling back to the Room catalog summary. */
    suspend fun getAlbumsForAssociation(): List<Album>

    /** Revalidates one album and requires explicit, valid performer relationships. */
    suspend fun getAlbumForAssociation(albumId: Int): Album

    /** Returns complete detail; forces HTTP revalidation when requested by a retry. */
    suspend fun getAlbum(
        id: Int,
        forceRefresh: Boolean = false,
    ): Album

    /** Creates [draft] in the backend and returns the saved album; throws like [getAlbums]. */
    suspend fun createAlbum(draft: NewAlbum): Album

    /** Adds [draft] to [albumId] and returns the backend-assigned track; throws like [getAlbums]. */
    suspend fun addTrack(
        albumId: Int,
        draft: NewTrack,
    ): Track

    /** Publishes [draft] on [albumId] and returns the saved comment with its author; throws like [getAlbums]. */
    suspend fun addComment(
        albumId: Int,
        draft: NewComment,
    ): Comment
}

internal class AlbumServiceAdapterImpl(
    apiProvider: () -> VinilosApiService,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) : AlbumServiceAdapter {
    // The first access creates the HTTP cache on disk, so it must never run on the main thread (StrictMode).
    private val api by lazy(apiProvider)

    override suspend fun getAlbums(forceRefresh: Boolean): List<Album> {
        val dtos = withContext(ioDispatcher) { api.getAlbums(if (forceRefresh) "no-cache" else null) }
        return withContext(defaultDispatcher) { dtos.map { it.toModel() } }
    }

    override suspend fun getAlbumsForAssociation(): List<Album> {
        val response = withContext(ioDispatcher) { api.getAlbumsForAssociation() }
        if (!response.isSuccessful) throw HttpException(response)
        return withContext(defaultDispatcher) {
            val albums = response.body() ?: throw JsonParseException("Missing association catalog")
            albums.map { dto ->
                (dto ?: throw JsonParseException("Null association album")).toAssociationModel()
            }
        }
    }

    override suspend fun getAlbumForAssociation(albumId: Int): Album {
        require(albumId > 0) { "albumId must be positive" }
        val response = withContext(ioDispatcher) { api.getAlbum(albumId, "no-cache") }
        if (!response.isSuccessful) throw HttpException(response)
        return withContext(defaultDispatcher) {
            val album = response.body()?.toAssociationModel() ?: throw JsonParseException("Missing association album")
            if (album.id != albumId) throw JsonParseException("Unexpected association album id")
            album
        }
    }

    override suspend fun getAlbum(
        id: Int,
        forceRefresh: Boolean,
    ): Album {
        val response = withContext(ioDispatcher) { api.getAlbum(id, if (forceRefresh) "no-cache" else null) }
        if (!response.isSuccessful) throw HttpException(response)
        return withContext(defaultDispatcher) {
            response.body()?.toModel() ?: throw JsonParseException("Missing album response")
        }
    }

    override suspend fun createAlbum(draft: NewAlbum): Album {
        val request = draft.toRequest()
        val dto = withContext(ioDispatcher) { api.createAlbum(request) }
        return withContext(defaultDispatcher) { dto.toModel() }
    }

    override suspend fun addTrack(
        albumId: Int,
        draft: NewTrack,
    ): Track {
        val request = draft.toRequest()
        val dto = withContext(ioDispatcher) { api.addTrack(albumId, request) }
        dto.album?.let { album ->
            val responseAlbumId = album.id ?: throw JsonParseException("Missing track album id")
            if (responseAlbumId != albumId) throw JsonParseException("Unexpected track album id")
        }
        return withContext(defaultDispatcher) { dto.toCreatedTrackModel() }
    }

    override suspend fun addComment(
        albumId: Int,
        draft: NewComment,
    ): Comment {
        val request = draft.toRequest()
        val dto = withContext(ioDispatcher) { api.addComment(albumId, request) }
        dto.album?.let { album ->
            val responseAlbumId = album.id ?: throw JsonParseException("Missing comment album id")
            if (responseAlbumId != albumId) throw JsonParseException("Unexpected comment album id")
        }
        val comment = withContext(defaultDispatcher) { dto.toCreatedCommentModel() }
        if (comment.author?.collectorId != draft.collectorId) throw JsonParseException("Unexpected comment collector")
        return comment
    }
}
