package com.sjunka.spectrumar.core

import org.junit.Assert.*
import org.junit.Test

class PitchControlTest {
    private fun s(i: Int, team: Team, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f) =
        PlayerSnapshot(i, team, Vec2(x, y), Vec2(vx, vy))

    @Test fun mirrorSymmetricSetupGivesHalf() {
        val ps = listOf(
            s(0, Team.HOME, -20f, 5f, 2f, 1f), s(1, Team.AWAY, 20f, -5f, -2f, -1f),
            s(2, Team.HOME, 10f, 12f), s(3, Team.AWAY, -10f, -12f),
        )
        val g = PitchControl.compute(ps, 42, 28)
        assertEquals(0.5f, g.homeShare, 1e-3f)
    }

    @Test fun homePlayerAloneNearCellDominatesThere() {
        val ps = listOf(s(0, Team.HOME, 30f, 10f), s(1, Team.AWAY, -30f, -10f))
        val g = PitchControl.compute(ps, 105, 68)
        val col = ((30f + 52.5f) / 105f * 105).toInt()
        val row = ((10f + 34f) / 68f * 68).toInt()
        assertTrue(g.value(col, row) > 0.9f)
        assertTrue(g.value(105 - 1 - col, 68 - 1 - row) < 0.1f)
    }

    @Test fun movingVelocityShiftsControl() {
        val still = listOf(s(0, Team.HOME, 0f, 0f), s(1, Team.AWAY, 20f, 0f))
        val moving = listOf(s(0, Team.HOME, 0f, 0f, 8f, 0f), s(1, Team.AWAY, 20f, 0f))
        assertTrue(PitchControl.compute(moving, 52, 34).homeShare > PitchControl.compute(still, 52, 34).homeShare)
    }

    @Test fun valuesAreProbabilities() {
        val ps = listOf(s(0, Team.HOME, -5f, 3f, 1f, 1f), s(1, Team.AWAY, 8f, -4f), s(2, Team.AWAY, 0f, 20f))
        val g = PitchControl.compute(ps, 30, 20)
        assertEquals(600, g.values.size)
        assertTrue(g.values.all { it in 0f..1f })
    }

    @Test fun onlyHomeGivesOneOnlyAwayGivesZero() {
        val h = PitchControl.compute(listOf(s(0, Team.HOME, 0f, 0f)), 10, 8)
        assertEquals(1f, h.homeShare, 0f)
        val a = PitchControl.compute(listOf(s(0, Team.AWAY, 0f, 0f)), 10, 8)
        assertEquals(0f, a.homeShare, 0f)
    }

    @Test fun cellCenterGeometry() {
        val c0 = PitchControl.cellCenter(0, 0, 105, 68)
        assertEquals(-52f, c0.x, 1e-4f)
        assertEquals(-33.5f, c0.y, 1e-4f)
        val c1 = PitchControl.cellCenter(104, 67, 105, 68)
        assertEquals(52f, c1.x, 1e-4f)
        assertEquals(33.5f, c1.y, 1e-4f)
    }

    @Test fun arrivalTimeUsesReactionAndRunSpeed() {
        val p = s(0, Team.HOME, 0f, 0f)
        assertEquals(PitchControl.REACTION_TIME + 7f / PitchControl.RUN_SPEED, PitchControl.arrivalTime(p, Vec2(7f, 0f)), 1e-5f)
        val fast = s(0, Team.HOME, 0f, 0f, 10f, 0f)
        assertTrue(PitchControl.arrivalTime(fast, Vec2(20f, 0f)) < PitchControl.arrivalTime(p, Vec2(20f, 0f)))
    }

    @Test fun defaultGridSizeAndRowOrder() {
        val g = PitchControl.compute(listOf(s(0, Team.HOME, 0f, -30f), s(1, Team.AWAY, 0f, 30f)))
        assertEquals(105, g.cols); assertEquals(68, g.rows)
        assertTrue(g.value(52, 2) > 0.5f)    // row 0 is at -Y, home side
        assertTrue(g.value(52, 65) < 0.5f)
    }
}
