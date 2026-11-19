package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.testing.FakeAlbumDao
import co.edu.uniandes.vinilos.testing.FakeAlbumServiceAdapter
import co.edu.uniandes.vinilos.testing.FakeCommentAuthorDao
import co.edu.uniandes.vinilos.testing.FakeTrackDao
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

@Suppress("MagicNumber") // Fixture ids and HTTP codes.
class AlbumAssociationRepositoryTest {
    @Test
    fun `association catalog preserves remote ownership instead of the empty Room relationships`() =
        runTest {
            val remote = album(100, "Occupied").copy(performers = listOf(PerformerSummary(73, "Artist")))
            val dao = FakeAlbumDao(listOf(remote.toEntity()))
            val adapter = FakeAlbumServiceAdapter(albums = listOf(remote))

            val result = repository(dao, adapter).getAlbumsForAssociation()

            assertEquals(DataResult.Success(listOf(remote)), result)
            assertEquals(1, adapter.associationCatalogCalls)
            assertEquals(0, adapter.callCount)
            assertEquals(0, dao.readCount)
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `association catalog follows Room NOCASE and id ordering without writing Room`() =
        runTest {
            val remote = listOf(album(3, "zeta"), album(5, "Ágape"), album(2, "Alfa"), album(1, "alfa"))
            val dao = FakeAlbumDao()

            val result = repository(dao, FakeAlbumServiceAdapter(albums = remote)).getAlbumsForAssociation()

            assertEquals(listOf(1, 2, 3, 5), (result as DataResult.Success).value.map { it.id })
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `association detail uses its full remote contract and never reads or writes Room`() =
        runTest {
            val remote = album(100, "Remote").copy(performers = listOf(PerformerSummary(73, "Artist")))
            val dao = FakeAlbumDao(listOf(album(100, "Stale").toEntity()))
            val adapter = FakeAlbumServiceAdapter().apply { detailResult = remote }

            assertEquals(DataResult.Success(remote), repository(dao, adapter).getAlbumForAssociation(100))
            assertEquals(listOf(100), adapter.associationDetailRequests)
            assertTrue(adapter.detailRequests.isEmpty())
            assertEquals(0, dao.readCount)
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `association reads map expected errors without touching the cached catalog`() =
        runTest {
            val failures =
                listOf(
                    IOException("offline") to DataError.Network,
                    HttpException(Response.error<Any>(404, "".toResponseBody())) to DataError.Http(404),
                    JsonParseException("relationships missing") to DataError.Serialization,
                )
            for ((failure, error) in failures) {
                val dao = FakeAlbumDao(listOf(album(1, "Cached").toEntity()))
                val adapter = FakeAlbumServiceAdapter(failure = failure)
                val repository = repository(dao, adapter)

                assertEquals(DataResult.Failure(error), repository.getAlbumsForAssociation())
                assertEquals(DataResult.Failure(error), repository.getAlbumForAssociation(100))
                assertEquals(0, dao.readCount)
                assertEquals(0, dao.writeCount)
            }
        }

    @Test
    fun `association detail rejects response for another album`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter().apply { detailResult = album(101, "Other") }

            assertEquals(
                DataResult.Failure(DataError.Serialization),
                repository(FakeAlbumDao(), adapter).getAlbumForAssociation(100),
            )
        }

    @Test
    fun `invalid association ids never reach the adapter`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter()
            val repository = repository(FakeAlbumDao(), adapter)
            for (id in listOf(0, -1)) {
                val error = runCatching { repository.getAlbumForAssociation(id) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertTrue(adapter.associationDetailRequests.isEmpty())
        }

    @Test
    fun `cancellation propagates from both association read operations`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter(failure = CancellationException("cancelled"))
            val repository = repository(FakeAlbumDao(), adapter)

            val catalogError = runCatching { repository.getAlbumsForAssociation() }.exceptionOrNull()
            val detailError = runCatching { repository.getAlbumForAssociation(100) }.exceptionOrNull()
            assertTrue(catalogError is CancellationException)
            assertTrue(detailError is CancellationException)
        }

    @Test
    fun `ordinary forced catalog refresh forwards HTTP revalidation`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter(albums = listOf(album(100, "A")))
            val repository = repository(FakeAlbumDao(), adapter)

            repository.getAlbums(forceRefresh = true)

            assertEquals(listOf(true), adapter.catalogRefreshRequests)
        }

    private fun TestScope.repository(
        dao: FakeAlbumDao,
        adapter: FakeAlbumServiceAdapter,
    ) = AlbumRepositoryImpl(dao, FakeTrackDao(), adapter, FakeCommentAuthorDao(), StandardTestDispatcher(testScheduler))

    private fun album(
        id: Int,
        name: String,
    ) = Album(
        id = id,
        name = name,
        coverUrl = "",
        releaseDate = LocalDate.of(1980, 1, 1),
        description = "",
        genre = Genre.ROCK,
        recordLabel = "EMI",
        performerName = null,
        tracks = emptyList(),
        performers = emptyList(),
        comments = emptyList(),
    )
}
