package dev.wenhui.library

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The main modifier for enable image content transformation
 */
internal fun Modifier.transformableNodes(imageState: ImageState) =
    // ImagePositionElement must be before ImageTransformElement, see ImagePositionNode docs for more details
    this then ImagePositionElement(imageState) then ImageTransformElement(imageState)

private data class ImagePositionElement(private val imageState: ImageState) :
    ModifierNodeElement<ImagePositionNode>() {
    override fun create(): ImagePositionNode = ImagePositionNode(imageState)

    override fun update(node: ImagePositionNode) {
        node.imageState = imageState
    }
}

/**
 * Must separate [ImagePositionNode] from [ImageTransformNode] so [ImageTransformNode] can observe
 * layout bounds changed. The same node won't get latest bounds update from its own or left modifier
 * layout measure
 */
private class ImagePositionNode(var imageState: ImageState) :
    Modifier.Node(),
    LayoutModifierNode,
    GlobalPositionAwareModifierNode,
    LayoutAwareModifierNode {
    override fun onRemeasured(size: IntSize) {
        // If content has changed, make sure reset our content bounds
        imageState.contentBounds = Rect.Zero
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (imageState.contentBounds.isEmpty) {
            imageState.contentBounds = coordinates.boundsInParent()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = {
                translationX = imageState.translation.x
                translationY = imageState.translation.y
                transformOrigin = imageState.transformOrigin
                scaleX = imageState.scale
                scaleY = imageState.scale
            })
        }
    }
}

private const val MIN_DOUBLE_TAP_SCALE_FACTOR = 2f

private data class ImageTransformElement(private val imageState: ImageState) :
    ModifierNodeElement<ImageTransformNode>() {
    override fun create(): ImageTransformNode = ImageTransformNode(imageState)

    override fun update(node: ImageTransformNode) {
        node.update(imageState)
    }
}

private class ImageTransformNode(private var imageState: ImageState) :
    Modifier.Node(),
    ModifierLocalModifierNode,
    GlobalPositionAwareModifierNode,
    ObserverModifierNode,
    ImageNode {
    private val contentBounds: Rect
        get() = imageState.contentBounds
    private val currentScale: Float
        get() = imageState.scale

    private var transformation = Transformation()

    private var parentSize: Size = Size.Zero
    private lateinit var layoutCoordinates: LayoutCoordinates

    private val animationJobs = mutableListOf<Job>()

    override val hasTransformation: Boolean
        get() = currentScale > 1.01f

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        layoutCoordinates = coordinates
        parentSize = coordinates.parentLayoutCoordinates!!.size.toSize()
    }

    fun update(imageState: ImageState) {
        if (imageState != this.imageState) {
            this.imageState = imageState
            transformation = Transformation()
            transformInternal(
                translationDelta = imageState.translation,
                scaleDelta = imageState.scale,
                localPivot = contentBounds.center,
            )
            observeTransformRequest()
        }
    }

    override fun onAttach() {
        checkNotNull(ImageNodeProviderLocal.current).providesImageNode(this)
        observeTransformRequest()
    }

    override fun onReset() {
        transformation = Transformation()
        parentSize = Size.Zero
    }

    override fun onGestureUpOrCancelled() {
        if (currentScale < 1f) {
            animateScaleToOrigin()
        }
    }

    override fun transform(
        translationDelta: Offset,
        scaleDelta: Float,
        pivotInWindowsCoords: Offset,
    ) {
        cancelAllAnimations()

        transformInternal(
            translationDelta,
            scaleDelta,
            layoutCoordinates.windowToLocal(pivotInWindowsCoords),
        )
    }

    private fun transformInternal(
        translationDelta: Offset,
        scaleDelta: Float,
        localPivot: Offset,
    ) {
        val transformResult =
            transformation.applyTransform(
                contentBounds = contentBounds,
                parentSize = parentSize,
                minScale = imageState.minScale,
                maxScale = imageState.maxScale,
                scaleDelta = scaleDelta,
                translationDelta = translationDelta,
                pivot = localPivot,
            )
        // This will trigger imageState snapshot state update, which will then trigger
        // imagePositionNode graphicsLayer update
        imageState.onTransformUpdated(
            scale = transformResult.scale,
            translation = transformResult.translation,
            transformOrigin = transformResult.transformOrigin,
        )
    }

    override fun doubleTapToScale(pivotInWindowsCoords: Offset) {
        cancelAllAnimations()
        animationJobs +=
            animateScale(
                initialScale = currentScale,
                targetScale =
                    if (currentScale > 1.1f) {
                        1f
                    } else {
                        maxOf(
                            parentSize.height / contentBounds.height,
                            parentSize.width / contentBounds.width,
                            MIN_DOUBLE_TAP_SCALE_FACTOR,
                        )
                    },
                pivot = layoutCoordinates.windowToLocal(pivotInWindowsCoords),
            )
    }

    private fun animateScaleToOrigin() {
        cancelAllAnimations()
        animationJobs +=
            animateScale(
                initialScale = currentScale,
                targetScale = 1f,
                pivot = contentBounds.center,
            )
    }

    private fun animateScale(
        initialScale: Float,
        targetScale: Float,
        pivot: Offset,
    ): Job {
        return coroutineScope.launch {
            var prevScale = initialScale
            AnimationState(initialValue = initialScale).animateTo(
                targetValue = targetScale,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            ) {
                val scaleDelta = value / prevScale
                prevScale = value
                transformInternal(
                    translationDelta = Offset.Zero,
                    scaleDelta = scaleDelta,
                    localPivot = pivot,
                )
            }
        }
    }

    private fun cancelAllAnimations() {
        animationJobs.forEach { it.cancel() }
        animationJobs.clear()
    }

    override fun onObservedReadsChanged() {
        observeTransformRequest()
        imageState.transformRequest?.let { transform ->
            if (transform.shouldAnimate) {
                animateTransform(transform)
            } else {
                transformInternal(
                    translationDelta = transform.translation - imageState.translation,
                    scaleDelta = transform.scale / imageState.scale,
                    localPivot = transform.getLocalPivot(),
                )
            }
        }
    }

    private fun Transform.getLocalPivot(): Offset {
        return Offset(
            x = contentBounds.width * transformOrigin.pivotFractionX,
            y = contentBounds.height * transformOrigin.pivotFractionY,
        )
    }

    private fun animateTransform(transform: Transform) {
        cancelAllAnimations()
        val localPivot = transform.getLocalPivot()
        if (transform.translation != imageState.translation) {
            animationJobs +=
                coroutineScope.launch {
                    var prevValue = imageState.translation
                    AnimationState(
                        typeConverter = Offset.VectorConverter,
                        initialValue = imageState.translation,
                        initialVelocity = Offset.Zero,
                    ).animateTo(
                        targetValue = transform.translation,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                    ) {
                        transformInternal(
                            translationDelta = value - prevValue,
                            scaleDelta = 1f,
                            localPivot = localPivot,
                        )
                        prevValue = value
                    }
                }
        }

        if (transform.scale != imageState.scale) {
            animationJobs +=
                animateScale(
                    initialScale = imageState.scale,
                    targetScale = transform.scale,
                    pivot = localPivot,
                )
        }
    }

    private fun observeTransformRequest() {
        observeReads { imageState.transformRequest }
    }
}
