package dev.wenhui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.modifier.ModifierLocalMap
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

/**
 *  A layout composable with [content]. The [ImageViewer] will enable pan/zoom, double tap to zoom
 *  [content]. It can only take one child and the child layout must specify [ImageViewerScope.transformable]
 *  to enable support of gesture transform, otherwise, exception will be thrown.
 */
@Composable
fun ImageViewer(
    modifier: Modifier = Modifier,
    enableGesture: Boolean = true,
    content: @Composable ImageViewerScope.() -> Unit,
) {
    Layout(
        modifier = modifier.clipToBounds() then ImageViewerElement(enableGesture),
        measurePolicy = ImageViewerMeasurePolicy,
        content = { ImageViewerScopeImpl.content() },
    )
}

private object ImageViewerMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        check(measurables.size == 1) {
            "ImageViewer can only work with single child"
        }
        val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeable = measurables.first().measure(contentConstraints)
        val layoutWidth = max(placeable.width, constraints.minWidth)
        val layoutHeight = max(placeable.height, constraints.minHeight)
        return layout(layoutWidth, layoutHeight) {
            // Always position child at the center
            val position =
                Alignment.Center.align(
                    IntSize(placeable.width, placeable.height),
                    IntSize(layoutWidth, layoutHeight),
                    layoutDirection,
                )
            placeable.place(position)
        }
    }
}

interface ImageViewerScope {
    /** Child content must call this to enable image transformation */
    fun Modifier.transformable(imageState: ImageState): Modifier
}

private object ImageViewerScopeImpl : ImageViewerScope {
    override fun Modifier.transformable(imageState: ImageState): Modifier = this then transformableNodes(imageState)
}

internal interface ImageNodeProvider {
    fun providesImageNode(imageNode: ImageNode)
}

internal interface ImageNode {
    /** Return true if image content has transformed */
    val hasTransformation: Boolean

    fun transform(
        translationDelta: Offset = Offset.Zero,
        scaleDelta: Float = 1f,
        // this is base on window's coordinates
        pivotInWindowsCoords: Offset = Offset.Unspecified,
    )

    fun doubleTapToScale(pivotInWindowsCoords: Offset)

    fun onGestureUpOrCancelled()
}

internal val ImageNodeProviderLocal =
    modifierLocalOf<ImageNodeProvider> {
        error("Missing ImageNodeProvider")
    }

private data class ImageViewerElement(private val enableGesture: Boolean) :
    ModifierNodeElement<ImageViewerNode>() {
    override fun create() = ImageViewerNode(enableGesture)

    override fun update(node: ImageViewerNode) {
        node.update(enableGesture)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "imageViewer"
        value = enableGesture
    }
}

private class ImageViewerNode(enableGesture: Boolean) :
    DelegatingNode(),
    ModifierLocalModifierNode,
    ImageNodeProvider,
    PointerInputModifierNode,
    GlobalPositionAwareModifierNode {
    // Pass a handle to the ImageNode provider child layout tree where child node
    // will provide the actual imageNode
    override val providedValues: ModifierLocalMap =
        modifierLocalMapOf(ImageNodeProviderLocal to this)

    private var _imageNode: ImageNode? = null
    private val imageNode: ImageNode
        get() =
            checkNotNull(_imageNode) {
                "Do you forget to call Modifier.imageContentNode() in your child layout"
            }
    private lateinit var layoutCoords: LayoutCoordinates

    private val transformGestureNode =
        delegate(
            TransformGestureNode(
                onGesture = { translationDelta, scaleDelta, pivot ->
                    imageNode.transform(
                        translationDelta = translationDelta,
                        scaleDelta = scaleDelta,
                        pivotInWindowsCoords = layoutCoords.localToWindow(pivot),
                    )
                },
                hasTransformation = { imageNode.hasTransformation },
                onGestureReset = { imageNode.onGestureUpOrCancelled() },
                enabled = enableGesture,
            ),
        )

    private val doubleTapGestureNode =
        delegate(
            DoubleTapGestureNode(
                onDoubleTap = { pivot ->
                    imageNode.doubleTapToScale(layoutCoords.localToWindow(pivot))
                },
                enabled = enableGesture,
            ),
        )

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onAttach() {
        sideEffect {
            // this is to make sure Modifier.transformable() is called on child layout
            imageNode
        }
    }

    fun update(enableGesture: Boolean) {
        transformGestureNode.update(enableGesture)
        doubleTapGestureNode.update(enableGesture)
    }

    override fun providesImageNode(imageNode: ImageNode) {
        _imageNode = imageNode
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.layoutCoords = coordinates
    }

    override fun onCancelPointerInput() {
        doubleTapGestureNode.onCancelPointerInput()
        transformGestureNode.onCancelPointerInput()
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        doubleTapGestureNode.onPointerEvent(pointerEvent, pass, bounds)
        transformGestureNode.onPointerEvent(pointerEvent, pass, bounds)
    }
}
