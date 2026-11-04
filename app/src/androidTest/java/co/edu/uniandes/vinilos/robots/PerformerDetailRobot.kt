package co.edu.uniandes.vinilos.robots

import android.graphics.Rect
import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.isA

/** Desplazamiento por contenido del detalle, incluso dentro de cabeceras más altas que la pantalla. */
class PerformerDetailRobot {
    fun scrollToText(
        text: String,
        @IdRes viewId: Int? = null,
    ) {
        val target = if (viewId == null) withText(text) else allOf(withId(viewId), withText(text))
        waitForDisplayed(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                anyOf(target, hasDescendant(target)),
            ),
        )
        // Una cabecera puede ser más alta que la pantalla en horizontal: desplazar también su texto hijo.
        onView(allOf(target, isDescendantOfA(withId(R.id.detailList)))).perform(
            object : ViewAction {
                override fun getConstraints() = isA(View::class.java)

                override fun getDescription() = "Desplazar hasta el texto dentro de la fila"

                override fun perform(
                    uiController: UiController,
                    view: View,
                ) {
                    view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
                    uiController.loopMainThreadUntilIdle()
                }
            },
        )
        waitForDisplayed(allOf(target, isDescendantOfA(withId(R.id.detailList))))
    }
}
