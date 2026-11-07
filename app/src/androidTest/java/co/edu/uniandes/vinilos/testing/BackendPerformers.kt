package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.network.BackendEndpoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import java.net.HttpURLConnection
import java.net.URL

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204
private const val TIMEOUT_MILLIS = 10_000

/**
 * Datos de una prueba E2E del listado de artistas: crea músicos, bandas y álbumes en BackVynils local y, al terminar
 * la prueba, los borra en el orden que el backend admite (primero las pertenencias, después los artistas y los álbumes).
 * Cada prueba usa nombres únicos, así que no depende de cuántos artistas haya además de la semilla.
 * Los errores de preparación y de limpieza fallan la prueba: no se reintentan ni se ignoran.
 */
class BackendPerformers : ExternalResource() {
    private val albums = mutableListOf<Int>()
    private val musicians = mutableListOf<Int>()
    private val bands = mutableListOf<Int>()
    private val memberships = mutableListOf<Pair<Int, Int>>()

    /** Crea un músico y, si se pide, le asocia [albumCount] álbumes nuevos. Devuelve su id. */
    fun createMusician(
        name: String,
        birthDate: String = "1947-07-19T00:00:00.000Z",
        albumCount: Int = 0,
    ): Int {
        val body = performerBody(name).put("birthDate", birthDate)
        val id = send("POST", "musicians", body, HTTP_OK).getInt("id")
        musicians += id
        linkNewAlbums("musicians/$id", name, albumCount)
        return id
    }

    /** Crea una banda con [albumCount] álbumes nuevos y a [members] como integrantes. Devuelve su id. */
    fun createBand(
        name: String,
        creationDate: String = "1970-01-01T00:00:00.000Z",
        albumCount: Int = 0,
        members: List<Int> = emptyList(),
    ): Int {
        val body = performerBody(name).put("creationDate", creationDate)
        val id = send("POST", "bands", body, HTTP_OK).getInt("id")
        bands += id
        linkNewAlbums("bands/$id", name, albumCount)
        members.forEach { musicianId ->
            send("POST", "bands/$id/musicians/$musicianId", null, HTTP_OK)
            memberships += id to musicianId
        }
        return id
    }

    /** Removes an owned fixture to exercise the real 404 when its cached row is opened. */
    fun deleteMusician(id: Int) {
        require(id in musicians) { "Only owned test musicians can be deleted" }
        send("DELETE", "musicians/$id", null, HTTP_NO_CONTENT)
        musicians.remove(id)
        memberships.removeAll { it.second == id }
    }

    override fun after() {
        memberships.forEach { (bandId, musicianId) ->
            send("DELETE", "bands/$bandId/musicians/$musicianId", null, HTTP_NO_CONTENT)
        }
        musicians.forEach { send("DELETE", "musicians/$it", null, HTTP_NO_CONTENT) }
        bands.forEach { send("DELETE", "bands/$it", null, HTTP_NO_CONTENT) }
        albums.forEach { send("DELETE", "albums/$it", null, HTTP_NO_CONTENT) }
    }

    // Cada artista recibe álbumes propios: asociar los de la semilla cambiaría el artista que muestra el catálogo.
    private fun linkNewAlbums(
        performerPath: String,
        performerName: String,
        count: Int,
    ) {
        repeat(count) { index ->
            val albumId = createAlbum("$performerName álbum ${index + 1}")
            send("POST", "$performerPath/albums/$albumId", null, HTTP_OK)
        }
    }

    private fun createAlbum(name: String): Int {
        val body =
            JSONObject()
                .put("name", name)
                .put("cover", "https://example.com/e2e.jpg")
                .put("releaseDate", "1980-06-30T00:00:00.000Z")
                .put("description", "Álbum creado por una prueba E2E.")
                .put("genre", "Rock")
                .put("recordLabel", "EMI")
        return send("POST", "albums", body, HTTP_OK).getInt("id").also { albums += it }
    }

    private fun performerBody(name: String) =
        JSONObject()
            .put("name", name)
            .put("image", "https://example.com/e2e.jpg")
            .put("description", "Artista creado por una prueba E2E.")

    private fun send(
        method: String,
        path: String,
        body: JSONObject?,
        expectedCode: Int,
    ): JSONObject {
        val connection = URL("${BackendEndpoint.baseUrl}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            assertEquals("$method /$path", expectedCode, connection.responseCode)
            if (expectedCode == HTTP_NO_CONTENT) return JSONObject()
            return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
