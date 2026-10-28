package co.edu.uniandes.vinilos.repositories

import androidx.lifecycle.LiveData
import co.edu.uniandes.vinilos.models.UserRole

/**
 * Rol elegido en esta ejecución. Vive en memoria: si el proceso muere, la app vuelve al selector.
 */
interface SessionRepository {
    val role: LiveData<UserRole?>

    fun selectRole(role: UserRole)

    fun clearRole()
}
