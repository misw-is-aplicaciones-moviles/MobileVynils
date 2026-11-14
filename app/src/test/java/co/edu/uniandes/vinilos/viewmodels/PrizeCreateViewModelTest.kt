package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewPrize
import co.edu.uniandes.vinilos.models.Prize
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

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber") // Fixture IDs and HTTP status codes.
class PrizeCreateViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val draft = NewPrize("Voz del Caribe", "Reconoce a intérpretes del Caribe.", "Asociación de Vinilo")
    private val saved = Prize(75, draft.name, draft.description, draft.organization)

    @Test
    fun `invalid submit shows an error per field and does not call the repository`() =
        runTest {
            val repository = FakePrizeRepository()
            val vm = viewModel(repository)

            vm.onSubmit()
            advanceUntilIdle()

            val required = FieldError.REQUIRED
            assertEquals(PrizeFormErrors(required, required, required), state(vm).errors)
            assertFalse(state(vm).isSubmitting)
            assertTrue(repository.createdDrafts.isEmpty())
            assertNull(vm.events.value)
        }

    @Test
    fun `a field with only spaces is empty and is the only error`() =
        runTest {
            val repository = FakePrizeRepository()
            val vm = viewModel(repository)
            fill(vm)
            vm.onDescriptionChanged("    ")

            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(PrizeFormErrors(description = FieldError.REQUIRED), state(vm).errors)
            assertTrue(repository.createdDrafts.isEmpty())
        }

    @Test
    fun `editing a field clears only its own error`() =
        runTest {
            val vm = viewModel(FakePrizeRepository())
            vm.onSubmit()

            vm.onNameChanged("Voz del Caribe")

            val required = FieldError.REQUIRED
            assertEquals(PrizeFormErrors(null, required, required), state(vm).errors)
            vm.onOrganizationChanged("Asociación")
            assertEquals(PrizeFormErrors(description = required), state(vm).errors)
        }

    @Test
    fun `valid submit sends the trimmed draft once and emits the created prize once`() =
        runTest {
            val repository = FakePrizeRepository().also { it.created = DataResult.Success(saved) }
            val vm = viewModel(repository)
            vm.onNameChanged("  ${draft.name} ")
            vm.onDescriptionChanged("\n${draft.description}  ")
            vm.onOrganizationChanged(" ${draft.organization}")

            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(listOf(draft), repository.createdDrafts)
            assertEquals(PrizeCreateEvent.Created(saved), vm.events.value?.getContentIfNotHandled())
            assertNull(vm.events.value?.getContentIfNotHandled())
            assertEquals(0, repository.loadCalls)
        }

    @Test
    fun `second tap while submitting sends a single request`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakePrizeRepository().also {
                    it.created = DataResult.Success(saved)
                    it.createGate = gate
                }
            val vm = viewModel(repository)
            fill(vm)

            vm.onSubmit()
            vm.onSubmit()
            runCurrent()
            vm.onSubmit()

            assertEquals(1, repository.createdDrafts.size)
            assertTrue(state(vm).isSubmitting)
            assertTrue(state(vm).isLocked)
            gate.complete(Unit)
            advanceUntilIdle()
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(1, repository.createdDrafts.size)
            assertEquals(PrizeCreateEvent.Created(saved), vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `fields cannot change while a request runs`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository = FakePrizeRepository().also { it.createGate = gate }
            val vm = viewModel(repository)
            fill(vm)
            vm.onSubmit()
            runCurrent()

            vm.onNameChanged("Otro nombre")
            vm.onDescriptionChanged("Otra descripción")
            vm.onOrganizationChanged("Otra organización")

            assertEquals(PrizeForm(draft.name, draft.description, draft.organization), state(vm).form)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `rejected request keeps the data, is not uncertain and a retry creates the prize`() =
        runTest {
            listOf(400, 404).forEach { code ->
                val repository = FakePrizeRepository().also { it.created = DataResult.Failure(DataError.Http(code)) }
                val vm = viewModel(repository)
                fill(vm)

                vm.onSubmit()
                advanceUntilIdle()

                assertFalse(state(vm).isSubmitting)
                assertFalse(state(vm).isUncertain)
                assertFalse(state(vm).isLocked)
                assertEquals(PrizeCreateNotice.REJECTED, state(vm).notice)
                assertEquals(PrizeForm(draft.name, draft.description, draft.organization), state(vm).form)
                assertNull(vm.events.value)
                repository.created = DataResult.Success(saved)
                vm.onSubmit()
                advanceUntilIdle()

                assertEquals(2, repository.createdDrafts.size)
                assertEquals(PrizeCreateEvent.Created(saved), vm.events.value?.getContentIfNotHandled())
            }
        }

    @Test
    fun `an unknown result locks the form and never sends a second POST`() =
        runTest {
            val unknownResults =
                listOf(DataError.Network, DataError.Http(500), DataError.Http(503), DataError.Serialization)
            unknownResults.forEach { error ->
                val repository = FakePrizeRepository().also { it.created = DataResult.Failure(error) }
                val vm = viewModel(repository)
                fill(vm)
                vm.onSubmit()
                advanceUntilIdle()

                assertTrue("$error", state(vm).isUncertain)
                assertTrue(state(vm).isLocked)
                assertEquals(PrizeCreateNotice.UNCERTAIN, state(vm).notice)
                assertEquals(PrizeForm(draft.name, draft.description, draft.organization), state(vm).form)
                vm.onNameChanged("Editado")
                vm.onSubmit()
                advanceUntilIdle()

                assertEquals("$error", 1, repository.createdDrafts.size)
                assertEquals(draft.name, state(vm).form.name)
            }
        }

    @Test
    fun `checking finds the prize that was saved and returns it instead of creating another`() =
        runTest {
            val repository =
                FakePrizeRepository().also {
                    it.created = DataResult.Failure(DataError.Network)
                    it.catalog =
                        DataResult.Success(
                            listOf(
                                Prize(70, draft.name, "Otra descripción", draft.organization),
                                saved,
                                Prize(76, "Otro", draft.description, draft.organization),
                            ),
                        )
                }
            val vm = viewModel(repository)
            fill(vm)
            vm.onSubmit()
            advanceUntilIdle()

            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(1, repository.createdDrafts.size)
            assertEquals(1, repository.loadCalls)
            assertTrue(repository.lastForceRefresh)
            assertEquals(PrizeCreateEvent.Created(saved), vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `checking finds nothing and only then allows sending again`() =
        runTest {
            val repository =
                FakePrizeRepository().also {
                    it.created = DataResult.Failure(DataError.Http(500))
                    it.catalog = DataResult.Success(listOf(Prize(70, draft.name, "Otra descripción", "Otra")))
                }
            val vm = viewModel(repository)
            fill(vm)
            vm.onSubmit()
            advanceUntilIdle()

            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(PrizeCreateNotice.NOT_SAVED, state(vm).notice)
            assertFalse(state(vm).isUncertain)
            assertFalse(state(vm).isLocked)
            assertNull(vm.events.value)
            repository.created = DataResult.Success(saved)
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(2, repository.createdDrafts.size)
            assertEquals(PrizeCreateEvent.Created(saved), vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `a failed check keeps the form locked and never sends`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakePrizeRepository().also {
                    it.created = DataResult.Failure(DataError.Network)
                    it.catalog = DataResult.Failure(DataError.Network)
                }
            val vm = viewModel(repository)
            fill(vm)
            vm.onSubmit()
            advanceUntilIdle()
            repository.loadGate = gate

            vm.onSubmit()
            vm.onSubmit()
            runCurrent()
            assertTrue(state(vm).isChecking)
            assertEquals(1, repository.loadCalls)
            vm.onCancel()
            assertNull(vm.events.value)
            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(state(vm).isUncertain)
            assertFalse(state(vm).isChecking)
            assertEquals(PrizeCreateNotice.CHECK_UNAVAILABLE, state(vm).notice)
            assertEquals(1, repository.createdDrafts.size)
            repository.catalog = DataResult.Success(emptyList())
            vm.onSubmit()
            advanceUntilIdle()
            assertEquals(PrizeCreateNotice.NOT_SAVED, state(vm).notice)
            assertEquals(1, repository.createdDrafts.size)
        }

    @Test
    fun `typed text and field errors survive a new ViewModel with the same saved state`() =
        runTest {
            val repository = FakePrizeRepository()
            val handle = SavedStateHandle()
            val vm = viewModel(repository, handle)
            vm.onNameChanged(draft.name)
            vm.onSubmit()

            val restored = viewModel(repository, handle)

            val required = FieldError.REQUIRED
            assertEquals(PrizeForm(name = draft.name), state(restored).form)
            assertEquals(PrizeFormErrors(null, required, required), state(restored).errors)
            restored.onDescriptionChanged(draft.description)
            val again = viewModel(repository, handle)
            assertEquals(PrizeFormErrors(organization = required), state(again).errors)
            assertTrue(repository.createdDrafts.isEmpty())
        }

    @Test
    fun `an unknown result survives recreation and still blocks a blind create`() =
        runTest {
            val repository = FakePrizeRepository().also { it.created = DataResult.Failure(DataError.Network) }
            val handle = SavedStateHandle()
            val vm = viewModel(repository, handle)
            fill(vm)
            vm.onSubmit()
            advanceUntilIdle()

            val restored = viewModel(repository, handle)
            assertTrue(state(restored).isUncertain)
            assertEquals(PrizeCreateNotice.UNCERTAIN, state(restored).notice)
            restored.onSubmit()
            advanceUntilIdle()

            // The first thing "Crear premio" does after recreation is read the catalog, never a second POST.
            assertEquals(PrizeCreateNotice.NOT_SAVED, state(restored).notice)
            assertEquals(1, repository.createdDrafts.size)
            assertEquals(1, repository.loadCalls)
        }

    @Test
    fun `a rejected request is not remembered as pending after recreation`() =
        runTest {
            val repository = FakePrizeRepository().also { it.created = DataResult.Failure(DataError.Http(400)) }
            val handle = SavedStateHandle()
            val vm = viewModel(repository, handle)
            fill(vm)
            vm.onSubmit()
            advanceUntilIdle()

            val restored = viewModel(repository, handle)

            assertFalse(state(restored).isUncertain)
            assertNull(state(restored).notice)
            assertEquals(draft.name, state(restored).form.name)
        }

    @Test
    fun `cancel writes nothing and warns the picker only when a result was left unknown`() =
        runTest {
            val repository = FakePrizeRepository().also { it.created = DataResult.Failure(DataError.Network) }
            val vm = viewModel(repository)
            fill(vm)

            vm.onCancel()

            val cancelled = vm.events.value?.getContentIfNotHandled()
            assertEquals(PrizeCreateEvent.Cancelled(catalogMayBeStale = false), cancelled)
            assertTrue(repository.createdDrafts.isEmpty())
            vm.onSubmit()
            advanceUntilIdle()
            vm.onCancel()
            val cancelledAfterUnknown = vm.events.value?.getContentIfNotHandled()
            assertEquals(PrizeCreateEvent.Cancelled(catalogMayBeStale = true), cancelledAfterUnknown)
        }

    @Test
    fun `cancel and role chip are ignored while a request runs`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val session = FakeSessionRepository(UserRole.COLLECTOR)
            val repository = FakePrizeRepository().also { it.createGate = gate }
            val vm = viewModel(repository, session = session)
            fill(vm)
            vm.onSubmit()
            runCurrent()

            vm.onCancel()
            vm.onRoleChipClicked()

            assertNull(vm.events.value)
            assertEquals(0, session.clearCount)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `changing the role leaves the screen and a visitor can never write`() =
        runTest {
            val session = FakeSessionRepository(UserRole.COLLECTOR)
            val repository = FakePrizeRepository()
            val vm = viewModel(repository, session = session)
            val observer = Observer<PrizeCreateUiState> {}
            vm.uiState.observeForever(observer)
            try {
                fill(vm)
                assertTrue(state(vm).canEdit)

                vm.onRoleChipClicked()

                assertEquals(1, session.clearCount)
                assertFalse(state(vm).canEdit)
                assertEquals(PrizeCreateEvent.OpenRoleSelector, vm.events.value?.getContentIfNotHandled())
                session.selectRole(UserRole.VISITOR)
                vm.onSubmit()
                advanceUntilIdle()
                assertTrue(repository.createdDrafts.isEmpty())
                assertEquals(PrizeCreateEvent.OpenRoleSelector, vm.events.value?.getContentIfNotHandled())
            } finally {
                vm.uiState.removeObserver(observer)
            }
        }

    @Test
    fun `a visitor who reaches the screen cannot send`() =
        runTest {
            val repository = FakePrizeRepository()
            val vm = viewModel(repository, session = FakeSessionRepository(UserRole.VISITOR))
            fill(vm)

            vm.onSubmit()
            advanceUntilIdle()

            assertFalse(state(vm).canEdit)
            assertTrue(repository.createdDrafts.isEmpty())
        }

    private fun fill(vm: PrizeCreateViewModel) {
        vm.onNameChanged(draft.name)
        vm.onDescriptionChanged(draft.description)
        vm.onOrganizationChanged(draft.organization)
    }

    private fun state(vm: PrizeCreateViewModel) = checkNotNull(vm.uiState.value)

    private fun viewModel(
        repository: FakePrizeRepository,
        handle: SavedStateHandle = SavedStateHandle(),
        session: FakeSessionRepository = FakeSessionRepository(UserRole.COLLECTOR),
    ) = PrizeCreateViewModel(handle, repository, session)
}
