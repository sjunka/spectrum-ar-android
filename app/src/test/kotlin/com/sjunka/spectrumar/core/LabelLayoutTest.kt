package com.sjunka.spectrumar.core

import org.junit.Assert.*
import org.junit.Test

class LabelLayoutTest {
    private fun c(i: Int, x: Float, y: Float, depth: Float, prio: Boolean = false) = LabelCandidate(i, x, y, depth, prio)
    private fun run(vararg cs: LabelCandidate) = LabelLayout.layout(cs.toList(), 400f, 800f)

    @Test fun nearLabelFullyVisible() {
        val r = run(c(1, 100f, 100f, 50f))
        assertEquals(1, r.size)
        assertEquals(1f, r[0].opacity, 1e-6f)
    }

    @Test fun farLabelsDropped() {
        assertTrue(run(c(1, 100f, 100f, 300f)).isEmpty())
    }

    @Test fun midDepthFaded() {
        val r = run(c(1, 100f, 100f, 175f))
        assertEquals(1, r.size)
        assertTrue(r[0].opacity > 0.08f && r[0].opacity < 1f)
    }

    @Test fun opacityMonotonicInDepth() {
        var prev = 2f
        for (d in listOf(100f, 130f, 150f, 170f, 190f, 210f)) {
            val r = run(c(1, 100f, 100f, d))
            val o = if (r.isEmpty()) 0f else r[0].opacity
            assertTrue(o <= prev)
            prev = o
        }
    }

    @Test fun overlappingKeepsNearer() {
        val r = run(c(1, 100f, 100f, 80f), c(2, 105f, 100f, 60f))
        assertEquals(listOf(2), r.map { it.index })
    }

    @Test fun separatedBothKept() {
        assertEquals(2, run(c(1, 100f, 100f, 80f), c(2, 200f, 100f, 60f)).size)
    }

    @Test fun priorityAlwaysShownEvenWhenOverlappingAndFar() {
        val r = run(c(1, 100f, 100f, 400f, true), c(2, 102f, 100f, 400f, true))
        assertEquals(2, r.size)
        assertTrue(r.all { it.priority && it.opacity == 1f })
    }

    @Test fun nonPriorityOverlappingPriorityDropped() {
        val r = run(c(1, 100f, 100f, 10f), c(2, 104f, 100f, 400f, true))
        assertEquals(listOf(2), r.map { it.index })
    }

    @Test fun outOfViewDropped() {
        assertTrue(run(c(1, -5f, 100f, 50f), c(2, 100f, 900f, 50f), c(3, 395f, 100f, 50f), c(4, 100f, 3f, 50f, true)).isEmpty())
    }

    @Test fun scaleWithinBounds() {
        for (d in listOf(0f, 100f, 200f)) {
            val r = run(c(1, 100f, 100f, d))
            assertTrue(r[0].scale in 0.75f..1.05f)
        }
        assertEquals(1.1f, run(c(1, 100f, 100f, 0f, true))[0].scale, 1e-6f)
    }
}
