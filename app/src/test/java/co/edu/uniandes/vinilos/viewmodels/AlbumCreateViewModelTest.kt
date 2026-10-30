package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.DataError
import co.edu.uniandes.vinilos.models.DataResult
import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.RecordLabel
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.testing.FakeAlbumRepository
import co.edu.uniandes.vinilos.testing.FakeSessionRepository
import co.edu.uniandes.vinilos.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber") // Fixture id and date.
class AlbumCreateViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val releaseDate = LocalDate.of(1980, 6, 30)

    private val draft =
        NewAlbum(
            name = "The Game",
            coverUrl = "https://example.com/the-game.jpg",
            releaseDate = releaseDate,
            description = "Noveno álbum de Queen.",
            genre = Genre.ROCK,
            recordLabel = RecordLabel.EMI,
        )

    private val created =
        Album(
            id = 101,
            name = draft.name,
            coverUrl = draft.coverUrl,
            releaseDate = releaseDate,
            description = draft.description,
            genre = draft.genre,
            recordLabel = draft.recordLabel.apiName,
            performerName = null,
            tracks = emptyList(),
            performers = emptyList(),
            comments = emptyList(),
        )

    private fun viewModel(repository: FakeAlbumRepository) =
        AlbumCreateViewModel(repository, FakeSessionRepository(UserRole.COLLECTOR))

    private fun AlbumCreateViewModel.fillValidForm() {
        onNameChanged(draft.name)
        onCoverChanged(draft.coverUrl)
        onReleaseDateSelected(draft.releaseDate)
        onDescriptionChanged(draft.description)
        onGenreSelected(draft.genre)
        onRecordLabelSelected(draft.recordLabel)
    }

    private fun AlbumCreateViewModel.state() = checkNotNull(uiState.value)

    private fun AlbumCreateViewModel.nextEvent() = events.value?.getContentIfNotHandled()

    @Test
    fun `invalid submit shows every error and does not call the repository`() =
        runTest {
            val repository = FakeAlbumRepository()
            val viewModel = viewModel(repository)

            viewModel.onSubmit()
            advanceUntilIdle()

            val expected =
                AlbumFormErrors(
                    name = FieldError.REQUIRED,
                    coverUrl = FieldError.REQUIRED,
                    releaseDate = FieldError.REQUIRED,
                    description = FieldError.REQUIRED,
                    genre = FieldError.MISSING_GENRE,
                    recordLabel = FieldError.MISSING_RECORD_LABEL,
                )
            assertEquals(expected, viewModel.state().errors)
            assertFalse(viewModel.state().isSubmitting)
            assertTrue(repository.createdDrafts.isEmpty())
            assertNull(viewModel.nextEvent())
        }

    @Test
    fun `failed submit keeps the form and a retry creates the album`() =
        runTest {
            val repository = FakeAlbumRepository(createResult = DataResult.Failure(DataError.Network))
            val viewModel = viewModel(repository)
            viewModel.fillValidForm()
            val filledForm = viewModel.state().form

            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(AlbumCreateEvent.Failed(DataError.Network), viewModel.nextEvent())
            assertFalse("The button must be enabled again to retry", viewModel.state().isSubmitting)
            assertEquals(filledForm, viewModel.state().form)

            repository.createResult = DataResult.Success(created)
            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(AlbumCreateEvent.Created, viewModel.nextEvent())
            assertEquals(listOf(draft, draft), repository.createdDrafts)
        }

    @Test
    fun `second tap while submitting sends a single request`() =
        runTest {
            val repository = FakeAlbumRepository(createResult = DataResult.Success(created))
            val viewModel = viewModel(repository)
            viewModel.fillValidForm()

            viewModel.onSubmit()
            viewModel.onSubmit()
            advanceUntilIdle()

            assertEquals(listOf(draft), repository.createdDrafts)
            assertEquals(AlbumCreateEvent.Created, viewModel.nextEvent())
        }
}
