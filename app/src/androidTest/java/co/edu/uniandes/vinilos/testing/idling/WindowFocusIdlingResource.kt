package co.edu.uniandes.vinilos.testing.idling

import android.view.View
import android.view.ViewTreeObserver
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.test.espresso.IdlingResource
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.Closeable

/** El foco y el estado de las hojas notifican cambios reales; isIdleNow no emite callbacks ni sondea con pausas. */
internal class WindowFocusIdlingResource :
    IdlingResource,
    Closeable {
    private val views = mutableSetOf<View>()
    private val managers = mutableSetOf<FragmentManager>()
    private val sheets = mutableSetOf<BottomSheetBehavior<*>>()
    private var awaitingActivityFocus = false
    private var busy = false
    private var callback: IdlingResource.ResourceCallback? = null
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { notifyIfReady() }
    private val fragments =
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(
                fm: FragmentManager,
                f: Fragment,
            ) = notifyIfReady()

            override fun onFragmentResumed(
                fm: FragmentManager,
                f: Fragment,
            ) = notifyIfReady()

            override fun onFragmentDestroyed(
                fm: FragmentManager,
                f: Fragment,
            ) = notifyIfReady()
        }
    private val sheetCallback =
        object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(
                bottomSheet: View,
                newState: Int,
            ) = notifyIfReady()

            override fun onSlide(
                bottomSheet: View,
                slideOffset: Float,
            ) = Unit
        }

    override fun getName(): String = "Vinilos: foco y transición de hojas"

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }

    override fun isIdleNow(): Boolean = ready().also { busy = !it }

    private fun notifyIfReady() {
        if (busy && ready()) {
            busy = false
            callback?.onTransitionToIdle()
        }
    }

    private fun ready(): Boolean {
        val activity =
            ActivityLifecycleMonitorRegistry
                .getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull() as? FragmentActivity ?: return true
        watch(activity.window.decorView)
        if (managers.add(activity.supportFragmentManager)) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragments, true)
        }
        val top = dialogs(activity.supportFragmentManager).lastOrNull()
        return if (top != null) {
            awaitingActivityFocus = true
            dialogReady(top)
        } else if (awaitingActivityFocus) {
            activity.window.decorView
                .hasWindowFocus()
                .also { if (it) awaitingActivityFocus = false }
        } else {
            true // Los menús de Spinner no son DialogFragments.
        }
    }

    private fun dialogReady(top: DialogFragment): Boolean {
        val dialog = top.dialog ?: return false
        val decor = dialog.window?.decorView ?: return false
        watch(decor)
        val stationary =
            if (dialog is BottomSheetDialog) {
                val behavior = dialog.behavior
                if (sheets.add(behavior)) behavior.addBottomSheetCallback(sheetCallback)
                behavior.state != BottomSheetBehavior.STATE_SETTLING &&
                    behavior.state != BottomSheetBehavior.STATE_DRAGGING
            } else {
                true
            }
        return dialog.isShowing && decor.hasWindowFocus() && stationary
    }

    private fun watch(view: View) {
        if (views.add(view)) view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
    }

    private fun dialogs(manager: FragmentManager): List<DialogFragment> =
        manager.fragments.flatMap { fragment ->
            val own =
                if (fragment is DialogFragment && fragment.isAdded && !fragment.isRemoving) {
                    listOf(fragment)
                } else {
                    emptyList()
                }
            own + if (fragment.isAdded) dialogs(fragment.childFragmentManager) else emptyList()
        }

    /** La regla lo llama en Main después de cerrar la Activity. */
    override fun close() {
        views.forEach {
            if (it.viewTreeObserver.isAlive) it.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        }
        managers.forEach { it.unregisterFragmentLifecycleCallbacks(fragments) }
        sheets.forEach { it.removeBottomSheetCallback(sheetCallback) }
        views.clear()
        managers.clear()
        sheets.clear()
        callback = null
    }
}
