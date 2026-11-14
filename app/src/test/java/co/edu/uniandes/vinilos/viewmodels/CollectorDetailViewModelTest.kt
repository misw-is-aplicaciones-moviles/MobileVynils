package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.CollectorAlbum
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakeCollectorRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.collectorDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectorDetailViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val album = CollectorAlbum(200, "Buscando América", "https://cover", 35, AlbumStatus.ACTIVE)
    private val favorite = PerformerSummary(100, "Rubén Blades", type = PerformerType.MUSICIAN)
    private val detail = collectorDetail(albums = listOf(album), favorites = listOf(favorite))

    @Test
    fun `initial load publishes the requested collector`() =
        runTest {
            val repository = FakeCollectorRepository().apply { detailResult = DataResult.Success(detail) }
            val vm = CollectorDetailViewModel(100, repository, FakeSessionRepository(UserRole.VISITOR))
            assertTrue(checkNotNull(vm.uiState.value).isLoading)

            advanceUntilIdle()

            assertEquals(detail, vm.uiState.value?.detail)
            assertEquals(UserRole.VISITOR, vm.uiState.value?.role)
            assertFalse(checkNotNull(vm.uiState.value).canEdit)
            assertFalse(checkNotNull(vm.uiState.value).isLoading)
            assertNull(vm.uiState.value?.error)
            assertEquals(1, repository.detailCalls)
            assertEquals(100, repository.lastDetailId)
            assertEquals(false, repository.lastDetailRefresh)
        }

    @Test
    fun `a role change does not reload and only a collector can edit`() =
        runTest {
            val repository = FakeCollectorRepository().apply { detailResult = DataResult.Success(detail) }
            val session = FakeSessionRepository(UserRole.VISITOR)
            val vm = CollectorDetailViewModel(100, repository, session)
            val observer = Observer<CollectorDetailUiState> {}
            vm.uiState.observeForever(observer)
            try {
                advanceUntilIdle()
                session.selectRole(UserRole.COLLECTOR)

                assertEquals(UserRole.COLLECTOR, vm.uiState.value?.role)
                assertTrue(checkNotNull(vm.uiState.value).canEdit)
                assertEquals(detail, vm.uiState.value?.detail)
                assertEquals(1, repository.detailCalls)
            } finally {
                vm.uiState.removeObserver(observer)
            }
        }

    @Test
    fun `retry sends force refresh after an error`() =
        runTest {
            val repository =
                FakeCollectorRepository().apply { detailResult = DataResult.Failure(DataError.Network) }
            val vm = CollectorDetailViewModel(100, repository, FakeSessionRepository())
            advanceUntilIdle()
            assertEquals(DataError.Network, vm.uiState.value?.error)
            repository.detailResult = DataResult.Success(detail)

            vm.retry()
            advanceUntilIdle()

            assertEquals(detail, vm.uiState.value?.detail)
            assertNull(vm.uiState.value?.error)
            assertEquals(2, repository.detailCalls)
            assertEquals(true, repository.lastDetailRefresh)
        }

    @Test
    fun `retries while loading do not duplicate the request`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakeCollectorRepository().apply {
                    detailResult = DataResult.Success(detail)
                    detailGate = gate
                }
            val vm = CollectorDetailViewModel(100, repository, FakeSessionRepository())
            runCurrent()
            vm.retry()
            vm.retry()
            assertEquals(1, repository.detailCalls)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, repository.detailCalls)
            assertEquals(false, repository.lastDetailRefresh)
            assertEquals(detail, vm.uiState.value?.detail)
        }

    @Test
    fun `an invalid id shows a safe state without querying or retrying`() =
        runTest {
            listOf(0, -1).forEach { id ->
                val repository = FakeCollectorRepository()
                val vm = CollectorDetailViewModel(id, repository, FakeSessionRepository())
                vm.retry()
                advanceUntilIdle()

                assertFalse(checkNotNull(vm.uiState.value).hasValidIdentity)
                assertFalse(checkNotNull(vm.uiState.value).isLoading)
                assertEquals(DataError.Serialization, vm.uiState.value?.error)
                assertEquals(0, repository.detailCalls)
            }
        }

    @Test
    fun `a missing collector finishes as not found`() =
        runTest {
            val repository =
                FakeCollectorRepository().apply { detailResult = DataResult.Failure(DataError.Http(404)) }
            val vm = CollectorDetailViewModel(100, repository, FakeSessionRepository())

            advanceUntilIdle()

            assertEquals(DataError.Http(404), vm.uiState.value?.error)
            assertNull(vm.uiState.value?.detail)
            assertFalse(checkNotNull(vm.uiState.value).isLoading)
        }

    @Test
    fun `a favorite without a type does not navigate`() =
        runTest {
            val untyped = detail.copy(favorites = listOf(PerformerSummary(5, "Sin tipo")))
            val repository = FakeCollectorRepository().apply { detailResult = DataResult.Success(untyped) }
            val vm = CollectorDetailViewModel(100, repository, FakeSessionRepository())
            advanceUntilIdle()

            vm.onFavoriteClicked(5)

            assertNull(vm.events.value)
        }

    @Test
    fun `a second tap is ignored until navigation returns`() =
        runTest {
            val repository = FakeCollectorRepository().apply { detailResult = DataResult.Success(detail) }
            val vm = CollectorDetailViewModel(100, repository, FakeSessionRepository())
            advanceUntilIdle()

            vm.onAlbumClicked(album.albumId)
            val event = vm.events.value
            assertEquals(CollectorDetailEvent.OpenAlbum(album.albumId), event?.getContentIfNotHandled())
            vm.onFavoriteClicked(favorite.id)
            assertSame(event, vm.events.value)

            vm.onNavigationReturned()
            vm.onFavoriteClicked(favorite.id)

            assertEquals(
                CollectorDetailEvent.OpenFavorite(favorite.id, PerformerType.MUSICIAN),
                vm.events.value?.getContentIfNotHandled(),
            )
        }

    @Test
    fun `an album or favorite outside the detail does not navigate`() =
        runTest {
            val repository = FakeCollectorRepository().apply { detailResult = DataResult.Success(detail) }
            val vm = CollectorDetailViewModel(100, repository, FakeSessionRepository())
            advanceUntilIdle()

            vm.onAlbumClicked(album.albumId + 1)
            vm.onFavoriteClicked(favorite.id + 1)

            assertNull(vm.events.value)
        }
}
