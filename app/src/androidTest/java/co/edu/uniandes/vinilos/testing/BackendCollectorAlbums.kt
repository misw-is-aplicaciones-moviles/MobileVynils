package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import java.net.HttpURLConnection
import java.net.URL

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204
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

/**
 * Coleccionista y álbumes de una ejecución. Los crea con POST y, al terminar, borra solo los vínculos de esos
 * coleccionistas cuyo álbum también creó esta regla. Después borra el coleccionista y el álbum. No llama al DELETE
 * de un vínculo ajeno: antes de borrar anota cuántos álbumes tenía cada coleccionista que ya existía y, al final,
 * comprueba que ese número no bajó.
 */
class BackendCollectorAlbums : ExternalResource() {
    private val collectors = mutableListOf<Int>()
    private val albums = mutableSetOf<Int>()
    private var albumCountsAtStart: Map<Int, Int> = emptyMap()

    override fun before() {
        albumCountsAtStart = albumCounts()
    }

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

    override fun after() {
        collectors.forEach { collectorId -> removeOwnedLinks(collectorId) }
        collectors.forEach { send("DELETE", "collectors/$it", null, HTTP_NO_CONTENT) }
        albums.forEach { send("DELETE", "albums/$it", null, HTTP_NO_CONTENT) }
        val now = albumCounts()
        albumCountsAtStart.forEach { (id, count) ->
            assertEquals("El coleccionista $id perdió álbumes durante la limpieza", count, now[id] ?: 0)
        }
    }

    private fun removeOwnedLinks(collectorId: Int) {
        repeat(albums.size + 1) {
            val current = albumsOf(collectorId)
            val target = current.firstOrNull { it.albumId in albums } ?: return
            val others = current.filter { it.albumId != target.albumId }.map { it.albumId }.toSet()
            send("DELETE", "collectors/$collectorId/albums/${target.albumId}", null, HTTP_NO_CONTENT)
            val after = albumsOf(collectorId).map { it.albumId }.toSet()
            check(target.albumId !in after) {
                "DELETE /collectors/$collectorId/albums/${target.albumId} no quitó ese álbum"
            }
            check(others == after) {
                "DELETE /collectors/$collectorId/albums/${target.albumId} quitó otros vínculos"
            }
        }
        val leftover = albumsOf(collectorId).filter { it.albumId in albums }
        check(leftover.isEmpty()) { "Siguieron vínculos de esta prueba en el coleccionista $collectorId" }
    }

    private fun albumCounts(): Map<Int, Int> {
        val response = JSONArray(send("GET", "collectors", null, HTTP_OK))
        return buildMap {
            repeat(response.length()) { index ->
                val collector = response.getJSONObject(index)
                put(collector.getInt("id"), collector.optJSONArray("collectorAlbums")?.length() ?: 0)
            }
        }
    }

    private fun send(
        method: String,
        path: String,
        body: JSONObject?,
        expectedCode: Int,
    ): String {
        val connection = URL("${BuildConfig.BASE_URL}$path").openConnection() as HttpURLConnection
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
