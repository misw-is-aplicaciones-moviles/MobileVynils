package co.edu.uniandes.vinilos.testing.idling

import androidx.test.espresso.IdlingResource
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/** Cuenta tareas en cola y activas; una excepción o rechazo libera el contador una sola vez. */
internal class AsyncWorkIdlingResource(
    private val label: String,
) : IdlingResource {
    private val pending = AtomicInteger()

    @Volatile private var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String = "Vinilos: $label"

    override fun isIdleNow(): Boolean = pending.get() == 0

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }

    fun executor(delegate: Executor): Executor = Executor { task -> submit(delegate::execute, task) }

    fun dispatcher(delegate: CoroutineDispatcher): CoroutineDispatcher =
        object : CoroutineDispatcher() {
            override fun dispatch(
                context: CoroutineContext,
                block: Runnable,
            ) {
                submit({ task -> delegate.dispatch(context, task) }, block)
            }
        }

    private fun submit(
        dispatch: (Runnable) -> Unit,
        task: Runnable,
    ) {
        pending.incrementAndGet()
        val completed = AtomicBoolean()
        val finish = {
            if (completed.compareAndSet(false, true)) {
                check(pending.decrementAndGet() >= 0) { "Contador de tareas desbalanceado" }
                if (isIdleNow()) callback?.onTransitionToIdle()
            }
        }
        try {
            dispatch {
                try {
                    task.run()
                } finally {
                    finish()
                }
            }
        } catch (error: Throwable) {
            finish()
            throw error
        }
    }
}
