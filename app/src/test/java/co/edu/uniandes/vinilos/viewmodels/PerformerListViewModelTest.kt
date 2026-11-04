package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakePerformerRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.getOrAwaitValue
import co.edu.uniandes.vinilos.testing.musician
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber") // Fixture ids, years and counts.
class PerformerListViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // Already by name, as the repository returns them.
    private val brian = musician(id = 103, name = "Brian May", year = 1947, bandId = 101, bandName = "Queen")
    private val queen = band(id = 101, name = "Queen", year = 1970, albums = 2, members = 4)
    private val ruben = musician(id = 100, name = "Rubén Blades", year = 1948, albums = 2)
    private val performers = listOf(brian, queen, ruben)

    private fun viewModel(
        repository: FakePerformerRepository,
        session: FakeSessionRepository = FakeSessionRepository(UserRole.VISITOR),
    ) = PerformerListViewModel(repository, session)

    /** Records every state so a test can see transitions, not only the last value. */
    private fun PerformerListViewModel.recordStates(): List<PerformerListUiState> {
        val states = mutableListOf<PerformerListUiState>()
        uiState.observeForever(Observer { states += it })
        return states
    }

    @Test
    fun `without cache it shows loading and then the list`() =
        runTest {
            val repository = FakePerformerRepository(hasCache = false, localResult = DataResult.Success(performers))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            assertTrue(states.first().isLoading)

            advanceUntilIdle()

            val last = states.last()
            assertFalse(last.isLoading)
            assertEquals(listOf("Brian May", "Queen", "Rubén Blades"), last.performers.map { it.name })
            assertEquals(1, repository.localCalls)
            assertEquals(0, repository.remoteCalls)
        }

    @Test
    fun `with cache it shows the list without loading and revalidates once`() =
        runTest {
            val repository =
                FakePerformerRepository(
                    hasCache = true,
                    localResult = DataResult.Success(listOf(queen)),
                    remoteResult = DataResult.Success(performers),
                )
            val gate = CompletableDeferred<Unit>().also { repository.remoteGate = it }
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()

            advanceUntilIdle()

            assertTrue(states.filter { it.performers.isNotEmpty() }.none { it.isLoading })
            assertEquals(listOf("Queen"), states.last().performers.map { it.name })
            assertTrue("Revalidation must be flagged while it waits", states.last().isRefreshing)
            assertEquals(1, repository.remoteCalls)

            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(states.last().isRefreshing)
            assertEquals(3, states.last().performers.size)
            assertEquals(1, repository.remoteCalls)
        }

    @Test
    fun `failed revalidation with a list keeps it and emits the network notice`() =
        runTest {
            val repository =
                FakePerformerRepository(
                    hasCache = true,
                    localResult = DataResult.Success(performers),
                    remoteResult = DataResult.Failure(DataError.Network),
                )
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()

            advanceUntilIdle()

            assertEquals(3, states.last().performers.size)
            assertNull(states.last().error)
            assertFalse(states.last().isRefreshing)
            val event = viewModel.events.getOrAwaitValue().getContentIfNotHandled()
            assertEquals(PerformerListEvent.ShowRefreshError(DataError.Network), event)
        }

    @Test
    fun `failure without a list sets the error and it is not the empty state`() =
        runTest {
            val repository =
                FakePerformerRepository(hasCache = false, localResult = DataResult.Failure(DataError.Http(500)))
            val states = viewModel(repository).recordStates()

            advanceUntilIdle()

            val last = states.last()
            assertEquals(DataError.Http(500), last.error)
            assertFalse(last.isLoading)
            assertTrue(last.performers.isEmpty())
            assertFalse(last.hasPerformers)
            assertFalse(last.isEmpty)
        }

    @Test
    fun `refresh after an error loads the list`() =
        runTest {
            val repository =
                FakePerformerRepository(hasCache = false, localResult = DataResult.Failure(DataError.Network))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            advanceUntilIdle()
            repository.remoteResult = DataResult.Success(performers)

            viewModel.refresh()
            assertTrue(states.last().isLoading)
            assertNull(states.last().error)
            advanceUntilIdle()

            assertFalse(states.last().isLoading)
            assertNull(states.last().error)
            assertEquals(3, states.last().performers.size)
            assertEquals(1, repository.remoteCalls)
        }

    @Test
    fun `refresh with a list flags refreshing and a failure keeps the list and emits the error`() =
        runTest {
            val repository = FakePerformerRepository(hasCache = false, localResult = DataResult.Success(performers))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            advanceUntilIdle()
            repository.remoteResult = DataResult.Failure(DataError.Http(503))

            viewModel.refresh()
            assertTrue(states.last().isRefreshing)
            assertFalse(states.last().isLoading)
            advanceUntilIdle()

            assertEquals(3, states.last().performers.size)
            assertNull(states.last().error)
            assertFalse(states.last().isRefreshing)
            val event = viewModel.events.getOrAwaitValue().getContentIfNotHandled()
            assertEquals(PerformerListEvent.ShowRefreshError(DataError.Http(503)), event)
        }

    @Test
    fun `refresh is ignored while a load is in progress`() =
        runTest {
            val repository = FakePerformerRepository(hasCache = false, localResult = DataResult.Success(performers))
            val gate = CompletableDeferred<Unit>().also { repository.remoteGate = it }
            val viewModel = viewModel(repository)
            viewModel.recordStates()
            advanceUntilIdle()

            repeat(3) { viewModel.refresh() }
            advanceUntilIdle()

            assertEquals(1, repository.remoteCalls)
            gate.complete(Unit)
            advanceUntilIdle()
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(2, repository.remoteCalls)
        }

    @Test
    fun `an empty list is the empty state`() =
        runTest {
            val repository = FakePerformerRepository(hasCache = false, localResult = DataResult.Success(emptyList()))
            val states = viewModel(repository).recordStates()

            advanceUntilIdle()

            assertTrue(states.last().isEmpty)
            assertFalse(states.last().hasPerformers)
        }

    @Test
    fun `the filter starts on Todos and shows everything in the order received`() =
        runTest {
            val states = loadedStates()

            assertEquals(PerformerFilter.ALL, states.last().filter)
            assertEquals(listOf(103, 101, 100), states.last().performers.map { it.id })
        }

    @Test
    fun `Musicos shows only musicians and Bandas only bands, without new repository calls`() =
        runTest {
            val repository = FakePerformerRepository(hasCache = false, localResult = DataResult.Success(performers))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            advanceUntilIdle()

            viewModel.onFilterSelected(PerformerFilter.MUSICIANS)
            assertEquals(listOf("Brian May", "Rubén Blades"), states.last().performers.map { it.name })
            assertTrue(states.last().performers.all { it.type == PerformerType.MUSICIAN })

            viewModel.onFilterSelected(PerformerFilter.BANDS)
            assertEquals(listOf("Queen"), states.last().performers.map { it.name })

            viewModel.onFilterSelected(PerformerFilter.ALL)
            assertEquals(3, states.last().performers.size)
            advanceUntilIdle()
            assertEquals(1, repository.localCalls)
            assertEquals(0, repository.remoteCalls)
        }

    @Test
    fun `a filter without artists is empty but the loaded list is still there`() =
        runTest {
            val repository =
                FakePerformerRepository(hasCache = false, localResult = DataResult.Success(listOf(ruben, brian)))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            advanceUntilIdle()

            viewModel.onFilterSelected(PerformerFilter.BANDS)

            val last = states.last()
            assertTrue(last.performers.isEmpty())
            assertTrue(last.hasPerformers)
            assertTrue(last.isEmpty)
            assertNull(last.error)

            viewModel.onFilterSelected(PerformerFilter.ALL)
            assertEquals(2, states.last().performers.size)
        }

    @Test
    fun `selecting the filter already selected does not publish a new state`() =
        runTest {
            val viewModel = viewModel(FakePerformerRepository(localResult = DataResult.Success(performers)))
            val states = viewModel.recordStates()
            advanceUntilIdle()
            val before = states.size

            viewModel.onFilterSelected(PerformerFilter.ALL)

            assertEquals(before, states.size)
        }

    @Test
    fun `the filter survives a refresh and a new observer, as after a rotation`() =
        runTest {
            val repository = FakePerformerRepository(hasCache = false, localResult = DataResult.Success(performers))
            val viewModel = viewModel(repository)
            viewModel.recordStates()
            advanceUntilIdle()
            viewModel.onFilterSelected(PerformerFilter.BANDS)
            repository.remoteResult = DataResult.Success(performers + band(id = 7, name = "Zeta"))

            viewModel.refresh()
            advanceUntilIdle()

            // A recreated Fragment observes the same ViewModel and gets the last state.
            val afterRotation = viewModel.uiState.getOrAwaitValue()
            assertEquals(PerformerFilter.BANDS, afterRotation.filter)
            assertEquals(listOf("Queen", "Zeta"), afterRotation.performers.map { it.name })
        }

    @Test
    fun `a band shows its albums and members, a member its band, a musician without band its albums`() =
        runTest {
            val states = loadedStates()

            val items = states.last().performers.associateBy { it.name }
            assertEquals(PerformerSummaryLine.BandCounts(albums = 2, members = 4), items.getValue("Queen").summary)
            assertEquals(PerformerSummaryLine.MemberOf("Queen"), items.getValue("Brian May").summary)
            assertEquals(PerformerSummaryLine.AlbumCount(2), items.getValue("Rubén Blades").summary)
        }

    @Test
    fun `a band with one album and one member keeps the singular counts for the adapter`() =
        runTest {
            val one = band(id = 8, name = "Dúo", albums = 1, members = 1)
            val viewModel = viewModel(FakePerformerRepository(localResult = DataResult.Success(listOf(one))))
            val states = viewModel.recordStates()

            advanceUntilIdle()

            val item = states.last().performers.single()
            assertEquals(PerformerSummaryLine.BandCounts(1, 1), item.summary)
        }

    @Test
    fun `items carry the type, the image and the year`() =
        runTest {
            val states = loadedStates()

            val items = states.last().performers.associateBy { it.name }
            assertEquals(PerformerType.BAND, items.getValue("Queen").type)
            assertEquals(1970, items.getValue("Queen").year)
            assertEquals(1947, items.getValue("Brian May").year)
            assertEquals("https://example.com/103.jpg", items.getValue("Brian May").imageUrl)
        }

    @Test
    fun `onPerformerClicked opens the detail with the id and the type once`() {
        val viewModel = viewModel(FakePerformerRepository())

        viewModel.onPerformerClicked(101, PerformerType.BAND)

        val event = viewModel.events.value
        assertEquals(PerformerListEvent.OpenDetail(101, PerformerType.BAND), event?.getContentIfNotHandled())
        assertNull(event?.getContentIfNotHandled())
    }

    @Test
    fun `the role of the session is in the state and follows its changes`() =
        runTest {
            val session = FakeSessionRepository(UserRole.VISITOR)
            val viewModel = viewModel(FakePerformerRepository(localResult = DataResult.Success(performers)), session)
            val states = viewModel.recordStates()
            advanceUntilIdle()
            assertEquals(UserRole.VISITOR, states.last().role)

            session.selectRole(UserRole.COLLECTOR)

            assertEquals(UserRole.COLLECTOR, states.last().role)
            assertEquals(3, states.last().performers.size)
        }

    @Test
    fun `onRoleChipClicked clears the role and opens the selector`() {
        val session = FakeSessionRepository(UserRole.COLLECTOR)
        val viewModel = viewModel(FakePerformerRepository(), session)

        viewModel.onRoleChipClicked()

        assertEquals(1, session.clearCount)
        assertNull(session.role.value)
        assertEquals(PerformerListEvent.OpenRoleSelector, viewModel.events.value?.getContentIfNotHandled())
    }

    @Test
    fun `every filter accepts the types it should`() {
        assertTrue(PerformerType.entries.all { PerformerFilter.ALL.accepts(it) })
        assertTrue(PerformerFilter.MUSICIANS.accepts(PerformerType.MUSICIAN))
        assertFalse(PerformerFilter.MUSICIANS.accepts(PerformerType.BAND))
        assertTrue(PerformerFilter.BANDS.accepts(PerformerType.BAND))
        assertFalse(PerformerFilter.BANDS.accepts(PerformerType.MUSICIAN))
    }

    private fun TestScope.loadedStates(): List<PerformerListUiState> {
        val repository = FakePerformerRepository(hasCache = false, localResult = DataResult.Success(performers))
        val states = viewModel(repository).recordStates()
        advanceUntilIdle()
        return states
    }
}
