package co.edu.uniandes.vinilos.testing

import android.database.sqlite.SQLiteException
import co.edu.uniandes.vinilos.database.dao.CommentAuthorDao
import co.edu.uniandes.vinilos.database.entities.CommentAuthorEntity

/**
 * In-memory [CommentAuthorDao]. [readFailure] makes [getAll] throw and [writeFailure] makes every write throw before
 * changing anything, as an unreadable database or a full disk would.
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

    /** What the table holds now, by comment id, without counting as a read. */
    val stored: Map<Int, CommentAuthorEntity> get() = rows.toMap()

    override suspend fun getAll(): List<CommentAuthorEntity> {
        readCount++
        readFailure?.let { throw it }
        return rows.values.toList()
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
