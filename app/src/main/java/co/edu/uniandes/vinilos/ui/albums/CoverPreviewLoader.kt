package co.edu.uniandes.vinilos.ui.albums

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decides when the cover preview of the new album form loads (HU07). A URL is shown only after
 * [delayMillis] without another change, so typing or pasting does not start one load per character.
 * An empty URL clears the preview at once. The first URL of a view, such as one restored after a
 * rotation, is shown without waiting. [scope] is the view's lifecycle scope, so a pending load dies
 * with the view and never reaches an old binding.
 */
internal class CoverPreviewLoader(
    private val scope: CoroutineScope,
    private val delayMillis: Long = DELAY_MILLIS,
    private val show: (url: String) -> Unit,
) {
    private var pending: Job? = null
    private var requestedUrl: String? = null
    private var shownUrl: String? = null

    /** Asks for [url], already trimmed; an empty string means "no cover". */
    fun request(url: String) {
        if (url == requestedUrl) return
        requestedUrl = url
        pending?.cancel()
        pending = null
        if (url == shownUrl) return
        if (shownUrl == null || url.isEmpty()) {
            display(url)
            return
        }
        pending =
            scope.launch {
                delay(delayMillis)
                display(url)
            }
    }

    private fun display(url: String) {
        shownUrl = url
        show(url)
    }

    companion object {
        const val DELAY_MILLIS = 400L
    }
}
