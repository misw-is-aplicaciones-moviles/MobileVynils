package co.edu.uniandes.vinilos.viewmodels

import java.util.Locale

/**
 * Duration typed on the sheet keypad, stored as four digits `mmss`.
 * The valid range is 00:01–59:59. `00:00` and `60:00` match `^\d{1,2}:[0-5]\d$` and still fail:
 * the empty frame shows the format message on `00:00`, and the story rejects anything outside that range.
 */
internal object TrackDuration {
    const val EMPTY_VALUE = 0

    private const val MINUTES_PER_HUNDRED = 100
    private const val DECIMAL = 10
    private const val MAX_ENTRY = 10_000
    private const val MAX_MINUTE = 59
    private const val MAX_SECOND = 59
    private val FORMAT = Regex("^\\d{1,2}:[0-5]\\d$")

    /** New digits enter from the right. A fifth digit drops the first one. */
    fun append(
        current: Int,
        digit: Int,
    ): Int = (current * DECIMAL + digit) % MAX_ENTRY

    fun deleteLast(current: Int): Int = current / DECIMAL

    fun format(value: Int): String {
        val minutes = value / MINUTES_PER_HUNDRED
        val seconds = value % MINUTES_PER_HUNDRED
        return "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }

    /** BackVynils examples use unpadded minutes (for example, `5:55`). */
    fun formatForRequest(value: Int): String {
        val minutes = value / MINUTES_PER_HUNDRED
        val seconds = value % MINUTES_PER_HUNDRED
        return "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }

    fun isValid(value: Int): Boolean {
        val minutes = value / MINUTES_PER_HUNDRED
        val seconds = value % MINUTES_PER_HUNDRED
        return value != EMPTY_VALUE &&
            minutes <= MAX_MINUTE &&
            seconds <= MAX_SECOND &&
            FORMAT.matches(format(value))
    }
}
