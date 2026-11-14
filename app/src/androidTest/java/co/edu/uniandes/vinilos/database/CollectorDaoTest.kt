package co.edu.uniandes.vinilos.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.database.dao.CollectorDao
import co.edu.uniandes.vinilos.database.entities.CollectorEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Nombres en snake_case: los nombres con espacios no son válidos en DEX antes de API 30.
@RunWith(AndroidJUnit4::class)
class CollectorDaoTest {
    private lateinit var database: VinilosDatabase
    private lateinit var dao: CollectorDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VinilosDatabase::class.java).build()
        dao = database.collectorDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun get_all_returns_collectors_by_name_ignoring_case_with_ties_by_id() =
        runTest {
            dao.upsertAll(
                listOf(
                    entity(id = 3, name = "zeta"),
                    entity(id = 5, name = "Alfa"),
                    entity(id = 4, name = "Beta"),
                    entity(id = 2, name = "alfa"),
                ),
            )

            assertEquals(listOf(2, 5, 4, 3), dao.getAll().map { it.id })
        }

    @Test
    fun replace_all_leaves_exactly_what_it_receives_and_updates_existing_rows() =
        runTest {
            dao.upsertAll(listOf(entity(id = 1, name = "Se va"), entity(id = 2, name = "Antes")))

            dao.replaceAll(listOf(entity(id = 2, name = "Después"), entity(id = 3, name = "Nuevo")))
            assertEquals(listOf("Después", "Nuevo"), dao.getAll().map { it.name })

            dao.replaceAll(emptyList())
            assertEquals(0, dao.count())
        }

    private fun entity(
        id: Int,
        name: String,
    ) = CollectorEntity(id = id, name = name, email = "$id@example.com", albumCount = 1, favoriteCount = 2)
}
