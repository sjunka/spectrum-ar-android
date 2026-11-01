package com.sjunka.spectrumar.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class CameraPreset(val label: String) {
    TACTICAL("Tactical"),
    STADIUM("Stadium"),
    BALL("Ball + cluster"),
    CLOSE("Close-up"),
    FREE("Free"),
}

/** Orbit camera pose: look-at target on/above the pitch plane, yaw/pitch in radians, distance in metres. */
data class OrbitPose(
    val tx: Float, val ty: Float, val tz: Float,
    val yaw: Float, val pitch: Float, val dist: Float,
) {
    val target get() = Vec3(tx, ty, tz)

    /** yaw 0 places the eye on +Z looking towards -Z; yaw -90 deg looks along +X (the home attack direction). */
    val eye: Vec3
        get() = Vec3(
            tx + dist * sin(yaw) * cos(pitch),
            ty + dist * sin(pitch),
            tz + dist * cos(yaw) * cos(pitch),
        )
}

/** Screen-space box (NDC, -1..1) in which framed content has to stay: leaves room for HUD chrome. */
data class NdcBox(val xMax: Float, val yMin: Float, val yMax: Float)

object CameraMath {
    /** Filament default lens (28 mm, 24 mm sensor height): tan of the vertical half field of view. */
    const val DEFAULT_TAN_V = 12f / 28f

    fun forward(p: OrbitPose): Vec3 = (p.target - p.eye).normalized()

    /** NDC projection of a world point, null when behind the camera. */
    fun project(p: OrbitPose, pt: Vec3, aspect: Float, tanV: Float = DEFAULT_TAN_V): Pair<Float, Float>? {
        val eye = p.eye
        val fwd = (p.target - eye).normalized()
        val right = fwd.cross(Vec3(0f, 1f, 0f)).normalized()
        val up = right.cross(fwd)
        val rel = pt - eye
        val depth = rel.dot(fwd)
        if (depth <= 0.05f) return null
        return Pair(rel.dot(right) / (depth * tanV * aspect), rel.dot(up) / (depth * tanV))
    }

    private fun bounds(p: OrbitPose, pts: List<Vec3>, aspect: Float, tanV: Float): FloatArray {
        var xm = 0f; var ymin = Float.MAX_VALUE; var ymax = -Float.MAX_VALUE
        for (pt in pts) {
            val n = project(p, pt, aspect, tanV) ?: return floatArrayOf(99f, -99f, 99f)
            xm = max(xm, abs(n.first)); ymin = min(ymin, n.second); ymax = max(ymax, n.second)
        }
        return floatArrayOf(xm, ymin, ymax)
    }

    /**
     * Frames [pts] inside [box] for the given orientation. The target is shifted on the ground plane
     * along the view direction so the content centres in the safe band, and the distance is the
     * smallest that fits.
     */
    fun frame(
        pts: List<Vec3>, yaw: Float, pitch: Float, aspect: Float, box: NdcBox,
        minDist: Float = 8f, maxDist: Float = 700f, tanV: Float = DEFAULT_TAN_V,
    ): OrbitPose {
        var cx = 0f; var cz = 0f
        for (p in pts) { cx += p.x; cz += p.z }
        cx /= pts.size; cz /= pts.size
        var pose = OrbitPose(cx, 0f, cz, yaw, pitch, 120f)
        val groundFwd = Vec3(-sin(yaw), 0f, -cos(yaw))
        val bandCenter = (box.yMin + box.yMax) / 2f
        repeat(6) {
            // binary search the distance that just fits
            var lo = minDist; var hi = maxDist
            repeat(30) {
                val mid = (lo + hi) / 2f
                val b = bounds(pose.copy(dist = mid), pts, aspect, tanV)
                val fits = b[0] <= box.xMax && b[1] >= box.yMin && b[2] <= box.yMax
                if (fits) hi = mid else lo = mid
            }
            pose = pose.copy(dist = hi)
            val b = bounds(pose, pts, aspect, tanV)
            val c = (b[1] + b[2]) / 2f
            // content too high on screen (c > bandCenter): move target up the screen = forward along the ground
            val shift = (c - bandCenter) * tanV * pose.dist
            pose = pose.copy(tx = pose.tx + groundFwd.x * shift, tz = pose.tz + groundFwd.z * shift)
        }
        return pose
    }

    fun pitchCorners(margin: Float = 0f): List<Vec3> {
        val hx = MatchData.PITCH_LENGTH / 2 + margin
        val hz = MatchData.PITCH_WIDTH / 2 + margin
        return listOf(Vec3(-hx, 0f, -hz), Vec3(hx, 0f, -hz), Vec3(hx, 0f, hz), Vec3(-hx, 0f, hz))
    }

    fun shortestAngle(from: Float, to: Float): Float {
        var d = (to - from) % (2 * PI.toFloat())
        if (d > PI) d -= 2 * PI.toFloat()
        if (d < -PI) d += 2 * PI.toFloat()
        return d
    }
}

/**
 * Smoothed orbit camera shared by the table mode: presets compute a goal pose, the current pose
 * eases towards it; user gestures write directly and switch to FREE.
 */
class CameraController {
    var aspect = 0.45f
    var preset = CameraPreset.TACTICAL
        private set
    var current = defaultTactical(0.45f)
        private set
    private var goal = current
    /** Extra yaw added on top of a preset (used by the demo fly-through). */
    var yawBias = 0f
    var easeRate = 3.2f

