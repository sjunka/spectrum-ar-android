package com.sjunka.spectrumar.core

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExpectedThreatTest {
    private fun snap(i: Int, team: Team, x: Float, y: Float) = PlayerSnapshot(i, team, Vec2(x, y), Vec2.ZERO)

    @Test fun gridMatchesJsonAsset() {
        val arr = JSONArray(File("src/main/assets/xt_12x8.json").readText())
        assertEquals(ExpectedThreat.ROWS, arr.length())
        for (r in 0 until arr.length()) {
            val row = arr.getJSONArray(r)
            assertEquals(ExpectedThreat.COLS, row.length())
            for (c in 0 until row.length()) assertEquals(row.getDouble(c), ExpectedThreat.grid[r][c], 1e-12)
        }
    }

    @Test fun valueNearAttackedGoalIsLarger() {
        val near = ExpectedThreat.value(Vec2(50f, 0f), 1f)
        val own = ExpectedThreat.value(Vec2(-50f, 0f), 1f)
        assertTrue(near > own * 10)
    }

    @Test fun orientationFlipsWithAttackSign() {
        val p = Vec2(50f, 2f)
        assertEquals(ExpectedThreat.value(p, 1f), ExpectedThreat.value(Vec2(-50f, 2f), -1f), 0.0)
        assertTrue(ExpectedThreat.value(p, Team.HOME) > ExpectedThreat.value(p, Team.AWAY))
    }

    @Test fun valueClampsOutsidePitch() {
        assertEquals(ExpectedThreat.value(Vec2(52f, 0f), 1f), ExpectedThreat.value(Vec2(200f, 0f), 1f), 0.0)
        assertEquals(ExpectedThreat.value(Vec2(0f, -33f), 1f), ExpectedThreat.value(Vec2(0f, -90f), 1f), 0.0)
    }

    @Test fun segmentDistanceBasics() {
        val (d, t) = PassEvaluator.segmentDistance(Vec2(5f, 3f), Vec2.ZERO, Vec2(10f, 0f))
        assertEquals(3f, d, 1e-5f)
        assertEquals(0.5f, t, 1e-5f)
        val (d2, t2) = PassEvaluator.segmentDistance(Vec2(15f, 0f), Vec2.ZERO, Vec2(10f, 0f))
        assertEquals(5f, d2, 1e-5f)
        assertEquals(1.5f, t2, 1e-5f)
        val (d3, t3) = PassEvaluator.segmentDistance(Vec2(3f, 4f), Vec2.ZERO, Vec2.ZERO)
        assertEquals(5f, d3, 1e-5f); assertEquals(0f, t3, 0f)
    }

    @Test fun probabilityDecreasesWithDistance() {
        val a = Vec2.ZERO
        val p10 = PassEvaluator.probability(a, Vec2(10f, 0f), emptyList())
        val p30 = PassEvaluator.probability(a, Vec2(30f, 0f), emptyList())
        val p60 = PassEvaluator.probability(a, Vec2(60f, 0f), emptyList())
        assertTrue(p10 > p30 && p30 > p60)
        assertTrue(p10 <= 0.98 && p60 >= 0.02)
    }

    @Test fun probabilityDropsWithDefenderOnLane() {
        val a = Vec2.ZERO; val b = Vec2(20f, 0f)
        val free = PassEvaluator.probability(a, b, emptyList())
        val blocked = PassEvaluator.probability(a, b, listOf(Vec2(10f, 0.5f)))
        val aside = PassEvaluator.probability(a, b, listOf(Vec2(10f, 15f)))
        assertTrue(blocked < free * 0.5)
        assertEquals(free, aside, 1e-9)
    }

    @Test fun probabilityDropsWithPressureOnReceiver() {
        val a = Vec2.ZERO; val b = Vec2(20f, 0f)
        val free = PassEvaluator.probability(a, b, emptyList())
        val pressed = PassEvaluator.probability(a, b, listOf(Vec2(21f, 1f)))
        assertTrue(pressed < free)
    }

    @Test fun carrierIsNearestWithinRange() {
        val ps = listOf(snap(0, Team.HOME, 0f, 0f), snap(1, Team.AWAY, 3f, 0f))
        assertEquals(1, PassEvaluator.carrier(ps, Vec2(2.5f, 0f)))
        assertEquals(0, PassEvaluator.carrier(ps, Vec2(-1f, 1f)))
    }

    @Test fun carrierNullWhenBallFar() {
        val ps = listOf(snap(0, Team.HOME, 0f, 0f), snap(1, Team.AWAY, 3f, 0f))
        assertNull(PassEvaluator.carrier(ps, Vec2(30f, 20f)))
        assertNull(PassEvaluator.carrier(emptyList(), Vec2.ZERO))
    }

    @Test fun bestPicksClearTeammateNearGoal() {
        val ps = listOf(
            snap(0, Team.HOME, 0f, 0f),
            snap(1, Team.HOME, 40f, 0f),    // near goal, no defenders
            snap(2, Team.HOME, -30f, 0f),   // backwards
            snap(3, Team.HOME, 20f, 5f),    // blocked
            snap(4, Team.AWAY, 10f, 2.5f),
        )
        val best = PassEvaluator.best(0, ps)!!
        assertEquals(1, best.target)
    }

    @Test fun optionsSortedAndConsistent() {
        val ps = listOf(
            snap(0, Team.HOME, 0f, 0f), snap(1, Team.HOME, 40f, 0f), snap(2, Team.HOME, -30f, 0f),
            snap(3, Team.HOME, 20f, 5f), snap(4, Team.AWAY, 10f, 2.5f),
        )
        val opts = PassEvaluator.options(0, ps)
        assertEquals(3, opts.size)
        assertTrue(opts.zipWithNext().all { (a, b) -> a.expectedValue >= b.expectedValue })
        val here = ExpectedThreat.value(Vec2.ZERO, Team.HOME)
        for (o in opts) {
            assertEquals(o.probability * o.targetValue, o.expectedValue, 1e-12)
            assertEquals(o.targetValue - here, o.gain, 1e-12)
        }
        assertTrue(opts.none { it.target == 0 || it.target == 4 })
    }

    @Test fun awayCarrierUsesOwnAttackDirection() {
        val ps = listOf(snap(0, Team.AWAY, 0f, 0f), snap(1, Team.AWAY, -40f, 0f), snap(2, Team.AWAY, 40f, 0f))
        assertEquals(1, PassEvaluator.best(0, ps)!!.target)
    }

    @Test fun unknownCarrierGivesNoOptions() {
        assertTrue(PassEvaluator.options(9, listOf(snap(0, Team.HOME, 0f, 0f))).isEmpty())
        assertNull(PassEvaluator.best(9, emptyList()))
    }
}
