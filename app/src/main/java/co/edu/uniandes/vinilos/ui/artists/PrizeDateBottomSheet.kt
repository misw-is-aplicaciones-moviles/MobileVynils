package co.edu.uniandes.vinilos.ui.artists

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentPrizeDateSheetBinding
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.viewmodels.PrizePickerUiState
import co.edu.uniandes.vinilos.viewmodels.PrizePickerViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Child of the picker: shares its workflow state instead of copying selected prize/date. */
class PrizeDateBottomSheet : BottomSheetDialogFragment(R.layout.fragment_prize_date_sheet) {
    private var bindingOrNull: FragmentPrizeDateSheetBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: PrizePickerViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { PrizePickerViewModel.Factory },
    )

    override fun getTheme(): Int = R.style.ThemeOverlay_Vinilos_BottomSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        paintWindowBackground(dialog)
        dialog.window?.setDimAmount(0f)
        dialog.setOnShowListener { showExpanded(dialog) }
        return dialog
    }

    // Scrim over the screen and, since API 35 ignores navigationBarColor, cream behind the navigation bar so the sheet
    // reaches the bottom edge as in the mockup. Below API 23 there are no root insets and the system paints the bar.
    private fun paintWindowBackground(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return
        val context = requireContext()
        val scrim = ColorDrawable(ContextCompat.getColor(context, R.color.vinilos_sheet_scrim))
        val cream = ColorDrawable(ContextCompat.getColor(context, R.color.vinilos_cream))
        window.setBackgroundDrawable(scrim)
        var painted = 0
        window.decorView.addOnLayoutChangeListener { decor, _, top, _, bottom, _, _, _, _ ->
            val navigation =
                ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            if (navigation != painted) {
                painted = navigation
                // A layer inset only takes effect with new bounds, so each size gets its own drawable.
                val background = LayerDrawable(arrayOf(scrim, cream))
                background.setLayerInset(1, 0, bottom - top - navigation, 0, 0)
                window.setBackgroundDrawable(background)
            }
        }
    }

    // Same setup as the track sheet: the shape of TrackSheetLayout is the only thing drawn, hump included.
    private fun showExpanded(dialog: BottomSheetDialog) {
        val sheet = dialog.findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.setBackgroundColor(Color.TRANSPARENT)
        sheet.clipChildren = false
        (sheet.parent as? ViewGroup)?.clipChildren = false
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentPrizeDateSheetBinding.bind(view)
        binding.closeSheet.setOnClickListener { if (viewModel.uiState.value?.isSubmitting != true) dismiss() }
        binding.prizeDate.setOnClickListener { openCalendar() }
        binding.savePrize.setOnClickListener { viewModel.onSave() }
        binding.reviewArtist.setOnClickListener { viewModel.onReviewArtist() }
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        @Suppress("UNCHECKED_CAST")
        val existing = childFragmentManager.findFragmentByTag(CALENDAR_TAG) as? MaterialDatePicker<Long>
        existing?.let(::bindCalendar)
    }

    override fun onDestroyView() {
        bindingOrNull = null
        super.onDestroyView()
    }

    private fun render(state: PrizePickerUiState) {
        binding.sheetContext.text =
            getString(R.string.prize_date_context, state.selectedPrize?.name.orEmpty(), viewModel.performerName)
        binding.prizeDate.text = state.date?.format(DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault()))
            ?: getString(R.string.prize_date_hint)
        binding.prizeDate.isEnabled = !state.isSubmitting && !state.isUncertain
        binding.dateError.isVisible = state.dateRequired
        binding.savePrize.isEnabled = state.canSubmit
        binding.savePrize.setText(if (state.isSubmitting) R.string.prize_assigning else R.string.prize_assign_action)
        binding.closeSheet.isEnabled = !state.isSubmitting
        isCancelable = !state.isSubmitting
        binding.reviewArtist.isVisible = state.isUncertain
        binding.submitError.isVisible = state.submitError != null || state.isUncertain
        binding.submitError.setText(
            when {
                state.isUncertain -> R.string.prize_assign_uncertain
                (state.submitError as? DataError.Http)?.code == HTTP_NOT_FOUND -> R.string.prize_assign_missing
                else -> R.string.prize_assign_error
            },
        )
    }

    private fun openCalendar() {
        if (childFragmentManager.findFragmentByTag(CALENDAR_TAG) != null) return
        val builder = MaterialDatePicker.Builder.datePicker().setTitleText(R.string.prize_date_title)
        viewModel.uiState.value
            ?.date
            ?.let { builder.setSelection(TimeUnit.DAYS.toMillis(it.toEpochDay())) }
        builder.build().also(::bindCalendar).show(childFragmentManager, CALENDAR_TAG)
    }

    private fun bindCalendar(picker: MaterialDatePicker<Long>) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener { millis ->
            viewModel.onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }

    companion object {
        const val TAG = "prizeDateSheet"
        private const val CALENDAR_TAG = "premiationDatePicker"
        private const val DATE_PATTERN = "dd / MM / yyyy"
        private const val HTTP_NOT_FOUND = 404
    }
}
