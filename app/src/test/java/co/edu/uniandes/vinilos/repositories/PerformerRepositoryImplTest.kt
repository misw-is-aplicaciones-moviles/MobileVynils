package co.edu.uniandes.vinilos.repositories

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.FakePerformerDao
import co.edu.uniandes.vinilos.testing.FakePerformerServiceAdapter
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    private fun TestScope.repository(
        dao: FakePerformerDao,
        adapter: FakePerformerServiceAdapter,
    ) = PerformerRepositoryImpl(dao, adapter, StandardTestDispatcher(testScheduler), FakeAlbumRepository())

    private suspend fun TestScope.loadFromNetwork(performers: List<Performer>) =
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
    fun `an unreadable Room says there is no cache instead of crashing`() =
        runTest {
            val dao = FakePerformerDao(listOf(band(1, "A").toEntity()))
            dao.readFailure = SQLiteException("corrupt")

            assertFalse(repository(dao, FakePerformerServiceAdapter()).hasCachedPerformers())
        }

    @Test
    fun `a failing cache read goes to the network and stores its answer`() =
        runTest {
            val dao = FakePerformerDao().apply { readFailure = SQLiteException("corrupt") }
            val adapter = FakePerformerServiceAdapter(performers = network)

            val result = repository(dao, adapter).getPerformers()

            assertEquals(1, adapter.callCount)
            assertEquals(listOf("Brian May", "Freddie Mercury", "Queen", "Rubén Blades"), names(result))
            dao.readFailure = null
            assertEquals(network.size, dao.count())
        }

    @Test
    fun `a failing write shows the backend answer in the order Room would have given`() =
        runTest {
            val dao = FakePerformerDao().apply { writeFailure = SQLiteException("disk full") }
            val fromBackend =
                listOf(
                    musician(id = 3, name = "zeta"),
                    musician(id = 5, name = "Ángel"),
                    musician(id = 1, name = "alfa", bandId = 2, bandName = "Alfa"),
                    musician(id = 4, name = "Beta"),
                    band(id = 2, name = "Alfa"),
                )

            val result = repository(dao, FakePerformerServiceAdapter(performers = fromBackend)).getPerformers()

            // COLLATE NOCASE only folds A-Z: "Ángel" goes after "zeta", and equal names keep the id order.
            assertEquals(listOf(1, 2, 4, 3, 5), (result as DataResult.Success).value.map { it.id })
            assertEquals("Alfa", result.value.first().bandName)
            assertEquals(0, dao.count())
        }

    @Test
    fun `a failing cache read without network returns the network error`() =
        runTest {
            val dao = FakePerformerDao().apply { readFailure = SQLiteException("corrupt") }
            val adapter = FakePerformerServiceAdapter(failure = IOException("offline"))

            val result = repository(dao, adapter).getPerformers()

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(0, dao.writeCount)
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

    @Test
    fun `detail bypasses list cache forwards refresh and leaves saved artists intact`() =
        runTest {
            val cached = listOf(ruben.toEntity(), queen.toEntity())
            val dao = FakePerformerDao(cached)
            val adapter =
                FakePerformerServiceAdapter().apply {
                    detail =
                        PerformerDetail(
                            musician(100, "Información actualizada"),
                            "Descripción",
                            emptyList(),
                            emptyList(),
                        )
                }

            val result = repository(dao, adapter).getPerformer(100, PerformerType.MUSICIAN, forceRefresh = true)

            assertEquals(DataResult.Success(adapter.detail), result)
            assertEquals(listOf(Triple(100, PerformerType.MUSICIAN, true)), adapter.detailRequests)
            assertEquals(0, adapter.callCount)
            assertEquals(cached.sortedBy { it.name }, dao.getAll())
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `detail rejects a mismatched id or type without touching Room`() =
        runTest {
            val dao = FakePerformerDao(listOf(ruben.toEntity()))
            val adapter = FakePerformerServiceAdapter()
            val repository = repository(dao, adapter)
            listOf(musician(999, "Otro"), band(100, "Otro tipo")).forEach { wrong ->
                adapter.detail = PerformerDetail(wrong, "D", emptyList(), emptyList())

                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    repository.getPerformer(100, PerformerType.MUSICIAN),
                )
            }
            assertEquals(listOf(ruben.toEntity()), dao.getAll())
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `non positive detail ids are rejected before invoking the adapter`() =
        runTest {
            val dao = FakePerformerDao()
            val adapter = FakePerformerServiceAdapter()
            val repository = repository(dao, adapter)
            listOf(0, -1).forEach { id ->
                val error = runCatching { repository.getPerformer(id, PerformerType.MUSICIAN) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertTrue(adapter.detailRequests.isEmpty())
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `cancelling a detail propagates cancellation and never modifies the list cache`() =
        runTest {
            val dao = FakePerformerDao(listOf(ruben.toEntity()))
            val adapter = FakePerformerServiceAdapter(failure = CancellationException("cancelled"))

            val error =
                runCatching {
                    repository(dao, adapter).getPerformer(100, PerformerType.MUSICIAN)
                }.exceptionOrNull()

            assertTrue(error is CancellationException)
            assertEquals(listOf(ruben.toEntity()), dao.getAll())
            assertEquals(0, dao.writeCount)
        }

    private val adam = musician(id = 104, name = "Adam Lambert", year = 1982)
    private val adamMember = PerformerSummary(104, "Adam Lambert", null, PerformerType.MUSICIAN)

    private fun cachedBandAndFreeMusician() = FakePerformerDao(listOf(queen.toEntity(), adam.toEntity()))

    @Test
    fun `added musician updates the band and the musician in Room`() =
        runTest {
            val dao = cachedBandAndFreeMusician()
            val adapter = FakePerformerServiceAdapter().apply { addedMember = adamMember }

            val result = repository(dao, adapter).addMusicianToBand(101, 104)

            assertEquals(DataResult.Success(adamMember), result)
            assertEquals(listOf(101 to 104), adapter.addRequests)
            assertEquals(101, dao.row(104)?.bandId)
            assertEquals(3, dao.row(101)?.memberCount)
        }

    @Test
    fun `applying the same membership twice counts the member once`() =
        runTest {
            val dao = cachedBandAndFreeMusician()
            val repository = repository(dao, FakePerformerServiceAdapter().apply { addedMember = adamMember })

            repository.addMusicianToBand(101, 104)
            repository.addMusicianToBand(101, 104)

            assertEquals(3, dao.row(101)?.memberCount)
        }

    @Test
    fun `Room failure after the POST still reports success`() =
        runTest {
            val dao = cachedBandAndFreeMusician().apply { writeFailure = SQLiteException("disk full") }
            val adapter = FakePerformerServiceAdapter().apply { addedMember = adamMember }

            val result = repository(dao, adapter).addMusicianToBand(101, 104)

            assertEquals(DataResult.Success(adamMember), result)
        }

    @Test
    fun `not found returns HTTP 404 and leaves Room intact`() =
        runTest {
            val dao = cachedBandAndFreeMusician()
            val adapter =
                FakePerformerServiceAdapter().apply {
                    addFailure = HttpException(Response.error<Any>(404, "".toResponseBody()))
                }

            val result = repository(dao, adapter).addMusicianToBand(101, 104)

            assertEquals(DataResult.Failure(DataError.Http(404)), result)
            assertEquals(0, dao.writeCount)
            assertEquals(null, dao.row(104)?.bandId)
        }

    @Test
    fun `IO failure returns a network error and leaves Room intact`() =
        runTest {
            val dao = cachedBandAndFreeMusician()
            val adapter = FakePerformerServiceAdapter().apply { addFailure = IOException("offline") }

            val result = repository(dao, adapter).addMusicianToBand(101, 104)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `unexpected musician identity is a serialization error`() =
        runTest {
            val dao = cachedBandAndFreeMusician()
            val adapter =
                FakePerformerServiceAdapter().apply { addFailure = JsonParseException("Unexpected musician identity") }

            val result = repository(dao, adapter).addMusicianToBand(101, 104)

            assertEquals(DataResult.Failure(DataError.Serialization), result)
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `non positive ids are rejected before the network`() =
        runTest {
            val adapter = FakePerformerServiceAdapter()
            val repository = repository(FakePerformerDao(), adapter)
            listOf(0 to 104, -1 to 104, 101 to 0, 101 to -1).forEach { (bandId, musicianId) ->
                val error = runCatching { repository.addMusicianToBand(bandId, musicianId) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertTrue(adapter.addRequests.isEmpty())
        }

    @Test
    fun `refresh fetched before the POST does not undo the membership`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val dao = cachedBandAndFreeMusician()
            val adapter =
                FakePerformerServiceAdapter(performers = listOf(queen, adam), gate = gate).apply {
                    addedMember = adamMember
                }
            val repository = repository(dao, adapter)

            val refresh = async { repository.getPerformers(forceRefresh = true) }
            runCurrent()
            val add = async { repository.addMusicianToBand(101, 104) }
            runCurrent()
            gate.complete(Unit)
            refresh.await()
            add.await()

            assertEquals(101, dao.row(104)?.bandId)
            assertEquals(3, dao.row(101)?.memberCount)
        }
}
