package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.CollectorAlbum
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CollectorAlbumPickerViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val buscando = album(1, "Buscando América", "Rubén Blades", Genre.SALSA)
    private val poeta = album(2, "Poeta del pueblo", "Rubén Blades", Genre.SALSA)
    private val queen = album(3, "A Night at the Opera", "Queen", Genre.ROCK)
    private val catalog = listOf(buscando, poeta, queen)

    @Test
    fun `the loaded catalog is published with its rows in the same state`() =
        runTest {
            val vm = picker()
            val states = recordStates(vm)

            advanceUntilIdle()

            val state = checkNotNull(vm.uiState.value)
            assertEquals(catalog, state.visibleAlbums.map { it.album })
            assertFalse(state.isLoading)
            assertTrue("no result message while loading", states.none { it.hasNoSearchResults })
        }

    @Test
    fun `the search matches name, performer and genre ignoring case`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            vm.onQueryChanged("  OPERA ")
            advanceUntilIdle()
            assertEquals(listOf(queen), visible(vm))

            vm.onQueryChanged("rubén")
            advanceUntilIdle()
            assertEquals(listOf(buscando, poeta), visible(vm))

            vm.onQueryChanged("rock")
            advanceUntilIdle()
            assertEquals(listOf(queen), visible(vm))

            vm.onQueryChanged("")
            advanceUntilIdle()
            assertEquals(catalog, visible(vm))
        }

    @Test
    fun `a search without matches reports no results`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()

            vm.onQueryChanged("jazz")
            advanceUntilIdle()

            val state = checkNotNull(vm.uiState.value)
            assertTrue(state.visibleAlbums.isEmpty())
            assertTrue(state.hasNoSearchResults)
            assertFalse(state.isEmpty)
        }

    @Test
    fun `only the latest of two quick searches is published`() =
        runTest {
            val vm = picker()
            advanceUntilIdle()
            val states = recordStates(vm)

            vm.onQueryChanged("opera")
            vm.onQueryChanged("poeta")
            advanceUntilIdle()

            assertEquals(listOf(poeta), visible(vm))
            val published = states.map { state -> state.visibleAlbums.map { it.album } }
            assertFalse("older search published", listOf(queen) in published)
        }

    @Test
    fun `rows mark the selected album and the ones already in the collection`() =
        runTest {
            val owned = CollectorAlbum(queen.id, queen.name, queen.coverUrl, 10, AlbumStatus.ACTIVE)
            val vm = picker(owned = listOf(owned))
            advanceUntilIdle()

            vm.onAlbumSelected(poeta.id)
            vm.onAlbumSelected(queen.id) // already added: ignored
            advanceUntilIdle()

            val rows = checkNotNull(vm.uiState.value).visibleAlbums.associateBy { it.album.id }
            assertTrue(checkNotNull(rows[poeta.id]).isSelected)
            assertFalse(checkNotNull(rows[buscando.id]).isSelected)
            assertTrue(checkNotNull(rows[queen.id]).isAlreadyAdded)
            assertFalse(checkNotNull(rows[queen.id]).isSelected)
            assertEquals(poeta, vm.uiState.value?.selectedAlbum)
            assertTrue(checkNotNull(vm.uiState.value).canContinue)
        }

    @Test
    fun `a restored search is applied to the loaded catalog`() =
        runTest {
            val vm = picker(savedQuery = "salsa")

            advanceUntilIdle()

            assertEquals("salsa", vm.uiState.value?.query)
            assertEquals(listOf(buscando, poeta), visible(vm))
        }

    @Test
    fun `a blank query keeps every album in order`() {
        val rows = buildVisibleAlbums(catalog, emptySet(), "   ", selectedAlbumId = null)

        assertEquals(catalog, rows.map { it.album })
        assertTrue(rows.none { it.isSelected || it.isAlreadyAdded })
    }

    private fun picker(
        owned: List<CollectorAlbum> = emptyList(),
        savedQuery: String? = null,
    ): CollectorAlbumPickerViewModel {
        val albums = FakeAlbumRepository(localResult = DataResult.Success(catalog))
        val collectors = FakeCollectorRepository()
        collectors.detailResult = DataResult.Success(collectorDetail(id = 100, albums = owned))
        val saved = mutableMapOf<String, Any?>(CollectorAlbumPickerViewModel.ARG_COLLECTOR_ID to 100)
        savedQuery?.let { saved["collectorAlbumPickerQuery"] = it }
        return CollectorAlbumPickerViewModel(
            savedState = SavedStateHandle(saved),
            albumRepository = albums,
            collectorRepository = collectors,
            sessionRepository = FakeSessionRepository(UserRole.COLLECTOR),
            defaultDispatcher = mainDispatcherRule.testDispatcher,
        )
    }

    private fun recordStates(vm: CollectorAlbumPickerViewModel): List<CollectorAlbumPickerUiState> {
        val states = mutableListOf<CollectorAlbumPickerUiState>()
        vm.uiState.observeForever(Observer { states += it })
        return states
    }

    private fun visible(vm: CollectorAlbumPickerViewModel) =
        checkNotNull(vm.uiState.value).visibleAlbums.map { it.album }

    private fun album(
        id: Int,
        name: String,
        performer: String,
        genre: Genre,
    ) = Album(
        id = id,
        name = name,
        coverUrl = "https://cover/$id",
        releaseDate = LocalDate.of(1984, 1, 1),
        description = "",
        genre = genre,
        recordLabel = "Elektra",
        performerName = performer,
        tracks = emptyList(),
        performers = emptyList(),
        comments = emptyList(),
    )
}
