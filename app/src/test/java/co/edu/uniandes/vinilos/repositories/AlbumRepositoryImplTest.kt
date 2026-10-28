package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.testing.FakeAlbumDao
import co.edu.uniandes.vinilos.testing.FakeAlbumServiceAdapter
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
import java.time.LocalDate

class AlbumRepositoryImplTest {
    private val network =
        listOf(
            album(3, "Poeta del pueblo"),
            album(1, "A Night at the Opera"),
            album(2, "A Day at the Races"),
            album(4, "Buscando América"),
        )

    @Test
    fun `filled Room without force refresh does not call the adapter`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter(albums = network)
            val repository = AlbumRepositoryImpl(FakeAlbumDao(listOf(album(9, "Cached").toEntity())), adapter)

            val result = repository.getAlbums()

            assertEquals(0, adapter.callCount)
            assertEquals(listOf("Cached"), (result as DataResult.Success).value.map { it.name })
        }

    @Test
    fun `empty Room goes to the network, stores and returns albums alphabetically`() =
        runTest {
            val dao = FakeAlbumDao()
            val adapter = FakeAlbumServiceAdapter(albums = network)
            val repository = AlbumRepositoryImpl(dao, adapter)

            val result = repository.getAlbums()

            assertEquals(1, adapter.callCount)
            assertEquals(
                listOf("A Day at the Races", "A Night at the Opera", "Buscando América", "Poeta del pueblo"),
                (result as DataResult.Success).value.map { it.name },
            )
            assertEquals(network.size, dao.getAll().size)
        }

    @Test
    fun `force refresh goes to the network even with a cache`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter(albums = network)
            val repository = AlbumRepositoryImpl(FakeAlbumDao(listOf(album(9, "Cached").toEntity())), adapter)

            val result = repository.getAlbums(forceRefresh = true)

            assertEquals(1, adapter.callCount)
            assertEquals(network.size, (result as DataResult.Success).value.size)
        }

    @Test
    fun `IO failure returns a network error and leaves Room intact`() =
        runTest {
            val dao = FakeAlbumDao(listOf(album(9, "Cached").toEntity()))
            val repository = AlbumRepositoryImpl(dao, FakeAlbumServiceAdapter(failure = IOException("offline")))

            val result = repository.getAlbums(forceRefresh = true)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(listOf("Cached"), dao.getAll().map { it.name })
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `has cached albums reflects the Room content`() =
        runTest {
            assertFalse(AlbumRepositoryImpl(FakeAlbumDao(), FakeAlbumServiceAdapter()).hasCachedAlbums())
            assertTrue(
                AlbumRepositoryImpl(FakeAlbumDao(listOf(album(1, "A").toEntity())), FakeAlbumServiceAdapter())
                    .hasCachedAlbums(),
            )
        }

    @Test
    fun `concurrent calls with empty Room make a single network call`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val adapter = FakeAlbumServiceAdapter(albums = network, gate = gate)
            val repository = AlbumRepositoryImpl(FakeAlbumDao(), adapter)

            val calls = List(2) { async { repository.getAlbums() } }
            runCurrent()
            gate.complete(Unit)
            val results = calls.awaitAll()

            assertEquals(1, adapter.callCount)
            assertEquals(results[0], results[1])
        }

    @Test
    fun `network failure with empty Room leaves Room empty`() =
        runTest {
            val dao = FakeAlbumDao()
            val repository = AlbumRepositoryImpl(dao, FakeAlbumServiceAdapter(failure = IOException("offline")))

            val result = repository.getAlbums()

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertTrue(dao.getAll().isEmpty())
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `HTTP error returns an HTTP data error`() =
        runTest {
            val error = HttpException(Response.error<Any>(HTTP_SERVER_ERROR, "".toResponseBody()))
            val repository = AlbumRepositoryImpl(FakeAlbumDao(), FakeAlbumServiceAdapter(failure = error))

            assertEquals(DataResult.Failure(DataError.Http(HTTP_SERVER_ERROR)), repository.getAlbums())
        }

    private companion object {
        const val HTTP_SERVER_ERROR = 500
    }

    private fun album(
        id: Int,
        name: String,
    ) = Album(
        id = id,
        name = name,
        coverUrl = "",
        releaseDate = LocalDate.of(1984, 8, 1),
        description = "",
        genre = Genre.SALSA,
        recordLabel = "",
        performerName = null,
        tracks = emptyList(),
        performers = emptyList(),
        comments = emptyList(),
    )
}
