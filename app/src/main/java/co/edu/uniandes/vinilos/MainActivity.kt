package co.edu.uniandes.vinilos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.edu.uniandes.vinilos.core.ui.navigation.VinilosApp
import co.edu.uniandes.vinilos.core.ui.theme.VinilosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // La cabecera es carbón en ambos temas: iconos claros en la barra de estado.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            VinilosTheme {
                VinilosApp()
            }
        }
    }
}
