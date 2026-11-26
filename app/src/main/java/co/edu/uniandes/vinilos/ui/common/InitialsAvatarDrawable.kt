package co.edu.uniandes.vinilos.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import co.edu.uniandes.vinilos.R

/**
 * Vinyl avatar with a collector's initials, the same one the comment cards draw with `bg_album_detail_avatar`.
 * A Chip only takes a Drawable as its icon, so the "¿Quién comenta?" chips (HU09) draw it here. It is decorative:
 * the chip text already says the name.
 */
internal class InitialsAvatarDrawable(
    context: Context,
    private val initials: String,
) : Drawable() {
    private val disc = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.bg_album_detail_avatar)).mutate()
    private val textBounds = Rect()
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.vinilos_charcoal)
            textAlign = Paint.Align.CENTER
            textSize = context.resources.getDimension(R.dimen.comment_chip_initials)
            typeface = ResourcesCompat.getFont(context, R.font.montserrat_extrabold) ?: Typeface.DEFAULT_BOLD
        }

    override fun onBoundsChange(bounds: Rect) {
        disc.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        disc.draw(canvas)
        paint.getTextBounds(initials, 0, initials.length, textBounds)
        val box = bounds
        canvas.drawText(initials, box.exactCenterX(), box.exactCenterY() - textBounds.exactCenterY(), paint)
    }

    override fun setAlpha(alpha: Int) {
        disc.alpha = alpha
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        disc.colorFilter = colorFilter
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
