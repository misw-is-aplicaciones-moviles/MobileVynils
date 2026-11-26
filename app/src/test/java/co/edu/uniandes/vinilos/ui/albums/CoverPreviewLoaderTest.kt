package co.edu.uniandes.vinilos.ui.albums

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoverPreviewLoaderTest {
    private val shown = mutableListOf<String>()

    private fun TestScope.loader() = CoverPreviewLoader(backgroundScope, DELAY) { shown += it }

    @Test
    fun `the first url of a view is shown without waiting`() =
        runTest {
            val loader = loader()

            loader.request(URL_A)

            assertEquals(listOf(URL_A), shown)
        }

    @Test
    fun `typing a url loads only the last value after the pause`() =
        runTest {
            val loader = loader()
            loader.request("")

            listOf("h", "ht", "htt", URL_A).forEach {
                loader.request(it)
                advanceTimeBy(DELAY / 2)
            }
            assertEquals(listOf(""), shown)

            waitThePause()

            assertEquals(listOf("", URL_A), shown)
        }

    @Test
    fun `a second url pasted during the pause replaces the first`() =
        runTest {
            val loader = loader()
            loader.request("")

            loader.request(URL_A)
            advanceTimeBy(DELAY - 1)
            loader.request(URL_B)
            waitThePause()

            assertEquals(listOf("", URL_B), shown)
        }

    @Test
    fun `clearing the url shows the placeholder at once and drops the pending load`() =
        runTest {
            val loader = loader()
            loader.request(URL_A)
            loader.request(URL_B)
            advanceTimeBy(DELAY / 2)

            loader.request("")
            assertEquals(listOf(URL_A, ""), shown)

            waitThePause()
            assertEquals(listOf(URL_A, ""), shown)
        }

    @Test
    fun `repeating the url on screen does not load it again`() =
        runTest {
            val loader = loader()
            loader.request(URL_A)

            loader.request(URL_A)
            loader.request(URL_A + "x")
            loader.request(URL_A)
            waitThePause()

            assertEquals(listOf(URL_A), shown)
        }

    @Test
    fun `a load pending when the view is destroyed never runs`() =
        runTest {
            val viewScope = TestScope(testScheduler)
            val loader = CoverPreviewLoader(viewScope, DELAY) { shown += it }
            loader.request("")
            loader.request(URL_A)

            viewScope.cancel()
            advanceUntilIdle()

            assertEquals(listOf(""), shown)
        }

    // advanceUntilIdle ignores backgroundScope, so the pause is advanced explicitly.
    private fun TestScope.waitThePause() {
        advanceTimeBy(DELAY)
        runCurrent()
    }

    private companion object {
        const val DELAY = 400L
        const val URL_A = "https://example.com/a.jpg"
        const val URL_B = "https://example.com/b.jpg"
    }
}
