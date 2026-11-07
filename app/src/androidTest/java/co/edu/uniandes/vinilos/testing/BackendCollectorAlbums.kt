package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.network.BackendEndpoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import java.net.HttpURLConnection
import java.net.URL

private const val HTTP_OK = 200
private const val TIMEOUT_MILLIS = 10_000
private const val TELEPHONE = "3000000000"
private const val HTTP_ERROR = 400

/** Un vínculo tal como lo devuelve `GET /collectors/:id/albums`. */
data class CollectorAlbumLink(
    val albumId: Int,
    val albumName: String,
    val price: Int,
    val status: String,
)

/** Datos propios de HU11. La base completa se descarta al final; no usar DELETE de vínculos defectuoso. */
class BackendCollectorAlbums : ExternalResource() {
    private val collectors = mutableListOf<Int>()
    private val albums = mutableSetOf<Int>()

    /** Crea un coleccionista sin álbumes ni favoritos. Devuelve su id. */
    fun createCollector(
        name: String,
        email: String,
    ): Int {
        val body =
            JSONObject()
                .put("name", name)
                .put("telephone", TELEPHONE)
                .put("email", email)
        return JSONObject(send("POST", "collectors", body, HTTP_OK)).getInt("id").also { collectors += it }
    }

    /** Crea un álbum sin artista. El año queda en [RELEASE_YEAR]. [genre] sale de las listas del backend. */
    fun createAlbum(
        name: String,
        genre: String = "Rock",
    ): Int {
        val body =
            JSONObject()
                .put("name", name)
                .put("cover", "https://example.com/e2e.jpg")
                .put("releaseDate", "$RELEASE_YEAR-06-30T00:00:00.000Z")
                .put("description", "Álbum creado por una prueba E2E.")
                .put("genre", genre)
                .put("recordLabel", "EMI")
        return JSONObject(send("POST", "albums", body, HTTP_OK)).getInt("id").also { albums += it }
    }

    /** Asocia un álbum creado por esta regla a un coleccionista creado por esta regla. */
    fun link(
        collectorId: Int,
        albumId: Int,
        price: Int,
        status: String,
    ) {
        check(collectorId in collectors) { "El coleccionista $collectorId no lo creó esta prueba" }
        check(albumId in albums) { "El álbum $albumId no lo creó esta prueba" }
        val body = JSONObject().put("price", price).put("status", status)
        send("POST", "collectors/$collectorId/albums/$albumId", body, HTTP_OK)
    }

    fun albumsOf(collectorId: Int): List<CollectorAlbumLink> {
        val response = JSONArray(send("GET", "collectors/$collectorId/albums", null, HTTP_OK))
        return List(response.length()) { index ->
            val item = response.optJSONObject(index) ?: error("Vínculo nulo en la posición $index")
            val album = item.optJSONObject("album") ?: error("Vínculo sin álbum anidado")
            CollectorAlbumLink(
                albumId = album.getInt("id"),
                albumName = album.getString("name"),
                price = item.getInt("price"),
                status = item.getString("status"),
            )
        }
    }

    private fun send(
        method: String,
        path: String,
        body: JSONObject?,
        expectedCode: Int,
    ): String {
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
            val payload =
                if (connection.responseCode >= HTTP_ERROR) {
                    connection.errorStream
                } else {
                    connection.inputStream
                }
            val text = payload?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            assertEquals("$method /$path $text", expectedCode, connection.responseCode)
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELEASE_YEAR = 1980
    }
}
