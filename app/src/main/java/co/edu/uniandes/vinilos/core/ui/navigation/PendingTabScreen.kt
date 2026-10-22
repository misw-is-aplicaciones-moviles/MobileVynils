package co.edu.uniandes.vinilos.core.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.core.ui.theme.VinilosTheme
import co.edu.uniandes.vinilos.core.ui.theme.paperTexture

/**
 * Contenido provisional de una pestaña mientras llega su historia: la cabecera oscura con el título
 * y la hoja crema redondeada del kit v2. HU01, HU03 y HU05 la reemplazan por sus pantallas.
 */
@Composable
internal fun PendingTabScreen(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Cabecera del kit v2: fila de 64 dp bajo la barra de estado, título Outfit 19 a 22 dp del borde.
        Box(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 22.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.navContent().semantics { heading() },
            )
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().paperTexture().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(messageRes),
                    modifier = Modifier.navContent(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PendingTabScreenPreview() {
    VinilosTheme {
        Surface(color = MaterialTheme.colorScheme.inverseSurface) {
            PendingTabScreen(titleRes = R.string.artist_list_title, messageRes = R.string.pending_next_sprint)
        }
    }
}
