package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.testing.FakePerformerDao
import co.edu.uniandes.vinilos.testing.FakePerformerServiceAdapter
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
import com.google.gson.JsonParseException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@Suppress("MagicNumber") // Fixture ids and counts.
class PerformerRepositoryImplTest {
    private val queen = band(id = 101, name = "Queen", albums = 2, members = 2)
    private val brian = musician(id = 103, name = "Brian May", bandId = 101, bandName = "Queen")
    private val freddie = musician(id = 102, name = "Freddie Mercury", bandId = 101, bandName = "Queen")
    private val ruben = musician(id = 100, name = "Rubén Blades", year = 1948, albums = 2)
    private val network = listOf(ruben, queen, freddie, brian)

    private fun repository(
        dao: FakePerformerDao,
        adapter: FakePerformerServiceAdapter,
    ) = PerformerRepositoryImpl(dao, adapter)

    private suspend fun loadFromNetwork(performers: List<Performer>) =
        repository(FakePerformerDao(), FakePerformerServiceAdapter(performers = performers)).getPerformers()

    private fun names(result: DataResult<List<Performer>, DataError>) =
        (result as DataResult.Success).value.map { it.name }

    @Test
    fun `filled Room without force refresh does not call the adapter`() =
        runTest {
            val adapter = FakePerformerServiceAdapter(performers = network)
            val dao = FakePerformerDao(listOf(band(9, "Cached").toEntity()))

            val result = repository(dao, adapter).getPerformers()

            assertEquals(0, adapter.callCount)
            assertEquals(listOf("Cached"), names(result))
        }

    @Test
    fun `empty Room goes to the network, stores and returns the performers by name`() =
        runTest {
            val dao = FakePerformerDao()
            val adapter = FakePerformerServiceAdapter(performers = network)

            val result = repository(dao, adapter).getPerformers()

            assertEquals(1, adapter.callCount)
            assertEquals(listOf("Brian May", "Freddie Mercury", "Queen", "Rubén Blades"), names(result))
            assertEquals(network.size, dao.count())
        }

    @Test
    fun `the order ignores case and puts equal names in id order`() =
        runTest {
            val performers =
                listOf(
                    musician(id = 3, name = "zeta"),
                    band(id = 2, name = "Alfa"),
                    musician(id = 1, name = "alfa"),
                    musician(id = 4, name = "Beta"),
                )

            val result = loadFromNetwork(performers)

            assertEquals(listOf(1, 2, 4, 3), (result as DataResult.Success).value.map { it.id })
        }

    @Test
    fun `members come back with the name of their band after a round trip through Room`() =
        runTest {
            val result = loadFromNetwork(network)

            val members = (result as DataResult.Success).value.filter { it.bandId != null }
            assertEquals(listOf("Brian May", "Freddie Mercury"), members.map { it.name })
            assertTrue(members.all { it.bandName == "Queen" })
            assertEquals(null, result.value.single { it.name == "Rubén Blades" }.bandName)
        }

    @Test
    fun `force refresh goes to the network even with a cache and replaces what Room had`() =
        runTest {
            val dao = FakePerformerDao(listOf(band(9, "Cached").toEntity()))
            val adapter = FakePerformerServiceAdapter(performers = network)

            val result = repository(dao, adapter).getPerformers(forceRefresh = true)

            assertEquals(1, adapter.callCount)
            assertEquals(network.size, (result as DataResult.Success).value.size)
            assertEquals(network.size, dao.count())
            assertFalse(dao.getAll().any { it.name == "Cached" })
        }

    @Test
    fun `an empty backend answer empties Room`() =
        runTest {
            val dao = FakePerformerDao(listOf(band(9, "Cached").toEntity()))

            val result = repository(dao, FakePerformerServiceAdapter()).getPerformers(forceRefresh = true)

            assertEquals(DataResult.Success(emptyList<Performer>()), result)
            assertEquals(0, dao.count())
        }

    @Test
    fun `a failed load with a cache returns the failure and leaves Room intact`() =
        runTest {
            val dao = FakePerformerDao(listOf(band(9, "Cached").toEntity()))
            val adapter = FakePerformerServiceAdapter(failure = IOException("one of the two calls failed"))

            val result = repository(dao, adapter).getPerformers(forceRefresh = true)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(listOf("Cached"), dao.getAll().map { it.name })
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `a failed load with empty Room leaves Room empty`() =
        runTest {
            val dao = FakePerformerDao()
            val adapter = FakePerformerServiceAdapter(failure = IOException("offline"))

            val result = repository(dao, adapter).getPerformers()

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(0, dao.count())
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `HTTP and serialization failures become data errors and do not touch Room`() =
        runTest {
            val http = HttpException(Response.error<Any>(500, "".toResponseBody()))
            val dao = FakePerformerDao()

            val httpResult = repository(dao, FakePerformerServiceAdapter(failure = http)).getPerformers()
            val jsonResult =
                repository(dao, FakePerformerServiceAdapter(failure = JsonParseException("bad"))).getPerformers()

            assertEquals(DataResult.Failure(DataError.Http(500)), httpResult)
            assertEquals(DataResult.Failure(DataError.Serialization), jsonResult)
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `has cached performers reflects the Room content`() =
        runTest {
            val adapter = FakePerformerServiceAdapter()

            assertFalse(repository(FakePerformerDao(), adapter).hasCachedPerformers())
            assertTrue(repository(FakePerformerDao(listOf(band(1, "A").toEntity())), adapter).hasCachedPerformers())
        }

    @Test
    fun `concurrent calls with empty Room make a single network call`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val adapter = FakePerformerServiceAdapter(performers = network, gate = gate)
            val repository = repository(FakePerformerDao(), adapter)

            val calls = List(2) { async { repository.getPerformers() } }
            runCurrent()
            gate.complete(Unit)
            val results = calls.awaitAll()

            assertEquals(1, adapter.callCount)
            assertEquals(results[0], results[1])
        }
}
