package co.edu.uniandes.vinilos

import android.app.Application
import android.os.StrictMode
import co.edu.uniandes.vinilos.di.AppContainer

class VinilosApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        container = AppContainer(this)
    }

    // En debug, cualquier E/S en el hilo principal o fuga de recursos queda en logcat (docs/07).
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }
}
