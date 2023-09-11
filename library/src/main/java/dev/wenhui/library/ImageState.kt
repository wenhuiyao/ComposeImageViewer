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

/** Use [transformBlock] to control the current transform state */
@Composable
fun rememberImageState(
    minScale: Float = 0.8f,
    maxScale: Float = 5f,
    transformBlock: (TransformRequest.() -> Unit)? = null,
): ImageState {
    return remember { ImageState(minScale, maxScale, transformBlock) }
}

/** The state of current image transformation */
@Stable
class ImageState(
    val minScale: Float,
    val maxScale: Float,
    internal val transformBlock: (TransformRequest.() -> Unit)?,
) {
    private var _translation: Offset by mutableStateOf(Offset.Zero)
    val translation: Offset get() = _translation

    private var _scale: Float by mutableFloatStateOf(1f)
    val scale: Float get() = _scale

    private var _transformOrigin: TransformOrigin by mutableStateOf(TransformOrigin.Center)
    val transformOrigin: TransformOrigin
        get() = _transformOrigin

    internal var contentBounds = Rect.Zero

    /**
     * This is meant to be used internally, do NOT make it public. To update image's state,
     * use [transformBlock]
     */
    internal fun updatePosition(
        translation: Offset,
        scale: Float,
        transformOrigin: TransformOrigin,
    ) {
        _translation = translation
        _scale = scale
        _transformOrigin = transformOrigin
    }
}

class Transform(
    val translation: Offset,
    val scale: Float,
    val shouldAnimate: Boolean,
    val transformOrigin: TransformOrigin = TransformOrigin.Center,
)

interface TransformRequest {
    var transform: Transform?
}

internal class TransformRequestImpl(override var transform: Transform? = null) : TransformRequest
