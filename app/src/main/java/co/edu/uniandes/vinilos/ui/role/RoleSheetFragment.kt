package co.edu.uniandes.vinilos.ui.role

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentRoleSheetBinding
import co.edu.uniandes.vinilos.databinding.ItemRoleCardBinding
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.viewmodels.RoleSelectViewModel

/** Pregunta y tarjetas. Los toques los resuelve el ViewModel del selector, no este fragment. */
class RoleSheetFragment : Fragment(R.layout.fragment_role_sheet) {
    private var bindingOrNull: FragmentRoleSheetBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: RoleSelectViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { RoleSelectViewModel.Factory },
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentRoleSheetBinding.bind(view)
        showCard(
            card = binding.visitorCard,
            iconRes = R.drawable.ic_role_visitor,
            titleRes = R.string.role_visitor,
            descriptionRes = R.string.role_visitor_description,
            actionRes = R.string.role_visitor_action,
            role = UserRole.VISITOR,
        )
        showCard(
            card = binding.collectorCard,
            iconRes = R.drawable.ic_role_collector,
            titleRes = R.string.role_collector,
            descriptionRes = R.string.role_collector_description,
            actionRes = R.string.role_collector_action,
            role = UserRole.COLLECTOR,
        )
        // En esta pantalla la barra inferior está oculta, así que la hoja recibe el margen del sistema.
        ViewCompat.setOnApplyWindowInsetsListener(binding.sheetContent) { content, insets ->
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val base = content.resources.getDimensionPixelSize(R.dimen.space_xl)
            content.updatePadding(bottom = base + navigationBar)
            insets
        }
        ViewCompat.requestApplyInsets(binding.sheetContent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }

    private fun showCard(
        card: ItemRoleCardBinding,
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int,
        @StringRes actionRes: Int,
        role: UserRole,
    ) {
        card.roleIcon.setImageDrawable(AppCompatResources.getDrawable(requireContext(), iconRes))
        card.roleTitle.setText(titleRes)
        card.roleDescription.setText(descriptionRes)
        card.roleCard.contentDescription = getString(actionRes)
        card.roleIconBadge.clipToOutline = true
        card.roleCard.setOnClickListener { viewModel.onRoleSelected(role) }
    }
}
