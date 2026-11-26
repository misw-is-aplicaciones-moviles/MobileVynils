package co.edu.uniandes.vinilos.network

import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CommentAuthor

/**
 * Everything one `GET /collectors` gives: the list and, for each comment id, the collector who wrote it.
 * Both come from the same response, so the comment authors cost no extra request.
 */
internal data class CollectorDirectory(
    val collectors: List<Collector>,
    val commentAuthors: Map<Int, CommentAuthor>,
)
