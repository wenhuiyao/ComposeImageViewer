package dev.wenhui.library

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
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
            detectTapGestures(
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
