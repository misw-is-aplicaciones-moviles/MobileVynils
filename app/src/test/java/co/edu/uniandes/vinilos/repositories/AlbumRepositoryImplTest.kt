package co.edu.uniandes.vinilos.repositories

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.entities.TrackEntity
import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.testing.FakeAlbumDao
import co.edu.uniandes.vinilos.testing.FakeAlbumServiceAdapter
import co.edu.uniandes.vinilos.testing.FakeTrackDao
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
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
            val repository =
                AlbumRepositoryImpl(
                    FakeAlbumDao(listOf(album(9, "Cached").toEntity())),
                    FakeTrackDao(),
                    adapter,
                )

            val result = repository.getAlbums()

            assertEquals(0, adapter.callCount)
            assertEquals(listOf("Cached"), (result as DataResult.Success).value.map { it.name })
        }

    @Test
    fun `empty Room goes to the network, stores and returns albums alphabetically`() =
        runTest {
            val dao = FakeAlbumDao()
            val adapter = FakeAlbumServiceAdapter(albums = network)
            val repository = AlbumRepositoryImpl(dao, FakeTrackDao(), adapter)

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
            val repository =
                AlbumRepositoryImpl(
                    FakeAlbumDao(listOf(album(9, "Cached").toEntity())),
                    FakeTrackDao(),
                    adapter,
                )

            val result = repository.getAlbums(forceRefresh = true)

            assertEquals(1, adapter.callCount)
            assertEquals(network.size, (result as DataResult.Success).value.size)
        }

    @Test
    fun `IO failure returns a network error and leaves Room intact`() =
        runTest {
            val dao = FakeAlbumDao(listOf(album(9, "Cached").toEntity()))
            val repository =
                AlbumRepositoryImpl(
                    dao,
                    FakeTrackDao(),
                    FakeAlbumServiceAdapter(failure = IOException("offline")),
                )

            val result = repository.getAlbums(forceRefresh = true)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(listOf("Cached"), dao.getAll().map { it.name })
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `has cached albums reflects the Room content`() =
        runTest {
            assertFalse(
                AlbumRepositoryImpl(FakeAlbumDao(), FakeTrackDao(), FakeAlbumServiceAdapter()).hasCachedAlbums(),
            )
            assertTrue(
                AlbumRepositoryImpl(
                    FakeAlbumDao(listOf(album(1, "A").toEntity())),
                    FakeTrackDao(),
                    FakeAlbumServiceAdapter(),
                )
                    .hasCachedAlbums(),
            )
        }

    @Test
    fun `concurrent calls with empty Room make a single network call`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val adapter = FakeAlbumServiceAdapter(albums = network, gate = gate)
            val repository = AlbumRepositoryImpl(FakeAlbumDao(), FakeTrackDao(), adapter)

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
            val repository =
                AlbumRepositoryImpl(
                    dao,
                    FakeTrackDao(),
                    FakeAlbumServiceAdapter(failure = IOException("offline")),
                )

            val result = repository.getAlbums()

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertTrue(dao.getAll().isEmpty())
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `HTTP error returns an HTTP data error`() =
        runTest {
            val error = HttpException(Response.error<Any>(HTTP_SERVER_ERROR, "".toResponseBody()))
            val repository =
                AlbumRepositoryImpl(FakeAlbumDao(), FakeTrackDao(), FakeAlbumServiceAdapter(failure = error))

            assertEquals(DataResult.Failure(DataError.Http(HTTP_SERVER_ERROR)), repository.getAlbums())
        }

    @Test
    fun `detail returns complete remote data without reading or writing the catalog cache`() =
        runTest {
            val cached = album(1, "Cached summary").toEntity()
            val dao = FakeAlbumDao(listOf(cached))
            val complete =
                album(1, "Remote detail").copy(
                    tracks = listOf(Track(10, "Song", "3:25")),
                    performers = listOf(PerformerSummary(7, "Artist")),
                    comments = listOf(Comment(20, "Review", 5)),
                )
            val adapter = FakeAlbumServiceAdapter().apply { detailResult = complete }

            val result = AlbumRepositoryImpl(dao, FakeTrackDao(), adapter).getAlbum(1)

            assertEquals(DataResult.Success(complete), result)
            assertEquals(listOf(1 to false), adapter.detailRequests)
            assertEquals(0, adapter.callCount)
            assertEquals(0, dao.readCount)
            assertEquals(0, dao.writeCount)
            assertEquals(listOf(cached), dao.getAll())
        }

    @Test
    fun `detail forwards force refresh to the shared adapter`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter(albums = network)

            AlbumRepositoryImpl(FakeAlbumDao(), FakeTrackDao(), adapter).getAlbum(1, forceRefresh = true)

            assertEquals(listOf(1 to true), adapter.detailRequests)
        }

    @Test
    fun `detail errors are returned without mutating the catalog cache`() =
        runTest {
            val failures =
                listOf(
                    IOException("offline") to DataError.Network,
                    HttpException(Response.error<Any>(404, "".toResponseBody())) to DataError.Http(404),
                    JsonParseException("bad detail") to DataError.Serialization,
                )
            for ((failure, expected) in failures) {
                val dao = FakeAlbumDao(listOf(album(9, "Cached").toEntity()))

                val result =
                    AlbumRepositoryImpl(dao, FakeTrackDao(), FakeAlbumServiceAdapter(failure = failure)).getAlbum(1)

                assertEquals(DataResult.Failure(expected), result)
                assertEquals(0, dao.readCount)
                assertEquals(0, dao.writeCount)
            }
        }

    @Test
    fun `detail with a different response id is rejected`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter().apply { detailResult = album(2, "Other") }

            val result = AlbumRepositoryImpl(FakeAlbumDao(), FakeTrackDao(), adapter).getAlbum(1)

            assertEquals(DataResult.Failure(DataError.Serialization), result)
        }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid detail id does not reach the adapter`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter(albums = network)
            try {
                AlbumRepositoryImpl(FakeAlbumDao(), FakeTrackDao(), adapter).getAlbum(0)
            } finally {
                assertTrue(adapter.detailRequests.isEmpty())
            }
        }

    @Test(expected = CancellationException::class)
    fun `detail cancellation is propagated`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter(failure = CancellationException("cancelled"))

            AlbumRepositoryImpl(FakeAlbumDao(), FakeTrackDao(), adapter).getAlbum(1)
        }

    @Test
    fun `created track is cached with the requested album id`() =
        runTest {
            val track = Track(777, "Bohemian Rhapsody", "5:55")
            val draft = NewTrack("Bohemian Rhapsody", "5:55")
            val trackDao = FakeTrackDao()
            val adapter = FakeAlbumServiceAdapter(createdTrack = track)

            val result = AlbumRepositoryImpl(FakeAlbumDao(), trackDao, adapter).addTrack(100, draft)

            assertEquals(DataResult.Success(track), result)
            assertEquals(listOf(100 to draft), adapter.addedTrackRequests)
            assertEquals(listOf(TrackEntity(777, "Bohemian Rhapsody", "5:55", 100)), trackDao.getByAlbum(100))
            assertTrue(trackDao.getByAlbum(101).isEmpty())
        }

    @Test
    fun `track remains successful when Room cannot cache a backend success`() =
        runTest {
            val track = Track(777, "Bohemian Rhapsody", "5:55")
            val trackDao = FakeTrackDao().apply { writeFailure = SQLiteException("disk full") }
            val adapter = FakeAlbumServiceAdapter(createdTrack = track)

            val result = AlbumRepositoryImpl(FakeAlbumDao(), trackDao, adapter)
                .addTrack(100, NewTrack(track.name, track.duration))

            assertEquals(DataResult.Success(track), result)
            assertEquals(1, trackDao.writeCount)
        }

    @Test
    fun `track network failure is returned and leaves Room untouched`() =
        runTest {
            val trackDao = FakeTrackDao()
            val adapter = FakeAlbumServiceAdapter(failure = IOException("offline"))

            val result = AlbumRepositoryImpl(FakeAlbumDao(), trackDao, adapter)
                .addTrack(100, NewTrack("Bohemian Rhapsody", "5:55"))

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(0, trackDao.writeCount)
        }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid track album id does not reach the adapter`() =
        runTest {
            val adapter = FakeAlbumServiceAdapter(createdTrack = Track(1, "Song", "1:00"))
            try {
                AlbumRepositoryImpl(FakeAlbumDao(), FakeTrackDao(), adapter)
                    .addTrack(0, NewTrack("Song", "1:00"))
            } finally {
                assertTrue(adapter.addedTrackRequests.isEmpty())
            }
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
