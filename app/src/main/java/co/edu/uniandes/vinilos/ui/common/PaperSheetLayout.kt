package co.edu.uniandes.vinilos.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.withClip
import co.edu.uniandes.vinilos.R

/**
 * Hoja crema del kit v2: esquinas superiores redondeadas y una retícula sutil de puntos ("papel").
 * Contenedor de cada pantalla bajo la cabecera carbón.
 */
class PaperSheetLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        init {
            background = PaperSheetDrawable(context)
        }
    }

private class PaperSheetDrawable(
    context: Context,
) : Drawable() {
    private val cornerRadius = context.resources.getDimension(R.dimen.sheet_corner_radius)
    private val dotSpacing = context.resources.getDimension(R.dimen.paper_dot_spacing)
    private val dotRadius = context.resources.getDimension(R.dimen.paper_dot_radius)
    private val sheetPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.vinilos_cream) }
    private val dotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.vinilos_paper_dot) }
    private val sheetPath = Path()
    private val sheetRect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        sheetRect.set(bounds)
        sheetPath.reset()
        // Solo las esquinas superiores van redondeadas.
        val radii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
        sheetPath.addRoundRect(sheetRect, radii, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(sheetPath, sheetPaint)
        canvas.withClip(sheetPath) {
            var y = bounds.top + dotSpacing / 2
            while (y < bounds.bottom) {
                var x = bounds.left + dotSpacing / 2
                while (x < bounds.right) {
                    drawCircle(x, y, dotRadius, dotPaint)
                    x += dotSpacing
                }
                y += dotSpacing
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        sheetPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        sheetPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
