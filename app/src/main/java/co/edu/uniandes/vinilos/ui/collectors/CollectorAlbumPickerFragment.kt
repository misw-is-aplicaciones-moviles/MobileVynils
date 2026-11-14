package co.edu.uniandes.vinilos.ui.collectors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.navigation.navOptions
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentCollectorAlbumPickerBinding
import co.edu.uniandes.vinilos.databinding.ItemCollectorAlbumOptionBinding
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.CollectorAlbumPickerEvent
import co.edu.uniandes.vinilos.viewmodels.CollectorAlbumPickerOption
import co.edu.uniandes.vinilos.viewmodels.CollectorAlbumPickerUiState
import co.edu.uniandes.vinilos.viewmodels.CollectorAlbumPickerViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.google.android.material.transition.MaterialFadeThrough

/** HU11 phase 1: choose an album from the catalog before adding it to a collector's collection. */
class CollectorAlbumPickerFragment : Fragment(R.layout.fragment_collector_album_picker) {
    private var bindingOrNull: FragmentCollectorAlbumPickerBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: CollectorAlbumPickerViewModel by viewModels { CollectorAlbumPickerViewModel.Factory }
    private lateinit var adapter: CollectorAlbumPickerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
        exitTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentCollectorAlbumPickerBinding.bind(view)
        (activity as? PrimaryActionHost)?.hidePrimaryAction()
        bindHeader()
        // One RequestManager tied to this Fragment for every row, instead of resolving it from each row's view.
        adapter = CollectorAlbumPickerAdapter(Glide.with(this)) { albumId -> viewModel.onAlbumSelected(albumId) }
        binding.albumList.layoutManager = LinearLayoutManager(requireContext())
        // Its height comes from the layout weight, not from the rows, so new lists do not re-measure it.
        binding.albumList.setHasFixedSize(true)
        binding.albumList.adapter = adapter
        binding.searchField.doAfterTextChanged { text ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
        }
        binding.continueButton.setOnClickListener { viewModel.onContinue() }
        binding.errorState.retryButton.setOnClickListener { viewModel.retry() }
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let(::handle)
        }
        observeAlbumAdded()
    }

    override fun onDestroyView() {
        binding.albumList.adapter = null
        bindingOrNull = null
        super.onDestroyView()
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.collector_album_picker_title)
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        header.headerNav.isVisible = true
        header.headerClose.isVisible = true
        header.headerClose.setBackgroundResource(R.drawable.bg_album_detail_back)
        header.headerNav.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.album_detail_margin)
        }
        header.headerClose.setOnClickListener { findNavController().navigateUp() }
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
                insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()).bottom
            target.updatePadding(bottom = padding + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun render(state: CollectorAlbumPickerUiState) {
        val visibleAlbums = state.visibleAlbums
        val noSearchResults = state.hasNoSearchResults
        val selectedAlbum = state.selectedAlbum
        binding.searchField.isEnabled = !state.isLoading
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
                R.string.collector_album_picker_no_results
            } else {
                R.string.collector_album_picker_empty
            },
        )
        binding.albumList.isVisible = visibleAlbums.isNotEmpty()
        binding.pickerContext.text = getString(R.string.collector_album_picker_context, state.collectorName)
        adapter.submitList(visibleAlbums)
        binding.continueButton.isEnabled = state.canContinue
        binding.continueButton.text =
            selectedAlbum?.let {
                getString(R.string.collector_album_picker_continue, it.name)
            } ?: getString(R.string.collector_album_picker_select)
    }

    private fun handle(event: CollectorAlbumPickerEvent) {
        when (event) {
            is CollectorAlbumPickerEvent.ContinueWithAlbum -> openAddSheet(event)
            CollectorAlbumPickerEvent.OpenRoleSelector -> findNavController().navigateToRoleSelector()
        }
    }

    private fun openAddSheet(event: CollectorAlbumPickerEvent.ContinueWithAlbum) {
        if (childFragmentManager.findFragmentByTag(CollectorAlbumAddSheet.TAG) != null) return
        CollectorAlbumAddSheet()
            .apply {
                arguments =
                    CollectorAlbumAddSheet.argumentsFor(
                        collectorId = event.collectorId,
                        albumId = event.albumId,
                        albumName = event.albumName,
                        artistName =
                            viewModel.uiState.value
                                ?.collectorName
                                .orEmpty(),
                    )
            }.showNow(childFragmentManager, CollectorAlbumAddSheet.TAG)
    }

    private fun returnSelectedAlbum(event: CollectorAlbumPickerEvent.ContinueWithAlbum) {
        val result =
            bundleOf(
                CollectorAlbumPickerViewModel.RESULT_COLLECTOR_ID to event.collectorId,
                CollectorAlbumPickerViewModel.RESULT_ALBUM_ID to event.albumId,
                CollectorAlbumPickerViewModel.RESULT_ALBUM_NAME to event.albumName,
            )
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            CollectorAlbumPickerViewModel.RESULT_ALBUM_SELECTED,
            result,
        )
        findNavController().navigateUp()
    }

    private fun observeAlbumAdded() {
        childFragmentManager.setFragmentResultListener(
            CollectorAlbumAddSheet.RESULT_ALBUM_ADDED,
            viewLifecycleOwner,
        ) { _, result ->
            val collectorId = result.getInt(CollectorAlbumAddSheet.RESULT_COLLECTOR_ID)
            val albumId = result.getInt(CollectorAlbumAddSheet.RESULT_ALBUM_ID)
            val albumName = result.getString(CollectorAlbumAddSheet.RESULT_ALBUM_NAME).orEmpty()
            returnSelectedAlbum(
                CollectorAlbumPickerEvent.ContinueWithAlbum(collectorId, albumId, albumName),
            )
        }
    }

    private companion object {
        const val TRANSITION_MILLIS = 300L
    }
}

