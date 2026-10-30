package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.net.HttpURLConnection
import java.net.URL

private const val HTTP_OK = 200
private const val TIMEOUT_MILLIS = 10_000

/**
 * Crea un álbum en BackVynils local con `POST /albums`, como dato de una prueba E2E. El backend no asocia intérpretes
 * al crear, así que el álbum queda sin artista. Cada prueba usa un nombre único para poder repetirse.
 */
fun createAlbumOnBackend(name: String) {
    val body =
        JSONObject()
            .put("name", name)
            .put("cover", "https://example.com/e2e.jpg")
            .put("releaseDate", "1980-06-30T00:00:00.000Z")
            .put("description", "Álbum creado por una prueba E2E.")
            .put("genre", "Rock")
            .put("recordLabel", "EMI")
            .toString()
    val connection = URL("${BuildConfig.BASE_URL}albums").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        assertEquals("POST /albums para preparar \"$name\"", HTTP_OK, connection.responseCode)
    } finally {
        connection.disconnect()
    }
}
