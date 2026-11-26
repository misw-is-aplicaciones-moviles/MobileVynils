package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.JsonParser
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Suppress("MagicNumber") // Fixture ids, HTTP response codes and bounded queue timeout.
class PerformerAlbumServiceAdapterTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val serverRule = MockWebServerRule()

    private val clients = mutableListOf<NetworkClient>()

    @After
    fun closeClients() = clients.forEach(NetworkClient::close)

    @Test
    fun `musician and band use distinct routes with the same ids and no request body`() =
        runTest {
            val adapter = adapter(testScheduler)
            for ((type, collection) in listOf(PerformerType.MUSICIAN to "musicians", PerformerType.BAND to "bands")) {
                serverRule.enqueueJson("api/album_100.json")

                adapter.addAlbum(73, type, 100)

                val request = serverRule.server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/$collection/73/albums/100", request.path)
                assertEquals(0L, request.bodySize)
                assertNull(request.getHeader("Content-Type"))
            }
        }

    @Test
    fun `invalid ids do not create the API or send requests`() =
        runTest {
            val adapter = adapter(testScheduler)
            for ((artistId, albumId) in listOf(0 to 100, -1 to 100, 73 to 0, 73 to -1)) {
                val error = runCatching { adapter.addAlbum(artistId, PerformerType.BAND, albumId) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertTrue(clients.isEmpty())
        }

    @Test
    fun `missing or mismatched response identities become serialization errors`() =
        runTest {
            val adapter = adapter(testScheduler)
            for (body in listOf("{}", "{\"id\":101}", "{\"id\":0}", "null", "{bad}")) {
                serverRule.server.enqueue(MockResponse().setBody(body))
                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    networkRequest { adapter.addAlbum(73, PerformerType.BAND, 100) },
                )
            }
            serverRule.server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(
                DataResult.Failure(DataError.Serialization),
                networkRequest { adapter.addAlbum(73, PerformerType.BAND, 100) },
            )
        }

    @Test
    fun `association HTTP errors are preserved for both types`() =
        runTest {
            val adapter = adapter(testScheduler)
            for (type in PerformerType.entries) {
                for (code in listOf(404, 500)) {
                    serverRule.server.enqueue(MockResponse().setResponseCode(code))
                    assertEquals(
                        DataResult.Failure(DataError.Http(code)),
                        networkRequest { adapter.addAlbum(73, type, 100) },
                    )
                }
            }
        }

    @Test
    fun `lost response sends only one POST and does not make an implicit read or retry`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val adapter = adapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Network),
                networkRequest { adapter.addAlbum(73, PerformerType.MUSICIAN, 100) },
            )
            assertEquals("POST", serverRule.server.takeRequest().method)
            assertNull(serverRule.server.takeRequest(100, TimeUnit.MILLISECONDS))
        }

    @Test
    fun `detail after association can revalidate without altering members or prizes`() =
        runTest {
            val updatedBand =
                JsonParser
                    .parseString(
                        readResource("api/bands_with_members.json"),
                    ).asJsonArray[0]
                    .asJsonObject
            updatedBand.getAsJsonArray("albums").add(JsonParser.parseString(readResource("api/album_100.json")))
            serverRule.enqueueJson("api/album_100.json")
            serverRule.server.enqueue(MockResponse().setBody(updatedBand.toString()))
            val adapter = adapter(testScheduler)

            adapter.addAlbum(101, PerformerType.BAND, 100)
            val detail = adapter.getPerformer(101, PerformerType.BAND, forceRefresh = true)

            assertEquals(3, detail.albums.size)
            assertTrue(detail.albums.any { it.id == 100 })
            assertEquals(4, detail.members.size)
            assertEquals(1, detail.prizeAssignments.size)
            assertEquals("POST", serverRule.server.takeRequest().method)
            val refresh = serverRule.server.takeRequest()
            assertEquals("/bands/101", refresh.path)
            assertEquals("no-cache", refresh.getHeader("Cache-Control"))
        }

    @Test
    fun `forced artist catalog revalidates both musician and band queries`() =
        runTest {
            serverRule.server.dispatcher =
                object : okhttp3.mockwebserver.Dispatcher() {
                    override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                        MockResponse().setBody("[]")
                }
            assertTrue(adapter(testScheduler).getPerformers(forceRefresh = true).isEmpty())
            val requests = List(2) { serverRule.server.takeRequest() }
            assertEquals(setOf("/musicians", "/bands"), requests.map { it.path }.toSet())
            assertTrue(requests.all { it.getHeader("Cache-Control") == "no-cache" })
        }

    @Test
    fun `association API initialization runs on the injected IO dispatcher`() =
        runTest {
            serverRule.enqueueJson("api/album_100.json")
            val executor = Executors.newSingleThreadExecutor { Thread(it, "association-io") }
            executor.asCoroutineDispatcher().use { io ->
                var providerThread: String? = null
                val dispatcher = StandardTestDispatcher(testScheduler)
                val adapter =
                    PerformerServiceAdapterImpl(
                        apiProvider = {
                            providerThread = Thread.currentThread().name
                            NetworkClient(serverRule.baseUrl.toString(), folder.newFolder())
                                .also(clients::add)
                                .create(VinilosApiService::class.java)
                        },
                        ioDispatcher = io,
                        defaultDispatcher = dispatcher,
                    )
                adapter.addAlbum(73, PerformerType.MUSICIAN, 100)
                assertEquals("association-io", providerThread)
            }
        }

    private fun adapter(scheduler: TestCoroutineScheduler): PerformerServiceAdapterImpl {
        val dispatcher = StandardTestDispatcher(scheduler)
        return PerformerServiceAdapterImpl(
            apiProvider = {
                NetworkClient(serverRule.baseUrl.toString(), folder.newFolder())
                    .also(clients::add)
                    .create(VinilosApiService::class.java)
            },
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
    }
}
