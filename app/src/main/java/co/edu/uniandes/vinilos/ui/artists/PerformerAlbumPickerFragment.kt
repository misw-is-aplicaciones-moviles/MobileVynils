package co.edu.uniandes.vinilos.ui.artists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
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
import co.edu.uniandes.vinilos.databinding.FragmentPerformerAlbumPickerBinding
import co.edu.uniandes.vinilos.databinding.ItemPerformerAlbumOptionBinding
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.keepHintForAccessibility
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.AlbumPickerBlock
import co.edu.uniandes.vinilos.viewmodels.AlbumPickerNotice
import co.edu.uniandes.vinilos.viewmodels.AlbumPickerPhase
import co.edu.uniandes.vinilos.viewmodels.PerformerAlbumPickerEvent
import co.edu.uniandes.vinilos.viewmodels.PerformerAlbumPickerOption
import co.edu.uniandes.vinilos.viewmodels.PerformerAlbumPickerUiState
import co.edu.uniandes.vinilos.viewmodels.PerformerAlbumPickerViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager

class PerformerAlbumPickerFragment : Fragment(R.layout.fragment_performer_album_picker) {
    private var bindingOrNull: FragmentPerformerAlbumPickerBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: PerformerAlbumPickerViewModel by viewModels { PerformerAlbumPickerViewModel.Factory }
    private lateinit var adapter: PerformerAlbumPickerAdapter

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentPerformerAlbumPickerBinding.bind(view)
        (activity as? PrimaryActionHost)?.hidePrimaryAction()
        bindHeader()
        adapter = PerformerAlbumPickerAdapter(Glide.with(this), viewModel::onAlbumSelected)
        binding.optionList.layoutManager = LinearLayoutManager(requireContext())
        binding.optionList.setHasFixedSize(true)
        // Default item animations reset root alpha and erase the unavailable-row state.
        binding.optionList.itemAnimator = null
        binding.optionList.adapter = adapter
        binding.searchField.setText(
            viewModel.uiState.value
                ?.query
                .orEmpty(),
        )
        binding.searchField.keepHintForAccessibility()
        binding.searchField.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.confirmButton.setOnClickListener { viewModel.onConfirm() }
        binding.errorState.retryButton.setOnClickListener { viewModel.retry() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { viewModel.onClose() }
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { it.getContentIfNotHandled()?.let(::handle) }
    }

