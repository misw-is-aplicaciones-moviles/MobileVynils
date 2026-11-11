package co.edu.uniandes.vinilos.testing

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.PerformerDao
import co.edu.uniandes.vinilos.database.entities.PerformerEntity

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

    override suspend fun deleteAllExcept(ids: List<Int>) {
        writeCount++
        writeFailure?.let { throw it }
        rows.keys.retainAll(ids.toSet())
    }

    // SQLite COLLATE NOCASE only folds A-Z, so "Ángel" sorts after "Zeta"; lowercase() would fold Unicode too.
    private fun asciiFolded(text: String): String =
        text.map { if (it in 'A'..'Z') it + ASCII_CASE_OFFSET else it }.joinToString("")

    private companion object {
        const val ASCII_CASE_OFFSET = 'a' - 'A'
    }
}
