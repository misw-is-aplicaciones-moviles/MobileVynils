package co.edu.uniandes.vinilos.ui.collectors

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentCollectorFavoritePickerBinding
import co.edu.uniandes.vinilos.databinding.ItemCollectorFavoriteOptionBinding
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.CollectorFavoriteOption
import co.edu.uniandes.vinilos.viewmodels.CollectorFavoritePickerEvent
import co.edu.uniandes.vinilos.viewmodels.CollectorFavoritePickerUiState
import co.edu.uniandes.vinilos.viewmodels.CollectorFavoritePickerViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.google.android.material.transition.MaterialFadeThrough

/** HU10: choose an artist and add them to a collector's favorites. */
class CollectorFavoritePickerFragment : Fragment(R.layout.fragment_collector_favorite_picker) {
    private var bindingOrNull: FragmentCollectorFavoritePickerBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: CollectorFavoritePickerViewModel by viewModels { CollectorFavoritePickerViewModel.Factory }
    private lateinit var adapter: CollectorFavoritePickerAdapter
    private val blockBackWhileSaving =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
        exitTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentCollectorFavoritePickerBinding.bind(view)
        (activity as? PrimaryActionHost)?.hidePrimaryAction()
        bindHeader()
        adapter = CollectorFavoritePickerAdapter(Glide.with(this), viewModel::onPerformerSelected)
        binding.performerList.layoutManager = LinearLayoutManager(requireContext())
        // Its height comes from the layout weight, not from the rows, so new lists do not re-measure it.
        binding.performerList.setHasFixedSize(true)
        binding.performerList.adapter = adapter
        restoreSearchBeforeWatchingIt()
        binding.continueButton.setOnClickListener { viewModel.onAddSelected() }
        binding.errorState.retryButton.setOnClickListener { viewModel.retry() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, blockBackWhileSaving)
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let(::handle)
        }
    }

    override fun onDestroyView() {
        binding.performerList.adapter = null
        bindingOrNull = null
        super.onDestroyView()
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.collector_favorite_picker_title)
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        header.headerNav.isVisible = true
        header.headerClose.isVisible = true
        header.headerClose.setBackgroundResource(R.drawable.bg_album_detail_back)
        header.headerNav.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.album_detail_margin)
        }
        header.headerClose.setOnClickListener {
            if (viewModel.uiState.value?.isSubmitting == true) return@setOnClickListener
            findNavController().navigateUp()
        }
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
            val bars = WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()
            target.updatePadding(bottom = padding + insets.getInsets(bars).bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /** Writes the saved query first, so the watcher does not replace it with an empty field. */
    private fun restoreSearchBeforeWatchingIt() {
        val query =
            viewModel.uiState.value
                ?.query
                .orEmpty()
        binding.searchField.setText(query)
        binding.searchField.setSelection(query.length)
        binding.searchField.doAfterTextChanged { text ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
        }
    }

    private fun render(state: CollectorFavoritePickerUiState) {
        val visible = state.visiblePerformers
        val noSearchResults = state.hasNoSearchResults
        val busy = state.isSubmitting
        binding.searchField.isEnabled = !state.isLoading && !busy
        binding.headerBar.headerClose.isEnabled = !busy
        binding.headerBar.roleChip.isEnabled = !busy
        blockBackWhileSaving.isEnabled = busy
        binding.loadingState.root.isVisible = state.isLoading
        binding.pickerStatus.isVisible = state.error != null
        binding.errorState.root.isVisible = state.error != null
        binding.errorState.errorBody.setText(
            when (state.error) {
                DataError.Network -> R.string.collector_list_error_body
                else -> R.string.collector_detail_error_body
            },
        )
        binding.emptyMessage.isVisible = state.isEmpty || noSearchResults
        binding.emptyMessage.setText(
            if (noSearchResults) {
                R.string.collector_favorite_picker_no_results
            } else {
                R.string.collector_favorite_picker_empty
            },
        )
        binding.performerList.isVisible = state.error == null && visible.isNotEmpty()
        binding.performerList.isEnabled = !busy
        binding.performerList.alpha = if (busy) SUBMITTING_ALPHA else 1f
        binding.pickerContext.text = getString(R.string.collector_favorite_picker_context, state.collectorName)
        adapter.submitList(visible)
        binding.continueButton.isEnabled = state.canAdd
        binding.continueButton.text = continueLabel(state)
        binding.submitError.isVisible = state.submitError != null
        state.submitError?.let { binding.submitError.setText(submitErrorMessage(it)) }
    }

    private fun continueLabel(state: CollectorFavoritePickerUiState): String {
        val name = state.selectedPerformer?.name
        return when {
            state.isSubmitting -> getString(R.string.collector_favorite_picker_adding)
            name != null -> getString(R.string.collector_favorite_picker_add, name)
            else -> getString(R.string.collector_favorite_picker_select)
        }
    }

    private fun submitErrorMessage(error: DataError): Int =
        when (error) {
            DataError.Network -> R.string.collector_album_add_network_error
            is DataError.Http, DataError.Serialization, is DataError.Unknown -> R.string.collector_favorite_add_error
        }

    private fun handle(event: CollectorFavoritePickerEvent) {
        when (event) {
            CollectorFavoritePickerEvent.OpenRoleSelector -> findNavController().navigateToRoleSelector()
            is CollectorFavoritePickerEvent.FavoriteAdded -> returnAddedFavorite(event)
        }
    }

    private fun returnAddedFavorite(event: CollectorFavoritePickerEvent.FavoriteAdded) {
        val result =
            bundleOf(
                CollectorFavoritePickerViewModel.RESULT_COLLECTOR_ID to event.collectorId,
                CollectorFavoritePickerViewModel.RESULT_PERFORMER_ID to event.performerId,
                CollectorFavoritePickerViewModel.RESULT_PERFORMER_TYPE to event.type.name,
                CollectorFavoritePickerViewModel.RESULT_PERFORMER_NAME to event.name,
            )
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            CollectorFavoritePickerViewModel.RESULT_FAVORITE_ADDED,
            result,
        )
        findNavController().navigateUp()
    }

    private companion object {
        const val TRANSITION_MILLIS = 300L
        const val SUBMITTING_ALPHA = 0.5f
    }
}

