package com.sjunka.spectrumar.core

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class LabelCandidate(val index: Int, val x: Float, val y: Float, val depth: Float, val priority: Boolean)

data class VisibleLabel(
    val index: Int,
    val x: Float,
    val y: Float,
    val opacity: Float,
    val scale: Float,
    val priority: Boolean,
)

/**
 * Decides which jersey numbers are drawn: priority players (selected, ball carrier, dragged)
 * always show; the rest fade out with camera distance and are dropped when they would overlap
 * a label that is nearer to the camera (or any priority label).
 */
object LabelLayout {
    private fun smoothstep(x: Float): Float {
        val t = min(max(x, 0f), 1f)
        return t * t * (3f - 2f * t)
    }

    fun layout(
        candidates: List<LabelCandidate>,
        viewWidth: Float,
        viewHeight: Float,
        minSeparation: Float = 22f,
        nearDepth: Float = 120f,
        farDepth: Float = 230f,
    ): List<VisibleLabel> {
        val out = ArrayList<VisibleLabel>()
        fun fits(x: Float, y: Float) = out.all { hypot(it.x - x, it.y - y) >= minSeparation }
        fun inView(x: Float, y: Float) = x > 8 && y > 8 && x < viewWidth - 8 && y < viewHeight - 8

        for (c in candidates) {
            if (c.priority && inView(c.x, c.y)) out += VisibleLabel(c.index, c.x, c.y, 1f, 1.1f, true)
        }
        for (c in candidates.filter { !it.priority }.sortedBy { it.depth }) {
            if (!inView(c.x, c.y)) continue
            val opacity = 1f - smoothstep((c.depth - nearDepth) / (farDepth - nearDepth))
            if (opacity <= 0.08f || !fits(c.x, c.y)) continue
            val scale = min(max(1.15f - c.depth / 300f, 0.75f), 1.05f)
            out += VisibleLabel(c.index, c.x, c.y, opacity, scale, false)
        }
        return out
    }
}
