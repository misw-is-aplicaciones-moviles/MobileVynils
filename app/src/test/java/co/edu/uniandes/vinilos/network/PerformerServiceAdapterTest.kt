package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.repositories.PerformerRepositoryImpl
import co.edu.uniandes.vinilos.testing.FakePerformerDao
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
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/** Contract of `GET /musicians` and `GET /bands` (HU03-06), with real responses of the seeded backend. */
@Suppress("MagicNumber") // Seed ids, years and counts.
class PerformerServiceAdapterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val serverRule = MockWebServerRule()

    private val clients = mutableListOf<NetworkClient>()
    private val requests = ConcurrentLinkedQueue<String>()

    @After
    fun closeClients() {
        clients.forEach(NetworkClient::close)
    }

    @Test
    fun `GET musicians deserializes the seed musician`() =
        runTest {
            serve("/musicians" to json("api/musicians.json"))

            val musicians = newApi().getMusicians()

            val ruben = musicians.single()
            assertEquals(100, ruben.id)
            assertEquals("Rubén Blades Bellido de Luna", ruben.name)
            assertEquals("1948-07-16T00:00:00.000Z", ruben.birthDate)
            assertTrue(ruben.image.orEmpty().startsWith("https://"))
            assertEquals(2, ruben.albums?.size)
            assertEquals(listOf("/musicians"), requests.toList())
        }

    @Test
    fun `GET bands deserializes the seed band with its members`() =
        runTest {
            serve("/bands" to json("api/bands_with_members.json"))

            val queen = newApi().getBands().single()

            assertEquals(101, queen.id)
            assertEquals("Queen", queen.name)
            assertEquals("1970-01-01T00:00:00.000Z", queen.creationDate)
            assertEquals(2, queen.albums?.size)
            val memberIds = queen.musicians.orEmpty().map { it.id }
            assertEquals(setOf(102, 103, 104, 105), memberIds.toSet())
            assertEquals(listOf("/bands"), requests.toList())
        }

    @Test
    fun `both endpoints are called with GET and the seed becomes a musician and a band`() =
        runTest {
            serve("/musicians" to json("api/musicians.json"), "/bands" to json("api/bands.json"))

            val performers = newAdapter(testScheduler).getPerformers()

            assertEquals(listOf("/bands", "/musicians"), requests.sorted())
            assertTrue(serverRule.server.requestCount == 2)
            assertEquals(setOf(PerformerType.MUSICIAN, PerformerType.BAND), performers.map { it.type }.toSet())
            val queen = performers.single { it.type == PerformerType.BAND }
            assertEquals("Queen", queen.name)
            assertEquals(LocalDate.of(1970, 1, 1), queen.date)
            assertEquals(2, queen.albumCount)
            assertEquals(0, queen.memberCount)
            assertNull(performers.single { it.type == PerformerType.MUSICIAN }.bandId)
        }

    @Test
    fun `members of a band are joined from the band list, not from the musician list`() =
        runTest {
            serve(
                "/musicians" to json("api/musicians_with_band.json"),
                "/bands" to json("api/bands_with_members.json"),
            )

            val performers = newAdapter(testScheduler).getPerformers()

            val members = performers.filter { it.bandId == 101 }
            assertEquals(4, members.size)
            assertTrue(members.all { it.type == PerformerType.MUSICIAN && it.bandName == "Queen" })
            assertEquals(4, performers.single { it.type == PerformerType.BAND }.memberCount)
            assertEquals(6, performers.size)
        }

    @Test
    fun `two empty lists are an empty result`() =
        runTest {
            serve("/musicians" to MockResponse().setBody("[]"), "/bands" to MockResponse().setBody("[]"))

            assertTrue(newAdapter(testScheduler).getPerformers().isEmpty())
        }

    @Test
    fun `a failing bands call fails the whole load with its HTTP code`() =
        runTest {
            serve("/musicians" to json("api/musicians.json"), "/bands" to MockResponse().setResponseCode(500))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Http(500)), networkRequest { adapter.getPerformers() })
        }

    @Test
    fun `a failing musicians call fails the whole load with its HTTP code`() =
        runTest {
            serve("/musicians" to MockResponse().setResponseCode(503), "/bands" to json("api/bands.json"))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Http(503)), networkRequest { adapter.getPerformers() })
        }

    @Test
    fun `a connection cut on one call is a network error for the whole load`() =
        runTest {
            serve(
                "/musicians" to json("api/musicians.json"),
                "/bands" to MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
            )
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Network), networkRequest { adapter.getPerformers() })
        }

    @Test
    fun `malformed JSON in one call is a serialization error`() =
        runTest {
            serve("/musicians" to MockResponse().setBody("[{"), "/bands" to json("api/bands.json"))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getPerformers() })
        }

    @Test
    fun `a musician without a name is a serialization error`() =
        runTest {
            serve(
                "/musicians" to MockResponse().setBody("""[{"id":1,"birthDate":"1948-07-16T00:00:00.000Z"}]"""),
                "/bands" to json("api/bands.json"),
            )
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getPerformers() })
        }

    @Test
    fun `a partial failure through the repository never touches Room`() =
        runTest {
            serve("/musicians" to json("api/musicians.json"), "/bands" to MockResponse().setResponseCode(500))
            val dao = FakePerformerDao()
            val repository = PerformerRepositoryImpl(dao, newAdapter(testScheduler))

            val result = repository.getPerformers()

            assertEquals(DataResult.Failure(DataError.Http(500)), result)
            assertEquals(0, dao.count())
            assertEquals(0, dao.writeCount)
        }

    @Test
    fun `api provider is invoked once even though each load makes two calls`() =
        runTest {
            serve("/musicians" to json("api/musicians.json"), "/bands" to json("api/bands.json"))
            var providerCalls = 0
            val adapter = newAdapter(testScheduler) { providerCalls++ }

            adapter.getPerformers()
            adapter.getPerformers()

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
            serve("/musicians" to json("api/musicians.json"), "/bands" to json("api/bands.json"))
            val executor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD_NAME) }
            try {
                var providerThread: String? = null
                val adapter =
                    newAdapter(
                        scheduler = testScheduler,
                        ioDispatcher = executor.asCoroutineDispatcher(),
                    ) { providerThread = Thread.currentThread().name }

                adapter.getPerformers()

                // In debug mode the coroutine name is appended to the thread name of an async child.
                assertTrue("Provider ran on $providerThread", providerThread.orEmpty().startsWith(IO_THREAD_NAME))
            } finally {
                executor.shutdown()
            }
        }

    // Both calls arrive at the same time, so answers are chosen by path and not by their order in a queue.
    private fun serve(vararg routes: Pair<String, MockResponse>) {
        val byPath = routes.toMap()
        serverRule.server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    assertEquals("GET", request.method)
                    requests.add(request.path.orEmpty())
                    return byPath[request.path] ?: MockResponse().setResponseCode(404)
                }
            }
    }

    private fun json(fixture: String) =
        MockResponse().setHeader("Content-Type", "application/json").setBody(readResource(fixture))

    private fun newApi(): VinilosApiService {
        val client =
            NetworkClient(
                baseUrl = serverRule.baseUrl.toString(),
                cacheDirectory = temporaryFolder.newFolder(),
            )
        clients.add(client)
        return client.create(VinilosApiService::class.java)
    }

    private fun newAdapter(
        scheduler: TestCoroutineScheduler,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler),
        onProvide: () -> Unit = {},
    ): PerformerServiceAdapterImpl =
        PerformerServiceAdapterImpl(
            apiProvider = {
                onProvide()
                newApi()
            },
            ioDispatcher = ioDispatcher,
            defaultDispatcher = StandardTestDispatcher(scheduler),
        )

    private companion object {
        const val IO_THREAD_NAME = "io-test"
    }
}
