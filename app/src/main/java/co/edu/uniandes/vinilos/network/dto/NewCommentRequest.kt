package co.edu.uniandes.vinilos.network.dto

import co.edu.uniandes.vinilos.models.NewComment

/** The BackVynils comment endpoint accepts exactly these three fields; the collector goes as `{ "id": … }`. */
internal data class NewCommentRequest(
    val description: String,
    val rating: Int,
    val collector: CollectorRefRequest,
)

internal data class CollectorRefRequest(
    val id: Int,
)

internal fun NewComment.toRequest(): NewCommentRequest =
    NewCommentRequest(
        description = description,
        rating = rating,
        collector = CollectorRefRequest(collectorId),
    )
