package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.network.dto.toModel
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
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

    @Test
    fun `initial load publishes the complete album and current role`() =
        runTest {
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Success(album) }
            val viewModel = AlbumDetailViewModel(album.id, repository, FakeSessionRepository(UserRole.VISITOR))
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
            val viewModel = AlbumDetailViewModel(999, repository, FakeSessionRepository())

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
                val viewModel = AlbumDetailViewModel(album.id, repository, FakeSessionRepository())

                advanceUntilIdle()

                assertEquals(AlbumDetailUiState(isLoading = false, error = error), viewModel.uiState.value)
                assertNull(viewModel.events.value)
            }
        }

    @Test
    fun `retry clears the error and forces a new query for the same id`() =
        runTest {
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Failure(DataError.Network) }
            val viewModel = AlbumDetailViewModel(album.id, repository, FakeSessionRepository(UserRole.COLLECTOR))
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
            val viewModel = AlbumDetailViewModel(album.id, repository, FakeSessionRepository())
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
            val viewModel = AlbumDetailViewModel(album.id, repository, FakeSessionRepository())

            advanceUntilIdle()

            assertEquals(AlbumDetailUiState(album = emptySections, isLoading = false), viewModel.uiState.value)
        }

    @Test
    fun `invalid ids are rejected before requesting data`() {
        val repository = FakeAlbumRepository()

        for (id in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                AlbumDetailViewModel(id, repository, FakeSessionRepository())
            }
        }

        assertTrue(repository.detailRequests.isEmpty())
    }

    @Test
    fun `session role changes update the header state without reloading the album`() =
        runTest {
            val session = FakeSessionRepository(UserRole.VISITOR)
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Success(album) }
            val viewModel = AlbumDetailViewModel(album.id, repository, session)
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
            val viewModel = AlbumDetailViewModel(album.id, repository, session)
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
}
