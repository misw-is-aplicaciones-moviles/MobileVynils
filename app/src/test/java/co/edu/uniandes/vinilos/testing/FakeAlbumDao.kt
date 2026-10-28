package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.database.dao.AlbumDao
import co.edu.uniandes.vinilos.database.entities.AlbumEntity
import co.edu.uniandes.vinilos.database.entities.AlbumWithTracks

/** In-memory [AlbumDao] that sorts by name ignoring case, like the real query. */
internal class FakeAlbumDao(
    initial: List<AlbumEntity> = emptyList(),
) : AlbumDao {
    private val rows = initial.associateBy { it.id }.toMutableMap()

    var readCount = 0
        private set
    var writeCount = 0
        private set

    override suspend fun getAll(): List<AlbumEntity> {
        readCount++
        return rows.values.sortedBy { asciiFolded(it.name) }
    }

    override suspend fun getWithTracks(id: Int): AlbumWithTracks? = null

    override suspend fun upsert(album: AlbumEntity) {
        writeCount++
        rows[album.id] = album
    }

    override suspend fun upsertAll(albums: List<AlbumEntity>) {
        writeCount++
        albums.forEach { rows[it.id] = it }
    }

    override suspend fun deleteAllExcept(ids: List<Int>) {
        rows.keys.retainAll(ids.toSet())
    }

    // SQLite COLLATE NOCASE only folds A-Z, so "Ágape" sorts after "Zeta"; lowercase() would fold Unicode too.
    private fun asciiFolded(text: String): String =
        text.map { if (it in 'A'..'Z') it + ASCII_CASE_OFFSET else it }.joinToString("")

    private companion object {
        const val ASCII_CASE_OFFSET = 'a' - 'A'
    }
}
