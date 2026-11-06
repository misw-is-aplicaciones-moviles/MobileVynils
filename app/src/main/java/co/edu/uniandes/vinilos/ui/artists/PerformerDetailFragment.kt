package co.edu.uniandes.vinilos.ui.artists

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentAlbumDetailBinding
import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.ui.common.showConfirmationSnackbar
import co.edu.uniandes.vinilos.viewmodels.AlbumDetailViewModel
import co.edu.uniandes.vinilos.viewmodels.PerformerDetailEvent
import co.edu.uniandes.vinilos.viewmodels.PerformerDetailUiState
import co.edu.uniandes.vinilos.viewmodels.PerformerDetailViewModel
import co.edu.uniandes.vinilos.viewmodels.PerformerListViewModel
import co.edu.uniandes.vinilos.viewmodels.PrizeFlowContract
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import java.time.LocalDate
import java.time.format.DateTimeParseException

class PerformerDetailFragment : Fragment(R.layout.fragment_album_detail) {
    private var bindingOrNull: FragmentAlbumDetailBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: PerformerDetailViewModel by viewModels { PerformerDetailViewModel.Factory }
    private var revealAssignedPrizeOnNextList = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
        exitTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentAlbumDetailBinding.bind(view)
        (activity as? PrimaryActionHost)?.hidePrimaryAction()
        bindHeader()
        val adapter =
            PerformerDetailAdapter(
                viewModel::onAlbumClicked,
                viewModel::onMemberClicked,
                viewModel::onAddPrizeClicked,
                viewModel::retry,
            )
        binding.detailList.layoutManager = LinearLayoutManager(requireContext())
        binding.detailList.adapter = adapter
        binding.detailList.updatePadding(
            bottom =
                binding.detailList.paddingBottom +
                    resources.getDimensionPixelSize(R.dimen.performer_detail_notice_clearance),
        )
        binding.errorState.retryButton.setOnClickListener { viewModel.retry() }
        viewModel.uiState.observe(viewLifecycleOwner) { render(it, adapter) }
        viewModel.events.observe(viewLifecycleOwner) { it.getContentIfNotHandled()?.let(::handle) }
        observePrizeResults()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onNavigationReturned()
    }

    override fun onDestroyView() {
        binding.detailList.adapter = null
        bindingOrNull = null
        super.onDestroyView()
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(
            when (arguments?.getString(PerformerListViewModel.ARG_PERFORMER_TYPE)) {
                PerformerType.MUSICIAN.name -> R.string.artist_musician
                PerformerType.BAND.name -> R.string.artist_band
                else -> R.string.artist_detail_title
            },
        )
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        header.headerNav.isVisible = true
        header.headerBack.isVisible = true
        header.headerBack.setBackgroundResource(R.drawable.bg_album_detail_back)
        header.headerNav.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.album_detail_margin)
        }
        header.headerBack.setOnClickListener { findNavController().navigateUp() }
        header.roleChip.setOnClickListener { viewModel.onRoleChipClicked() }
        ViewCompat.setOnApplyWindowInsetsListener(header.header) { target, insets ->
            target.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        ViewCompat.requestApplyInsets(header.header)
    }

    private fun render(
        state: PerformerDetailUiState,
        adapter: PerformerDetailAdapter,
    ) {
        binding.loadingState.root.isVisible = state.isLoading
        binding.errorScroll.isVisible = state.error != null
        binding.detailList.isVisible = state.detail != null
        state.detail?.let { adapter.submitDetail(state) { revealAssignedPrize(adapter) } }
        renderRole(state.role)
        val notFound = state.error == DataError.Http(NOT_FOUND)
        binding.errorState.errorTitle.setText(
            when {
                !state.hasValidIdentity -> R.string.artist_detail_invalid
                notFound -> R.string.artist_detail_not_found
                else -> R.string.artist_detail_error
            },
        )
        binding.errorState.errorBody.setText(
            when {
                !state.hasValidIdentity -> R.string.artist_detail_invalid_body
                notFound -> R.string.artist_detail_not_found_body
                state.error == DataError.Network -> R.string.artist_list_error_body
                else -> R.string.artist_detail_error_body
            },
        )
        binding.errorState.retryButton.isVisible = state.hasValidIdentity
    }

    private fun renderRole(role: UserRole?) {
        val chip = binding.headerBar.roleChip
        chip.isVisible = role != null
        if (role == null) return
        val label = if (role == UserRole.VISITOR) R.string.role_visitor else R.string.role_collector
        chip.setText(label)
        chip.contentDescription = getString(R.string.role_chip_action, getString(label))
    }

    // A long biography would leave the highlighted row below the fold (mockup HU14-6 shows it).
    private fun revealAssignedPrize(adapter: PerformerDetailAdapter) {
        if (!revealAssignedPrizeOnNextList) return
        val position = adapter.highlightedPosition()
        if (position < 0) return
        revealAssignedPrizeOnNextList = false
        bindingOrNull?.detailList?.scrollToPosition(position)
    }

    private fun observePrizeResults() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        handle.getLiveData<Boolean>(PrizeFlowContract.REFRESH_REQUESTED).observe(viewLifecycleOwner) { refresh ->
            handle.remove<Boolean>(PrizeFlowContract.REFRESH_REQUESTED)
            if (refresh == true) viewModel.retry()
        }
        handle.getLiveData<Bundle>(PrizeFlowContract.PRIZE_ASSIGNED).observe(viewLifecycleOwner) { result ->
            handle.remove<Bundle>(PrizeFlowContract.PRIZE_ASSIGNED)
            val date =
                try {
                    LocalDate.parse(result.getString(PrizeFlowContract.PREMIATION_DATE) ?: return@observe)
                } catch (_: DateTimeParseException) {
                    return@observe
                }
            val prize =
                Prize(
                    result.getInt(PrizeFlowContract.PRIZE_ID),
                    result.getString(PrizeFlowContract.NAME) ?: return@observe,
                    result.getString(PrizeFlowContract.DESCRIPTION) ?: return@observe,
                    result.getString(PrizeFlowContract.ORGANIZATION) ?: return@observe,
                )
            viewModel.onPrizeAssigned(
                result.getInt(PrizeFlowContract.PERFORMER_ID),
                PerformerType.entries.firstOrNull { it.name == result.getString(PrizeFlowContract.PERFORMER_TYPE) },
                ArtistPrize(result.getInt(PrizeFlowContract.ASSIGNMENT_ID), prize, date),
            )
        }
    }

    private fun handle(event: PerformerDetailEvent) {
        when (event) {
            is PerformerDetailEvent.OpenAlbum -> {
                findNavController().navigate(
                    R.id.albumDetailFragment,
                    bundleOf(AlbumDetailViewModel.ARG_ALBUM_ID to event.albumId),
                    navOptions { launchSingleTop = true },
                )
            }

            is PerformerDetailEvent.OpenMember -> {
                findNavController().navigate(
                    R.id.performerDetailFragment,
                    bundleOf(
                        PerformerListViewModel.ARG_PERFORMER_ID to event.performerId,
                        PerformerListViewModel.ARG_PERFORMER_TYPE to PerformerType.MUSICIAN.name,
                    ),
                )
            }

            is PerformerDetailEvent.OpenPrizes -> {
                val detail = event.detail
                findNavController().navigate(
                    R.id.prizePickerFragment,
                    PrizePickerFragment.argumentsFor(
                        detail.performer.id,
                        detail.performer.type,
                        detail.performer.name,
                        detail.prizeAssignments.map { it.id }.toIntArray(),
                    ),
                )
            }

            PerformerDetailEvent.PrizeAssigned -> {
                revealAssignedPrizeOnNextList = true
                showConfirmationSnackbar(
                    binding.root,
                    (activity as? PrimaryActionHost)?.snackbarAnchor,
                    R.string.prize_assigned_message,
                    Snackbar.LENGTH_LONG,
                )
            }

            PerformerDetailEvent.OpenRoleSelector -> {
                findNavController().navigateToRoleSelector()
            }
        }
    }

    private companion object {
        const val TRANSITION_MILLIS = 300L
        const val NOT_FOUND = 404
    }
}
