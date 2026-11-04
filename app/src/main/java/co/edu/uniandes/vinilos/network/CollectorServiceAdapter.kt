package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.network.dto.toModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal interface CollectorServiceAdapter {
    /**
     * Returns every collector from the backend, in no particular order. A Retrofit, IO or JSON exception reaches the
     * Repository, which converts it.
     */
    suspend fun getCollectors(): List<Collector>
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
}
