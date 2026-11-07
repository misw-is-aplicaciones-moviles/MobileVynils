package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.network.BackendEndpoint
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

/** Un coleccionista de `GET /collectors`, solo con lo necesario para abrirlo. */
data class ListedCollector(
    val id: Int,
    val name: String,
    val albumCount: Int,
    val favoriteCount: Int,
)

/** Detalle leído de las dos rutas, sin pasar por la app. */
data class CollectorSnapshot(
    val id: Int,
    val name: String,
    val telephone: String,
    val email: String,
    val favorites: List<FavoriteSnapshot>,
    val albums: List<AlbumSnapshot>,
    val commentDescriptions: List<String>,
)

/** Un favorito del detalle. El tipo sale de una sola fecha, como lo interpreta la pantalla. */
data class FavoriteSnapshot(
    val name: String,
    val kind: FavoriteKind,
)

enum class FavoriteKind { MUSICIAN, BAND }

/** Un álbum de `GET /collectors/:id/albums`. `status` queda en el valor del backend: `Active` o `Inactive`. */
data class AlbumSnapshot(
    val name: String,
    val price: Int,
    val status: String,
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
    fun listedCollectors(): List<ListedCollector> {
        val response = JSONArray(send("GET", "collectors", null, HTTP_OK))
        return List(response.length()) { index ->
            val collector = response.getJSONObject(index)
            ListedCollector(
                id = collector.getInt("id"),
                name = collector.getString("name"),
                albumCount = collector.optJSONArray("collectorAlbums")?.length() ?: 0,
                favoriteCount = collector.optJSONArray("favoritePerformers")?.length() ?: 0,
            )
        }
    }

    /**
     * Un id que `GET /collectors` no devuelve. Sirve para abrir el detalle de alguien que no existe
     * sin crear ni borrar coleccionistas.
     */
    fun unusedCollectorId(): Int {
        val ids = listedCollectors().map { it.id }.toSet()
        var candidate = (ids.maxOrNull() ?: 0) + 1
        while (candidate in ids) candidate++
        return candidate
    }

    /** Junta `GET /collectors/:id` y `GET /collectors/:id/albums` tal como están ahora. */
    fun readDetail(id: Int): CollectorSnapshot {
        val collector = JSONObject(send("GET", "collectors/$id", null, HTTP_OK))
        val albums = JSONArray(send("GET", "collectors/$id/albums", null, HTTP_OK))
        return CollectorSnapshot(
            id = collector.getInt("id"),
            name = collector.getString("name"),
            telephone = collector.getString("telephone"),
            email = collector.getString("email"),
            favorites = favoritesOf(collector.optJSONArray("favoritePerformers")),
            albums = albumsOf(albums),
            commentDescriptions = commentsOf(collector.optJSONArray("comments")),
        )
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

    private fun favoritesOf(array: JSONArray?): List<FavoriteSnapshot> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: error("Favorito nulo en la posición $index")
            FavoriteSnapshot(name = item.getString("name"), kind = favoriteKind(item))
        }
    }

    private fun favoriteKind(item: JSONObject): FavoriteKind {
        val musician = !optText(item, "birthDate").isNullOrBlank()
        val band = !optText(item, "creationDate").isNullOrBlank()
        check(musician != band) { "El favorito \"${item.optString("name")}\" no tiene un solo tipo" }
        return if (musician) FavoriteKind.MUSICIAN else FavoriteKind.BAND
    }

    private fun albumsOf(array: JSONArray): List<AlbumSnapshot> =
        List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: error("Vínculo de álbum nulo en la posición $index")
            val album = item.optJSONObject("album") ?: error("Vínculo sin álbum anidado")
            AlbumSnapshot(
                name = album.getString("name"),
                price = item.getInt("price"),
                status = item.getString("status"),
            )
        }

    private fun commentsOf(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
            array.optJSONObject(index)?.optString("description").orEmpty()
        }.filter { it.isNotBlank() }
    }

    private fun optText(
        json: JSONObject,
        key: String,
    ): String? = if (!json.has(key) || json.isNull(key)) null else json.getString(key)

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
            assertEquals("$method /$path", expectedCode, connection.responseCode)
            if (expectedCode == HTTP_NO_CONTENT) return ""
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
