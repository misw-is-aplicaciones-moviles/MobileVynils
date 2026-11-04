package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.database.dao.CollectorDao
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.database.toModel
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.network.CollectorServiceAdapter
import co.edu.uniandes.vinilos.network.networkRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CollectorRepositoryImpl(
    private val collectorDao: CollectorDao,
    private val serviceAdapter: CollectorServiceAdapter,
    private val defaultDispatcher: CoroutineDispatcher,
) : CollectorRepository {
    private val mutex = Mutex()

    override suspend fun hasCachedCollectors(): Boolean = collectorDao.count() > 0

    override suspend fun getCollectors(forceRefresh: Boolean): DataResult<List<Collector>, DataError> =
        mutex.withLock {
            val cached = if (forceRefresh) emptyList() else readAll()
            if (cached.isNotEmpty()) {
                DataResult.Success(cached)
            } else {
                when (val result = networkRequest { serviceAdapter.getCollectors() }) {
                    is DataResult.Success -> {
                        collectorDao.replaceAll(withContext(defaultDispatcher) { result.value.map { it.toEntity() } })
                        DataResult.Success(readAll())
                    }

                    // Nothing was fetched completely, so Room is not touched.
                    is DataResult.Failure -> {
                        result
                    }
                }
            }
        }

    // The backend has no pagination, so the size of the list has no limit: it is mapped off the main thread.
    private suspend fun readAll(): List<Collector> {
        val rows = collectorDao.getAll()
        return withContext(defaultDispatcher) { rows.map { it.toModel() } }
    }
}
