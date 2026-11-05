package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.network.CollectorServiceAdapter

/** Adapter that returns [collectors] or throws [failure], and counts its calls. */
internal class FakeCollectorServiceAdapter(
    var collectors: List<Collector> = emptyList(),
    var failure: Throwable? = null,
) : CollectorServiceAdapter {
    var callCount = 0
        private set

    override suspend fun getCollectors(): List<Collector> {
        callCount++
        failure?.let { throw it }
        return collectors
    }
}
