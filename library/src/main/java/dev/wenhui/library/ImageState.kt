package dev.wenhui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin


@Composable
fun rememberImageState(minScale: Float = 0.8f, maxScale: Float = 4f): ImageState {
    return remember { ImageState(minScale, maxScale) }
}

/** The state of current image transformation */
@Stable
class ImageState(val minScale: Float, val maxScale: Float) {
    private var _translation: Offset by mutableStateOf(Offset.Zero)
    val translation: Offset get() = _translation

    private var _scale: Float by mutableFloatStateOf(1f)
    val scale: Float get() = _scale

    private var _transformOrigin: TransformOrigin by mutableStateOf(TransformOrigin.Center)
    val transformOrigin: TransformOrigin
        get() = _transformOrigin

    internal fun updatePosition(
        translation: Offset,
        scale: Float,
        transformOrigin: TransformOrigin
    ) {
        _translation = translation
        _scale = scale
        _transformOrigin = transformOrigin
    }
}