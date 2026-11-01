package com.sjunka.spectrumar.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration

class SceneGestureCallbacks(
    /** Touch landed: return true when it starts a drag of an object (what-if). */
    val onDown: (Offset) -> Boolean,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onTap: (Offset) -> Unit,
    val onOrbit: (Offset) -> Unit,
    /** Two fingers: pan in px, zoom factor, rotation in degrees (clockwise positive). */
    val onTransform: (pan: Offset, zoom: Float, rotationDeg: Float) -> Unit,
)

/** One gesture recogniser for both modes: tap, one-finger orbit or object drag, two-finger pan/pinch/twist. */
@Composable
fun GestureLayer(callbacks: SceneGestureCallbacks, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val cb = rememberUpdatedState(callbacks)
    val slop = LocalViewConfiguration.current.touchSlop
    Box(
        modifier.fillMaxSize().pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val dragging = cb.value.onDown(down.position)
                var moved = false
                var multi = false
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Main)
                    val pressed = ev.changes.count { it.pressed }
                    if (pressed >= 2) {
                        multi = true
                        val zoom = ev.calculateZoom(); val pan = ev.calculatePan(); val rot = ev.calculateRotation()
                        cb.value.onTransform(pan, zoom, rot)
                        ev.changes.forEach { it.consume() }
                    } else if (pressed == 1 && !multi) {
                        val ch = ev.changes.first { it.pressed }
                        if (!moved && (ch.position - down.position).getDistance() > slop) moved = true
                        if (moved) {
                            if (dragging) cb.value.onDrag(ch.position) else cb.value.onOrbit(ch.positionChange())
                            ch.consume()
                        }
                    }
                } while (ev.changes.any { it.pressed })
                if (dragging) cb.value.onDragEnd()
                if (!moved && !multi) cb.value.onTap(down.position)
            }
        },
    )
}
