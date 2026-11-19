package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.CommentAuthor
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Track
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.network.dto.toModel
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.FakeCollectorRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val album = Gson().fromJson(readResource("api/albums.json"), Array<AlbumDto>::class.java).first().toModel()
    private val manolo = CommentAuthor(collectorId = 100, name = "Manolo Bellon")
    private val jaime = CommentAuthor(collectorId = 101, name = "Jaime Monsalve")
    private val commented =
        album.copy(comments = listOf(Comment(100, "Relevante", 5), Comment(101, "Me encanta", 4)))

    private fun detail(
        id: Int,
        repository: FakeAlbumRepository,
        session: FakeSessionRepository,
        collectors: FakeCollectorRepository = FakeCollectorRepository(),
    ) = AlbumDetailViewModel(id, repository, session, collectors, mainDispatcherRule.testDispatcher)

    private fun loaded(
        result: DataResult<Album, DataError> = DataResult.Success(commented),
        gate: CompletableDeferred<Unit>? = null,
    ) = FakeAlbumRepository().apply {
        detailResult = result
        detailGate = gate
    }

    private fun AlbumDetailViewModel.authors(): List<CommentAuthor?> =
        checkNotNull(uiState.value?.album).comments.map { it.author }

    @Test
    fun `initial load publishes the complete album and current role`() =
        runTest {
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Success(album) }
            val viewModel = detail(album.id, repository, FakeSessionRepository(UserRole.VISITOR))
            assertTrue(checkNotNull(viewModel.uiState.value).isLoading)

            advanceUntilIdle()

            assertEquals(
                AlbumDetailUiState(album = album, isLoading = false, role = UserRole.VISITOR),
                viewModel.uiState.value,
            )
            assertEquals(listOf(album.id to false), repository.detailRequests)
        }

    @Test
    fun `missing album publishes 404 without content`() =
        runTest {
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Failure(DataError.Http(404)) }
            val viewModel = detail(999, repository, FakeSessionRepository())

            advanceUntilIdle()

            assertEquals(
                AlbumDetailUiState(isLoading = false, error = DataError.Http(404)),
                viewModel.uiState.value,
            )
            assertEquals(listOf(999 to false), repository.detailRequests)
        }

    @Test
    fun `remote failures publish an error without album content`() =
        runTest {
            val errors = listOf(DataError.Network, DataError.Http(503), DataError.Serialization)
            for (error in errors) {
                val repository = FakeAlbumRepository().apply { detailResult = DataResult.Failure(error) }
                val viewModel = detail(album.id, repository, FakeSessionRepository())

                advanceUntilIdle()

                assertEquals(AlbumDetailUiState(isLoading = false, error = error), viewModel.uiState.value)
                assertNull(viewModel.events.value)
            }
        }

    @Test
    fun `retry clears the error and forces a new query for the same id`() =
        runTest {
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Failure(DataError.Network) }
            val viewModel = detail(album.id, repository, FakeSessionRepository(UserRole.COLLECTOR))
            advanceUntilIdle()
            assertEquals(DataError.Network, viewModel.uiState.value?.error)
            repository.detailResult = DataResult.Success(album)

            viewModel.retry()

            assertTrue(checkNotNull(viewModel.uiState.value).isLoading)
            assertNull(viewModel.uiState.value?.error)
            advanceUntilIdle()
            assertEquals(
                AlbumDetailUiState(album = album, isLoading = false, role = UserRole.COLLECTOR),
                viewModel.uiState.value,
            )
            assertEquals(listOf(album.id to false, album.id to true), repository.detailRequests)
        }

    @Test
    fun `retry does not duplicate an active request`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakeAlbumRepository().apply {
                    detailResult = DataResult.Success(album)
                    detailGate = gate
                }
            val viewModel = detail(album.id, repository, FakeSessionRepository())
            advanceUntilIdle()
            assertTrue(checkNotNull(viewModel.uiState.value).isLoading)

            viewModel.retry()
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(listOf(album.id to false), repository.detailRequests)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(album, viewModel.uiState.value?.album)
            assertFalse(checkNotNull(viewModel.uiState.value).isLoading)
        }

    @Test
    fun `an album with empty sections is successful content`() =
        runTest {
            val emptySections = album.copy(tracks = emptyList(), comments = emptyList())
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Success(emptySections) }
            val viewModel = detail(album.id, repository, FakeSessionRepository())

            advanceUntilIdle()

            assertEquals(AlbumDetailUiState(album = emptySections, isLoading = false), viewModel.uiState.value)
        }

    @Test
    fun `created track is appended to the currently displayed album without another request`() =
        runTest {
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Success(album) }
            val viewModel = detail(album.id, repository, FakeSessionRepository(UserRole.COLLECTOR))
            advanceUntilIdle()
            val track = Track(999, "Bohemian Rhapsody", "5:55")

            viewModel.onTrackCreated(track)

            assertEquals(
                album.tracks + track,
                viewModel.uiState.value
                    ?.album
                    ?.tracks,
            )
            assertEquals(listOf(album.id to false), repository.detailRequests)
        }

    @Test
    fun `invalid ids are rejected before requesting data`() {
        val repository = FakeAlbumRepository()

        for (id in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                detail(id, repository, FakeSessionRepository())
            }
        }

        assertTrue(repository.detailRequests.isEmpty())
    }

    @Test
    fun `session role changes update the header state without reloading the album`() =
        runTest {
            val session = FakeSessionRepository(UserRole.VISITOR)
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Success(album) }
            val viewModel = detail(album.id, repository, session)
            val observer = Observer<AlbumDetailUiState> {}
            viewModel.uiState.observeForever(observer)
            try {
                advanceUntilIdle()

                session.selectRole(UserRole.COLLECTOR)

                assertEquals(UserRole.COLLECTOR, viewModel.uiState.value?.role)
                assertEquals(album, viewModel.uiState.value?.album)
                assertEquals(listOf(album.id to false), repository.detailRequests)
            } finally {
                viewModel.uiState.removeObserver(observer)
            }
        }

    @Test
    fun `role chip clears the session and emits navigation only once`() =
        runTest {
            val session = FakeSessionRepository(UserRole.COLLECTOR)
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Success(album) }
            val viewModel = detail(album.id, repository, session)
            val observer = Observer<AlbumDetailUiState> {}
            viewModel.uiState.observeForever(observer)
            try {
                advanceUntilIdle()

                viewModel.onRoleChipClicked()

                assertEquals(1, session.clearCount)
                assertNull(viewModel.uiState.value?.role)
                val event = checkNotNull(viewModel.events.value)
                assertEquals(AlbumDetailEvent.OpenRoleSelector, event.getContentIfNotHandled())
                assertNull(event.getContentIfNotHandled())
            } finally {
                viewModel.uiState.removeObserver(observer)
            }
        }

    @Test
    fun `typed artist navigation is emitted once until returning`() =
        runTest {
            val artist = album.performers.first().copy(type = PerformerType.MUSICIAN)
            val repository =
                FakeAlbumRepository().apply {
                    detailResult = DataResult.Success(album.copy(performers = listOf(artist)))
                }
            val vm = detail(album.id, repository, FakeSessionRepository())
            advanceUntilIdle()
            vm.onPerformerClicked()
            assertEquals(
                AlbumDetailEvent.OpenPerformer(artist.id, PerformerType.MUSICIAN),
                vm.events.value?.getContentIfNotHandled(),
            )
            vm.onPerformerClicked()
            assertNull(vm.events.value?.getContentIfNotHandled())
            vm.onNavigationReturned()
            vm.onPerformerClicked()
            assertEquals(
                AlbumDetailEvent.OpenPerformer(artist.id, PerformerType.MUSICIAN),
                vm.events.value?.getContentIfNotHandled(),
            )
            assertEquals(1, repository.detailRequests.size)
        }

    @Test
    fun `missing or untyped album artists cannot open a guessed endpoint`() =
        runTest {
            val artist = album.performers.first().copy(type = null)
            listOf(emptyList(), listOf(artist)).forEach { artists ->
                val repository =
                    FakeAlbumRepository().apply {
                        detailResult = DataResult.Success(album.copy(performers = artists))
                    }
                val vm = detail(album.id, repository, FakeSessionRepository())
                advanceUntilIdle()
                vm.onPerformerClicked()
                assertNull(vm.events.value)
            }
        }

    @Test
    fun `an album without comments asks for no authors`() =
        runTest {
            val noComments = album.copy(comments = emptyList())
            val collectors = FakeCollectorRepository()
            val vm = detail(album.id, loaded(DataResult.Success(noComments)), FakeSessionRepository(), collectors)

            advanceUntilIdle()

            assertEquals(AlbumDetailUiState(album = noComments, isLoading = false), vm.uiState.value)
            assertTrue(collectors.authorsRequests.isEmpty())
        }

    @Test
    fun `known authors render from Room and an identical revalidation emits no new state`() =
        runTest {
            val known = DataResult.Success(mapOf(100 to manolo, 101 to jaime))
            val refreshGate = CompletableDeferred<Unit>()
            val collectors =
                FakeCollectorRepository(
                    authorsResult = known,
                    refreshedAuthorsResult = known,
                    refreshGate = refreshGate,
                )
            val vm = detail(album.id, loaded(), FakeSessionRepository(), collectors)
            advanceUntilIdle()
            assertEquals(listOf(manolo, jaime), vm.authors())
            assertEquals(mapOf(100 to "MB", 101 to "JM"), vm.uiState.value?.commentInitials)
            assertFalse(checkNotNull(vm.uiState.value).isLoading)
            val emissions = mutableListOf<AlbumDetailUiState>()
            val observer = Observer<AlbumDetailUiState> { emissions += it }
            vm.uiState.observeForever(observer)
            try {
                refreshGate.complete(Unit)
                advanceUntilIdle()

                // Only the value delivered on subscription: the same answer does not redraw the detail.
                assertEquals(1, emissions.size)
            } finally {
                vm.uiState.removeObserver(observer)
            }
            assertEquals(listOf(setOf(100, 101) to false, setOf(100, 101) to true), collectors.authorsRequests)
        }

    @Test
    fun `a stale Room author is replaced by the revalidated one with its initials`() =
        runTest {
            val stale = CommentAuthor(collectorId = 9, name = "Ana Pardo")
            val collectors =
                FakeCollectorRepository(
                    authorsResult = DataResult.Success(mapOf(100 to manolo, 101 to stale)),
                    refreshedAuthorsResult = DataResult.Success(mapOf(100 to manolo, 101 to jaime)),
                )
            val vm = detail(album.id, loaded(), FakeSessionRepository(), collectors)

            advanceUntilIdle()

            assertEquals(listOf(manolo, jaime), vm.authors())
            assertEquals(mapOf(100 to "MB", 101 to "JM"), vm.uiState.value?.commentInitials)
            assertEquals(true, collectors.authorsRequests.last().second)
        }

    @Test
    fun `a missing author is refreshed exactly once after the album is visible`() =
        runTest {
            val refreshGate = CompletableDeferred<Unit>()
            val collectors =
                FakeCollectorRepository(
                    authorsResult = DataResult.Success(mapOf(100 to manolo)),
                    refreshedAuthorsResult = DataResult.Success(mapOf(100 to manolo, 101 to jaime)),
                    refreshGate = refreshGate,
                )
            val vm = detail(album.id, loaded(), FakeSessionRepository(), collectors)

            advanceUntilIdle()

            assertFalse(checkNotNull(vm.uiState.value).isLoading)
            assertEquals(listOf(manolo, null), vm.authors())
            assertEquals(mapOf(100 to "MB"), vm.uiState.value?.commentInitials)
            assertEquals(
                listOf(setOf(100, 101) to false, setOf(100, 101) to true),
                collectors.authorsRequests,
            )

            refreshGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(manolo, jaime), vm.authors())
            assertEquals(mapOf(100 to "MB", 101 to "JM"), vm.uiState.value?.commentInitials)
            assertEquals(2, collectors.authorsRequests.size)
        }

    @Test
    fun `an author still unknown after the refresh stays null without another request`() =
        runTest {
            val collectors = FakeCollectorRepository(authorsResult = DataResult.Success(mapOf(100 to manolo)))
            val vm = detail(album.id, loaded(), FakeSessionRepository(), collectors)

            advanceUntilIdle()

            assertEquals(listOf(manolo, null), vm.authors())
            assertEquals(2, collectors.authorsRequests.size)
            assertNull(vm.uiState.value?.error)
        }

    @Test
    fun `authors failures keep the album without names and without error`() =
        runTest {
            val collectors =
                FakeCollectorRepository(
                    authorsResult = DataResult.Failure(DataError.Network),
                    refreshedAuthorsResult = DataResult.Failure(DataError.Http(500)),
                )
            val vm = detail(album.id, loaded(), FakeSessionRepository(), collectors)

            advanceUntilIdle()

            val state = checkNotNull(vm.uiState.value)
            assertEquals(commented, state.album)
            assertNull(state.error)
            assertFalse(state.isLoading)
            assertTrue(state.commentInitials.isEmpty())
            assertEquals(2, collectors.authorsRequests.size)
        }

    @Test
    fun `a comment published during the refresh is kept and the refresh names the newer album`() =
        runTest {
            val refreshGate = CompletableDeferred<Unit>()
            val collectors =
                FakeCollectorRepository(
                    refreshedAuthorsResult = DataResult.Success(mapOf(100 to manolo, 101 to jaime)),
                    refreshGate = refreshGate,
                )
            val vm = detail(album.id, loaded(), FakeSessionRepository(UserRole.COLLECTOR), collectors)
            advanceUntilIdle()
            val created = Comment(7, "Una obra que no envejece.", 4, jaime)

            vm.onCommentCreated(created)
            refreshGate.complete(Unit)
            advanceUntilIdle()

            val state = checkNotNull(vm.uiState.value)
            assertEquals(listOf(100, 101, 7), state.album?.comments?.map { it.id })
            assertEquals(listOf(manolo, jaime, jaime), vm.authors())
            assertEquals(mapOf(7 to "JM", 100 to "MB", 101 to "JM"), state.commentInitials)
            assertEquals(7, state.highlightedCommentId)
        }

    @Test
    fun `a created comment is appended at the end with its initials and highlighted without another request`() =
        runTest {
            val collectors =
                FakeCollectorRepository(authorsResult = DataResult.Success(mapOf(100 to manolo, 101 to jaime)))
            val repository = loaded()
            val vm = detail(album.id, repository, FakeSessionRepository(UserRole.COLLECTOR), collectors)
            advanceUntilIdle()
            val created = Comment(7, "Una obra que no envejece.", 4, jaime)

            vm.onCommentCreated(created)
            vm.onCommentCreated(created.copy(description = "Duplicado"))

            val state = checkNotNull(vm.uiState.value)
            assertEquals(listOf(100, 101, 7), checkNotNull(state.album).comments.map { it.id })
            assertEquals(created, state.album?.comments?.last())
            assertEquals(7, state.highlightedCommentId)
            assertEquals("JM", state.commentInitials[7])
            assertEquals(listOf(album.id to false), repository.detailRequests)
            assertEquals(listOf(setOf(100, 101) to false, setOf(100, 101) to true), collectors.authorsRequests)
        }

    @Test
    fun `a created comment before the album is shown is ignored`() =
        runTest {
            val vm = detail(album.id, loaded(gate = CompletableDeferred()), FakeSessionRepository())
            advanceUntilIdle()

            vm.onCommentCreated(Comment(7, "Texto", 4, jaime))

            assertNull(vm.uiState.value?.album)
            assertNull(vm.uiState.value?.highlightedCommentId)
            assertTrue(checkNotNull(vm.uiState.value).commentInitials.isEmpty())
        }

    @Test
    fun `a reload clears the initials of the previous album`() =
        runTest {
            val collectors =
                FakeCollectorRepository(authorsResult = DataResult.Success(mapOf(100 to manolo, 101 to jaime)))
            val repository = loaded()
            val vm = detail(album.id, repository, FakeSessionRepository(), collectors)
            advanceUntilIdle()
            assertEquals(
                2,
                vm.uiState.value
                    ?.commentInitials
                    ?.size,
            )
            repository.detailGate = CompletableDeferred()

            vm.retry()

            assertTrue(checkNotNull(vm.uiState.value).commentInitials.isEmpty())
            assertNull(vm.uiState.value?.album)
        }

    @Test
    fun `only a collector with a loaded album can edit`() =
        runTest {
            assertFalse(AlbumDetailUiState(album = null, role = UserRole.COLLECTOR).canEdit)
            assertFalse(AlbumDetailUiState(album = album, role = UserRole.VISITOR).canEdit)
            assertFalse(AlbumDetailUiState(album = album, role = null).canEdit)
            assertTrue(AlbumDetailUiState(album = album, role = UserRole.COLLECTOR).canEdit)

            val vm = detail(album.id, loaded(), FakeSessionRepository(UserRole.COLLECTOR))
            assertFalse(checkNotNull(vm.uiState.value).canEdit)
            advanceUntilIdle()
            assertTrue(checkNotNull(vm.uiState.value).canEdit)
        }
}
