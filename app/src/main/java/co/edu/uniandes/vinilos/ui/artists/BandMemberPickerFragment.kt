package co.edu.uniandes.vinilos.ui.artists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
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
import co.edu.uniandes.vinilos.databinding.FragmentBandMemberPickerBinding
import co.edu.uniandes.vinilos.databinding.ItemBandMemberOptionBinding
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.ui.common.PrimaryActionHost
import co.edu.uniandes.vinilos.ui.common.keepHintForAccessibility
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.BandMemberFlowContract
import co.edu.uniandes.vinilos.viewmodels.BandMemberOption
import co.edu.uniandes.vinilos.viewmodels.BandMemberPickerEvent
import co.edu.uniandes.vinilos.viewmodels.BandMemberPickerUiState
import co.edu.uniandes.vinilos.viewmodels.BandMemberPickerViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager

/** The band detail opens this destination with the band identity and the ids of its current members. */
class BandMemberPickerFragment : Fragment(R.layout.fragment_band_member_picker) {
    private var bindingOrNull: FragmentBandMemberPickerBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: BandMemberPickerViewModel by viewModels { BandMemberPickerViewModel.Factory }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentBandMemberPickerBinding.bind(view)
        (activity as? PrimaryActionHost)?.hidePrimaryAction()
        bindHeaderAndInsets()
        // Created with the view and released in onDestroyView, so the adapter never outlives the RecyclerView.
        val adapter = BandMemberOptionAdapter(Glide.with(this)) { viewModel.onMusicianSelected(it) }
        binding.musicianList.layoutManager = LinearLayoutManager(requireContext())
        // Its height comes from the layout weight, not from the rows, so new lists do not re-measure it.
        binding.musicianList.setHasFixedSize(true)
        binding.musicianList.adapter = adapter
        binding.bandContext.text = getString(R.string.band_member_context, viewModel.bandName)
        binding.musicianSearch.keepHintForAccessibility()
        binding.musicianSearch.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        binding.addMember.setOnClickListener { viewModel.onAdd() }
        binding.errorState.retryButton.setOnClickListener { viewModel.retry() }
        viewModel.uiState.observe(viewLifecycleOwner) { render(it, adapter) }
        viewModel.events.observe(viewLifecycleOwner) { it.getContentIfNotHandled()?.let(::handle) }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            },
        )
    }

    override fun onDestroyView() {
        binding.musicianList.adapter = null
        bindingOrNull = null
        super.onDestroyView()
    }

    private fun bindHeaderAndInsets() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.band_member_picker_title)
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        header.headerNav.isVisible = true
        header.headerClose.isVisible = true
        header.headerClose.setBackgroundResource(R.drawable.bg_album_detail_back)
        header.headerNav.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginStart = resources.getDimensionPixelSize(R.dimen.album_detail_margin)
        }
        header.headerClose.setOnClickListener { close() }
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

    private fun render(
        state: BandMemberPickerUiState,
        adapter: BandMemberOptionAdapter,
    ) {
        binding.musicianSearch.isEnabled = !state.isSubmitting
        if (binding.musicianSearch.text.toString() != state.query) binding.musicianSearch.setText(state.query)
        binding.loadingState.root.isVisible = state.isLoading && state.musicians.isEmpty()
        binding.errorState.root.isVisible = state.loadError != null && state.musicians.isEmpty()
        binding.errorState.errorBody.setText(R.string.band_member_load_error)
        binding.emptyMessage.isVisible = state.isEmpty || state.hasNoSearchResults
        binding.emptyMessage.setText(if (state.isEmpty) R.string.band_member_empty else R.string.band_member_no_results)
        adapter.submitList(state.visibleRows)
        renderNotice(state)
        binding.addMember.isEnabled = state.canAdd || (state.isUncertain && !state.isSubmitting)
        binding.addMember.text =
            when {
                state.isUncertain -> getString(R.string.band_member_review_action)
                state.selectedMusician != null -> getString(R.string.band_member_add_action, state.bandName)
                else -> getString(R.string.band_member_select_action)
            }
        binding.headerBar.headerClose.isEnabled = !state.isSubmitting
        binding.headerBar.roleChip.isEnabled = !state.isSubmitting
    }

    // One notice for the one-band rule and for the submit errors; a live region reads it when it appears.
    private fun renderNotice(state: BandMemberPickerUiState) {
        val conflict = state.conflict
        val text =
            when {
                conflict?.bandName != null -> {
                    getString(R.string.band_member_conflict, conflict.musicianName, conflict.bandName)
                }

                conflict != null -> {
                    getString(R.string.band_member_conflict_unknown_band, conflict.musicianName)
                }

                state.isUncertain -> {
                    getString(R.string.band_member_uncertain)
                }

                state.submitError == DataError.Http(NOT_FOUND) -> {
                    getString(R.string.band_member_not_found)
                }

                state.submitError != null -> {
                    getString(R.string.band_member_add_error)
                }

                else -> {
                    null
                }
            }
        binding.memberNotice.isVisible = text != null
        if (text != null && binding.memberNotice.text.toString() != text) binding.memberNotice.text = text
    }

    private fun handle(event: BandMemberPickerEvent) {
        when (event) {
            is BandMemberPickerEvent.Added -> returnMember(event.member)
            BandMemberPickerEvent.ReviewBand -> close(requestRefresh = true)
            BandMemberPickerEvent.Close -> close()
            BandMemberPickerEvent.OpenRoleSelector -> findNavController().navigateToRoleSelector()
        }
    }

    private fun returnMember(member: PerformerSummary) {
        val result =
            bundleOf(
                BandMemberFlowContract.BAND_ID to viewModel.bandId,
                BandMemberFlowContract.MEMBER_ID to member.id,
                BandMemberFlowContract.MEMBER_NAME to member.name,
                BandMemberFlowContract.MEMBER_IMAGE to member.imageUrl,
            )
        findNavController().previousBackStackEntry?.savedStateHandle?.set(BandMemberFlowContract.MEMBER_ADDED, result)
        close()
    }

    private fun close(requestRefresh: Boolean = false) {
        val state = viewModel.uiState.value ?: return
        if (state.isSubmitting) return
        if (requestRefresh || state.isUncertain) {
            findNavController()
                .previousBackStackEntry
                ?.savedStateHandle
                ?.set(BandMemberFlowContract.REFRESH_REQUESTED, true)
        }
        findNavController().navigateUp()
    }

    companion object {
        private const val NOT_FOUND = 404

        fun argumentsFor(
            bandId: Int,
            bandName: String,
            memberIds: IntArray,
        ): Bundle =
            bundleOf(
                BandMemberFlowContract.BAND_ID to bandId,
                BandMemberFlowContract.BAND_NAME to bandName,
                BandMemberFlowContract.MEMBER_IDS to memberIds,
            )
    }
}