    private var tacticalBox = NdcBox(0.97f, -0.34f, 0.76f)

    /** HUD panels cover the bottom [fraction] of the screen: keep the board above them. */
    fun reserveBottom(fraction: Float) {
        val yMin = (-1f + 2f * fraction + 0.04f).coerceIn(-0.6f, 0.35f)
        if (abs(yMin - tacticalBox.yMin) < 0.01f) return
        tacticalBox = NdcBox(0.97f, yMin, 0.76f)
        if (preset == CameraPreset.TACTICAL) goal = goalFor(CameraPreset.TACTICAL, Vec2.ZERO, emptyList())
    }
    private val ballBox get() = NdcBox(0.86f, tacticalBox.yMin + 0.06f, 0.66f)

    fun setPreset(p: CameraPreset, ball: Vec2 = Vec2.ZERO, others: List<Vec2> = emptyList()) {
        preset = p
        goal = goalFor(p, ball, others)
    }

    /** Call every frame for dynamic presets. */
    fun track(ball: Vec2, others: List<Vec2>) {
        if (preset == CameraPreset.FREE || preset == CameraPreset.TACTICAL) return
        goal = goalFor(preset, ball, others)
    }

    fun goalFor(p: CameraPreset, ball: Vec2, others: List<Vec2>): OrbitPose = when (p) {
        CameraPreset.TACTICAL -> defaultTactical(aspect, tacticalBox, yawBias)
        CameraPreset.STADIUM -> {
            // low broadcast-style angle from the corner, whole board kept in frame
            val yaw = -PI.toFloat() / 2 + 0.22f + yawBias
            CameraMath.frame(CameraMath.pitchCorners(BOARD_MARGIN), yaw, 0.6f, aspect, NdcBox(0.97f, tacticalBox.yMin, 0.76f), minDist = 40f)
        }
        CameraPreset.BALL -> {
            val near = others.filter { it.distanceTo(ball) < 26f }
            val pts = (listOf(ball) + near).map { Vec3(it.x, 0f, it.y) } + extraHeight(ball)
            val yaw = -PI.toFloat() / 2 + (ball.y / 34f).coerceIn(-1f, 1f) * 0.22f + yawBias
            CameraMath.frame(pts, yaw, 0.78f, aspect, ballBox, minDist = 72f, maxDist = 170f)
        }
        CameraPreset.CLOSE -> {
            val near = others.filter { it.distanceTo(ball) < 13f }
            val pts = (listOf(ball) + near).map { Vec3(it.x, 0f, it.y) } + extraHeight(ball)
            val yaw = -PI.toFloat() / 2 + (ball.y / 34f).coerceIn(-1f, 1f) * 0.35f + yawBias
            CameraMath.frame(pts, yaw, 0.62f, aspect, ballBox, minDist = 44f, maxDist = 100f)
        }
        CameraPreset.FREE -> current
    }

    private fun extraHeight(b: Vec2) = listOf(Vec3(b.x, 4.5f, b.y))

    fun snapToGoal() { current = goal }

    fun step(dt: Float) {
        if (preset == CameraPreset.FREE) { goal = current; return }
        val k = 1f - exp(-dt * easeRate)
        val g = goal
        val c = current
        current = OrbitPose(
            c.tx + (g.tx - c.tx) * k, c.ty + (g.ty - c.ty) * k, c.tz + (g.tz - c.tz) * k,
            c.yaw + CameraMath.shortestAngle(c.yaw, g.yaw) * k,
            c.pitch + (g.pitch - c.pitch) * k,
            c.dist + (g.dist - c.dist) * k,
        )
    }

    /** Direct manipulation: switches to FREE. */
    fun orbit(dYaw: Float, dPitch: Float) {
        freeze()
        current = current.copy(yaw = current.yaw + dYaw, pitch = (current.pitch + dPitch).coerceIn(0.12f, 1.5f))
        goal = current
    }

    /** Pan in screen terms: dx right, dy up, in metres at the target depth. */
    fun pan(dxMetres: Float, dyMetres: Float) {
        freeze()
        val c = current
        val right = Vec3(cos(c.yaw), 0f, -sin(c.yaw))
        val fwd = Vec3(-sin(c.yaw), 0f, -cos(c.yaw))
        val nx = (c.tx - right.x * dxMetres + fwd.x * dyMetres).coerceIn(-70f, 70f)
        val nz = (c.tz - right.z * dxMetres + fwd.z * dyMetres).coerceIn(-50f, 50f)
        current = c.copy(tx = nx, tz = nz)
        goal = current
    }

    fun zoom(scale: Float) {
        freeze()
        current = current.copy(dist = (current.dist / scale).coerceIn(14f, 260f))
        goal = current
    }

    fun jumpTo(p: OrbitPose) { preset = CameraPreset.FREE; current = p; goal = p }

    private fun freeze() { preset = CameraPreset.FREE }

    companion object {
        /** Metres of slab around the pitch lines that must stay on screen so the board never looks cropped. */
        const val BOARD_MARGIN = 7.3f

        fun defaultTactical(aspect: Float, box: NdcBox = NdcBox(0.97f, -0.34f, 0.76f), yawBias: Float = 0f): OrbitPose =
            CameraMath.frame(CameraMath.pitchCorners(BOARD_MARGIN), -PI.toFloat() / 2 + yawBias, 1.0f, aspect, box)
    }
}
