package co.edu.uniandes.vinilos.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemCollectorBinding
import co.edu.uniandes.vinilos.ui.collectors.CollectorViewHolder
import co.edu.uniandes.vinilos.viewmodels.CollectorItem
import co.edu.uniandes.vinilos.viewmodels.CollectorSummaryLine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** La tercera línea con los recursos reales, sin backend: las E2E no crean coleccionistas con álbumes. */
@RunWith(AndroidJUnit4::class)
class CollectorAdapterTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Vinilos)

    @Test
    fun third_line_writes_singular_plural_and_no_albums() {
        assertEquals("1 álbum · 1 favorito", summaryOf(CollectorSummaryLine.Counts(albums = 1, favorites = 1)))
        assertEquals("2 álbumes · 3 favoritos", summaryOf(CollectorSummaryLine.Counts(albums = 2, favorites = 3)))
        assertEquals("1 álbum · 0 favoritos", summaryOf(CollectorSummaryLine.Counts(albums = 1, favorites = 0)))
        assertEquals("Sin álbumes aún", summaryOf(CollectorSummaryLine.NoAlbums))
    }

    private fun summaryOf(summary: CollectorSummaryLine): String {
        var text = ""
        instrumentation.runOnMainSync {
            val binding = ItemCollectorBinding.inflate(LayoutInflater.from(context))
            val item = CollectorItem(1, "01", "MB", "Manolo Bellon", "m@example.com", summary)
            CollectorViewHolder(binding) {}.bind(item)
            text = binding.collectorSummary.text.toString()
        }
        return text
    }
}
