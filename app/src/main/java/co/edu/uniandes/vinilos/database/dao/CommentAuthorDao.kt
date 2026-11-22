package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.MAX_IDS_PER_STATEMENT
import co.edu.uniandes.vinilos.database.entities.CommentAuthorEntity

/**
 * Índice de autores de comentarios (HU09). Solo crece: la app no borra comentarios, así que una fila nunca queda
 * equivocada, y una respuesta vieja de `GET /collectors` no puede borrar el autor de una publicación reciente.
 */
@Dao
internal interface CommentAuthorDao {
    @Query("SELECT * FROM comment_authors")
    suspend fun getAll(): List<CommentAuthorEntity>

    // Recibe a lo sumo MAX_IDS_PER_STATEMENT ids: Room crea una variable por id.
    @Query("SELECT * FROM comment_authors WHERE commentId IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<CommentAuthorEntity>

    @Transaction
    suspend fun getByIdsInChunks(ids: Collection<Int>): List<CommentAuthorEntity> =
        ids.chunked(MAX_IDS_PER_STATEMENT).flatMap { getByIds(it) }

    @Upsert
    suspend fun upsert(author: CommentAuthorEntity)

    @Upsert
    suspend fun upsertAll(authors: List<CommentAuthorEntity>)
}
