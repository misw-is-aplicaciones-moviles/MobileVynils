package co.edu.uniandes.vinilos.database

import co.edu.uniandes.vinilos.database.entities.CommentAuthorEntity
import co.edu.uniandes.vinilos.models.CommentAuthor

internal fun CommentAuthor.toEntity(commentId: Int): CommentAuthorEntity =
    CommentAuthorEntity(
        commentId = commentId,
        collectorId = collectorId,
        collectorName = name,
    )

internal fun CommentAuthorEntity.toModel(): CommentAuthor =
    CommentAuthor(
        collectorId = collectorId,
        name = collectorName,
    )
