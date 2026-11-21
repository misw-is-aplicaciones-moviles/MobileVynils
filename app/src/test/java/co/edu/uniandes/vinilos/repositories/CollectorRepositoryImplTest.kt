package co.edu.uniandes.vinilos.repositories

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.MAX_IDS_PER_STATEMENT
import co.edu.uniandes.vinilos.database.entities.CommentAuthorEntity
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CommentAuthor
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.testing.FakeCollectorDao
import co.edu.uniandes.vinilos.testing.FakeCollectorServiceAdapter
import co.edu.uniandes.vinilos.testing.FakeCommentAuthorDao
import co.edu.uniandes.vinilos.testing.collector
import co.edu.uniandes.vinilos.testing.collectorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@Suppress("MagicNumber") // Fixture ids.
class CollectorRepositoryImplTest {
    private val cached = collector(id = 9, name = "Guardado")
    private val network = listOf(collector(id = 100, name = "Manolo"), collector(id = 101, name = "Jaime"))
    private val manolo = CommentAuthor(collectorId = 100, name = "Manolo")
    private val jaime = CommentAuthor(collectorId = 101, name = "Jaime")
    private val networkAuthors = mapOf(100 to manolo, 101 to jaime)
    private val cachedAuthor = CommentAuthorEntity(commentId = 9, collectorId = 9, collectorName = "Guardado")

    private fun TestScope.repository(
        dao: FakeCollectorDao,
        adapter: FakeCollectorServiceAdapter,
        authors: FakeCommentAuthorDao = FakeCommentAuthorDao(),
    ) = CollectorRepositoryImpl(dao, adapter, StandardTestDispatcher(testScheduler), authors)

    private fun names(result: DataResult<List<Collector>, DataError>) =
        (result as DataResult.Success).value.map { it.name }

    @Test
    fun `filled Room without force refresh does not call the adapter`() =
        runTest {
            val adapter = FakeCollectorServiceAdapter(collectors = network)
            val dao = FakeCollectorDao(listOf(cached.toEntity()))

            val result = repository(dao, adapter).getCollectors()

            assertEquals(0, adapter.callCount)
            assertEquals(listOf("Guardado"), names(result))
        }

