package co.edu.uniandes.vinilos.ui.common

import android.view.View
import android.widget.EditText
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * The picker searches have no visible label: the hint is their only name (T7). From API 26 the framework already
 * reports the hint after the user types; on API 21 to 25 it is dropped once there is text, so TalkBack would read
 * only "Queen, cuadro de edición". This keeps the hint in the node on every version.
 */
internal fun EditText.keepHintForAccessibility() {
    ViewCompat.setAccessibilityDelegate(
        this,
        object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.hintText = (host as EditText).hint
            }
        },
    )
}
