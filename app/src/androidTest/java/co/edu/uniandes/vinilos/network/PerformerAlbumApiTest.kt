package co.edu.uniandes.vinilos.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.BuildConfig
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.testing.BackendPerformers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.File
import java.net.URI

/** Real HTTP contract, using owned fixtures and the production Retrofit client and Service Adapters. */
@RunWith(AndroidJUnit4::class)
class PerformerAlbumApiTest {
    private val baseUrl = InstrumentationRegistry.getArguments().getString("hu15ApiBaseUrl") ?: BuildConfig.BASE_URL

    @get:Rule val data = BackendPerformers(baseUrl)
    private val mark = "HU15 API ${System.nanoTime()}"
    private lateinit var client: NetworkClient
    private lateinit var cacheDirectory: File
    private lateinit var api: VinilosApiService
    private lateinit var artists: PerformerServiceAdapter
    private lateinit var albums: AlbumServiceAdapter

    @Before
    fun openClient() {
        val uri = URI(baseUrl)
        check(uri.host in setOf("127.0.0.1", "localhost", "10.0.2.2") && baseUrl.endsWith("/")) {
            "HU15 API requires a local test backend with a trailing slash"
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        cacheDirectory = File(context.cacheDir, "hu15-api-${System.nanoTime()}")
        client = NetworkClient(baseUrl, cacheDirectory)
        api = client.create(VinilosApiService::class.java)
        artists = PerformerServiceAdapterImpl({ api }, Dispatchers.IO, Dispatchers.Default)
        albums = AlbumServiceAdapterImpl({ api }, Dispatchers.IO, Dispatchers.Default)
    }

    @After
    fun closeClient() {
        if (::client.isInitialized) client.close()
        if (::cacheDirectory.isInitialized) check(cacheDirectory.deleteRecursively())
    }

    @Test
    fun musician_returns_200_album_id_and_complete_relationships_without_a_body() =
        runTest {
            val id = data.createMusician(mark)
            val albumId = data.createAlbum("$mark libre")
            assertTrue(albums.getAlbumForAssociation(albumId).performers.isEmpty())

            assertPost(PerformerType.MUSICIAN, id, albumId)

            assertEquals(
                setOf(id),
                albums
                    .getAlbumForAssociation(albumId)
                    .performers
                    .map { it.id }
                    .toSet(),
            )
            val catalog = checkNotNull(api.getAlbumsForAssociation().body())
            assertTrue(catalog.first { it?.id == albumId }?.performers?.any { it.id == id } == true)
            assertEquals(
                setOf(albumId),
                artists
                    .getPerformer(id, PerformerType.MUSICIAN, true)
                    .albums
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun band_returns_200_and_refresh_preserves_its_other_album_and_member() =
        runTest {
            val member = data.createMusician("$mark integrante")
            val id = data.createBand(mark, albumCount = 1, members = listOf(member))
            val before = artists.getPerformer(id, PerformerType.BAND, true)
            val albumId = data.createAlbum("$mark libre")

            assertPost(PerformerType.BAND, id, albumId)

            val after = artists.getPerformer(id, PerformerType.BAND, true)
            assertEquals(before.albums.map { it.id }.toSet() + albumId, after.albums.map { it.id }.toSet())
            assertEquals(setOf(member), after.members.map { it.id }.toSet())
            assertEquals(before.prizeAssignments, after.prizeAssignments)
            assertEquals(
                setOf(id),
                albums
                    .getAlbumForAssociation(albumId)
                    .performers
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun unknown_artists_return_404_for_both_routes_and_leave_the_album_free() =
        runTest {
            val albumId = data.createAlbum("$mark libre")
            assertEquals(HTTP_NOT_FOUND, api.getMusician(ABSENT_ID, "no-cache").code())
            assertEquals(HTTP_NOT_FOUND, api.getBand(ABSENT_ID, "no-cache").code())
            for (type in PerformerType.entries) {
                assertEquals(
                    DataResult.Failure(DataError.Http(HTTP_NOT_FOUND)),
                    networkRequest { artists.addAlbum(ABSENT_ID, type, albumId) },
                )
                assertTrue(albums.getAlbumForAssociation(albumId).performers.isEmpty())
            }
        }

    @Test
    fun unknown_album_returns_404_for_both_routes_without_changing_the_artists() =
        runTest {
            assertEquals(HTTP_NOT_FOUND, api.getAlbum(ABSENT_ID, "no-cache").code())
            val identities =
                listOf(
                    data.createMusician("$mark músico") to PerformerType.MUSICIAN,
                    data.createBand("$mark banda") to PerformerType.BAND,
                )
            for ((id, type) in identities) {
                assertEquals(
                    DataResult.Failure(DataError.Http(HTTP_NOT_FOUND)),
                    networkRequest { artists.addAlbum(id, type, ABSENT_ID) },
                )
                assertTrue(artists.getPerformer(id, type, true).albums.isEmpty())
            }
        }

    @Test
    fun occupied_album_appends_musicians_but_band_replaces_the_existing_relationships() =
        runTest {
            val first = data.createMusician("$mark primero")
            val second = data.createMusician("$mark segundo")
            val band = data.createBand("$mark banda")
            val albumId = data.createAlbum("$mark ocupado")
            data.associateAlbum(first, PerformerType.MUSICIAN, albumId)

            assertPost(PerformerType.MUSICIAN, second, albumId)
            assertEquals(
                setOf(first, second),
                albums
                    .getAlbumForAssociation(albumId)
                    .performers
                    .map { it.id }
                    .toSet(),
            )

            // Characterizes the backend risk. The app blocks occupied albums before reaching this endpoint.
            assertPost(PerformerType.BAND, band, albumId)
            assertEquals(
                setOf(band),
                albums
                    .getAlbumForAssociation(albumId)
                    .performers
                    .map { it.id }
                    .toSet(),
            )
            assertTrue(artists.getPerformer(first, PerformerType.MUSICIAN, true).albums.isEmpty())
            assertTrue(artists.getPerformer(second, PerformerType.MUSICIAN, true).albums.isEmpty())
        }

    private suspend fun assertPost(
        type: PerformerType,
        id: Int,
        albumId: Int,
    ) {
        val segment = if (type == PerformerType.MUSICIAN) "musicians" else "bands"
        val response: Response<AlbumDto> =
            when (type) {
                PerformerType.MUSICIAN -> api.addAlbumToMusician(id, albumId)
                PerformerType.BAND -> api.addAlbumToBand(id, albumId)
            }
        assertEquals(HTTP_OK, response.code())
        assertNotNull(response.body())
        assertEquals(albumId, response.body()?.id)
        val request = response.raw().request
        assertEquals("POST", request.method)
        assertEquals("/$segment/$id/albums/$albumId", request.url.encodedPath)
        assertEquals(0L, request.body?.contentLength() ?: 0L)
        assertNull(request.header("Content-Type"))
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        const val ABSENT_ID = Int.MAX_VALUE
    }
}
