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
    fun the_first_url_of_a_view_is_shown_without_waiting() =
        runTest {
            val loader = loader()

            loader.request(URL_A)

            assertEquals(listOf(URL_A), shown)
        }

    @Test
    fun typing_a_url_loads_only_the_last_value_after_the_pause() =
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
    fun a_second_url_pasted_during_the_pause_replaces_the_first() =
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
    fun clearing_the_url_shows_the_placeholder_at_once_and_drops_the_pending_load() =
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
    fun repeating_the_url_on_screen_does_not_load_it_again() =
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
    fun a_load_pending_when_the_view_is_destroyed_never_runs() =
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
