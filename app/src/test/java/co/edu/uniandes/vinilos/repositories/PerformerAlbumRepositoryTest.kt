package co.edu.uniandes.vinilos.repositories

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.FakePerformerDao
import co.edu.uniandes.vinilos.testing.FakePerformerServiceAdapter
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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

@Suppress("MagicNumber") // Fixture identities, counts and HTTP codes.
class PerformerAlbumRepositoryTest {
    @Test
    fun `album catalog is invalidated after successful failed and cancelled association writes`() =
        runTest {
            for (failure in listOf(null, IOException("lost response"), CancellationException("cancelled"))) {
                val albums = FakeAlbumRepository(hasCache = true)
                val adapter = FakePerformerServiceAdapter(failure = failure)
                val repository =
                    PerformerRepositoryImpl(
                        FakePerformerDao(),
                        adapter,
                        StandardTestDispatcher(testScheduler),
                        albums,
                    )
                runCatching { repository.addAlbum(73, PerformerType.MUSICIAN, 100) }
                assertEquals(1, albums.invalidationCount)
                assertFalse(albums.hasCachedAlbums())
            }
        }

    @Test
    fun `association forwards the exact identity for either type without overwriting the list cache`() =
        runTest {
            for (type in PerformerType.entries) {
                val cached = band(73, "Cached", albums = 2).toEntity()
                val dao = FakePerformerDao(listOf(cached))
                val adapter = FakePerformerServiceAdapter()
                val repository = repository(dao, adapter)

                assertEquals(DataResult.Success(Unit), repository.addAlbum(73, type, 100))
                assertEquals(listOf(Triple(73, type, 100)), adapter.addedAlbumRequests)
                assertEquals(listOf(cached), dao.getAll())
                assertEquals(0, dao.writeCount)
                assertEquals(0, adapter.callCount)
                assertTrue(adapter.detailRequests.isEmpty())
                assertFalse(repository.hasCachedPerformers())
            }
        }

    @Test
    fun `next detail revalidates and a successful catalog refresh makes the new Room summary usable`() =
        runTest {
            val artist = musician(73, "Artist", albums = 3)
            val adapter =
                FakePerformerServiceAdapter(performers = listOf(artist)).apply {
                    detail = PerformerDetail(artist, "", emptyList(), emptyList())
                }
            val dao = FakePerformerDao(listOf(artist.copy(albumCount = 2).toEntity()))
            val repository = repository(dao, adapter)

            repository.addAlbum(73, PerformerType.MUSICIAN, 100)
            assertEquals(DataResult.Success(adapter.detail), repository.getPerformer(73, PerformerType.MUSICIAN))
            assertEquals(listOf(Triple(73, PerformerType.MUSICIAN, true)), adapter.detailRequests)
            assertFalse(repository.hasCachedPerformers())
            val refreshed = repository.getPerformers()
            assertEquals(3, (refreshed as DataResult.Success).value.single().albumCount)
            assertEquals(listOf(true), adapter.catalogRefreshRequests)
            assertTrue(repository.hasCachedPerformers())
            repository.getPerformers()
            assertEquals(1, adapter.callCount)
        }

    @Test
    fun `lost response maps to network error but still makes subsequent reads revalidate`() =
        runTest {
            val artist = musician(73, "Artist")
            val adapter = FakePerformerServiceAdapter(failure = IOException("response lost"))
            val repository = repository(FakePerformerDao(listOf(artist.toEntity())), adapter)

            assertEquals(DataResult.Failure(DataError.Network), repository.addAlbum(73, PerformerType.MUSICIAN, 100))
            assertFalse(repository.hasCachedPerformers())
            assertEquals(1, adapter.addedAlbumRequests.size)
            adapter.failure = null
            adapter.detail = PerformerDetail(artist, "", emptyList(), emptyList())
            repository.getPerformer(73, PerformerType.MUSICIAN)
            assertEquals(listOf(Triple(73, PerformerType.MUSICIAN, true)), adapter.detailRequests)
            assertEquals(1, adapter.addedAlbumRequests.size)
        }

