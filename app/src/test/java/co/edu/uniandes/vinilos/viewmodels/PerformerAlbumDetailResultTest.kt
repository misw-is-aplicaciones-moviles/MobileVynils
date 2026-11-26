package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.Prize
import co.edu.uniandes.vinilos.models.PrizeAssignmentRef
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.network.dto.AlbumDto
import co.edu.uniandes.vinilos.network.dto.toModel
import co.edu.uniandes.vinilos.testing.FakePerformerRepository
import co.edu.uniandes.vinilos.testing.FakePrizeRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.band
import co.edu.uniandes.vinilos.testing.musician
import co.edu.uniandes.vinilos.testing.readResource
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PerformerAlbumDetailResultTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val detail = PerformerDetail(musician(1, "Destino"), "", emptyList(), emptyList())
    private val artists = FakePerformerRepository().apply { detailResult = DataResult.Success(detail) }
    private val session = FakeSessionRepository(UserRole.COLLECTOR)
    private val prizes = FakePrizeRepository()

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

    @Test
    fun `verified result refreshes albums and preserves members and prizes for both types`() =
        runTest {
            val album = Gson().fromJson(readResource("api/albums.json"), Array<AlbumDto>::class.java).first().toModel()
            val prize = ArtistPrize(7, Prize(8, "Premio", "Descripción", "Organización"), LocalDate.of(2026, 1, 1))
            prizes.artistPrizes = DataResult.Success(listOf(prize))
            PerformerType.entries.forEach { type ->
                val performer = if (type == PerformerType.BAND) band(1, "Destino") else musician(1, "Destino")
                val members = if (type == PerformerType.BAND) listOf(PerformerSummary(2, "Integrante")) else emptyList()
                val before =
                    detail.copy(
                        performer = performer,
                        members = members,
                        prizeAssignments = listOf(PrizeAssignmentRef(prize.id, prize.premiationDate)),
                    )
                artists.detailResult = DataResult.Success(before)
                val vm = create(type)
                advanceUntilIdle()
                val after = before.copy(albums = listOf(album), performer = performer.copy(albumCount = 1))
                artists.detailResult = DataResult.Success(after)
                vm.onAlbumAssociated(1, type, album.id, false)
                advanceUntilIdle()
                assertEquals(after, vm.uiState.value?.detail)
                assertEquals(album.id, vm.uiState.value?.highlightedAlbumId)
                assertEquals(listOf(prize), vm.uiState.value?.prizes)
                assertEquals(Triple(1, type, true), artists.detailRequests.last())
                assertTrue(prizes.artistRequests.last().second)
                assertEquals(PerformerDetailEvent.AlbumAssociated(false), vm.events.value?.getContentIfNotHandled())
                assertTrue(artists.addedAlbumRequests.isEmpty())
            }
        }

    @Test
    fun `existing association result refreshes without creating another album or success event`() =
        runTest {
            val vm = create()
            advanceUntilIdle()
            vm.onAlbumAssociated(1, PerformerType.MUSICIAN, 20, true)
            advanceUntilIdle()
            assertEquals(PerformerDetailEvent.AlbumAssociated(true), vm.events.value?.getContentIfNotHandled())
            assertNull(vm.events.value?.getContentIfNotHandled())
            assertTrue(artists.addedAlbumRequests.isEmpty())
            assertEquals(Triple(1, PerformerType.MUSICIAN, true), artists.detailRequests.last())
        }

    private fun create(type: PerformerType = PerformerType.MUSICIAN) =
        PerformerDetailViewModel(1, type, artists, session, prizes)
}
