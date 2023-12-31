package dev.wenhui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Use [transformBlock] to control the current transform state, i.e. manually zoom image back to its
 * origin,
 *
 * ```
 * var transform by remember {
 *   mutableStateOf<Transform?>(null)
 * }
 * rememberImageState{ this.transform = transform }
 *
 * BackHandler {
 *    transform = Transform(
 *       translation = Offset.Zero,
 *       transformOrigin = TransformOrigin.Center,
 *       scale = 1f,
 *       shouldAnimate = true,
 *   )
 * }
 * ```
 *
 * will transform image back to its original position on back pressed.
 */
@Composable
fun rememberImageState(
    minScale: Float = 0.8f,
    maxScale: Float = 5f,
): ImageState {
    return remember { ImageState(minScale, maxScale) }
}

/** The state of current image transformation */
@Stable
class ImageState(
    val minScale: Float,
    val maxScale: Float,
) {
    private var _translation: Offset by mutableStateOf(Offset.Zero)
    val translation: Offset get() = _translation

    private var _scale: Float by mutableFloatStateOf(1f)
    val scale: Float get() = _scale

    private var _transformOrigin: TransformOrigin by mutableStateOf(TransformOrigin.Center)
    val transformOrigin: TransformOrigin
        get() = _transformOrigin

    internal var contentBounds = Rect.Zero

    internal var transformRequest by mutableStateOf<Transform?>(null)

    fun updateTransform(
        translation: Offset,
        scale: Float,
        transformOrigin: TransformOrigin = TransformOrigin.Center,
        shouldAnimate: Boolean = true,
    ) {
        this.transformRequest =
            Transform(
                translation = translation,
                scale = scale,
                transformOrigin = transformOrigin,
                shouldAnimate = shouldAnimate,
            )
    }

    internal fun onTransformUpdated(
        translation: Offset,
        scale: Float,
        transformOrigin: TransformOrigin,
    ) {
        _translation = translation
        _scale = scale
        _transformOrigin = transformOrigin
    }
}

internal class Transform(
    val translation: Offset,
    val scale: Float,
    val shouldAnimate: Boolean,
    val transformOrigin: TransformOrigin,
)
