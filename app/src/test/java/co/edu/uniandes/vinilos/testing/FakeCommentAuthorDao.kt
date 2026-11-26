package co.edu.uniandes.vinilos.testing

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.CommentAuthorDao
import co.edu.uniandes.vinilos.database.entities.CommentAuthorEntity

/**
 * In-memory [CommentAuthorDao]. [readFailure] makes every read throw and [writeFailure] makes every write throw before
 * changing anything, as an unreadable database or a full disk would. [idBatches] holds the size of each [getByIds] call.
 */
internal class FakeCommentAuthorDao(
    initial: List<CommentAuthorEntity> = emptyList(),
    var readFailure: SQLiteException? = null,
    var writeFailure: SQLiteException? = null,
) : CommentAuthorDao {
    private val rows = initial.associateBy { it.commentId }.toMutableMap()

    var readCount = 0
        private set
    var writeCount = 0
        private set
    private val batches = mutableListOf<Int>()
    val idBatches: List<Int> get() = batches.toList()

    /** What the table holds now, by comment id, without counting as a read. */
    val stored: Map<Int, CommentAuthorEntity> get() = rows.toMap()

    override suspend fun getAll(): List<CommentAuthorEntity> {
        readCount++
        readFailure?.let { throw it }
        return rows.values.toList()
    }

    override suspend fun getByIds(ids: List<Int>): List<CommentAuthorEntity> {
        readCount++
        batches += ids.size
        readFailure?.let { throw it }
        return ids.mapNotNull(rows::get)
    }

    override suspend fun upsert(author: CommentAuthorEntity) {
        write { rows[author.commentId] = author }
    }

    override suspend fun upsertAll(authors: List<CommentAuthorEntity>) {
        write { authors.forEach { rows[it.commentId] = it } }
    }

    private fun write(change: () -> Unit) {
        writeCount++
        writeFailure?.let { throw it }
        change()
    }
}
