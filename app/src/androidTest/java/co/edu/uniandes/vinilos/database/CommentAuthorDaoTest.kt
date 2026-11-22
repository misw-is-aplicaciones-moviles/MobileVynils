package co.edu.uniandes.vinilos.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.database.dao.CommentAuthorDao
import co.edu.uniandes.vinilos.database.entities.CommentAuthorEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Nombres en snake_case: los nombres con espacios no son válidos en DEX antes de API 30.
@RunWith(AndroidJUnit4::class)
class CommentAuthorDaoTest {
    private lateinit var database: VinilosDatabase
    private lateinit var dao: CommentAuthorDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VinilosDatabase::class.java).build()
        dao = database.commentAuthorDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun upsert_all_adds_new_rows_and_keeps_the_ones_it_does_not_mention() =
        runTest {
            dao.upsertAll(listOf(entity(commentId = 1, collectorId = 100), entity(commentId = 2, collectorId = 100)))

            dao.upsertAll(listOf(entity(commentId = 2, collectorId = 101), entity(commentId = 3, collectorId = 101)))

            assertEquals(
                setOf(
                    entity(commentId = 1, collectorId = 100),
                    entity(commentId = 2, collectorId = 101),
                    entity(commentId = 3, collectorId = 101),
                ),
                dao.getAll().toSet(),
            )
        }

    @Test
    fun upsert_adds_a_new_row_and_updates_the_author_of_an_existing_comment() =
        runTest {
            dao.upsert(entity(commentId = 1, collectorId = 100))
            dao.upsert(entity(commentId = 2, collectorId = 100))

            dao.upsert(entity(commentId = 1, collectorId = 101))

            assertEquals(
                setOf(entity(commentId = 1, collectorId = 101), entity(commentId = 2, collectorId = 100)),
                dao.getAll().toSet(),
            )
        }

    @Test
    fun upsert_all_with_more_than_999_rows_does_not_hit_the_sqlite_variable_limit() =
        runTest {
            val many = (1..MANY_ROWS).map { entity(commentId = it, collectorId = it % 7) }

            dao.upsertAll(many)

            val stored = dao.getAll()
            assertEquals(MANY_ROWS, stored.size)
            assertEquals(many.toSet(), stored.toSet())
        }

    @Test
    fun get_by_ids_in_chunks_returns_only_the_requested_rows_and_skips_unknown_ids() =
        runTest {
            dao.upsertAll((1..10).map { entity(commentId = it, collectorId = 100) })

            val read = dao.getByIdsInChunks(setOf(2, 5, 42))

            assertEquals(
                setOf(entity(commentId = 2, collectorId = 100), entity(commentId = 5, collectorId = 100)),
                read.toSet(),
            )
        }

    @Test
    fun get_by_ids_in_chunks_without_ids_returns_nothing() =
        runTest {
            dao.upsertAll(listOf(entity(commentId = 1, collectorId = 100)))

            assertEquals(emptyList<CommentAuthorEntity>(), dao.getByIdsInChunks(emptySet()))
        }

    @Test
    fun get_by_ids_in_chunks_with_more_than_999_ids_does_not_hit_the_sqlite_variable_limit() =
        runTest {
            val many = (1..MANY_ROWS).map { entity(commentId = it, collectorId = it % 7) }
            dao.upsertAll(many)

            listOf(SOME_ROWS, MANY_ROWS).forEach { count ->
                val read = dao.getByIdsInChunks((1..count).toSet())

                assertEquals(many.take(count).toSet(), read.toSet())
            }
        }

    private fun entity(
        commentId: Int,
        collectorId: Int,
    ) = CommentAuthorEntity(
        commentId = commentId,
        collectorId = collectorId,
        collectorName = "Coleccionista $collectorId",
    )

    private companion object {
        // Above SQLITE_MAX_VARIABLE_NUMBER (999) of the SQLite in API 21 to API 30.
        const val MANY_ROWS = 2500
        const val SOME_ROWS = 1500
    }
}
