package co.edu.uniandes.vinilos.ui.albums

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentAlbumCreateBinding
import co.edu.uniandes.vinilos.databinding.ItemChoiceChipBinding
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.RecordLabel
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector
import co.edu.uniandes.vinilos.viewmodels.AlbumCreateEvent
import co.edu.uniandes.vinilos.viewmodels.AlbumCreateUiState
import co.edu.uniandes.vinilos.viewmodels.AlbumCreateViewModel
import co.edu.uniandes.vinilos.viewmodels.AlbumForm
import co.edu.uniandes.vinilos.viewmodels.AlbumFormErrors
import co.edu.uniandes.vinilos.viewmodels.FieldError
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialFadeThrough
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * New album form. Forwards what the collector types to the ViewModel, draws its state and
 * reacts to its events. On success it tells the catalog through the back stack and closes.
 */
class AlbumCreateFragment : Fragment(R.layout.fragment_album_create) {
    private var bindingOrNull: FragmentAlbumCreateBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: AlbumCreateViewModel by viewModels { AlbumCreateViewModel.Factory }
    private val dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.ROOT)

    // Last URL sent to Glide, so a redraw that does not change the URL does not reload the cover.
    private var loadedCoverUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
        exitTransition = MaterialFadeThrough().setDuration(TRANSITION_MILLIS)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentAlbumCreateBinding.bind(view)
        loadedCoverUrl = null
        bindHeader()
        bindInputs()
        bindChips()
        bindActions()
        // A picker restored after a rotation lost its listener with the old fragment.
        findDatePicker()?.let(::listenToDatePicker)
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let(::handle)
        }
        ViewCompat.requestApplyInsets(binding.headerBar.header)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
    }

    private fun bindHeader() {
        val header = binding.headerBar
        header.headerTitle.setText(R.string.create_album_title)
        ViewCompat.setAccessibilityHeading(header.headerTitle, true)
        ViewCompat.setOnApplyWindowInsetsListener(header.header) { bar, insets ->
            bar.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        header.headerNav.isVisible = true
        header.headerClose.isVisible = true
        header.headerClose.setOnClickListener { viewModel.onCancel() }
        header.roleChip.setOnClickListener { viewModel.onRoleChipClicked() }
    }

    private fun bindInputs() {
        binding.nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.coverInput.doAfterTextChanged { viewModel.onCoverChanged(it?.toString().orEmpty()) }
        binding.descriptionInput.doAfterTextChanged { viewModel.onDescriptionChanged(it?.toString().orEmpty()) }
        binding.dateInput.setOnClickListener { openDatePicker() }
        binding.dateLayout.setEndIconOnClickListener { openDatePicker() }
    }

    private fun bindChips() {
        addChips(binding.genreChips, Genre.entries.filter { it != Genre.UNKNOWN }) { it.apiName }
        addChips(binding.recordLabelChips, RecordLabel.entries) { it.apiName }
        binding.genreChips.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.onGenreSelected(group.checkedValue(checkedIds) as? Genre)
        }
        binding.recordLabelChips.setOnCheckedStateChangeListener { group, checkedIds ->
            viewModel.onRecordLabelSelected(group.checkedValue(checkedIds) as? RecordLabel)
        }
    }

    // One chip per value; the value travels in the tag. The checked state comes from the ViewModel, not from the view.
    private fun <T : Enum<T>> addChips(
        group: ChipGroup,
        values: List<T>,
        label: (T) -> String,
    ) {
        values.forEach { value ->
            val chip = ItemChoiceChipBinding.inflate(layoutInflater, group, false).root
            chip.id = View.generateViewId()
            chip.text = label(value)
            chip.tag = value
            chip.isSaveEnabled = false
            group.addView(chip)
        }
    }

    private fun ChipGroup.checkedValue(checkedIds: List<Int>): Any? =
        checkedIds.firstOrNull()?.let { findViewById<Chip>(it)?.tag }

    private fun bindActions() {
        binding.cancelButton.setOnClickListener { viewModel.onCancel() }
        binding.createButton.setOnClickListener { viewModel.onSubmit() }
        val baseBottom = binding.actions.paddingBottom
        // The bottom bar is hidden here, so the buttons take the navigation bar and the keyboard themselves.
        ViewCompat.setOnApplyWindowInsetsListener(binding.actions) { actions, insets ->
            val bottom =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()).bottom
            actions.updatePadding(bottom = baseBottom + bottom)
            insets
        }
    }

    private fun render(state: AlbumCreateUiState) {
        renderRole(state.role)
        renderForm(state.form)
        renderErrors(state.errors)
        binding.formScroll.alpha = if (state.isSubmitting) SUBMITTING_ALPHA else 1f
        binding.createButton.isEnabled = !state.isSubmitting
    }

    private fun renderRole(role: UserRole?) {
        val chip = binding.headerBar.roleChip
        chip.isVisible = role != null
        if (role == null) return
        val label = if (role == UserRole.VISITOR) R.string.role_visitor else R.string.role_collector
        chip.setText(label)
        chip.contentDescription = getString(R.string.role_chip_action, getString(label))
    }

    private fun renderForm(form: AlbumForm) {
        binding.nameInput.setTextIfChanged(form.name)
        binding.coverInput.setTextIfChanged(form.coverUrl)
        binding.descriptionInput.setTextIfChanged(form.description)
        binding.dateInput.setTextIfChanged(form.releaseDate?.format(dateFormatter).orEmpty())
        binding.genreChips.checkValue(form.genre)
        binding.recordLabelChips.checkValue(form.recordLabel)
        renderCover(form.coverUrl.trim())
    }

    // Writing the same text again would move the cursor and fire the watcher in a loop.
    private fun EditText.setTextIfChanged(value: String) {
        if (text?.toString() != value) setText(value)
    }

    private fun ChipGroup.checkValue(value: Any?) {
        val chip = children.filterIsInstance<Chip>().firstOrNull { it.tag == value }
        when {
            chip == null && checkedChipIds.isNotEmpty() -> clearCheck()
            chip != null && !chip.isChecked -> chip.isChecked = true
        }
    }

    private fun renderCover(url: String) {
        if (url == loadedCoverUrl) return
        loadedCoverUrl = url
        Glide
            .with(this)
            .load(url.ifEmpty { null })
            .placeholder(R.drawable.bg_cover_placeholder)
            .error(R.drawable.bg_cover_placeholder)
            .centerCrop()
            .into(binding.coverPreview.coverView)
    }

    private fun renderErrors(errors: AlbumFormErrors) {
        binding.nameLayout.showError(errors.name)
        binding.coverLayout.showError(errors.coverUrl)
        binding.dateLayout.showError(errors.releaseDate)
        binding.descriptionLayout.showError(errors.description)
        binding.genreError.isVisible = errors.genre != null
        binding.genreError.text = errors.genre?.let(::errorText)
        binding.recordLabelError.isVisible = errors.recordLabel != null
        binding.recordLabelError.text = errors.recordLabel?.let(::errorText)
    }

    // Disabling the error when there is none frees the space it reserves under the field.
    private fun TextInputLayout.showError(error: FieldError?) {
        if (error == null) {
            isErrorEnabled = false
        } else {
            this.error = errorText(error)
        }
    }

    private fun errorText(error: FieldError): CharSequence {
        @StringRes val message =
            when (error) {
                FieldError.REQUIRED -> R.string.error_required
                FieldError.MISSING_GENRE -> R.string.error_select_genre
                FieldError.MISSING_RECORD_LABEL -> R.string.error_select_label
            }
        val icon = checkNotNull(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_warning))
        val size = resources.getDimensionPixelSize(R.dimen.field_error_icon)
        icon.setBounds(0, 0, size, size)
        return SpannableStringBuilder()
            .append(ICON_PLACEHOLDER, ImageSpan(icon, ImageSpan.ALIGN_BASELINE), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            .append(ICON_GAP)
            .append(getString(message))
    }

    private fun openDatePicker() {
        if (findDatePicker() != null) return
        val selection =
            viewModel.uiState.value
                ?.form
                ?.releaseDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli()
                ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker =
            MaterialDatePicker.Builder
                .datePicker()
                .setTitleText(R.string.date_picker_title)
                .setPositiveButtonText(R.string.action_accept)
                .setNegativeButtonText(R.string.action_cancel)
                .setSelection(selection)
                .setTheme(R.style.ThemeOverlay_Vinilos_DatePicker)
                .build()
        listenToDatePicker(picker)
        picker.show(childFragmentManager, DATE_PICKER_TAG)
    }

    @Suppress("UNCHECKED_CAST") // Only this fragment adds a picker with this tag, always a MaterialDatePicker<Long>.
    private fun findDatePicker(): MaterialDatePicker<Long>? =
        childFragmentManager.findFragmentByTag(DATE_PICKER_TAG) as? MaterialDatePicker<Long>

    // The picker answers in UTC milliseconds; reading them in UTC keeps the chosen day in UTC-5.
    private fun listenToDatePicker(picker: MaterialDatePicker<Long>) {
        picker.addOnPositiveButtonClickListener { millis ->
            viewModel.onReleaseDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }

    private fun handle(event: AlbumCreateEvent) {
        when (event) {
            AlbumCreateEvent.Created -> {
                val navController = findNavController()
                navController.previousBackStackEntry?.savedStateHandle?.set(RESULT_ALBUM_CREATED, true)
                navController.popBackStack()
            }

            AlbumCreateEvent.Cancelled -> {
                findNavController().popBackStack()
            }

            is AlbumCreateEvent.Failed -> {
                Snackbar
                    .make(binding.root, R.string.create_album_error, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.actions)
                    .show()
            }

            AlbumCreateEvent.OpenRoleSelector -> {
                findNavController().navigateToRoleSelector()
            }
        }
    }

    companion object {
        /** Key the catalog reads from its back stack entry to know an album was just created. */
        const val RESULT_ALBUM_CREATED = "album_created"

        private const val TRANSITION_MILLIS = 300L
        private const val DATE_PICKER_TAG = "releaseDatePicker"
        private const val DATE_PATTERN = "dd / MM / yyyy"
        private const val SUBMITTING_ALPHA = 0.5f
        private const val ICON_PLACEHOLDER = " "
        private const val ICON_GAP = "  "
    }
}
