package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.database.toEntity
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.repositories.PerformerRepositoryImpl
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.FakePerformerDao
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.JsonParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Contract of `POST /bands/:bandId/musicians/:musicianId` through the real Repository and Retrofit client.
 * The 404 bodies were captured from BackVynils without writing (it answers before saving); the 200 body is synthetic
 * with the backend shape, so capturing it did not move a musician in the local database.
 */
@Suppress("MagicNumber") // Fixture ids and HTTP status codes.
class BandMemberServiceAdapterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @get:Rule val serverRule = MockWebServerRule()
    private val clients = mutableListOf<NetworkClient>()
    private val dao =
        FakePerformerDao(
            listOf(
                band(101, "Queen", members = 4).toEntity(),
                musician(100, "Rubén Blades Bellido de Luna").toEntity(),
            ),
        )

    @After
    fun closeClients() = clients.forEach(NetworkClient::close)

    @Test
    fun `add musician to band posts to the band route and maps the musician`() =
        runTest {
            serverRule.enqueueJson("api/band_musician_added.json")

            val result = repository(testScheduler).addMusicianToBand(101, 100)

            val member = (result as DataResult.Success).value
            assertEquals(
                PerformerSummary(
                    id = 100,
                    name = "Rubén Blades Bellido de Luna",
                    imageUrl = added().get("image").asString,
                    type = PerformerType.MUSICIAN,
                ),
                member,
            )
            val request = serverRule.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/bands/101/musicians/100", request.path)
            assertEquals(0L, request.bodySize)
            assertEquals(1, serverRule.server.requestCount)
            assertEquals(101, dao.row(100)?.bandId)
            assertEquals(5, dao.row(101)?.memberCount)
        }

    @Test
    fun `unknown band becomes HTTP 404`() =
        runTest {
            serverRule.enqueueJson("api/band_musician_band_not_found.json", code = 404)

            val result = repository(testScheduler).addMusicianToBand(2147483647, 100)

            assertEquals(DataResult.Failure(DataError.Http(404)), result)
            assertEquals("/bands/2147483647/musicians/100", serverRule.server.takeRequest().path)
            assertRoomUntouched()
        }

    @Test
    fun `unknown musician becomes HTTP 404`() =
        runTest {
            serverRule.enqueueJson("api/band_musician_musician_not_found.json", code = 404)

            val result = repository(testScheduler).addMusicianToBand(101, 2147483647)

            assertEquals(DataResult.Failure(DataError.Http(404)), result)
            assertEquals("/bands/101/musicians/2147483647", serverRule.server.takeRequest().path)
            assertRoomUntouched()
        }

    @Test
    fun `server error becomes HTTP 500`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(500))

            val result = repository(testScheduler).addMusicianToBand(101, 100)

            assertEquals(DataResult.Failure(DataError.Http(500)), result)
            assertRoomUntouched()
        }

    @Test
    fun `connection cut becomes a network error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

            val result = repository(testScheduler).addMusicianToBand(101, 100)

            assertEquals(DataResult.Failure(DataError.Network), result)
            // The client does not retry a POST on a broken connection, so the backend saw a single request.
            assertEquals(1, serverRule.server.requestCount)
            assertRoomUntouched()
        }

    @Test
    fun `malformed or incomplete body becomes a serialization error`() =
        runTest {
            val withoutId = added().apply { remove("id") }
            val otherMusician = added().apply { addProperty("id", 999) }
            listOf("{", "", withoutId.toString(), otherMusician.toString()).forEach { body ->
                serverRule.server.enqueue(
                    MockResponse().setHeader("Content-Type", "application/json").setBody(body),
                )

                val result = repository(testScheduler).addMusicianToBand(101, 100)

                assertEquals("body: $body", DataResult.Failure(DataError.Serialization), result)
            }
            assertRoomUntouched()
        }

    private fun assertRoomUntouched() {
        assertNull(dao.row(100)?.bandId)
        assertEquals(4, dao.row(101)?.memberCount)
        assertEquals(0, dao.writeCount)
    }

    private fun added() = JsonParser.parseString(readResource("api/band_musician_added.json")).asJsonObject

    private fun repository(scheduler: TestCoroutineScheduler): PerformerRepositoryImpl {
        val client = NetworkClient(serverRule.baseUrl.toString(), temporaryFolder.newFolder())
        clients += client
        val dispatcher = StandardTestDispatcher(scheduler)
        return PerformerRepositoryImpl(
            dao,
            PerformerServiceAdapterImpl({ client.create(VinilosApiService::class.java) }, dispatcher, dispatcher),
            dispatcher,
            FakeAlbumRepository(),
        )
    }
}
