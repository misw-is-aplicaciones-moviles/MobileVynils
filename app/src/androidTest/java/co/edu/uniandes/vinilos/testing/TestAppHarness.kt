package co.edu.uniandes.vinilos.testing

import android.content.Context
import androidx.test.espresso.IdlingResource
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.core.DefaultDispatcherProvider
import co.edu.uniandes.vinilos.core.DispatcherProvider
import co.edu.uniandes.vinilos.di.AppContainer
import co.edu.uniandes.vinilos.testing.idling.AsyncWorkIdlingResource
import co.edu.uniandes.vinilos.testing.idling.WindowFocusIdlingResource
import co.edu.uniandes.vinilos.testing.network.NetworkFaults
import java.io.Closeable
import java.util.concurrent.Executors

/** Dependencias reales, nuevas por caso, con trabajo observable y fallos limitados al cliente de la app. */
internal class TestAppHarness(
    context: Context,
) : Closeable {
    private val io = AsyncWorkIdlingResource("IO")
    private val default = AsyncWorkIdlingResource("Default")
    private val room = AsyncWorkIdlingResource("Room")
    private val roomPool = Executors.newFixedThreadPool(2)
    private val windows = WindowFocusIdlingResource()
    val network = NetworkFaults()

    val container =
        AppContainer(
            context,
            dispatchers =
                object : DispatcherProvider {
                    override val io = this@TestAppHarness.io.dispatcher(DefaultDispatcherProvider.io)
                    override val default = this@TestAppHarness.default.dispatcher(DefaultDispatcherProvider.default)
                },
            roomExecutor = room.executor(roomPool),
            httpCacheEnabled = false,
            configureHttp = { it.addInterceptor(network) },
        )

    // La regla los obtiene fuera de Main, antes de abrir la Activity.
    val idlingResources: List<IdlingResource> get() =
        listOf(
            io,
            default,
            room,
            OkHttpIdlingResource("Vinilos: HTTP", container.networkClient.httpClient.dispatcher),
            windows,
        )

    override fun close() {
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { windows.close() }
        } finally {
            try {
                network.restore()
                container.close()
            } finally {
                roomPool.shutdown()
            }
        }
    }
}
