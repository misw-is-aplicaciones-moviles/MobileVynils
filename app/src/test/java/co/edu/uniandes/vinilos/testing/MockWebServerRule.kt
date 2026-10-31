package co.edu.uniandes.vinilos.testing

import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.ExternalResource
import java.net.HttpURLConnection.HTTP_OK

/**
 * Servidor HTTP local para probar los Service Adapters sin BackVynils.
 * Retrofit se construye con [baseUrl]; las respuestas se encolan con [enqueueJson] desde `src/test/resources`.
 */
class MockWebServerRule : ExternalResource() {
    val server = MockWebServer()

    val baseUrl: HttpUrl
        get() = server.url("/")

    override fun before() {
        server.start()
    }

    override fun after() {
        server.shutdown()
    }

    /** Encola el contenido de [fixture] (ruta dentro de `src/test/resources`) como respuesta JSON. */
    fun enqueueJson(
        fixture: String,
        code: Int = HTTP_OK,
    ) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(readResource(fixture)),
        )
    }
}
