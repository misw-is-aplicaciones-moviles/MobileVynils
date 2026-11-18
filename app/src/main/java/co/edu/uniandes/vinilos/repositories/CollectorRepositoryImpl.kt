package co.edu.uniandes.vinilos.repositories

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.CollectorDao
import co.edu.uniandes.vinilos.database.dao.CommentAuthorDao
import co.edu.uniandes.vinilos.database.nocaseKey
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.database.toModel
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.CommentAuthor
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewCollectorAlbum
import co.edu.uniandes.vinilos.network.CollectorDirectory
import co.edu.uniandes.vinilos.network.CollectorServiceAdapter
import co.edu.uniandes.vinilos.network.networkRequest
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Collectors from Room, revalidated against the backend. One `GET /collectors` fills both the list and the comment
 * authors. Room is only a cache: an unreadable cache counts as empty and a failed write still returns what the
 * backend sent, so a database error never closes the app.
 */
internal class CollectorRepositoryImpl(
    private val collectorDao: CollectorDao,
    private val serviceAdapter: CollectorServiceAdapter,
    private val defaultDispatcher: CoroutineDispatcher,
    private val commentAuthorDao: CommentAuthorDao,
) : CollectorRepository {
    // Shared by the list and the authors: two cold callers make one request, the second one reads what it stored.
    private val mutex = Mutex()

    // Only read and written under [mutex] or before it is taken; a stale `false` just costs one more refresh.
    @Volatile private var authorsRevalidated = false

    override suspend fun hasCachedCollectors(): Boolean =
        try {
            collectorDao.count() > 0
        } catch (_: SQLiteException) {
            false
        }

    override suspend fun getCollectors(forceRefresh: Boolean): DataResult<List<Collector>, DataError> =
        mutex.withLock {
            val cached = if (forceRefresh) emptyList() else readCollectorsOrEmpty()
            if (cached.isNotEmpty()) {
                DataResult.Success(cached)
            } else {
                when (val result = refresh()) {
                    is DataResult.Success -> DataResult.Success(result.value.collectors)

                    // Nothing was fetched completely, so Room is not touched.
                    is DataResult.Failure -> result
                }
            }
        }

    override suspend fun getCommentAuthors(
        commentIds: Set<Int>,
        revalidate: Boolean,
    ): DataResult<Map<Int, CommentAuthor>, DataError> {
        if (commentIds.isEmpty()) return DataResult.Success(emptyMap())
        val cached = readAuthorsOrEmpty(commentIds)
        if (!revalidate || isTrusted(cached, commentIds)) return DataResult.Success(cached)
        return mutex.withLock {
            // Another caller may have refreshed while this one waited for the lock.
            val again = readAuthorsOrEmpty(commentIds)
            if (isTrusted(again, commentIds)) {
                DataResult.Success(again)
            } else {
                when (val result = refresh()) {
                    is DataResult.Success -> {
                        val authors =
                            withContext(defaultDispatcher) {
                                // The backend answer wins over Room: it corrects an author saved for a reused id.
                                again + result.value.commentAuthors.filterKeys(commentIds::contains)
                            }
                        DataResult.Success(authors)
                    }

                    is DataResult.Failure -> {
                        result
                    }
                }
            }
        }
    }

    override suspend fun getCollector(
        id: Int,
        forceRefresh: Boolean,
    ): DataResult<CollectorDetail, DataError> {
        require(id > 0) { "collectorId must be positive" }
        return networkRequest {
            serviceAdapter.getCollector(id, forceRefresh).also { detail ->
                if (detail.collector.id != id) throw JsonParseException("Unexpected collector identity")
            }
        }
    }

    override suspend fun addAlbum(
        collectorId: Int,
        albumId: Int,
        draft: NewCollectorAlbum,
    ): DataResult<Unit, DataError> {
        require(collectorId > 0) { "collectorId must be positive" }
        require(albumId > 0) { "albumId must be positive" }
        return networkRequest {
            serviceAdapter.addAlbum(collectorId, albumId, draft)
            Unit
        }
    }

    // Room is trusted only after this process stored one complete `GET /collectors`: the backend gives no sign that its
    // database was recreated, and then it reuses comment ids, so a row saved in an earlier session may name the wrong
    // collector. The first revalidation of each session corrects it; later ones only ask for ids Room does not have.
    private fun isTrusted(
        cached: Map<Int, CommentAuthor>,
        commentIds: Set<Int>,
    ): Boolean = authorsRevalidated && cached.size == commentIds.size

    private suspend fun refresh(): DataResult<CollectorDirectory, DataError> =
        when (val result = networkRequest { serviceAdapter.getCollectors() }) {
            is DataResult.Success -> DataResult.Success(store(result.value))
            is DataResult.Failure -> result
        }

    // Saves the complete answer and returns the list as Room orders it. If Room cannot save or read it back, the
    // answer is still returned, sorted the same way, so a database error does not change what the screen gets.
    private suspend fun store(directory: CollectorDirectory): CollectorDirectory {
        // The index only grows: comments are never deleted from the app, and an older answer that arrives late cannot
        // erase an author saved by a publication in the meantime.
        try {
            val authors =
                withContext(defaultDispatcher) {
                    directory.commentAuthors.map { (commentId, author) -> author.toEntity(commentId) }
                }
            commentAuthorDao.upsertAll(authors)
            authorsRevalidated = true
        } catch (_: SQLiteException) {
            // The authors are still returned; Room catches up on the next refresh.
        }
        val collectors =
            try {
                collectorDao.replaceAll(withContext(defaultDispatcher) { directory.collectors.map { it.toEntity() } })
                readCollectors()
            } catch (_: SQLiteException) {
                withContext(defaultDispatcher) { directory.collectors.sortedWith(ROOM_ORDER) }
            }
        return directory.copy(collectors = collectors)
    }

    private suspend fun readCollectorsOrEmpty(): List<Collector> =
        try {
            readCollectors()
        } catch (_: SQLiteException) {
            emptyList()
        }

    // The backend has no pagination, so the size of the list has no limit: it is mapped off the main thread.
    private suspend fun readCollectors(): List<Collector> {
        val rows = collectorDao.getAll()
        return withContext(defaultDispatcher) { rows.map { it.toModel() } }
    }

    // The whole index is read and filtered here, so there is no SQL variable per id and no 999 limit (#139).
    private suspend fun readAuthorsOrEmpty(commentIds: Set<Int>): Map<Int, CommentAuthor> =
        try {
            val rows = commentAuthorDao.getAll()
            withContext(defaultDispatcher) {
                rows.filter { it.commentId in commentIds }.associate { it.commentId to it.toModel() }
            }
        } catch (_: SQLiteException) {
            emptyMap()
        }

    private companion object {
        // Same order as CollectorDao.getAll(): `name COLLATE NOCASE, id`.
        val ROOM_ORDER: Comparator<Collector> = compareBy<Collector>({ nocaseKey(it.name) }, { it.id })
    }
}
