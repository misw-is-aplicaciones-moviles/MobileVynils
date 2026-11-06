package co.edu.uniandes.vinilos.repositories

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewPrize
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import co.edu.uniandes.vinilos.network.NetworkClient
import co.edu.uniandes.vinilos.network.PrizeServiceAdapterImpl
import co.edu.uniandes.vinilos.network.VinilosApiService
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.readResource
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
import java.util.TimeZone

/** Contratos HU14 a través del Repository, Adapter y cliente HTTP reales, con respuestas controladas. */
@Suppress("MagicNumber") // Synthetic fixture IDs and HTTP status codes.
class PrizeRepositoryImplTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @get:Rule val serverRule = MockWebServerRule()

    private val clients = mutableListOf<NetworkClient>()
    private val date = LocalDate.of(1985, 9, 18)
    private val draft =
        NewPrize(
            name = "Voz del Caribe",
            description = "Reconoce a intérpretes que llevaron la música del Caribe a nuevas audiencias.",
            organization = "Asociación de Coleccionistas de Vinilo",
        )

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
            val repository = repository(testScheduler)
            val body = readResource("api/prizes.json")
            serverRule.server.enqueue(MockResponse().setBody(body).setHeader("Cache-Control", "public, max-age=3600"))
            val initial = repository.getPrizes() as DataResult.Success
            assertEquals("Premio de pruebas", initial.value.first().name)
            serverRule.server.takeRequest()
            val updated = JsonParser.parseString(body).asJsonArray
            updated[0].asJsonObject.addProperty("name", "Premio actualizado")
            serverRule.server.enqueue(MockResponse().setBody(updated.toString()))

            val refreshed = repository.getPrizes(forceRefresh = true) as DataResult.Success

            assertEquals("Premio actualizado", refreshed.value.first().name)
            assertEquals("no-cache", serverRule.server.takeRequest().getHeader("Cache-Control"))
            assertEquals(2, serverRule.server.requestCount)
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
            for (body in listOf("null", "[null]", "{", "{}", "", json.toString())) {
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

            for ((artistId, prizeId) in listOf(0 to 71, 83 to 0, -1 to 71, 83 to -1)) {
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
            PerformerType.entries.forEach { type ->
                for (code in listOf(400, 404, 500)) {
                    serverRule.server.enqueue(MockResponse().setResponseCode(code))

                    assertEquals(
                        DataResult.Failure(DataError.Http(code)),
                        repository.assignPrize(83, type, 71, date),
                    )
                }
            }
            assertEquals(6, serverRule.server.requestCount)
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
    fun `missing response date or identities are serialization errors`() =
        runTest {
            val repository = repository(testScheduler)
            listOf("id", "prize", "performer", "premiationDate").forEach { field ->
                val json = JsonParser.parseString(readResource("api/prize_assignment.json")).asJsonObject
                json.remove(field)
                serverRule.server.enqueue(MockResponse().setBody(json.toString()))
                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    repository.assignPrize(83, PerformerType.BAND, 71, date),
                )
            }
        }

    @Test
    fun `invalid success bodies are not confirmed assignments`() =
        runTest {
            val repository = repository(testScheduler)
            val responses =
                listOf(
                    MockResponse().setResponseCode(204),
                    MockResponse().setBody(""),
                    MockResponse().setBody("null"),
                    MockResponse().setBody("{"),
                )
            responses.forEach { response ->
                serverRule.server.enqueue(response)
                assertEquals(
                    DataResult.Failure(DataError.Serialization),
                    repository.assignPrize(83, PerformerType.BAND, 71, date),
                )
            }
            assertEquals(responses.size, serverRule.server.requestCount)
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

    @Test
    fun `catalog HTTP and transport failures recover only after an explicit refresh`() =
        runTest {
            val failures =
                listOf(
                    MockResponse().setResponseCode(404) to DataError.Http(404),
                    MockResponse().setResponseCode(500) to DataError.Http(500),
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) to DataError.Network,
                    MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) to DataError.Network,
                )
            failures.forEach { (response, error) ->
                // DISCONNECT_AT_START debe actuar sobre una conexión nueva, no sobre el pool del caso anterior.
                val repository = repository(testScheduler, readTimeoutMillis = 100)
                val before = serverRule.server.requestCount
                serverRule.server.enqueue(response)
                assertEquals(DataResult.Failure(error), repository.getPrizes(forceRefresh = true))
                assertEquals(before + 1, serverRule.server.requestCount)
                serverRule.enqueueJson("api/prizes.json")

                val recovered = repository.getPrizes(forceRefresh = true) as DataResult.Success

                assertEquals(listOf(71, 72), recovered.value.map { it.id })
                assertEquals(before + 2, serverRule.server.requestCount)
            }
        }

    @Test
    fun `assignment timeout is a network error and never resends the POST`() =
        runTest {
            val repository = repository(testScheduler, readTimeoutMillis = 100)
            PerformerType.entries.forEach { type ->
                val before = serverRule.server.requestCount
                serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

                assertEquals(DataResult.Failure(DataError.Network), repository.assignPrize(83, type, 71, date))

                assertEquals(before + 1, serverRule.server.requestCount)
                val request = serverRule.server.takeRequest()
                assertEquals("POST", request.method)
                val collection = if (type == PerformerType.MUSICIAN) "musicians" else "bands"
                assertEquals("/prizes/71/$collection/83", request.path)
            }
        }

    @Test
    fun `assignment and catalog preserve the UTC day under Bogota timezone`() =
        runTest {
            val previous = TimeZone.getDefault()
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"))
                val repository = repository(testScheduler)
                PerformerType.entries.forEach { type ->
                    serverRule.enqueueJson("api/prize_assignment.json")
                    val assigned = repository.assignPrize(83, type, 71, date) as DataResult.Success
                    assertEquals(date, assigned.value.premiationDate)
                    val body =
                        JsonParser
                            .parseString(
                                serverRule.server
                                    .takeRequest()
                                    .body
                                    .readUtf8(),
                            ).asJsonObject
                    assertEquals("1985-09-18T00:00:00.000Z", body["premiationDate"].asString)
                }
                serverRule.enqueueJson("api/prizes.json")
                val prizes = repository.getArtistPrizes(listOf(PrizeAssignmentRef(901, date))) as DataResult.Success
                assertEquals(date, prizes.value.single().premiationDate)
            } finally {
                TimeZone.setDefault(previous)
            }
        }

    @Test
    fun `a repeated association ID across different prizes is rejected as ambiguous`() =
        runTest {
            val catalog = JsonParser.parseString(readResource("api/prizes.json")).asJsonArray
            catalog[1].asJsonObject.add("performerPrizes", catalog[0].asJsonObject["performerPrizes"].deepCopy())
            serverRule.server.enqueue(MockResponse().setBody(catalog.toString()))

            val result = repository(testScheduler).getArtistPrizes(listOf(PrizeAssignmentRef(901, date)))

            assertEquals(DataResult.Failure(DataError.Serialization), result)
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `create prize posts exactly the three backend fields and maps the saved prize`() =
        runTest {
            serverRule.enqueueJson("api/prize_created.json")

            val result = repository(testScheduler).createPrize(draft)

            val created = (result as DataResult.Success).value
            assertEquals(Prize(75, draft.name, draft.description, draft.organization), created)
            assertTrue(created.assignments.isEmpty())
            val request = serverRule.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/prizes", request.path)
            assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
            val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("name", "description", "organization"), json.keySet())
            assertEquals(draft.name, json["name"].asString)
            assertEquals(draft.description, json["description"].asString)
            assertEquals(draft.organization, json["organization"].asString)
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `create prize sends accents quotes and line breaks unchanged`() =
        runTest {
            val special = NewPrize("Año «Vinilo» \"2026\"", "Línea 1\nLínea 2 ñ", "Org & Co. <ñandú>")
            serverRule.server.enqueue(
                MockResponse().setBody(
                    JsonParser
                        .parseString(readResource("api/prize_created.json"))
                        .asJsonObject
                        .apply {
                            addProperty("name", special.name)
                            addProperty("description", special.description)
                            addProperty("organization", special.organization)
                        }.toString(),
                ),
            )

            val result = repository(testScheduler).createPrize(special)

            val saved = (result as DataResult.Success).value
            assertEquals(special, NewPrize(saved.name, saved.description, saved.organization))
            val request = serverRule.server.takeRequest()
            val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(special.name, json["name"].asString)
            assertEquals(special.description, json["description"].asString)
            assertEquals(special.organization, json["organization"].asString)
        }

    @Test
    fun `a 2xx without a valid id name description or organization is a serialization error`() =
        runTest {
            val repository = repository(testScheduler)
            val invalid =
                listOf("id", "name", "description", "organization").flatMap { field ->
                    listOf(
                        mutatedCreated { remove(field) },
                        mutatedCreated { add(field, JsonNull.INSTANCE) },
                    )
                } +
                    listOf(
                        mutatedCreated { addProperty("id", 0) },
                        mutatedCreated { addProperty("id", -3) },
                        mutatedCreated { addProperty("id", "abc") },
                        mutatedCreated { addProperty("id", 7.5) },
                    )
            invalid.forEach { body ->
                serverRule.server.enqueue(MockResponse().setBody(body))

                assertEquals(body, DataResult.Failure(DataError.Serialization), repository.createPrize(draft))
            }
            assertEquals(invalid.size, serverRule.server.requestCount)
        }

    @Test
    fun `a 2xx for a different prize is not a confirmation`() =
        runTest {
            val repository = repository(testScheduler)
            listOf("name", "description", "organization").forEach { field ->
                serverRule.server.enqueue(MockResponse().setBody(mutatedCreated { addProperty(field, "Otro valor") }))

                assertEquals(DataResult.Failure(DataError.Serialization), repository.createPrize(draft))
            }
        }

    @Test
    fun `invalid success bodies are not a created prize`() =
        runTest {
            val repository = repository(testScheduler)
            val responses =
                listOf(
                    MockResponse().setResponseCode(204),
                    MockResponse().setBody(""),
                    MockResponse().setBody("null"),
                    MockResponse().setBody("{"),
                    MockResponse().setBody("[]"),
                    MockResponse().setBody("<html>ok</html>"),
                )
            responses.forEach { response ->
                serverRule.server.enqueue(response)

                assertEquals(DataResult.Failure(DataError.Serialization), repository.createPrize(draft))
            }
            assertEquals(responses.size, serverRule.server.requestCount)
        }

    @Test
    fun `create prize keeps the HTTP 400 of the backend and does not retry`() =
        runTest {
            serverRule.enqueueJson("api/prize_create_missing_field.json", code = 400)

            val result = repository(testScheduler).createPrize(draft)

            assertEquals(DataResult.Failure(DataError.Http(400)), result)
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `create prize keeps the HTTP 500 of the backend and does not retry`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(500).setBody("""{"statusCode":500}"""))

            val result = repository(testScheduler).createPrize(draft)

            assertEquals(DataResult.Failure(DataError.Http(500)), result)
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `create prize timeout is a network error and never resends the POST`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            serverRule.enqueueJson("api/prize_created.json")

            val result = repository(testScheduler, readTimeoutMillis = 100).createPrize(draft)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(1, serverRule.server.requestCount)
            val request = serverRule.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/prizes", request.path)
            // The queued 200 is still there: nothing resent the request after the timeout.
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `create prize with a lost response is a network error without an automatic retry`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            serverRule.enqueueJson("api/prize_created.json")

            val result = repository(testScheduler).createPrize(draft)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertEquals(1, serverRule.server.requestCount)
        }

    @Test
    fun `a blank draft does not reach the network`() =
        runTest {
            val repository = repository(testScheduler)
            val blanks =
                listOf(
                    draft.copy(name = ""),
                    draft.copy(name = "   "),
                    draft.copy(description = "\n"),
                    draft.copy(organization = " "),
                )

            blanks.forEach { blank ->
                val error = runCatching { repository.createPrize(blank) }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }

            assertEquals(0, clients.size)
            assertEquals(0, serverRule.server.requestCount)
        }

    private fun mutatedCreated(change: JsonObject.() -> Unit): String =
        JsonParser
            .parseString(readResource("api/prize_created.json"))
            .asJsonObject
            .apply(change)
            .toString()

    private fun repository(
        scheduler: TestCoroutineScheduler,
        readTimeoutMillis: Long = 30_000,
    ): PrizeRepository {
        val dispatcher = StandardTestDispatcher(scheduler)
        return PrizeRepositoryImpl(
            PrizeServiceAdapterImpl(
                apiProvider = {
                    val client =
                        NetworkClient(
                            serverRule.baseUrl.toString(),
                            temporaryFolder.newFolder(),
                            readTimeoutMillis = readTimeoutMillis,
                        )
                    clients += client
                    client.create(VinilosApiService::class.java)
                },
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            ),
        )
    }
}
