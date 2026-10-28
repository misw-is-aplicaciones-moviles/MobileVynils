package co.edu.uniandes.vinilos.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.repositories.SessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RoleSelectViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `onRoleSelected stores visitor and opens the catalog once`() {
        val session = FakeSessionRepository()
        val viewModel = RoleSelectViewModel(session)

        viewModel.onRoleSelected(UserRole.VISITOR)

        assertEquals(UserRole.VISITOR, session.role.value)
        assertEquals(RoleSelectEvent.OpenCatalog, viewModel.events.value?.getContentIfNotHandled())
        assertNull(viewModel.events.value?.getContentIfNotHandled())
    }

    @Test
    fun `onRoleSelected stores collector and opens the catalog once`() {
        val session = FakeSessionRepository()
        val viewModel = RoleSelectViewModel(session)

        viewModel.onRoleSelected(UserRole.COLLECTOR)

        assertEquals(UserRole.COLLECTOR, session.role.value)
        assertEquals(RoleSelectEvent.OpenCatalog, viewModel.events.value?.getContentIfNotHandled())
        assertNull(viewModel.events.value?.getContentIfNotHandled())
    }
}

private class FakeSessionRepository : SessionRepository {
    private val _role = MutableLiveData<UserRole?>(null)
    override val role: LiveData<UserRole?> = _role

    override fun selectRole(role: UserRole) {
        _role.value = role
    }

    override fun clearRole() {
        _role.value = null
    }
}
