package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.database.dao.PerformerDao
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.database.toModels
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.network.PerformerServiceAdapter
import co.edu.uniandes.vinilos.network.networkRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PerformerRepositoryImpl(
    private val performerDao: PerformerDao,
    private val serviceAdapter: PerformerServiceAdapter,
) : PerformerRepository {
    private val mutex = Mutex()

    override suspend fun hasCachedPerformers(): Boolean = performerDao.count() > 0

    override suspend fun getPerformers(forceRefresh: Boolean): DataResult<List<Performer>, DataError> =
        mutex.withLock {
            val cached = if (forceRefresh) emptyList() else performerDao.getAll()
            if (cached.isNotEmpty()) {
                DataResult.Success(cached.toModels())
            } else {
                when (val result = networkRequest { serviceAdapter.getPerformers() }) {
                    is DataResult.Success -> {
                        performerDao.replaceAll(result.value.map { it.toEntity() })
                        DataResult.Success(performerDao.getAll().toModels())
                    }

                    // Nothing was fetched completely, so Room is not touched.
                    is DataResult.Failure -> {
                        result
                    }
                }
            }
        }
}
