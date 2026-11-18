package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakePerformerRepository
import co.edu.uniandes.vinilos.testing.FakePrizeRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.musician
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerformerAlbumDetailResultTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val detail = PerformerDetail(musician(1, "Destino"), "", emptyList(), emptyList())
    private val artists = FakePerformerRepository().apply { detailResult = DataResult.Success(detail) }
    private val session = FakeSessionRepository(UserRole.COLLECTOR)

    @Test
    fun `only collector can open a typed picker and repeated taps do not navigate twice`() =
        runTest {
            val vm = create()
            val observer = Observer<PerformerDetailUiState> {}
            vm.uiState.observeForever(observer)
            advanceUntilIdle()
            session.selectRole(UserRole.VISITOR)
            vm.onAddAlbumClicked()
            assertNull(vm.events.value)
            session.selectRole(UserRole.COLLECTOR)
            vm.onAddAlbumClicked()
            assertEquals(PerformerDetailEvent.OpenAlbumPicker(detail), vm.events.value?.getContentIfNotHandled())
            vm.onAddAlbumClicked()
            assertNull(vm.events.value?.getContentIfNotHandled())
            vm.uiState.removeObserver(observer)
        }

    @Test
    fun `results from a different artist or type do not refresh the current detail`() =
        runTest {
            val vm = create()
            advanceUntilIdle()
            vm.onAlbumAssociated(2, PerformerType.MUSICIAN, 20, false)
            vm.onAlbumAssociated(1, PerformerType.BAND, 20, false)
            advanceUntilIdle()
            assertEquals(1, artists.detailRequests.size)
            assertNull(vm.events.value)
        }

    @Test
    fun `a failed detail refresh keeps the confirmed association and never repeats a post`() =
        runTest {
            val vm = create()
            advanceUntilIdle()
            artists.detailResult = DataResult.Failure(DataError.Network)
            vm.onAlbumAssociated(1, PerformerType.MUSICIAN, 20, false)
            advanceUntilIdle()
            assertEquals(PerformerDetailEvent.AlbumAssociated(false), vm.events.value?.getContentIfNotHandled())
            assertEquals(DataError.Network, vm.uiState.value?.error)
            vm.retry()
            advanceUntilIdle()
            assertTrue(artists.detailRequests.last().third)
            assertTrue(artists.addedAlbumRequests.isEmpty())
        }

    private fun create() = PerformerDetailViewModel(1, PerformerType.MUSICIAN, artists, session, FakePrizeRepository())
}
