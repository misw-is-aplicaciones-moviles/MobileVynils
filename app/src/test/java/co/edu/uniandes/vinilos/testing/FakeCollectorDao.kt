package co.edu.uniandes.vinilos.testing

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.CollectorDao
import co.edu.uniandes.vinilos.database.entities.CollectorEntity

/**
 * In-memory [CollectorDao]. The real order is SQL, so `CollectorDaoTest` checks it; here it is by name only.
 * [readFailure] makes the reads throw and [writeFailure] makes the writes throw before changing anything.
 */
internal class FakeCollectorDao(
    initial: List<CollectorEntity> = emptyList(),
    var readFailure: SQLiteException? = null,
    var writeFailure: SQLiteException? = null,
) : CollectorDao {
    private val rows = initial.associateBy { it.id }.toMutableMap()

    var writeCount = 0
        private set

    override suspend fun getAll(): List<CollectorEntity> {
        readFailure?.let { throw it }
        return rows.values.sortedBy { it.name }
    }

    override suspend fun count(): Int {
        readFailure?.let { throw it }
        return rows.size
    }

    override suspend fun upsertAll(collectors: List<CollectorEntity>) {
        writeFailure?.let { throw it }
        writeCount++
        collectors.forEach { rows[it.id] = it }
    }

    override suspend fun deleteAllExcept(ids: List<Int>) {
        writeFailure?.let { throw it }
        writeCount++
        rows.keys.retainAll(ids.toSet())
    }
}
