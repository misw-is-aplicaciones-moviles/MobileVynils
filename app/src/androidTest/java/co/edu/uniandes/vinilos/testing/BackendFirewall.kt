package co.edu.uniandes.vinilos.testing

import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.testing.network.NetworkFaults
import org.junit.rules.ExternalResource

/** Conserva la API de los casos existentes; el corte solo afecta al cliente de la app, nunca a sus fixtures. */
class BackendFirewall : ExternalResource() {
    private var active: NetworkFaults? = null

    fun block() {
        check(active == null) { "El corte de red ya está activo" }
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as VinilosApplication
        val faults =
            app.container.networkClient.httpClient.interceptors
                .filterIsInstance<NetworkFaults>()
                .singleOrNull()
        checkNotNull(faults) { "El caso debe usar VinilosE2ERule antes de cortar la red" }
        active = faults
        faults.offline()
    }

    fun restore() {
        active?.restore()
        active = null
    }

    override fun after() = restore()
}
