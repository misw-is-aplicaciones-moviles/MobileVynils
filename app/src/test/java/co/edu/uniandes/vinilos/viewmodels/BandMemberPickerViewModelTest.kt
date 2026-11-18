package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakePerformerRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
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
@Suppress("MagicNumber") // Fixture ids.
class BandMemberPickerViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val queen = band(101, "Queen", members = 2)
    private val beatles = band(120, "The Beatles", members = 1)
    private val freddie = musician(102, "Freddie Mercury", 1946, bandId = 101, bandName = "Queen")
    private val brian = musician(103, "Brian May", 1947, bandId = 101, bandName = "Queen")
    private val adam = musician(106, "Adam Lambert", 1982)
    private val ringo = musician(110, "Ringo Starr", 1940, bandId = 120, bandName = "The Beatles")
    private val musicians = listOf(adam, brian, freddie, ringo)
    private val everyone = listOf(adam, brian, freddie, queen, ringo, beatles)
    private val adamMember = PerformerSummary(106, "Adam Lambert", null, PerformerType.MUSICIAN)

    private val session = FakeSessionRepository(UserRole.COLLECTOR)
    private val repository = FakePerformerRepository(remoteResult = DataResult.Success(everyone))
    private val events = mutableListOf<BandMemberPickerEvent>()
    private val states = mutableListOf<BandMemberPickerUiState>()

    @Test
    fun `loads only musicians and marks current members`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            val state = state(vm)
            assertEquals(musicians, state.musicians)
            assertEquals(setOf(102, 103), state.memberIds)
            val rows = state.visibleRows.associateBy { it.musician.id }
            assertTrue(rows.getValue(102).isMember && rows.getValue(103).isMember)
            assertFalse(rows.getValue(106).isMember)
            assertEquals("The Beatles", rows.getValue(110).otherBandName)
            assertNull(rows.getValue(106).otherBandName)
            assertFalse(state.isLoading)
            assertEquals(1, repository.remoteCalls)
            assertEquals(0, repository.localCalls)
        }

    @Test
    fun `without musicians shows the empty state`() =
        runTest {
            repository.remoteResult = DataResult.Success(listOf(queen, beatles))
            val vm = picker()
            advanceUntilIdle()

            assertTrue(state(vm).isEmpty)
            assertFalse(state(vm).hasNoSearchResults)
            assertFalse(state(vm).canAdd)
        }

    @Test
    fun `failed load shows the error and retry loads the list`() =
        runTest {
            repository.remoteResult = DataResult.Failure(DataError.Network)
            val vm = picker()
            advanceUntilIdle()
            assertEquals(DataError.Network, state(vm).loadError)
            assertTrue(state(vm).visibleRows.isEmpty())

            repository.remoteResult = DataResult.Success(everyone)
            vm.retry()
            advanceUntilIdle()

            assertNull(state(vm).loadError)
            assertEquals(musicians, state(vm).visibleRows.map { it.musician })
        }

    @Test
    fun `search ignores case and surrounding spaces`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            vm.onQueryChanged("  ADAM ")
            advanceUntilIdle()

            assertEquals(listOf(adam), visible(vm))
            vm.onQueryChanged("")
            advanceUntilIdle()
            assertEquals(musicians, visible(vm))
        }

    @Test
    fun `search without matches shows no results`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            vm.onQueryChanged("zzz")
            advanceUntilIdle()

            assertTrue(state(vm).visibleRows.isEmpty())
            assertTrue(state(vm).hasNoSearchResults)
            assertFalse(state(vm).isEmpty)
        }

    @Test
    fun `a newer search cancels the previous one`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            vm.onQueryChanged("a")
            vm.onQueryChanged("ad")
            advanceUntilIdle()

            assertEquals(listOf(adam), visible(vm))
            val published = states.map { state -> state.visibleRows.map { it.musician } }
            assertFalse("older search published", listOf(adam, brian, ringo) in published)
        }

    @Test
    fun `selecting a free musician enables the add button`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            vm.onMusicianSelected(106)
            advanceUntilIdle()

            assertEquals(adam, state(vm).selectedMusician)
            assertTrue(state(vm).canAdd)
            assertTrue(state(vm).visibleRows.single { it.musician.id == 106 }.isSelected)
        }

    @Test
    fun `members and unknown ids cannot be selected`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            vm.onMusicianSelected(103)
            vm.onMusicianSelected(999)
            advanceUntilIdle()

            assertNull(state(vm).selectedId)
            assertFalse(state(vm).canAdd)
        }

    @Test
    fun `musician of another band shows the notice and sends nothing`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            vm.onMusicianSelected(110)
            vm.onAdd()
            advanceUntilIdle()

            assertEquals(BandConflict("Ringo Starr", "The Beatles"), state(vm).conflict)
            assertTrue(repository.addRequests.isEmpty())
            assertTrue(events.none { it is BandMemberPickerEvent.Added })
            assertFalse(state(vm).isSubmitting)
        }

    @Test
    fun `choosing another musician clears the notice`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()
            vm.onMusicianSelected(110)
            vm.onAdd()

            vm.onMusicianSelected(106)

            assertNull(state(vm).conflict)
        }

    @Test
    fun `successful add emits the new member once`() =
        runTest {
            repository.addResult = DataResult.Success(adamMember)
            val vm = picker()
            advanceUntilIdle()

            vm.onMusicianSelected(106)
            vm.onAdd()
            assertTrue(state(vm).isSubmitting)
            advanceUntilIdle()

            assertEquals(listOf(101 to 106), repository.addRequests)
            val added = events.filterIsInstance<BandMemberPickerEvent.Added>()
            assertEquals(listOf(BandMemberPickerEvent.Added(adamMember)), added)
            assertFalse(state(vm).isSubmitting)
            assertFalse(state(vm).isUncertain)
            assertTrue(106 in state(vm).memberIds)
        }

    @Test
    fun `double tap sends a single request`() =
        runTest {
            repository.addGate = CompletableDeferred()
            val vm = picker()
            advanceUntilIdle()
            vm.onMusicianSelected(106)

            vm.onAdd()
            vm.onAdd()
            runCurrent()
            repository.addGate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, repository.addRequests.size)
            assertEquals(1, events.count { it is BandMemberPickerEvent.Added })
        }

    @Test
    fun `search selection and role chip are blocked while submitting`() =
        runTest {
            repository.addGate = CompletableDeferred()
            val vm = picker()
            advanceUntilIdle()
            vm.onMusicianSelected(106)
            vm.onAdd()
            runCurrent()

            vm.onQueryChanged("ringo")
            vm.onMusicianSelected(110)
            vm.onRoleChipClicked()

            assertEquals("", state(vm).query)
            assertEquals(106, state(vm).selectedId)
            assertEquals(0, session.clearCount)
            assertTrue(state(vm).isSubmitting)
            repository.addGate?.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `not found is a definite error`() =
        runTest {
            repository.addResult = DataResult.Failure(DataError.Http(404))
            val vm = picker()
            advanceUntilIdle()
            vm.onMusicianSelected(106)

            vm.onAdd()
            advanceUntilIdle()

            assertEquals(DataError.Http(404), state(vm).submitError)
            assertFalse(state(vm).isUncertain)
            assertTrue(state(vm).canAdd)
            vm.onMusicianSelected(110)
            assertEquals(110, state(vm).selectedId)
            assertNull(state(vm).submitError)
        }

    @Test
    fun `network failure leaves the write uncertain`() =
        runTest {
            listOf(DataError.Network, DataError.Http(500)).forEach { error ->
                events.clear()
                repository.addRequests.clear()
                repository.addResult = DataResult.Failure(error)
                val saved = SavedStateHandle(arguments())
                val vm = picker(saved)
                advanceUntilIdle()
                vm.onMusicianSelected(106)

                vm.onAdd()
                advanceUntilIdle()

                assertTrue(state(vm).isUncertain)
                assertEquals(true, saved.get<Boolean>("bandMemberWritePending"))
                assertFalse(state(vm).canAdd)
                vm.onAdd()
                assertEquals(BandMemberPickerEvent.ReviewBand, events.last())
                assertEquals(1, repository.addRequests.size)
            }
        }

    @Test
    fun `visitor is sent to the role selector`() =
        runTest {
            session.selectRole(UserRole.VISITOR)
            val vm = picker()
            advanceUntilIdle()

            assertEquals(listOf(BandMemberPickerEvent.OpenRoleSelector), events)
            assertEquals(0, repository.remoteCalls)
            assertFalse(state(vm).canEdit)
        }

    @Test
    fun `role change cancels the load`() =
        runTest {
            repository.remoteGate = CompletableDeferred()
            val vm = picker()
            runCurrent()

            session.selectRole(UserRole.VISITOR)
            repository.remoteGate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(BandMemberPickerEvent.OpenRoleSelector), events)
            assertTrue(state(vm).musicians.isEmpty())
        }

    @Test
    fun `invalid band arguments close the picker`() =
        runTest {
            listOf(arguments(bandId = 0), arguments(bandName = " ")).forEach { args ->
                events.clear()
                picker(SavedStateHandle(args))
                advanceUntilIdle()

                assertEquals(listOf(BandMemberPickerEvent.Close), events)
            }
            assertEquals(0, repository.remoteCalls)
        }

    @Test
    fun `restores search selection and pending write`() =
        runTest {
            val saved =
                arguments() +
                    mapOf("bandMemberQuery" to "ad", "bandMemberSelectedId" to 106, "bandMemberWritePending" to true)
            val vm = picker(SavedStateHandle(saved))
            advanceUntilIdle()

            assertEquals("ad", state(vm).query)
            assertEquals(listOf(adam), visible(vm))
            assertEquals(106, state(vm).selectedId)
            assertTrue(state(vm).isUncertain)
            vm.onAdd()
            assertEquals(BandMemberPickerEvent.ReviewBand, events.last())
            assertTrue(repository.addRequests.isEmpty())
        }

    @Test
    fun `restored selection that became a member is dropped`() =
        runTest {
            repository.remoteResult = DataResult.Success(everyone.map { if (it.id == 106) it.inBand(queen) else it })
            val saved = SavedStateHandle(arguments() + ("bandMemberSelectedId" to 106))
            val vm = picker(saved)
            advanceUntilIdle()

            assertNull(state(vm).selectedId)
            assertNull(saved.get<Int>("bandMemberSelectedId"))
            assertTrue(state(vm).visibleRows.single { it.musician.id == 106 }.isMember)
            assertFalse(state(vm).canAdd)
        }

    private fun arguments(
        bandId: Int = 101,
        bandName: String = "Queen",
    ): Map<String, Any?> =
        mapOf(
            BandMemberFlowContract.BAND_ID to bandId,
            BandMemberFlowContract.BAND_NAME to bandName,
            BandMemberFlowContract.MEMBER_IDS to intArrayOf(102, 103),
        )

    private fun picker(saved: SavedStateHandle = SavedStateHandle(arguments())): BandMemberPickerViewModel {
        val vm = BandMemberPickerViewModel(saved, repository, session, mainDispatcherRule.testDispatcher)
        vm.uiState.observeForever(Observer { states += it })
        vm.events.observeForever(Observer { event -> event.getContentIfNotHandled()?.let { events += it } })
        return vm
    }

    private fun state(vm: BandMemberPickerViewModel) = checkNotNull(vm.uiState.value)

    private fun visible(vm: BandMemberPickerViewModel) = state(vm).visibleRows.map { it.musician }

    private fun Performer.inBand(band: Performer) = copy(bandId = band.id, bandName = band.name)
}
