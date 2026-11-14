package co.edu.uniandes.vinilos.testing

import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.Tapper
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import org.hamcrest.Matcher
import org.hamcrest.Matchers.isA

/**
 * Lleva el hijo al área visible.
 * En API 21 `requestRectangleOnScreen` no mueve el NestedScrollView de la hoja de rol y la nota
 * queda con el rectángulo visible vacío. El desplazamiento se aplica sobre el scroller.
 */
fun bringIntoView(): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = isA(View::class.java)

        override fun getDescription() = "bring the view into the visible area"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            val scroller = findScroller(view)
            if (scroller != null) {
                scrollDescendantInto(scroller, view)
            } else {
                view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
            }
            uiController.loopMainThreadUntilIdle()
        }
    }

private fun findScroller(view: View): ViewGroup? {
    var parent = view.parent
    while (parent is View) {
        if (parent is NestedScrollView || parent is ScrollView) return parent
        parent = parent.parent
    }
    return null
}

private fun scrollDescendantInto(
    scroller: ViewGroup,
    view: View,
) {
    val rect = Rect(0, 0, view.width, view.height)
    scroller.offsetDescendantRectToMyCoords(view, rect)
    val contentBottom = scroller.getChildAt(0)?.bottom ?: rect.bottom
    val maxScroll = (contentBottom - scroller.height).coerceAtLeast(0)
    val viewportTop = scroller.scrollY + scroller.paddingTop
    val viewportBottom = scroller.scrollY + scroller.height - scroller.paddingBottom
    val target =
        when {
            rect.top >= viewportTop && rect.bottom <= viewportBottom -> scroller.scrollY
            rect.height() >= scroller.height -> rect.top - scroller.paddingTop
            rect.bottom > viewportBottom -> rect.bottom - scroller.height + scroller.paddingBottom
            else -> rect.top - scroller.paddingTop
        }
    scroller.scrollTo(scroller.scrollX, target.coerceIn(0, maxScroll))
}

/**
 * Toca el centro de lo que sí se ve.
 * El clic de Espresso exige el 90 % del área. En API 21 la barra de estado tapa la cabecera y la lista
 * corta el botón de premio. Un `performClick()` tampoco abre la fila: el listener está en el padre.
 */
fun clickVisible(): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = withEffectiveVisibility(Visibility.VISIBLE)

        override fun getDescription() = "click the visible part of the view"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            val visible = Rect()
            check(view.getGlobalVisibleRect(visible) && !visible.isEmpty) {
                "El control no tiene área visible"
            }
            val origin = IntArray(2)
            view.rootView.getLocationOnScreen(origin)
            val coordinates =
                floatArrayOf(
                    origin[0] + visible.exactCenterX(),
                    origin[1] + visible.exactCenterY(),
                )
            val status =
                Tap.SINGLE.sendTap(
                    uiController,
                    coordinates,
                    Press.FINGER.describePrecision(),
                    InputDevice.SOURCE_UNKNOWN,
                    MotionEvent.BUTTON_PRIMARY,
                )
            val pressed = ViewConfiguration.getPressedStateDuration().toLong()
            if (pressed > 0) uiController.loopMainThreadForAtLeast(pressed)
            check(status != Tapper.Status.FAILURE) { "No se pudo tocar el área visible" }
        }
    }

/**
 * Llama al OnClickListener de la vista.
 * Tras recrear la actividad, el toque inyectado no cierra la hoja de fecha en API 21.
 */
fun clickListener(): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = withEffectiveVisibility(Visibility.VISIBLE)

        override fun getDescription() = "invoke the view click listener"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            view.performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }
