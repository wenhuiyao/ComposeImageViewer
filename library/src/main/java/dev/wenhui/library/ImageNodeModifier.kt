package dev.wenhui.library

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.modifier.ModifierLocalMap
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


fun Modifier.imageNode() =
    // ImagePositionElement must be before ImageTransformElement
    this then ImagePositionElement then ImageTransformElement

private val ImagePositionNodeLocal =
    modifierLocalOf<ImagePositionNode> { error("Missing ImagePositionNode") }

private object ImagePositionElement : ModifierNodeElement<ImagePositionNode>() {
    override fun create(): ImagePositionNode = ImagePositionNode()
    override fun update(node: ImagePositionNode) {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = "imagePosition".hashCode()
}

/**
 * Must separate [ImagePositionNode] from [ImageTransformNode] so [ImageTransformNode] can observe
 * layout bounds changed.
 */
private class ImagePositionNode :
    Modifier.Node(),
    LayoutModifierNode,
    ModifierLocalModifierNode,
    GlobalPositionAwareModifierNode,
    LayoutAwareModifierNode {

    override val providedValues: ModifierLocalMap =
        modifierLocalMapOf(ImagePositionNodeLocal to this)

    var translation by mutableStateOf(Offset.Zero)
        private set
    var scale by mutableFloatStateOf(1f)
        private set
    var transformOrigin by mutableStateOf(TransformOrigin.Center)
        private set
    var contentBounds: Rect = Rect.Zero
        private set

    fun position(
        scale: Float,
        translation: Offset,
        transformOrigin: TransformOrigin
    ) {
        this.scale = scale
        this.transformOrigin = transformOrigin
        this.translation = translation
    }

    override fun onRemeasured(size: IntSize) {
        // If content has changed, make sure reset our content bounds
        contentBounds = Rect.Zero
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (contentBounds.isEmpty) {
            contentBounds = coordinates.boundsInParent()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = {
                translationX = translation.x
                translationY = translation.y
                transformOrigin = this@ImagePositionNode.transformOrigin
                scaleX = scale
                scaleY = scale
            })
        }
    }

    override fun onReset() {
        translation = Offset.Zero
        scale = 1f
        transformOrigin = TransformOrigin.Center
        contentBounds = Rect.Zero
    }

}

private const val MIN_DOUBLE_TAP_SCALE_FACTOR = 1.8f

private object ImageTransformElement : ModifierNodeElement<ImageTransformNode>() {
    override fun create(): ImageTransformNode = ImageTransformNode()
    override fun update(node: ImageTransformNode) {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = "imageTransform".hashCode()
}

private class ImageTransformNode :
    Modifier.Node(),
    ModifierLocalModifierNode,
    GlobalPositionAwareModifierNode,
    ImageNode {

    private val imagePositionNode: ImagePositionNode
        get() = checkNotNull(ImagePositionNodeLocal.current)

    private val contentBounds: Rect
        get() = imagePositionNode.contentBounds
    private val currentScale: Float
        get() = imagePositionNode.scale

    private var imageTransform = Transformation()

    private var parentSize: Size = Size.Zero
    private lateinit var layoutCoordinates: LayoutCoordinates

    private var animationJob: Job? = null

    override val hasTransformation: Boolean
        get() = currentScale > 1.01f

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        layoutCoordinates = coordinates
        parentSize = coordinates.parentLayoutCoordinates!!.size.toSize()
    }

    override fun onAttach() {
        checkNotNull(ImageNodeProviderLocal.current).providesImageNode(this)
    }

    override fun onReset() {
        imageTransform = Transformation()
        parentSize = Size.Zero
    }

    override fun onGestureUpOrCancelled() {
        if (imagePositionNode.scale < 1f) {
            animateScaleToOrigin()
        }
    }

    override fun transform(
        translationDelta: Offset,
        scaleDelta: Float,
        pivotInWindowsCoords: Offset,
    ) {
        cancelCurrentAnimation()

        transformInternal(
            translationDelta,
            scaleDelta,
            layoutCoordinates.windowToLocal(pivotInWindowsCoords)
        )
    }

    private fun transformInternal(
        translationDelta: Offset,
        scaleDelta: Float,
        localPivot: Offset,
    ) {
        val transformResult = imageTransform.applyTransform(
            contentBounds = contentBounds,
            parentSize = parentSize,
            scaleDelta = scaleDelta,
            translationDelta = translationDelta,
            pivot = localPivot,
        )
        imagePositionNode.position(
            scale = transformResult.scale,
            translation = transformResult.translation,
            transformOrigin = transformResult.transformOrigin
        )
    }

    override fun doubleTapToScale(pivotInWindowsCoords: Offset) {
        animateScale(
            initialScale = currentScale,
            targetScale = if (currentScale > 1.1f) 1f else maxOf(
                parentSize.height / contentBounds.height,
                parentSize.width / contentBounds.width,
                MIN_DOUBLE_TAP_SCALE_FACTOR,
            ),
            pivot = layoutCoordinates.windowToLocal(pivotInWindowsCoords)
        )
    }

    private fun animateScaleToOrigin() {
        animateScale(
            initialScale = currentScale,
            targetScale = 1f,
            pivot = contentBounds.center
        )
    }

    private fun animateScale(initialScale: Float, targetScale: Float, pivot: Offset) {
        cancelCurrentAnimation()
        animationJob = coroutineScope.launch {
            var prevScale = initialScale
            AnimationState(initialValue = initialScale).animateTo(
                targetValue = targetScale,
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
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

    private fun cancelCurrentAnimation() {
        animationJob?.cancel()
        animationJob = null
    }
}

