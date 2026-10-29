package co.edu.uniandes.vinilos.ui.common

import android.view.View
import androidx.annotation.StringRes

/**
 * Lets a screen put its main action ("+") in the bottom bar of the activity that hosts it.
 * The screen decides when it shows; the host knows nothing about roles.
 */
interface PrimaryActionHost {
    /** View a Snackbar should sit above: the button while it shows, the bar otherwise. */
    val snackbarAnchor: View

    /** Shows the round button with the hump in the bar. [contentDescription] names the action for TalkBack. */
    fun showPrimaryAction(
        @StringRes contentDescription: Int,
        onClick: () -> Unit,
    )

    /** Hides the button and leaves the bar flat. */
    fun hidePrimaryAction()
}
