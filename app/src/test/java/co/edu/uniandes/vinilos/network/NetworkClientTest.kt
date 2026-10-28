package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

class NetworkClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val clients = mutableListOf<NetworkClient>()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun closeResources() {
        clients.forEach(NetworkClient::close)
        server.shutdown()
    }

    @Test
    fun `request preserves base path and decodes backend shaped JSON`() =
        runTest {
            server.enqueue(MockResponse().setBody(albumsJson))
            val api = newClient().create(ProbeApi::class.java)

            val result = networkRequest { api.albums() }

            assertTrue(result is DataResult.Success)
            val albums = (result as DataResult.Success).value
            assertEquals("Buscando América", albums.single().name)
            assertEquals(100, albums.single().id)
            assertEquals("/api/albums", server.takeRequest().path)
        }

    @Test
    fun `missing optional DTO fields remain nullable for the service adapter`() =
        runTest {
            server.enqueue(MockResponse().setBody("[{\"id\":100}]"))
            val api = newClient().create(ProbeApi::class.java)

            val albums = api.albums()

            assertNull(albums.single().name)
        }

    @Test
    fun `empty catalog is a successful empty list`() =
        runTest {
            server.enqueue(MockResponse().setBody("[]"))
            val api = newClient().create(ProbeApi::class.java)

            assertEquals(DataResult.Success(emptyList<ProbeAlbumDto>()), networkRequest { api.albums() })
        }

    @Test
    fun `HTTP errors preserve status codes`() =
        runTest {
            val api = newClient().create(ProbeApi::class.java)
            for (code in listOf(400, 404, 500)) {
                server.enqueue(MockResponse().setResponseCode(code))

                assertEquals(DataResult.Failure(DataError.Http(code)), networkRequest { api.albums() })
            }
        }

    @Test
    fun `invalid truncated and incompatible JSON produce serialization errors`() =
        runTest {
            val api = newClient().create(ProbeApi::class.java)
            for (body in listOf("not JSON", "[{", "{}", "[{\"id\":\"invalid\"}]")) {
                server.enqueue(MockResponse().setBody(body))

                assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { api.albums() })
            }
        }

    @Test
    fun `non standard JSON is rejected by the strict converter`() =
        runTest {
            server.enqueue(MockResponse().setBody("[{'id':100}]"))
            val api = newClient().create(ProbeApi::class.java)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { api.albums() })
        }

    @Test
    fun `read timeout produces a network error`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val api = newClient(readTimeoutMillis = SHORT_TIMEOUT_MILLIS).create(ProbeApi::class.java)

            assertEquals(DataResult.Failure(DataError.Network), networkRequest { api.albums() })
        }

    @Test
    fun `lost POST response does not repeat the write`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setBody("{\"id\":101}"))
            val api = newClient().create(ProbeApi::class.java)

            val result = networkRequest { api.createAlbum(mapOf("name" to "Nuevo álbum")) }

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals("POST", server.takeRequest().method)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `fresh response is reused from HTTP cache`() =
        runTest {
            server.enqueue(MockResponse().setBody(albumsJson).setHeader("Cache-Control", "max-age=60"))
            val api = newClient().create(ProbeApi::class.java)

            val first = api.albums()
            val second = api.albums()

            assertEquals(first, second)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `no store response is requested again`() =
        runTest {
            repeat(2) {
                server.enqueue(MockResponse().setBody(albumsJson).setHeader("Cache-Control", "no-store"))
            }
            val api = newClient().create(ProbeApi::class.java)

            api.albums()
            api.albums()

            assertEquals(2, server.requestCount)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `cancellation propagates during an HTTP request`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val api = newClient().create(ProbeApi::class.java)
            val request = async { networkRequest { api.albums() } }
            runCurrent()
            assertNotNull(server.takeRequest(REQUEST_WAIT_SECONDS, TimeUnit.SECONDS))

            request.cancelAndJoin()

            assertTrue(request.isCancelled)
        }

    private fun newClient(readTimeoutMillis: Long = DEFAULT_TEST_TIMEOUT_MILLIS): NetworkClient =
        NetworkClient(
            baseUrl = server.url("/api/").toString(),
            cacheDirectory = temporaryFolder.newFolder(),
            readTimeoutMillis = readTimeoutMillis,
        ).also(clients::add)

    // Contrato exclusivo de pruebas: HU01 añadirá VinilosApiService y sus DTO de producción.
    internal interface ProbeApi {
        @GET("albums")
        suspend fun albums(): List<ProbeAlbumDto>

        @POST("albums")
        suspend fun createAlbum(
            @Body body: Map<String, String>,
        ): ProbeAlbumDto
    }

    internal data class ProbeAlbumDto(
        val id: Int? = null,
        val name: String? = null,
    )

    private companion object {
        const val SHORT_TIMEOUT_MILLIS = 100L
        const val DEFAULT_TEST_TIMEOUT_MILLIS = 2_000L
        const val REQUEST_WAIT_SECONDS = 5L
        val albumsJson = checkNotNull(NetworkClientTest::class.java.getResource("/api/albums.json")).readText()
    }
}
