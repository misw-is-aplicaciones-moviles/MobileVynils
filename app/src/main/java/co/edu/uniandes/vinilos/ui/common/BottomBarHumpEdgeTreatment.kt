package co.edu.uniandes.vinilos.ui.common

import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapePath
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Top edge of the bottom bar with a smooth rise around the round "+" button: a circular crown of [radius]
 * whose center sits [centerRise] above the edge, joined to the flat edge by two curved shoulders that
 * start [flare] away from the center.
 */
class BottomBarHumpEdgeTreatment(
    private val radius: Float,
    private val centerRise: Float,
    private val flare: Float,
) : EdgeTreatment() {
    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath,
    ) {
        // Negative y is outward (up) for the top edge.
        val crownCenterY = -centerRise
        val join = Math.toRadians(JOIN_ANGLE_DEGREES)
        val joinX = radius * cos(join).toFloat()
        val joinY = crownCenterY - radius * sin(join).toFloat()
        // The shoulder leaves the edge horizontally and meets the crown along its tangent at the join point.
        val shoulderControl = center - joinX + joinY * tan(join).toFloat()

        shapePath.lineTo(center - flare, 0f)
        shapePath.quadToPoint(shoulderControl, 0f, center - joinX, joinY)
        shapePath.addArc(
            center - radius,
            crownCenterY - radius,
            center + radius,
            crownCenterY + radius,
            HALF_TURN_DEGREES + JOIN_ANGLE_DEGREES.toFloat(),
            HALF_TURN_DEGREES - 2 * JOIN_ANGLE_DEGREES.toFloat(),
        )
        shapePath.quadToPoint(2 * center - shoulderControl, 0f, center + flare, 0f)
        shapePath.lineTo(length, 0f)
    }

    private companion object {
        /** Where the crown meets the shoulders, above the horizontal through the crown center. */
        const val JOIN_ANGLE_DEGREES = 20.0
        const val HALF_TURN_DEGREES = 180f
    }
}
