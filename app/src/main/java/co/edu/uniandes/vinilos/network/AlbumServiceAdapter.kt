package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.network.dto.toCreatedTrackModel
import co.edu.uniandes.vinilos.network.dto.toModel
import co.edu.uniandes.vinilos.network.dto.toRequest
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException

internal interface AlbumServiceAdapter {
    /** Returns the albums from the backend; throws the Retrofit, IO or JSON exceptions that the Repository converts. */
    suspend fun getAlbums(): List<Album>

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
}

internal class AlbumServiceAdapterImpl(
    apiProvider: () -> VinilosApiService,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) : AlbumServiceAdapter {
    // The first access creates the HTTP cache on disk, so it must never run on the main thread (StrictMode).
    private val api by lazy(apiProvider)

    override suspend fun getAlbums(): List<Album> {
        val dtos = withContext(ioDispatcher) { api.getAlbums() }
        return withContext(defaultDispatcher) { dtos.map { it.toModel() } }
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
}
