package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewTrack
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackCreateViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAlbumRepository()
    private val viewModel = TrackCreateViewModel(ALBUM_ID, repository)

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
        assertTrue(repository.addedTrackRequests.isEmpty())
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
    fun `deleting a valid duration brings the error back`() =
        runTest {
            repository.addTrackResult = DataResult.Success(Track(1, "América", "1:30"))
            enter(1, 3, 0)
            viewModel.onNameChanged("América")
            viewModel.onSave()
            advanceUntilIdle()
            assertFalse(state().isDurationInvalid)

            repeat(3) { viewModel.onDeleteDigit() }
            assertEquals("00:00", state().durationText)
            assertTrue(state().isDurationInvalid)
        }

    @Test
    fun `valid save sends a trimmed draft to the current album and emits the created track`() =
        runTest {
            val created = Track(5, "Bohemian Rhapsody", "5:55")
            repository.addTrackResult = DataResult.Success(created)
            viewModel.onNameChanged("  Bohemian Rhapsody  ")
            enter(5, 5, 5)
            viewModel.onSave()

            advanceUntilIdle()

            assertEquals(listOf(ALBUM_ID to NewTrack("Bohemian Rhapsody", "5:55")), repository.addedTrackRequests)
            assertFalse(state().isSubmitting)
            assertNull(state().submitError)
            val event = checkNotNull(viewModel.events.value).getContentIfNotHandled()
            assertEquals(TrackCreateEvent.Created(created), event)
        }

    @Test
    fun `failed save preserves the form and exposes a retryable error`() =
        runTest {
            viewModel.onNameChanged("Bohemian Rhapsody")
            enter(5, 5, 5)
            viewModel.onSave()

            advanceUntilIdle()

            assertEquals(DataError.Network, state().submitError)
            assertFalse(state().isSubmitting)
            assertEquals("Bohemian Rhapsody", state().name)
            assertEquals("05:55", state().durationText)
            assertTrue(repository.addTrackResult is DataResult.Failure)
        }

    @Test
    fun `two save taps while submitting make one repository request`() =
        runTest {
            viewModel.onNameChanged("Bohemian Rhapsody")
            enter(5, 5, 5)
            viewModel.onSave()
            viewModel.onSave()

            advanceUntilIdle()

            assertEquals(1, repository.addedTrackRequests.size)
        }

    private fun enter(vararg digits: Int) {
        digits.forEach(viewModel::onDigit)
    }

    private fun state(): TrackCreateUiState = checkNotNull(viewModel.uiState.value)

    private companion object {
        const val ALBUM_ID = 100
    }
}
