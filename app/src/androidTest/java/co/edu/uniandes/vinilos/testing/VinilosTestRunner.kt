package co.edu.uniandes.vinilos.testing

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import co.edu.uniandes.vinilos.network.BackendEndpoint

/** Selecciona el endpoint de pruebas antes de crear el cliente HTTP de la app. */
class VinilosTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        check(arguments?.getString("isolatedE2e") == "true") {
            "Ejecuta ./gradlew connectedDebugAndroidTest para preparar el backend aislado"
        }
        BackendEndpoint.useIsolatedTests()
        super.onCreate(arguments)
    }
}
