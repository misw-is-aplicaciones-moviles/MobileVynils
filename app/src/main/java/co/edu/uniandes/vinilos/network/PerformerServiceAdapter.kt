package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.network.dto.mergePerformers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal interface PerformerServiceAdapter {
    /**
     * Returns every musician and band from the backend as typed performers, in no particular order.
     * The backend has no single endpoint, so this calls two; if either fails the whole call fails with the
     * Retrofit, IO or JSON exception that the Repository converts. It never returns half of the list.
     */
    suspend fun getPerformers(): List<Performer>
}

internal class PerformerServiceAdapterImpl(
    apiProvider: () -> VinilosApiService,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) : PerformerServiceAdapter {
    // The first access creates the HTTP cache on disk, so it must never run on the main thread (StrictMode).
    private val api by lazy(apiProvider)

    // The two requests are independent, so they go out together; a failure cancels the other one.
    override suspend fun getPerformers(): List<Performer> =
        coroutineScope {
            val musicians = async(ioDispatcher) { api.getMusicians() }
            val bands = async(ioDispatcher) { api.getBands() }
            val musicianDtos = musicians.await()
            val bandDtos = bands.await()
            withContext(defaultDispatcher) { mergePerformers(musicianDtos, bandDtos) }
        }
}
