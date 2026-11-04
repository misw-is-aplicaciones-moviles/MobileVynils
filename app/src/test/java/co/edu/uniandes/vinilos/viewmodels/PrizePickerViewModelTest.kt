package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakePrizeRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber") // Independent fixture IDs.
class PrizePickerViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val date = LocalDate.of(1985, 9, 18)
    private val prize = Prize(71, "Premio nuevo", "Descripción", "Organización")
    private val oldPrize = Prize(72, "Premio anterior", "Descripción", "Otra", listOf(PrizeAssignmentRef(901, date)))

    @Test
    fun `search is local and already assigned prizes cannot be selected`() =
        runTest {
            val repository = repository()
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.onPrizeSelected(72)
            assertNull(vm.uiState.value?.selectedId)
            vm.onQueryChanged("NUEVO")
            assertEquals(listOf(prize), vm.uiState.value?.visiblePrizes)
            assertEquals(1, repository.loadCalls)
            vm.onPrizeSelected(71)
            assertTrue(checkNotNull(vm.uiState.value).canContinue)
        }

    @Test
    fun `missing date prevents a write and selecting a date enables it`() =
        runTest {
            val repository = repository()
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onPrizeSelected(71)

            vm.onSave()

            assertEquals(0, repository.saveCalls)
            assertTrue(checkNotNull(vm.uiState.value).dateRequired)
            assertFalse(checkNotNull(vm.uiState.value).canSubmit)
            vm.onDateSelected(date)
            assertTrue(checkNotNull(vm.uiState.value).canSubmit)
        }

    @Test
    fun `success sends the correct type and date only once while saving`() =
        runTest {
            val repository = repository()
            val gate = CompletableDeferred<Unit>().also { repository.saveGate = it }
            repository.assigned = DataResult.Success(ArtistPrize(903, prize, date))
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onPrizeSelected(71)
            vm.onDateSelected(date)

            vm.onSave()
            vm.onSave()
            runCurrent()
            assertEquals(1, repository.saveCalls)
            assertTrue(checkNotNull(vm.uiState.value).isSubmitting)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(PerformerType.BAND, repository.lastType)
            assertEquals(date, repository.lastDate)
            assertEquals(
                PrizePickerEvent.Assigned(ArtistPrize(903, prize, date)),
                vm.events.value?.getContentIfNotHandled(),
            )
            assertNull(vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `uncertain write preserves entries and cannot repeat the POST`() =
        runTest {
            val repository = repository()
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onPrizeSelected(71)
            vm.onDateSelected(date)
            vm.onSave()
            advanceUntilIdle()

            vm.onSave()

            assertEquals(1, repository.saveCalls)
            assertTrue(checkNotNull(vm.uiState.value).isUncertain)
            assertEquals(date, vm.uiState.value?.date)
            vm.onReviewArtist()
            assertEquals(PrizePickerEvent.ReviewArtist, vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `definite validation failure preserves the date and permits retry`() =
        runTest {
            val repository = repository().also { it.assigned = DataResult.Failure(DataError.Http(400)) }
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onPrizeSelected(71)
            vm.onDateSelected(date)
            vm.onSave()
            advanceUntilIdle()

            assertFalse(checkNotNull(vm.uiState.value).isUncertain)
            assertTrue(checkNotNull(vm.uiState.value).canSubmit)
            assertEquals(date, vm.uiState.value?.date)
        }

    @Test
    fun `new prize remains visible despite an earlier catalog response`() =
        runTest {
            val repository = repository()
            val gate = CompletableDeferred<Unit>().also { repository.loadGate = it }
            val vm = viewModel(repository)
            runCurrent()
            vm.onQueryChanged("sin coincidencias")
            val created = Prize(73, "Creado en HU13", "Nuevo", "Organización")

            vm.onPrizeCreated(created)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(created, vm.uiState.value?.selectedPrize)
            assertEquals(
                created,
                vm.uiState.value
                    ?.visiblePrizes
                    ?.first(),
            )
            assertEquals("", vm.uiState.value?.query)
        }

    @Test
    fun `visitor and invalid artist do not load prizes`() =
        runTest {
            val repository = repository()
            viewModel(repository, session = FakeSessionRepository(UserRole.VISITOR))
            viewModel(repository, handle = args().also { it[PrizeFlowContract.PERFORMER_ID] = 0 })
            advanceUntilIdle()

            assertEquals(0, repository.loadCalls)
        }

    @Test
    fun `saved selection query and date survive a new ViewModel without a write`() =
        runTest {
            val repository = repository()
            val handle = args()
            val vm = viewModel(repository, handle)
            advanceUntilIdle()
            vm.onPrizeSelected(71)
            vm.onQueryChanged("nuevo")
            vm.onDateSelected(date)

            val restored = viewModel(repository, handle)
            advanceUntilIdle()

            assertEquals(71, restored.uiState.value?.selectedId)
            assertEquals("nuevo", restored.uiState.value?.query)
            assertEquals(date, restored.uiState.value?.date)
            assertEquals(0, repository.saveCalls)
        }

    private fun repository() = FakePrizeRepository().also { it.catalog = DataResult.Success(listOf(oldPrize, prize)) }

    @Test
    fun `reselecting the same prize preserves the chosen date`() =
        runTest {
            val vm = viewModel(repository())
            advanceUntilIdle()
            vm.onPrizeSelected(71)
            vm.onDateSelected(date)

            vm.onPrizeSelected(71)

            assertEquals(date, vm.uiState.value?.date)
        }

    @Test
    fun `restoring an uncertain write allows review but never another automatic submission`() =
        runTest {
            val repository = repository()
            val handle = args()
            val vm = viewModel(repository, handle)
            advanceUntilIdle()
            vm.onPrizeSelected(71)
            vm.onDateSelected(date)
            vm.onSave()
            advanceUntilIdle()

            val restored = viewModel(repository, handle)
            advanceUntilIdle()
            restored.onSave()
            restored.onContinue()

            assertEquals(1, repository.saveCalls)
            assertTrue(checkNotNull(restored.uiState.value).isUncertain)
            assertEquals(PrizePickerEvent.ReviewArtist, restored.events.value?.getContentIfNotHandled())
        }

    private fun args() =
        SavedStateHandle(
            mapOf(
                PrizeFlowContract.PERFORMER_ID to 83,
                PrizeFlowContract.PERFORMER_TYPE to PerformerType.BAND.name,
                PrizeFlowContract.PERFORMER_NAME to "Banda de pruebas",
                PrizeFlowContract.ASSIGNMENT_IDS to intArrayOf(901),
            ),
        )

    private fun viewModel(
        repository: FakePrizeRepository,
        handle: SavedStateHandle = args(),
        session: FakeSessionRepository = FakeSessionRepository(UserRole.COLLECTOR),
    ) = PrizePickerViewModel(handle, repository, session)
}
