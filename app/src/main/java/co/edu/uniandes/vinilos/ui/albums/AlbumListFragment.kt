package co.edu.uniandes.vinilos.ui.albums

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentAlbumListBinding
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.AlbumListEvent
import co.edu.uniandes.vinilos.viewmodels.AlbumListUiState
import co.edu.uniandes.vinilos.viewmodels.AlbumListViewModel
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough

/** Catalog (HU01): numbered list of albums with its loading, error and empty states. */
class AlbumListFragment : Fragment(R.layout.fragment_album_list) {
    private var bindingOrNull: FragmentAlbumListBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: AlbumListViewModel by viewModels { AlbumListViewModel.Factory }
    private val adapter = AlbumAdapter(onAlbumClick = { albumId -> viewModel.onAlbumClicked(albumId) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cambio de pestaña del kit v2: título y contenido salen y entran juntos en 300 ms.
        enterTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
        exitTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentAlbumListBinding.bind(view)
        bindHeader()
        bindList()
        bindStateTexts()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let(::handle)
        }
        ViewCompat.requestApplyInsets(binding.headerBar.header)
    }

    override fun onDestroyView() {
        (activity as? PrimaryActionHost)?.hidePrimaryAction()
        binding.albumList.adapter = null
        super.onDestroyView()
        bindingOrNull = null
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.album_list_title)
        // android:accessibilityHeading solo existe desde API 28; ViewCompat lo aplica desde API 21.
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        // La cabecera carbón se extiende bajo la barra de estado (edge-to-edge).
        ViewCompat.setOnApplyWindowInsetsListener(header.header) { bar, insets ->
            bar.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        header.roleChip.setOnClickListener { viewModel.onRoleChipClicked() }
    }

    private fun bindList() {
        binding.albumList.adapter = adapter
        binding.albumList.addItemDecoration(createDivider())
        val indicator = ContextCompat.getColor(requireContext(), R.color.vinilos_charcoal)
        binding.swipeRefresh.setColorSchemeColors(indicator)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.emptyRefresh.setColorSchemeColors(indicator)
        binding.emptyRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun createDivider(): RecyclerView.ItemDecoration {
        val context = requireContext()
        val inset = context.resources.getDimensionPixelSize(R.dimen.list_divider_inset)
        return MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL).apply {
            dividerThickness = context.resources.getDimensionPixelSize(R.dimen.list_divider_thickness)
            dividerColor = ContextCompat.getColor(context, R.color.vinilos_line)
            dividerInsetStart = inset
            dividerInsetEnd = inset
            isLastItemDecorated = false
        }
    }

    private fun bindStateTexts() {
        binding.errorState.errorTitle.setText(R.string.album_list_error_title)
        binding.errorState.errorBody.setText(R.string.album_list_error_body)
        binding.errorState.retryButton.setOnClickListener { viewModel.refresh() }
        binding.emptyState.emptyMessage.setText(R.string.album_list_empty)
    }

    private fun render(state: AlbumListUiState) {
        renderRole(state.role)
        renderList(state)
        renderStates(state)
        renderPrimaryAction(state)
    }

    private fun renderRole(role: UserRole?) {
        val chip = binding.headerBar.roleChip
        chip.isVisible = role != null
        if (role == null) return
        val label = if (role == UserRole.VISITOR) R.string.role_visitor else R.string.role_collector
        chip.setText(label)
        chip.contentDescription = getString(R.string.role_chip_action, getString(label))
    }

    private fun renderList(state: AlbumListUiState) {
        val hasAlbums = state.albums.isNotEmpty()
        adapter.submitList(state.albums)
        binding.swipeRefresh.isVisible = hasAlbums
        binding.listFade.isVisible = hasAlbums
        binding.swipeRefresh.isRefreshing = state.isRefreshing
    }

    private fun renderStates(state: AlbumListUiState) {
        val noAlbums = state.albums.isEmpty()
        binding.loadingState.root.isVisible = state.isLoading && noAlbums
        binding.errorState.root.isVisible = state.error != null && noAlbums
        binding.emptyRefresh.isVisible = state.isEmpty
        binding.emptyRefresh.isRefreshing = state.isEmpty && state.isRefreshing
    }

    private fun renderPrimaryAction(state: AlbumListUiState) {
        val host = activity as? PrimaryActionHost ?: return
        if (state.canEdit) {
            host.showPrimaryAction(R.string.action_create_album) { viewModel.onCreateAlbumClicked() }
        } else {
            host.hidePrimaryAction()
        }
    }

    // TODO(#15): navigate to albumDetailFragment when HU02 adds it (OpenDetail)
    private fun handle(event: AlbumListEvent) {
        when (event) {
            is AlbumListEvent.OpenDetail -> Unit
            is AlbumListEvent.ShowRefreshError -> showRefreshError(event.error)
            AlbumListEvent.ShowCreatePending -> showMessage(R.string.album_create_pending)
            AlbumListEvent.OpenRoleSelector -> findNavController().navigateToRoleSelector()
        }
    }

    private fun showRefreshError(error: DataError) {
        if (error is DataError.Network) {
            showMessage(R.string.album_list_offline_notice)
            return
        }
        snackbar(R.string.album_list_refresh_error, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_retry) { viewModel.refresh() }
            .show()
    }

    private fun showMessage(messageRes: Int) {
        snackbar(messageRes, Snackbar.LENGTH_SHORT).show()
    }

    private fun snackbar(
        messageRes: Int,
        duration: Int,
    ): Snackbar =
        Snackbar
            .make(requireActivity().findViewById(R.id.main), messageRes, duration)
            .setAnchorView((activity as? PrimaryActionHost)?.snackbarAnchor)

    private companion object {
        const val TRANSITION_MILLIS = 300L
    }
}
