package co.edu.uniandes.vinilos.testing

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.PerformerDao
import co.edu.uniandes.vinilos.database.entities.PerformerEntity
import co.edu.uniandes.vinilos.models.PerformerType

/** In-memory [PerformerDao] that sorts by name ignoring ASCII case and then by id, like the real query. */
internal class FakePerformerDao(
    initial: List<PerformerEntity> = emptyList(),
) : PerformerDao {
    private val rows = initial.associateBy { it.id }.toMutableMap()

    var writeCount = 0
        private set

    /** Thrown by [getAll] and [count], as a corrupt or unreadable database would. */
    var readFailure: SQLiteException? = null

    /** Thrown by the writes before changing anything, as a full disk would. */
    var writeFailure: SQLiteException? = null

    override suspend fun getAll(): List<PerformerEntity> {
        readFailure?.let { throw it }
        return rows.values.sortedWith(compareBy({ asciiFolded(it.name) }, { it.id }))
    }

    override suspend fun count(): Int {
        readFailure?.let { throw it }
        return rows.size
    }

    override suspend fun upsertAll(performers: List<PerformerEntity>) {
        writeCount++
        writeFailure?.let { throw it }
        performers.forEach { rows[it.id] = it }
    }

    override suspend fun getIds(): List<Int> = rows.keys.toList()

    override suspend fun deleteByIds(ids: List<Int>) {
        writeCount++
        writeFailure?.let { throw it }
        rows.keys.removeAll(ids.toSet())
    }

    override suspend fun setMusicianBand(
        musicianId: Int,
        bandId: Int,
    ): Int {
        writeCount++
        writeFailure?.let { throw it }
        val row = rows[musicianId]?.takeIf { it.type == PerformerType.MUSICIAN.name && it.bandId != bandId } ?: return 0
        rows[musicianId] = row.copy(bandId = bandId)
        return 1
    }

    override suspend fun incrementMemberCount(bandId: Int) {
        writeCount++
        writeFailure?.let { throw it }
        val row = rows[bandId]?.takeIf { it.type == PerformerType.BAND.name } ?: return
        rows[bandId] = row.copy(memberCount = row.memberCount + 1)
    }

    /** Row as stored, for assertions on the cache. */
    fun row(id: Int): PerformerEntity? = rows[id]

    // SQLite COLLATE NOCASE only folds A-Z, so "Ángel" sorts after "Zeta"; lowercase() would fold Unicode too.
    private fun asciiFolded(text: String): String =
        text.map { if (it in 'A'..'Z') it + ASCII_CASE_OFFSET else it }.joinToString("")

    private companion object {
        const val ASCII_CASE_OFFSET = 'a' - 'A'
    }
}
