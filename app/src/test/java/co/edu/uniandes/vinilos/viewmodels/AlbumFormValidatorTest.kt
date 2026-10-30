package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Genre
import co.edu.uniandes.vinilos.models.NewAlbum
import co.edu.uniandes.vinilos.models.RecordLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AlbumFormValidatorTest {
    private val releaseDate = LocalDate.of(1980, 6, 30)

    private val validForm =
        AlbumForm(
            name = "The Game",
            coverUrl = "https://example.com/the-game.jpg",
            releaseDate = releaseDate,
            description = "Noveno álbum de Queen.",
            genre = Genre.ROCK,
            recordLabel = RecordLabel.EMI,
        )

    @Test
    fun `valid form returns the album with surrounding spaces removed`() {
        val form =
            validForm.copy(
                name = "  The Game ",
                coverUrl = " https://example.com/the-game.jpg ",
                description = " Noveno álbum de Queen.  ",
            )

        val result = AlbumFormValidator.validate(form)

        val expected =
            NewAlbum(
                name = "The Game",
                coverUrl = "https://example.com/the-game.jpg",
                releaseDate = releaseDate,
                description = "Noveno álbum de Queen.",
                genre = Genre.ROCK,
                recordLabel = RecordLabel.EMI,
            )
        assertEquals(AlbumFormResult.Valid(expected), result)
    }

    @Test
    fun `each invalid partition flags only its own field`() {
        val required = FieldError.REQUIRED
        val partitions =
            listOf(
                Partition("empty name", validForm.copy(name = ""), AlbumFormErrors(name = required)),
                Partition("blank name", validForm.copy(name = "   "), AlbumFormErrors(name = required)),
                Partition("empty cover", validForm.copy(coverUrl = ""), AlbumFormErrors(coverUrl = required)),
                Partition("blank cover", validForm.copy(coverUrl = " \t"), AlbumFormErrors(coverUrl = required)),
                Partition("no date", validForm.copy(releaseDate = null), AlbumFormErrors(releaseDate = required)),
                Partition(
                    "empty description",
                    validForm.copy(description = ""),
                    AlbumFormErrors(description = required),
                ),
                Partition(
                    "blank description",
                    validForm.copy(description = "\n "),
                    AlbumFormErrors(description = required),
                ),
                Partition("no genre", validForm.copy(genre = null), AlbumFormErrors(genre = FieldError.MISSING_GENRE)),
                Partition(
                    "unknown genre",
                    validForm.copy(genre = Genre.UNKNOWN),
                    AlbumFormErrors(genre = FieldError.MISSING_GENRE),
                ),
                Partition(
                    "no record label",
                    validForm.copy(recordLabel = null),
                    AlbumFormErrors(recordLabel = FieldError.MISSING_RECORD_LABEL),
                ),
            )

        partitions.forEach { partition ->
            val result = AlbumFormValidator.validate(partition.form)

            assertEquals(partition.name, AlbumFormResult.Invalid(partition.expected), result)
        }
    }

    /** One row of the table: a valid form with a single field broken, and the only error it must produce. */
    private data class Partition(
        val name: String,
        val form: AlbumForm,
        val expected: AlbumFormErrors,
    )
}
