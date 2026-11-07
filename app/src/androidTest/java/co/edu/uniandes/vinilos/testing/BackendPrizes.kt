package co.edu.uniandes.vinilos.testing

import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.network.BackendEndpoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/**
 * Fixtures propios de HU13/HU14. Conserva las comprobaciones de propiedad de Jhon.
 * El backend aislado se descarta al terminar: no se usa su DELETE de vínculos defectuoso.
 */
class BackendPrizes : ExternalResource() {
    private val mark = "HU14 E2E ${UUID.randomUUID()}"
    private val opened = mutableListOf<Created>()
    private var prizesAtStart = emptySet<Int>()
    private var musiciansAtStart = emptySet<Int>()
    private var bandsAtStart = emptySet<Int>()
    private var linksAtStart = emptySet<PrizeLink>()

    override fun before() {
        val host = URI(BackendEndpoint.baseUrl).host
        check(host in LOCAL_HOSTS) { "HU14 requiere un backend local de pruebas" }
        prizesAtStart = ids("prizes")
        musiciansAtStart = ids("musicians")
        bandsAtStart = ids("bands")
        linksAtStart = allLinks().toSet()
    }

    /** Crea un premio y un artista con el mismo nombre. La pantalla reutiliza ese nombre como prefijo. */
    fun open(type: PerformerType): Fixture {
        val name = "$mark ${UUID.randomUUID().toString().take(SHORT_ID_LENGTH)}"
        val prizeId = request("POST", "prizes", prizeBody(name)).getInt("id")
        val created = Created(prizeId)
        opened += created
        val dateKey = if (type == PerformerType.MUSICIAN) "birthDate" else "creationDate"
        created.performerId = request("POST", collection(type), performerBody(name, dateKey)).getInt("id")
        return Fixture(name, prizeId, checkNotNull(created.performerId), type)
    }

    override fun after() {
        val links = allLinks().toSet()
        assertOwnedLinks(ownedPrizeIds(), ownedPerformerIds(), links)
        check(links.containsAll(linksAtStart)) { "La prueba modificó asignaciones que ya existían" }
        assertPreserved()
        // Los registros propios permanecen hasta el descarte de la base exclusiva de esta ejecución.
    }

    /** Premio y artista creados por [open]. */
    inner class Fixture(
        val name: String,
        val prizeId: Int,
        val performerId: Int,
        private val type: PerformerType,
    ) {
        fun assign(): Int =
            request(
                "POST",
                "prizes/$prizeId/${collection(type)}/$performerId",
                JSONObject().put("premiationDate", "2026-01-15T00:00:00.000Z"),
            ).getInt("id")

        fun artist(): JSONObject = request("GET", "${collection(type)}/$performerId")

        /** Todos los premios del backend ahora: HU13 comprueba con esto que una pantalla escribió una vez o ninguna. */
        fun prizes(): List<JSONObject> = catalog()

        /** Nombre de un premio que la pantalla crea. La limpieza lo reconoce por la marca, la descripción y la organización. */
        fun uiPrizeName(suffix: String) = "$name $suffix"
    }

    private fun assertOwnedLinks(
        prizeIds: Set<Int>,
        performerIds: Set<Int>,
        links: Set<PrizeLink>,
    ) {
        val foreign =
            links.filter { link ->
                val prizeOwned = link.prizeId in prizeIds
                val performerOwned = link.performerId in performerIds
                prizeOwned != performerOwned
            }
        check(foreign.isEmpty()) {
            "Los fixtures HU14 tienen un vínculo con datos ajenos; no se elimina ningún dato"
        }
    }

    private fun ownedPrizeIds(): Set<Int> {
        val posted = opened.map { it.prizeId }.toSet()
        val writtenByTheScreen =
            catalog()
                .filter { prize ->
                    prize.optString("name").startsWith(mark) &&
                        prize.optString("description") == PRIZE_DESCRIPTION &&
                        prize.optString("organization") == PRIZE_ORGANIZATION
                }.map { it.getInt("id") }
        return posted + writtenByTheScreen
    }

    private fun ownedPerformerIds(): Set<Int> = opened.mapNotNull { it.performerId }.toSet()

    private fun allLinks(): List<PrizeLink> = ids("prizes").flatMap(::linksOf)

    private fun linksOf(prizeId: Int): List<PrizeLink> {
        val response = JSONArray(send("GET", "prizes/$prizeId/performers", null, HTTP_OK))
        return List(response.length()) { index ->
            val item = response.optJSONObject(index) ?: error("Vínculo nulo en la posición $index")
            val performer = item.optJSONObject("performer") ?: error("Vínculo sin artista")
            PrizeLink(
                prizeId = item.getJSONObject("prize").getInt("id"),
                performerId = performer.getInt("id"),
            )
        }
    }

    private fun assertPreserved() {
        check(prizesAtStart.all { it in ids("prizes") }) { "La prueba eliminó un premio que ya existía" }
        check(musiciansAtStart.all { it in ids("musicians") }) { "La prueba eliminó un músico que ya existía" }
        check(bandsAtStart.all { it in ids("bands") }) { "La prueba eliminó una banda que ya existía" }
    }

    private fun catalog(): List<JSONObject> {
        val response = JSONArray(send("GET", "prizes", null, HTTP_OK))
        return List(response.length()) { response.getJSONObject(it) }
    }

    private fun ids(path: String): Set<Int> {
        val response = JSONArray(send("GET", path, null, HTTP_OK))
        return List(response.length()) { response.getJSONObject(it).getInt("id") }.toSet()
    }

    private fun prizeBody(name: String): JSONObject =
        JSONObject()
            .put("name", name)
            .put("organization", PRIZE_ORGANIZATION)
            .put("description", PRIZE_DESCRIPTION)

    private fun performerBody(
        name: String,
        dateKey: String,
    ): JSONObject =
        JSONObject()
            .put("name", name)
            .put("description", ARTIST_DESCRIPTION)
            .put("image", "https://example.com/hu14.png")
            .put(dateKey, "2000-01-01T00:00:00.000Z")

    private fun collection(type: PerformerType): String = if (type == PerformerType.MUSICIAN) "musicians" else "bands"

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
    ): JSONObject = JSONObject(send(method, path, body, HTTP_OK))

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
            val payload = if (connection.responseCode >= HTTP_ERROR) connection.errorStream else connection.inputStream
            val text = payload?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            assertEquals("$method /$path $text", expectedCode, connection.responseCode)
            return text
        } finally {
            connection.disconnect()
        }
    }

    private class Created(
        val prizeId: Int,
        var performerId: Int? = null,
    )

    private data class PrizeLink(
        val prizeId: Int,
        val performerId: Int,
    )

    private companion object {
        const val PRIZE_DESCRIPTION = "Dato HU14"
        const val PRIZE_ORGANIZATION = "Equipo de pruebas HU14"
        const val ARTIST_DESCRIPTION = "Artista de prueba HU14"
        const val SHORT_ID_LENGTH = 8
        const val HTTP_OK = 200
        const val HTTP_ERROR = 400
        const val TIMEOUT_MILLIS = 10_000
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
    }
}
