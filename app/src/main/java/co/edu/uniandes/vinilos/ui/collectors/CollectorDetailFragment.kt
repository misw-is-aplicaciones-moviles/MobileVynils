package co.edu.uniandes.vinilos.ui.collectors

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
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.AlbumDetailViewModel
import co.edu.uniandes.vinilos.viewmodels.CollectorDetailEvent
import co.edu.uniandes.vinilos.viewmodels.CollectorDetailUiState
import co.edu.uniandes.vinilos.viewmodels.CollectorDetailViewModel
import co.edu.uniandes.vinilos.viewmodels.PerformerListViewModel
import com.google.android.material.transition.MaterialFadeThrough

/** Collector detail: contact, favorite artists and albums with price and availability. */
class CollectorDetailFragment : Fragment(R.layout.fragment_album_detail) {
    private var bindingOrNull: FragmentAlbumDetailBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: CollectorDetailViewModel by viewModels { CollectorDetailViewModel.Factory }

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
        val adapter = CollectorDetailAdapter(viewModel::onAlbumClicked, viewModel::onFavoriteClicked)
        binding.detailList.layoutManager = LinearLayoutManager(requireContext())
        binding.detailList.adapter = adapter
        binding.errorState.retryButton.setOnClickListener { viewModel.retry() }
        viewModel.uiState.observe(viewLifecycleOwner) { render(it, adapter) }
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let(::handle)
        }
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
        header.headerTitle.setText(R.string.collector_detail_title)
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
        state: CollectorDetailUiState,
        adapter: CollectorDetailAdapter,
    ) {
        binding.loadingState.root.isVisible = state.isLoading
        binding.errorScroll.isVisible = state.error != null
        binding.detailList.isVisible = state.detail != null
        state.detail?.let { adapter.submitDetail(state) }
        renderRole(state.role)
        renderError(state)
    }

    private fun renderRole(role: UserRole?) {
        val chip = binding.headerBar.roleChip
        chip.isVisible = role != null
        if (role == null) return
        val label = if (role == UserRole.VISITOR) R.string.role_visitor else R.string.role_collector
        chip.setText(label)
        chip.contentDescription = getString(R.string.role_chip_action, getString(label))
    }

    private fun renderError(state: CollectorDetailUiState) {
        val notFound = state.error == DataError.Http(NOT_FOUND)
        binding.errorState.errorTitle.setText(
            when {
                !state.hasValidIdentity -> R.string.collector_detail_invalid
                notFound -> R.string.collector_detail_not_found
                else -> R.string.collector_detail_error
            },
        )
        binding.errorState.errorBody.setText(
            when {
                !state.hasValidIdentity -> R.string.collector_detail_invalid_body
                notFound -> R.string.collector_detail_not_found_body
                state.error == DataError.Network -> R.string.collector_list_error_body
                else -> R.string.collector_detail_error_body
            },
        )
        binding.errorState.retryButton.isVisible = state.hasValidIdentity
    }

    private fun handle(event: CollectorDetailEvent) {
        when (event) {
            is CollectorDetailEvent.OpenAlbum -> {
                findNavController().navigate(
                    R.id.albumDetailFragment,
                    bundleOf(AlbumDetailViewModel.ARG_ALBUM_ID to event.albumId),
                    navOptions { launchSingleTop = true },
                )
            }

            is CollectorDetailEvent.OpenFavorite -> {
                findNavController().navigate(
                    R.id.performerDetailFragment,
                    bundleOf(
                        PerformerListViewModel.ARG_PERFORMER_ID to event.performerId,
                        PerformerListViewModel.ARG_PERFORMER_TYPE to event.type.name,
                    ),
                )
            }

            CollectorDetailEvent.OpenRoleSelector -> {
                findNavController().navigateToRoleSelector()
            }
        }
    }

    private companion object {
        const val TRANSITION_MILLIS = 300L
        const val NOT_FOUND = 404
    }
}