    override fun onDestroyView() {
        binding.optionList.adapter = null
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
        header.headerClose.setOnClickListener { viewModel.onClose() }
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
            target.updatePadding(
                bottom =
                    padding +
                        insets
                            .getInsets(
                                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime(),
                            ).bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun render(state: PerformerAlbumPickerUiState) {
        val content = state.phase != AlbumPickerPhase.LOADING && state.phase != AlbumPickerPhase.LOAD_ERROR
        adapter.locked = state.isLocked
        adapter.submitList(state.options)
        binding.optionList.isVisible = content && state.options.isNotEmpty()
        binding.searchField.isEnabled = content && !state.isLocked
        binding.pickerContext.text = getString(R.string.artist_album_context, state.performerName)
        binding.loadingState.root.isVisible = state.phase == AlbumPickerPhase.LOADING
        binding.pickerStatus.isVisible = state.phase == AlbumPickerPhase.LOAD_ERROR
        binding.errorState.errorTitle.setText(R.string.artist_album_picker_error)
        binding.errorState.errorBody.setText(R.string.artist_album_picker_error_body)
        binding.errorState.retryButton.isVisible = viewModel.performerType != null && viewModel.performerId > 0
        binding.emptyMessage.isVisible = content && state.options.isEmpty()
        binding.emptyMessage.setText(
            if (state.query.isBlank()) {
                R.string.collector_album_picker_empty
            } else {
                R.string.collector_album_picker_no_results
            },
        )
        binding.headerBar.headerClose.isEnabled = !state.isBusy
        binding.headerBar.roleChip.isEnabled = !state.isBusy
        renderAction(state)
    }

    private fun renderAction(state: PerformerAlbumPickerUiState) {
        binding.confirmButton.isEnabled = state.canConfirm || state.canCheck
        binding.confirmButton.text =
            when {
                state.isBusy -> {
                    getString(R.string.artist_album_busy)
                }

                state.canCheck -> {
                    getString(R.string.artist_album_check)
                }

                state.selectedAlbum != null -> {
                    getString(R.string.artist_album_confirm, state.selectedAlbum?.name)
                }

                else -> {
                    getString(R.string.collector_album_picker_select)
                }
            }
        val notice =
            when (state.notice) {
                AlbumPickerNotice.UNAVAILABLE -> R.string.artist_album_unavailable
                AlbumPickerNotice.NOT_SAVED -> R.string.artist_album_not_saved
                AlbumPickerNotice.CHECK_FAILED, AlbumPickerNotice.UNCERTAIN -> R.string.artist_album_uncertain
                AlbumPickerNotice.READ_FAILED -> R.string.artist_album_read_failed
                null -> null
            }
        binding.notice.isVisible = notice != null
        notice?.let(binding.notice::setText)
    }

    private fun handle(event: PerformerAlbumPickerEvent) {
        val nav = findNavController()
        val result = nav.previousBackStackEntry?.savedStateHandle
        when (event) {
            is PerformerAlbumPickerEvent.Associated -> {
                result?.set(
                    PerformerAlbumPickerViewModel.ALBUM_ASSOCIATED,
                    bundleOf(
                        PerformerAlbumPickerViewModel.PERFORMER_ID to viewModel.performerId,
                        PerformerAlbumPickerViewModel.PERFORMER_TYPE to viewModel.performerType?.name,
                        PerformerAlbumPickerViewModel.ALBUM_ID to event.albumId,
                        PerformerAlbumPickerViewModel.ALREADY_ASSOCIATED to event.alreadyAssociated,
                    ),
                )
                nav.navigateUp()
            }

            is PerformerAlbumPickerEvent.Closed -> {
                if (event.refreshRequested) result?.set(PerformerAlbumPickerViewModel.REFRESH_REQUESTED, true)
                nav.navigateUp()
            }

            PerformerAlbumPickerEvent.OpenRoleSelector -> {
                nav.navigateToRoleSelector()
            }
        }
    }

    companion object {
        fun argumentsFor(
            id: Int,
            type: PerformerType,
        ): Bundle =
            bundleOf(
                PerformerAlbumPickerViewModel.PERFORMER_ID to id,
                PerformerAlbumPickerViewModel.PERFORMER_TYPE to type.name,
            )
    }
}

/** HU15 album rows; availability rules remain in the ViewModel. */
private class PerformerAlbumPickerAdapter(
    private val images: RequestManager,
    private val onSelected: (Int) -> Unit,
) : ListAdapter<PerformerAlbumPickerOption, PerformerAlbumPickerAdapter.Holder>(Diff) {
    var locked: Boolean = false

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder = Holder(ItemPerformerAlbumOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: Holder) = holder.clear()

    inner class Holder(
        private val binding: ItemPerformerAlbumOptionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var option: PerformerAlbumPickerOption? = null

        init {
            binding.root.setOnClickListener {
                option?.takeIf { !locked && it.block == null }?.let { onSelected(it.album.id) }
            }
        }

        fun bind(row: PerformerAlbumPickerOption) {
            option = row
            val context = binding.root.context
            val album = row.album
            val metadata = context.getString(R.string.album_genre_year, album.genre.apiName, album.releaseDate.year)
            val artist = album.performerName.orEmpty().ifEmpty { context.getString(R.string.album_no_artist) }
            val status =
                when (row.block) {
                    AlbumPickerBlock.ALREADY_ADDED -> R.string.collector_album_picker_already_added
                    AlbumPickerBlock.ASSIGNED_ELSEWHERE -> R.string.artist_album_elsewhere
                    null -> if (row.isSelected) R.string.picker_selected else R.string.picker_available
                }
            binding.albumTitle.text = album.name
            binding.albumSubtitle.text = artist
            binding.albumMeta.text = metadata
            binding.addedChip.isVisible = row.block == AlbumPickerBlock.ALREADY_ADDED
            binding.radioIcon.isVisible = row.block == null
            binding.radioIcon.setImageResource(
                if (row.isSelected) R.drawable.bg_picker_radio_checked else R.drawable.bg_picker_radio_unchecked,
            )
            binding.root.isClickable = row.block == null
            binding.root.isSelected = row.isSelected
            binding.root.setBackgroundResource(R.drawable.bg_picker_option)
            // Only the cover fades: the title, the artist and the chip of a blocked album must stay readable.
            binding.albumArtwork.alpha = if (row.block == null) 1f else BLOCKED_ALPHA
            binding.root.contentDescription =
                context.getString(
                    R.string.picker_option_description,
                    album.name,
                    artist,
                    metadata,
                    context.getString(status),
                )
            ViewCompat.setStateDescription(binding.root, context.getString(status))
            images
                .load(album.coverUrl)
                .placeholder(R.drawable.img_album_cover)
                .error(R.drawable.img_album_cover)
                .centerCrop()
                .into(binding.albumArtwork.coverView)
        }

        fun clear() {
            option = null
            images.clear(binding.albumArtwork.coverView)
        }
    }

    private object Diff : DiffUtil.ItemCallback<PerformerAlbumPickerOption>() {
        override fun areItemsTheSame(
            oldItem: PerformerAlbumPickerOption,
            newItem: PerformerAlbumPickerOption,
        ): Boolean = oldItem.album.id == newItem.album.id

        override fun areContentsTheSame(
            oldItem: PerformerAlbumPickerOption,
            newItem: PerformerAlbumPickerOption,
        ): Boolean = oldItem == newItem
    }

    private companion object {
        const val BLOCKED_ALPHA = 0.5f
    }
}
