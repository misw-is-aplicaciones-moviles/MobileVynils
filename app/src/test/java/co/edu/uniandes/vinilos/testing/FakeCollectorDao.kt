package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.database.dao.CollectorDao
import co.edu.uniandes.vinilos.database.entities.CollectorEntity

/** In-memory [CollectorDao]. The real order is SQL, so `CollectorDaoTest` checks it; here it is by name only. */
internal class FakeCollectorDao(
    initial: List<CollectorEntity> = emptyList(),
) : CollectorDao {
    private val rows = initial.associateBy { it.id }.toMutableMap()

    var writeCount = 0
        private set

    override suspend fun getAll(): List<CollectorEntity> = rows.values.sortedBy { it.name }

    override suspend fun count(): Int = rows.size

    override suspend fun upsertAll(collectors: List<CollectorEntity>) {
        writeCount++
        collectors.forEach { rows[it.id] = it }
    }

    override suspend fun deleteAllExcept(ids: List<Int>) {
        writeCount++
        rows.keys.retainAll(ids.toSet())
    }
}
