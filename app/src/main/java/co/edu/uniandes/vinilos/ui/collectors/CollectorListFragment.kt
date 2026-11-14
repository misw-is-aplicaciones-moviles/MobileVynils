package co.edu.uniandes.vinilos.ui.collectors

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
import co.edu.uniandes.vinilos.databinding.FragmentCollectorListBinding
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.createListDivider
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.CollectorListEvent
import co.edu.uniandes.vinilos.viewmodels.CollectorListUiState
import co.edu.uniandes.vinilos.viewmodels.CollectorListViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough

/** Collectors: initials, name, email and counts of every collector by name, with its states. */
class CollectorListFragment : Fragment(R.layout.fragment_collector_list) {
    private var bindingOrNull: FragmentCollectorListBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: CollectorListViewModel by viewModels { CollectorListViewModel.Factory }
    private val adapter = CollectorAdapter(onCollectorClick = { id -> viewModel.onCollectorClicked(id) })

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
        bindingOrNull = FragmentCollectorListBinding.bind(view)
        bindHeader()
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
        binding.collectorList.adapter = null
        super.onDestroyView()
        bindingOrNull = null
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.collector_list_title)
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
        // Su alto no depende del contenido: es el de la hoja. Así, cambiar las filas no recalcula el layout de la lista.
        binding.collectorList.setHasFixedSize(true)
        binding.collectorList.adapter = adapter
        binding.collectorList.addItemDecoration(
            createListDivider(requireContext(), R.dimen.collector_row_padding_end),
        )
        val indicator = ContextCompat.getColor(requireContext(), R.color.vinilos_charcoal)
        binding.swipeRefresh.setColorSchemeColors(indicator)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.emptyRefresh.setColorSchemeColors(indicator)
        binding.emptyRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun bindStateTexts() {
        binding.errorState.errorTitle.setText(R.string.collector_list_error_title)
        binding.errorState.errorBody.setText(R.string.collector_list_error_body)
        binding.errorState.retryButton.setOnClickListener { viewModel.refresh() }
        binding.emptyState.emptyMessage.setText(R.string.collector_list_empty)
    }

    private fun render(state: CollectorListUiState) {
        renderRole(state.role)
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

    private fun renderList(state: CollectorListUiState) {
        val hasCollectors = state.collectors.isNotEmpty()
        adapter.submitList(state.collectors)
        binding.swipeRefresh.isVisible = hasCollectors
        binding.listFade.isVisible = hasCollectors
        binding.swipeRefresh.isRefreshing = state.isRefreshing
    }

    private fun renderStates(state: CollectorListUiState) {
        val noCollectors = state.collectors.isEmpty()
        binding.loadingState.root.isVisible = state.isLoading && noCollectors
        binding.errorState.root.isVisible = state.error != null && noCollectors
        binding.emptyRefresh.isVisible = state.isEmpty
        binding.emptyRefresh.isRefreshing = state.isEmpty && state.isRefreshing
    }

    private fun handle(event: CollectorListEvent) {
        when (event) {
            is CollectorListEvent.OpenDetail -> {
                findNavController().navigate(
                    R.id.action_collectorList_to_collectorDetail,
                    bundleOf(CollectorListViewModel.ARG_COLLECTOR_ID to event.collectorId),
                )
            }

            is CollectorListEvent.ShowRefreshError -> {
                showRefreshError(event.error)
            }

            CollectorListEvent.OpenRoleSelector -> {
                findNavController().navigateToRoleSelector()
            }
        }
    }

    private fun showRefreshError(error: DataError) {
        if (error is DataError.Network) {
            snackbar(R.string.collector_list_offline_notice, Snackbar.LENGTH_SHORT).show()
            return
        }
        snackbar(R.string.collector_list_refresh_error, Snackbar.LENGTH_LONG)
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

    private companion object {
        const val TRANSITION_MILLIS = 300L
    }
}
