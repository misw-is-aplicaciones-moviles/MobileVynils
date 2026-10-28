package co.edu.uniandes.vinilos.di

import co.edu.uniandes.vinilos.repositories.SessionRepository
import co.edu.uniandes.vinilos.repositories.SessionRepositoryImpl

/** Dependencias de la app. Por ahora solo la sesión (TR-06); la red entra con TR-02. */
class AppContainer {
    val sessionRepository: SessionRepository by lazy { SessionRepositoryImpl() }
}
