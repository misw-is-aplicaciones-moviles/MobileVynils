package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackCreateViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val viewModel = TrackCreateViewModel()

    @Test
    fun `digits enter from the right and a fifth digit drops the first`() {
        viewModel.onDigit(1)
        assertEquals("00:01", state().durationText)

        viewModel.onDigit(2)
        viewModel.onDigit(3)
        assertEquals("01:23", state().durationText)

        viewModel.onDigit(4)
        viewModel.onDigit(5)
        assertEquals("23:45", state().durationText)
    }

    @Test
    fun `delete removes the last digit and clear returns to zero`() {
        listOf(1, 2, 3).forEach(viewModel::onDigit)
        viewModel.onDeleteDigit()
        assertEquals("00:12", state().durationText)

        viewModel.onClearDuration()
        assertEquals("00:00", state().durationText)
        assertFalse(state().hasDuration)

        viewModel.onDeleteDigit()
        assertEquals("00:00", state().durationText)
    }

    @Test
    fun `a digit outside the keypad is ignored`() {
        viewModel.onDigit(-1)
        viewModel.onDigit(10)
        assertEquals("00:00", state().durationText)
    }

    @Test
    fun `errors stay hidden until save`() {
        assertFalse(state().isNameMissing)
        assertFalse(state().isDurationInvalid)

        viewModel.onSave()

        assertTrue(state().isNameMissing)
        assertTrue(state().isDurationInvalid)
    }

    @Test
    fun `a blank name is required and a written name clears the error`() {
        viewModel.onNameChanged("   ")
        viewModel.onSave()
        assertTrue(state().isNameMissing)

        viewModel.onNameChanged("América")
        assertFalse(state().isNameMissing)
        assertEquals("América", state().name)
    }

    @Test
    fun `zero sixty minutes and sixty seconds fail and the ends of the range pass`() {
        viewModel.onSave()
        assertTrue(state().isDurationInvalid)

        enter(0, 0, 6, 0)
        assertEquals("00:60", state().durationText)
        assertTrue(state().isDurationInvalid)

        viewModel.onClearDuration()
        enter(6, 0, 0, 0)
        assertEquals("60:00", state().durationText)
        assertTrue(state().isDurationInvalid)

        viewModel.onClearDuration()
        enter(1)
        assertEquals("00:01", state().durationText)
        assertFalse(state().isDurationInvalid)

        viewModel.onClearDuration()
        enter(5, 9, 5, 9)
        assertEquals("59:59", state().durationText)
        assertFalse(state().isDurationInvalid)
    }

    @Test
    fun `deleting a valid duration brings the error back`() {
        enter(1, 3, 0)
        viewModel.onNameChanged("América")
        viewModel.onSave()
        assertFalse(state().isDurationInvalid)

        repeat(3) { viewModel.onDeleteDigit() }
        assertEquals("00:00", state().durationText)
        assertTrue(state().isDurationInvalid)
    }

    private fun enter(vararg digits: Int) {
        digits.forEach(viewModel::onDigit)
    }

    private fun state(): TrackCreateUiState = checkNotNull(viewModel.uiState.value)
}
