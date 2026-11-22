package co.edu.uniandes.vinilos.ui.albums

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentCommentCreateSheetBinding
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.ui.common.InitialsAvatarDrawable
import co.edu.uniandes.vinilos.viewmodels.CommentCollectorOption
import co.edu.uniandes.vinilos.viewmodels.CommentCreateEvent
import co.edu.uniandes.vinilos.viewmodels.CommentCreateUiState
import co.edu.uniandes.vinilos.viewmodels.CommentCreateViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

/** Hoja "Comentar álbum" (HU09): quién comenta, la valoración y el texto del álbum recibido en sus argumentos. */
class CommentCreateBottomSheet : BottomSheetDialogFragment(R.layout.fragment_comment_create_sheet) {
    private var bindingOrNull: FragmentCommentCreateSheetBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: CommentCreateViewModel by viewModels { CommentCreateViewModel.Factory }

    // The chips are rebuilt only when the list itself changes, not on every selection or keystroke.
    private var renderedCollectors: List<CommentCollectorOption>? = null

    // The five stars, looked up once per view instead of on every render.
    private var stars: List<ImageButton> = emptyList()

    // Last state drawn in this view. Typing changes only the text, so the stars, the rating label, the errors
    // and the buttons are redrawn only when their own inputs change. Null means the next render draws everything.
    private var renderedState: CommentCreateUiState? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_Vinilos_BottomSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply { configureAlbumSheet(requireContext()) }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentCommentCreateSheetBinding.bind(view)
        ViewCompat.setAccessibilityHeading(binding.sheetTitle, true)
        stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        renderedState = null
        binding.sheetSubtitle.text = requireArguments().getString(ARG_ALBUM_NAME).orEmpty()
        bindStars()
        bindFields()
        bindAlbumSheetInsets(binding.sheetBody)
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (val content = event.getContentIfNotHandled()) {
                is CommentCreateEvent.Created -> onCommentCreated(content.comment)
                null -> Unit
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        renderedCollectors = null
        renderedState = null
        stars = emptyList()
        bindingOrNull = null
    }

    private fun bindStars() {
        stars.forEachIndexed { index, star ->
            val rating = index + 1
            star.contentDescription = getString(R.string.comment_star_description, rating)
            star.setOnClickListener {
                hideKeyboard(binding.root)
                viewModel.onRatingSelected(rating)
            }
        }
    }

    private fun bindFields() {
        binding.closeSheet.setOnClickListener { dismiss() }
        binding.cancelComment.setOnClickListener { dismiss() }
        binding.publishComment.setOnClickListener {
            hideKeyboard(binding.root)
            viewModel.onPublish()
        }
        binding.retryCollectors.setOnClickListener { viewModel.retryCollectors() }
        binding.commentField.doAfterTextChanged { text ->
            viewModel.onDescriptionChanged(text?.toString().orEmpty())
        }
    }

    private fun render(state: CommentCreateUiState) {
        val previous = renderedState
        renderedState = state
        val redraw = CommentSheetRedraw.between(previous, state)
        renderCollectors(state)
        if (redraw.ratingLabel) renderRatingLabel(state)
        if (redraw.stars) renderStars(state)
        renderDescription(state)
        if (redraw.errors) renderErrors(state)
        if (redraw.submitError) renderSubmitError(state.submitError)
        if (redraw.submitting) renderSubmitting(state.isSubmitting)
    }

    private fun renderErrors(state: CommentCreateUiState) {
        binding.collectorError.isVisible = state.isCollectorMissing
        binding.ratingError.isVisible = state.isRatingMissing
        binding.descriptionError.isVisible = state.isDescriptionMissing
        binding.commentField.setBackgroundResource(
            if (state.isDescriptionMissing) R.drawable.bg_comment_field_error else R.drawable.bg_comment_field,
        )
    }

    private fun renderSubmitError(error: DataError?) {
        binding.submitError.isVisible = error != null
        error?.let { binding.submitError.setText(submitErrorMessage(it)) }
    }

    private fun renderSubmitting(isSubmitting: Boolean) {
        val editable = !isSubmitting
        binding.publishComment.isEnabled = editable
        binding.publishComment.setText(if (editable) R.string.action_publish_comment else R.string.comment_publishing)
        binding.cancelComment.isEnabled = editable
        binding.closeSheet.isEnabled = editable
        binding.commentField.isEnabled = editable
        binding.retryCollectors.isEnabled = editable
        isCancelable = editable
    }

