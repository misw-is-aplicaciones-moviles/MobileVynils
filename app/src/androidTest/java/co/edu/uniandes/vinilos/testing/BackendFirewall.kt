package co.edu.uniandes.vinilos.testing

import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.BuildConfig
import org.junit.Assert.fail
import org.junit.rules.ExternalResource
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Deja BackVynils inalcanzable para que guardar una canción falle por red, y vuelve a abrir el acceso.
 * En este emulador iptables exige root. El modo avión sí corta 10.0.2.2; al salir hay que encender
 * wifi y datos, porque solo desactivar el modo avión no devuelve la ruta.
 * Antes de cada prueba borra un proxy HTTP pegado: OkHttp lo usa y la sonda hacia 10.0.2.2 no.
 */
class BackendFirewall : ExternalResource() {
    private var blocked = false

    override fun before() {
        clearProxy()
        if (canConnect()) return
        bringNetworkBack()
        if (!waitUntil(connected = true)) fail("BackVynils no responde en ${BuildConfig.BASE_URL}")
    }

    override fun after() {
        if (blocked) restore()
    }

    fun block() {
        blocked = true
        runShell("iptables -I OUTPUT -p tcp --dport $backendPort -j REJECT")
        if (canConnect()) {
            runShell("cmd connectivity airplane-mode enable")
            runShell("svc wifi disable")
            runShell("svc data disable")
            if (!waitUntil(connected = false)) fail("No se pudo cortar el acceso a ${BuildConfig.BASE_URL}")
        }
        // El catálogo deja un socket en el pool de OkHttp (4 s). Hay que soltarlo antes de guardar.
        Thread.sleep(POOL_DRAIN_MILLIS)
    }

    fun restore() {
        bringNetworkBack()
        blocked = false
        if (!waitUntil(connected = true)) fail("BackVynils no responde en ${BuildConfig.BASE_URL}")
    }

    private fun bringNetworkBack() {
        repeat(RULE_DELETES) { runShell("iptables -D OUTPUT -p tcp --dport $backendPort -j REJECT") }
        runShell("cmd connectivity airplane-mode disable")
        runShell("svc wifi enable")
        runShell("svc data enable")
        clearProxy()
    }

    /** `:0` y el borrado sacan un proxy que `settings delete` deja vivo en ConnectivityService. */
    private fun clearProxy() {
        runShell("settings put global http_proxy :0")
        runShell("settings delete global http_proxy")
        runShell("settings delete global global_http_proxy_host")
        runShell("settings delete global global_http_proxy_port")
    }

    private fun waitUntil(connected: Boolean): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (canConnect() == connected) return true
            Thread.sleep(POLL_MILLIS)
        }
        return canConnect() == connected
    }

    private fun canConnect(): Boolean {
        val connection = URL(BuildConfig.BASE_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = PROBE_TIMEOUT_MILLIS
        connection.readTimeout = PROBE_TIMEOUT_MILLIS
        return try {
            connection.requestMethod = "GET"
            connection.responseCode > 0
        } catch (_: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun runShell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        descriptor.use { FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
    }

    private companion object {
        const val POLL_MILLIS = 300L
        const val WAIT_MILLIS = 12_000L
        const val PROBE_TIMEOUT_MILLIS = 2_000
        const val HTTP_PORT = 80
        const val RULE_DELETES = 3
        const val POOL_DRAIN_MILLIS = 5_000L
        val backendPort: Int = URL(BuildConfig.BASE_URL).port.let { port -> if (port == -1) HTTP_PORT else port }
    }
}
