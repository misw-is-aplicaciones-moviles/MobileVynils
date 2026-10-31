package co.edu.uniandes.vinilos.testing

import androidx.test.espresso.IdlingRegistry
import okhttp3.OkHttpClient
import org.junit.rules.ExternalResource

/**
 * Registra un [OkHttpIdlingResource] para el [client] de la app durante cada prueba E2E y lo retira al terminar.
 * Uso: `@get:Rule val okHttpIdlingRule = OkHttpIdlingResourceRule(container.okHttpClient)`.
 */
class OkHttpIdlingResourceRule(
    client: OkHttpClient,
) : ExternalResource() {
    private val idlingResource = OkHttpIdlingResource(name = "okhttp", dispatcher = client.dispatcher)

    override fun before() {
        IdlingRegistry.getInstance().register(idlingResource)
    }

    override fun after() {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }
}
