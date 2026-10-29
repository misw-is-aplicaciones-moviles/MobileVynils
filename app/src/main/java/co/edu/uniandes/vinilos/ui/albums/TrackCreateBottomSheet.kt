package co.edu.uniandes.vinilos.ui.albums

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentTrackCreateSheetBinding
import co.edu.uniandes.vinilos.viewmodels.TrackCreateUiState
import co.edu.uniandes.vinilos.viewmodels.TrackCreateViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Hoja para agregar una canción. El nombre y la duración se editan aquí.
 * Guardar muestra los errores; el envío al álbum llega con el resto de HU08.
 */
class TrackCreateBottomSheet : BottomSheetDialogFragment(R.layout.fragment_track_create_sheet) {
    private var bindingOrNull: FragmentTrackCreateSheetBinding? = null
    private val binding get() = checkNotNull(bindingOrNull)
    private val viewModel: TrackCreateViewModel by viewModels()

    override fun getTheme(): Int = R.style.ThemeOverlay_Vinilos_BottomSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setBackgroundDrawable(
            ColorDrawable(ContextCompat.getColor(requireContext(), R.color.vinilos_sheet_scrim)),
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
        bindingOrNull = FragmentTrackCreateSheetBinding.bind(view)
        bindKeypad()
        bindFields()
        bindInsets()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingOrNull = null
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
            val digit = index + 1
            key.text = digit.toString()
            key.setOnClickListener { onDigit(digit) }
        }
        binding.key0.text = "0"
        binding.key0.setOnClickListener { onDigit(0) }
        binding.keyClear.setOnClickListener { onDigitAction { viewModel.onClearDuration() } }
        binding.keyDelete.setOnClickListener { onDigitAction { viewModel.onDeleteDigit() } }
    }

    private fun bindFields() {
        binding.closeSheet.setOnClickListener { dismiss() }
        binding.cancelTrack.setOnClickListener { dismiss() }
        binding.saveTrack.setOnClickListener { submit() }
        binding.trackNameField.doAfterTextChanged { text ->
            viewModel.onNameChanged(text?.toString().orEmpty())
        }
        binding.trackNameField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            submit()
            true
        }
        binding.trackNameField.setOnFocusChangeListener { field, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener
            field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), true)
        }
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

    private fun render(state: TrackCreateUiState) {
        renderName(state)
        renderDuration(state)
        binding.nameError.isVisible = state.isNameMissing
        binding.durationError.isVisible = state.isDurationInvalid
    }

    private fun renderName(state: TrackCreateUiState) {
        val field = binding.trackNameField
        if (field.text.toString() != state.name) {
            field.setText(state.name)
            field.setSelection(state.name.length)
        }
        val background =
            if (state.isNameMissing) R.drawable.bg_track_field_error else R.drawable.bg_track_field
        field.setBackgroundResource(background)
    }

    private fun renderDuration(state: TrackCreateUiState) {
        val color = if (state.hasDuration) R.color.vinilos_cream else R.color.vinilos_duration_empty
        binding.trackDuration.text = state.durationText
        binding.trackDuration.setTextColor(ContextCompat.getColor(requireContext(), color))
        binding.trackDuration.isInvalid = state.isDurationInvalid
    }

    private fun onDigit(digit: Int) {
        onDigitAction { viewModel.onDigit(digit) }
    }

    private fun onDigitAction(action: () -> Unit) {
        hideKeyboard()
        action()
    }

    private fun submit() {
        hideKeyboard()
        viewModel.onSave()
    }

    private fun hideKeyboard() {
        val token = binding.root.windowToken ?: return
        val manager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(token, 0)
    }

    private fun showExpanded(dialog: BottomSheetDialog) {
        val sheet =
            dialog.findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.setBackgroundColor(Color.TRANSPARENT)
        sheet.clipChildren = false
        (sheet.parent as? ViewGroup)?.clipChildren = false
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    companion object {
        const val TAG = "track-create"
    }
}
