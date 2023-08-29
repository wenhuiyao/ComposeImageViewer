package dev.wenhui.library

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin

fun Transformation(): Transformation {
    return TransformationImpl()
}

/**
 * Handle transformation logic and returns [TransformedResult] of the final transformation
 */
interface Transformation {
    /**
     * Apply the transform deltas to the existing transform, and get the final transform back
     */
    fun applyTransform(
        contentBounds: Rect,
        parentSize: Size,
        minScale: Float,
        maxScale: Float,
        translationDelta: Offset = Offset.Zero,
        scaleDelta: Float = 1f,
        pivot: Offset = Offset.Zero,
    ): TransformedResult

    interface TransformedResult {
        val scale: Float
        val translation: Offset
        val transformOrigin: TransformOrigin
    }
}

private val transformOriginZero = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0f)

/**
 * Unfortunately, we can't use [androidx.compose.ui.graphics.Matrix], it doesn't support scale with
 * pivot point. Instead, fallback to use Android [Matrix]
 */
private class TransformationImpl :
    Transformation {

    private val matrix = Matrix()

    /* *Holders are the temporary objects for computation */
    private val contentBoundsHolder = RectF()
    private val pointHolder = FloatArray(2)
    private val matrixValuesHolder = FloatArray(9)
    private val transformedBoundsHolder = RectF()
    private val transformedResultHolder = MutableTransformedResult()

    override fun applyTransform(
        contentBounds: Rect,
        parentSize: Size,
        minScale: Float,
        maxScale: Float,
        translationDelta: Offset,
        scaleDelta: Float,
        pivot: Offset,
    ): Transformation.TransformedResult {
        if (scaleDelta != 1f) {
            val allowScaleDelta = getSafeScaleDelta(
                minScale = minScale,
                maxScale = maxScale,
                scaleDelta = scaleDelta,
            )
            // The pivot is based on the original content bounds, we need to transform it
            // to latest coordinates
            val transformedPivot = pivot.applyTransform()
            matrix.postScale(
                allowScaleDelta,
                allowScaleDelta,
                transformedPivot.x,
                transformedPivot.y,
            )
        }
        matrix.postTranslate(translationDelta.x, translationDelta.y)

        ensureContentInBounds(contentBounds, parentSize)

        matrix.getValues(matrixValuesHolder)
        return transformedResultHolder.update(
            scale = matrixValuesHolder[Matrix.MSCALE_X],
            translation = Offset(
                matrixValuesHolder[Matrix.MTRANS_X],
                matrixValuesHolder[Matrix.MTRANS_Y],
            ),
            // Android matrix scale is using [0,0] as its default pivot point
            transformOrigin = transformOriginZero,
        )
    }

    /**
     * Make sure we don't scale outside of allowed [minScale] and [maxScale] range
     */
    private fun getSafeScaleDelta(minScale: Float, maxScale: Float, scaleDelta: Float): Float {
        matrix.getValues(matrixValuesHolder)
        val toBeScale =
            (matrixValuesHolder[Matrix.MSCALE_X] * scaleDelta).coerceIn(minScale, maxScale)
        return toBeScale / matrixValuesHolder[Matrix.MSCALE_X]
    }

    /** Apply current transform to this offset */
    private fun Offset.applyTransform(): Offset {
        pointHolder[0] = x
        pointHolder[1] = y
        matrix.mapPoints(pointHolder)
        return Offset(pointHolder[0], pointHolder[1])
    }

    /**
     * Check after transform content, content is outside of its valid bounds.
     *
     * Valid bounds is defined as following:
     * 1). If the size of an axis is less than parent's size, don't translate that axis
     * 2). If the size of an axis is larger than parent's size, don't allow content move inside
     *  parent viewport, i.e. left/top can't > parent's left/top
     */
    private fun ensureContentInBounds(contentBounds: Rect, parentSize: Size) {
        val contentBoundsF = contentBounds.toAndroidRectF()
        matrix.getValues(matrixValuesHolder)
        val scale = matrixValuesHolder[Matrix.MSCALE_X]
        val translationX = matrixValuesHolder[Matrix.MTRANS_X]
        val translationY = matrixValuesHolder[Matrix.MTRANS_Y]

        // Using Matrix.mapRect() doesn't work properly, we will map rect manually by pivot point of [0,0]
        transformedBoundsHolder.set(contentBoundsF)
        transformedBoundsHolder.transformByOriginZero(scale, translationX, translationY)

        var offsetX = 0f
        var offsetY = 0f
        if (transformedBoundsHolder.width() < parentSize.width) {
            // If scaled width hasn't reached parent size yet, keep x centered
            offsetX = contentBoundsF.centerX() - transformedBoundsHolder.centerX()
        } else {
            // Otherwise keep x axis' edges to the parent's edges
            if (transformedBoundsHolder.left > 0f) {
                offsetX = -transformedBoundsHolder.left
            } else if (transformedBoundsHolder.right < parentSize.width) {
                offsetX = parentSize.width - transformedBoundsHolder.right
            }
        }

        if (transformedBoundsHolder.height() < parentSize.height) {
            // If scaled height hasn't reached parent size yet, keep y centered
            offsetY = contentBoundsF.centerY() - transformedBoundsHolder.centerY()
        } else {
            // Otherwise keep y axis' edges to the parent's edges
            if (transformedBoundsHolder.top > 0f) {
                offsetY = -transformedBoundsHolder.top
            } else if (transformedBoundsHolder.bottom < parentSize.height) {
                offsetY = parentSize.height - transformedBoundsHolder.bottom
            }
        }

        if (offsetX != 0f || offsetY != 0f) {
            matrix.postTranslate(offsetX, offsetY)
        }
    }

    private fun Rect.toAndroidRectF(): RectF {
        contentBoundsHolder.set(left, top, right, bottom)
        return contentBoundsHolder
    }

    private fun RectF.transformByOriginZero(
        scale: Float,
        translationX: Float,
        translationY: Float,
    ) {
        offset(translationX, translationY)
        right = left + width() * scale
        bottom = top + height() * scale
    }

    private class MutableTransformedResult(
        override var scale: Float = 1f,
        override var translation: Offset = Offset.Zero,
        override var transformOrigin: TransformOrigin = TransformOrigin.Center,
    ) : Transformation.TransformedResult {
        fun update(
            scale: Float,
            translation: Offset,
            transformOrigin: TransformOrigin,
        ) = apply {
            this.scale = scale
            this.translation = translation
            this.transformOrigin = transformOrigin
        }
    }
}
