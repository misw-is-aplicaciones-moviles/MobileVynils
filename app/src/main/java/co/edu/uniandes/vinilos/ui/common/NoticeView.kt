package co.edu.uniandes.vinilos.ui.common

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import co.edu.uniandes.vinilos.R

/**
 * Aviso del kit. Éxito: píldora carbón de 50 dp con sombra. Error: fondo blanco al 50 % y borde carbón.
 * El texto y el momento de mostrarlo los decide la pantalla.
 */
class NoticeView
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
        private val shape = RectF()
        private var success = true
        private var radius = 0f
        private var strokeWidth = 0f
        private var shadowOffsetY = 0f
        private var shadowSpread = 0f
        private var shadowInsetBottom = 0

        init {
            val styled = context.obtainStyledAttributes(attrs, R.styleable.NoticeView, defStyleAttr, 0)
            val kind = styled.getInt(R.styleable.NoticeView_noticeKind, KIND_SUCCESS)
            styled.recycle()
            TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Vinilos_Notice)
            includeFontPadding = false
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
            // El desenfoque de la sombra no es fiable con aceleración por hardware en API 21.
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            applyKind(kind == KIND_SUCCESS)
        }

        /** Cambia el aspecto sin recrear la vista. La pantalla lo usa cuando llega el evento. */
        fun setSuccess(isSuccess: Boolean) {
            if (success == isSuccess) return
            applyKind(isSuccess)
            invalidate()
        }

        override fun draw(canvas: Canvas) {
            drawShape(canvas)
            super.draw(canvas)
        }

        private fun applyKind(isSuccess: Boolean) {
            success = isSuccess
            val resources = resources
            radius =
                resources.getDimension(if (isSuccess) R.dimen.notice_success_radius else R.dimen.notice_error_radius)
            strokeWidth = if (isSuccess) 0f else resources.getDimension(R.dimen.notice_error_stroke)
            shadowOffsetY = resources.getDimension(R.dimen.notice_shadow_offset_y)
            shadowSpread = resources.getDimension(R.dimen.notice_shadow_spread)
            shadowInsetBottom =
                if (isSuccess) resources.getDimensionPixelSize(R.dimen.notice_shadow_inset_bottom) else 0
            val textPaddingH = resources.getDimensionPixelSize(R.dimen.notice_padding_horizontal)
            val textPaddingV = resources.getDimensionPixelSize(R.dimen.notice_padding_vertical)
            val pillHeight = resources.getDimensionPixelSize(R.dimen.notice_success_height)
            // La píldora ocupa el ancho del padre. La sombra cabe en el relleno inferior, no a los lados.
            minHeight = if (isSuccess) pillHeight + shadowInsetBottom else 0
            if (isSuccess) {
                setPadding(textPaddingH, 0, textPaddingH, shadowInsetBottom)
            } else {
                setPadding(textPaddingH, textPaddingV, textPaddingH, textPaddingV)
            }
            gravity = Gravity.CENTER_VERTICAL or if (isSuccess) Gravity.CENTER_HORIZONTAL else Gravity.START
            setTextColor(
                ContextCompat.getColor(context, if (isSuccess) R.color.vinilos_cream else R.color.vinilos_charcoal),
            )
            fillPaint.color =
                ContextCompat.getColor(
                    context,
                    if (isSuccess) R.color.vinilos_charcoal else R.color.vinilos_notice_error_fill,
                )
            strokePaint.color = ContextCompat.getColor(context, R.color.vinilos_charcoal)
            strokePaint.strokeWidth = strokeWidth
            shadowPaint.color = ContextCompat.getColor(context, R.color.vinilos_notice_shadow)
            val blur = resources.getDimension(R.dimen.notice_shadow_blur)
            shadowPaint.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }

        private fun drawShape(canvas: Canvas) {
            shape.set(0f, 0f, width.toFloat(), (height - shadowInsetBottom).toFloat())
            if (success) {
                val shadow = RectF(shape)
                shadow.inset(shadowSpread, shadowSpread)
                shadow.offset(0f, shadowOffsetY)
                canvas.drawRoundRect(shadow, radius, radius, shadowPaint)
            }
            canvas.drawRoundRect(shape, radius, radius, fillPaint)
            if (strokeWidth > 0f) {
                val border = RectF(shape)
                val inset = strokeWidth / 2f
                border.inset(inset, inset)
                canvas.drawRoundRect(border, radius, radius, strokePaint)
            }
        }

        private companion object {
            const val KIND_SUCCESS = 0
        }
    }
