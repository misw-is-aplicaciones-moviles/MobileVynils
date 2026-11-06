package co.edu.uniandes.vinilos.ui.common

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import co.edu.uniandes.vinilos.R
import com.google.android.material.textfield.TextInputLayout

private const val ICON_PLACEHOLDER = " "
private const val ICON_GAP = "  "

/** Text of a field error as the mockups draw it: the alert icon, then [message]. Shared by every form. */
internal fun Context.fieldErrorText(
    @StringRes message: Int,
): CharSequence {
    val icon = checkNotNull(AppCompatResources.getDrawable(this, R.drawable.ic_warning))
    val size = resources.getDimensionPixelSize(R.dimen.field_error_icon)
    icon.setBounds(0, 0, size, size)
    return SpannableStringBuilder()
        .append(ICON_PLACEHOLDER, ImageSpan(icon, ImageSpan.ALIGN_BASELINE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        .append(ICON_GAP)
        .append(getString(message))
}

/** Shows [message] under the field; with null it frees the space the error reserves. */
internal fun TextInputLayout.showFieldError(
    @StringRes message: Int?,
) {
    if (message == null) {
        isErrorEnabled = false
    } else {
        error = context.fieldErrorText(message)
    }
}