    @Test
    fun `HTTP and serialization failures preserve their category and do not write Room`() =
        runTest {
            val failures =
                listOf(
                    HttpException(Response.error<Any>(404, "".toResponseBody())) to DataError.Http(404),
                    JsonParseException("unexpected album") to DataError.Serialization,
                )
            for ((failure, error) in failures) {
                val dao = FakePerformerDao()
                val adapter = FakePerformerServiceAdapter(failure = failure)
                assertEquals(DataResult.Failure(error), repository(dao, adapter).addAlbum(73, PerformerType.BAND, 100))
                assertEquals(0, dao.writeCount)
                assertEquals(1, adapter.addedAlbumRequests.size)
            }
        }

    @Test
    fun `invalid ids neither reach the adapter nor invalidate an existing list`() =
        runTest {
            val adapter = FakePerformerServiceAdapter()
            val repository = repository(FakePerformerDao(listOf(band(73, "Band").toEntity())), adapter)
            for ((artistId, albumId) in listOf(0 to 100, -1 to 100, 73 to 0, 73 to -1)) {
                val error = runCatching { repository.addAlbum(artistId, PerformerType.BAND, albumId) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertTrue(adapter.addedAlbumRequests.isEmpty())
            assertTrue(repository.hasCachedPerformers())
        }

    @Test
    fun `cancellation propagates and the next catalog read still revalidates`() =
        runTest {
            val adapter = FakePerformerServiceAdapter(failure = CancellationException("cancelled after send"))
            val repository = repository(FakePerformerDao(), adapter)

            val error = runCatching { repository.addAlbum(73, PerformerType.BAND, 100) }.exceptionOrNull()

            assertTrue(error is CancellationException)
            adapter.failure = null
            repository.getPerformers()
            assertEquals(listOf(true), adapter.catalogRefreshRequests)
            assertEquals(1, adapter.addedAlbumRequests.size)
        }

    @Test
    fun `failed refresh after an association never makes stale Room eligible again`() =
        runTest {
            val artist = band(73, "Band", albums = 2)
            val adapter = FakePerformerServiceAdapter()
            val repository = repository(FakePerformerDao(listOf(artist.toEntity())), adapter)
            repository.addAlbum(73, PerformerType.BAND, 100)
            adapter.failure = IOException("offline")

            assertEquals(DataResult.Failure(DataError.Network), repository.getPerformers())
            assertFalse(repository.hasCachedPerformers())
            adapter.failure = null
            adapter.performers = listOf(artist.copy(albumCount = 3))
            val result = repository.getPerformers()
            assertEquals(3, (result as DataResult.Success).value.single().albumCount)
            assertEquals(listOf(true, true), adapter.catalogRefreshRequests)
        }

    @Test
    fun `Room write failure does not undo association success or make stale summaries usable`() =
        runTest {
            val old = band(73, "Band", albums = 2)
            val dao = FakePerformerDao(listOf(old.toEntity())).apply { writeFailure = SQLiteException("disk full") }
            val adapter = FakePerformerServiceAdapter(performers = listOf(old.copy(albumCount = 3)))
            val repository = repository(dao, adapter)

            assertEquals(DataResult.Success(Unit), repository.addAlbum(73, PerformerType.BAND, 100))
            assertEquals(3, (repository.getPerformers() as DataResult.Success).value.single().albumCount)
            assertFalse(repository.hasCachedPerformers())
            assertEquals(2, dao.getAll().single().albumCount)
            assertEquals(1, adapter.addedAlbumRequests.size)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a catalog read started before the write cannot clear the association invalidation`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val adapter = FakePerformerServiceAdapter(performers = listOf(band(73, "Band")), gate = gate)
            val repository = repository(FakePerformerDao(), adapter)
            val refresh = async { repository.getPerformers(forceRefresh = true) }
            runCurrent()
            val write = async { repository.addAlbum(73, PerformerType.BAND, 100) }
            runCurrent()
            gate.complete(Unit)
            refresh.await()
            assertEquals(DataResult.Success(Unit), write.await())

            assertFalse(repository.hasCachedPerformers())
            adapter.gate = null
            repository.getPerformers()
            assertEquals(listOf(true, true), adapter.catalogRefreshRequests)
        }

    private fun TestScope.repository(
        dao: FakePerformerDao,
        adapter: FakePerformerServiceAdapter,
    ) = PerformerRepositoryImpl(dao, adapter, StandardTestDispatcher(testScheduler), FakeAlbumRepository())
}
