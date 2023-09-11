package dev.wenhui.library

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize

/** Detecting double tap gesture */
internal class DoubleTapGestureNode(
    private val onDoubleTap: (pivot: Offset) -> Unit,
    private var enabled: Boolean,
) : DelegatingNode(), PointerInputModifierNode {

    private val tapNode = delegate(
        SuspendingPointerInputModifierNode {
            if (!enabled) return@SuspendingPointerInputModifierNode
            detectDoubleTap(
                onDoubleTap = { pivot -> onDoubleTap(pivot) },
            )
        },
    )

    fun update(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            tapNode.resetPointerInputHandler()
        }
    }

    override fun onReset() {
        tapNode.resetPointerInputHandler()
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

private suspend fun PointerInputScope.detectDoubleTap(
    onDoubleTap: ((Offset) -> Unit),
) {
    awaitEachGesture {
        awaitFirstDown()
        val upOrCancel = waitForUpOrCancellation()
        if (upOrCancel != null) {
            // tap was successful. check for second tap
            val secondDown = awaitSecondDown(upOrCancel)
            if (secondDown != null) {
                val secondUp = waitForUpOrCancellation()
                if (secondUp != null) {
                    secondUp.consume()
                    onDoubleTap(secondUp.position)
                }
            }
        }
    }
}

/** This is directly copied from framework */
internal suspend fun AwaitPointerEventScope.awaitSecondDown(
    firstUp: PointerInputChange,
    requireUnconsumed: Boolean = true,
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
    val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
    var change: PointerInputChange
    // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
    do {
        change = awaitFirstDown(requireUnconsumed = requireUnconsumed)
    } while (change.uptimeMillis < minUptime)
    change
}
