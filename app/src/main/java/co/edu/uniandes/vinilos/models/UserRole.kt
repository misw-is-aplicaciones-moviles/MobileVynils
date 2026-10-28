package co.edu.uniandes.vinilos.models

/** Rol de la sesión. El backend no autentica: lo elige quien abre la app (TR-06). */
enum class UserRole {
    VISITOR,
    COLLECTOR,
}
