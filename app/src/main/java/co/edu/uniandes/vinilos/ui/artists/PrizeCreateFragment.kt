package co.edu.uniandes.vinilos.ui.artists

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentPrizeCreateBinding
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.ui.common.fieldErrorText
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.FieldError
import co.edu.uniandes.vinilos.viewmodels.PrizeCreateEvent
import co.edu.uniandes.vinilos.viewmodels.PrizeCreateNotice
import co.edu.uniandes.vinilos.viewmodels.PrizeCreateUiState
import co.edu.uniandes.vinilos.viewmodels.PrizeCreateViewModel
import co.edu.uniandes.vinilos.viewmodels.PrizeFlowContract
import co.edu.uniandes.vinilos.viewmodels.PrizeFormErrors
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialFadeThrough

/**
 * New prize form (HU13), opened from "Elegir premio". Forwards what the collector types to the ViewModel and draws
 * its state. On success it leaves the prize in the back stack entry of the picker and closes; cancelling writes
 * nothing.
 */
class PrizeCreateFragment : Fragment(R.layout.fragment_prize_create) {
    private var bindingOrNull: FragmentPrizeCreateBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: PrizeCreateViewModel by viewModels { PrizeCreateViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
        exitTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentPrizeCreateBinding.bind(view)
        bindHeader()
        bindInputs()
        bindActions()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let(::handle)
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = viewModel.onCancel()
            },
        )
        ViewCompat.requestApplyInsets(binding.headerBar.header)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.create_prize_title)
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(header.header) { bar, insets ->
            bar.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        header.headerNav.isVisible = true
        header.headerClose.isVisible = true
        header.headerClose.setBackgroundResource(R.drawable.bg_album_detail_back)
        header.headerNav.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.album_detail_margin)
        }
        header.headerClose.setOnClickListener { viewModel.onCancel() }
        // Only a collector reaches this screen; any other role is sent to the role selector by the ViewModel.
        header.roleChip.isVisible = true
        header.roleChip.setText(R.string.role_collector)
        header.roleChip.contentDescription = getString(R.string.role_chip_action, getString(R.string.role_collector))
        header.roleChip.setOnClickListener { viewModel.onRoleChipClicked() }
    }

    private fun bindInputs() {
        binding.prizeNameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.prizeDescriptionInput.doAfterTextChanged { viewModel.onDescriptionChanged(it?.toString().orEmpty()) }
        binding.prizeOrganizationInput.doAfterTextChanged {
            viewModel.onOrganizationChanged(it?.toString().orEmpty())
        }
    }

    private fun bindActions() {
        binding.cancelPrizeButton.setOnClickListener { viewModel.onCancel() }
        binding.createPrizeButton.setOnClickListener { viewModel.onSubmit() }
        val baseBottom = binding.actions.paddingBottom
        // The bottom bar is hidden here, so the buttons take the navigation bar and the keyboard themselves.
        ViewCompat.setOnApplyWindowInsetsListener(binding.actions) { actions, insets ->
            val bottom =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()).bottom
            actions.updatePadding(bottom = baseBottom + bottom)
            insets
        }
    }

    private fun render(state: PrizeCreateUiState) {
        binding.prizeNameInput.setTextIfChanged(state.form.name)
        binding.prizeDescriptionInput.setTextIfChanged(state.form.description)
        binding.prizeOrganizationInput.setTextIfChanged(state.form.organization)
        renderErrors(state.errors)
        listOf(binding.prizeNameLayout, binding.prizeDescriptionLayout, binding.prizeOrganizationLayout)
            .forEach { it.isEnabled = !state.isLocked }
        binding.formScroll.alpha = if (state.isBusy) BUSY_ALPHA else 1f
        binding.cancelPrizeButton.isEnabled = !state.isBusy
        binding.headerBar.headerClose.isEnabled = !state.isBusy
        binding.headerBar.roleChip.isEnabled = !state.isBusy
        renderPrimaryButton(state)
        renderNotice(state.notice)
    }

    // Writing the same text again would move the cursor and fire the watcher in a loop.
    private fun EditText.setTextIfChanged(value: String) {
        if (text?.toString() != value) setText(value)
    }

    // The three fields are text: their only error is "Campo obligatorio".
    private fun renderErrors(errors: PrizeFormErrors) {
        showRequired(binding.prizeNameLayout, binding.prizeNameError, errors.name)
        showRequired(binding.prizeDescriptionLayout, binding.prizeDescriptionError, errors.description)
        showRequired(binding.prizeOrganizationLayout, binding.prizeOrganizationError, errors.organization)
    }

    // The mockups draw the field with a thicker border and the message flush with its edge.
    private fun showRequired(
        field: TextInputLayout,
        message: TextView,
        error: FieldError?,
    ) {
        field.setBoxStrokeWidthResource(if (error == null) R.dimen.field_stroke else R.dimen.field_stroke_focused)
        message.isVisible = error != null
        message.text = if (error == null) null else requireContext().fieldErrorText(R.string.error_required)
    }

    private fun renderPrimaryButton(state: PrizeCreateUiState) {
        val button = binding.createPrizeButton
        button.isEnabled = !state.isBusy
        button.setText(
            when {
                state.isSubmitting -> R.string.action_creating_prize
                state.isChecking -> R.string.action_checking_prize
                state.isUncertain -> R.string.action_check_prize
                else -> R.string.action_create_prize
            },
        )
    }

    private fun renderNotice(notice: PrizeCreateNotice?) {
        binding.formNotice.isVisible = notice != null
        if (notice == null) return
        binding.formNotice.setText(
            when (notice) {
                PrizeCreateNotice.REJECTED -> R.string.create_prize_rejected
                PrizeCreateNotice.UNCERTAIN -> R.string.create_prize_uncertain
                PrizeCreateNotice.CHECK_UNAVAILABLE -> R.string.create_prize_check_failed
                PrizeCreateNotice.NOT_SAVED -> R.string.create_prize_not_saved
            },
        )
    }

    private fun handle(event: PrizeCreateEvent) {
        when (event) {
            is PrizeCreateEvent.Created -> {
                returnPrize(event.prize)
            }

            is PrizeCreateEvent.Cancelled -> {
                if (event.catalogMayBeStale) {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(
                        PrizeFlowContract.REFRESH_REQUESTED,
                        true,
                    )
                }
                findNavController().popBackStack()
            }

            PrizeCreateEvent.OpenRoleSelector -> {
                findNavController().navigateToRoleSelector()
            }
        }
    }

    // The picker reads this Bundle, selects the prize and shows "Premio creado" once.
    private fun returnPrize(prize: Prize) {
        val navController = findNavController()
        navController.previousBackStackEntry?.savedStateHandle?.set(
            PrizeFlowContract.PRIZE_CREATED,
            bundleOf(
                PrizeFlowContract.PRIZE_ID to prize.id,
                PrizeFlowContract.NAME to prize.name,
                PrizeFlowContract.DESCRIPTION to prize.description,
                PrizeFlowContract.ORGANIZATION to prize.organization,
            ),
        )
        navController.popBackStack()
    }

    private companion object {
        const val TRANSITION_MILLIS = 300L
        const val BUSY_ALPHA = 0.5f
    }
}
