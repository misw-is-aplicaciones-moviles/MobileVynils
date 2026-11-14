package co.edu.uniandes.vinilos.testing

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.entities.AlbumEntity
import co.edu.uniandes.vinilos.database.entities.AlbumWithTracks

/**
 * In-memory [AlbumDao] that sorts by name ignoring ASCII case and then by id, like the real query.
 * [upsertFailure] makes [upsert] throw.
 */
internal class FakeAlbumDao(
    initial: List<AlbumEntity> = emptyList(),
    var upsertFailure: Throwable? = null,
) : AlbumDao {
    private val rows = initial.associateBy { it.id }.toMutableMap()

    var readCount = 0
        private set
    var writeCount = 0
        private set

    /** Thrown by [getAll] and [hasAlbums], as a corrupt or unreadable database would. */
    var readFailure: SQLiteException? = null

    /** Thrown by the list writes before changing anything, as a full disk would. */
    var writeFailure: SQLiteException? = null

    override suspend fun hasAlbums(): Boolean {
        readFailure?.let { throw it }
        return rows.isNotEmpty()
    }

    override suspend fun getAll(): List<AlbumEntity> {
        readFailure?.let { throw it }
        readCount++
        return rows.values.sortedWith(compareBy({ asciiFolded(it.name) }, { it.id }))
    }

    override suspend fun getWithTracks(id: Int): AlbumWithTracks? = null

    override suspend fun upsert(album: AlbumEntity) {
        upsertFailure?.let { throw it }
        writeCount++
        rows[album.id] = album
    }

    override suspend fun upsertAll(albums: List<AlbumEntity>) {
        writeFailure?.let { throw it }
        writeCount++
        albums.forEach { rows[it.id] = it }
    }

    override suspend fun deleteAllExcept(ids: List<Int>) {
        writeFailure?.let { throw it }
        rows.keys.retainAll(ids.toSet())
    }

    // SQLite COLLATE NOCASE only folds A-Z, so "Ágape" sorts after "Zeta"; lowercase() would fold Unicode too.
    private fun asciiFolded(text: String): String =
        text.map { if (it in 'A'..'Z') it + ASCII_CASE_OFFSET else it }.joinToString("")

    private companion object {
        const val ASCII_CASE_OFFSET = 'a' - 'A'
    }
}
