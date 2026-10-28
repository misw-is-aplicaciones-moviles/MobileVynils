package co.edu.uniandes.vinilos.ui.common

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentPendingTabBinding
import com.google.android.material.transition.MaterialFadeThrough

/**
 * Pantalla provisional de una pestaña mientras llega su historia (HU01, HU03, HU05 la reemplazan).
 * Título y mensaje llegan como argumentos desde nav_graph.xml.
 */
class PendingTabFragment : Fragment(R.layout.fragment_pending_tab) {
    private var bindingOrNull: FragmentPendingTabBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cambio de pestaña del kit v2: título y contenido salen y entran juntos en 300 ms.
        enterTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
        exitTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentPendingTabBinding.bind(view)
        val args = requireArguments()
        binding.headerBar.headerTitle.setText(args.getInt(ARG_TITLE_RES))
        // android:accessibilityHeading solo existe desde API 28; ViewCompat lo aplica desde API 21.
        ViewCompat.setAccessibilityHeading(binding.headerBar.headerTitle, true)
        binding.pendingMessage.setText(args.getInt(ARG_MESSAGE_RES))
        // La cabecera carbón se extiende bajo la barra de estado (edge-to-edge).
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar.header) { header, insets ->
            header.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.headerBar.header)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }

    private companion object {
        const val ARG_TITLE_RES = "titleRes"
        const val ARG_MESSAGE_RES = "messageRes"
        const val TRANSITION_MILLIS = 300L
    }
}
