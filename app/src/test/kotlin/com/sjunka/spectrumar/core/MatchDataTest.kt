package com.sjunka.spectrumar.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MatchDataTest {
    private val missing = -32768

    private fun json(frames: Int, events: String = "[]") =
        """{"fps":25,"frames":$frames,"scale":10000,"missing":$missing,"pitchLength":105.0,"pitchWidth":68.0,
        "players":[{"id":"H7","team":"home","number":7},{"id":"A9","team":"away","number":9}],
        "events":$events,"source":"synthetic"}"""

    private fun bytes(vararg v: Int): ByteArray {
        val bb = ByteBuffer.allocate(v.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { bb.putShort(it.toShort()) }
        return bb.array()
    }

    // 3 frames: ball, player 1, player 2 (player 2 missing in frames 0 and 2)
    private fun tiny(): MatchData = MatchData.fromJson(
        json(3),
        bytes(
            5000, 5000, 5000, 5000, missing, missing,
            5000, 5000, 6000, 5000, 7000, 2500,
            5000, 5000, 7000, 5000, missing, missing,
        ),
    )

    private fun realData(): MatchData {
        val j = File("src/main/assets/match.json").readText()
        val b = File("src/main/assets/tracking.bin").readBytes()
        return MatchData.fromJson(j, b)
    }

    @Test fun tinyDecodesBasics() {
        val m = tiny()
        assertEquals(2, m.playerCount)
        assertEquals(3, m.frameCount)
        assertEquals(2.0 / 25.0, m.duration, 1e-9)
        assertEquals("synthetic", m.source)
    }

    @Test fun tinyCoordinatesToWorldMetres() {
        val m = tiny()
        val p = m.position(0, 1.0 / 25.0)
        assertEquals(0.6f * 105f - 52.5f, p.x, 1e-3f)
        assertEquals(0f, p.y, 1e-3f)
    }

    @Test fun missingValuesAreHoldFilled() {
        val m = tiny()
        val mid = m.position(1, 1.0 / 25.0)
        assertEquals(0.7f * 105f - 52.5f, mid.x, 1e-3f)
        assertEquals(0.25f * 68f - 34f, mid.y, 1e-3f)
        assertEquals(mid, m.position(1, 0.0))
        assertEquals(mid, m.position(1, 2.0 / 25.0))
    }

    @Test fun sizeMismatchThrows() {
        try {
            MatchData.fromJson(json(3), ByteArray(10))
            fail("expected exception")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun badJsonThrowsIllegalArgument() {
        try {
            MatchData.fromJson("{not json", ByteArray(0))
            fail("expected exception")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun eventsParseWithNullCoordinates() {
        val ev = """[{"team":"away","type":"SHOT","subtype":"ON TARGET-GOAL","start":1.5,"end":2.0,
            "from":"Player9","to":"","sx":null,"sy":0.4,"ex":null,"ey":null}]"""
        val m = MatchData.fromJson(json(1, ev), bytes(5000, 5000, 5000, 5000, 5000, 5000))
        val e = m.events.single()
        assertEquals(Team.AWAY, e.team)
        assertNull(e.sx)
        assertEquals(0.4, e.sy!!, 1e-9)
        assertTrue(e.isGoal)
        assertEquals(1.5, m.goalTime!!, 1e-9)
    }

    @Test fun teamHelpers() {
        assertEquals(Team.AWAY, Team.HOME.opponent)
        assertEquals(Team.HOME, Team.AWAY.opponent)
        assertEquals(1f, Team.HOME.attackSign, 0f)
        assertEquals(-1f, Team.AWAY.attackSign, 0f)
    }

    @Test fun indexLookups() {
        val m = tiny()
        assertEquals(0, m.indexOf(Team.HOME, 7))
        assertEquals(1, m.indexOf(Team.AWAY, 9))
        assertNull(m.indexOf(Team.AWAY, 7))
        assertEquals(1, m.indexOfEventPlayer("Player9", Team.AWAY))
        assertNull(m.indexOfEventPlayer("Player9", Team.HOME))
        assertNull(m.indexOfEventPlayer("nobody", Team.HOME))
    }

    @Test fun snapshotOverridesGetZeroVelocity() {
        val m = tiny()
        val s = m.snapshot(0.02, mapOf(0 to Vec2(3f, 4f)))
        assertEquals(Vec2(3f, 4f), s[0].position)
        assertEquals(Vec2.ZERO, s[0].velocity)
        assertEquals(Team.AWAY, s[1].team)
        assertEquals(m.position(1, 0.02), s[1].position)
    }

    @Test fun realAssetsDecode() {
        val m = realData()
        assertEquals(22, m.playerCount)
        assertEquals(1601, m.frameCount)
        assertEquals(64.0, m.duration, 1e-9)
        assertNotNull(m.goalTime)
        assertTrue(m.events.isNotEmpty())
    }

    @Test fun realPositionsInsidePitch() {
        val m = realData()
        var t = 0.0
        while (t <= m.duration) {
            for (i in 0 until m.playerCount) {
                val p = m.position(i, t)
                assertTrue("x $p", p.x in -57.5f..57.5f)
                assertTrue("y $p", p.y in -39f..39f)
            }
            val b = m.ballPosition(t)
            assertTrue("ball $b", b.x in -75f..75f && b.y in -55f..55f)
            t += 0.37
        }
    }

    @Test fun catmullRomExactAtIntegerFrames() {
        val m = realData()
        val a = m.position(3, 10.0)          // frame 250, exact
        val b = m.position(3, 10.0 + 1e-9)   // numerically almost the same
        assertEquals(a.x, b.x, 1e-2f)
        assertEquals(a.y, b.y, 1e-2f)
        val p0 = Vec2(1f, 2f); val p1 = Vec2(3f, 1f); val p2 = Vec2(-2f, 5f); val p3 = Vec2(0f, 0f)
        assertEquals(p1, MatchData.catmullRom(p0, p1, p2, p3, 0f))
        val e = MatchData.catmullRom(p0, p1, p2, p3, 1f)
        assertEquals(p2.x, e.x, 1e-5f); assertEquals(p2.y, e.y, 1e-5f)
    }

    @Test fun catmullRomMidpointOfConstantVelocityLine() {
        val v = Vec2(2f, -1f)
        val pts = (0..3).map { v * it.toFloat() }
        val mid = MatchData.catmullRom(pts[0], pts[1], pts[2], pts[3], 0.5f)
        assertEquals(1.5f * 2f, mid.x, 1e-5f)
        assertEquals(1.5f * -1f, mid.y, 1e-5f)
    }

    @Test fun timeIsClamped() {
        val m = realData()
        assertEquals(m.position(5, 0.0), m.position(5, -3.0))
        assertEquals(m.position(5, m.duration), m.position(5, m.duration + 10))
        assertEquals(m.speed(5, 0.0), m.speed(5, -1.0), 0f)
    }

    @Test fun speedNeverExceedsHumanMax() {
        val m = realData()
        var t = 0.0
        while (t <= m.duration) {
            for (i in 0 until m.playerCount) {
                val s = m.speed(i, t)
                assertTrue(s >= 0f && s <= MatchData.MAX_HUMAN_SPEED + 1e-4f)
            }
            t += 0.173
        }
    }

    @Test fun distanceAndTopSpeedAreMonotonic() {
        val m = realData()
        for (i in 0 until m.playerCount) {
            var d = -1f; var top = -1f
            var t = 0.0
            while (t <= m.duration) {
                val nd = m.distance(i, t); val nt = m.topSpeed(i, t)
                assertTrue(nd >= d - 1e-4f)
                assertTrue(nt >= top - 1e-4f)
                d = nd; top = nt
                t += 0.5
            }
            assertTrue(m.distance(i, m.duration) > 0f)
        }
    }

    @Test fun velocityConsistentWithSpeed() {
        val m = realData()
        var sum = 0.0; var count = 0
        var t = 1.0
        while (t < m.duration - 1) {
            for (i in 0 until m.playerCount) {
                sum += Math.abs(m.velocity(i, t).length() - m.speed(i, t)); count++
            }
            t += 1.3
        }
        assertTrue("mean diff ${sum / count}", sum / count < 1.0)
    }

    @Test fun velocityOfConstantMotionIsExact() {
        // player moves 1000 units/frame in x: 0.1 * 105 m per 0.04 s
        val m = MatchData.fromJson(
            json(4),
            bytes(
                5000, 5000, 1000, 5000, 1000, 5000,
                5000, 5000, 2000, 5000, 1000, 5000,
                5000, 5000, 3000, 5000, 1000, 5000,
                5000, 5000, 4000, 5000, 1000, 5000,
            ),
        )
        val v = m.velocity(0, 0.06)
        assertEquals(0.1f * 105f / 0.04f, v.x, 0.5f)
        assertEquals(0f, v.y, 1e-3f)
        assertEquals(0f, m.velocity(1, 0.06).length(), 1e-3f)
    }

    @Test fun kmhConversion() {
        assertEquals(36f, MatchData.mpsToKmh(10f), 1e-4f)
    }

    @Test fun ballSpeedIsReasonable() {
        val m = realData()
        var t = 0.0
        while (t <= m.duration) {
            val s = m.ballSpeed(t)
            assertTrue(s in 0f..40.001f)
            t += 0.5
        }
    }
}
