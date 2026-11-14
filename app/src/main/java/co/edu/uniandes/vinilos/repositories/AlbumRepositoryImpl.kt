package co.edu.uniandes.vinilos.repositories

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.dao.TrackDao
import co.edu.uniandes.vinilos.database.nocaseKey
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.database.toModel
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.network.AlbumServiceAdapter
import co.edu.uniandes.vinilos.network.networkRequest
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Albums from Room, revalidated against the backend. Room is only a cache: when it fails, the backend is the source
 * (an unreadable cache counts as empty and a failed write still returns what the backend sent), so a database
 * error never closes the app. Network failures are still reported as before.
 */
internal class AlbumRepositoryImpl(
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val serviceAdapter: AlbumServiceAdapter,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AlbumRepository {
    private val mutex = Mutex()

    override suspend fun hasCachedAlbums(): Boolean =
        try {
            albumDao.hasAlbums()
        } catch (_: SQLiteException) {
            false
        }

    override suspend fun getAlbum(
        id: Int,
        forceRefresh: Boolean,
    ): DataResult<Album, DataError> {
        require(id > 0) { "albumId must be positive" }
        return networkRequest {
            serviceAdapter.getAlbum(id, forceRefresh).also {
                if (it.id != id) throw JsonParseException("Unexpected album id")
            }
        }
    }

    override suspend fun getAlbums(forceRefresh: Boolean): DataResult<List<Album>, DataError> =
        mutex.withLock {
            val cached = if (forceRefresh) emptyList() else readCacheOrEmpty()
            if (cached.isNotEmpty()) {
                DataResult.Success(cached)
            } else {
                when (val result = networkRequest { serviceAdapter.getAlbums() }) {
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

    private suspend fun readCacheOrEmpty(): List<Album> =
        try {
            readAll()
        } catch (_: SQLiteException) {
            emptyList()
        }

    // Saves the complete answer and returns it as Room orders it. If Room cannot save it or read it back, the answer
    // is still shown, sorted the same way, so a disk or database error does not change what the screen gets.
    private suspend fun store(albums: List<Album>): List<Album> =
        try {
            albumDao.replaceAll(withContext(defaultDispatcher) { albums.map { it.toEntity() } })
            readAll()
        } catch (_: SQLiteException) {
            withContext(defaultDispatcher) { albums.sortedWith(ROOM_ORDER) }
        }

    // Room resumes the caller, which for the catalog is Main: the rows are mapped on the default dispatcher.
    private suspend fun readAll(): List<Album> {
        val rows = albumDao.getAll()
        return withContext(defaultDispatcher) { rows.map { it.toModel() } }
    }

    // Same mutex as getAlbums: a refresh that fetched the list before this POST would otherwise
    // finish later and its replaceAll would delete the album just created.
    override suspend fun createAlbum(draft: NewAlbum): DataResult<Album, DataError> =
        mutex.withLock {
            val result = networkRequest { serviceAdapter.createAlbum(draft) }
            if (result is DataResult.Success) {
                try {
                    albumDao.upsert(result.value.toEntity())
                } catch (_: SQLiteException) {
                    // The backend already saved it: reporting a failure would make the user retry and duplicate it.
                    // Room catches up on the next forced refresh.
                }
            }
            result
        }

    // Share the catalog mutex: replaceAll removes missing albums and their tracks via CASCADE.
    override suspend fun addTrack(
        albumId: Int,
        draft: NewTrack,
    ): DataResult<Track, DataError> {
        require(albumId > 0) { "albumId must be positive" }
        return mutex.withLock {
            val result = networkRequest { serviceAdapter.addTrack(albumId, draft) }
            if (result is DataResult.Success) {
                try {
                    trackDao.upsert(result.value.toEntity(albumId))
                } catch (_: SQLiteException) {
                    // The backend already saved it. Room can catch up on a later album detail request.
                }
            }
            result
        }
    }

    private companion object {
        // Same order as AlbumDao.getAll(): `name COLLATE NOCASE, id`.
        val ROOM_ORDER: Comparator<Album> = compareBy<Album>({ nocaseKey(it.name) }, { it.id })
    }
}