    @Test
    fun `force refresh goes to the network and replaces what Room had`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()))
            val adapter = FakeCollectorServiceAdapter(collectors = network)

            val result = repository(dao, adapter).getCollectors(forceRefresh = true)

            assertEquals(1, adapter.callCount)
            assertEquals(listOf("Jaime", "Manolo"), names(result))
            assertEquals(listOf("Jaime", "Manolo"), dao.getAll().map { it.name })
        }

    @Test
    fun `an empty backend answer empties Room`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()))

            val result = repository(dao, FakeCollectorServiceAdapter()).getCollectors(forceRefresh = true)

            assertEquals(DataResult.Success(emptyList<Collector>()), result)
            assertEquals(0, dao.count())
        }

    @Test
    fun `a failed load returns the data error and leaves Room intact`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()))
            val adapter = FakeCollectorServiceAdapter(failure = IOException("offline"))

            val result = repository(dao, adapter).getCollectors(forceRefresh = true)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(listOf("Guardado"), dao.getAll().map { it.name })
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `detail does not read or write the list cache`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()))
            val detail = collectorDetail()
            val adapter = FakeCollectorServiceAdapter(detail = detail)

            val result = repository(dao, adapter).getCollector(100, forceRefresh = true)

            assertEquals(DataResult.Success(detail), result)
            assertEquals(1, adapter.detailCalls)
            assertEquals(100, adapter.lastDetailId)
            assertEquals(true, adapter.lastDetailRefresh)
            assertEquals(0, adapter.callCount)
            assertEquals(0, dao.writeCount)
            assertEquals(listOf("Guardado"), dao.getAll().map { it.name })
        }

    @Test
    fun `a mismatched collector id is a serialization error and leaves Room intact`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()))
            val adapter = FakeCollectorServiceAdapter(detail = collectorDetail(id = 999))

            val result = repository(dao, adapter).getCollector(100)

            assertEquals(DataResult.Failure(DataError.Serialization), result)
            assertEquals(0, dao.writeCount)
            assertEquals(listOf("Guardado"), dao.getAll().map { it.name })
        }

    @Test
    fun `a detail IO failure is a network error and leaves Room intact`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()))
            val adapter = FakeCollectorServiceAdapter(detailFailure = IOException("offline"))

            val result = repository(dao, adapter).getCollector(100, forceRefresh = true)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(0, dao.writeCount)
            assertEquals(listOf("Guardado"), dao.getAll().map { it.name })
        }

    @Test
    fun `a non positive detail id is rejected before the adapter`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()))
            val adapter = FakeCollectorServiceAdapter()
            listOf(0, -1).forEach { id ->
                val error = runCatching { repository(dao, adapter).getCollector(id) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertEquals(0, adapter.detailCalls)
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `cancelling a detail propagates and does not touch Room`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()))
            val adapter = FakeCollectorServiceAdapter(detailFailure = CancellationException("cancelled"))

            val error = runCatching { repository(dao, adapter).getCollector(100) }.exceptionOrNull()

            assertTrue(error is CancellationException)
            assertEquals(0, dao.writeCount)
            assertEquals(listOf("Guardado"), dao.getAll().map { it.name })
        }

    @Test
    fun `empty comment ids return an empty map without reading Room or the network`() =
        runTest {
            val authors = FakeCommentAuthorDao(listOf(cachedAuthor))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)

            val result = repository(FakeCollectorDao(), adapter, authors).getCommentAuthors(emptySet(), true)

            assertEquals(DataResult.Success(emptyMap<Int, CommentAuthor>()), result)
            assertEquals(0, authors.readCount)
            assertEquals(0, adapter.callCount)
        }

    @Test
    fun `without revalidate known ids come from Room only and only the requested ones`() =
        runTest {
            val other = CommentAuthorEntity(commentId = 50, collectorId = 100, collectorName = "Manolo")
            val authors = FakeCommentAuthorDao(listOf(cachedAuthor, other))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)

            val result = repository(FakeCollectorDao(), adapter, authors).getCommentAuthors(setOf(9), false)

            assertEquals(DataResult.Success(mapOf(9 to CommentAuthor(9, "Guardado"))), result)
            assertEquals(0, adapter.callCount)
            assertEquals(0, authors.writeCount)
        }

    @Test
    fun `without revalidate an unknown id is left out and the network is never called`() =
        runTest {
            val authors = FakeCommentAuthorDao(listOf(cachedAuthor))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)

            val result = repository(FakeCollectorDao(), adapter, authors).getCommentAuthors(setOf(9, 100), false)

            assertEquals(DataResult.Success(mapOf(9 to CommentAuthor(9, "Guardado"))), result)
            assertEquals(0, adapter.callCount)
        }

    @Test
    fun `missing ids are fetched once and the additive store keeps rows absent from the answer`() =
        runTest {
            val dao = FakeCollectorDao()
            val authors = FakeCommentAuthorDao(listOf(cachedAuthor))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)
            val repository = repository(dao, adapter, authors)

            val result = repository.getCommentAuthors(setOf(100), true)
            val again = repository.getCommentAuthors(setOf(100, 101), true)

            assertEquals(DataResult.Success(mapOf(100 to manolo)), result)
            assertEquals(DataResult.Success(networkAuthors), again)
            assertEquals(1, adapter.callCount)
            assertEquals(
                mapOf(
                    9 to cachedAuthor,
                    100 to CommentAuthorEntity(100, 100, "Manolo"),
                    101 to CommentAuthorEntity(101, 101, "Jaime"),
                ),
                authors.stored,
            )
            assertEquals(listOf("Jaime", "Manolo"), dao.getAll().map { it.name })
        }

    @Test
    fun `a collectors refresh does not erase an author saved by a publication`() =
        runTest {
            val published = CommentAuthorEntity(commentId = 7, collectorId = 101, collectorName = "Jaime")
            val authors = FakeCommentAuthorDao(listOf(published))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)

            repository(FakeCollectorDao(), adapter, authors).getCollectors(forceRefresh = true)

            assertEquals(published, authors.stored[7])
            assertEquals(setOf(7, 100, 101), authors.stored.keys)
        }

    @Test
    fun `a failed authors refresh returns the data error and leaves Room intact`() =
        runTest {
            val authors = FakeCommentAuthorDao(listOf(cachedAuthor))
            val dao = FakeCollectorDao(listOf(cached.toEntity()))
            val adapter = FakeCollectorServiceAdapter(failure = IOException("offline"))

            val result = repository(dao, adapter, authors).getCommentAuthors(setOf(100), true)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(0, authors.writeCount)
            assertEquals(mapOf(9 to cachedAuthor), authors.stored)
            assertEquals(0, dao.writeCount)
            assertEquals(listOf("Guardado"), dao.getAll().map { it.name })
        }

    @Test
    fun `an unreadable author index counts as empty`() =
        runTest {
            val authors = FakeCommentAuthorDao(listOf(cachedAuthor), readFailure = SQLiteException("corrupt"))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)
            val repository = repository(FakeCollectorDao(), adapter, authors)

            val roomOnly = repository.getCommentAuthors(setOf(9), false)
            val refreshed = repository.getCommentAuthors(setOf(100), true)

            assertEquals(DataResult.Success(emptyMap<Int, CommentAuthor>()), roomOnly)
            assertEquals(DataResult.Success(mapOf(100 to manolo)), refreshed)
            assertEquals(1, adapter.callCount)
        }

    @Test
    fun `an unreadable collector table counts as empty and still returns the answer in Room order`() =
        runTest {
            val dao = FakeCollectorDao(listOf(cached.toEntity()), readFailure = SQLiteException("corrupt"))
            val adapter = FakeCollectorServiceAdapter(collectors = unsorted)
            val repository = repository(dao, adapter)

            assertFalse(repository.hasCachedCollectors())
            val result = repository.getCollectors()

            assertEquals(1, adapter.callCount)
            assertEquals(listOf(2, 5, 3, 4), (result as DataResult.Success).value.map { it.id })
        }

    @Test
    fun `an unwritable Room still returns the backend answer with the list in Room order`() =
        runTest {
            val dao = FakeCollectorDao(writeFailure = SQLiteException("disk full"))
            val authors = FakeCommentAuthorDao(writeFailure = SQLiteException("disk full"))
            val adapter = FakeCollectorServiceAdapter(collectors = unsorted, commentAuthors = networkAuthors)
            val repository = repository(dao, adapter, authors)

            val list = repository.getCollectors(forceRefresh = true)
            val index = repository.getCommentAuthors(setOf(100, 101), true)

            assertEquals(listOf(2, 5, 3, 4), (list as DataResult.Success).value.map { it.id })
            assertEquals(DataResult.Success(networkAuthors), index)
            assertTrue(authors.stored.isEmpty())
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `concurrent cold refreshes of the authors and the list make one network call`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val adapter =
                FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors, gate = gate)
            val repository = repository(FakeCollectorDao(), adapter)

            val first = async { repository.getCommentAuthors(setOf(100), true) }
            val second = async { repository.getCommentAuthors(setOf(100, 101), true) }
            val list = async { repository.getCollectors() }
            advanceUntilIdle()
            assertEquals(1, adapter.callCount)
            gate.complete(Unit)

            assertEquals(DataResult.Success(mapOf(100 to manolo)), first.await())
            assertEquals(DataResult.Success(networkAuthors), second.await())
            assertEquals(listOf("Jaime", "Manolo"), names(list.await()))
            assertEquals(1, adapter.callCount)
        }

    @Test
    fun `the first revalidate fetches even with every id in Room and the backend replaces a stale author`() =
        runTest {
            val stale = CommentAuthorEntity(commentId = 100, collectorId = 9, collectorName = "Guardado")
            val authors = FakeCommentAuthorDao(listOf(stale))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)

            val result = repository(FakeCollectorDao(), adapter, authors).getCommentAuthors(setOf(100), true)

            assertEquals(DataResult.Success(mapOf(100 to manolo)), result)
            assertEquals(1, adapter.callCount)
            assertEquals(CommentAuthorEntity(100, 100, "Manolo"), authors.stored[100])
        }

    @Test
    fun `a second revalidate in the same repository with every id known makes no request`() =
        runTest {
            val authors = FakeCommentAuthorDao()
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)
            val repository = repository(FakeCollectorDao(), adapter, authors)
            repository.getCommentAuthors(setOf(100), true)

            val result = repository.getCommentAuthors(setOf(100, 101), true)

            assertEquals(DataResult.Success(networkAuthors), result)
            assertEquals(1, adapter.callCount)
        }

    @Test
    fun `a forced collectors refresh also makes the author index trusted`() =
        runTest {
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)
            val repository = repository(FakeCollectorDao(), adapter)
            repository.getCollectors(forceRefresh = true)

            val result = repository.getCommentAuthors(setOf(100, 101), true)

            assertEquals(DataResult.Success(networkAuthors), result)
            assertEquals(1, adapter.callCount)
        }

    @Test
    fun `a failed author write keeps Room untrusted so the next revalidate fetches again`() =
        runTest {
            val stale = CommentAuthorEntity(commentId = 100, collectorId = 9, collectorName = "Guardado")
            val authors = FakeCommentAuthorDao(listOf(stale), writeFailure = SQLiteException("disk full"))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)
            val repository = repository(FakeCollectorDao(), adapter, authors)

            val first = repository.getCommentAuthors(setOf(100), true)
            val second = repository.getCommentAuthors(setOf(100), true)

            assertEquals(DataResult.Success(mapOf(100 to manolo)), first)
            assertEquals(DataResult.Success(mapOf(100 to manolo)), second)
            assertEquals(2, adapter.callCount)
            assertEquals(stale, authors.stored[100])
        }

    @Test
    fun `a revalidate merges Room authors not in the answer with the backend ones`() =
        runTest {
            val authors = FakeCommentAuthorDao(listOf(cachedAuthor))
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)

            val result = repository(FakeCollectorDao(), adapter, authors).getCommentAuthors(setOf(9, 100), true)

            assertEquals(DataResult.Success(mapOf(9 to CommentAuthor(9, "Guardado"), 100 to manolo)), result)
            assertEquals(1, adapter.callCount)
        }

    @Test
    fun `authors are read from Room only for the requested ids`() =
        runTest {
            val authors = FakeCommentAuthorDao((1..1_000).map { authorRow(commentId = it) })
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)

            val result = repository(FakeCollectorDao(), adapter, authors).getCommentAuthors(setOf(7), false)

            assertEquals(DataResult.Success(mapOf(7 to manolo)), result)
            assertEquals(listOf(1), authors.idBatches)
        }

    @Test
    fun `thousands of comment ids are read in batches that fit in one statement`() =
        runTest {
            val ids = (1..2_000).toSet()
            val authors = FakeCommentAuthorDao(ids.map { authorRow(commentId = it) })
            val adapter = FakeCollectorServiceAdapter(collectors = network, commentAuthors = networkAuthors)

            val result = repository(FakeCollectorDao(), adapter, authors).getCommentAuthors(ids, false)

            assertEquals(ids, (result as DataResult.Success).value.keys)
            assertEquals(listOf(MAX_IDS_PER_STATEMENT, MAX_IDS_PER_STATEMENT, 200), authors.idBatches)
        }

    private fun authorRow(commentId: Int) =
        CommentAuthorEntity(commentId = commentId, collectorId = 100, collectorName = "Manolo")

    private companion object {
        // NOCASE order is alfa (2), Alfa (5), beta (3), Beta (4): case only folds, the id breaks ties.
        val unsorted =
            listOf(
                collector(id = 3, name = "beta"),
                collector(id = 5, name = "Alfa"),
                collector(id = 4, name = "Beta"),
                collector(id = 2, name = "alfa"),
            )
    }
}
