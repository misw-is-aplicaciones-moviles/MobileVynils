package co.edu.uniandes.vinilos.ui.artists

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentPrizePickerBinding
import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.ui.common.showConfirmationSnackbar
import co.edu.uniandes.vinilos.viewmodels.PrizeFlowContract
import co.edu.uniandes.vinilos.viewmodels.PrizePickerEvent
import co.edu.uniandes.vinilos.viewmodels.PrizePickerUiState
import co.edu.uniandes.vinilos.viewmodels.PrizePickerViewModel
import com.google.android.material.snackbar.Snackbar

/** HU04 opens this destination with artist identity and its current association IDs. */
class PrizePickerFragment : Fragment(R.layout.fragment_prize_picker) {
    private var bindingOrNull: FragmentPrizePickerBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: PrizePickerViewModel by viewModels { PrizePickerViewModel.Factory }
    private val adapter = PrizeAdapter { viewModel.onPrizeSelected(it) }
    private var revealCreatedPrizeOnNextList = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentPrizePickerBinding.bind(view)
        (activity as? PrimaryActionHost)?.hidePrimaryAction()
        bindHeaderAndInsets()
        binding.prizeList.layoutManager = LinearLayoutManager(requireContext())
        binding.prizeList.adapter = adapter
        binding.artistContext.text = getString(R.string.prize_picker_context, viewModel.performerName)
        binding.prizeSearch.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.continuePrize.setOnClickListener { viewModel.onContinue() }
        binding.createPrize.setOnClickListener { viewModel.onCreatePrize() }
        // A clickable LinearLayout is announced as a plain view; the card is a button.
        ViewCompat.setAccessibilityDelegate(
            binding.createPrize,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            },
        )
        binding.errorState.retryButton.setOnClickListener { viewModel.load(forceRefresh = true) }
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { it.getContentIfNotHandled()?.let(::handle) }
        observeCreatedPrize()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            },
        )
    }

    override fun onDestroyView() {
        binding.prizeList.adapter = null
        bindingOrNull = null
        super.onDestroyView()
    }

    private fun bindHeaderAndInsets() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.prize_picker_title)
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        header.headerNav.isVisible = true
        header.headerClose.isVisible = true
        header.headerClose.setBackgroundResource(R.drawable.bg_album_detail_back)
        header.headerNav.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.album_detail_margin)
        }
        header.headerClose.setOnClickListener { close() }
        header.roleChip.isVisible = true
        header.roleChip.setText(R.string.role_collector)
        header.roleChip.contentDescription = getString(R.string.role_chip_action, getString(R.string.role_collector))
        header.roleChip.setOnClickListener { viewModel.onRoleChipClicked() }
        ViewCompat.setOnApplyWindowInsetsListener(header.header) { target, insets ->
            target.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        val padding = resources.getDimensionPixelSize(R.dimen.form_padding_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.actions) { target, insets ->
            val bottom =
                insets
                    .getInsets(
                        WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime(),
                    ).bottom
            target.updatePadding(bottom = padding + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun render(state: PrizePickerUiState) {
        binding.prizeSearch.isEnabled = !state.isSubmitting
        if (binding.prizeSearch.text.toString() != state.query) binding.prizeSearch.setText(state.query)
        binding.loadingState.root.isVisible = state.isLoading && state.prizes.isEmpty()
        binding.errorState.root.isVisible = state.loadError != null && state.prizes.isEmpty()
        binding.errorState.errorBody.setText(R.string.prize_catalog_error)
        val empty = !state.isLoading && state.loadError == null && state.visiblePrizes.isEmpty()
        binding.emptyMessage.isVisible = empty
        binding.emptyMessage.setText(if (state.prizes.isEmpty()) R.string.prize_empty else R.string.prize_no_results)
        // Invisible, not gone: it heads the chain that keeps the card right below the rows.
        binding.prizeList.isInvisible = state.prizes.isEmpty()
        adapter.submitList(
            state.visiblePrizes.map { PrizeRow(it, it.id in state.assignedIds, it.id == state.selectedId) },
            ::revealCreatedPrize,
        )
        binding.continuePrize.isEnabled = state.canContinue || (state.isUncertain && !state.isSubmitting)
        binding.continuePrize.text = state.selectedPrize?.let { getString(R.string.prize_continue_action, it.name) }
            ?: getString(R.string.prize_select_action)
        if (state.isUncertain) binding.continuePrize.setText(R.string.prize_review_action)
        binding.headerBar.headerClose.isEnabled = !state.isSubmitting
        binding.headerBar.roleChip.isEnabled = !state.isSubmitting
        renderCreateCard(state)
    }

    // The new prize is inserted at position 0; a list that was scrolled would keep showing the rows below it.
    private fun revealCreatedPrize() {
        if (!revealCreatedPrizeOnNextList) return
        revealCreatedPrizeOnNextList = false
        bindingOrNull?.prizeList?.scrollToPosition(0)
    }

    private fun renderCreateCard(state: PrizePickerUiState) {
        // While the catalog loads or fails there is no list to complete: the loading and retry states take its place.
        binding.createPrize.isVisible = state.prizes.isNotEmpty() || (!state.isLoading && state.loadError == null)
        val canCreate = state.canEdit && !state.isSubmitting && !state.isUncertain
        binding.createPrize.isEnabled = canCreate
        binding.createPrize.alpha = if (canCreate) 1f else DISABLED_ALPHA
    }

    private fun handle(event: PrizePickerEvent) {
        when (event) {
            PrizePickerEvent.OpenDate -> {
                if (childFragmentManager.findFragmentByTag(PrizeDateBottomSheet.TAG) == null) {
                    PrizeDateBottomSheet().show(childFragmentManager, PrizeDateBottomSheet.TAG)
                }
            }

            PrizePickerEvent.OpenCreate -> {
                // Two taps before the first transition ends must not stack two forms.
                findNavController().navigate(
                    R.id.prizeCreateFragment,
                    null,
                    navOptions { launchSingleTop = true },
                )
            }

            PrizePickerEvent.PrizeCreated -> {
                revealCreatedPrizeOnNextList = true
                // Right above the strip of the button, below the card when the rows leave room (mockup HU13-4).
                showConfirmationSnackbar(
                    binding.root,
                    binding.actions,
                    R.string.prize_created_message,
                    Snackbar.LENGTH_SHORT,
                )
            }

            is PrizePickerEvent.Assigned -> {
                returnAssignment(event.assignment)
            }

            PrizePickerEvent.ReviewArtist -> {
                close(requestRefresh = true)
            }

            PrizePickerEvent.Close -> {
                close()
            }

            PrizePickerEvent.OpenRoleSelector -> {
                closeSheet()
                findNavController().navigateToRoleSelector()
            }
        }
    }

    private fun observeCreatedPrize() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        // HU13 left a request with an unknown result: the catalog may already have the new prize.
        handle.getLiveData<Boolean>(PrizeFlowContract.REFRESH_REQUESTED).observe(viewLifecycleOwner) {
            handle.remove<Boolean>(PrizeFlowContract.REFRESH_REQUESTED)
            viewModel.load(forceRefresh = true)
        }
        handle.getLiveData<Bundle>(PrizeFlowContract.PRIZE_CREATED).observe(viewLifecycleOwner) { result ->
            handle.remove<Bundle>(PrizeFlowContract.PRIZE_CREATED)
            val name = result.getString(PrizeFlowContract.NAME) ?: return@observe
            val description = result.getString(PrizeFlowContract.DESCRIPTION) ?: return@observe
            val organization = result.getString(PrizeFlowContract.ORGANIZATION) ?: return@observe
            viewModel.onPrizeCreated(Prize(result.getInt(PrizeFlowContract.PRIZE_ID), name, description, organization))
        }
    }

    private fun returnAssignment(assignment: ArtistPrize) {
        val result =
            bundleOf(
                PrizeFlowContract.ASSIGNMENT_ID to assignment.id,
                PrizeFlowContract.PERFORMER_ID to viewModel.performerId,
                PrizeFlowContract.PERFORMER_TYPE to viewModel.performerType?.name,
                PrizeFlowContract.PRIZE_ID to assignment.prize.id,
                PrizeFlowContract.NAME to assignment.prize.name,
                PrizeFlowContract.DESCRIPTION to assignment.prize.description,
                PrizeFlowContract.ORGANIZATION to assignment.prize.organization,
                PrizeFlowContract.PREMIATION_DATE to assignment.premiationDate.toString(),
            )
        findNavController().previousBackStackEntry?.savedStateHandle?.set(PrizeFlowContract.PRIZE_ASSIGNED, result)
        close()
    }

    private fun close(requestRefresh: Boolean = false) {
        val state = viewModel.uiState.value ?: return
        if (state.isSubmitting) return
        if (requestRefresh || state.isUncertain) {
            findNavController().previousBackStackEntry?.savedStateHandle?.set(PrizeFlowContract.REFRESH_REQUESTED, true)
        }
        closeSheet()
        findNavController().navigateUp()
    }

    private fun closeSheet() {
        (childFragmentManager.findFragmentByTag(PrizeDateBottomSheet.TAG) as? PrizeDateBottomSheet)?.dismiss()
    }

    companion object {
        private const val DISABLED_ALPHA = 0.5f

        fun argumentsFor(
            id: Int,
            type: PerformerType,
            name: String,
            assignmentIds: IntArray,
        ): Bundle =
            bundleOf(
                PrizeFlowContract.PERFORMER_ID to id,
                PrizeFlowContract.PERFORMER_TYPE to type.name,
                PrizeFlowContract.PERFORMER_NAME to name,
                PrizeFlowContract.ASSIGNMENT_IDS to assignmentIds,
            )
    }
}
