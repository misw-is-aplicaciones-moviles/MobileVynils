package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.network.dto.toModel
import co.edu.uniandes.vinilos.testing.FakePerformerRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.Gson
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
class PerformerDetailViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val album =
        Gson().fromJson(readResource("api/albums.json"), Array<AlbumDto>::class.java).first().toModel()
    private val member = PerformerSummary(2, "Integrante", type = PerformerType.MUSICIAN)
    private val band =
        PerformerDetail(
            band(1, "Banda", albums = 1, members = 1),
            "Descripción",
            listOf(album),
            listOf(member),
        )

    @Test
    fun `initial load preserves the requested type content and role`() =
        runTest {
            listOf(band, band.copy(performer = musician(1, "Músico"), members = emptyList())).forEach { detail ->
                val repository = FakePerformerRepository().apply { detailResult = DataResult.Success(detail) }
                val vm =
                    PerformerDetailViewModel(
                        1,
                        detail.performer.type,
                        repository,
                        FakeSessionRepository(UserRole.VISITOR),
                    )
                assertTrue(checkNotNull(vm.uiState.value).isLoading)

                advanceUntilIdle()

                assertEquals(detail, vm.uiState.value?.detail)
                assertEquals(UserRole.VISITOR, vm.uiState.value?.role)
                assertFalse(checkNotNull(vm.uiState.value).isLoading)
                assertEquals(listOf(Triple(1, detail.performer.type, false)), repository.detailRequests)
            }
        }

    @Test
    fun `invalid identity shows a safe state without querying or retrying`() =
        runTest {
            listOf(0 to PerformerType.BAND, -1 to PerformerType.MUSICIAN, 1 to null).forEach { (id, type) ->
                val repository = FakePerformerRepository()
                val vm = PerformerDetailViewModel(id, type, repository, FakeSessionRepository())
                vm.retry()
                advanceUntilIdle()

                assertFalse(checkNotNull(vm.uiState.value).hasValidIdentity)
                assertFalse(checkNotNull(vm.uiState.value).isLoading)
                assertEquals(DataError.Serialization, vm.uiState.value?.error)
                assertTrue(repository.detailRequests.isEmpty())
            }
        }

    @Test
    fun `errors finish loading and explicit retry revalidates the same artist`() =
        runTest {
            val errors = listOf(DataError.Network, DataError.Http(404), DataError.Http(500), DataError.Serialization)
            errors.forEach { error ->
                val repository = FakePerformerRepository().apply { detailResult = DataResult.Failure(error) }
                val vm = PerformerDetailViewModel(1, PerformerType.BAND, repository, FakeSessionRepository())
                advanceUntilIdle()
                assertEquals(error, vm.uiState.value?.error)
                assertNull(vm.uiState.value?.detail)
                assertFalse(checkNotNull(vm.uiState.value).isLoading)
                repository.detailResult = DataResult.Success(band)

                vm.retry()
                advanceUntilIdle()

                assertEquals(band, vm.uiState.value?.detail)
                assertNull(vm.uiState.value?.error)
                assertEquals(
                    listOf(Triple(1, PerformerType.BAND, false), Triple(1, PerformerType.BAND, true)),
                    repository.detailRequests,
                )
            }
        }

    @Test
    fun `retries while loading and new observers do not duplicate the request`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakePerformerRepository().apply {
                    detailResult = DataResult.Success(band)
                    detailGate = gate
                }
            val vm = PerformerDetailViewModel(1, PerformerType.BAND, repository, FakeSessionRepository())
            runCurrent()
            vm.retry()
            vm.retry()
            assertEquals(1, repository.detailRequests.size)
            gate.complete(Unit)
            advanceUntilIdle()
            val observer = Observer<PerformerDetailUiState> {}
            vm.uiState.observeForever(observer)
            vm.uiState.removeObserver(observer)
            advanceUntilIdle()

            assertEquals(1, repository.detailRequests.size)
            assertEquals(band, vm.uiState.value?.detail)
        }

    @Test
    fun `only visible album and member identities navigate and double taps are ignored`() =
        runTest {
            val repository = FakePerformerRepository().apply { detailResult = DataResult.Success(band) }
            val vm = PerformerDetailViewModel(1, PerformerType.BAND, repository, FakeSessionRepository())
            advanceUntilIdle()
            vm.onAlbumClicked(-1)
            vm.onMemberClicked(-1)
            assertNull(vm.events.value)

            vm.onAlbumClicked(album.id)
            val event = vm.events.value
            assertEquals(PerformerDetailEvent.OpenAlbum(album.id), event?.getContentIfNotHandled())
            vm.onMemberClicked(member.id)
            assertSame(event, vm.events.value)
            assertNull(event?.getContentIfNotHandled())
            vm.onNavigationReturned()
            vm.onMemberClicked(member.id)
            assertEquals(PerformerDetailEvent.OpenMember(member.id), vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `role changes update the header without reloading and the chip clears the session`() =
        runTest {
            val repository = FakePerformerRepository().apply { detailResult = DataResult.Success(band) }
            val session = FakeSessionRepository(UserRole.COLLECTOR)
            val vm = PerformerDetailViewModel(1, PerformerType.BAND, repository, session)
            val observer = Observer<PerformerDetailUiState> {}
            vm.uiState.observeForever(observer)
            try {
                advanceUntilIdle()
                session.selectRole(UserRole.VISITOR)
                assertEquals(UserRole.VISITOR, vm.uiState.value?.role)
                vm.onRoleChipClicked()

                assertNull(session.role.value)
                assertNull(vm.uiState.value?.role)
                assertEquals(PerformerDetailEvent.OpenRoleSelector, vm.events.value?.getContentIfNotHandled())
                assertEquals(1, repository.detailRequests.size)
            } finally {
                vm.uiState.removeObserver(observer)
            }
        }
}
