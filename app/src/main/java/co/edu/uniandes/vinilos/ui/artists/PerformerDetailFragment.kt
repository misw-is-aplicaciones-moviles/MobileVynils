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
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.AlbumDetailViewModel
import co.edu.uniandes.vinilos.viewmodels.PerformerDetailEvent
import co.edu.uniandes.vinilos.viewmodels.PerformerDetailUiState
import co.edu.uniandes.vinilos.viewmodels.PerformerDetailViewModel
import co.edu.uniandes.vinilos.viewmodels.PerformerListViewModel
import com.google.android.material.transition.MaterialFadeThrough

class PerformerDetailFragment : Fragment(R.layout.fragment_album_detail) {
    private var bindingOrNull: FragmentAlbumDetailBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: PerformerDetailViewModel by viewModels { PerformerDetailViewModel.Factory }

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
        val adapter = PerformerDetailAdapter(viewModel::onAlbumClicked, viewModel::onMemberClicked)
        binding.detailList.layoutManager = LinearLayoutManager(requireContext())
        binding.detailList.adapter = adapter
        binding.errorState.retryButton.setOnClickListener { viewModel.retry() }
        viewModel.uiState.observe(viewLifecycleOwner) { render(it, adapter) }
        viewModel.events.observe(viewLifecycleOwner) { it.getContentIfNotHandled()?.let(::handle) }
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
        state.detail?.let { adapter.submitDetail(it, state.role == UserRole.COLLECTOR) }
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
