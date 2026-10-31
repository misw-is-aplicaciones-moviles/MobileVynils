package co.edu.uniandes.vinilos.ui.common

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentPendingTabBinding
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.SessionRepository
import com.google.android.material.transition.MaterialFadeThrough

/**
 * Pantalla provisional de una pestaña mientras llega su historia (HU03 y HU05 la reemplazan).
 * Título y mensaje llegan como argumentos desde nav_graph.xml.
 * El chip vuelve al selector. Álbumes ya no la usa: tiene AlbumListFragment (HU01).
 */
class PendingTabFragment : Fragment(R.layout.fragment_pending_tab) {
    private var bindingOrNull: FragmentPendingTabBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val sessionRepository: SessionRepository
        get() = (requireActivity().application as VinilosApplication).container.sessionRepository

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
        bindHeader(args.getInt(ARG_TITLE_RES))
        bindContent(args.getInt(ARG_MESSAGE_RES))
        ViewCompat.requestApplyInsets(binding.headerBar.header)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }

    private fun bindHeader(titleRes: Int) {
        binding.headerBar.headerTitle.setText(titleRes)
        // android:accessibilityHeading solo existe desde API 28; ViewCompat lo aplica desde API 21.
        ViewCompat.setAccessibilityHeading(binding.headerBar.headerTitle, true)
        // La cabecera carbón se extiende bajo la barra de estado (edge-to-edge).
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar.header) { header, insets ->
            header.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        binding.headerBar.roleChip.setOnClickListener { returnToRoleSelector() }
        sessionRepository.role.observe(viewLifecycleOwner, ::showRole)
    }

    private fun showRole(role: UserRole?) {
        val chip = binding.headerBar.roleChip
        chip.isVisible = role != null
        if (role == null) return
        val label = if (role == UserRole.VISITOR) R.string.role_visitor else R.string.role_collector
        chip.setText(label)
        chip.contentDescription = getString(R.string.role_chip_action, getString(label))
    }

    private fun returnToRoleSelector() {
        sessionRepository.clearRole()
        findNavController().navigateToRoleSelector()
    }

    private fun bindContent(messageRes: Int) {
        binding.pendingMessage.setText(messageRes)
    }

    private companion object {
        const val ARG_TITLE_RES = "titleRes"
        const val ARG_MESSAGE_RES = "messageRes"
        const val TRANSITION_MILLIS = 300L
    }
}
