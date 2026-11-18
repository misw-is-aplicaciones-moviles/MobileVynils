package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PerformerAlbumPickerViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val free =
        Album(
            20,
            "Libre",
            "",
            LocalDate.of(1974, 1, 1),
            "",
            Genre.ROCK,
            "",
            null,
            emptyList(),
            emptyList(),
            emptyList(),
        )
    private val owned = free.copy(id = 21, name = "Propio")
    private val occupied =
        free.copy(
            id = 22,
            name = "Ajeno",
            performerName = "Otro",
            performers = listOf(PerformerSummary(9, "Otro", type = PerformerType.MUSICIAN)),
        )
    private val albums =
        FakeAlbumRepository().apply {
            associationCatalogResult = DataResult.Success(listOf(free, owned, occupied))
            associationDetailResult = DataResult.Success(free)
        }
    private val artists =
        FakePerformerRepository().apply {
            detailResult = DataResult.Success(PerformerDetail(musician(1, "Destino"), "", listOf(owned), emptyList()))
        }
    private val session = FakeSessionRepository(UserRole.COLLECTOR)

    @Test
    fun `catalog blocks existing and occupied rows and filters without losing selection`() =
        runTest {
            val vm = create()
            advanceUntilIdle()
            assertEquals(
                listOf(null, AlbumPickerBlock.ALREADY_ADDED, AlbumPickerBlock.ASSIGNED_ELSEWHERE),
                vm.uiState.value
                    ?.options
                    ?.map { it.block },
            )
            vm.onAlbumSelected(21)
            assertNull(vm.uiState.value?.selectedAlbumId)
            vm.onAlbumSelected(20)
            vm.onQueryChanged("ajeno")
            advanceUntilIdle()
            assertEquals(
                listOf(22),
                vm.uiState.value
                    ?.options
                    ?.map { it.album.id },
            )
            assertEquals(20, vm.uiState.value?.selectedAlbumId)
            vm.onQueryChanged("")
            advanceUntilIdle()
            assertTrue(checkNotNull(vm.uiState.value).options.first().isSelected)
        }

    @Test
    fun `verified writes return the typed musician and band without duplicate taps`() =
        runTest {
            PerformerType.entries.forEach { type ->
                artists.addedAlbumRequests.clear()
                val identity = if (type == PerformerType.BAND) band(1, "Destino") else musician(1, "Destino")
                artists.detailResult = DataResult.Success(PerformerDetail(identity, "", emptyList(), emptyList()))
                albums.associationDetailResults.add(DataResult.Success(free))
                albums.associationDetailResults.add(DataResult.Success(associated(type)))
                val vm = create(type = type)
                advanceUntilIdle()
                vm.onAlbumSelected(20)
                vm.onConfirm()
                vm.onConfirm()
                advanceUntilIdle()
                assertEquals(listOf(Triple(1, type, 20)), artists.addedAlbumRequests)
                assertEquals(AlbumPickerPhase.FINISHED, vm.uiState.value?.phase)
                assertEquals(
                    PerformerAlbumPickerEvent.Associated(20, false),
                    vm.events.value?.getContentIfNotHandled(),
                )
            }
        }

    @Test
    fun `preflight recognizes an existing association and does not post`() =
        runTest {
            albums.associationDetailResult = DataResult.Success(associated())
            val vm = create()
            advanceUntilIdle()
            vm.onAlbumSelected(20)
            vm.onConfirm()
            advanceUntilIdle()
            assertTrue(artists.addedAlbumRequests.isEmpty())
            assertEquals(PerformerAlbumPickerEvent.Associated(20, true), vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `preflight blocks an album assigned after loading the catalog`() =
        runTest {
            albums.associationDetailResult = DataResult.Success(occupied.copy(id = 20))
            val vm = create()
            advanceUntilIdle()
            vm.onAlbumSelected(20)
            vm.onConfirm()
            advanceUntilIdle()
            assertTrue(artists.addedAlbumRequests.isEmpty())
            assertFalse(checkNotNull(vm.uiState.value).canConfirm)
            assertEquals(AlbumPickerNotice.UNAVAILABLE, vm.uiState.value?.notice)
        }

    @Test
    fun `unreadable preflight never posts and permits another check`() =
        runTest {
            albums.associationDetailResult = DataResult.Failure(DataError.Network)
            val vm = create()
            advanceUntilIdle()
            vm.onAlbumSelected(20)
            vm.onConfirm()
            advanceUntilIdle()
            assertTrue(artists.addedAlbumRequests.isEmpty())
            assertTrue(checkNotNull(vm.uiState.value).canConfirm)
            assertEquals(AlbumPickerNotice.READ_FAILED, vm.uiState.value?.notice)
        }

    @Test
    fun `lost write response is reconciled by reading before announcing success`() =
        runTest {
            artists.addAlbumResult = DataResult.Failure(DataError.Network)
            albums.associationDetailResults.add(DataResult.Success(free))
            albums.associationDetailResults.add(DataResult.Success(associated()))
            val vm = create()
            advanceUntilIdle()
            vm.onAlbumSelected(20)
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(1, artists.addedAlbumRequests.size)
            assertEquals(AlbumPickerPhase.FINISHED, vm.uiState.value?.phase)
        }

    @Test
    fun `uncertain result locks writes and confirm only checks again`() =
        runTest {
            albums.associationDetailResults.add(DataResult.Success(free))
            albums.associationDetailResult = DataResult.Failure(DataError.Network)
            val vm = create()
            advanceUntilIdle()
            vm.onAlbumSelected(20)
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(AlbumPickerPhase.UNKNOWN_RESULT, vm.uiState.value?.phase)
            vm.onAlbumSelected(21)
            vm.onQueryChanged("otro")
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(1, artists.addedAlbumRequests.size)
            assertEquals(20, vm.uiState.value?.selectedAlbumId)
            assertEquals("", vm.uiState.value?.query)
            albums.associationDetailResult = DataResult.Success(associated())
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(AlbumPickerPhase.FINISHED, vm.uiState.value?.phase)
            assertEquals(1, artists.addedAlbumRequests.size)
        }

    @Test
    fun `restoring a pending write never automatically posts again`() =
        runTest {
            val saved = arguments().apply { set("artistAlbumWritePending", 20) }
            val vm = create(saved)
            advanceUntilIdle()
            assertTrue(checkNotNull(vm.uiState.value).canCheck)
            assertTrue(artists.addedAlbumRequests.isEmpty())
            albums.associationDetailResult = DataResult.Success(associated())
            vm.onConfirm()
            advanceUntilIdle()
            assertTrue(artists.addedAlbumRequests.isEmpty())
            assertEquals(AlbumPickerPhase.FINISHED, vm.uiState.value?.phase)
        }

    @Test
    fun `visitor and invalid identities never load or write`() =
        runTest {
            session.selectRole(UserRole.VISITOR)
            val visitor = create()
            visitor.onConfirm()
            advanceUntilIdle()
            assertEquals(0, albums.associationCatalogCalls)
            session.selectRole(UserRole.COLLECTOR)
            val invalid = create(arguments().apply { set(PerformerAlbumPickerViewModel.PERFORMER_ID, 0) })
            invalid.retry()
            advanceUntilIdle()
            assertEquals(AlbumPickerPhase.LOAD_ERROR, invalid.uiState.value?.phase)
            assertTrue(artists.addedAlbumRequests.isEmpty())
        }

    @Test
    fun `slow preflight blocks close and repeated actions`() =
        runTest {
            val vm = create()
            advanceUntilIdle()
            albums.detailGate = CompletableDeferred()
            vm.onAlbumSelected(20)
            vm.onConfirm()
            runCurrent()
            vm.onClose()
            vm.onConfirm()
            assertTrue(checkNotNull(vm.uiState.value).isBusy)
            assertNull(vm.events.value)
            albums.detailGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, artists.addedAlbumRequests.size)
        }

    @Test
    fun `missing subtype uses typed detail and a failed read never claims success`() =
        runTest {
            albums.associationDetailResult =
                DataResult.Success(
                    associated().copy(
                        performers = listOf(PerformerSummary(1, "Destino")),
                    ),
                )
            val vm = create()
            advanceUntilIdle()
            artists.detailResult = DataResult.Failure(DataError.Network)
            vm.onAlbumSelected(20)
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(AlbumPickerNotice.READ_FAILED, vm.uiState.value?.notice)
            assertTrue(artists.addedAlbumRequests.isEmpty())
            assertNull(vm.events.value)
        }

    private fun create(
        saved: SavedStateHandle = arguments(),
        type: PerformerType = PerformerType.MUSICIAN,
    ): PerformerAlbumPickerViewModel {
        saved[PerformerAlbumPickerViewModel.PERFORMER_TYPE] = type.name
        return PerformerAlbumPickerViewModel(saved, albums, artists, session, mainDispatcherRule.testDispatcher)
    }

    private fun arguments() = SavedStateHandle(mapOf(PerformerAlbumPickerViewModel.PERFORMER_ID to 1))

    private fun associated(type: PerformerType = PerformerType.MUSICIAN) =
        free.copy(
            performerName = "Destino",
            performers = listOf(PerformerSummary(1, "Destino", type = type)),
        )
}
