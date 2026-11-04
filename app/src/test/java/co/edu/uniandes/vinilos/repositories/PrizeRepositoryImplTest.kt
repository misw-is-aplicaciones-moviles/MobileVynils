package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import co.edu.uniandes.vinilos.network.NetworkClient
import co.edu.uniandes.vinilos.network.PrizeServiceAdapterImpl
import co.edu.uniandes.vinilos.network.VinilosApiService
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.readResource
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

/** Initial HU14-02 regression coverage through the real Repository, Adapter and HTTP client. */
@Suppress("MagicNumber") // Synthetic fixture IDs and HTTP status codes.
class PrizeRepositoryImplTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @get:Rule val serverRule = MockWebServerRule()

    private val clients = mutableListOf<NetworkClient>()
    private val date = LocalDate.of(1985, 9, 18)

    @After
    fun closeClients() {
        clients.forEach(NetworkClient::close)
    }

    @Test
    fun `catalog maps prizes and association IDs independently`() =
        runTest {
            serverRule.enqueueJson("api/prizes.json")

            val result = repository(testScheduler).getPrizes() as DataResult.Success

            assertEquals(listOf(71, 72), result.value.map { it.id })
            val prize = result.value.first()
            assertEquals("Premio de pruebas", prize.name)
            assertEquals("Organización de pruebas", prize.organization)
            assertEquals(listOf(901, 902), prize.assignments.map { it.id })
            assertEquals(date, prize.assignments.first().premiationDate)
            val request = serverRule.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/prizes", request.path)
        }

    @Test
    fun `forced catalog refresh revalidates HTTP`() =
        runTest {
            serverRule.enqueueJson("api/prizes.json")

            repository(testScheduler).getPrizes(forceRefresh = true)

            assertEquals("no-cache", serverRule.server.takeRequest().getHeader("Cache-Control"))
        }

    @Test
    fun `empty catalog is successful`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody("[]"))

            assertEquals(DataResult.Success(emptyList<Any>()), repository(testScheduler).getPrizes())
        }

    @Test
    fun `null catalog entries and missing required fields are serialization errors`() =
        runTest {
            val json = JsonParser.parseString(readResource("api/prizes.json")).asJsonArray
            json[0].asJsonObject.remove("organization")
            val repository = repository(testScheduler)
            for (body in listOf("null", "[null]", json.toString())) {
                serverRule.server.enqueue(MockResponse().setBody(body))

                assertEquals(DataResult.Failure(DataError.Serialization), repository.getPrizes())
            }
        }

    @Test
    fun `musician assignment sends only the mandatory date and returns the confirmed association`() =
        runTest {
            serverRule.enqueueJson("api/prize_assignment.json")

            val result = repository(testScheduler).assignPrize(83, PerformerType.MUSICIAN, 71, date)
            val assigned = (result as DataResult.Success).value

            assertEquals(903, assigned.id)
            assertEquals(71, assigned.prize.id)
            assertEquals(date, assigned.premiationDate)
            val request = serverRule.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/prizes/71/musicians/83", request.path)
            val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("premiationDate"), json.keySet())
            assertEquals("1985-09-18T00:00:00.000Z", json["premiationDate"].asString)
        }

    @Test
    fun `band assignment uses the band endpoint`() =
        runTest {
            serverRule.enqueueJson("api/prize_assignment.json")

            val result = repository(testScheduler).assignPrize(83, PerformerType.BAND, 71, date)

            assertTrue(result is DataResult.Success)
            assertEquals("/prizes/71/bands/83", serverRule.server.takeRequest().path)
        }

    @Test
    fun `invalid IDs do not initialize the HTTP client`() =
        runTest {
            val repository = repository(testScheduler)

            for ((artistId, prizeId) in listOf(0 to 71, 83 to 0)) {
                val error =
                    runCatching { repository.assignPrize(artistId, PerformerType.MUSICIAN, prizeId, date) }
                        .exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }

            assertEquals(0, clients.size)
            assertEquals(0, serverRule.server.requestCount)
        }

    @Test
    fun `assignment preserves backend HTTP errors`() =
        runTest {
            val repository = repository(testScheduler)
            for (code in listOf(400, 404, 500)) {
                serverRule.server.enqueue(MockResponse().setResponseCode(code))

                assertEquals(
                    DataResult.Failure(DataError.Http(code)),
                    repository.assignPrize(83, PerformerType.BAND, 71, date),
                )
            }
        }

    @Test
    fun `a lost POST response is a network error without an automatic retry`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

            val result = repository(testScheduler).assignPrize(83, PerformerType.MUSICIAN, 71, date)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `assignment rejects a different prize artist or date in the response`() =
        runTest {
            val repository = repository(testScheduler)
            val original = readResource("api/prize_assignment.json")
            for (body in listOf(
                original.replace("71", "72"),
                original.replace("83", "84"),
                original.replace("1985", "1986"),
            )) {
                serverRule.server.enqueue(MockResponse().setBody(body))

                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    repository.assignPrize(83, PerformerType.MUSICIAN, 71, date),
                )
            }
        }

    @Test
    fun `missing response date is a serialization error`() =
        runTest {
            val json = JsonParser.parseString(readResource("api/prize_assignment.json")).asJsonObject
            json.remove("premiationDate")
            serverRule.server.enqueue(MockResponse().setBody(json.toString()))

            assertEquals(
                DataResult.Failure(DataError.Serialization),
                repository(testScheduler).assignPrize(83, PerformerType.BAND, 71, date),
            )
        }

    @Test
    fun `empty success body is not a confirmed assignment`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(204))

            assertEquals(
                DataResult.Failure(DataError.Serialization),
                repository(testScheduler).assignPrize(83, PerformerType.BAND, 71, date),
            )
        }

    @Test
    fun `artist prizes resolve by association ID with one catalog request`() =
        runTest {
            serverRule.enqueueJson("api/prizes.json")
            val references = listOf(PrizeAssignmentRef(901, date), PrizeAssignmentRef(902, date.plusYears(1)))

            val result = repository(testScheduler).getArtistPrizes(references, forceRefresh = true)
            val awards = (result as DataResult.Success).value

            assertEquals(listOf(901, 902), awards.map { it.id })
            assertEquals(listOf(71, 71), awards.map { it.prize.id })
            assertEquals(date.plusYears(1), awards.last().premiationDate)
            assertEquals(1, serverRule.server.requestCount)
            assertEquals("no-cache", serverRule.server.takeRequest().getHeader("Cache-Control"))
        }

    @Test
    fun `artist without assignments does not query the catalog`() =
        runTest {
            val result = repository(testScheduler).getArtistPrizes(emptyList())

            assertEquals(DataResult.Success(emptyList<Any>()), result)
            assertTrue(clients.isEmpty())
        }

    @Test
    fun `unresolved association fails instead of falsely showing no prizes`() =
        runTest {
            serverRule.enqueueJson("api/prizes.json")

            val result = repository(testScheduler).getArtistPrizes(listOf(PrizeAssignmentRef(999, date)))

            assertEquals(DataResult.Failure(DataError.Serialization), result)
        }

    @Test
    fun `inconsistent association date fails instead of returning an unrelated prize`() =
        runTest {
            serverRule.enqueueJson("api/prizes.json")

            val result = repository(testScheduler).getArtistPrizes(listOf(PrizeAssignmentRef(901, date.plusDays(1))))

            assertEquals(DataResult.Failure(DataError.Serialization), result)
        }

    private fun repository(scheduler: TestCoroutineScheduler): PrizeRepository {
        val dispatcher = StandardTestDispatcher(scheduler)
        return PrizeRepositoryImpl(
            PrizeServiceAdapterImpl(
                apiProvider = {
                    val client = NetworkClient(serverRule.baseUrl.toString(), temporaryFolder.newFolder())
                    clients += client
                    client.create(VinilosApiService::class.java)
                },
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            ),
        )
    }
}
