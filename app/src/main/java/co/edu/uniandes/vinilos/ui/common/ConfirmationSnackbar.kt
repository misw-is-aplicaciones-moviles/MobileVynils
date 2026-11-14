package co.edu.uniandes.vinilos.ui.common

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import co.edu.uniandes.vinilos.R
import com.google.android.material.snackbar.Snackbar

/**
 * Success notice of the prize mockups (HU13 and HU14): a charcoal pill with a check mark, over [anchor].
 * Other notices keep the default Snackbar.
 */
internal fun showConfirmationSnackbar(
    view: View,
    anchor: View?,
    @StringRes message: Int,
    duration: Int,
) {
    val context = ContextThemeWrapper(view.context, R.style.ThemeOverlay_Vinilos_ConfirmationSnackbar)
    val snackbar = Snackbar.make(context, view, view.context.getString(message), duration).setAnchorView(anchor)
    val resources = view.resources
    snackbar.view.layoutParams =
        (snackbar.view.layoutParams as ViewGroup.MarginLayoutParams).apply {
            val side = resources.getDimensionPixelSize(R.dimen.confirmation_snackbar_margin)
            setMargins(side, 0, side, resources.getDimensionPixelSize(R.dimen.confirmation_snackbar_gap))
        }
    snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
        compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.confirmation_snackbar_icon_gap)
    }
    snackbar.show()
}
