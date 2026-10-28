package co.edu.uniandes.vinilos

import android.app.Application
import co.edu.uniandes.vinilos.di.AppContainer

class VinilosApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
