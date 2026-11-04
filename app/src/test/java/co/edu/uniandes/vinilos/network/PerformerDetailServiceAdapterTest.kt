package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.repositories.PerformerRepositoryImpl
import co.edu.uniandes.vinilos.testing.FakePerformerDao
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

/** HU04 contracts through the real Repository, Retrofit client and existing HU03 response fixtures. */
@Suppress("MagicNumber") // Fixture identities, dates and HTTP status codes.
class PerformerDetailServiceAdapterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @get:Rule val serverRule = MockWebServerRule()
    private val clients = mutableListOf<NetworkClient>()

    @After
    fun closeClients() = clients.forEach(NetworkClient::close)

    @Test
    fun `musician detail includes biography date and full albums with one GET`() =
        runTest {
            serverRule.server.enqueue(json(musician()))
            val repository = repository(testScheduler)

            val detail = success(repository.getPerformer(100, PerformerType.MUSICIAN))

            assertEquals(PerformerType.MUSICIAN, detail.performer.type)
            assertEquals("Rubén Blades Bellido de Luna", detail.performer.name)
            assertEquals(LocalDate.of(1948, 7, 16), detail.performer.date)
            assertTrue(detail.performer.imageUrl.startsWith("https://"))
            assertEquals(musician().get("description").asString, detail.description)
            assertEquals(setOf("Buscando América", "Poeta del pueblo"), detail.albums.map { it.name }.toSet())
            assertEquals(2, detail.performer.albumCount)
            assertTrue(detail.albums.all { it.coverUrl.isNotBlank() && it.releaseDate.year == 1984 })
            assertTrue(detail.members.isEmpty())
            assertRequest("/musicians/100")
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `band detail includes typed members and albums without extra requests`() =
        runTest {
            serverRule.server.enqueue(json(band()))

            val detail = success(repository(testScheduler).getPerformer(101, PerformerType.BAND))

            assertEquals("Queen", detail.performer.name)
            assertEquals(PerformerType.BAND, detail.performer.type)
            assertEquals(LocalDate.of(1970, 1, 1), detail.performer.date)
            assertEquals(band().get("description").asString, detail.description)
            assertEquals(2, detail.performer.albumCount)
            assertEquals(4, detail.performer.memberCount)
            assertEquals(setOf(102, 103, 104, 105), detail.members.map { it.id }.toSet())
            assertTrue(detail.members.all { it.name.isNotBlank() && it.type == PerformerType.MUSICIAN })
            assertEquals(setOf("A Night at the Opera", "A Day at the Races"), detail.albums.map { it.name }.toSet())
            assertRequest("/bands/101")
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `retry revalidates even a fresh HTTP cache for both types`() =
        runTest {
            val repository = repository(testScheduler)
            listOf(PerformerType.MUSICIAN, PerformerType.BAND).forEach { type ->
                val body = if (type == PerformerType.MUSICIAN) musician() else band()
                val id = body.get("id").asInt
                val path = if (type == PerformerType.MUSICIAN) "/musicians/$id" else "/bands/$id"
                serverRule.server.enqueue(json(body).setHeader("Cache-Control", "public, max-age=3600"))
                success(repository.getPerformer(id, type))
                assertRequest(path)
                body.addProperty("description", "Biografía actualizada")
                serverRule.server.enqueue(json(body))

                val refreshed = success(repository.getPerformer(id, type, forceRefresh = true))

                assertEquals("Biografía actualizada", refreshed.description)
                assertEquals("no-cache", serverRule.server.takeRequest().getHeader("Cache-Control"))
            }
            assertEquals(4, serverRule.server.requestCount)
        }

    @Test
    fun `absent relationships become empty sections and a missing image is allowed`() =
        runTest {
            val body =
                band().apply {
                    remove("albums")
                    remove("musicians")
                    remove("image")
                }
            serverRule.server.enqueue(json(body))

            val detail = success(repository(testScheduler).getPerformer(101, PerformerType.BAND))

            assertTrue(detail.albums.isEmpty())
            assertTrue(detail.members.isEmpty())
            assertEquals(0, detail.performer.albumCount)
            assertEquals(0, detail.performer.memberCount)
            assertEquals("", detail.performer.imageUrl)
        }

    @Test
    fun `404 and 500 retain their HTTP status for both endpoints`() =
        runTest {
            val repository = repository(testScheduler)
            listOf(PerformerType.MUSICIAN, PerformerType.BAND).forEach { type ->
                listOf(404, 500).forEach { code ->
                    serverRule.server.enqueue(MockResponse().setResponseCode(code))

                    assertEquals(
                        DataResult.Failure(DataError.Http(code)),
                        repository.getPerformer(700, type),
                    )
                }
            }
        }

    @Test
    fun `disconnection is a network error and the next explicit request recovers`() =
        runTest {
            val repository = repository(testScheduler)
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            assertEquals(
                DataResult.Failure(DataError.Network),
                repository.getPerformer(100, PerformerType.MUSICIAN),
            )
            serverRule.server.enqueue(json(musician()))
            assertEquals(100, success(repository.getPerformer(100, PerformerType.MUSICIAN, true)).performer.id)
            assertEquals(2, serverRule.server.requestCount)
        }

    @Test
    fun `read timeout is a network error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

            val result = repository(testScheduler, readTimeoutMillis = 100).getPerformer(101, PerformerType.BAND)

            assertEquals(DataResult.Failure(DataError.Network), result)
        }

    @Test
    fun `malformed null empty and 204 bodies are serialization failures`() =
        runTest {
            val repository = repository(testScheduler)
            listOf("{", "null", "").forEach { body ->
                serverRule.server.enqueue(MockResponse().setBody(body))
                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    repository.getPerformer(100, PerformerType.MUSICIAN),
                )
            }
            serverRule.server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(
                DataResult.Failure(DataError.Serialization),
                repository.getPerformer(101, PerformerType.BAND),
            )
        }

    @Test
    fun `null albums and null members become serialization failures instead of crashes`() =
        runTest {
            val repository = repository(testScheduler)
            listOf("albums", "musicians").forEach { relationship ->
                val body = band().apply { add(relationship, JsonArray().apply { add(JsonNull.INSTANCE) }) }
                serverRule.server.enqueue(json(body))

                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    repository.getPerformer(101, PerformerType.BAND),
                )
            }
        }

    @Test
    fun `missing identity dates and related fields reject incomplete details`() =
        runTest {
            val repository = repository(testScheduler)
            val invalid =
                listOf(
                    band().apply { remove("id") },
                    band().apply { addProperty("id", 0) },
                    band().apply { remove("name") },
                    band().apply { addProperty("creationDate", "yesterday") },
                    band().apply { getAsJsonArray("albums")[0].asJsonObject.remove("name") },
                    band().apply { getAsJsonArray("albums")[0].asJsonObject.remove("releaseDate") },
                    band().apply { getAsJsonArray("musicians")[0].asJsonObject.remove("id") },
                    band().apply { getAsJsonArray("musicians")[0].asJsonObject.remove("name") },
                )
            invalid.forEach { body ->
                serverRule.server.enqueue(json(body))
                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    repository.getPerformer(101, PerformerType.BAND),
                )
            }
            serverRule.server.enqueue(json(musician().apply { remove("birthDate") }))
            assertEquals(
                DataResult.Failure(DataError.Serialization),
                repository.getPerformer(100, PerformerType.MUSICIAN),
            )
        }

    @Test
    fun `a detail for another artist or endpoint is a serialization failure`() =
        runTest {
            val repository = repository(testScheduler)
            serverRule.server.enqueue(json(musician().apply { addProperty("id", 999) }))
            assertEquals(
                DataResult.Failure(DataError.Serialization),
                repository.getPerformer(100, PerformerType.MUSICIAN),
            )
            serverRule.server.enqueue(json(band()))
            assertEquals(
                DataResult.Failure(DataError.Serialization),
                repository.getPerformer(101, PerformerType.MUSICIAN),
            )
        }

    private fun musician(): JsonObject = firstObject("api/musicians.json")

    private fun band(): JsonObject = firstObject("api/bands_with_members.json")

    private fun firstObject(path: String): JsonObject =
        JsonParser.parseString(readResource(path)).asJsonArray[0].asJsonObject

    private fun json(body: JsonObject): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())

    private fun success(result: DataResult<PerformerDetail, DataError>): PerformerDetail =
        (result as DataResult.Success).value

    private fun assertRequest(path: String) {
        val request = serverRule.server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(path, request.path)
    }

    private fun repository(
        scheduler: TestCoroutineScheduler,
        readTimeoutMillis: Long = 30_000,
    ): PerformerRepositoryImpl {
        val client =
            NetworkClient(
                serverRule.baseUrl.toString(),
                temporaryFolder.newFolder(),
                readTimeoutMillis = readTimeoutMillis,
            )
        clients += client
        val dispatcher = StandardTestDispatcher(scheduler)
        return PerformerRepositoryImpl(
            FakePerformerDao(),
            PerformerServiceAdapterImpl({ client.create(VinilosApiService::class.java) }, dispatcher, dispatcher),
        )
    }
}
