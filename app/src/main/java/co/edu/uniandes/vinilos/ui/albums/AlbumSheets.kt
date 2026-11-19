package co.edu.uniandes.vinilos.ui.albums

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import co.edu.uniandes.vinilos.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Window of the album sheets ("Nueva canción" and "Comentar álbum"): the kit scrim instead of the dim, resize for
 * the keyboard, and always expanded with a transparent container so the hump of [TrackSheetLayout] shows.
 */
internal fun BottomSheetDialog.configureAlbumSheet(context: Context) {
    window?.setBackgroundDrawable(ContextCompat.getColor(context, R.color.vinilos_sheet_scrim).toDrawable())
    window?.setDimAmount(0f)
    window?.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN,
    )
    setOnShowListener { showExpanded() }
}

/** Keeps the sheet content above the navigation bar and leaves room for the shadow over the hump. */
internal fun bindAlbumSheetInsets(sheetBody: View) {
    ViewCompat.setOnApplyWindowInsetsListener(sheetBody) { sheet, insets ->
        val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val shadow = sheet.resources.getDimensionPixelSize(R.dimen.track_sheet_shadow_space)
        val bottom = sheet.resources.getDimensionPixelSize(R.dimen.space_xl)
        sheet.updatePadding(top = shadow, bottom = navigation + bottom)
        insets
    }
    ViewCompat.requestApplyInsets(sheetBody)
}

internal fun hideKeyboard(view: View) {
    val token = view.windowToken ?: return
    val manager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    manager?.hideSoftInputFromWindow(token, 0)
}

private fun BottomSheetDialog.showExpanded() {
    val sheet = findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet) ?: return
    sheet.setBackgroundColor(Color.TRANSPARENT)
    sheet.clipChildren = false
    (sheet.parent as? ViewGroup)?.clipChildren = false
    BottomSheetBehavior.from(sheet).apply {
        skipCollapsed = true
        state = BottomSheetBehavior.STATE_EXPANDED
    }
}
