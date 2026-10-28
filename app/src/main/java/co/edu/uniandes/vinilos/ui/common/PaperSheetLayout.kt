package co.edu.uniandes.vinilos.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import co.edu.uniandes.vinilos.R
import kotlin.math.roundToInt

/**
 * Hoja crema del kit v2: esquinas superiores redondeadas y una retícula sutil de puntos ("papel").
 * Contenedor de cada pantalla bajo la cabecera carbón.
 *
 * El fondo no recorta a los hijos: cuando HU01 ponga un RecyclerView en la hoja, el contenido
 * puede pasar por encima de las esquinas al hacer scroll. Se resuelve allí con un
 * ViewOutlineProvider y clipToOutline.
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
    private val sheetPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.vinilos_cream) }

    // La retícula se pinta con un mosaico de un solo punto repetido por el shader: un drawPath
    // por cuadro en lugar de miles de drawCircle.
    private val dotTile = createDotTile(context)
    private val dotShader = BitmapShader(dotTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = dotShader }
    private val sheetPath = Path()
    private val sheetRect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        sheetRect.set(bounds)
        sheetPath.reset()
        // Solo las esquinas superiores van redondeadas.
        val radii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
        sheetPath.addRoundRect(sheetRect, radii, Path.Direction.CW)
        // La retícula arranca en la esquina de la hoja, no en la del lienzo.
        dotShader.setLocalMatrix(
            Matrix().apply { setTranslate(bounds.left.toFloat(), bounds.top.toFloat()) },
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(sheetPath, sheetPaint)
        canvas.drawPath(sheetPath, dotPaint)
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

    private companion object {
        /** Celda de la retícula con un punto en el centro. */
        fun createDotTile(context: Context): Bitmap {
            val resources = context.resources
            val size = resources.getDimension(R.dimen.paper_dot_spacing).roundToInt().coerceAtLeast(1)
            val radius = resources.getDimension(R.dimen.paper_dot_radius)
            val paint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG,
                ).apply { color = ContextCompat.getColor(context, R.color.vinilos_paper_dot) }
            return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { tile ->
                Canvas(tile).drawCircle(size / 2f, size / 2f, radius, paint)
            }
        }
    }
}
