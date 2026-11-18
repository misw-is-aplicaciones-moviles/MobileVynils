package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import java.util.concurrent.TimeUnit

@Suppress("MagicNumber") // Fixture ids and HTTP response codes.
class AlbumAssociationServiceAdapterTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val serverRule = MockWebServerRule()

    private val clients = mutableListOf<NetworkClient>()

    @After
    fun closeClients() = clients.forEach(NetworkClient::close)

    @Test
    fun `association catalog preserves performer relationships and revalidates HTTP`() =
        runTest {
            serverRule.enqueueJson("api/albums.json")
            val albums = adapter(testScheduler).getAlbumsForAssociation()

            assertEquals(4, albums.size)
            assertEquals(
                100,
                albums
                    .first()
                    .performers
                    .single()
                    .id,
            )
            val request = serverRule.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/albums", request.path)
            assertEquals("no-cache", request.getHeader("Cache-Control"))
        }

    @Test
    fun `explicit empty performers is the only valid unassigned relationship list`() =
        runTest {
            val body = albumJson().apply { add("performers", JsonArray()) }
            serverRule.server.enqueue(MockResponse().setBody("[$body]"))

            val album = adapter(testScheduler).getAlbumsForAssociation().single()

            assertTrue(album.performers.isEmpty())
            assertNull(album.performerName)
        }

    @Test
    fun `missing null or invalid performers are serialization errors instead of empty relationships`() =
        runTest {
            val bodies =
                listOf(
                    null,
                    "null",
                    "[null]",
                    "[{}]",
                    "[{\"id\":0,\"name\":\"Artist\"}]",
                    "[{\"id\":7,\"name\":\" \"}]",
                    "{}",
                )
            val adapter = adapter(testScheduler)
            for (performers in bodies) {
                val album =
                    albumJson().apply {
                        if (performers == null) {
                            remove("performers")
                        } else {
                            add("performers", JsonParser.parseString(performers))
                        }
                    }
                serverRule.server.enqueue(MockResponse().setBody("[$album]"))

                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    networkRequest { adapter.getAlbumsForAssociation() },
                )
            }
        }

    @Test
    fun `unknown performer type remains occupied rather than being discarded`() =
        runTest {
            val relationships = JsonParser.parseString("[{\"id\":7,\"name\":\"Artist\"}]")
            val body = albumJson().apply { add("performers", relationships) }
            serverRule.server.enqueue(MockResponse().setBody("[$body]"))

            val performer =
                adapter(testScheduler)
                    .getAlbumsForAssociation()
                    .single()
                    .performers
                    .single()

            assertEquals(7, performer.id)
            assertNull(performer.type)
        }

    @Test
    fun `empty catalog is valid but invalid entries and missing bodies are not`() =
        runTest {
            val adapter = adapter(testScheduler)
            serverRule.server.enqueue(MockResponse().setBody("[]"))
            assertTrue(adapter.getAlbumsForAssociation().isEmpty())
            val invalidId = albumJson().apply { addProperty("id", 0) }
            val blankName = albumJson().apply { addProperty("name", " ") }
            for (body in listOf("null", "[null]", "[$invalidId]", "[$blankName]", "[{")) {
                serverRule.server.enqueue(MockResponse().setBody(body))
                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    networkRequest { adapter.getAlbumsForAssociation() },
                )
            }
            serverRule.server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(
                DataResult.Failure(DataError.Serialization),
                networkRequest { adapter.getAlbumsForAssociation() },
            )
        }

    @Test
    fun `association detail preserves relationships and revalidates the exact requested album`() =
        runTest {
            serverRule.enqueueJson("api/album_100.json")

            val album = adapter(testScheduler).getAlbumForAssociation(100)

            assertEquals(100, album.id)
            assertEquals(100, album.performers.single().id)
            val request = serverRule.server.takeRequest()
            assertEquals("/albums/100", request.path)
            assertEquals("no-cache", request.getHeader("Cache-Control"))
        }

    @Test
    fun `association detail rejects missing relationships and a different response id`() =
        runTest {
            val adapter = adapter(testScheduler)
            val invalid =
                listOf(
                    albumJson().apply { remove("performers") },
                    albumJson().apply { addProperty("id", 101) },
                )
            for (body in invalid) {
                serverRule.server.enqueue(MockResponse().setBody(body.toString()))
                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    networkRequest { adapter.getAlbumForAssociation(100) },
                )
            }
        }

    @Test
    fun `catalog and detail preserve HTTP errors`() =
        runTest {
            val adapter = adapter(testScheduler)
            serverRule.server.enqueue(MockResponse().setResponseCode(500))
            assertEquals(
                DataResult.Failure(DataError.Http(500)),
                networkRequest { adapter.getAlbumsForAssociation() },
            )
            serverRule.server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(
                DataResult.Failure(DataError.Http(404)),
                networkRequest { adapter.getAlbumForAssociation(100) },
            )
        }

    @Test
    fun `invalid detail id is rejected without a network request`() =
        runTest {
            val adapter = adapter(testScheduler)
            for (id in listOf(0, -1)) {
                val error = runCatching { adapter.getAlbumForAssociation(id) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertTrue(clients.isEmpty())
        }

    @Test
    fun `connection failure is not retried`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val adapter = adapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Network),
                networkRequest { adapter.getAlbumsForAssociation() },
            )
            assertEquals("/albums", serverRule.server.takeRequest().path)
            assertNull(serverRule.server.takeRequest(100, TimeUnit.MILLISECONDS))
        }

    @Test
    fun `ordinary catalog remains tolerant while forced refresh revalidates HTTP`() =
        runTest {
            val body = albumJson().apply { remove("performers") }
            serverRule.server.enqueue(MockResponse().setBody("[$body]"))
            val adapter = adapter(testScheduler)

            assertTrue(
                adapter
                    .getAlbums()
                    .single()
                    .performers
                    .isEmpty(),
            )
            assertNull(serverRule.server.takeRequest().getHeader("Cache-Control"))
            serverRule.server.enqueue(MockResponse().setBody("[$body]"))
            adapter.getAlbums(forceRefresh = true)
            assertEquals("no-cache", serverRule.server.takeRequest().getHeader("Cache-Control"))
        }

    private fun albumJson(): JsonObject = JsonParser.parseString(readResource("api/album_100.json")).asJsonObject

    private fun adapter(scheduler: TestCoroutineScheduler): AlbumServiceAdapterImpl {
        val dispatcher = StandardTestDispatcher(scheduler)
        return AlbumServiceAdapterImpl(
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
