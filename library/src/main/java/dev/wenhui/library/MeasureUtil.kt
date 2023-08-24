package dev.wenhui.library

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Similar to [fillWidthOrHeight(Int, Int)], but use element's [IntrinsicMeasurable.minIntrinsicWidth]
 * and [IntrinsicMeasurable.minIntrinsicHeight] for the measurement
 */
fun Modifier.fillWidthOrHeight() = this.layout { measurable, constraints ->
    measureInternal(
        intrinsicWidth = measurable.minIntrinsicWidth(constraints.maxHeight),
        intrinsicHeight = measurable.minIntrinsicHeight(constraints.maxWidth),
        measurable = measurable,
        constraints = constraints,
    )
}

/**
 * Measure element to fill either width or height depends on the aspect ratio of the element's
 * [intrinsicWidth] and [intrinsicHeight], it's useful with image to fill the max of both width
 * and height.
 */
fun Modifier.fillWidthOrHeight(intrinsicWidth: Int, intrinsicHeight: Int) =
    this.layout { measurable, constraints ->
        measureInternal(
            intrinsicWidth = intrinsicWidth,
            intrinsicHeight = intrinsicHeight,
            measurable = measurable,
            constraints = constraints,
        )
    }

private fun MeasureScope.measureInternal(
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    measurable: Measurable,
    constraints: Constraints,
): MeasureResult {
    val contentConstraints = if (intrinsicWidth == 0 || intrinsicHeight == 0) {
        constraints
    } else {
        val widthRatio = if (constraints.hasBoundedWidth && intrinsicWidth > 0) {
            constraints.maxWidth / intrinsicWidth.toFloat()
        } else {
            1f
        }
        val heightRatio = if (constraints.hasBoundedHeight && intrinsicHeight > 0) {
            constraints.maxHeight / intrinsicHeight.toFloat()
        } else {
            1f
        }
        val scaleRatio = min(widthRatio, heightRatio)
        Constraints.fixed(
            width = (intrinsicWidth * scaleRatio).roundToInt(),
            height = (intrinsicHeight * scaleRatio).roundToInt(),
        )
    }
    // Fill either maxWidth or maxHeight, keep original aspect ratio
    val placeable = measurable.measure(contentConstraints)
    return layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
    }
}
