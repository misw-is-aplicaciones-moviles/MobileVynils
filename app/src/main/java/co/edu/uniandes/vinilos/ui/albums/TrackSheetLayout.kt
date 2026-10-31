package co.edu.uniandes.vinilos.ui.albums

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import co.edu.uniandes.vinilos.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Hoja crema de la canción. El borde superior sube alrededor del botón de cerrar
 * con la misma corona que la barra del botón «+». Las esquinas exteriores usan
 * el radio de 28 dp de la hoja del catálogo.
 */
class TrackSheetLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.vinilos_cream)
            }
        private val shadowPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.vinilos_sheet_shadow)
                val blur = resources.getDimension(R.dimen.track_sheet_shadow_blur)
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
        private val sheetPath = Path()
        private val crown = RectF()
        private val cornerOval = RectF()
        private val shadowOffset = resources.getDimension(R.dimen.track_sheet_shadow_offset)
        private val shadowBlur = resources.getDimension(R.dimen.track_sheet_shadow_blur)
        private val cornerRadius = resources.getDimension(R.dimen.sheet_corner_radius)
        private val humpRadius = resources.getDimension(R.dimen.track_sheet_hump_radius)
        private val humpRise = resources.getDimension(R.dimen.track_sheet_hump_rise)
        private val humpFlare = resources.getDimension(R.dimen.track_sheet_hump_flare)
        private val shadowCanvas = Canvas()
        private var shadowStrip: Bitmap? = null

        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            val flat = buildSheetPath()
            drawShadow(canvas, flat)
            canvas.drawPath(sheetPath, fillPaint)
        }

        override fun onDetachedFromWindow() {
            shadowStrip?.recycle()
            shadowStrip = null
            super.onDetachedFromWindow()
        }

        /**
         * El desenfoque se pinta en un bitmap propio. Una capa de software sobre toda la hoja
         * deja de dibujarse cuando la pantalla está en horizontal.
         */
        private fun drawShadow(
            canvas: Canvas,
            flat: Float,
        ) {
            val stripHeight = (flat + shadowBlur).toInt().coerceIn(1, height)
            val strip = shadowStrip(stripHeight)
            strip.eraseColor(Color.TRANSPARENT)
            shadowCanvas.setBitmap(strip)
            shadowCanvas.save()
            shadowCanvas.translate(0f, -shadowOffset)
            shadowCanvas.drawPath(sheetPath, shadowPaint)
            shadowCanvas.restore()
            shadowCanvas.setBitmap(null)
            canvas.drawBitmap(strip, 0f, 0f, null)
        }

        private fun shadowStrip(stripHeight: Int): Bitmap {
            val current = shadowStrip
            if (current != null && current.width == width && current.height == stripHeight) {
                return current
            }
            current?.recycle()
            return Bitmap.createBitmap(width, stripHeight, Bitmap.Config.ARGB_8888).also { shadowStrip = it }
        }

        /**
         * La corona sigue a [co.edu.uniandes.vinilos.ui.common.BottomBarHumpEdgeTreatment].
         * La cima queda en el relleno superior; el borde plano, [humpRise] más abajo del centro.
         * Cada extremo de ese borde plano cierra con un cuarto de círculo.
         *
         * @return la altura del borde plano, donde terminan las esquinas.
         */
        private fun buildSheetPath(): Float {
            val center = width / 2f
            val widthF = width.toFloat()
            val peak = paddingTop.toFloat()
            val crownCenterY = peak + humpRadius
            val flat = crownCenterY + humpRise
            val join = Math.toRadians(JOIN_ANGLE_DEGREES)
            val joinX = humpRadius * cos(join).toFloat()
            val joinY = -humpRise - humpRadius * sin(join).toFloat()
            val shoulder = center - joinX + joinY * tan(join).toFloat()
            val flare = humpFlare.coerceAtMost(center)
            val corner = cornerRadius.coerceAtMost((center - flare).coerceAtLeast(0f))

            sheetPath.reset()
            sheetPath.moveTo(0f, flat + corner)
            cornerOval.set(0f, flat, corner + corner, flat + corner + corner)
            sheetPath.arcTo(cornerOval, LEFT_CORNER_START_DEGREES, CORNER_SWEEP_DEGREES, false)
            sheetPath.lineTo(center - flare, flat)
            sheetPath.quadTo(shoulder, flat, center - joinX, flat + joinY)
            crown.set(
                center - humpRadius,
                crownCenterY - humpRadius,
                center + humpRadius,
                crownCenterY + humpRadius,
            )
            sheetPath.arcTo(crown, ARC_START_DEGREES, ARC_SWEEP_DEGREES, false)
            sheetPath.quadTo(widthF - shoulder, flat, center + flare, flat)
            sheetPath.lineTo(widthF - corner, flat)
            cornerOval.set(widthF - corner - corner, flat, widthF, flat + corner + corner)
            sheetPath.arcTo(cornerOval, RIGHT_CORNER_START_DEGREES, CORNER_SWEEP_DEGREES, false)
            sheetPath.lineTo(widthF, height.toFloat())
            sheetPath.lineTo(0f, height.toFloat())
            sheetPath.close()
            return flat
        }

        private companion object {
            /** Donde la corona se une a los hombros, sobre la horizontal del centro. */
            const val JOIN_ANGLE_DEGREES = 20.0

            /** 180° + el ángulo de unión. El barrido positivo pasa por arriba del círculo. */
            const val ARC_START_DEGREES = 200f
            const val ARC_SWEEP_DEGREES = 140f

            /** Cuarto de círculo de la esquina superior izquierda, de izquierda a arriba. */
            const val LEFT_CORNER_START_DEGREES = 180f
            const val CORNER_SWEEP_DEGREES = 90f

            /** Cuarto de círculo de la esquina superior derecha, de arriba a la derecha. */
            const val RIGHT_CORNER_START_DEGREES = 270f
        }
    }
