package co.edu.uniandes.vinilos.testing

import androidx.test.espresso.IdlingResource
import okhttp3.Dispatcher

/**
 * Espresso espera a que no haya llamadas de red en curso en el [Dispatcher] de OkHttp antes de cada acción.
 * Vive en androidTest: la app no necesita `espresso-idling-resource`. Se registra con [OkHttpIdlingResourceRule].
 */
class OkHttpIdlingResource(
    private val name: String,
    private val dispatcher: Dispatcher,
) : IdlingResource {
    override fun getName(): String = name

    override fun isIdleNow(): Boolean = dispatcher.runningCallsCount() == 0

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        // OkHttp llama a idleCallback cuando termina la última llamada en curso.
        dispatcher.idleCallback = Runnable { callback.onTransitionToIdle() }
    }
}