    private fun renderCollectors(state: CommentCreateUiState) {
        val group = binding.collectorChips
        if (state.collectors !== renderedCollectors) {
            renderedCollectors = state.collectors
            group.removeAllViews()
            val inflater = LayoutInflater.from(group.context)
            state.collectors.forEach { option ->
                val chip = inflater.inflate(R.layout.item_collector_chip, group, false) as Chip
                chip.id = View.generateViewId()
                chip.text = option.name
                chip.chipIcon = InitialsAvatarDrawable(chip.context, option.initials)
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) viewModel.onCollectorSelected(option.id)
                }
                chip.tag = option.id
                group.addView(chip)
            }
        }
        for (index in 0 until group.childCount) {
            val chip = group.getChildAt(index) as Chip
            val isSelected = chip.tag == state.selectedCollectorId
            if (chip.isChecked != isSelected) chip.isChecked = isSelected
            chip.isEnabled = !state.isSubmitting
        }
        val showNotice = state.collectors.isEmpty() && (state.isLoadingCollectors || state.collectorsError != null)
        binding.collectorsNotice.isVisible = showNotice
        binding.retryCollectors.isVisible = state.collectorsError != null
        val failed = state.collectorsError != null
        binding.collectorsStatus.setText(
            if (failed) R.string.comment_collectors_error else R.string.comment_collectors_loading,
        )
    }

    private fun renderRatingLabel(state: CommentCreateUiState) {
        binding.ratingLabel.text =
            if (state.hasRating) {
                getString(R.string.comment_rating_value, state.rating)
            } else {
                getString(R.string.comment_rating)
            }
    }

    private fun renderStars(state: CommentCreateUiState) {
        stars.forEachIndexed { index, star ->
            val position = index + 1
            star.setImageResource(if (position <= state.rating) R.drawable.ic_star else R.drawable.ic_star_outline)
            // TalkBack says "seleccionado" on the chosen star; the label above also says the value, not only the color.
            star.isSelected = position == state.rating
            star.isEnabled = !state.isSubmitting
        }
    }

    private fun renderDescription(state: CommentCreateUiState) {
        val field = binding.commentField
        if (field.text.toString() != state.description) {
            field.setText(state.description)
            field.setSelection(state.description.length)
        }
    }

    private fun submitErrorMessage(error: DataError): Int =
        when (error) {
            DataError.Network -> R.string.comment_create_network_error
            is DataError.Http -> R.string.comment_create_error
            DataError.Serialization -> R.string.comment_create_error
            is DataError.Unknown -> R.string.comment_create_error
        }

    private fun onCommentCreated(comment: Comment) {
        val author = comment.author ?: return
        parentFragmentManager.setFragmentResult(
            RESULT_COMMENT_CREATED,
            bundleOf(
                RESULT_ALBUM_ID to requireArguments().getInt(CommentCreateViewModel.ARG_ALBUM_ID),
                RESULT_COMMENT_ID to comment.id,
                RESULT_DESCRIPTION to comment.description,
                RESULT_RATING to comment.rating,
                RESULT_COLLECTOR_ID to author.collectorId,
                RESULT_COLLECTOR_NAME to author.name,
            ),
        )
        dismiss()
    }

    companion object {
        const val TAG = "comment-create"
        const val RESULT_COMMENT_CREATED = "comment_created"
        const val RESULT_ALBUM_ID = "comment_created_album_id"
        const val RESULT_COMMENT_ID = "comment_created_id"
        const val RESULT_DESCRIPTION = "comment_created_description"
        const val RESULT_RATING = "comment_created_rating"
        const val RESULT_COLLECTOR_ID = "comment_created_collector_id"
        const val RESULT_COLLECTOR_NAME = "comment_created_collector_name"

        private const val ARG_ALBUM_NAME = "albumName"

        fun newInstance(album: Album): CommentCreateBottomSheet =
            CommentCreateBottomSheet().apply {
                arguments =
                    bundleOf(
                        CommentCreateViewModel.ARG_ALBUM_ID to album.id,
                        ARG_ALBUM_NAME to album.name,
                    )
            }
    }
}
