package co.edu.uniandes.vinilos.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.database.dao.PerformerDao
import co.edu.uniandes.vinilos.database.entities.PerformerEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

// Nombres en snake_case: los nombres con espacios no son válidos en DEX antes de API 30.
@RunWith(AndroidJUnit4::class)
class PerformerDaoTest {
    private lateinit var database: VinilosDatabase
    private lateinit var dao: PerformerDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VinilosDatabase::class.java).build()
        dao = database.performerDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun get_all_returns_performers_by_name_ignoring_case_with_ties_by_id() =
        runTest {
            val upperAlfa = entity(id = 5, name = "Alfa")
            val lowerAlfa = entity(id = 2, name = "alfa")
            dao.upsertAll(listOf(entity(id = 3, name = "zeta"), upperAlfa, entity(id = 4, name = "Beta"), lowerAlfa))

            assertEquals(listOf(2, 5, 4, 3), dao.getAll().map { it.id })
        }

    @Test
    fun names_with_an_accent_on_the_first_letter_sort_after_z_like_the_catalog() =
        runTest {
            dao.upsertAll(listOf(entity(id = 1, name = "Álvaro"), entity(id = 2, name = "Zeta")))

            assertEquals(listOf("Zeta", "Álvaro"), dao.getAll().map { it.name })
        }

    @Test
    fun count_reflects_the_rows() =
        runTest {
            assertEquals(0, dao.count())

            dao.upsertAll(listOf(entity(id = 1, name = "A"), entity(id = 2, name = "B")))

            assertEquals(2, dao.count())
        }

    @Test
    fun fields_are_stored_and_read_back_unchanged() =
        runTest {
            val band = entity(id = 101, name = "Queen", type = "BAND", albumCount = 2, memberCount = 4)
            val member = entity(id = 103, name = "Brian May", bandId = 101)

            dao.upsertAll(listOf(band, member))

            val restored = dao.getAll().associateBy { it.id }
            assertEquals(band, restored.getValue(101))
            assertEquals(member, restored.getValue(103))
            assertEquals(LocalDate.of(1970, 1, 1), restored.getValue(101).date)
            assertNull(restored.getValue(101).bandId)
        }

    @Test
    fun upsert_updates_a_performer_with_the_same_id() =
        runTest {
            dao.upsertAll(listOf(entity(id = 1, name = "Antes", albumCount = 1)))

            dao.upsertAll(listOf(entity(id = 1, name = "Después", albumCount = 3)))

            assertEquals(listOf(entity(id = 1, name = "Después", albumCount = 3)), dao.getAll())
        }

    @Test
    fun replace_all_removes_performers_missing_from_the_new_list() =
        runTest {
            dao.upsertAll(listOf(entity(id = 1, name = "A"), entity(id = 2, name = "B")))

            dao.replaceAll(listOf(entity(id = 2, name = "B"), entity(id = 3, name = "C")))

            assertEquals(listOf(2, 3), dao.getAll().map { it.id })
        }

    @Test
    fun replace_all_with_an_empty_list_empties_the_table() =
        runTest {
            dao.upsertAll(listOf(entity(id = 1, name = "A")))

            dao.replaceAll(emptyList())

            assertEquals(0, dao.count())
        }

    private fun entity(
        id: Int,
        name: String,
        type: String = "MUSICIAN",
        albumCount: Int = 0,
        memberCount: Int = 0,
        bandId: Int? = null,
    ) = PerformerEntity(
        id = id,
        type = type,
        name = name,
        image = "https://example.com/$id.jpg",
        date = LocalDate.of(1970, 1, 1),
        albumCount = albumCount,
        memberCount = memberCount,
        bandId = bandId,
    )
}
