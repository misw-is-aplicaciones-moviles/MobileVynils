package co.edu.uniandes.vinilos.ui.albums

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import co.edu.uniandes.vinilos.R

/**
 * Caja de duración. Vacía lleva la sombra del mockup; con error, el anillo crema y carbón.
 * El texto y el color los pone la hoja.
 */
class TrackDurationView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : AppCompatTextView(context, attrs, defStyleAttr) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
            }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shape = RectF()
        private val radius: Float
        private val shadowOffsetY: Float
        private val shadowSpread: Float
        private val ringOuter: Float
        private val ringInner: Float

        var isInvalid: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        init {
            val resources = resources
            radius = resources.getDimension(R.dimen.track_duration_radius)
            shadowOffsetY = resources.getDimension(R.dimen.track_duration_shadow_offset_y)
            shadowSpread = resources.getDimension(R.dimen.track_duration_shadow_spread)
            ringOuter = resources.getDimension(R.dimen.track_duration_ring_outer)
            ringInner = resources.getDimension(R.dimen.track_duration_ring_inner)
            val shadowInsetBottom = resources.getDimensionPixelSize(R.dimen.track_duration_shadow_inset_bottom)
            val ringSpace = resources.getDimensionPixelSize(R.dimen.track_duration_ring_space)
            val strokeWidth = resources.getDimension(R.dimen.track_duration_stroke)
            val dash = resources.getDimension(R.dimen.track_duration_dash)
            val box = resources.getDimensionPixelSize(R.dimen.track_duration_height)
            minHeight = box + shadowInsetBottom + ringSpace
            setPadding(ringSpace, ringSpace, ringSpace, shadowInsetBottom)
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Vinilos_Duration)
            includeFontPadding = false
            gravity = Gravity.CENTER
            fillPaint.color = ContextCompat.getColor(context, R.color.vinilos_charcoal)
            strokePaint.color = ContextCompat.getColor(context, R.color.vinilos_ash)
            strokePaint.strokeWidth = strokeWidth
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
            shadowPaint.color = ContextCompat.getColor(context, R.color.vinilos_duration_shadow)
            val blur = resources.getDimension(R.dimen.track_duration_shadow_blur)
            shadowPaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            ringOuterPaint.color = ContextCompat.getColor(context, R.color.vinilos_charcoal)
            ringInnerPaint.color = ContextCompat.getColor(context, R.color.vinilos_cream)
            // El desenfoque no es fiable con aceleración por hardware en API 21.
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            shape.set(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                (width - paddingRight).toFloat(),
                (height - paddingBottom).toFloat(),
            )
            if (isInvalid) {
                drawErrorRing(canvas)
            } else {
                drawShadow(canvas)
            }
            canvas.drawRoundRect(shape, radius, radius, fillPaint)
            val border = RectF(shape)
            border.inset(strokePaint.strokeWidth / 2f, strokePaint.strokeWidth / 2f)
            canvas.drawRoundRect(border, radius, radius, strokePaint)
            super.onDraw(canvas)
        }

        private fun drawShadow(canvas: Canvas) {
            val shadow = RectF(shape)
            shadow.inset(shadowSpread, shadowSpread)
            shadow.offset(0f, shadowOffsetY)
            canvas.drawRoundRect(shadow, radius, radius, shadowPaint)
        }

        /** Anillo de 3 dp crema y 2,5 dp carbón por fuera de la pastilla. El CSS lo da como dos sombras de 0. */
        private fun drawErrorRing(canvas: Canvas) {
            val outer = RectF(shape)
            outer.inset(-ringOuter, -ringOuter)
            canvas.drawRoundRect(outer, radius + ringOuter, radius + ringOuter, ringOuterPaint)
            val inner = RectF(shape)
            inner.inset(-ringInner, -ringInner)
            canvas.drawRoundRect(inner, radius + ringInner, radius + ringInner, ringInnerPaint)
        }
    }
