package co.edu.uniandes.vinilos.testing

import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.BuildConfig
import co.edu.uniandes.vinilos.VinilosApplication
import org.junit.rules.ExternalResource
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI

/** Corta el transporte HTTP del backend durante la prueba, sin modificar la red del dispositivo. */
class BackendFirewall : ExternalResource() {
    private var server: ServerSocket? = null
    private var worker: Thread? = null

    override fun after() = restore()

    fun block() {
        check(server == null) { "El corte de red ya está activo" }
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server = socket
        worker =
            Thread {
                try {
                    while (!socket.isClosed) socket.accept().use { /* Cierre sin respuesta HTTP. */ }
                } catch (error: IOException) {
                    if (!socket.isClosed) throw error
                }
            }.apply {
                isDaemon = true
                start()
            }
        BackendProxySelector.blockedProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", socket.localPort))
        // Una petición podría reutilizar un socket directo sin consultar de nuevo al ProxySelector.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as VinilosApplication
        application.container.networkClient.httpClient.connectionPool
            .evictAll()
    }

    fun restore() {
        BackendProxySelector.blockedProxy = null
        server?.close()
        worker?.join()
        server = null
        worker = null
    }
}

/** Se instala antes de construir OkHttp y delega todas las peticiones salvo durante el corte. */
internal object BackendProxySelector : ProxySelector() {
    private val backend = URI(BuildConfig.BASE_URL)
    private var original: ProxySelector? = null

    @Volatile var blockedProxy: Proxy? = null

    fun install() {
        original = getDefault()
        setDefault(this)
    }

    fun uninstall() {
        blockedProxy = null
        setDefault(original)
    }

    override fun select(uri: URI): List<Proxy> {
        val blocked = blockedProxy
        val isBackend = uri.scheme == backend.scheme && uri.authority == backend.authority
        return if (blocked != null && isBackend) {
            listOf(blocked)
        } else {
            original?.select(uri) ?: listOf(Proxy.NO_PROXY)
        }
    }

    override fun connectFailed(
        uri: URI,
        address: SocketAddress,
        error: IOException,
    ) {
        if (blockedProxy == null) original?.connectFailed(uri, address, error)
    }
}
