package co.edu.uniandes.vinilos.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import co.edu.uniandes.vinilos.models.UserRole

class SessionRepositoryImpl : SessionRepository {
    private val _role = MutableLiveData<UserRole?>(null)
    override val role: LiveData<UserRole?> = _role

    override fun selectRole(role: UserRole) {
        _role.value = role
    }

    override fun clearRole() {
        _role.value = null
    }
}
