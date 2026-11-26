package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.Comment

sealed interface CommentCreateEvent {
    data class Created(
        val comment: Comment,
    ) : CommentCreateEvent
}
