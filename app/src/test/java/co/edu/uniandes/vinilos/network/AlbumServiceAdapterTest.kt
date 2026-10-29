package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
    fun `detail query returns the requested album with its nested data`() =
        runTest {
            enqueueDetail()

            val album = newAdapter(testScheduler).getAlbum(100)

            assertEquals(100, album.id)
            assertEquals(listOf("Decisiones", "Desapariciones"), album.tracks.map { it.name })
            assertEquals("Rubén Blades Bellido de Luna", album.performers.single().name)
            assertTrue(checkNotNull(album.performers.single().imageUrl).startsWith("https://"))
            assertEquals(5, album.comments.single().rating)
            val request = serverRule.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/albums/100", request.path)
            assertNull(request.getHeader("Cache-Control"))
        }

    @Test
    fun `forced detail query revalidates the existing HTTP cache`() =
        runTest {
            enqueueDetail()

            newAdapter(testScheduler).getAlbum(100, forceRefresh = true)

            assertEquals("no-cache", serverRule.server.takeRequest().getHeader("Cache-Control"))
        }

    @Test
    fun `detail query preserves 404 and classifies null and empty bodies`() =
        runTest {
            val adapter = newAdapter(testScheduler)
            serverRule.server.enqueue(MockResponse().setResponseCode(HTTP_NOT_FOUND))
            assertEquals(
                DataResult.Failure(DataError.Http(HTTP_NOT_FOUND)),
                networkRequest { adapter.getAlbum(100) },
            )
            serverRule.server.enqueue(MockResponse().setBody("null"))
            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbum(100) })
            serverRule.server.enqueue(MockResponse().setResponseCode(HTTP_NO_CONTENT))
            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbum(100) })
        }

    private fun enqueueDetail() {
        val album = JsonParser.parseString(readResource("api/albums.json")).asJsonArray.first()
        serverRule.server.enqueue(MockResponse().setBody(album.toString()))
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
        const val HTTP_NOT_FOUND = 404
        const val HTTP_NO_CONTENT = 204
        const val HTTP_SERVER_ERROR = 500
        const val IO_THREAD_NAME = "io-test"
        const val ALBUM_WITHOUT_NAME = "[{\"id\":1,\"releaseDate\":\"1984-08-01T00:00:00.000Z\"}]"
    }
}
