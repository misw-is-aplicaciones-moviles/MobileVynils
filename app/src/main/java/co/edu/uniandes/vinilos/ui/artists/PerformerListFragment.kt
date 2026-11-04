package co.edu.uniandes.vinilos.ui.artists

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentPerformerListBinding
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.createListDivider
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.PerformerFilter
import co.edu.uniandes.vinilos.viewmodels.PerformerListEvent
import co.edu.uniandes.vinilos.viewmodels.PerformerListUiState
import co.edu.uniandes.vinilos.viewmodels.PerformerListViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough

/** Artists (HU03): musicians and bands by name, with the Todos / Músicos / Bandas filter and its states. */
class PerformerListFragment : Fragment(R.layout.fragment_performer_list) {
    private var bindingOrNull: FragmentPerformerListBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: PerformerListViewModel by viewModels { PerformerListViewModel.Factory }
    private val adapter = PerformerAdapter(onPerformerClick = { id, type -> viewModel.onPerformerClicked(id, type) })

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
        bindingOrNull = FragmentPerformerListBinding.bind(view)
        bindHeader()
        bindFilters()
        bindList()
        bindStateTexts()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let(::handle)
        }
        // Esta pantalla no tiene acción principal en ningún rol. Se oculta una sola vez, al abrirla: el botón
        // "+" del catálogo no debe seguir visible aquí, y render() no debe tocarlo cuando esta vista ya sale.
        (activity as? PrimaryActionHost)?.hidePrimaryAction()
        ViewCompat.requestApplyInsets(binding.headerBar.header)
    }

    override fun onDestroyView() {
        binding.performerList.adapter = null
        super.onDestroyView()
        bindingOrNull = null
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.artist_list_title)
        // android:accessibilityHeading solo existe desde API 28; ViewCompat lo aplica desde API 21.
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        // La cabecera carbón se extiende bajo la barra de estado (edge-to-edge).
        ViewCompat.setOnApplyWindowInsetsListener(header.header) { bar, insets ->
            bar.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        header.roleChip.setOnClickListener { viewModel.onRoleChipClicked() }
    }

    // Programmatic checks also notify, so the ViewModel ignores a filter that is already selected.
    private fun bindFilters() {
        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            checkedIds.firstOrNull()?.let { id -> viewModel.onFilterSelected(filterOf(id)) }
        }
    }

    private fun bindList() {
        binding.performerList.adapter = adapter
        binding.performerList.addItemDecoration(
            createListDivider(requireContext(), R.dimen.performer_divider_inset_end),
        )
        val indicator = ContextCompat.getColor(requireContext(), R.color.vinilos_charcoal)
        binding.swipeRefresh.setColorSchemeColors(indicator)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.emptyRefresh.setColorSchemeColors(indicator)
        binding.emptyRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun bindStateTexts() {
        binding.errorState.errorTitle.setText(R.string.artist_list_error_title)
        binding.errorState.errorBody.setText(R.string.artist_list_error_body)
        binding.errorState.retryButton.setOnClickListener { viewModel.refresh() }
    }

    private fun render(state: PerformerListUiState) {
        renderRole(state.role)
        renderFilter(state.filter)
        renderList(state)
        renderStates(state)
    }

    private fun renderRole(role: UserRole?) {
        val chip = binding.headerBar.roleChip
        chip.isVisible = role != null
        if (role == null) return
        val label = if (role == UserRole.VISITOR) R.string.role_visitor else R.string.role_collector
        chip.setText(label)
        chip.contentDescription = getString(R.string.role_chip_action, getString(label))
    }

    private fun renderFilter(filter: PerformerFilter) {
        val chipId = chipIdOf(filter)
        if (binding.filterChips.checkedChipId != chipId) binding.filterChips.check(chipId)
    }

    private fun renderList(state: PerformerListUiState) {
        val hasPerformers = state.performers.isNotEmpty()
        adapter.submitList(state.performers)
        binding.swipeRefresh.isVisible = hasPerformers
        binding.listFade.isVisible = hasPerformers
        binding.swipeRefresh.isRefreshing = state.isRefreshing
    }

    private fun renderStates(state: PerformerListUiState) {
        val noPerformers = !state.hasPerformers
        binding.loadingState.root.isVisible = state.isLoading && noPerformers
        binding.errorState.root.isVisible = state.error != null && noPerformers
        binding.emptyRefresh.isVisible = state.isEmpty
        binding.emptyRefresh.isRefreshing = state.isEmpty && state.isRefreshing
        binding.emptyState.emptyMessage.setText(emptyMessageOf(state.filter))
    }

    private fun handle(event: PerformerListEvent) {
        when (event) {
            is PerformerListEvent.OpenDetail -> {
                findNavController().navigate(
                    R.id.action_performerList_to_performerDetail,
                    bundleOf(
                        PerformerListViewModel.ARG_PERFORMER_ID to event.performerId,
                        PerformerListViewModel.ARG_PERFORMER_TYPE to event.type.name,
                    ),
                )
            }

            is PerformerListEvent.ShowRefreshError -> {
                showRefreshError(event.error)
            }

            PerformerListEvent.OpenRoleSelector -> {
                findNavController().navigateToRoleSelector()
            }
        }
    }

    private fun showRefreshError(error: DataError) {
        if (error is DataError.Network) {
            snackbar(R.string.artist_list_offline_notice, Snackbar.LENGTH_SHORT).show()
            return
        }
        snackbar(R.string.artist_list_refresh_error, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_retry) { viewModel.refresh() }
            .show()
    }

    private fun snackbar(
        messageRes: Int,
        duration: Int,
    ): Snackbar =
        Snackbar
            .make(requireActivity().findViewById(R.id.main), messageRes, duration)
            .setAnchorView((activity as? PrimaryActionHost)?.snackbarAnchor)

    private fun chipIdOf(filter: PerformerFilter): Int =
        when (filter) {
            PerformerFilter.ALL -> R.id.filterAll
            PerformerFilter.MUSICIANS -> R.id.filterMusicians
            PerformerFilter.BANDS -> R.id.filterBands
        }

    private fun filterOf(chipId: Int): PerformerFilter =
        when (chipId) {
            R.id.filterMusicians -> PerformerFilter.MUSICIANS
            R.id.filterBands -> PerformerFilter.BANDS
            else -> PerformerFilter.ALL
        }

    private fun emptyMessageOf(filter: PerformerFilter): Int =
        when (filter) {
            PerformerFilter.ALL -> R.string.artist_list_empty
            PerformerFilter.MUSICIANS -> R.string.artist_list_empty_musicians
            PerformerFilter.BANDS -> R.string.artist_list_empty_bands
        }

    private companion object {
        const val TRANSITION_MILLIS = 300L
    }
}
