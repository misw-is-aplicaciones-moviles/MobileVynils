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

/** Un coleccionista tal como lo responde el backend en este momento. */
data class BackendCollector(
    val name: String,
    val email: String,
    val albumCount: Int,
    val favoriteCount: Int,
)

/**
 * Datos de una prueba E2E del listado de coleccionistas: los crea en BackVynils y, al terminar la prueba, los borra.
 * Cada prueba usa nombres únicos, así que no depende de quién más esté en la lista.
 *
 * Solo crea coleccionistas sin álbumes a propósito: `DELETE /collectors/:id/albums/:albumId` no borra el vínculo pedido
 * sino el de id más bajo, que puede ser de la semilla, y sin esa ruta un coleccionista con álbumes no se puede borrar.
 * Los errores de preparación y de limpieza fallan la prueba: no se reintentan ni se ignoran.
 */
class BackendCollectors : ExternalResource() {
    private val collectors = mutableListOf<Int>()

    /** Crea un coleccionista sin álbumes ni favoritos. Devuelve su id. */
    fun createCollector(
        name: String,
        email: String,
    ): Int {
        val body =
            JSONObject()
                .put("name", name)
                .put("telephone", "3000000000")
                .put("email", email)
        return JSONObject(send("POST", "collectors", body, HTTP_OK)).getInt("id").also { collectors += it }
    }

    /** Lee los coleccionistas que tiene ahora el backend, sean quienes sean, sin escribir nada. */
    fun allCollectors(): List<BackendCollector> {
        val response = JSONArray(send("GET", "collectors", null, HTTP_OK))
        return List(response.length()) { index ->
            val collector = response.getJSONObject(index)
            BackendCollector(
                name = collector.getString("name"),
                email = collector.getString("email"),
                albumCount = collector.getJSONArray("collectorAlbums").length(),
                favoriteCount = collector.getJSONArray("favoritePerformers").length(),
            )
        }
    }

    override fun after() {
        collectors.forEach { send("DELETE", "collectors/$it", null, HTTP_NO_CONTENT) }
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
