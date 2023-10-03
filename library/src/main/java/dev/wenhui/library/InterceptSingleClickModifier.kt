package dev.wenhui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

/**
 * Intercept single click on parent layout, it's design to work with child layout that handles double
 * click, because it will only trigger on single click, unlike [Modifier.clickable] which will trigger
 * single click when double click if double click listener is not set.
 */
fun Modifier.interceptSingleClick(
    enabled: Boolean = true,
    onClicked: () -> Unit,
) = this.then(InterceptSingleClickElement(enabled, onClicked))

private data class InterceptSingleClickElement(
    private val enabled: Boolean,
    private val onClicked: () -> Unit,
) : ModifierNodeElement<InterceptSingleClickedNode>() {
    override fun create() = InterceptSingleClickedNode(enabled, onClicked)

    override fun update(node: InterceptSingleClickedNode) {
        node.update(enabled, onClicked)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "interceptSingleClick"
    }
}

private class InterceptSingleClickedNode(
    private var enabled: Boolean,
    private var onClicked: () -> Unit,
) : DelegatingNode(), PointerInputModifierNode {
    private val tapNode =
        delegate(
            SuspendingPointerInputModifierNode {
                if (!enabled) return@SuspendingPointerInputModifierNode
                awaitEachGesture {
                    // Check pointer event on initial pass, this will intercept all events
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    val upOrCancel = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (upOrCancel != null) {
                        // tap was successful, wait to see if there is second tap, regardless if
                        // it's consumed or not, if there is second down, don't trigger click
                        val secondDown = awaitSecondDown(upOrCancel, pass = PointerEventPass.Initial)
                        if (secondDown == null) {
                            onClicked()
                        }
                    }
                }
            },
        )

    fun update(
        enabled: Boolean,
        onClicked: () -> Unit,
    ) {
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

/** This is modified the framework awaitSecondDown */
private suspend fun AwaitPointerEventScope.awaitSecondDown(
    firstUp: PointerInputChange,
    pass: PointerEventPass,
): PointerInputChange? =
    withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
        var change: PointerInputChange
        // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
        do {
            change = awaitFirstDown(pass = pass)
        } while (change.uptimeMillis < minUptime)
        change
    }
