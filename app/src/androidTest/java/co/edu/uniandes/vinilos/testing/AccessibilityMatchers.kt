package co.edu.uniandes.vinilos.testing

import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/**
 * Pista que recibe TalkBack, leída del mismo nodo que él consulta. En API 21 a 25 la biblioteca compat la guarda en los
 * extras del nodo, así que la comprobación es igual en los dos emuladores.
 */
fun hasAccessibilityHint(
    @StringRes hint: Int,
): Matcher<View> =
    object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("has accessibility hint text with resource id $hint")
        }

        override fun matchesSafely(view: View): Boolean {
            val info = AccessibilityNodeInfoCompat.wrap(view.createAccessibilityNodeInfo())
            return info.hintText?.toString() == view.context.getString(hint)
        }
    }
