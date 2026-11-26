package co.edu.uniandes.vinilos.ui.collectors

import android.content.Context
import co.edu.uniandes.vinilos.R

/**
 * State that TalkBack reads after a picker row, as in the HU15 picker. A row that is already in the collection or
 * among the favorites says so in its description and has no selectable state.
 */
internal fun pickerStateDescription(
    context: Context,
    alreadyAdded: Boolean,
    selected: Boolean,
): CharSequence? =
    when {
        alreadyAdded -> null
        selected -> context.getString(R.string.picker_selected)
        else -> context.getString(R.string.picker_available)
    }
