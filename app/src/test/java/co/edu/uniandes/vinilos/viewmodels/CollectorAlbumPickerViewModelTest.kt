package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.CollectorAlbum
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.FakeCollectorRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.collectorDetail
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

/** Catalog, search and selection of the album picker that opens the price sheet (HU11). */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber") // Ids of the scenarios.
class CollectorAlbumPickerViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val buscandoAmerica = album(id = 100, name = "Buscando América", performerName = "Rubén Blades")
    private val poetaDelPueblo = album(id = 200, name = "Poeta del pueblo", genre = Genre.FOLK)
    private val catalog = listOf(buscandoAmerica, poetaDelPueblo)
    private val addedLink = CollectorAlbum(200, "Poeta del pueblo", "https://cover/200", 35, AlbumStatus.ACTIVE)

    private fun album(
        id: Int,
        name: String,
        performerName: String? = null,
        genre: Genre = Genre.ROCK,
    ) = Album(
        id = id,
        name = name,
        coverUrl = "https://cover/$id",
        releaseDate = LocalDate.of(1980, 1, 1),
        description = "",
        genre = genre,
        recordLabel = "EMI",
        performerName = performerName,
        tracks = emptyList(),
        performers = emptyList(),
        comments = emptyList(),
    )

    private fun savedState(collectorId: Int = 100) =
        SavedStateHandle(mapOf(CollectorAlbumPickerViewModel.ARG_COLLECTOR_ID to collectorId))

    private fun catalogRepository() =
        FakeAlbumRepository().apply {
            localResult = DataResult.Success(catalog)
            remoteResult = DataResult.Success(catalog)
        }

    private fun collectorRepository(vararg albums: CollectorAlbum) =
        FakeCollectorRepository().apply {
            detailResult = DataResult.Success(collectorDetail(albums = albums.toList()))
        }

    private fun viewModel(
        repository: FakeAlbumRepository = catalogRepository(),
        collector: FakeCollectorRepository = collectorRepository(),
        session: FakeSessionRepository = FakeSessionRepository(UserRole.COLLECTOR),
        handle: SavedStateHandle = savedState(),
    ) = CollectorAlbumPickerViewModel(handle, repository, collector, session)

    private fun CollectorAlbumPickerViewModel.nextEvent() = events.value?.getContentIfNotHandled()

    @Test
    fun `the load shows the catalog and the albums already added to the collector`() =
        runTest {
            val repository = catalogRepository()
            val collector = collectorRepository(addedLink)

            val viewModel = viewModel(repository = repository, collector = collector)
            advanceUntilIdle()

            val state = checkNotNull(viewModel.uiState.value)
            assertEquals("Manolo Bellon", state.collectorName)
            assertEquals(catalog, state.albums)
            assertEquals(setOf(200), state.addedAlbumIds)
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(1, repository.localCalls)
            assertEquals(1, collector.detailCalls)
            assertEquals(false, collector.lastDetailRefresh)
        }

    @Test
    fun `the search matches name artist and genre ignoring case`() =
        runTest {
            val viewModel = viewModel(collector = collectorRepository(addedLink))
            advanceUntilIdle()

            viewModel.onQueryChanged("  busCANdo  ")
            assertEquals(
                listOf(100),
                viewModel.uiState.value
                    ?.visibleAlbums
                    ?.map { it.album.id },
            )

            viewModel.onQueryChanged("rubén")
            assertEquals(
                listOf(100),
                viewModel.uiState.value
                    ?.visibleAlbums
                    ?.map { it.album.id },
            )

            viewModel.onQueryChanged("folk")
            assertEquals(
                listOf(200),
                viewModel.uiState.value
                    ?.visibleAlbums
                    ?.map { it.album.id },
            )

            viewModel.onQueryChanged("")
            assertEquals(
                listOf(100, 200),
                viewModel.uiState.value
                    ?.visibleAlbums
                    ?.map { it.album.id },
            )
        }

    @Test
    fun `a search without matches is the empty search state`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onQueryChanged("zzz")

            val state = checkNotNull(viewModel.uiState.value)
            assertTrue(state.visibleAlbums.isEmpty())
            assertTrue(state.hasNoSearchResults)
            assertFalse(state.isEmpty)
        }

    @Test
    fun `an added album and one outside the catalog cannot be selected`() =
        runTest {
            val viewModel = viewModel(collector = collectorRepository(addedLink))
            advanceUntilIdle()

            viewModel.onAlbumSelected(200)
            viewModel.onAlbumSelected(999)

            assertNull(viewModel.uiState.value?.selectedAlbumId)
            assertFalse(checkNotNull(viewModel.uiState.value).canContinue)

            viewModel.onAlbumSelected(100)
            assertEquals(100, viewModel.uiState.value?.selectedAlbumId)
            assertTrue(checkNotNull(viewModel.uiState.value).canContinue)
        }

    @Test
    fun `continue opens the sheet with the selected album`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onContinue()
            assertNull(viewModel.nextEvent())

            viewModel.onAlbumSelected(100)
            viewModel.onContinue()

            assertEquals(
                CollectorAlbumPickerEvent.ContinueWithAlbum(100, 100, "Buscando América"),
                viewModel.nextEvent(),
            )
        }

    @Test
    fun `a failed load shows the error and the retry reloads with force refresh`() =
        runTest {
            val repository =
                FakeAlbumRepository().apply {
                    localResult = DataResult.Failure(DataError.Network)
                    remoteResult = DataResult.Success(catalog)
                }
            val viewModel = viewModel(repository = repository)
            advanceUntilIdle()

            assertEquals(DataError.Network, viewModel.uiState.value?.error)
            assertFalse(checkNotNull(viewModel.uiState.value).isLoading)
            assertEquals(1, repository.localCalls)

            viewModel.retry()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value?.error)
            assertEquals(catalog, viewModel.uiState.value?.albums)
            assertEquals(1, repository.remoteCalls)
        }

    @Test
    fun `an invalid collector id shows a safe state without loading`() =
        runTest {
            val repository = catalogRepository()
            val collector = collectorRepository()

            val viewModel = viewModel(repository = repository, collector = collector, handle = savedState(0))
            advanceUntilIdle()

            val state = checkNotNull(viewModel.uiState.value)
            assertFalse(state.hasValidIdentity)
            assertEquals(DataError.Serialization, state.error)
            assertFalse(state.isLoading)
            assertEquals(0, repository.localCalls)
            assertEquals(0, collector.detailCalls)
        }

    @Test
    fun `a visitor opens the role selector and does not load the catalog`() =
        runTest {
            val repository = catalogRepository()
            val session = FakeSessionRepository(UserRole.VISITOR)

            val viewModel = viewModel(repository = repository, session = session)
            advanceUntilIdle()

            assertEquals(CollectorAlbumPickerEvent.OpenRoleSelector, viewModel.nextEvent())
            assertNull(viewModel.uiState.value?.error)
            assertEquals(0, repository.localCalls)
        }

    @Test
    fun `losing the collector role opens the role selector`() =
        runTest {
            val session = FakeSessionRepository(UserRole.COLLECTOR)
            val viewModel = viewModel(session = session)
            advanceUntilIdle()
            viewModel.uiState.observeForever { }

            session.selectRole(UserRole.VISITOR)

            assertEquals(UserRole.VISITOR, viewModel.uiState.value?.role)
            assertEquals(CollectorAlbumPickerEvent.OpenRoleSelector, viewModel.nextEvent())
        }

    @Test
    fun `recreating the view model keeps the query and the selection`() =
        runTest {
            val handle = savedState()
            val first = viewModel(handle = handle)
            advanceUntilIdle()
            first.onQueryChanged("buscando")
            first.onAlbumSelected(100)

            val recreated = viewModel(handle = handle)
            advanceUntilIdle()

            val state = checkNotNull(recreated.uiState.value)
            assertEquals("buscando", state.query)
            assertEquals(100, state.selectedAlbumId)
            assertTrue(state.canContinue)
        }
}
