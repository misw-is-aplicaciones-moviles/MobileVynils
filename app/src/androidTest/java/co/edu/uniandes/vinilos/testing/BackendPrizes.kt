package co.edu.uniandes.vinilos.testing

import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.BuildConfig
import co.edu.uniandes.vinilos.models.PerformerType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/** Datos propios para probar HU14 sin modificar premios ni artistas de la semilla. */
class BackendPrizes(
    private val type: PerformerType,
) {
    private val collection = if (type == PerformerType.MUSICIAN) "musicians" else "bands"
    private val runId = InstrumentationRegistry.getArguments().getString("hu14FixtureRun").orEmpty()
    val name = "HU14 E2E $runId ${UUID.randomUUID().toString().take(8)}"
    var prizeId = 0
        private set
    var performerId = 0
        private set

    fun prepare() {
        check(runId.matches(Regex("[0-9]+-[0-9]+"))) {
            "Usa bash scripts/run_e2e.sh para preparar y limpiar las asignaciones de HU14 de forma segura"
        }
        val host = URI(BuildConfig.BASE_URL).host
        check(host in setOf("127.0.0.1", "localhost", "10.0.2.2")) { "HU14 requiere un backend local de pruebas" }
        prizeId =
            request(
                "POST",
                "prizes",
                JSONObject()
                    .put(
                        "name",
                        name,
                    ).put("organization", "Equipo de pruebas HU14")
                    .put("description", "Dato HU14"),
            ).getInt("id")
        val dateKey = if (type == PerformerType.MUSICIAN) "birthDate" else "creationDate"
        performerId =
            request(
                "POST",
                collection,
                JSONObject()
                    .put("name", name)
                    .put("description", "Artista de prueba HU14")
                    .put("image", "https://example.com/hu14.png")
                    .put(dateKey, "2000-01-01T00:00:00.000Z"),
            ).getInt("id")
    }

    fun assign(): Int =
        request(
            "POST",
            "prizes/$prizeId/$collection/$performerId",
            JSONObject().put("premiationDate", "2026-01-15T00:00:00.000Z"),
        ).getInt("id")

    fun artist(): JSONObject = request("GET", "$collection/$performerId")

    /** Todos los premios del backend ahora: HU13 comprueba con esto que una pantalla escribió una vez o ninguna. */
    fun prizes(): List<JSONObject> {
        val all = JSONArray(send("GET", "prizes"))
        return List(all.length()) { all.getJSONObject(it) }
    }

    /**
     * Nombre de un premio que HU13 crea desde la pantalla. Lleva la marca de la ejecución y, escritos con la
     * descripción y la organización de los fixtures, `limpiar_premios_e2e.sql` lo reconoce y lo elimina.
     */
    fun uiPrizeName(suffix: String) = "$name $suffix"

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
    ): JSONObject {
        val text = send(method, path, body)
        return if (method == "DELETE") JSONObject() else JSONObject(text)
    }

    private fun send(
        method: String,
        path: String,
        body: JSONObject? = null,
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
            assertEquals("$method $path", if (method == "DELETE") HTTP_NO_CONTENT else HTTP_OK, connection.responseCode)
            return if (method == "DELETE") "" else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val TIMEOUT_MILLIS = 10_000
    }
}
