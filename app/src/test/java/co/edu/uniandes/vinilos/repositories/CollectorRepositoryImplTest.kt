package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.testing.FakeCollectorDao
import co.edu.uniandes.vinilos.testing.FakeCollectorServiceAdapter
import co.edu.uniandes.vinilos.testing.collector
import co.edu.uniandes.vinilos.testing.collectorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@Suppress("MagicNumber") // Fixture ids.
class CollectorRepositoryImplTest {
    private val cached = collector(id = 9, name = "Guardado")
    private val network = listOf(collector(id = 100, name = "Manolo"), collector(id = 101, name = "Jaime"))

    private fun TestScope.repository(
        dao: FakeCollectorDao,
        adapter: FakeCollectorServiceAdapter,
    ) = CollectorRepositoryImpl(dao, adapter, StandardTestDispatcher(testScheduler))

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
}
