package co.edu.uniandes.vinilos.ui.role

import android.content.res.Configuration
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
        val baseLeft = binding.stageRoot.paddingLeft
        val baseRight = binding.stageRoot.paddingRight
        val baseBottom = binding.stageRoot.paddingBottom
        // La marca queda bajo la barra de estado; el carbón del padre sigue hasta el borde.
        ViewCompat.setOnApplyWindowInsetsListener(binding.stageRoot) { stage, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            val extra = stage.resources.getDimensionPixelSize(R.dimen.space_l)
            val landscape =
                stage.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            // En horizontal la columna solo toca el borde inicial y el inferior. El otro borde es la hoja.
            stage.updatePadding(
                left = baseLeft + bars.left,
                top = bars.top + extra,
                right = baseRight + if (landscape) 0 else bars.right,
                bottom = baseBottom + if (landscape) bars.bottom else 0,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.stageRoot)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }
}
