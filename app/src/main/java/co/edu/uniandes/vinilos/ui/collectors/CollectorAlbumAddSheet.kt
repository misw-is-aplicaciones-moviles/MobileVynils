package co.edu.uniandes.vinilos.ui.collectors

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentCollectorAlbumAddSheetBinding
import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.viewmodels.CollectorAlbumAddEvent
import co.edu.uniandes.vinilos.viewmodels.CollectorAlbumAddUiState
import co.edu.uniandes.vinilos.viewmodels.CollectorAlbumAddViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** HU11: price and status for adding an album to a collector's collection. */
class CollectorAlbumAddSheet : BottomSheetDialogFragment(R.layout.fragment_collector_album_add_sheet) {
    private var bindingOrNull: FragmentCollectorAlbumAddSheetBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: CollectorAlbumAddViewModel by viewModels { CollectorAlbumAddViewModel.Factory }

    // Built once per view and released with it: render runs on every digit and only reads them.
    private var keypad: List<View> = emptyList()
    private var colors: SheetColors? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_Vinilos_BottomSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getColor(requireContext(), R.color.vinilos_sheet_scrim).toDrawable(),
        )
        dialog.window?.setDimAmount(0f)
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN,
        )
        dialog.setOnShowListener { showExpanded(dialog) }
        return dialog
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        bindingOrNull = FragmentCollectorAlbumAddSheetBinding.bind(view)
        colors = SheetColors.from(requireContext())
        bindSubtitle()
        bindKeypad()
        bindFields()
        bindInsets()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (event.getContentIfNotHandled()) {
                CollectorAlbumAddEvent.Added -> onAdded()
                null -> Unit
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        keypad = emptyList()
        colors = null
        bindingOrNull = null
    }

    private fun bindSubtitle() {
        val name = viewModel.albumName
        val artist = viewModel.artistName
        binding.sheetSubtitle.text =
            if (artist.isNotEmpty()) "$name · $artist" else name
    }

    private fun bindKeypad() {
        val digits =
            listOf(
                binding.key1,
                binding.key2,
                binding.key3,
                binding.key4,
                binding.key5,
                binding.key6,
                binding.key7,
                binding.key8,
                binding.key9,
            )
        digits.forEachIndexed { index, key ->
            key.text = (index + 1).toString()
            key.setOnClickListener { onDigit(index + 1) }
        }
        binding.key0.text = "0"
        binding.key0.setOnClickListener { onDigit(0) }
        binding.keyClear.setOnClickListener { onDigitAction { viewModel.onClearPrice() } }
        binding.keyDelete.setOnClickListener { onDigitAction { viewModel.onDeleteDigit() } }
        keypad = digits + listOf(binding.key0, binding.keyClear, binding.keyDelete)
    }

    private fun bindFields() {
        binding.closeSheet.setOnClickListener { dismiss() }
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.addButton.setOnClickListener { viewModel.onSave() }
        binding.statusAvailable.setOnClickListener { viewModel.onStatusSelected(AlbumStatus.ACTIVE) }
        binding.statusUnavailable.setOnClickListener { viewModel.onStatusSelected(AlbumStatus.INACTIVE) }
    }

    private fun bindInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.sheetBody) { sheet, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val shadow = resources.getDimensionPixelSize(R.dimen.track_sheet_shadow_space)
            val bottom = resources.getDimensionPixelSize(R.dimen.space_xl)
            sheet.updatePadding(top = shadow, bottom = navigation + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.sheetBody)
    }

    private fun render(state: CollectorAlbumAddUiState) {
        val colors = checkNotNull(colors)
        binding.priceDisplay.text = state.priceText
        binding.priceDisplay.setTextColor(if (state.priceDigits > 0) colors.cream else colors.emptyPrice)
        binding.priceError.isVisible = state.isPriceInvalid
        binding.statusError.isVisible = state.isStatusMissing
        binding.submitError.isVisible = state.submitError != null
        state.submitError?.let { binding.submitError.setText(submitErrorMessage(it)) }

        val available = state.status == AlbumStatus.ACTIVE
        val unavailable = state.status == AlbumStatus.INACTIVE
        binding.statusAvailable.setBackgroundColor(if (available) colors.charcoal else colors.transparent)
        binding.statusAvailable.setTextColor(if (available) colors.cream else colors.charcoal)
        binding.statusUnavailable.setBackgroundColor(if (unavailable) colors.charcoal else colors.transparent)
        binding.statusUnavailable.setTextColor(if (unavailable) colors.cream else colors.charcoal)

        binding.addButton.isEnabled = !state.isSubmitting
        binding.addButton.setText(
            if (state.isSubmitting) {
                R.string.collector_album_add_submitting
            } else {
                R.string.collector_album_add_submit
            },
        )
        binding.cancelButton.isEnabled = !state.isSubmitting
        binding.closeSheet.isEnabled = !state.isSubmitting
        keypad.forEach { it.isEnabled = !state.isSubmitting }
        binding.statusAvailable.isEnabled = !state.isSubmitting
        binding.statusUnavailable.isEnabled = !state.isSubmitting
        isCancelable = !state.isSubmitting
    }

    private fun submitErrorMessage(error: DataError): Int =
        when (error) {
            DataError.Network -> R.string.collector_album_add_network_error
            is DataError.Http -> R.string.collector_album_add_error
            DataError.Serialization -> R.string.collector_album_add_error
            is DataError.Unknown -> R.string.collector_album_add_error
        }

    private fun onDigit(digit: Int) {
        onDigitAction { viewModel.onDigit(digit) }
    }

    private fun onDigitAction(action: () -> Unit) {
        hideKeyboard()
        action()
    }

    private fun onAdded() {
        parentFragmentManager.setFragmentResult(
            RESULT_ALBUM_ADDED,
            bundleOf(
                RESULT_COLLECTOR_ID to viewModel.collectorId,
                RESULT_ALBUM_ID to viewModel.albumId,
                RESULT_ALBUM_NAME to viewModel.albumName,
            ),
        )
        dismiss()
    }

    private fun hideKeyboard() {
        val token = binding.root.windowToken ?: return
        val manager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(token, 0)
    }

    private fun showExpanded(dialog: BottomSheetDialog) {
        val sheet = dialog.findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.setBackgroundColor(Color.TRANSPARENT)
        sheet.clipChildren = false
        (sheet.parent as? ViewGroup)?.clipChildren = false
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /** The sheet's colors, resolved once per view instead of on every render. */
    private class SheetColors(
        val cream: Int,
        val charcoal: Int,
        val emptyPrice: Int,
        val transparent: Int,
    ) {
        companion object {
            fun from(context: Context) =
                SheetColors(
                    cream = ContextCompat.getColor(context, R.color.vinilos_cream),
                    charcoal = ContextCompat.getColor(context, R.color.vinilos_charcoal),
                    emptyPrice = ContextCompat.getColor(context, R.color.vinilos_duration_empty),
                    transparent = ContextCompat.getColor(context, android.R.color.transparent),
                )
        }
    }

    companion object {
        const val TAG = "collector-album-add"
        const val RESULT_ALBUM_ADDED = "collector_album_added"
        const val RESULT_COLLECTOR_ID = "collector_album_added_collector_id"
        const val RESULT_ALBUM_ID = "collector_album_added_album_id"
        const val RESULT_ALBUM_NAME = "collector_album_added_album_name"

        fun argumentsFor(
            collectorId: Int,
            albumId: Int,
            albumName: String,
            artistName: String,
        ): Bundle =
            bundleOf(
                CollectorAlbumAddViewModel.ARG_COLLECTOR_ID to collectorId,
                CollectorAlbumAddViewModel.ARG_ALBUM_ID to albumId,
                CollectorAlbumAddViewModel.ARG_ALBUM_NAME to albumName,
                CollectorAlbumAddViewModel.ARG_ARTIST_NAME to artistName,
            )
    }
}
