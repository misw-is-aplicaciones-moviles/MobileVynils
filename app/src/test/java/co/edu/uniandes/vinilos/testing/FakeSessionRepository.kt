package co.edu.uniandes.vinilos.testing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.SessionRepository

/** In-memory session that counts how many times the role was cleared. */
internal class FakeSessionRepository(
    initialRole: UserRole? = null,
) : SessionRepository {
    private val _role = MutableLiveData(initialRole)
    override val role: LiveData<UserRole?> = _role

    var clearCount = 0
        private set

    override fun selectRole(role: UserRole) {
        _role.value = role
    }

    override fun clearRole() {
        clearCount++
        _role.value = null
    }
}
