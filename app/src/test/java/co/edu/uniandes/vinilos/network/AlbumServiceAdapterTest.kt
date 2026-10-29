package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.RecordLabel
import co.edu.uniandes.vinilos.repositories.AlbumRepositoryImpl
import co.edu.uniandes.vinilos.testing.FakeAlbumDao
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.util.concurrent.Executors

class AlbumServiceAdapterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val serverRule = MockWebServerRule()

    private val clients = mutableListOf<NetworkClient>()

    @After
    fun closeClients() {
        clients.forEach(NetworkClient::close)
    }

    @Test
    fun `deserializes the backend albums`() =
        runTest {
            serverRule.enqueueJson("api/albums.json")

            val albums = newAdapter(testScheduler).getAlbums()

            assertEquals(4, albums.size)
            val first = albums.first()
            assertEquals("Buscando América", first.name)
            assertEquals("Rubén Blades Bellido de Luna", first.performerName)
            assertEquals(Genre.SALSA, first.genre)
            assertEquals(1984, first.releaseDate.year)
            assertEquals("/albums", serverRule.server.takeRequest().path)
        }

    @Test
    fun `server error becomes an HTTP data error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(HTTP_SERVER_ERROR))
            val adapter = newAdapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Http(HTTP_SERVER_ERROR)),
                networkRequest { adapter.getAlbums() },
            )
        }

    @Test
    fun `malformed JSON becomes a serialization error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody("[{"))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbums() })
        }

    @Test
    fun `album without a name becomes a serialization error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody(ALBUM_WITHOUT_NAME))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbums() })
        }

    @Test
    fun `api provider is invoked once across two calls`() =
        runTest {
            repeat(2) { serverRule.enqueueJson("api/albums.json") }
            var providerCalls = 0
            val adapter = newAdapter(testScheduler) { providerCalls++ }

            adapter.getAlbums()
            adapter.getAlbums()

            assertEquals(1, providerCalls)
        }

    @Test
    fun `constructing the adapter does not invoke the provider`() =
        runTest {
            var providerCalls = 0

            newAdapter(testScheduler) { providerCalls++ }

            assertEquals(0, providerCalls)
            assertTrue(clients.isEmpty())
        }

    @Test
    fun `api provider runs on the IO dispatcher`() =
        runTest {
            serverRule.enqueueJson("api/albums.json")
            val executor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD_NAME) }
            try {
                var providerThread: String? = null
                val adapter =
                    newAdapter(
                        scheduler = testScheduler,
                        ioDispatcher = executor.asCoroutineDispatcher(),
                    ) { providerThread = Thread.currentThread().name }

                adapter.getAlbums()

                assertEquals(IO_THREAD_NAME, providerThread)
            } finally {
                executor.shutdown()
            }
        }

    @Test
    fun `create album posts exactly the six backend fields and maps the saved album`() =
        runTest {
            serverRule.enqueueJson("api/album_created.json")

            val album = newAdapter(testScheduler).createAlbum(draft)

            val request = serverRule.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/albums", request.path)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("name", "cover", "releaseDate", "description", "genre", "recordLabel"), body.keySet())
            assertEquals("The Game", body["name"].asString)
            assertEquals("https://example.com/the-game.jpg", body["cover"].asString)
            assertEquals("1980-06-30T00:00:00.000Z", body["releaseDate"].asString)
            assertEquals("Noveno álbum de Queen.", body["description"].asString)
            assertEquals("Rock", body["genre"].asString)
            assertEquals("EMI", body["recordLabel"].asString)
            assertEquals(101, album.id)
            assertEquals("The Game", album.name)
            assertEquals(Genre.ROCK, album.genre)
            assertEquals("EMI", album.recordLabel)
            assertEquals(LocalDate.of(1980, 6, 30), album.releaseDate)
        }

    @Test
    fun `create album with a missing field becomes HTTP 400 and stores nothing`() =
        runTest {
            serverRule.enqueueJson("api/album_create_missing_field.json", HTTP_BAD_REQUEST)
            val dao = FakeAlbumDao()
            val repository = AlbumRepositoryImpl(dao, newAdapter(testScheduler))

            val result = repository.createAlbum(draft)

            assertEquals(DataResult.Failure(DataError.Http(HTTP_BAD_REQUEST)), result)
            assertTrue(dao.getAll().isEmpty())
        }

    @Test
    fun `create album with a value outside the backend lists becomes HTTP 400 and stores nothing`() =
        runTest {
            serverRule.enqueueJson("api/album_create_invalid_genre.json", HTTP_BAD_REQUEST)
            val dao = FakeAlbumDao()
            val repository = AlbumRepositoryImpl(dao, newAdapter(testScheduler))

            val result = repository.createAlbum(draft)

            assertEquals(DataResult.Failure(DataError.Http(HTTP_BAD_REQUEST)), result)
            assertTrue(dao.getAll().isEmpty())
        }

    @Test
    fun `create album with the connection cut becomes a network error and stores nothing`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val dao = FakeAlbumDao()
            val repository = AlbumRepositoryImpl(dao, newAdapter(testScheduler))

            val result = repository.createAlbum(draft)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertTrue(dao.getAll().isEmpty())
        }

    private fun newAdapter(
        scheduler: TestCoroutineScheduler,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler),
        onProvide: () -> Unit = {},
    ): AlbumServiceAdapterImpl {
        val dispatcher = StandardTestDispatcher(scheduler)
        return AlbumServiceAdapterImpl(
            apiProvider = {
                onProvide()
                val client =
                    NetworkClient(
                        baseUrl = serverRule.baseUrl.toString(),
                        cacheDirectory = temporaryFolder.newFolder(),
                    )
                clients.add(client)
                client.create(VinilosApiService::class.java)
            },
            ioDispatcher = ioDispatcher,
            defaultDispatcher = dispatcher,
        )
    }

    private companion object {
        const val HTTP_SERVER_ERROR = 500
        const val HTTP_BAD_REQUEST = 400

        val draft =
            NewAlbum(
                name = "The Game",
                coverUrl = "https://example.com/the-game.jpg",
                releaseDate = LocalDate.of(1980, 6, 30),
                description = "Noveno álbum de Queen.",
                genre = Genre.ROCK,
                recordLabel = RecordLabel.EMI,
            )
        const val IO_THREAD_NAME = "io-test"
        const val ALBUM_WITHOUT_NAME = "[{\"id\":1,\"releaseDate\":\"1984-08-01T00:00:00.000Z\"}]"
    }
}
