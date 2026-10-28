package co.edu.uniandes.vinilos.ui.role

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentRoleStageBinding

/** Marca y collage sobre el fondo carbón. No tiene estado propio. */
class RoleStageFragment : Fragment(R.layout.fragment_role_stage) {
    private var bindingOrNull: FragmentRoleStageBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentRoleStageBinding.bind(view)
        // La marca queda bajo la barra de estado; el carbón del padre sigue hasta el borde.
        ViewCompat.setOnApplyWindowInsetsListener(binding.stageRoot) { stage, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val extra = stage.resources.getDimensionPixelSize(R.dimen.space_l)
            stage.updatePadding(top = statusBar + extra)
            insets
        }
        ViewCompat.requestApplyInsets(binding.stageRoot)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }
}
