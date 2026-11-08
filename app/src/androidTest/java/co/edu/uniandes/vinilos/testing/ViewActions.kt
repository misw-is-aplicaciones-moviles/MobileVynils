package co.edu.uniandes.vinilos.testing

import android.graphics.Rect
import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.isA

/** Mueve un NestedScrollView o un RecyclerView hasta el hijo. `scrollTo()` solo acepta ScrollView. */
fun bringIntoView(): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = isA(View::class.java)

        override fun getDescription() = "bring the view into the visible area"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
            uiController.loopMainThreadUntilIdle()
        }
    }

/**
 * Dispara el clic aunque la barra de estado tape parte del control.
 * El clic estándar exige el 90 % del área y en API 21 la X de la cabecera no llega.
 */
fun clickVisible(): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = allOf(isClickable(), withEffectiveVisibility(Visibility.VISIBLE))

        override fun getDescription() = "click the view when part of it is visible"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            val visible = Rect()
            check(view.getGlobalVisibleRect(visible) && !visible.isEmpty) {
                "El control no tiene área visible"
            }
            view.performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }
