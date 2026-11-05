package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.testing.FakeCollectorDao
import co.edu.uniandes.vinilos.testing.FakeCollectorServiceAdapter
import co.edu.uniandes.vinilos.testing.collector
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
