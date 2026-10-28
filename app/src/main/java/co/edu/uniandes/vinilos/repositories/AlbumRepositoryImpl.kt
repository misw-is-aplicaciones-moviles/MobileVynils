package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.database.toModel
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.network.AlbumServiceAdapter
import co.edu.uniandes.vinilos.network.networkRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AlbumRepositoryImpl(
    private val albumDao: AlbumDao,
    private val serviceAdapter: AlbumServiceAdapter,
) : AlbumRepository {
    private val mutex = Mutex()

    override suspend fun hasCachedAlbums(): Boolean = albumDao.getAll().isNotEmpty()

    override suspend fun getAlbums(forceRefresh: Boolean): DataResult<List<Album>, DataError> =
        mutex.withLock {
            val cached = if (forceRefresh) emptyList() else albumDao.getAll()
            if (cached.isNotEmpty()) {
                DataResult.Success(cached.map { it.toModel() })
            } else {
                when (val result = networkRequest { serviceAdapter.getAlbums() }) {
                    is DataResult.Success -> {
                        albumDao.replaceAll(result.value.map { it.toEntity() })
                        DataResult.Success(albumDao.getAll().map { it.toModel() })
                    }

                    is DataResult.Failure -> {
                        result
                    }
                }
            }
        }
}
