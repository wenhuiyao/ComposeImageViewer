package dev.wenhui.library

import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Detecting pan and zoom gesture */
internal class TransformGestureNode(
    val onGesture: (
        translationDelta: Offset,
        scaleDelta: Float,
        pivot: Offset,
    ) -> Unit,
    val hasTransformation: () -> Boolean,
    val onGestureReset: () -> Unit,
    private var enabled: Boolean = true,
) : DelegatingNode(), PointerInputModifierNode {

    private val velocityTracker = VelocityTracker()
    private var flingJob: Job? = null

    private val pointerInputNode = delegate(
        SuspendingPointerInputModifierNode {
            if (!enabled) return@SuspendingPointerInputModifierNode
            coroutineScope {
                awaitEachGesture {
                    try {
                        onGestureReset()
                        handleGesture()
                    } catch (exception: CancellationException) {
                        onGestureReset()
                        if (!isActive) {
                            throw exception
                        }
                    }
                }
            }
        },
    )

    private suspend fun AwaitPointerEventScope.handleGesture() {
        var zoom = 1f
        var pan = Offset.Zero
        var canConsumeTouch = false
        val touchSlop = viewConfiguration.touchSlop

        val initialDown = awaitFirstDown(requireUnconsumed = false)
        if (hasTransformation()) {
            // Intercept all touch events after image is in transformed state
            canConsumeTouch = true
            initialDown.consume()
        }
        velocityTracker.resetTracking()
        velocityTracker.addPointerInputChange(initialDown)
        cancelAllAnimations()

        // The logic is based on detectTransformGestures
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!canConsumeTouch) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    // If we can't swipe, don't need to check panMotion distance
                    val panMotion = if (hasTransformation()) pan.getDistance() else 0f

                    // Check if motion pass touch slop
                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        canConsumeTouch = true
                    }
                }

                if (canConsumeTouch) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(panChange, zoomChange, centroid)
                    }
                    event.changes.firstOrNull()?.let {
                        velocityTracker.addPointerInputChange(it)
                    }
                    // Touch up
                    if (event.changes.all { it.changedToUp() }) {
                        fling(velocityTracker.calculateVelocity())
                    }
                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }

    fun update(enabled: Boolean) {
        if (enabled != this.enabled) {
            this.enabled = enabled
            pointerInputNode.resetPointerInputHandler()
        }
    }

    private fun fling(velocity: Velocity) {
        if (!hasTransformation()) {
            return
        }

        val flingDecay = SplineBasedFloatDecayAnimationSpec(requireDensity())
            .generateDecayAnimationSpec<Offset>()
        flingJob = coroutineScope.launch {
            var prevValue = Offset.Zero
            AnimationState(
                typeConverter = Offset.VectorConverter,
                initialValue = prevValue,
                initialVelocity = Offset(velocity.x, velocity.y),
            ).animateDecay(flingDecay) {
                onGesture(
                    /* translationDelta */
                    value - prevValue,
                    /* scaleDelta */
                    1f,
                    /* pivot */
                    Offset.Zero,
                )
                prevValue = value
            }
        }
    }

    private fun cancelAllAnimations() {
        flingJob?.cancel()
        flingJob = null
    }

    override fun onReset() {
        pointerInputNode.resetPointerInputHandler()
    }

    override fun onCancelPointerInput() {
        pointerInputNode.onCancelPointerInput()
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        pointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
    }
}