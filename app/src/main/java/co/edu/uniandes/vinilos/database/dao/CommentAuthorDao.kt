package co.edu.uniandes.vinilos.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import co.edu.uniandes.vinilos.database.entities.CommentAuthorEntity

/**
 * Índice de autores de comentarios (HU09). Solo crece: la app no borra comentarios, así que una fila nunca queda
 * equivocada, y una respuesta vieja de `GET /collectors` no puede borrar el autor de una publicación reciente.
 */
@Dao
internal interface CommentAuthorDao {
    // Se lee completo y no con `WHERE commentId IN (:ids)`: así no hay una variable por id ni límite de 999 (#139).
    @Query("SELECT * FROM comment_authors")
    suspend fun getAll(): List<CommentAuthorEntity>

    @Upsert
    suspend fun upsert(author: CommentAuthorEntity)

    @Upsert
    suspend fun upsertAll(authors: List<CommentAuthorEntity>)
}