private class BandMemberOptionAdapter(
    private val requestManager: RequestManager,
    private val onSelect: (Int) -> Unit,
) : ListAdapter<BandMemberOption, BandMemberOptionHolder>(Diff) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BandMemberOptionHolder =
        BandMemberOptionHolder(
            ItemBandMemberOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            requestManager,
            onSelect,
        )

    override fun onBindViewHolder(
        holder: BandMemberOptionHolder,
        position: Int,
    ) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: BandMemberOptionHolder) = holder.unbind()

    private object Diff : DiffUtil.ItemCallback<BandMemberOption>() {
        override fun areItemsTheSame(
            oldItem: BandMemberOption,
            newItem: BandMemberOption,
        ): Boolean = oldItem.musician.id == newItem.musician.id

        override fun areContentsTheSame(
            oldItem: BandMemberOption,
            newItem: BandMemberOption,
        ): Boolean = oldItem == newItem
    }
}

/**
 * Receives the binding already inflated, sets its click listener once and clears the photo when recycled, so a row
 * off screen neither keeps the bitmap nor finishes a download nobody will see.
 */
private class BandMemberOptionHolder(
    private val binding: ItemBandMemberOptionBinding,
    private val requestManager: RequestManager,
    onSelect: (Int) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    private var current: BandMemberOption? = null

    init {
        binding.root.setOnClickListener { current?.takeUnless { it.isMember }?.let { onSelect(it.musician.id) } }
    }

    fun bind(option: BandMemberOption) {
        current = option
        val context = binding.root.context
        val musician = option.musician
        val born =
            context.getString(
                R.string.artist_overline,
                context.getString(R.string.artist_musician),
                musician.date.year.toString(),
            )
        val overline =
            option.otherBandName?.let {
                context.getString(R.string.artist_overline, born, context.getString(R.string.artist_member_of, it))
            } ?: born
        binding.musicianName.text = musician.name
        binding.musicianOverline.text = overline
        binding.alreadyMember.isVisible = option.isMember
        binding.musicianCheck.isVisible = !option.isMember
        // The selected state reaches the row background and the check mark through the row.
        binding.root.isSelected = option.isSelected
        binding.root.isEnabled = !option.isMember
        val description =
            if (option.isMember) R.string.band_member_row_member_description else R.string.band_member_row_description
        binding.root.contentDescription = context.getString(description, musician.name, overline)
        requestManager
            .load(musician.imageUrl)
            .placeholder(R.drawable.bg_performer_image_placeholder)
            .error(R.drawable.bg_performer_image_placeholder)
            .centerCrop()
            .into(binding.memberArtwork.performerImage)
    }

    fun unbind() {
        current = null
        requestManager.clear(binding.memberArtwork.performerImage)
    }
}
