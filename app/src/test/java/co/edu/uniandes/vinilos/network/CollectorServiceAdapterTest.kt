package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.readResource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/** Contract of `GET /collectors`, with the real response of the seeded backend. */
@Suppress("MagicNumber") // Seed ids and HTTP codes.
class CollectorServiceAdapterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val serverRule = MockWebServerRule()

    private val clients = mutableListOf<NetworkClient>()
    private val requests = ConcurrentLinkedQueue<RecordedRequest>()

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

    @Test
    fun `GET collector joins the seed detail with its albums`() =
        runTest {
            serve(collectorRoute(), albumsRoute())

            val detail = newAdapter(testScheduler).getCollector(100)

            assertEquals(listOf("/collectors/100", "/collectors/100/albums"), paths())
            assertTrue(requests.all { it.method == "GET" && it.getHeader("Cache-Control") == null })
            assertEquals("Manolo Bellon", detail.collector.name)
            assertEquals("manollo@caracol.com.co", detail.collector.email)
            assertEquals("3502457896", detail.telephone)
            assertEquals(1, detail.collector.favoriteCount)
            assertEquals(1, detail.collector.albumCount)
            val favorite = detail.favorites.single()
            assertEquals(100, favorite.id)
            assertEquals("Rubén Blades Bellido de Luna", favorite.name)
            assertEquals(PerformerType.MUSICIAN, favorite.type)
            val album = detail.albums.single()
            assertEquals(100, album.albumId)
            assertEquals("Buscando América", album.name)
            assertEquals(35, album.price)
            assertEquals(AlbumStatus.ACTIVE, album.status)
        }

    @Test
    fun `force refresh sends no-cache on both calls`() =
        runTest {
            serve(collectorRoute(), albumsRoute())

            newAdapter(testScheduler).getCollector(100, forceRefresh = true)

            assertEquals(listOf("/collectors/100", "/collectors/100/albums"), paths())
            assertTrue(requests.all { it.getHeader("Cache-Control") == "no-cache" })
        }

    @Test
    fun `one failing call fails the whole detail`() =
        runTest {
            val adapter = newAdapter(testScheduler)
            listOf(collectorRoute().first, albumsRoute().first).forEach { failing ->
                requests.clear()
                serve(
                    collectorRoute().first to
                        if (failing == collectorRoute().first) {
                            MockResponse().setResponseCode(500)
                        } else {
                            collectorRoute().second
                        },
                    albumsRoute().first to
                        if (failing == albumsRoute().first) {
                            MockResponse().setResponseCode(500)
                        } else {
                            albumsRoute().second
                        },
                )

                assertEquals(DataResult.Failure(DataError.Http(500)), networkRequest { adapter.getCollector(100) })
            }
        }

    @Test
    fun `invalid album JSON is a serialization error for the whole detail`() =
        runTest {
            serve(
                collectorRoute().first to collectorRoute().second,
                albumsRoute().first to MockResponse().setBody("[{"),
            )

            val result = networkRequest { newAdapter(testScheduler).getCollector(100) }

            assertEquals(DataResult.Failure(DataError.Serialization), result)
        }

    @Test
    fun `a missing collector keeps its HTTP status`() =
        runTest {
            serve(
                "/collectors/404" to MockResponse().setResponseCode(404),
                "/collectors/404/albums" to MockResponse().setBody("[]"),
            )

            val result = networkRequest { newAdapter(testScheduler).getCollector(404) }

            assertEquals(DataResult.Failure(DataError.Http(404)), result)
            assertTrue(paths().contains("/collectors/404"))
        }

    @Test
    fun `an empty collection is a valid detail`() =
        runTest {
            serve(
                "/collectors/7" to
                    MockResponse().setBody(
                        """{"id":7,"name":"Ana","telephone":"1","email":"a@b.co","favoritePerformers":[]}""",
                    ),
                "/collectors/7/albums" to MockResponse().setBody("[]"),
            )

            val detail = newAdapter(testScheduler).getCollector(7)

            assertTrue(detail.favorites.isEmpty())
            assertTrue(detail.albums.isEmpty())
            assertEquals(0, detail.collector.albumCount)
            assertEquals(0, detail.collector.favoriteCount)
        }

    @Test
    fun `a non positive collector id fails before any request`() =
        runTest {
            listOf(0, -1).forEach { id ->
                val error = runCatching { newAdapter(testScheduler).getCollector(id) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertEquals(0, serverRule.server.requestCount)
        }

    // Both calls arrive together, so the answer is chosen by path.
    private fun serve(vararg routes: Pair<String, MockResponse>) {
        val byPath = routes.toMap()
        serverRule.server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests.add(request)
                    return byPath[request.path] ?: MockResponse().setResponseCode(404)
                }
            }
    }

    private fun collectorRoute() = "/collectors/100" to json("api/collector_100.json")

    private fun albumsRoute() = "/collectors/100/albums" to json("api/collector_100_albums.json")

    private fun json(fixture: String) =
        MockResponse().setHeader("Content-Type", "application/json").setBody(readResource(fixture))

    private fun paths() = requests.map { checkNotNull(it.path) }.sorted()

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
