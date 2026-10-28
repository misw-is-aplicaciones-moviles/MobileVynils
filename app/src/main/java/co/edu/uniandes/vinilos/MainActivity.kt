package co.edu.uniandes.vinilos

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.descendants
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import co.edu.uniandes.vinilos.databinding.ActivityMainBinding
import co.edu.uniandes.vinilos.ui.common.navigateToRoleSelector

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val cream = ContextCompat.getColor(this, R.color.vinilos_cream)
        // Íconos claros sobre la cabecera carbón y oscuros sobre la barra inferior crema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(cream, Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // El contenedor es el único que aplica el margen de la navegación del sistema; la barra de
        // Material lo añadiría otra vez por su cuenta (en API 21 sumaba 48 dp de más).
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { bar, insets ->
            bar.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { _, insets -> insets }

        val navHost = binding.navHost.getFragment<NavHostFragment>()
        val navController = navHost.navController
        // El rol vive solo en memoria. Navigation sí guarda la pantalla: si el proceso murió,
        // restaura el catálogo con el rol ya perdido. Un giro no entra aquí, porque el proceso sigue.
        returnToRoleSelectorIfRoleWasLost(savedInstanceState, navController)
        setupBottomNavigation(navController)
    }

    private fun returnToRoleSelectorIfRoleWasLost(
        savedInstanceState: Bundle?,
        navController: NavController,
    ) {
        if (savedInstanceState == null) return
        val role =
            (application as VinilosApplication)
                .container.sessionRepository.role.value
        if (role != null || navController.currentDestination?.id == R.id.roleSelectFragment) return
        navController.navigateToRoleSelector()
    }

    private fun setupBottomNavigation(navController: NavController) {
        binding.bottomNav.setupWithNavController(navController)
        // Misma navegación que NavigationUI pero sin animaciones de vista: la transición la da cada Fragment.
        binding.bottomNav.setOnItemSelectedListener { item ->
            // El punto acompaña a la selección en el mismo cuadro, sin esperar al cambio de destino.
            binding.bottomNav.post { moveNavDot(item.itemId) }
            navController.navigate(
                item.itemId,
                null,
                navOptions {
                    launchSingleTop = true
                    restoreState = true
                    // El inicio del grafo es el selector y sale de la pila al elegir rol.
                    // Las pestañas se anclan al catálogo, que queda como raíz.
                    popUpTo(R.id.albumListFragment) { saveState = true }
                },
            )
            true
        }
        binding.bottomNav.setOnItemReselectedListener { }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBottomBar = destination.id != R.id.roleSelectFragment
            binding.bottomBar.isVisible = showBottomBar
            if (showBottomBar) {
                binding.bottomNav.post { moveNavDot(destination.id) }
            }
        }
    }

    /** Coloca el punto del kit v2 centrado bajo la etiqueta de la pestaña activa. */
    private fun moveNavDot(itemId: Int) {
        val item = binding.bottomNav.findViewById<ViewGroup>(itemId) ?: return
        val label = item.visibleLabel() ?: return
        val barLocation = IntArray(2).also(binding.bottomBar::getLocationInWindow)
        val labelLocation = IntArray(2).also(label::getLocationInWindow)
        val gap = resources.getDimension(R.dimen.nav_dot_label_gap)
        val dot = binding.navDot
        dot.translationX = labelLocation[0] - barLocation[0] + label.width / 2f - dot.width / 2f
        dot.translationY = labelLocation[1] - barLocation[1] + label.height + gap
    }

    /** La etiqueta visible del ítem (Material alterna entre una etiqueta pequeña y una grande). */
    private fun ViewGroup.visibleLabel(): TextView? =
        descendants.filterIsInstance<TextView>().firstOrNull { it.isShown && it.text.isNotEmpty() }
}
