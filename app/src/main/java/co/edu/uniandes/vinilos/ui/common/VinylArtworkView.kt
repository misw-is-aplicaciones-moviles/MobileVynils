package co.edu.uniandes.vinilos.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View.MeasureSpec
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ViewVinylArtworkBinding

/**
 * Disco con carátula. Con el ancho fijo, el alto sigue la proporción 33:20.
 * Si el ancho no viene fijo, el marco es el mayor 33:20 que cabe en los dos topes.
 * La carátula cambia con [setCoverResource] o con `app:coverSrc`. El disco no cambia.
 */
class VinylArtworkView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : ConstraintLayout(context, attrs, defStyleAttr) {
        private var bindingOrNull: ViewVinylArtworkBinding? = null
        private val binding get() = checkNotNull(bindingOrNull)

        /** Imagen de la carátula. El listado carga aquí la portada del álbum. */
        val coverView: ImageView
            get() = binding.artworkCoverImage

        init {
            bindingOrNull = ViewVinylArtworkBinding.inflate(LayoutInflater.from(context), this)
            applyAppearance(attrs, defStyleAttr)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            applyCoverDescription(contentDescription)
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            // El selector vertical fija el ancho y no entra aquí: el alto lo resuelve la proporción.
            val maxWidth = boundedSize(widthMeasureSpec)
            val maxHeight = boundedSize(heightMeasureSpec)
            val widthIsFixed = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
            if (widthIsFixed || (maxWidth == null && maxHeight == null)) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val (width, height) = fitSize(maxWidth, maxHeight)
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
        }

        /** Sustituye la carátula. [resId] puede ser la foto por defecto o una portada local. */
        fun setCoverResource(
            @DrawableRes resId: Int,
        ) {
            coverView.setImageDrawable(AppCompatResources.getDrawable(context, resId))
        }

        override fun setContentDescription(description: CharSequence?) {
            super.setContentDescription(description)
            // El constructor de View llama esto antes de inflar los hijos.
            if (bindingOrNull != null) {
                applyCoverDescription(description)
            }
        }

        private fun applyAppearance(
            attrs: AttributeSet?,
            defStyleAttr: Int,
        ) {
            val styled =
                context.obtainStyledAttributes(attrs, R.styleable.VinylArtworkView, defStyleAttr, 0)
            try {
                setCoverResource(
                    styled.getResourceId(
                        R.styleable.VinylArtworkView_coverSrc,
                        R.drawable.img_album_cover,
                    ),
                )
                binding.artworkCover.radius =
                    styled.getDimension(
                        R.styleable.VinylArtworkView_artworkCornerRadius,
                        resources.getDimension(R.dimen.role_cover_radius),
                    )
                binding.artworkCover.cardElevation =
                    styled.getDimension(
                        R.styleable.VinylArtworkView_artworkElevation,
                        resources.getDimension(R.dimen.role_cover_elevation),
                    )
            } finally {
                styled.recycle()
            }
        }

        private fun applyCoverDescription(description: CharSequence?) {
            binding.artworkCoverImage.contentDescription = description
            binding.artworkCoverImage.importantForAccessibility =
                if (description.isNullOrEmpty()) {
                    IMPORTANT_FOR_ACCESSIBILITY_NO
                } else {
                    IMPORTANT_FOR_ACCESSIBILITY_YES
                }
        }

        internal companion object {
            /** Ancho del marco respecto a su alto: el disco asoma 13/33 y la carátula ocupa el alto. */
            const val RATIO_WIDTH = 33
            const val RATIO_HEIGHT = 20

            /** Mayor marco 33:20 que cabe en los topes. Un tope nulo no limita ese lado. */
            fun fitSize(
                maxWidth: Int?,
                maxHeight: Int?,
            ): Pair<Int, Int> {
                var width = maxWidth ?: (((maxHeight ?: 0).toLong() * RATIO_WIDTH) / RATIO_HEIGHT).toInt()
                var height = ((width.toLong() * RATIO_HEIGHT) / RATIO_WIDTH).toInt()
                if (maxHeight != null && height > maxHeight) {
                    height = maxHeight
                    width = ((height.toLong() * RATIO_WIDTH) / RATIO_HEIGHT).toInt()
                }
                return width to height
            }

            private fun boundedSize(spec: Int): Int? =
                when (MeasureSpec.getMode(spec)) {
                    MeasureSpec.UNSPECIFIED -> null
                    else -> MeasureSpec.getSize(spec)
                }
        }
    }
