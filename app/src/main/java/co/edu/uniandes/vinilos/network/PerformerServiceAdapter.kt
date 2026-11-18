package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.network.dto.mergePerformers
import co.edu.uniandes.vinilos.network.dto.toDetailModel
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

internal interface PerformerServiceAdapter {
    /**
     * Returns every musician and band from the backend as typed performers, in no particular order.
     * The backend has no single endpoint, so this calls two; if either fails the whole call fails with the
     * Retrofit, IO or JSON exception that the Repository converts. It never returns half of the list.
     */
    suspend fun getPerformers(forceRefresh: Boolean = false): List<Performer>

    /** Sends one association POST; errors after sending may still require checking the remote result. */
    suspend fun addAlbum(
        performerId: Int,
        type: PerformerType,
        albumId: Int,
    )

    /** Reads one remote detail, without replacing the cached list; retries revalidate HTTP. */
    suspend fun getPerformer(
        id: Int,
        type: PerformerType,
        forceRefresh: Boolean = false,
    ): PerformerDetail
}

internal class PerformerServiceAdapterImpl(
    apiProvider: () -> VinilosApiService,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) : PerformerServiceAdapter {
    // The first access creates the HTTP cache on disk, so it must never run on the main thread (StrictMode).
    private val api by lazy(apiProvider)

    // The two requests are independent, so they go out together; a failure cancels the other one.
    override suspend fun getPerformers(forceRefresh: Boolean): List<Performer> =
        coroutineScope {
            val cacheControl = if (forceRefresh) "no-cache" else null
            val musicians = async(ioDispatcher) { api.getMusicians(cacheControl) }
            val bands = async(ioDispatcher) { api.getBands(cacheControl) }
            val musicianDtos = musicians.await()
            val bandDtos = bands.await()
            withContext(defaultDispatcher) { mergePerformers(musicianDtos, bandDtos) }
        }

    override suspend fun getPerformer(
        id: Int,
        type: PerformerType,
        forceRefresh: Boolean,
    ): PerformerDetail {
        require(id > 0) { "performerId must be positive" }
        val cacheControl = if (forceRefresh) "no-cache" else null
        return when (type) {
            PerformerType.MUSICIAN -> {
                val response = withContext(ioDispatcher) { api.getMusician(id, cacheControl) }
                withContext(defaultDispatcher) { response.requiredBody().toDetailModel() }
            }

            PerformerType.BAND -> {
                val response = withContext(ioDispatcher) { api.getBand(id, cacheControl) }
                withContext(defaultDispatcher) { response.requiredBody().toDetailModel() }
            }
        }
    }

    override suspend fun addAlbum(
        performerId: Int,
        type: PerformerType,
        albumId: Int,
    ) {
        require(performerId > 0) { "performerId must be positive" }
        require(albumId > 0) { "albumId must be positive" }
        val response =
            withContext(ioDispatcher) {
                when (type) {
                    PerformerType.MUSICIAN -> api.addAlbumToMusician(performerId, albumId)
                    PerformerType.BAND -> api.addAlbumToBand(performerId, albumId)
                }
            }
        withContext(defaultDispatcher) {
            if (response.requiredBody().id != albumId) {
                throw JsonParseException("Unexpected associated album id")
            }
        }
    }

    private fun <T> Response<T>.requiredBody(): T {
        if (!isSuccessful) throw HttpException(this)
        return body() ?: throw JsonParseException("Missing performer response")
    }
}