private class CollectorAlbumPickerAdapter(
    private val requestManager: RequestManager,
    private val onAlbumClick: (Int) -> Unit,
) : ListAdapter<CollectorAlbumPickerOption, CollectorAlbumOptionHolder>(Diff) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CollectorAlbumOptionHolder =
        CollectorAlbumOptionHolder(
            ItemCollectorAlbumOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            requestManager,
            onAlbumClick,
        )

    override fun onBindViewHolder(
        holder: CollectorAlbumOptionHolder,
        position: Int,
    ) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: CollectorAlbumOptionHolder) = holder.unbind()

    private object Diff : DiffUtil.ItemCallback<CollectorAlbumPickerOption>() {
        override fun areItemsTheSame(
            oldItem: CollectorAlbumPickerOption,
            newItem: CollectorAlbumPickerOption,
        ): Boolean = oldItem.album.id == newItem.album.id

        override fun areContentsTheSame(
            oldItem: CollectorAlbumPickerOption,
            newItem: CollectorAlbumPickerOption,
        ): Boolean = oldItem == newItem
    }
}

private const val ALREADY_ADDED_ALPHA = 0.5f

/**
 * Receives the binding already inflated (one view lookup per row), sets its click listener once and clears its
 * cover when recycled, so a row off screen neither keeps the bitmap nor finishes a download nobody will see.
 */
private class CollectorAlbumOptionHolder(
    private val binding: ItemCollectorAlbumOptionBinding,
    private val requestManager: RequestManager,
    onAlbumClick: (Int) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    private var current: CollectorAlbumPickerOption? = null
    private val selectedColor = ContextCompat.getColor(binding.root.context, R.color.vinilos_selected_row)
    private val unselectedColor = ContextCompat.getColor(binding.root.context, android.R.color.transparent)

    init {
        binding.root.setOnClickListener {
            current?.takeUnless { it.isAlreadyAdded }?.let { onAlbumClick(it.album.id) }
        }
    }

    fun bind(option: CollectorAlbumPickerOption) {
        current = option
        val context = binding.root.context
        val album = option.album
        val artist = album.performerName ?: context.getString(R.string.album_no_artist)
        val meta = context.getString(R.string.album_genre_year, album.genre.apiName, album.releaseDate.year)
        binding.albumTitle.text = album.name
        binding.albumSubtitle.text = artist
        binding.albumMeta.text = meta
        binding.addedChip.isVisible = option.isAlreadyAdded
        binding.radioIcon.isVisible = !option.isAlreadyAdded
        binding.radioIcon.setImageResource(
            if (option.isSelected) {
                R.drawable.bg_picker_radio_checked
            } else {
                R.drawable.bg_picker_radio_unchecked
            },
        )
        binding.root.isEnabled = !option.isAlreadyAdded
        binding.root.alpha = if (option.isAlreadyAdded) ALREADY_ADDED_ALPHA else 1f
        binding.root.setBackgroundColor(if (option.isSelected) selectedColor else unselectedColor)
        binding.root.contentDescription =
            context.getString(R.string.collector_album_picker_row_description, album.name, artist, meta)
        requestManager
            .load(album.coverUrl)
            .placeholder(R.drawable.img_album_cover)
            .error(R.drawable.img_album_cover)
            .centerCrop()
            .into(binding.albumArtwork.coverView)
    }

    fun unbind() {
        current = null
        requestManager.clear(binding.albumArtwork.coverView)
    }
}
