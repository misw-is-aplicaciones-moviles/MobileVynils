package co.edu.uniandes.vinilos.viewmodels

import co.edu.uniandes.vinilos.models.UserRole

/** Everything the new album screen draws. [errors] stays empty until the first "Crear álbum". */
data class AlbumCreateUiState(
    val form: AlbumForm = AlbumForm(),
    val errors: AlbumFormErrors = AlbumFormErrors(),
    val isSubmitting: Boolean = false,
    val role: UserRole? = null,
)
