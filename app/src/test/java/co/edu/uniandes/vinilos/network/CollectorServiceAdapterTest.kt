package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executors

/** Contract of `GET /collectors`, with the real response of the seeded backend. */
@Suppress("MagicNumber") // Seed ids and HTTP codes.
class CollectorServiceAdapterTest {
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
    fun `GET collectors deserializes the seed and counts its nested lists`() =
        runTest {
            serverRule.enqueueJson("api/collectors.json")

            val collectors = newAdapter(testScheduler).getCollectors()

            val request = serverRule.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/collectors", request.path)
            assertEquals(listOf(100, 101), collectors.map { it.id })
            val manolo = collectors.first()
            assertEquals("Manolo Bellon", manolo.name)
            assertEquals("manollo@caracol.com.co", manolo.email)
            assertEquals(1, manolo.albumCount)
            assertEquals(1, manolo.favoriteCount)
        }

    @Test
    fun `a failing call is the HTTP error of the response`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(500))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Http(500)), networkRequest { adapter.getCollectors() })
        }

    @Test
    fun `constructing the adapter does not invoke the provider`() =
        runTest {
            var providerCalls = 0

            newAdapter(testScheduler) { providerCalls++ }

            assertEquals(0, providerCalls)
        }

    @Test
    fun `api provider runs on the IO dispatcher`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody("[]"))
            val executor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD_NAME) }
            try {
                var providerThread: String? = null
                val adapter =
                    newAdapter(
                        scheduler = testScheduler,
                        ioDispatcher = executor.asCoroutineDispatcher(),
                    ) { providerThread = Thread.currentThread().name }

                adapter.getCollectors()

                // In debug mode the coroutine name is appended to the thread name.
                assertTrue("Provider ran on $providerThread", providerThread.orEmpty().startsWith(IO_THREAD_NAME))
            } finally {
                executor.shutdown()
            }
        }

    private fun newAdapter(
        scheduler: TestCoroutineScheduler,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler),
        onProvide: () -> Unit = {},
    ): CollectorServiceAdapterImpl =
        CollectorServiceAdapterImpl(
            apiProvider = {
                onProvide()
                newApi()
            },
            ioDispatcher = ioDispatcher,
            defaultDispatcher = StandardTestDispatcher(scheduler),
        )

    private fun newApi(): VinilosApiService {
        val client =
            NetworkClient(
                baseUrl = serverRule.baseUrl.toString(),
                cacheDirectory = temporaryFolder.newFolder(),
            )
        clients.add(client)
        return client.create(VinilosApiService::class.java)
    }

    private companion object {
        const val IO_THREAD_NAME = "io-test"
    }
}
