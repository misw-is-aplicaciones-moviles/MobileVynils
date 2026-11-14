package co.edu.uniandes.vinilos.ui.common

import android.content.Context
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import com.google.android.material.divider.MaterialDividerItemDecoration

/**
 * Separador de las listas del kit v2: 1 dp, color de línea, con margen a cada lado y sin línea tras la última fila.
 * El margen final es el de la fila cuando difiere del inicial.
 */
internal fun createListDivider(
    context: Context,
    @DimenRes insetEndRes: Int = R.dimen.list_divider_inset,
): RecyclerView.ItemDecoration {
    val resources = context.resources
    return MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL).apply {
        dividerThickness = resources.getDimensionPixelSize(R.dimen.list_divider_thickness)
        dividerColor = ContextCompat.getColor(context, R.color.vinilos_line)
        dividerInsetStart = resources.getDimensionPixelSize(R.dimen.list_divider_inset)
        dividerInsetEnd = resources.getDimensionPixelSize(insetEndRes)
        isLastItemDecorated = false
    }
}
