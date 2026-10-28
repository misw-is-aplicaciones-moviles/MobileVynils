package co.edu.uniandes.vinilos.ui.role

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.viewmodels.RoleSelectEvent
import co.edu.uniandes.vinilos.viewmodels.RoleSelectViewModel
import com.google.android.material.transition.MaterialFadeThrough

/**
 * Destino inicial (TR-06). Compone [RoleStageFragment] y [RoleSheetFragment] y navega al catálogo.
 */
class ajustalRoleSelectFragment : Fragment(R.layout.fragment_role_select) {
    private val viewModel: RoleSelectViewModel by viewModels { RoleSelectViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
        exitTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let(::openCatalog)
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun openCatalog(event: RoleSelectEvent) {
        when (event) {
            RoleSelectEvent.OpenCatalog -> {
                findNavController().navigate(R.id.action_roleSelect_to_albumList)
            }
        }
    }

    private companion object {
        const val TRANSITION_MILLIS = 300L
    }
}
