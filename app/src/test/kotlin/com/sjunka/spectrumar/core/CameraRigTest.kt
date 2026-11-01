package com.sjunka.spectrumar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraRigTest {
    private val aspect = 1080f / 2400f

    @Test fun tacticalFramesWholePitchInsideSafeBox() {
        val box = NdcBox(0.94f, -0.40f, 0.80f)
        val pose = CameraController.defaultTactical(aspect, box)
        for (c in CameraMath.pitchCorners(2.5f)) {
            val (x, y) = CameraMath.project(pose, c, aspect)!!
            assertTrue("x=$x", kotlin.math.abs(x) <= box.xMax + 0.02f)
            assertTrue("y=$y", y >= box.yMin - 0.03f && y <= box.yMax + 0.03f)
        }
    }

    @Test fun tacticalIsHighAngleAlongPitch() {
        val pose = CameraController.defaultTactical(aspect)
        assertTrue(pose.pitch > 0.8f)
        assertTrue(pose.eye.x < -20f) // looks along +X from the home end
    }

    @Test fun ballPresetKeepsBallAndNearbyPlayersInView() {
        val ball = Vec2(20f, 5f)
        val others = listOf(Vec2(25f, 8f), Vec2(15f, 0f), Vec2(-40f, 10f))
        val cam = CameraController().apply { this.aspect = aspect }
        val g = cam.goalFor(CameraPreset.BALL, ball, others)
        val (x, y) = CameraMath.project(g, Vec3(ball.x, 0f, ball.y), aspect)!!
        assertTrue(kotlin.math.abs(x) < 0.9f && y > -0.5f && y < 0.8f)
        assertTrue(g.dist in 30f..160f)
    }

    @Test fun ballPresetIsCloserThanTactical() {
        val cam = CameraController().apply { this.aspect = aspect }
        val ball = Vec2(0f, 0f)
        val a = cam.goalFor(CameraPreset.BALL, ball, listOf(Vec2(5f, 5f)))
        val t = cam.goalFor(CameraPreset.TACTICAL, ball, emptyList())
        assertTrue(a.dist < t.dist)
    }

    @Test fun gesturesSwitchToFreeAndClamp() {
        val cam = CameraController()
        cam.orbit(0.3f, 5f)
        assertEquals(CameraPreset.FREE, cam.preset)
        assertTrue(cam.current.pitch <= 1.5f)
        cam.zoom(1000f)
        assertEquals(14f, cam.current.dist, 0.001f)
    }

    @Test fun stepEasesTowardsGoal() {
        val cam = CameraController()
        cam.setPreset(CameraPreset.STADIUM, Vec2(10f, 0f))
        val before = cam.current.dist
        repeat(300) { cam.step(1f / 60f) }
        assertTrue(kotlin.math.abs(cam.current.dist - 74f) < 1f)
        assertTrue(before != cam.current.dist)
    }

    @Test fun shortestAngleWraps() {
        assertEquals(0.2f, CameraMath.shortestAngle(3.1f, 3.1f + 0.2f), 0.001f)
        assertTrue(kotlin.math.abs(CameraMath.shortestAngle(3.0f, -3.0f)) < 0.3f)
    }
}
