package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.net.UnknownHostException

class NetworkRequestTest {
    @Test
    fun `DNS failure produces a network error`() =
        runTest {
            val result = networkRequest { throw UnknownHostException("backend") }

            assertEquals(DataResult.Failure(DataError.Network), result)
        }

    @Test
    fun `cancellation is never converted to a data error`() =
        runTest {
            val cancellation = CancellationException("screen closed")

            try {
                networkRequest { throw cancellation }
                fail("Cancellation must propagate")
            } catch (error: CancellationException) {
                assertSame(cancellation, error)
            }
        }

    @Test
    fun `programming errors are never converted to network failures`() =
        runTest {
            val programmingError = IllegalStateException("invalid mapper")

            try {
                networkRequest { throw programmingError }
                fail("Programming errors must propagate")
            } catch (error: IllegalStateException) {
                assertSame(programmingError, error)
            }
        }
}
