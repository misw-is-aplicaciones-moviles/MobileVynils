package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewCollectorAlbum
import co.edu.uniandes.vinilos.testing.FakeCollectorRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Price, status and single send of the sheet that adds an album to a collector (HU11). */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber") // Prices and ids of the scenarios.
class CollectorAlbumAddViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private fun savedState() =
        SavedStateHandle(
            mapOf(
                CollectorAlbumAddViewModel.ARG_COLLECTOR_ID to 100,
                CollectorAlbumAddViewModel.ARG_ALBUM_ID to 200,
                CollectorAlbumAddViewModel.ARG_ALBUM_NAME to "Buscando América",
                CollectorAlbumAddViewModel.ARG_ARTIST_NAME to "Rubén Blades",
            ),
        )

    private fun viewModel(
        repository: FakeCollectorRepository = FakeCollectorRepository(),
        handle: SavedStateHandle = savedState(),
    ) = CollectorAlbumAddViewModel(handle, repository)

    private fun CollectorAlbumAddViewModel.enter(vararg digits: Int) {
        digits.forEach(::onDigit)
    }

    private fun CollectorAlbumAddViewModel.nextEvent() = events.value?.getContentIfNotHandled()

    @Test
    fun `digits build the price and delete and clear remove it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.enter(1, 5, 0)

            val priced = checkNotNull(viewModel.uiState.value)
            assertEquals(150, priced.priceDigits)
            assertEquals("\$150", priced.priceText)

            viewModel.onDeleteDigit()
            assertEquals(15, checkNotNull(viewModel.uiState.value).priceDigits)

            viewModel.onClearPrice()
            val cleared = checkNotNull(viewModel.uiState.value)
            assertEquals(0, cleared.priceDigits)
            assertEquals("\$0", cleared.priceText)
        }

    @Test
    fun `digits past the maximum keep the price at 999999`() =
        runTest {
            val viewModel = viewModel()

            viewModel.enter(9, 9, 9, 9, 9, 9, 9, 9)

            assertEquals(999_999, checkNotNull(viewModel.uiState.value).priceDigits)
        }

    @Test
    fun `digits outside the keypad are ignored`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onDigit(10)
            viewModel.onDigit(-1)

            assertEquals(0, checkNotNull(viewModel.uiState.value).priceDigits)
        }

    @Test
    fun `saving without price or status only shows the field errors`() =
        runTest {
            val repository = FakeCollectorRepository()
            val viewModel = viewModel(repository = repository)

            viewModel.onSave()

            val state = checkNotNull(viewModel.uiState.value)
            assertTrue(state.validationAttempted)
            assertTrue(state.isPriceInvalid)
            assertTrue(state.isStatusMissing)
            assertFalse(state.canSubmit)
            assertEquals(0, repository.addAlbumCalls)
            assertNull(viewModel.nextEvent())
        }

    @Test
    fun `entering a digit clears the errors and another save shows the missing one`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onSave()
            assertTrue(checkNotNull(viewModel.uiState.value).isPriceInvalid)
            assertTrue(checkNotNull(viewModel.uiState.value).isStatusMissing)

            viewModel.onDigit(1)

            val priced = checkNotNull(viewModel.uiState.value)
            assertFalse(priced.isPriceInvalid)
            assertFalse(priced.isStatusMissing)

            viewModel.onSave()

            val revalidated = checkNotNull(viewModel.uiState.value)
            assertFalse(revalidated.isPriceInvalid)
            assertTrue(revalidated.isStatusMissing)

            viewModel.onStatusSelected(AlbumStatus.ACTIVE)

            val completed = checkNotNull(viewModel.uiState.value)
            assertFalse(completed.isStatusMissing)
            assertTrue(completed.canSubmit)
        }

    @Test
    fun `saving sends the price and status and reports the addition once`() =
        runTest {
            val repository = FakeCollectorRepository()
            val viewModel = viewModel(repository = repository)

            viewModel.enter(1, 5, 0)
            viewModel.onStatusSelected(AlbumStatus.ACTIVE)
            viewModel.onSave()
            advanceUntilIdle()

            val state = checkNotNull(viewModel.uiState.value)
            assertFalse(state.isSubmitting)
            assertNull(state.submitError)
            assertEquals(1, repository.addAlbumCalls)
            assertEquals(Triple(100, 200, NewCollectorAlbum(150, AlbumStatus.ACTIVE)), repository.lastAddAlbumRequest)
            assertEquals(CollectorAlbumAddEvent.Added, viewModel.nextEvent())
            assertNull(viewModel.nextEvent())
        }

    @Test
    fun `a failed save keeps the form and reports the error`() =
        runTest {
            val repository =
                FakeCollectorRepository().apply { addAlbumResult = DataResult.Failure(DataError.Network) }
            val viewModel = viewModel(repository = repository)

            viewModel.enter(1, 5, 0)
            viewModel.onStatusSelected(AlbumStatus.INACTIVE)
            viewModel.onSave()
            advanceUntilIdle()

            val state = checkNotNull(viewModel.uiState.value)
            assertEquals(150, state.priceDigits)
            assertEquals(AlbumStatus.INACTIVE, state.status)
            assertEquals(DataError.Network, state.submitError)
            assertFalse(state.isSubmitting)
            assertNull(viewModel.nextEvent())
        }

    @Test
    fun `while saving the form ignores input and another save`() =
        runTest {
            val repository = FakeCollectorRepository().apply { addAlbumGate = CompletableDeferred() }
            val viewModel = viewModel(repository = repository)
            viewModel.enter(1, 5, 0)
            viewModel.onStatusSelected(AlbumStatus.ACTIVE)

            viewModel.onSave()
            runCurrent()
            assertTrue(checkNotNull(viewModel.uiState.value).isSubmitting)

            viewModel.enter(9)
            viewModel.onStatusSelected(AlbumStatus.INACTIVE)
            viewModel.onClearPrice()
            viewModel.onDeleteDigit()
            viewModel.onSave()

            val pending = checkNotNull(viewModel.uiState.value)
            assertEquals(150, pending.priceDigits)
            assertEquals(AlbumStatus.ACTIVE, pending.status)
            assertEquals(1, repository.addAlbumCalls)

            repository.addAlbumGate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(CollectorAlbumAddEvent.Added, viewModel.nextEvent())
            assertEquals(1, repository.addAlbumCalls)
        }

    @Test
    fun `recreating the view model keeps the chosen status`() =
        runTest {
            val handle = savedState()
            val first = viewModel(handle = handle)
            first.enter(1, 5, 0)
            first.onStatusSelected(AlbumStatus.INACTIVE)

            val recreated = viewModel(handle = handle)

            val state = checkNotNull(recreated.uiState.value)
            assertEquals(AlbumStatus.INACTIVE, state.status)
            assertEquals("Buscando América", state.albumName)
            assertEquals("Rubén Blades", state.artistName)
        }
}
