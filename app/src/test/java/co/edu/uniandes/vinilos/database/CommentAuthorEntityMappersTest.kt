package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.database.entities.CommentAuthorEntity
import co.edu.uniandes.vinilos.models.CommentAuthor
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("MagicNumber") // Fixture ids.
class CommentAuthorEntityMappersTest {
    @Test
    fun `the entity is keyed by the comment and a round trip keeps the author`() {
        val author = CommentAuthor(collectorId = 101, name = "Jaime Monsalve")

        val entity = author.toEntity(commentId = 7)

        assertEquals(CommentAuthorEntity(commentId = 7, collectorId = 101, collectorName = "Jaime Monsalve"), entity)
        assertEquals(author, entity.toModel())
    }
}
