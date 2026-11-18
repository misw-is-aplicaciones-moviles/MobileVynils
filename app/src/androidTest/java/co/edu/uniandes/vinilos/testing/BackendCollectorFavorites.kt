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

/**
 * Coleccionista, músico y banda de una ejecución de HU10. Los crea con POST y, al terminar, borra primero los
 * artistas y después el coleccionista. La tabla de favoritos hace CASCADE al borrar el artista y NO ACTION al
 * borrar el coleccionista, así que el orden inverso deja el coleccionista sin poder borrarse.
 *
 * No llama a DELETE de un favorito: esa ruta responde 204 y no quita el vínculo. Al empezar anota cuántos
 * favoritos tenía cada coleccionista que ya existía y, al final, falla si ese número bajó.
 */
class BackendCollectorFavorites : ExternalResource() {
    private val collectors = mutableListOf<Int>()
    private val musicians = mutableListOf<Int>()
    private val bands = mutableListOf<Int>()
    private var favoriteCountsAtStart: Map<Int, Int> = emptyMap()

    override fun before() {
        favoriteCountsAtStart = favoriteCounts()
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

    /** Crea un músico con [birthDate] en UTC. Devuelve su id. */
    fun createMusician(
        name: String,
        birthDate: String,
    ): Int {
        val body = performerBody(name).put("birthDate", birthDate)
        return JSONObject(send("POST", "musicians", body, HTTP_OK)).getInt("id").also { musicians += it }
    }

    /** Crea una banda con [creationDate] en UTC. Devuelve su id. */
    fun createBand(
        name: String,
        creationDate: String,
    ): Int {
        val body = performerBody(name).put("creationDate", creationDate)
        return JSONObject(send("POST", "bands", body, HTTP_OK)).getInt("id").also { bands += it }
    }

    /** Asocia un músico de esta regla a un coleccionista de esta regla. El primer POST responde 200. */
    fun linkMusician(
        collectorId: Int,
        musicianId: Int,
    ) {
        check(collectorId in collectors) { "El coleccionista $collectorId no lo creó esta prueba" }
        check(musicianId in musicians) { "El músico $musicianId no lo creó esta prueba" }
        send("POST", "collectors/$collectorId/musicians/$musicianId", null, HTTP_OK)
    }

    /** Ids de `favoritePerformers` en `GET /collectors/:id`, en el orden de la respuesta. */
    fun favoriteIds(collectorId: Int): List<Int> {
        val collector = JSONObject(send("GET", "collectors/$collectorId", null, HTTP_OK))
        val favorites = collector.optJSONArray("favoritePerformers") ?: return emptyList()
        return List(favorites.length()) { index ->
            favorites.optJSONObject(index)?.getInt("id") ?: error("Favorito nulo en la posición $index")
        }
    }

    override fun after() {
        musicians.forEach { send("DELETE", "musicians/$it", null, HTTP_NO_CONTENT) }
        bands.forEach { send("DELETE", "bands/$it", null, HTTP_NO_CONTENT) }
        collectors.forEach { send("DELETE", "collectors/$it", null, HTTP_NO_CONTENT) }
        val now = favoriteCounts()
        favoriteCountsAtStart.forEach { (id, count) ->
            assertEquals("El coleccionista $id perdió favoritos durante la limpieza", count, now[id] ?: 0)
        }
    }

    private fun performerBody(name: String) =
        JSONObject()
            .put("name", name)
            .put("image", "https://example.com/e2e.jpg")
            .put("description", "Artista creado por una prueba E2E.")

    private fun favoriteCounts(): Map<Int, Int> {
        val response = JSONArray(send("GET", "collectors", null, HTTP_OK))
        return buildMap {
            repeat(response.length()) { index ->
                val collector = response.getJSONObject(index)
                put(collector.getInt("id"), collector.optJSONArray("favoritePerformers")?.length() ?: 0)
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
            assertEquals("$method /$path", expectedCode, connection.responseCode)
            if (expectedCode == HTTP_NO_CONTENT) return ""
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
