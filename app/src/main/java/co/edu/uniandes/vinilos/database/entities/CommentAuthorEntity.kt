package co.edu.uniandes.vinilos.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Quién escribió cada comentario. `GET /albums/:id` no lo dice; sale de `GET /collectors` o de la publicación. */
@Entity(tableName = "comment_authors")
internal data class CommentAuthorEntity(
    @PrimaryKey val commentId: Int,
    val collectorId: Int,
    val collectorName: String,
)
