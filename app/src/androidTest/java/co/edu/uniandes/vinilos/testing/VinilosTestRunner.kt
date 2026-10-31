package co.edu.uniandes.vinilos.testing

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Instala el control de transporte antes de que la app cree su cliente HTTP. */
class VinilosTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        BackendProxySelector.install()
        super.onCreate(arguments)
    }

    override fun finish(
        resultCode: Int,
        results: Bundle?,
    ) {
        BackendProxySelector.uninstall()
        super.finish(resultCode, results)
    }
}