private class CollectorFavoritePickerAdapter(
    private val requestManager: RequestManager,
    private val onPerformerClick: (Int, PerformerType) -> Unit,
) : ListAdapter<CollectorFavoriteOption, CollectorFavoriteOptionHolder>(Diff) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CollectorFavoriteOptionHolder =
        CollectorFavoriteOptionHolder(
            ItemCollectorFavoriteOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            requestManager,
            onPerformerClick,
        )

    override fun onBindViewHolder(
        holder: CollectorFavoriteOptionHolder,
        position: Int,
    ) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: CollectorFavoriteOptionHolder) = holder.unbind()

    private object Diff : DiffUtil.ItemCallback<CollectorFavoriteOption>() {
        override fun areItemsTheSame(
            oldItem: CollectorFavoriteOption,
            newItem: CollectorFavoriteOption,
        ): Boolean = oldItem.performer.id == newItem.performer.id && oldItem.performer.type == newItem.performer.type

        override fun areContentsTheSame(
            oldItem: CollectorFavoriteOption,
            newItem: CollectorFavoriteOption,
        ): Boolean = oldItem == newItem
    }
}

private const val ALREADY_FAVORITE_ALPHA = 0.5f

/**
 * Sets its click listener once and clears its photo when recycled, so a row off screen neither keeps the bitmap
 * nor finishes a download nobody will see.
 */
private class CollectorFavoriteOptionHolder(
    private val binding: ItemCollectorFavoriteOptionBinding,
    private val requestManager: RequestManager,
    onPerformerClick: (Int, PerformerType) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    private var current: CollectorFavoriteOption? = null
    private val selectedColor = ContextCompat.getColor(binding.root.context, R.color.vinilos_selected_row)
    private val unselectedColor = ContextCompat.getColor(binding.root.context, android.R.color.transparent)

    init {
        binding.root.setOnClickListener {
            current?.takeUnless { it.isAlreadyFavorite }?.let { option ->
                onPerformerClick(option.performer.id, option.performer.type)
            }
        }
    }

    fun bind(option: CollectorFavoriteOption) {
        current = option
        val context = binding.root.context
        val performer = option.performer
        val overline = overlineOf(context, performer)
        binding.performerName.text = performer.name
        binding.performerOverline.text = overline
        binding.addedChip.isVisible = option.isAlreadyFavorite
        binding.radioIcon.isVisible = !option.isAlreadyFavorite
        binding.radioIcon.setImageResource(
            if (option.isSelected) R.drawable.bg_picker_radio_checked else R.drawable.bg_picker_radio_unchecked,
        )
        binding.root.isEnabled = !option.isAlreadyFavorite
        binding.root.alpha = if (option.isAlreadyFavorite) ALREADY_FAVORITE_ALPHA else 1f
        binding.root.setBackgroundColor(if (option.isSelected) selectedColor else unselectedColor)
        val description =
            if (option.isAlreadyFavorite) {
                R.string.collector_favorite_picker_row_favorite
            } else {
                R.string.collector_favorite_picker_row_description
            }
        binding.root.contentDescription = context.getString(description, performer.name, overline)
        requestManager
            .load(performer.imageUrl)
            .placeholder(R.drawable.bg_performer_image_placeholder)
            .error(R.drawable.bg_performer_image_placeholder)
            .centerCrop()
            .into(binding.performerImage)
    }

    fun unbind() {
        current = null
        requestManager.clear(binding.performerImage)
    }

    private fun overlineOf(
        context: Context,
        performer: Performer,
    ): String {
        val kind =
            if (performer.type == PerformerType.BAND) {
                context.getString(R.string.artist_since, performer.date.year)
            } else {
                performer.date.year.toString()
            }
        val label =
            if (performer.type == PerformerType.BAND) R.string.artist_band else R.string.artist_musician
        return context.getString(R.string.artist_overline, context.getString(label), kind)
    }
}
