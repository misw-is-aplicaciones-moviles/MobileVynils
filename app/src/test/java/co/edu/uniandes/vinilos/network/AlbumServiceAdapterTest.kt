package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.RecordLabel
import co.edu.uniandes.vinilos.repositories.AlbumRepositoryImpl
import co.edu.uniandes.vinilos.testing.FakeAlbumDao
import co.edu.uniandes.vinilos.testing.MockWebServerRule
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
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
import java.time.LocalDate
import java.util.concurrent.Executors

class AlbumServiceAdapterTest {
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
    fun `deserializes the backend albums`() =
        runTest {
            serverRule.enqueueJson("api/albums.json")

            val albums = newAdapter(testScheduler).getAlbums()

            assertEquals(4, albums.size)
            val first = albums.first()
            assertEquals("Buscando América", first.name)
            assertEquals("Rubén Blades Bellido de Luna", first.performerName)
            assertEquals(Genre.SALSA, first.genre)
            assertEquals(1984, first.releaseDate.year)
            val request = serverRule.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/albums", request.path)
        }

    @Test
    fun `empty backend list is an empty catalog`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody("[]"))

            val albums = newAdapter(testScheduler).getAlbums()

            assertTrue(albums.isEmpty())
        }

    @Test
    fun `album without performers has no performer name`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody(ALBUM_WITHOUT_PERFORMERS))

            val album = newAdapter(testScheduler).getAlbums().single()

            assertEquals("The Game", album.name)
            assertNull(album.performerName)
        }

    @Test
    fun `catalog connection cut becomes a network data error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Network), networkRequest { adapter.getAlbums() })
        }

    @Test
    fun `server error becomes an HTTP data error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(HTTP_SERVER_ERROR))
            val adapter = newAdapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Http(HTTP_SERVER_ERROR)),
                networkRequest { adapter.getAlbums() },
            )
        }

    @Test
    fun `malformed JSON becomes a serialization error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody("[{"))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbums() })
        }

    @Test
    fun `album without a name becomes a serialization error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody(ALBUM_WITHOUT_NAME))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbums() })
        }

    @Test
    fun `api provider is invoked once across two calls`() =
        runTest {
            repeat(2) { serverRule.enqueueJson("api/albums.json") }
            var providerCalls = 0
            val adapter = newAdapter(testScheduler) { providerCalls++ }

            adapter.getAlbums()
            adapter.getAlbums()

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
            serverRule.enqueueJson("api/albums.json")
            val executor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD_NAME) }
            try {
                var providerThread: String? = null
                val adapter =
                    newAdapter(
                        scheduler = testScheduler,
                        ioDispatcher = executor.asCoroutineDispatcher(),
                    ) { providerThread = Thread.currentThread().name }

                adapter.getAlbums()

                assertEquals(IO_THREAD_NAME, providerThread)
            } finally {
                executor.shutdown()
            }
        }

    @Test
    fun `create album posts exactly the six backend fields and maps the saved album`() =
        runTest {
            serverRule.enqueueJson("api/album_created.json")

            val album = newAdapter(testScheduler).createAlbum(draft)

            val request = serverRule.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/albums", request.path)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("name", "cover", "releaseDate", "description", "genre", "recordLabel"), body.keySet())
            assertEquals("The Game", body["name"].asString)
            assertEquals("https://example.com/the-game.jpg", body["cover"].asString)
            assertEquals("1980-06-30T00:00:00.000Z", body["releaseDate"].asString)
            assertEquals("Noveno álbum de Queen.", body["description"].asString)
            assertEquals("Rock", body["genre"].asString)
            assertEquals("EMI", body["recordLabel"].asString)
            assertEquals(101, album.id)
            assertEquals("The Game", album.name)
            assertEquals(Genre.ROCK, album.genre)
            assertEquals("EMI", album.recordLabel)
            assertEquals(LocalDate.of(1980, 6, 30), album.releaseDate)
        }

    @Test
    fun `create album with a missing field becomes HTTP 400 and stores nothing`() =
        runTest {
            serverRule.enqueueJson("api/album_create_missing_field.json", HTTP_BAD_REQUEST)
            val dao = FakeAlbumDao()
            val repository = AlbumRepositoryImpl(dao, newAdapter(testScheduler))

            val result = repository.createAlbum(draft)

            assertEquals(DataResult.Failure(DataError.Http(HTTP_BAD_REQUEST)), result)
            assertTrue(dao.getAll().isEmpty())
        }

    @Test
    fun `create album with a value outside the backend lists becomes HTTP 400 and stores nothing`() =
        runTest {
            serverRule.enqueueJson("api/album_create_invalid_genre.json", HTTP_BAD_REQUEST)
            val dao = FakeAlbumDao()
            val repository = AlbumRepositoryImpl(dao, newAdapter(testScheduler))

            val result = repository.createAlbum(draft)

            assertEquals(DataResult.Failure(DataError.Http(HTTP_BAD_REQUEST)), result)
            assertTrue(dao.getAll().isEmpty())
        }

    @Test
    fun `create album with the connection cut becomes a network error and stores nothing`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val dao = FakeAlbumDao()
            val repository = AlbumRepositoryImpl(dao, newAdapter(testScheduler))

            val result = repository.createAlbum(draft)

            assertEquals(DataResult.Failure(DataError.Network), result)
            assertTrue(dao.getAll().isEmpty())
        }

    @Test
    fun `detail query returns the requested album with its nested data`() =
        runTest {
            enqueueDetail()

            val album = newAdapter(testScheduler).getAlbum(100)

            assertEquals(100, album.id)
            assertEquals("Buscando América", album.name)
            assertTrue(album.coverUrl.startsWith("https://"))
            assertEquals(LocalDate.of(1984, 8, 1), album.releaseDate)
            assertEquals(Genre.SALSA, album.genre)
            assertEquals("Elektra", album.recordLabel)
            assertTrue(album.description.startsWith("Buscando América es el primer álbum"))
            assertEquals(listOf(100, 101), album.tracks.map { it.id })
            assertEquals(listOf("Decisiones", "Desapariciones"), album.tracks.map { it.name })
            assertEquals(listOf("5:05", "6:29"), album.tracks.map { it.duration })
            assertEquals(100, album.performers.single().id)
            assertEquals("Rubén Blades Bellido de Luna", album.performers.single().name)
            assertTrue(checkNotNull(album.performers.single().imageUrl).startsWith("https://"))
            assertEquals(100, album.comments.single().id)
            assertEquals("The most relevant album of Ruben Blades", album.comments.single().description)
            assertEquals(5, album.comments.single().rating)
            val request = serverRule.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/albums/100", request.path)
            assertNull(request.getHeader("Cache-Control"))
        }

    @Test
    fun `forced detail query revalidates the existing HTTP cache`() =
        runTest {
            enqueueDetail()

            newAdapter(testScheduler).getAlbum(100, forceRefresh = true)

            assertEquals("no-cache", serverRule.server.takeRequest().getHeader("Cache-Control"))
        }

    @Test
    fun `missing detail preserves the backend 404 response`() =
        runTest {
            serverRule.enqueueJson("api/album_not_found.json", HTTP_NOT_FOUND)
            val adapter = newAdapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Http(HTTP_NOT_FOUND)),
                networkRequest { adapter.getAlbum(MISSING_ALBUM_ID) },
            )
            assertEquals("/albums/$MISSING_ALBUM_ID", serverRule.server.takeRequest().path)
        }

    @Test
    fun `detail server failure becomes an HTTP data error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(HTTP_SERVER_ERROR))
            val adapter = newAdapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Http(HTTP_SERVER_ERROR)),
                networkRequest { adapter.getAlbum(100) },
            )
        }

    @Test
    fun `detail connection failure becomes a network data error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Network), networkRequest { adapter.getAlbum(100) })
        }

    @Test
    fun `malformed detail JSON becomes a serialization error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody("{invalid}"))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbum(100) })
        }

    @Test
    fun `detail without required name becomes a serialization error`() =
        runTest {
            val body = JsonParser.parseString(readResource("api/album_100.json")).asJsonObject
            body.remove("name")
            serverRule.server.enqueue(MockResponse().setBody(body.toString()))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbum(100) })
        }

    @Test
    fun `null detail body becomes a serialization error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody("null"))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbum(100) })
        }

    @Test
    fun `detail without content becomes a serialization error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(HTTP_NO_CONTENT))
            val adapter = newAdapter(testScheduler)

            assertEquals(DataResult.Failure(DataError.Serialization), networkRequest { adapter.getAlbum(100) })
        }

    @Test
    fun `detail with empty tracks and comments remains a valid album`() =
        runTest {
            val body = JsonParser.parseString(readResource("api/albums.json")).asJsonArray[1]
            serverRule.server.enqueue(MockResponse().setBody(body.toString()))

            val album = newAdapter(testScheduler).getAlbum(101)

            assertEquals(101, album.id)
            assertEquals("Poeta del pueblo", album.name)
            assertTrue(album.tracks.isEmpty())
            assertTrue(album.comments.isEmpty())
            assertEquals("/albums/101", serverRule.server.takeRequest().path)
        }

    @Test
    fun `add track posts only name and duration and maps the response`() =
        runTest {
            serverRule.enqueueJson("api/track_100_created.json", HTTP_CREATED)

            val track = newAdapter(testScheduler).addTrack(100, NewTrack("Bohemian Rhapsody", "5:55"))

            assertEquals(Track(1, "Bohemian Rhapsody", "5:55"), track)
            val request = serverRule.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/albums/100/tracks", request.path)
            assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"))
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("name", "duration"), body.keySet())
            assertEquals("Bohemian Rhapsody", body.get("name").asString)
            assertEquals("5:55", body.get("duration").asString)
        }

    @Test
    fun `add track maps HTTP failures through network request`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setResponseCode(HTTP_BAD_REQUEST))
            val adapter = newAdapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Http(HTTP_BAD_REQUEST)),
                networkRequest { adapter.addTrack(100, NewTrack("Song", "5:55")) },
            )
            assertEquals("/albums/100/tracks", serverRule.server.takeRequest().path)
        }

    @Test
    fun `add track rejects a response for a different album`() =
        runTest {
            serverRule.server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(TRACK_RESPONSE_FOR_OTHER_ALBUM),
            )
            val adapter = newAdapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Serialization),
                networkRequest { adapter.addTrack(100, NewTrack("Song", "5:55")) },
            )
        }

    @Test
    fun `add track response without duration is a serialization error`() =
        runTest {
            serverRule.server.enqueue(MockResponse().setBody("""{"id":1,"name":"Song"}"""))
            val adapter = newAdapter(testScheduler)

            assertEquals(
                DataResult.Failure(DataError.Serialization),
                networkRequest { adapter.addTrack(100, NewTrack("Song", "5:55")) },
            )
        }

    private fun enqueueDetail() {
        serverRule.enqueueJson("api/album_100.json")
    }

    private fun newAdapter(
        scheduler: TestCoroutineScheduler,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler),
        onProvide: () -> Unit = {},
    ): AlbumServiceAdapterImpl {
        val dispatcher = StandardTestDispatcher(scheduler)
        return AlbumServiceAdapterImpl(
            apiProvider = {
                onProvide()
                val client =
                    NetworkClient(
                        baseUrl = serverRule.baseUrl.toString(),
                        cacheDirectory = temporaryFolder.newFolder(),
                    )
                clients.add(client)
                client.create(VinilosApiService::class.java)
            },
            ioDispatcher = ioDispatcher,
            defaultDispatcher = dispatcher,
        )
    }

    private companion object {
        const val MISSING_ALBUM_ID = 2147483647
        const val HTTP_NOT_FOUND = 404
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_CREATED = 201
        const val HTTP_NO_CONTENT = 204
        const val HTTP_SERVER_ERROR = 500
        const val HTTP_BAD_REQUEST = 400

        val draft =
            NewAlbum(
                name = "The Game",
                coverUrl = "https://example.com/the-game.jpg",
                releaseDate = LocalDate.of(1980, 6, 30),
                description = "Noveno álbum de Queen.",
                genre = Genre.ROCK,
                recordLabel = RecordLabel.EMI,
            )
        const val IO_THREAD_NAME = "io-test"
        const val ALBUM_WITHOUT_NAME = "[{\"id\":1,\"releaseDate\":\"1984-08-01T00:00:00.000Z\"}]"
        const val TRACK_RESPONSE_FOR_OTHER_ALBUM =
            """{"id":1,"name":"Song","duration":"5:55","album":{"id":101}}"""
        const val ALBUM_WITHOUT_PERFORMERS =
            "[{\"id\":104,\"name\":\"The Game\",\"cover\":\"https://example.com/the-game.jpg\"," +
                "\"releaseDate\":\"1980-06-30T00:00:00.000Z\",\"description\":\"Noveno álbum de Queen.\"," +
                "\"genre\":\"Rock\",\"recordLabel\":\"EMI\",\"tracks\":[],\"performers\":[],\"comments\":[]}]"
    }
}
