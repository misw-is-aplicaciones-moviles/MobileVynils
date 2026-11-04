package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakeCollectorRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.collector
import co.edu.uniandes.vinilos.testing.getOrAwaitValue
import kotlinx.coroutines.CompletableDeferred
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
@Suppress("MagicNumber") // Fixture ids and counts.
class CollectorListViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // Already by name, as the repository returns them.
    private val jaime = collector(id = 101, name = "Jaime Monsalve", albums = 1, favorites = 1)
    private val manolo = collector(id = 100, name = "Manolo Bellon", albums = 3, favorites = 2)
    private val collectors = listOf(jaime, manolo)

    private fun viewModel(repository: FakeCollectorRepository) =
        CollectorListViewModel(
            repository,
            FakeSessionRepository(UserRole.VISITOR),
            mainDispatcherRule.testDispatcher,
        )

    /** Records every state so a test can see transitions, not only the last value. */
    private fun CollectorListViewModel.recordStates(): List<CollectorListUiState> {
        val states = mutableListOf<CollectorListUiState>()
        uiState.observeForever(Observer { states += it })
        return states
    }

    @Test
    fun `without cache it shows loading and then the list`() =
        runTest {
            val repository = FakeCollectorRepository(hasCache = false, localResult = DataResult.Success(collectors))
            val states = viewModel(repository).recordStates()
            assertTrue(states.first().isLoading)

            advanceUntilIdle()

            assertFalse(states.last().isLoading)
            assertEquals(listOf("Jaime Monsalve", "Manolo Bellon"), states.last().collectors.map { it.name })
            assertEquals(1, repository.localCalls)
            assertEquals(0, repository.remoteCalls)
        }

    @Test
    fun `with cache it shows the list without loading and revalidates once`() =
        runTest {
            val repository =
                FakeCollectorRepository(
                    hasCache = true,
                    localResult = DataResult.Success(listOf(jaime)),
                    remoteResult = DataResult.Success(collectors),
                )
            val gate = CompletableDeferred<Unit>().also { repository.remoteGate = it }
            val states = viewModel(repository).recordStates()

            advanceUntilIdle()

            assertTrue(states.filter { it.collectors.isNotEmpty() }.none { it.isLoading })
            assertEquals(listOf("Jaime Monsalve"), states.last().collectors.map { it.name })
            assertTrue("Revalidation must be flagged while it waits", states.last().isRefreshing)

            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(states.last().isRefreshing)
            assertEquals(2, states.last().collectors.size)
            assertEquals(1, repository.remoteCalls)
        }

    @Test
    fun `an empty list is the empty state`() =
        runTest {
            val repository = FakeCollectorRepository(hasCache = false, localResult = DataResult.Success(emptyList()))
            val states = viewModel(repository).recordStates()

            advanceUntilIdle()

            assertTrue(states.last().isEmpty)
            assertNull(states.last().error)
        }

    @Test
    fun `failure without a list sets the error and it is not the empty state`() =
        runTest {
            val repository =
                FakeCollectorRepository(hasCache = false, localResult = DataResult.Failure(DataError.Http(500)))
            val states = viewModel(repository).recordStates()

            advanceUntilIdle()

            assertEquals(DataError.Http(500), states.last().error)
            assertFalse(states.last().isLoading)
            assertFalse(states.last().isEmpty)
        }

    @Test
    fun `refresh after an error loads the list`() =
        runTest {
            val repository =
                FakeCollectorRepository(hasCache = false, localResult = DataResult.Failure(DataError.Network))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            advanceUntilIdle()
            repository.remoteResult = DataResult.Success(collectors)

            viewModel.refresh()
            assertTrue(states.last().isLoading)
            assertNull(states.last().error)
            advanceUntilIdle()

            assertFalse(states.last().isLoading)
            assertNull(states.last().error)
            assertEquals(2, states.last().collectors.size)
        }

    @Test
    fun `failed revalidation with a list keeps it and emits the network notice`() =
        runTest {
            val repository =
                FakeCollectorRepository(
                    hasCache = true,
                    localResult = DataResult.Success(collectors),
                    remoteResult = DataResult.Failure(DataError.Network),
                )
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()

            advanceUntilIdle()

            assertEquals(2, states.last().collectors.size)
            assertNull(states.last().error)
            assertFalse(states.last().isRefreshing)
            val event = viewModel.events.getOrAwaitValue().getContentIfNotHandled()
            assertEquals(CollectorListEvent.ShowRefreshError(DataError.Network), event)
        }

    @Test
    fun `refresh is ignored while a load is in progress`() =
        runTest {
            val repository = FakeCollectorRepository(hasCache = false, localResult = DataResult.Success(collectors))
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
    fun `positions follow the order of the list with two digits`() =
        runTest {
            val many = List(10) { collector(id = it, name = "Coleccionista $it") }
            val repository = FakeCollectorRepository(hasCache = false, localResult = DataResult.Success(many))
            val states = viewModel(repository).recordStates()

            advanceUntilIdle()

            val positions = states.last().collectors.map { it.position }
            assertEquals("01", positions.first())
            assertEquals("10", positions.last())
        }

    @Test
    fun `the third line shows the counts, or no albums even if the collector has favorites`() =
        runTest {
            val noAlbums = collector(id = 1, name = "Sin álbumes")
            val onlyFavorites = collector(id = 2, name = "Solo favoritos", favorites = 3)
            val noFavorites = collector(id = 3, name = "Sin favoritos", albums = 2)
            val repository =
                FakeCollectorRepository(
                    hasCache = false,
                    localResult = DataResult.Success(listOf(noAlbums, onlyFavorites, noFavorites, manolo)),
                )
            val states = viewModel(repository).recordStates()

            advanceUntilIdle()

            assertEquals(
                listOf(
                    CollectorSummaryLine.NoAlbums,
                    CollectorSummaryLine.NoAlbums,
                    CollectorSummaryLine.Counts(albums = 2, favorites = 0),
                    CollectorSummaryLine.Counts(albums = 3, favorites = 2),
                ),
                states.last().collectors.map { it.summary },
            )
        }
}
