package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber") // Fixture ids and years.
class AlbumListViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val races = album(id = 2, name = "A Day at the Races", performer = "Queen", year = 1976)
    private val poeta = album(id = 4, name = "Poeta del pueblo", performer = null, year = 1974)
    private val albums = listOf(races, poeta)

    private fun viewModel(
        repository: FakeAlbumRepository,
        session: FakeSessionRepository = FakeSessionRepository(UserRole.VISITOR),
    ) = AlbumListViewModel(repository, session)

    /** Records every state so a test can see transitions, not only the last value. */
    private fun AlbumListViewModel.recordStates(): List<AlbumListUiState> {
        val states = mutableListOf<AlbumListUiState>()
        uiState.observeForever(Observer { states += it })
        return states
    }

    @Test
    fun `without cache it shows loading and then the list`() =
        runTest {
            val repository = FakeAlbumRepository(hasCache = false, localResult = DataResult.Success(albums))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            assertTrue(states.first().isLoading)

            advanceUntilIdle()

            val last = states.last()
            assertFalse(last.isLoading)
            assertEquals(listOf("A Day at the Races", "Poeta del pueblo"), last.albums.map { it.name })
            assertEquals(1, repository.localCalls)
            assertEquals(0, repository.remoteCalls)
        }

    @Test
    fun `with cache it shows the list without loading and revalidates once`() =
        runTest {
            val repository =
                FakeAlbumRepository(
                    hasCache = true,
                    localResult = DataResult.Success(listOf(races)),
                    remoteResult = DataResult.Success(albums),
                )
            val gate = CompletableDeferred<Unit>().also { repository.remoteGate = it }
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()

            advanceUntilIdle()

            val withList = states.filter { it.albums.isNotEmpty() }
            assertTrue(withList.none { it.isLoading })
            assertEquals(listOf("A Day at the Races"), states.last().albums.map { it.name })
            assertTrue("Revalidation must be flagged while it waits", states.last().isRefreshing)
            assertEquals(1, repository.remoteCalls)

            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(states.last().isRefreshing)
            assertEquals(2, states.last().albums.size)
            assertEquals(1, repository.remoteCalls)
        }

    @Test
    fun `failed revalidation with a list keeps it and emits the network notice`() =
        runTest {
            val repository =
                FakeAlbumRepository(
                    hasCache = true,
                    localResult = DataResult.Success(albums),
                    remoteResult = DataResult.Failure(DataError.Network),
                )
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()

            advanceUntilIdle()

            assertEquals(2, states.last().albums.size)
            assertNull(states.last().error)
            assertFalse(states.last().isRefreshing)
            val event = viewModel.events.getOrAwaitValue().getContentIfNotHandled()
            assertEquals(AlbumListEvent.ShowRefreshError(DataError.Network), event)
        }

    @Test
    fun `failure without a list sets the error`() =
        runTest {
            val repository =
                FakeAlbumRepository(hasCache = false, localResult = DataResult.Failure(DataError.Http(500)))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()

            advanceUntilIdle()

            assertEquals(DataError.Http(500), states.last().error)
            assertFalse(states.last().isLoading)
            assertTrue(states.last().albums.isEmpty())
            assertFalse("An error is not the empty state", states.last().isEmpty)
        }

    @Test
    fun `refresh after an error loads the list`() =
        runTest {
            val repository =
                FakeAlbumRepository(hasCache = false, localResult = DataResult.Failure(DataError.Network))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            advanceUntilIdle()
            repository.remoteResult = DataResult.Success(albums)

            viewModel.refresh()
            assertTrue(states.last().isLoading)
            assertNull(states.last().error)
            advanceUntilIdle()

            assertFalse(states.last().isLoading)
            assertNull(states.last().error)
            assertEquals(2, states.last().albums.size)
            assertEquals(1, repository.remoteCalls)
        }

    @Test
    fun `refresh with a list flags refreshing and a failure keeps the list and emits the error`() =
        runTest {
            val repository = FakeAlbumRepository(hasCache = false, localResult = DataResult.Success(albums))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()
            advanceUntilIdle()
            repository.remoteResult = DataResult.Failure(DataError.Http(503))

            viewModel.refresh()
            assertTrue(states.last().isRefreshing)
            assertFalse(states.last().isLoading)
            advanceUntilIdle()

            assertEquals(2, states.last().albums.size)
            assertNull(states.last().error)
            assertFalse(states.last().isRefreshing)
            val event = viewModel.events.getOrAwaitValue().getContentIfNotHandled()
            assertEquals(AlbumListEvent.ShowRefreshError(DataError.Http(503)), event)
        }

    @Test
    fun `refresh is ignored while a load is in progress`() =
        runTest {
            val repository = FakeAlbumRepository(hasCache = false, localResult = DataResult.Success(albums))
            val gate = CompletableDeferred<Unit>().also { repository.remoteGate = it }
            val viewModel = viewModel(repository)
            viewModel.recordStates()
            advanceUntilIdle()

            viewModel.refresh()
            viewModel.refresh()
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(1, repository.remoteCalls)
            gate.complete(Unit)
            advanceUntilIdle()
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(2, repository.remoteCalls)
        }

    @Test
    fun `items are numbered with two digits and carry a null artist when there is none`() =
        runTest {
            val repository = FakeAlbumRepository(hasCache = false, localResult = DataResult.Success(albums))
            val viewModel = viewModel(repository)
            val states = viewModel.recordStates()

            advanceUntilIdle()

            val items = states.last().albums
            assertEquals(listOf("01", "02"), items.map { it.position })
            assertEquals("Queen", items[0].artistName)
            assertNull(items[1].artistName)
            assertEquals("Rock", items[0].genreName)
            assertEquals(1976, items[0].year)
            assertEquals(2, items[0].id)
        }

    @Test
    fun `an empty catalog is the empty state`() =
        runTest {
            val repository = FakeAlbumRepository(hasCache = false, localResult = DataResult.Success(emptyList()))
            val states = viewModel(repository).recordStates()

            advanceUntilIdle()

            assertTrue(states.last().isEmpty)
        }

    @Test
    fun `canEdit follows the role and changes with it`() =
        runTest {
            val session = FakeSessionRepository(UserRole.VISITOR)
            val viewModel = viewModel(FakeAlbumRepository(localResult = DataResult.Success(albums)), session)
            val states = viewModel.recordStates()
            advanceUntilIdle()
            assertFalse(states.last().canEdit)

            session.selectRole(UserRole.COLLECTOR)
            assertTrue(states.last().canEdit)

            session.selectRole(UserRole.VISITOR)
            assertFalse(states.last().canEdit)
            assertEquals(2, states.last().albums.size)
        }

    @Test
    fun `onAlbumClicked opens the detail once`() {
        val viewModel = viewModel(FakeAlbumRepository())

        viewModel.onAlbumClicked(4)

        val event = viewModel.events.value
        assertEquals(AlbumListEvent.OpenDetail(4), event?.getContentIfNotHandled())
        assertNull(event?.getContentIfNotHandled())
    }

    @Test
    fun `onCreateAlbumClicked opens the track sheet`() {
        val viewModel = viewModel(FakeAlbumRepository())

        viewModel.onCreateAlbumClicked()

        assertEquals(AlbumListEvent.OpenTrackSheet, viewModel.events.value?.getContentIfNotHandled())
    }

    @Test
    fun `onRoleChipClicked clears the role and opens the selector`() {
        val session = FakeSessionRepository(UserRole.COLLECTOR)
        val viewModel = viewModel(FakeAlbumRepository(), session)

        viewModel.onRoleChipClicked()

        assertEquals(1, session.clearCount)
        assertNull(session.role.value)
        assertEquals(AlbumListEvent.OpenRoleSelector, viewModel.events.value?.getContentIfNotHandled())
    }

    private fun album(
        id: Int,
        name: String,
        performer: String?,
        year: Int,
    ) = Album(
        id = id,
        name = name,
        coverUrl = "https://example.com/$id.jpg",
        releaseDate = LocalDate.of(year, 1, 1),
        description = "",
        genre = if (performer == null) Genre.SALSA else Genre.ROCK,
        recordLabel = "Label",
        performerName = performer,
        tracks = emptyList(),
        performers = emptyList(),
        comments = emptyList(),
    )
}
