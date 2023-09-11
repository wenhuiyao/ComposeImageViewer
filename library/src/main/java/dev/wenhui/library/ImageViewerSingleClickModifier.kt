package dev.wenhui.library

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

/**
 * Handle single click on parent layout when using with [ImageViewer], this single click will check
 * for double click, if second down occurs, it won't trigger the single click
 */
fun Modifier.singleClickOnly(
    enabled: Boolean = true,
    onClicked: () -> Unit,
) = this.then(ForceSingleClickElement(enabled, onClicked))

private data class ForceSingleClickElement(
    private val enabled: Boolean,
    private val onClicked: () -> Unit,
) : ModifierNodeElement<ForceSingleClickedNode>() {
    override fun create() = ForceSingleClickedNode(enabled, onClicked)

    override fun update(node: ForceSingleClickedNode) {
        node.update(enabled, onClicked)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "singleClickOnly"
    }
}

private class ForceSingleClickedNode(
    private var enabled: Boolean,
    private var onClicked: () -> Unit,
) : DelegatingNode(), PointerInputModifierNode {

    private val tapNode = delegate(
        SuspendingPointerInputModifierNode {
            if (!enabled) return@SuspendingPointerInputModifierNode
            awaitEachGesture {
                awaitFirstDown()
                val upOrCancel = waitForUpOrCancellation()
                if (upOrCancel != null) {
                    // tap was successful, wait to see if there is second tap, regardless if
                    // it's consumed or not, if there is second down, don't trigger click
                    val secondDown = awaitSecondDown(upOrCancel, requireUnconsumed = false)
                    if (secondDown == null) {
                        onClicked()
                    }
                }
            }
        },
    )

    fun update(enabled: Boolean, onClicked: () -> Unit) {
        this.onClicked = onClicked
        if (this.enabled != enabled) {
            this.enabled = enabled
            tapNode.resetPointerInputHandler()
        }
    }

    override fun onCancelPointerInput() {
        tapNode.onCancelPointerInput()
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        tapNode.onPointerEvent(pointerEvent, pass, bounds)
    }
}
