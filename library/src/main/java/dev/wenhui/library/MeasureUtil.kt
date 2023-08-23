package dev.wenhui.library

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Measure element to fill either width or height depends on the aspect ratio of the element's
 * intrinsic sizes, it's useful with image to fill the max of both width and height.
 */
fun Modifier.fillWidthOrHeight() = this.layout { measurable, constraints ->
    val intrinsicWidth = measurable.minIntrinsicWidth(constraints.maxHeight)
    val intrinsicHeight = measurable.minIntrinsicHeight(constraints.maxWidth)
    val widthRatio = if (constraints.hasBoundedWidth) {
        constraints.maxWidth / intrinsicWidth.toFloat()
    } else {
        1f
    }
    val heightRatio = if (constraints.hasBoundedHeight) {
        constraints.maxHeight / intrinsicHeight.toFloat()
    } else {
        1f
    }
    val scaleRatio = min(widthRatio, heightRatio)
    val contentConstraints = Constraints.fixed(
        width = (intrinsicWidth * scaleRatio).roundToInt(),
        height = (intrinsicHeight * scaleRatio).roundToInt()
    )
    // Fill either maxWidth or maxHeight, keep original aspect ratio
    val placeable = measurable.measure(contentConstraints)
    layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
    }
}