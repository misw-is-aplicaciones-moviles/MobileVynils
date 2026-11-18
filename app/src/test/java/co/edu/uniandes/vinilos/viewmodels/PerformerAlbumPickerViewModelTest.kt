package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
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
            assertEquals(
                PerformerAlbumPickerEvent.Associated(20, true),
                vm.events.value?.getContentIfNotHandled(),
            )
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
            val identities = listOf(0 to "MUSICIAN", -1 to "BAND", 1 to "UNKNOWN", 1 to null)
            identities.forEach { (id, type) ->
                val saved =
                    SavedStateHandle(
                        mapOf(
                            PerformerAlbumPickerViewModel.PERFORMER_ID to id,
                            PerformerAlbumPickerViewModel.PERFORMER_TYPE to type,
                        ),
                    )
                val invalid =
                    PerformerAlbumPickerViewModel(
                        saved,
                        albums,
                        artists,
                        session,
                        mainDispatcherRule.testDispatcher,
                    )
                invalid.retry()
                invalid.onConfirm()
                advanceUntilIdle()
                assertEquals(AlbumPickerPhase.LOAD_ERROR, invalid.uiState.value?.phase)
                assertEquals(0, albums.associationCatalogCalls)
            }
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

    @Test
    fun `catalog or artist load failure disables confirmation and retry recovers`() =
        runTest {
            val detail = checkNotNull((artists.detailResult as? DataResult.Success)?.value)
            listOf(true, false).forEach { failCatalog ->
                albums.associationCatalogResult =
                    if (failCatalog) {
                        DataResult.Failure(DataError.Network)
                    } else {
                        DataResult.Success(listOf(free))
                    }
                artists.detailResult =
                    if (failCatalog) {
                        DataResult.Success(detail)
                    } else {
                        DataResult.Failure(DataError.Http(404))
                    }
                val vm = create()
                advanceUntilIdle()
                assertEquals(AlbumPickerPhase.LOAD_ERROR, vm.uiState.value?.phase)
                assertFalse(checkNotNull(vm.uiState.value).canConfirm)
                vm.onAlbumSelected(20)
                vm.onConfirm()
                assertTrue(artists.addedAlbumRequests.isEmpty())
                albums.associationCatalogResult = DataResult.Success(listOf(free))
                artists.detailResult = DataResult.Success(detail)
                vm.retry()
                advanceUntilIdle()
                assertEquals(AlbumPickerPhase.CONTENT, vm.uiState.value?.phase)
                assertNull(vm.uiState.value?.error)
                assertEquals(listOf(free), vm.uiState.value?.albums)
            }
        }

    @Test
    fun `empty catalog and artist search never enable occupied or unknown album ids`() =
        runTest {
            val vm = create()
            advanceUntilIdle()
            vm.onQueryChanged("  oTrO  ")
            advanceUntilIdle()
            assertEquals(
                listOf(22),
                vm.uiState.value
                    ?.options
                    ?.map { it.album.id },
            )
            listOf(22, 999, -1).forEach(vm::onAlbumSelected)
            assertNull(vm.uiState.value?.selectedAlbumId)
            assertFalse(checkNotNull(vm.uiState.value).canConfirm)
            vm.onQueryChanged("missing")
            advanceUntilIdle()
            assertTrue(checkNotNull(vm.uiState.value).options.isEmpty())
            albums.associationCatalogResult = DataResult.Success(emptyList())
            vm.onQueryChanged("")
            vm.retry()
            advanceUntilIdle()
            vm.onConfirm()
            assertEquals(AlbumPickerPhase.CONTENT, vm.uiState.value?.phase)
            assertTrue(checkNotNull(vm.uiState.value).options.isEmpty())
            assertTrue(artists.addedAlbumRequests.isEmpty())
        }

    @Test
    fun `restoration keeps query and free selection but drops a newly occupied album`() =
        runTest {
            val saved = arguments()
            val vm = create(saved)
            advanceUntilIdle()
            vm.onAlbumSelected(20)
            vm.onQueryChanged("  LiBrE  ")
            advanceUntilIdle()
            val snapshot = saved.keys().associateWith { saved.get<Any?>(it) }
            val restored = create(SavedStateHandle(snapshot))
            advanceUntilIdle()
            assertEquals("  LiBrE  ", restored.uiState.value?.query)
            assertEquals(20, restored.uiState.value?.selectedAlbumId)
            assertTrue(checkNotNull(restored.uiState.value).canConfirm)
            albums.associationCatalogResult = DataResult.Success(listOf(occupied.copy(id = 20)))
            val changed = create(SavedStateHandle(snapshot))
            advanceUntilIdle()
            assertNull(changed.uiState.value?.selectedAlbumId)
            assertFalse(checkNotNull(changed.uiState.value).canConfirm)
            assertTrue(artists.addedAlbumRequests.isEmpty())
        }

    @Test
    fun `losing collector role during preflight prevents the post`() =
        runTest {
            val vm = create()
            val observer = Observer<PerformerAlbumPickerUiState> {}
            vm.uiState.observeForever(observer)
            try {
                advanceUntilIdle()
                albums.detailGate = CompletableDeferred()
                vm.onAlbumSelected(20)
                vm.onConfirm()
                runCurrent()
                session.clearRole()
                albums.detailGate?.complete(Unit)
                advanceUntilIdle()
                vm.onConfirm()
                assertTrue(artists.addedAlbumRequests.isEmpty())
                assertEquals(PerformerAlbumPickerEvent.OpenRoleSelector, vm.events.value?.getContentIfNotHandled())
            } finally {
                vm.uiState.removeObserver(observer)
            }
        }

    @Test
    fun `losing role during a sent write never emits success to the next session`() =
        runTest {
            albums.associationDetailResults.add(DataResult.Success(free))
            albums.associationDetailResult = DataResult.Success(associated())
            artists.addAlbumGate = CompletableDeferred()
            val vm = create()
            val observer = Observer<PerformerAlbumPickerUiState> {}
            vm.uiState.observeForever(observer)
            try {
                advanceUntilIdle()
                vm.onAlbumSelected(20)
                vm.onConfirm()
                runCurrent()
                assertEquals(1, artists.addedAlbumRequests.size)
                session.clearRole()
                assertEquals(PerformerAlbumPickerEvent.OpenRoleSelector, vm.events.value?.getContentIfNotHandled())
                artists.addAlbumGate?.complete(Unit)
                advanceUntilIdle()
                vm.onConfirm()
                assertEquals(1, artists.addedAlbumRequests.size)
                assertNull(vm.events.value?.getContentIfNotHandled())
            } finally {
                vm.uiState.removeObserver(observer)
            }
        }

    @Test
    fun `an unconfirmed failed post requires an explicit retry and does not claim success`() =
        runTest {
            artists.addAlbumResult = DataResult.Failure(DataError.Http(500))
            val vm = create()
            advanceUntilIdle()
            vm.onAlbumSelected(20)
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(AlbumPickerNotice.NOT_SAVED, vm.uiState.value?.notice)
            assertTrue(checkNotNull(vm.uiState.value).canConfirm)
            assertNull(vm.events.value)
            assertEquals(1, artists.addedAlbumRequests.size)
            albums.associationDetailResults.add(DataResult.Success(free))
            albums.associationDetailResults.add(DataResult.Success(associated()))
            artists.addAlbumResult = DataResult.Success(Unit)
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(2, artists.addedAlbumRequests.size)
            assertEquals(PerformerAlbumPickerEvent.Associated(20, false), vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `untyped membership is confirmed against typed detail without posting`() =
        runTest {
            val detail = checkNotNull((artists.detailResult as? DataResult.Success)?.value)
            albums.associationDetailResult =
                DataResult.Success(
                    associated().copy(
                        performers = listOf(PerformerSummary(1, "Destino")),
                    ),
                )
            listOf(true, false).forEach { belongs ->
                artists.detailResult = DataResult.Success(detail)
                val vm = create()
                advanceUntilIdle()
                val confirmedAlbums = if (belongs) listOf(free) else emptyList()
                artists.detailResult = DataResult.Success(detail.copy(albums = confirmedAlbums))
                vm.onAlbumSelected(20)
                vm.onConfirm()
                advanceUntilIdle()
                if (belongs) {
                    assertEquals(
                        PerformerAlbumPickerEvent.Associated(20, true),
                        vm.events.value?.getContentIfNotHandled(),
                    )
                } else {
                    assertEquals(AlbumPickerNotice.UNAVAILABLE, vm.uiState.value?.notice)
                    assertNull(vm.events.value)
                }
                assertTrue(artists.addedAlbumRequests.isEmpty())
                assertTrue(artists.detailRequests.last().third)
            }
        }

    @Test
    fun `unreadable typed membership after posting keeps the result locked until checked`() =
        runTest {
            albums.associationDetailResults.add(DataResult.Success(free))
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
            assertEquals(AlbumPickerPhase.UNKNOWN_RESULT, vm.uiState.value?.phase)
            assertFalse(checkNotNull(vm.uiState.value).canConfirm)
            assertTrue(checkNotNull(vm.uiState.value).canCheck)
            assertNull(vm.events.value)
            artists.detailResult =
                DataResult.Success(
                    PerformerDetail(musician(1, "Destino"), "", listOf(free), emptyList()),
                )
            vm.onConfirm()
            advanceUntilIdle()
            assertEquals(1, artists.addedAlbumRequests.size)
            assertEquals(PerformerAlbumPickerEvent.Associated(20, false), vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `clearing a pending write cancels its job and restoration only checks the result`() =
        runTest {
            val saved = arguments()
            artists.addAlbumGate = CompletableDeferred()
            val vm = create(saved)
            val store = ViewModelStore().apply { put("picker", vm) }
            advanceUntilIdle()
            vm.onAlbumSelected(20)
            vm.onConfirm()
            runCurrent()
            assertEquals(1, artists.addedAlbumRequests.size)
            store.clear()
            artists.addAlbumGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(20), albums.associationDetailRequests)
            val restored = create(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
            advanceUntilIdle()
            assertTrue(checkNotNull(restored.uiState.value).canCheck)
            restored.onClose()
            assertEquals(PerformerAlbumPickerEvent.Closed(true), restored.events.value?.getContentIfNotHandled())
            albums.associationDetailResult = DataResult.Success(associated())
            restored.onConfirm()
            advanceUntilIdle()
            assertEquals(1, artists.addedAlbumRequests.size)
            assertEquals(
                PerformerAlbumPickerEvent.Associated(20, false),
                restored.events.value?.getContentIfNotHandled(),
            )
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
