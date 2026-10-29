package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial load publishes the complete album and current role`() =
        runTest {
            val album = Gson().fromJson(readResource("api/albums.json"), Array<AlbumDto>::class.java).first().toModel()
            val repository = FakeAlbumRepository().apply { detailResult = DataResult.Success(album) }
            val viewModel = AlbumDetailViewModel(album.id, repository, FakeSessionRepository(UserRole.VISITOR))
            assertTrue(checkNotNull(viewModel.uiState.value).isLoading)

            advanceUntilIdle()

            assertEquals(
                AlbumDetailUiState(album = album, isLoading = false, role = UserRole.VISITOR),
                viewModel.uiState.value,
            )
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
        }
}
