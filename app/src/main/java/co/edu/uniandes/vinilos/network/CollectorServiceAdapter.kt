package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.network.dto.toDetail
import co.edu.uniandes.vinilos.network.dto.toModel
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

internal interface CollectorServiceAdapter {
    /**
     * Returns every collector from the backend, in no particular order. A Retrofit, IO or JSON exception reaches the
     * Repository, which converts it.
     */
    suspend fun getCollectors(): List<Collector>

    /**
     * Returns one collector and the albums of their collection. The two calls go out together; if either fails, the
     * other is cancelled and the exception reaches the Repository.
     */
    suspend fun getCollector(
        id: Int,
        forceRefresh: Boolean = false,
    ): CollectorDetail
}

internal class CollectorServiceAdapterImpl(
    apiProvider: () -> VinilosApiService,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) : CollectorServiceAdapter {
    // The first access creates the HTTP cache on disk, so it must never run on the main thread (StrictMode).
    private val api by lazy(apiProvider)

    override suspend fun getCollectors(): List<Collector> {
        val collectors = withContext(ioDispatcher) { api.getCollectors() }
        return withContext(defaultDispatcher) { collectors.map { it.toModel() } }
    }

    override suspend fun getCollector(
        id: Int,
        forceRefresh: Boolean,
    ): CollectorDetail {
        require(id > 0) { "collectorId must be positive" }
        val cacheControl = if (forceRefresh) "no-cache" else null
        // The two requests are independent, so they go out together; a failure cancels the other one.
        return coroutineScope {
            val collector = async(ioDispatcher) { api.getCollector(id, cacheControl).requiredBody() }
            val albums = async(ioDispatcher) { api.getCollectorAlbums(id, cacheControl).requiredBody() }
            withContext(defaultDispatcher) { collector.await().toDetail(albums.await()) }
        }
    }

    private fun <T> Response<T>.requiredBody(): T {
        if (!isSuccessful) throw HttpException(this)
        return body() ?: throw JsonParseException("Missing collector response")
    }
}
