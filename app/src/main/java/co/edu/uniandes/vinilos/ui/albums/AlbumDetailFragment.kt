package co.edu.uniandes.vinilos.ui.albums

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentAlbumDetailBinding
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.AlbumDetailEvent
import co.edu.uniandes.vinilos.viewmodels.AlbumDetailUiState
import co.edu.uniandes.vinilos.viewmodels.AlbumDetailViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough

class AlbumDetailFragment : Fragment(R.layout.fragment_album_detail) {
    private var bindingOrNull: FragmentAlbumDetailBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: AlbumDetailViewModel by viewModels { AlbumDetailViewModel.Factory }

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
        val adapter = AlbumDetailAdapter()
        binding.detailList.layoutManager = LinearLayoutManager(requireContext())
        binding.detailList.adapter = adapter
        binding.errorState.retryButton.setOnClickListener { viewModel.retry() }
        bindTrackCreatedResult()
        viewModel.uiState.observe(viewLifecycleOwner) { render(it, adapter) }
        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (event.getContentIfNotHandled()) {
                AlbumDetailEvent.OpenRoleSelector -> findNavController().navigateToRoleSelector()
                null -> Unit
            }
        }
    }

    override fun onDestroyView() {
        // When returning, the restored catalog owns the button and may already have rendered it.
        if (findNavController().currentDestination?.id != R.id.albumListFragment) {
            (activity as? PrimaryActionHost)?.hidePrimaryAction()
        }
        binding.detailList.adapter = null
        bindingOrNull = null
        super.onDestroyView()
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.album_detail_title)
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        header.headerNav.isVisible = true
        header.headerBack.isVisible = true
        header.headerBack.setBackgroundResource(R.drawable.bg_album_detail_back)
        header.headerNav.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.album_detail_margin)
        }
        header.headerBack.setOnClickListener { findNavController().navigateUp() }
        header.roleChip.setOnClickListener { viewModel.onRoleChipClicked() }
        ViewCompat.setOnApplyWindowInsetsListener(header.header) { bar, insets ->
            bar.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        ViewCompat.requestApplyInsets(header.header)
    }

    private fun render(
        state: AlbumDetailUiState,
        adapter: AlbumDetailAdapter,
    ) {
        binding.loadingState.root.isVisible = state.isLoading
        binding.errorScroll.isVisible = state.error != null
        binding.detailList.isVisible = state.album != null
        state.album?.let(adapter::submitAlbum)
        renderRole(state.role)
        val notFound = state.error == DataError.Http(NOT_FOUND)
        binding.errorState.errorTitle.setText(
            if (notFound) R.string.album_detail_not_found else R.string.album_detail_error,
        )
        binding.errorState.errorBody.setText(
            when {
                notFound -> R.string.album_detail_not_found_body
                state.error == DataError.Network -> R.string.album_list_error_body
                else -> R.string.album_detail_error_body
            },
        )
        binding.errorState.retryButton.isVisible = !notFound
        renderPrimaryAction(state.album, state.role)
    }

    private fun renderPrimaryAction(
        album: Album?,
        role: UserRole?,
    ) {
        val host = activity as? PrimaryActionHost ?: return
        if (album != null && role == UserRole.COLLECTOR) {
            host.showPrimaryAction(R.string.track_sheet_title) { openTrackSheet(album) }
        } else {
            host.hidePrimaryAction()
        }
    }

    private fun openTrackSheet(album: Album) {
        if (childFragmentManager.findFragmentByTag(TrackCreateBottomSheet.TAG) != null) return
        TrackCreateBottomSheet.newInstance(album).show(childFragmentManager, TrackCreateBottomSheet.TAG)
    }

    private fun bindTrackCreatedResult() {
        childFragmentManager.setFragmentResultListener(
            TrackCreateBottomSheet.RESULT_TRACK_CREATED,
            viewLifecycleOwner,
        ) { _, result ->
            val album = viewModel.uiState.value?.album ?: return@setFragmentResultListener
            if (result.getInt(TrackCreateBottomSheet.RESULT_ALBUM_ID) != album.id) return@setFragmentResultListener
            val trackId = result.getInt(TrackCreateBottomSheet.RESULT_TRACK_ID)
            val trackName = result.getString(TrackCreateBottomSheet.RESULT_TRACK_NAME)
            val trackDuration = result.getString(TrackCreateBottomSheet.RESULT_TRACK_DURATION)
            if (trackId <= 0 || trackName == null || trackDuration == null) return@setFragmentResultListener

            viewModel.onTrackCreated(Track(trackId, trackName, trackDuration))
            showTrackCreatedMessage()
        }
    }

    private fun showTrackCreatedMessage() {
        val snackbar = Snackbar.make(binding.root, R.string.track_created, Snackbar.LENGTH_SHORT)
        (activity as? PrimaryActionHost)?.let { snackbar.setAnchorView(it.snackbarAnchor) }
        snackbar.show()
    }

    private fun renderRole(role: UserRole?) {
        val chip = binding.headerBar.roleChip
        chip.isVisible = role != null
        if (role == null) return
        val label = if (role == UserRole.VISITOR) R.string.role_visitor else R.string.role_collector
        chip.setText(label)
        chip.contentDescription = getString(R.string.role_chip_action, getString(label))
    }

    private companion object {
        const val TRANSITION_MILLIS = 300L
        const val NOT_FOUND = 404
    }
}
