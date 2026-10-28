package co.edu.uniandes.vinilos.testing

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val DEFAULT_TIMEOUT_SECONDS = 2L

/**
 * Espera el primer valor que publique el [LiveData] y lo devuelve; falla con [TimeoutException] si no llega.
 * Requiere `InstantTaskExecutorRule` en la prueba para que `observeForever` funcione fuera del hilo principal.
 * [afterObserve] se ejecuta ya suscrito, útil para disparar la acción que publica el valor.
 */
fun <T> LiveData<T>.getOrAwaitValue(
    timeout: Long = DEFAULT_TIMEOUT_SECONDS,
    unit: TimeUnit = TimeUnit.SECONDS,
    afterObserve: () -> Unit = {},
): T {
    val values = mutableListOf<T>()
    val latch = CountDownLatch(1)
    val observer =
        object : Observer<T> {
            override fun onChanged(value: T) {
                values += value
                latch.countDown()
                this@getOrAwaitValue.removeObserver(this)
            }
        }
    observeForever(observer)
    try {
        afterObserve()
        if (!latch.await(timeout, unit)) {
            throw TimeoutException("LiveData value was never set")
        }
    } finally {
        removeObserver(observer)
    }
    return values.first()
}
