package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.Comment
import co.edu.uniandes.vinilos.models.CommentAuthor
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.NewComment
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.FakeCollectorRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import co.edu.uniandes.vinilos.testing.collector
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
@Suppress("MagicNumber") // Seed ids and ratings.
class CommentCreateViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val seed = listOf(collector(100, "Manolo Bellon"), collector(101, "Jaime Monsalve"))
    private val albums = FakeAlbumRepository()
    private val collectors =
        FakeCollectorRepository(
            hasCache = true,
            localResult = DataResult.Success(seed),
            remoteResult = DataResult.Success(seed),
        )
    private val created = Comment(1, "Una obra que no envejece.", 4, CommentAuthor(101, "Jaime Monsalve"))

    // Created inside each test: `init` launches on Dispatchers.Main, which the rule replaces only once it starts.
    private fun newViewModel(
        handle: SavedStateHandle = SavedStateHandle(mapOf(CommentCreateViewModel.ARG_ALBUM_ID to ALBUM_ID)),
        collectorRepository: FakeCollectorRepository = collectors,
    ) = CommentCreateViewModel(handle, albums, collectorRepository, mainDispatcherRule.testDispatcher)

    @Test
    fun `collectors load into options with their initials`() =
        runTest {
            val vm = newViewModel()
            assertTrue(vm.state().isLoadingCollectors)

            advanceUntilIdle()

            assertEquals(
                listOf(
                    CommentCollectorOption(100, "Manolo Bellon", "MB"),
                    CommentCollectorOption(101, "Jaime Monsalve", "JM"),
                ),
                vm.state().collectors,
            )
            assertFalse(vm.state().isLoadingCollectors)
            assertNull(vm.state().collectorsError)
            assertEquals(1, collectors.localCalls)
            assertEquals(1, collectors.remoteCalls)
        }

    @Test
    fun `cached chips show at once and the refresh adds a collector created since`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            collectors.remoteGate = gate
            collectors.remoteResult = DataResult.Success(seed + collector(102, "Ana Pardo"))
            val vm = newViewModel()
            advanceUntilIdle()
            assertEquals(listOf(100, 101), vm.state().collectors.map { it.id })

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(100, 101, 102), vm.state().collectors.map { it.id })
            assertFalse(vm.state().isLoadingCollectors)
        }

    @Test
    fun `a failed refresh keeps the cached chips without an error`() =
        runTest {
            collectors.remoteResult = DataResult.Failure(DataError.Network)
            val vm = newViewModel()
            advanceUntilIdle()

            assertEquals(listOf(100, 101), vm.state().collectors.map { it.id })
            assertNull(vm.state().collectorsError)
            assertFalse(vm.state().isLoadingCollectors)
        }

    @Test
    fun `a restored collector missing from the cache is kept until the backend answers`() =
        runTest {
            val handle = SavedStateHandle(mapOf(CommentCreateViewModel.ARG_ALBUM_ID to ALBUM_ID))
            handle["commentCollectorId"] = 102
            val gate = CompletableDeferred<Unit>()
            collectors.remoteGate = gate
            collectors.remoteResult = DataResult.Success(seed + collector(102, "Ana Pardo"))
            val vm = newViewModel(handle)
            advanceUntilIdle()
            assertEquals(102, vm.state().selectedCollectorId)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(102, vm.state().selectedCollectorId)
        }

    @Test
    fun `a collector load failure exposes the error and retry forces a refresh`() =
        runTest {
            collectors.hasCache = false
            collectors.remoteResult = DataResult.Failure(DataError.Network)
            val vm = newViewModel()
            advanceUntilIdle()
            assertEquals(DataError.Network, vm.state().collectorsError)
            assertFalse(vm.state().isLoadingCollectors)
            assertTrue(vm.state().collectors.isEmpty())

            collectors.remoteResult = DataResult.Success(seed)
            vm.retryCollectors()

            assertTrue(vm.state().isLoadingCollectors)
            assertNull(vm.state().collectorsError)
            advanceUntilIdle()
            assertEquals(listOf(100, 101), vm.state().collectors.map { it.id })
            assertNull(vm.state().collectorsError)
            assertEquals(0, collectors.localCalls)
            assertEquals(2, collectors.remoteCalls)
        }

    @Test
    fun `errors stay hidden until publish and an empty publish sends nothing`() =
        runTest {
            val vm = newViewModel()
            advanceUntilIdle()
            assertFalse(vm.state().errors.hasErrors)

            vm.onPublish()

            assertTrue(vm.state().isCollectorMissing)
            assertTrue(vm.state().isRatingMissing)
            assertTrue(vm.state().isDescriptionMissing)
            assertFalse(vm.state().isSubmitting)
            assertTrue(albums.addedCommentRequests.isEmpty())
        }

    @Test
    fun `each error clears when its own field is fixed`() =
        runTest {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onPublish()

            vm.onCollectorSelected(101)
            assertEquals(CommentCreateErrors(rating = REQUIRED, description = REQUIRED), vm.state().errors)

            vm.onRatingSelected(4)
            assertEquals(CommentCreateErrors(description = REQUIRED), vm.state().errors)
            assertEquals(4, vm.state().rating)

            vm.onDescriptionChanged("   ")
            assertTrue(vm.state().isDescriptionMissing)
            vm.onDescriptionChanged("Una obra")
            assertFalse(vm.state().errors.hasErrors)
        }

    @Test
    fun `typing before the first publish shows no error even when blank`() =
        runTest {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onDescriptionChanged("Texto")
            vm.onDescriptionChanged("   ")

            assertFalse(vm.state().isDescriptionMissing)
        }

    @Test
    fun `an unknown collector and a rating outside one to five are ignored`() =
        runTest {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onCollectorSelected(999)
            vm.onRatingSelected(0)
            vm.onRatingSelected(6)

            assertNull(vm.state().selectedCollectorId)
            assertFalse(vm.state().hasRating)
        }

    @Test
    fun `a valid publish sends the trimmed draft to the album and emits the created comment`() =
        runTest {
            albums.addCommentResult = DataResult.Success(created)
            val vm = newViewModel()
            advanceUntilIdle()
            fill(vm, description = "  Una obra que no envejece.  ")

            vm.onPublish()

            assertTrue(vm.state().isSubmitting)
            advanceUntilIdle()
            assertEquals(
                listOf(ALBUM_ID to NewComment("Una obra que no envejece.", 4, 101)),
                albums.addedCommentRequests,
            )
            assertFalse(vm.state().isSubmitting)
            assertNull(vm.state().submitError)
            val event = checkNotNull(vm.events.value)
            assertEquals(CommentCreateEvent.Created(created), event.getContentIfNotHandled())
            assertNull(event.getContentIfNotHandled())
        }

    @Test
    fun `a failed publish keeps the form and exposes a retryable error`() =
        runTest {
            albums.addCommentResult = DataResult.Failure(DataError.Http(404))
            val vm = newViewModel()
            advanceUntilIdle()
            fill(vm)

            vm.onPublish()
            advanceUntilIdle()

            assertEquals(DataError.Http(404), vm.state().submitError)
            assertFalse(vm.state().isSubmitting)
            assertEquals(101, vm.state().selectedCollectorId)
            assertEquals(4, vm.state().rating)
            assertEquals("Una obra que no envejece.", vm.state().description)
            assertNull(vm.events.value)

            albums.addCommentResult = DataResult.Success(created)
            vm.onPublish()
            advanceUntilIdle()
            assertEquals(2, albums.addedCommentRequests.size)
            assertNull(vm.state().submitError)
            assertEquals(CommentCreateEvent.Created(created), vm.events.value?.getContentIfNotHandled())
        }

    @Test
    fun `editing a field after a failed publish hides the submit error`() =
        runTest {
            val vm = newViewModel()
            advanceUntilIdle()
            fill(vm)
            vm.onPublish()
            advanceUntilIdle()
            assertEquals(DataError.Network, vm.state().submitError)

            vm.onRatingSelected(5)

            assertNull(vm.state().submitError)
        }

    @Test
    fun `two publish taps while submitting make one request`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            albums.addCommentGate = gate
            albums.addCommentResult = DataResult.Success(created)
            val vm = newViewModel()
            advanceUntilIdle()
            fill(vm)

            vm.onPublish()
            advanceUntilIdle()
            vm.onPublish()
            advanceUntilIdle()

            assertEquals(1, albums.addedCommentRequests.size)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, albums.addedCommentRequests.size)
            assertFalse(vm.state().isSubmitting)
        }

    @Test
    fun `inputs are ignored while submitting`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            albums.addCommentGate = gate
            val vm = newViewModel()
            advanceUntilIdle()
            fill(vm)
            vm.onPublish()
            advanceUntilIdle()
            val submitting = vm.state()
            assertTrue(submitting.isSubmitting)

            vm.onCollectorSelected(100)
            vm.onRatingSelected(2)
            vm.onDescriptionChanged("Otro texto")

            assertEquals(submitting, vm.state())
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(101, vm.state().selectedCollectorId)
            assertEquals(4, vm.state().rating)
            assertEquals("Una obra que no envejece.", vm.state().description)
        }

    @Test
    fun `the form and its shown errors are restored from the saved state`() =
        runTest {
            val handle = SavedStateHandle(mapOf(CommentCreateViewModel.ARG_ALBUM_ID to ALBUM_ID))
            val first = newViewModel(handle)
            advanceUntilIdle()
            first.onCollectorSelected(101)
            first.onRatingSelected(3)
            first.onPublish()

            val restored = newViewModel(handle)

            assertEquals(101, restored.state().selectedCollectorId)
            assertEquals(3, restored.state().rating)
            assertEquals("", restored.state().description)
            assertEquals(CommentCreateErrors(description = REQUIRED), restored.state().errors)
            advanceUntilIdle()
            assertEquals(101, restored.state().selectedCollectorId)
            restored.onDescriptionChanged("Texto")
            assertFalse(restored.state().errors.hasErrors)
        }

    @Test
    fun `a restored collector that is no longer in the list is dropped`() =
        runTest {
            val handle = SavedStateHandle(mapOf(CommentCreateViewModel.ARG_ALBUM_ID to ALBUM_ID))
            val withAna = seedWith(collector(102, "Ana Pardo"))
            val first = newViewModel(handle, withAna)
            advanceUntilIdle()
            first.onCollectorSelected(102)
            first.onRatingSelected(5)

            val restored = newViewModel(handle)
            assertEquals(102, restored.state().selectedCollectorId)
            advanceUntilIdle()

            assertNull(restored.state().selectedCollectorId)
            assertEquals(5, restored.state().rating)
        }

    @Test
    fun `a dropped collector is removed from the saved state and its error returns after a publish attempt`() =
        runTest {
            val handle = SavedStateHandle(mapOf(CommentCreateViewModel.ARG_ALBUM_ID to ALBUM_ID))
            val withAna = seedWith(collector(102, "Ana Pardo"))
            val first = newViewModel(handle, withAna)
            advanceUntilIdle()
            first.onCollectorSelected(102)
            first.onRatingSelected(5)
            first.onPublish()
            assertEquals(CommentCreateErrors(description = REQUIRED), first.state().errors)

            val restored = newViewModel(handle)
            advanceUntilIdle()

            assertNull(restored.state().selectedCollectorId)
            assertEquals(CommentCreateErrors(collector = REQUIRED, description = REQUIRED), restored.state().errors)
            assertNull(newViewModel(handle).state().selectedCollectorId)
        }

    @Test
    fun `a missing or non positive album id is rejected before loading collectors`() {
        listOf(
            SavedStateHandle(),
            SavedStateHandle(mapOf(CommentCreateViewModel.ARG_ALBUM_ID to 0)),
            SavedStateHandle(mapOf(CommentCreateViewModel.ARG_ALBUM_ID to -1)),
        ).forEach { handle ->
            assertThrows(IllegalArgumentException::class.java) { newViewModel(handle) }
        }
        assertEquals(0, collectors.localCalls)
    }

    private fun seedWith(extra: Collector): FakeCollectorRepository {
        val all = DataResult.Success(seed + extra)
        return FakeCollectorRepository(hasCache = true, localResult = all, remoteResult = all)
    }

    private fun fill(
        vm: CommentCreateViewModel,
        description: String = "Una obra que no envejece.",
    ) {
        vm.onCollectorSelected(101)
        vm.onRatingSelected(4)
        vm.onDescriptionChanged(description)
    }

    private fun CommentCreateViewModel.state(): CommentCreateUiState = checkNotNull(uiState.value)

    private companion object {
        const val ALBUM_ID = 100
        val REQUIRED = CommentFieldError.REQUIRED
    }
}
