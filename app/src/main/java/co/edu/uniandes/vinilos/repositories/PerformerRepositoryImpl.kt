package co.edu.uniandes.vinilos.repositories

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.PerformerDao
import co.edu.uniandes.vinilos.database.nocaseKey
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.database.toModels
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.network.PerformerServiceAdapter
import co.edu.uniandes.vinilos.network.networkRequest
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Artists from Room, revalidated against the backend. Room is only a cache: when it fails, the backend is the source
 * (an unreadable cache counts as empty and a failed write still returns what the backend sent), so a database
 * error never closes the app. Network failures are still reported as before.
 */
internal class PerformerRepositoryImpl(
    private val performerDao: PerformerDao,
    private val serviceAdapter: PerformerServiceAdapter,
    private val defaultDispatcher: CoroutineDispatcher,
    private val albumRepository: AlbumRepository,
) : PerformerRepository {
    private val mutex = Mutex()

    // Guarded by the same mutex as catalog loads and writes, so an older refresh cannot clear this flag.
    private var associationNeedsRefresh = false

    override suspend fun hasCachedPerformers(): Boolean =
        mutex.withLock {
            try {
                !associationNeedsRefresh && performerDao.count() > 0
            } catch (_: SQLiteException) {
                false
            }
        }

    override suspend fun getPerformer(
        id: Int,
        type: PerformerType,
        forceRefresh: Boolean,
    ): DataResult<PerformerDetail, DataError> {
        require(id > 0) { "performerId must be positive" }
        val revalidate = mutex.withLock { forceRefresh || associationNeedsRefresh }
        return networkRequest {
            serviceAdapter.getPerformer(id, type, revalidate).also {
                if (it.performer.id != id || it.performer.type != type) {
                    throw JsonParseException("Unexpected performer identity")
                }
            }
        }
    }

    override suspend fun addMusicianToBand(
        bandId: Int,
        musicianId: Int,
    ): DataResult<PerformerSummary, DataError> {
        require(bandId > 0) { "bandId must be positive" }
        require(musicianId > 0) { "musicianId must be positive" }
        val result = networkRequest { serviceAdapter.addMusicianToBand(bandId, musicianId) }
        if (result is DataResult.Success) {
            // The POST runs outside the mutex; the cache update waits for a refresh in progress, so a list fetched
            // before the POST is stored first and this change is applied on top of it instead of being undone.
            mutex.withLock {
                try {
                    performerDao.applyMembership(musicianId, bandId)
                } catch (_: SQLiteException) {
                    // Room is only a cache: the next refresh brings the membership from the backend.
                }
            }
        }
        return result
    }

    override suspend fun getPerformers(forceRefresh: Boolean): DataResult<List<Performer>, DataError> =
        mutex.withLock {
            val revalidate = forceRefresh || associationNeedsRefresh
            val cached = if (revalidate) emptyList() else readCacheOrEmpty()
            if (cached.isNotEmpty()) {
                DataResult.Success(cached)
            } else {
                when (val result = networkRequest { serviceAdapter.getPerformers(revalidate) }) {
                    is DataResult.Success -> {
                        DataResult.Success(store(result.value))
                    }

                    // Nothing was fetched completely, so Room is not touched.
                    is DataResult.Failure -> {
                        result
                    }
                }
            }
        }

    override suspend fun addAlbum(
        performerId: Int,
        type: PerformerType,
        albumId: Int,
    ): DataResult<Unit, DataError> {
        require(performerId > 0) { "performerId must be positive" }
        require(albumId > 0) { "albumId must be positive" }
        return mutex.withLock {
            // A lost response may still have changed the backend: the next read must revalidate.
            associationNeedsRefresh = true
            try {
                networkRequest { serviceAdapter.addAlbum(performerId, type, albumId) }
            } finally {
                // Waits for any older album refresh, then invalidates even after cancellation or a lost response.
                withContext(NonCancellable) { albumRepository.invalidateCatalog() }
            }
        }
    }

    private suspend fun readCacheOrEmpty(): List<Performer> =
        try {
            readAll()
        } catch (_: SQLiteException) {
            emptyList()
        }

    // Saves the complete answer and returns it as Room orders it. If Room cannot save it or read it back, the answer
    // is still shown, sorted the same way, so a disk or database error does not change what the screen gets.
    private suspend fun store(performers: List<Performer>): List<Performer> =
        try {
            performerDao.replaceAll(withContext(defaultDispatcher) { performers.map { it.toEntity() } })
            readAll().also { associationNeedsRefresh = false }
        } catch (_: SQLiteException) {
            withContext(defaultDispatcher) { performers.sortedWith(ROOM_ORDER) }
        }

    // The backend has no pagination, so the size of the list has no limit: it is mapped off the main thread.
    // Room runs the query on its own executor but resumes the caller, which for the artist screen is Main.
    private suspend fun readAll(): List<Performer> {
        val rows = performerDao.getAll()
        return withContext(defaultDispatcher) { rows.toModels() }
    }

    private companion object {
        // Same order as PerformerDao.getAll(): `name COLLATE NOCASE, id`.
        val ROOM_ORDER: Comparator<Performer> = compareBy<Performer>({ nocaseKey(it.name) }, { it.id })
    }
}
