package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
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

    // Esta asignación pertenece a otro artista: no debe bloquear el premio para el actual.
    private val prize = Prize(71, "Premio nuevo", "Descripción", "Organización", listOf(PrizeAssignmentRef(902, date)))
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
            assertTrue(repository.lastForceRefresh)
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
            PerformerType.entries.forEach { type ->
                val repository = repository()
                val gate = CompletableDeferred<Unit>().also { repository.saveGate = it }
                repository.assigned = DataResult.Success(ArtistPrize(903, prize, date))
                val vm = viewModel(repository, args().also { it[PrizeFlowContract.PERFORMER_TYPE] = type.name })
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

                assertEquals(type, repository.lastType)
                assertEquals(date, repository.lastDate)
                assertEquals(
                    PrizePickerEvent.Assigned(ArtistPrize(903, prize, date)),
                    vm.events.value?.getContentIfNotHandled(),
                )
                assertNull(vm.events.value?.getContentIfNotHandled())
            }
        }

    @Test
    fun `uncertain write preserves entries and cannot repeat the POST`() =
        runTest {
            listOf(DataError.Network, DataError.Http(500), DataError.Serialization).forEach { error ->
                val repository = repository().also { it.assigned = DataResult.Failure(error) }
                val vm = viewModel(repository)
                advanceUntilIdle()
                vm.onPrizeSelected(71)
                vm.onDateSelected(date)
                vm.onSave()
                advanceUntilIdle()

                vm.onSave()

                assertEquals(1, repository.saveCalls)
                assertEquals(error, vm.uiState.value?.submitError)
                assertTrue(checkNotNull(vm.uiState.value).isUncertain)
                assertEquals(71, vm.uiState.value?.selectedId)
                assertEquals(date, vm.uiState.value?.date)
                vm.onReviewArtist()
                assertEquals(PrizePickerEvent.ReviewArtist, vm.events.value?.getContentIfNotHandled())
            }
        }

    @Test
    fun `definite validation failure preserves the date and permits retry`() =
        runTest {
            listOf(400, 404).forEach { code ->
                val repository = repository().also { it.assigned = DataResult.Failure(DataError.Http(code)) }
                val vm = viewModel(repository)
                advanceUntilIdle()
                vm.onPrizeSelected(71)
                vm.onDateSelected(date)
                vm.onSave()
                advanceUntilIdle()

                assertFalse(checkNotNull(vm.uiState.value).isUncertain)
                assertTrue(checkNotNull(vm.uiState.value).canSubmit)
                assertEquals(date, vm.uiState.value?.date)
                assertEquals(DataError.Http(code), vm.uiState.value?.submitError)
                assertNull(vm.events.value)
                val assignment = ArtistPrize(903, prize, date)
                repository.assigned = DataResult.Success(assignment)
                vm.onSave()
                advanceUntilIdle()

                assertEquals(2, repository.saveCalls)
                assertEquals(PrizePickerEvent.Assigned(assignment), vm.events.value?.getContentIfNotHandled())
                assertNull(vm.uiState.value?.submitError)
            }
        }

    @Test
    fun `empty catalog and failed loads recover without duplicating or losing valid entries`() =
        runTest {
            val repository = FakePrizeRepository()
            val gate = CompletableDeferred<Unit>().also { repository.loadGate = it }
            val vm = viewModel(repository)
            runCurrent()
            vm.load(forceRefresh = true)
            assertTrue(checkNotNull(vm.uiState.value).isLoading)
            assertEquals(1, repository.loadCalls)
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(checkNotNull(vm.uiState.value).prizes.isEmpty())
            assertNull(vm.uiState.value?.loadError)
            assertFalse(checkNotNull(vm.uiState.value).isLoading)
            assertFalse(checkNotNull(vm.uiState.value).canContinue)

            repository.catalog = DataResult.Failure(DataError.Network)
            vm.load(forceRefresh = true)
            advanceUntilIdle()
            assertEquals(DataError.Network, vm.uiState.value?.loadError)
            assertFalse(checkNotNull(vm.uiState.value).isLoading)
            repository.catalog = DataResult.Success(listOf(prize))
            vm.load(forceRefresh = true)
            advanceUntilIdle()
            assertEquals(listOf(prize), vm.uiState.value?.prizes)
            assertNull(vm.uiState.value?.loadError)
            assertTrue(repository.lastForceRefresh)
            vm.onPrizeSelected(prize.id)
            vm.onDateSelected(date)
            repository.catalog = DataResult.Failure(DataError.Network)
            vm.load(forceRefresh = true)
            advanceUntilIdle()
            assertEquals(listOf(prize), vm.uiState.value?.prizes)
            assertEquals(prize.id, vm.uiState.value?.selectedId)
            assertEquals(date, vm.uiState.value?.date)
            assertEquals(DataError.Network, vm.uiState.value?.loadError)
            assertEquals(0, repository.saveCalls)
        }

    @Test
    fun `changing the selected prize clears its date and rejects an unknown prize`() =
        runTest {
            val second = prize.copy(id = 73, name = "Otro premio")
            val repository = repository().also { it.catalog = DataResult.Success(listOf(prize, second)) }
            val handle = args()
            val vm = viewModel(repository, handle)
            advanceUntilIdle()
            vm.onPrizeSelected(prize.id)
            vm.onDateSelected(date)
            vm.onPrizeSelected(999)
            assertEquals(prize.id, vm.uiState.value?.selectedId)
            assertEquals(date, vm.uiState.value?.date)
            vm.onPrizeSelected(second.id)
            assertEquals(second, vm.uiState.value?.selectedPrize)
            assertNull(vm.uiState.value?.date)
            assertFalse(checkNotNull(vm.uiState.value).canSubmit)
            vm.onSave()
            assertTrue(checkNotNull(vm.uiState.value).dateRequired)
            assertEquals(0, repository.saveCalls)
            val restored = viewModel(repository, handle)
            advanceUntilIdle()
            assertEquals(second, restored.uiState.value?.selectedPrize)
            assertNull(restored.uiState.value?.date)
        }

    @Test
    fun `changing role before saving returns to the selector and never writes`() =
        runTest {
            val repository = repository()
            val session = FakeSessionRepository(UserRole.COLLECTOR)
            val vm = viewModel(repository, session = session)
            val observer = Observer<PrizePickerUiState> {}
            vm.uiState.observeForever(observer)
            try {
                advanceUntilIdle()
                vm.onPrizeSelected(prize.id)
                vm.onDateSelected(date)
                session.selectRole(UserRole.VISITOR)
                vm.onSave()
                advanceUntilIdle()
                assertFalse(checkNotNull(vm.uiState.value).canEdit)
                assertFalse(checkNotNull(vm.uiState.value).canSubmit)
                assertEquals(0, repository.saveCalls)
                assertEquals(PrizePickerEvent.OpenRoleSelector, vm.events.value?.getContentIfNotHandled())
            } finally {
                vm.uiState.removeObserver(observer)
            }
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
            listOf(
                args().also { it[PrizeFlowContract.PERFORMER_ID] = 0 },
                args().also { it[PrizeFlowContract.PERFORMER_ID] = -1 },
                args().also { it[PrizeFlowContract.PERFORMER_TYPE] = "UNKNOWN" },
                args().also { it[PrizeFlowContract.PERFORMER_NAME] = " " },
                SavedStateHandle(),
            ).forEach { viewModel(repository, handle = it).onSave() }
            advanceUntilIdle()

            assertEquals(0, repository.loadCalls)
            assertEquals(0, repository.saveCalls)
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
