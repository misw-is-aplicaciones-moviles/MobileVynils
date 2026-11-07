package co.edu.uniandes.vinilos.testing

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.base.DefaultFailureHandler
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.database.VinilosDatabase
import co.edu.uniandes.vinilos.di.AppContainer
import co.edu.uniandes.vinilos.testing.network.NetworkFaults
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Una regla por recorrido. autoLaunch conserva temporalmente los casos que aún usan el orden anterior. */
class VinilosE2ERule(
    private val autoLaunch: Boolean = false,
) : TestRule {
    private var harness: TestAppHarness? = null
    private var activity: ActivityScenario<MainActivity>? = null

    val scenario: ActivityScenario<MainActivity>
        get() = checkNotNull(activity) { "Llama a launch() antes de usar la Activity" }
    val network: NetworkFaults get() = checkNotNull(harness).network

    fun launch(): ActivityScenario<MainActivity> {
        check(harness != null) { "La regla E2E no está preparada" }
        check(activity == null) { "La Activity ya está abierta" }
        return ActivityScenario.launch(MainActivity::class.java).also { activity = it }
    }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() = evaluateCase(base)
        }

    private fun evaluateCase(base: Statement) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as VinilosApplication
        val registry = IdlingRegistry.getInstance()
        var resources = emptyArray<IdlingResource>()
        var failure: Throwable? = null
        try {
            app.container.close()
            if (app.getDatabasePath(VinilosDatabase.NAME).exists()) {
                check(app.deleteDatabase(VinilosDatabase.NAME)) { "No se pudo limpiar Room de pruebas" }
            }
            val fresh = TestAppHarness(app)
            harness = fresh
            instrumentation.runOnMainSync { app.container = fresh.container }
            resources = fresh.idlingResources.toTypedArray()
            registry.register(*resources)
            val capture = Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP
            Espresso.setFailureHandler(DefaultFailureHandler(app, capture))
            if (autoLaunch) launch()
            base.evaluate()
        } catch (error: Throwable) {
            failure = error
        } finally {
            // Intentar cada cierre, conservando el fallo original y cualquier fallo de limpieza.
            val cleanup =
                listOf<() -> Unit>(
                    { activity?.close() },
                    { registry.unregister(*resources) },
                    { harness?.close() },
                    { instrumentation.runOnMainSync { app.container = AppContainer(app) } },
                    { Espresso.setFailureHandler(DefaultFailureHandler(app)) },
                )
            failure = closeAll(cleanup, failure)
            activity = null
            harness = null
        }
        failure?.let { throw it }
    }

    private fun closeAll(
        actions: List<() -> Unit>,
        original: Throwable?,
    ): Throwable? {
        var failure = original
        for (close in actions) {
            try {
                close()
            } catch (error: Throwable) {
                failure = combine(failure, error)
            }
        }
        return failure
    }

    private fun combine(
        original: Throwable?,
        cleanup: Throwable,
    ): Throwable = original?.also { it.addSuppressed(cleanup) } ?: cleanup
}
