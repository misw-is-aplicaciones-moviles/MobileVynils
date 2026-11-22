package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Performer
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakeCollectorRepository
import co.edu.uniandes.vinilos.testing.FakePerformerRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.collectorDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
class CollectorFavoritePickerViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val brian = performer(MAY_ID, PerformerType.MUSICIAN, "Brian May")
    private val freddie = performer(FREDDIE_ID, PerformerType.MUSICIAN, "Freddie Mercury")
    private val queen = performer(QUEEN_ID, PerformerType.BAND, "Queen")
    private val ruben = performer(RUBEN_ID, PerformerType.MUSICIAN, "Rubén Blades Bellido de Luna")
    private val catalog = listOf(queen, brian, ruben, freddie)
    private val byName = listOf(brian, freddie, queen, ruben)

    @Test
    fun `the loaded catalog is published sorted with the collector name`() =
        runTest {
            val harness = picker()
            val states = recordStates(harness.viewModel)

            advanceUntilIdle()

            val state = checkNotNull(harness.viewModel.uiState.value)
            assertEquals("Manolo Bellon", state.collectorName)
            assertEquals(byName, state.visiblePerformers.map { it.performer })
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(1, harness.performers.localCalls)
            assertEquals(1, harness.collectors.detailCalls)
            assertEquals(COLLECTOR_ID, harness.collectors.lastDetailId)
            assertTrue("no result message while loading", states.none { it.hasNoSearchResults })
        }

    @Test
    fun `the search matches the artist name ignoring case`() =
        runTest {
            val viewModel = picker().viewModel
            advanceUntilIdle()

            viewModel.onQueryChanged("  QUEEN ")
            advanceUntilIdle()
            assertEquals(listOf(queen), visible(viewModel))

            viewModel.onQueryChanged("blades")
            advanceUntilIdle()
            assertEquals(listOf(ruben), visible(viewModel))

            viewModel.onQueryChanged("")
            advanceUntilIdle()
            assertEquals(byName, visible(viewModel))
        }

    @Test
    fun `a search without matches reports no results`() =
        runTest {
            val viewModel = picker().viewModel
            advanceUntilIdle()

            viewModel.onQueryChanged("jazz")
            advanceUntilIdle()

            val state = checkNotNull(viewModel.uiState.value)
            assertTrue(state.visiblePerformers.isEmpty())
            assertTrue(state.hasNoSearchResults)
            assertFalse(state.isEmpty)
        }

    @Test
    fun `a blank query keeps every artist in catalog order`() {
        val angel = performer(ANGEL_ID, PerformerType.MUSICIAN, "Ángel")
        val zeta = performer(ZETA_ID, PerformerType.BAND, "zeta")

        val sorted = listOf(angel, zeta).sortedWith(FAVORITE_PICKER_ORDER)

        val rows = buildVisibleFavorites(sorted, emptySet(), "   ", selectedId = null, selectedType = null)

        assertEquals(listOf(zeta, angel), rows.map { it.performer })
        assertTrue(rows.none { it.isSelected || it.isAlreadyFavorite })
    }

    @Test
    fun `the rows keep the order of the list they receive`() {
        val rows = buildVisibleFavorites(catalog, emptySet(), "", selectedId = null, selectedType = null)

        assertEquals(catalog, rows.map { it.performer })
    }

    @Test
    fun `the picker order folds only ASCII case and breaks ties by id and then by type`() {
        val upper = performer(QUEEN_ID, PerformerType.BAND, "ECO")
        val lowerMusician = performer(FREDDIE_ID, PerformerType.MUSICIAN, "eco")
        val lowerBand = performer(FREDDIE_ID, PerformerType.BAND, "eco")
        val zeta = performer(ZETA_ID, PerformerType.BAND, "zeta")
        val angel = performer(ANGEL_ID, PerformerType.MUSICIAN, "Ángel")

        val sorted = listOf(angel, lowerBand, zeta, lowerMusician, upper).sortedWith(FAVORITE_PICKER_ORDER)

        assertEquals(listOf(upper, lowerMusician, lowerBand, zeta, angel), sorted)
    }

    @Test
    fun `the loaded catalog is kept sorted while searching and choosing`() =
        runTest {
            val viewModel = picker().viewModel
            advanceUntilIdle()
            assertEquals(byName, checkNotNull(viewModel.uiState.value).performers)

            viewModel.onQueryChanged("e")
            advanceUntilIdle()
            assertEquals(listOf(freddie, queen, ruben), visible(viewModel))

            viewModel.onPerformerSelected(queen.id, PerformerType.BAND)
            viewModel.onQueryChanged("")
            advanceUntilIdle()

            assertEquals(byName, visible(viewModel))
            assertEquals(byName, checkNotNull(viewModel.uiState.value).performers)
        }

    @Test
    fun `a retry sorts the new catalog it receives`() =
        runTest {
            val harness = picker()
            advanceUntilIdle()
            val angel = performer(ANGEL_ID, PerformerType.MUSICIAN, "Ángel")
            harness.performers.remoteResult = DataResult.Success(listOf(angel, queen, brian))

            harness.viewModel.retry()
            advanceUntilIdle()

            assertEquals(listOf(brian, queen, angel), visible(harness.viewModel))
            assertEquals(listOf(brian, queen, angel), checkNotNull(harness.viewModel.uiState.value).performers)
            assertEquals(1, harness.performers.remoteCalls)
        }

    @Test
    fun `only the latest of two quick searches is published`() =
        runTest {
            val viewModel = picker().viewModel
            advanceUntilIdle()
            val states = recordStates(viewModel)

            viewModel.onQueryChanged("queen")
            viewModel.onQueryChanged("brian")
            advanceUntilIdle()

            assertEquals(listOf(brian), visible(viewModel))
            val published = states.map { state -> state.visiblePerformers.map { it.performer } }
            assertFalse("older search published", listOf(queen) in published)
        }

    @Test
    fun `a musician favorite and a band favorite cannot be chosen`() =
        runTest {
            val mayBand = performer(MAY_ID, PerformerType.BAND, "May Band")
            val viewModel =
                picker(
                    artists = listOf(ruben, queen, brian, mayBand),
                    favorites =
                        listOf(
                            summary(ruben, PerformerType.MUSICIAN),
                            summary(queen, PerformerType.BAND),
                            summary(mayBand, PerformerType.BAND),
                        ),
                ).viewModel
            advanceUntilIdle()

            viewModel.onPerformerSelected(ruben.id, PerformerType.MUSICIAN)
            viewModel.onPerformerSelected(queen.id, PerformerType.BAND)
            viewModel.onPerformerSelected(mayBand.id, PerformerType.BAND)
            advanceUntilIdle()

            val rows = checkNotNull(viewModel.uiState.value).visiblePerformers
            assertTrue(row(rows, ruben).isAlreadyFavorite)
            assertTrue(row(rows, queen).isAlreadyFavorite)
            assertTrue(row(rows, mayBand).isAlreadyFavorite)
            assertFalse(row(rows, brian).isAlreadyFavorite)
            assertTrue(rows.none { it.isSelected })
            assertFalse(checkNotNull(viewModel.uiState.value).canAdd)

            viewModel.onPerformerSelected(brian.id, PerformerType.MUSICIAN)
            advanceUntilIdle()

            assertTrue(row(checkNotNull(viewModel.uiState.value).visiblePerformers, brian).isSelected)
            assertEquals(brian, viewModel.uiState.value?.selectedPerformer)
        }

    @Test
    fun `a favorite without a type blocks every artist with that id`() =
        runTest {
            val mayBand = performer(MAY_ID, PerformerType.BAND, "May Band")
            val viewModel =
                picker(
                    artists = listOf(brian, mayBand),
                    favorites = listOf(PerformerSummary(id = MAY_ID, name = brian.name, type = null)),
                ).viewModel
            advanceUntilIdle()

            viewModel.onPerformerSelected(brian.id, PerformerType.MUSICIAN)
            viewModel.onPerformerSelected(mayBand.id, PerformerType.BAND)
            advanceUntilIdle()

            val rows = checkNotNull(viewModel.uiState.value).visiblePerformers
            assertTrue(rows.all { it.isAlreadyFavorite })
            assertTrue(rows.none { it.isSelected })
            assertFalse(checkNotNull(viewModel.uiState.value).canAdd)
        }

    @Test
    fun `choosing a musician and then a band keeps only the band`() =
        runTest {
            val viewModel = picker().viewModel
            advanceUntilIdle()

            viewModel.onPerformerSelected(brian.id, PerformerType.MUSICIAN)
            viewModel.onPerformerSelected(queen.id, PerformerType.BAND)
            advanceUntilIdle()

            val rows = checkNotNull(viewModel.uiState.value).visiblePerformers
            assertFalse(row(rows, brian).isSelected)
            assertTrue(row(rows, queen).isSelected)
            assertEquals(queen, viewModel.uiState.value?.selectedPerformer)
        }

    @Test
    fun `choosing a musician enables adding that artist`() =
        runTest {
            val viewModel = picker().viewModel
            advanceUntilIdle()

            viewModel.onPerformerSelected(UNKNOWN_ID, PerformerType.MUSICIAN)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value?.selectedId)

            viewModel.onPerformerSelected(brian.id, PerformerType.MUSICIAN)
            advanceUntilIdle()

            val state = checkNotNull(viewModel.uiState.value)
            assertTrue(state.canAdd)
            assertEquals(brian, state.selectedPerformer)
            assertEquals("Brian May", state.selectedPerformer?.name)
            assertEquals(PerformerType.MUSICIAN, state.selectedType)
        }

    @Test
    fun `choosing a band enables adding that artist`() =
        runTest {
            val viewModel = picker().viewModel
            advanceUntilIdle()

            viewModel.onPerformerSelected(queen.id, PerformerType.BAND)
            advanceUntilIdle()

            val state = checkNotNull(viewModel.uiState.value)
            assertTrue(state.canAdd)
            assertEquals(queen, state.selectedPerformer)
            assertEquals("Queen", state.selectedPerformer?.name)
            assertEquals(PerformerType.BAND, state.selectedType)
        }

    @Test
    fun `the button stays disabled until an artist is chosen`() =
        runTest {
            val harness = picker()
            val viewModel = harness.viewModel
            advanceUntilIdle()

            val state = checkNotNull(viewModel.uiState.value)
            assertFalse(state.canAdd)
            assertNull(state.selectedPerformer)
            viewModel.onAddSelected()
            advanceUntilIdle()

            assertNull(viewModel.events.value)
            assertNull(viewModel.uiState.value?.selectedId)
            assertEquals(0, harness.collectors.addFavoriteCalls)
        }

    @Test
    fun `adding a musician or a band posts once and a second tap or a failure does not`() =
        runTest {
            listOf(freddie to PerformerType.MUSICIAN, queen to PerformerType.BAND).forEach { (artist, type) ->
                assertSavedFavorite(artist, type)
            }
            assertSecondTapDoesNotPost()
            assertNetworkFailureKeepsTheArtist()
            assertExistingFavoriteIsNotPosted()
            assertRoleChangeDropsTheResult()
        }

    @Test
    fun `an invalid collector does not load`() =
        runTest {
            val harness = picker(collectorId = 0)
            advanceUntilIdle()

            val state = checkNotNull(harness.viewModel.uiState.value)
            assertEquals(DataError.Serialization, state.error)
            assertFalse(state.isLoading)
            assertEquals(0, harness.performers.localCalls)
            assertEquals(0, harness.collectors.detailCalls)
        }

    @Test
    fun `leaving the collector role opens the role selector`() =
        runTest {
            val harness = picker()
            recordStates(harness.viewModel)
            advanceUntilIdle()

            harness.session.selectRole(UserRole.VISITOR)

            val event =
                harness.viewModel.events.value
                    ?.getContentIfNotHandled()
            assertEquals(CollectorFavoritePickerEvent.OpenRoleSelector, event)
        }

    @Test
    fun `a visitor is sent to the role selector without loading`() =
        runTest {
            val harness = picker(role = UserRole.VISITOR)
            advanceUntilIdle()

            val event =
                harness.viewModel.events.value
                    ?.getContentIfNotHandled()
            assertEquals(CollectorFavoritePickerEvent.OpenRoleSelector, event)
            assertEquals(0, harness.performers.localCalls)
            assertEquals(0, harness.collectors.detailCalls)
            assertFalse(checkNotNull(harness.viewModel.uiState.value).isLoading)
        }

    @Test
    fun `a restored search and selection are applied to the loaded catalog`() =
        runTest {
            val harness =
                picker(
                    restored =
                        mapOf(
                            CollectorFavoritePickerViewModel.QUERY to "queen",
                            CollectorFavoritePickerViewModel.SELECTED_ID to queen.id,
                            CollectorFavoritePickerViewModel.SELECTED_TYPE to PerformerType.BAND.name,
                        ),
                )
            advanceUntilIdle()

            val state = checkNotNull(harness.viewModel.uiState.value)
            assertEquals("queen", state.query)
            assertEquals(listOf(queen), visible(harness.viewModel))
            assertEquals(queen, state.selectedPerformer)
            assertEquals(PerformerType.BAND, state.selectedType)
            assertTrue(state.canAdd)
        }

    @Test
    fun `a restored artist that is already a favorite is dropped`() =
        runTest {
            val harness =
                picker(
                    favorites = listOf(summary(queen, PerformerType.BAND)),
                    restored =
                        mapOf(
                            CollectorFavoritePickerViewModel.SELECTED_ID to queen.id,
                            CollectorFavoritePickerViewModel.SELECTED_TYPE to PerformerType.BAND.name,
                        ),
                )
            advanceUntilIdle()

            val state = checkNotNull(harness.viewModel.uiState.value)
            assertNull(state.selectedId)
            assertNull(state.selectedType)
            assertNull(harness.saved.get<Int>(CollectorFavoritePickerViewModel.SELECTED_ID))
            assertNull(harness.saved.get<String>(CollectorFavoritePickerViewModel.SELECTED_TYPE))
            assertFalse(state.canAdd)
            val rows = state.visiblePerformers
            assertTrue(row(rows, queen).isAlreadyFavorite)
            assertFalse(row(rows, queen).isSelected)
        }

    @Test
    fun `a catalog failure clears the rows after both calls`() =
        runTest {
            val harness = picker(catalogResult = DataResult.Failure(DataError.Network))
            advanceUntilIdle()

            val state = checkNotNull(harness.viewModel.uiState.value)
            assertEquals(DataError.Network, state.error)
            assertTrue(state.visiblePerformers.isEmpty())
            assertFalse(state.isLoading)
            assertEquals(1, harness.performers.localCalls)
            assertEquals(1, harness.collectors.detailCalls)
        }

    @Test
    fun `a collector failure clears the rows after both calls`() =
        runTest {
            val harness = picker(detailResult = DataResult.Failure(DataError.Http(NOT_FOUND)))
            advanceUntilIdle()

            val state = checkNotNull(harness.viewModel.uiState.value)
            assertEquals(DataError.Http(NOT_FOUND), state.error)
            assertTrue(state.visiblePerformers.isEmpty())
            assertFalse(state.isLoading)
            assertEquals(1, harness.performers.localCalls)
            assertEquals(1, harness.collectors.detailCalls)
        }

    private fun TestScope.assertSavedFavorite(
        artist: Performer,
        type: PerformerType,
    ) {
        val harness = picker()
        advanceUntilIdle()
        harness.viewModel.onPerformerSelected(artist.id, type)
        advanceUntilIdle()

        harness.viewModel.onAddSelected()
        advanceUntilIdle()

        val state = checkNotNull(harness.viewModel.uiState.value)
        assertEquals(1, harness.collectors.addFavoriteCalls)
        assertEquals(Triple(COLLECTOR_ID, artist.id, type), harness.collectors.lastAddFavorite)
        assertTrue(state.isSubmitting)
        assertFalse(state.canAdd)
        assertEquals(artist.id, state.selectedId)
        assertEquals(
            CollectorFavoritePickerEvent.FavoriteAdded(COLLECTOR_ID, artist.id, type, artist.name),
            harness.viewModel.events.value
                ?.getContentIfNotHandled(),
        )
    }

    private fun TestScope.assertSecondTapDoesNotPost() {
        val harness = picker()
        val gate = CompletableDeferred<Unit>()
        harness.collectors.addFavoriteGate = gate
        advanceUntilIdle()
        harness.viewModel.onPerformerSelected(freddie.id, PerformerType.MUSICIAN)
        advanceUntilIdle()

        harness.viewModel.onAddSelected()
        harness.viewModel.onAddSelected()
        harness.viewModel.onPerformerSelected(queen.id, PerformerType.BAND)
        harness.viewModel.onQueryChanged("zzz")
        harness.viewModel.onRoleChipClicked()
        runCurrent()

        val state = checkNotNull(harness.viewModel.uiState.value)
        assertEquals(1, harness.collectors.addFavoriteCalls)
        assertEquals(Triple(COLLECTOR_ID, freddie.id, PerformerType.MUSICIAN), harness.collectors.lastAddFavorite)
        assertTrue(state.isSubmitting)
        assertFalse(state.canAdd)
        assertEquals(freddie.id, state.selectedId)
        assertEquals("", state.query)
        assertEquals(UserRole.COLLECTOR, state.role)
        assertNull(harness.viewModel.events.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, harness.collectors.addFavoriteCalls)
        assertEquals(
            CollectorFavoritePickerEvent.FavoriteAdded(COLLECTOR_ID, freddie.id, PerformerType.MUSICIAN, freddie.name),
            harness.viewModel.events.value
                ?.getContentIfNotHandled(),
        )
    }

    private fun TestScope.assertNetworkFailureKeepsTheArtist() {
        val harness = picker()
        harness.collectors.addFavoriteResult = DataResult.Failure(DataError.Network)
        advanceUntilIdle()
        harness.viewModel.onPerformerSelected(freddie.id, PerformerType.MUSICIAN)
        advanceUntilIdle()

        harness.viewModel.onAddSelected()
        advanceUntilIdle()

        val failed = checkNotNull(harness.viewModel.uiState.value)
        assertEquals(DataError.Network, failed.submitError)
        assertFalse(failed.isSubmitting)
        assertEquals(freddie.id, failed.selectedId)
        assertEquals(PerformerType.MUSICIAN, failed.selectedType)
        assertTrue(failed.canAdd)
        assertNull(harness.viewModel.events.value)
        assertEquals(1, harness.collectors.addFavoriteCalls)

        harness.viewModel.onPerformerSelected(queen.id, PerformerType.BAND)
        advanceUntilIdle()
        val cleared = checkNotNull(harness.viewModel.uiState.value)
        assertNull(cleared.submitError)
        assertEquals(queen.id, cleared.selectedId)
        assertEquals(1, harness.collectors.addFavoriteCalls)

        harness.collectors.addFavoriteResult = DataResult.Success(Unit)
        harness.viewModel.onAddSelected()
        advanceUntilIdle()
        assertEquals(2, harness.collectors.addFavoriteCalls)
        assertEquals(
            CollectorFavoritePickerEvent.FavoriteAdded(COLLECTOR_ID, queen.id, PerformerType.BAND, queen.name),
            harness.viewModel.events.value
                ?.getContentIfNotHandled(),
        )
    }

    private fun TestScope.assertExistingFavoriteIsNotPosted() {
        val harness = picker(favorites = listOf(summary(freddie, PerformerType.MUSICIAN)))
        advanceUntilIdle()
        harness.viewModel.onPerformerSelected(freddie.id, PerformerType.MUSICIAN)
        harness.viewModel.onAddSelected()
        advanceUntilIdle()

        assertEquals(0, harness.collectors.addFavoriteCalls)
        assertNull(harness.viewModel.events.value)
        assertFalse(checkNotNull(harness.viewModel.uiState.value).canAdd)
    }

    private fun TestScope.assertRoleChangeDropsTheResult() {
        val harness = picker()
        recordStates(harness.viewModel)
        val gate = CompletableDeferred<Unit>()
        harness.collectors.addFavoriteGate = gate
        advanceUntilIdle()
        harness.viewModel.onPerformerSelected(freddie.id, PerformerType.MUSICIAN)
        advanceUntilIdle()
        harness.viewModel.onAddSelected()
        runCurrent()

        harness.session.selectRole(UserRole.VISITOR)
        assertEquals(
            CollectorFavoritePickerEvent.OpenRoleSelector,
            harness.viewModel.events.value
                ?.getContentIfNotHandled(),
        )

        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(
            harness.viewModel.events.value
                ?.getContentIfNotHandled(),
        )
        assertEquals(1, harness.collectors.addFavoriteCalls)
    }

    private fun picker(
        artists: List<Performer> = catalog,
        favorites: List<PerformerSummary> = emptyList(),
        collectorId: Int = COLLECTOR_ID,
        role: UserRole? = UserRole.COLLECTOR,
        catalogResult: DataResult<List<Performer>, DataError>? = null,
        detailResult: DataResult<CollectorDetail, DataError>? = null,
        restored: Map<String, Any?> = emptyMap(),
    ): Harness {
        val performers =
            FakePerformerRepository(
                localResult = catalogResult ?: DataResult.Success(artists),
            )
        val collectors = FakeCollectorRepository()
        collectors.detailResult =
            detailResult ?: DataResult.Success(collectorDetail(id = collectorId, favorites = favorites))
        val saved =
            mutableMapOf<String, Any?>(
                CollectorFavoritePickerViewModel.ARG_COLLECTOR_ID to collectorId,
            )
        saved.putAll(restored)
        val handle = SavedStateHandle(saved)
        val session = FakeSessionRepository(role)
        val viewModel =
            CollectorFavoritePickerViewModel(
                savedState = handle,
                performerRepository = performers,
                collectorRepository = collectors,
                sessionRepository = session,
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            )
        return Harness(viewModel, performers, collectors, session, handle)
    }

    private fun recordStates(viewModel: CollectorFavoritePickerViewModel): List<CollectorFavoritePickerUiState> {
        val states = mutableListOf<CollectorFavoritePickerUiState>()
        viewModel.uiState.observeForever(Observer { states += it })
        return states
    }

    private fun visible(viewModel: CollectorFavoritePickerViewModel) =
        checkNotNull(viewModel.uiState.value).visiblePerformers.map { it.performer }

    private fun row(
        rows: List<CollectorFavoriteOption>,
        performer: Performer,
    ) = checkNotNull(rows.firstOrNull { it.performer.id == performer.id && it.performer.type == performer.type })

    private fun summary(
        performer: Performer,
        type: PerformerType,
    ) = PerformerSummary(id = performer.id, name = performer.name, type = type)

    private fun performer(
        id: Int,
        type: PerformerType,
        name: String,
    ) = Performer(
        id = id,
        type = type,
        name = name,
        imageUrl = "https://img/$id",
        date = LocalDate.of(YEAR, MONTH, DAY),
        albumCount = 0,
        memberCount = 0,
        bandId = null,
        bandName = null,
    )

    private data class Harness(
        val viewModel: CollectorFavoritePickerViewModel,
        val performers: FakePerformerRepository,
        val collectors: FakeCollectorRepository,
        val session: FakeSessionRepository,
        val saved: SavedStateHandle,
    )

    private companion object {
        const val COLLECTOR_ID = 100
        const val YEAR = 1948
        const val MONTH = 1
        const val DAY = 1
        const val QUEEN_ID = 3
        const val RUBEN_ID = 4
        const val FREDDIE_ID = 5
        const val MAY_ID = 7
        const val ANGEL_ID = 8
        const val ZETA_ID = 9
        const val UNKNOWN_ID = 99
        const val NOT_FOUND = 404
    }
}
