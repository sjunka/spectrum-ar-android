package com.sjunka.spectrumar.core

import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.max

enum class Team {
    HOME, AWAY;

    val opponent: Team get() = if (this == HOME) AWAY else HOME

    /** Home attacks towards +X in the bundled window (period 1), away towards -X. */
    val attackSign: Float get() = if (this == HOME) 1f else -1f
}

data class PlayerInfo(val id: String, val team: Team, val number: Int)

data class MatchEvent(
    val team: Team,
    val type: String,
    val subtype: String,
    val start: Double,
    val end: Double,
    val from: String,
    val to: String,
    val sx: Double?,
    val sy: Double?,
    val ex: Double?,
    val ey: Double?,
) {
    val isGoal: Boolean get() = type == "SHOT" && subtype.contains("GOAL")
}

data class PlayerSnapshot(val index: Int, val team: Team, val position: Vec2, val velocity: Vec2)

/**
 * Tracking window decoded into world metres (X along the pitch, Y across, origin at centre spot).
 * All derived series (speed, cumulative distance, running top speed) are precomputed once.
 */
class MatchData private constructor(
    val players: List<PlayerInfo>,
    val events: List<MatchEvent>,
    val frameCount: Int,
    val fps: Double,
    val source: String,
    private val pos: Array<Vec2>,
    private val ballPos: Array<Vec2>,
    private val speeds: FloatArray,
    private val cumDist: FloatArray,
    private val topPrefix: FloatArray,
    private val ballSpeeds: FloatArray,
) {
    private val n = players.size

    val duration: Double get() = (frameCount - 1) / fps
    val playerCount: Int get() = n

    private fun clampFrame(f: Int) = min(max(f, 0), frameCount - 1)

    private fun frameAt(time: Double): Int =
        min(floor(min(max(time, 0.0), duration) * fps).toInt(), frameCount - 1)

    private fun fracAt(time: Double, f: Int): Float =
        (min(max(time, 0.0), duration) * fps - f).toFloat()

    fun position(player: Int, time: Double): Vec2 {
        val f = frameAt(time)
        val u = fracAt(time, f)
        if (u == 0f) return pos[f * n + player]
        return catmullRom(
            pos[clampFrame(f - 1) * n + player], pos[f * n + player],
            pos[clampFrame(f + 1) * n + player], pos[clampFrame(f + 2) * n + player], u,
        )
    }

    fun ballPosition(time: Double): Vec2 {
        val f = frameAt(time)
        val u = fracAt(time, f)
        if (u == 0f) return ballPos[f]
        return catmullRom(ballPos[clampFrame(f - 1)], ballPos[f], ballPos[clampFrame(f + 1)], ballPos[clampFrame(f + 2)], u)
    }

    fun ballSpeed(time: Double): Float {
        val f = frameAt(time)
        val u = fracAt(time, f)
        return ballSpeeds[f] + (ballSpeeds[clampFrame(f + 1)] - ballSpeeds[f]) * u
    }

    private fun lerpSeries(a: FloatArray, i: Int, time: Double): Float {
        val f = frameAt(time)
        val u = fracAt(time, f)
        val x = a[f * n + i]
        val y = a[clampFrame(f + 1) * n + i]
        return x + (y - x) * u
    }

    /** Speed in m/s. */
    fun speed(player: Int, time: Double): Float = lerpSeries(speeds, player, time)

    /** Distance covered since the start of the window, metres. */
    fun distance(player: Int, time: Double): Float = lerpSeries(cumDist, player, time)

    /** Highest speed reached since the start of the window, m/s. */
    fun topSpeed(player: Int, time: Double): Float = lerpSeries(topPrefix, player, time)

    /** Velocity vector (m/s) by central difference of interpolated positions. */
    fun velocity(player: Int, time: Double): Vec2 {
        val h = 0.12
        val a = position(player, time - h)
        val b = position(player, time + h)
        val eff = min(time + h, duration) - max(time - h, 0.0)
        return if (eff > 0) (b - a) / eff.toFloat() else Vec2.ZERO
    }

    fun snapshot(time: Double, overrides: Map<Int, Vec2> = emptyMap()): List<PlayerSnapshot> =
        (0 until n).map { i ->
            val o = overrides[i]
            if (o != null) PlayerSnapshot(i, players[i].team, o, Vec2.ZERO)
            else PlayerSnapshot(i, players[i].team, position(i, time), velocity(i, time))
        }

    fun indexOf(team: Team, number: Int): Int? =
        players.indexOfFirst { it.team == team && it.number == number }.takeIf { it >= 0 }

    /** Event player names look like "Player17": match the digits against team and shirt number. */
    fun indexOfEventPlayer(name: String, team: Team): Int? {
        val digits = name.filter { it.isDigit() }
        val number = digits.toIntOrNull() ?: return null
        return indexOf(team, number)
    }

    val goalTime: Double? get() = events.firstOrNull { it.isGoal }?.start

    companion object {
        const val PITCH_LENGTH = 105f
        const val PITCH_WIDTH = 68f
        const val MAX_HUMAN_SPEED = 11.5f

        /** Catmull-Rom interpolation between 25 Hz samples; exact at integer frames. */
        fun catmullRom(p0: Vec2, p1: Vec2, p2: Vec2, p3: Vec2, t: Float): Vec2 {
            val t2 = t * t
            val t3 = t2 * t
            val a = p1 * 2f
            val b = (p2 - p0) * t
            val c = (p0 * 2f - p1 * 5f + p2 * 4f - p3) * t2
            val d = (p1 * 3f - p0 - p2 * 3f + p3) * t3
            return (a + b + c + d) * 0.5f
        }

        fun mpsToKmh(v: Float): Float = v * 3.6f

        private fun parseTeam(s: String): Team = when (s.lowercase()) {
            "home" -> Team.HOME
            "away" -> Team.AWAY
            else -> throw IllegalArgumentException("bad team: $s")
        }

        private fun optDouble(o: JSONObject, key: String): Double? =
            if (!o.has(key) || o.isNull(key)) null else o.getDouble(key)

        fun movingAverage(s: Array<Vec2>, radius: Int): Array<Vec2> {
            val out = s.copyOf()
            for (f in s.indices) {
                val a = max(0, f - radius)
                val b = min(s.size - 1, f + radius)
                var ax = 0f
                var ay = 0f
                for (k in a..b) { ax += s[k].x; ay += s[k].y }
                val c = (b - a + 1).toFloat()
                out[f] = Vec2(ax / c, ay / c)
            }
            return out
        }

        fun fromJson(json: String, tracking: ByteArray): MatchData {
            try {
                return build(json, tracking)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: org.json.JSONException) {
                throw IllegalArgumentException("bad match json: ${e.message}", e)
            }
        }

        private fun build(json: String, tracking: ByteArray): MatchData {
            val meta = JSONObject(json)
            val fpsInt = meta.getInt("fps")
            val fc = meta.getInt("frames")
            val scaleD = meta.getDouble("scale")
            val missing = meta.getInt("missing")
            require(fpsInt > 0) { "fps must be positive" }
            require(fc >= 1) { "frames must be positive" }
            require(scaleD > 0) { "scale must be positive" }
            val source = meta.optString("source", "")

            val pArr = meta.getJSONArray("players")
            val players = ArrayList<PlayerInfo>(pArr.length())
            for (i in 0 until pArr.length()) {
                val o = pArr.getJSONObject(i)
                players += PlayerInfo(o.getString("id"), parseTeam(o.getString("team")), o.getInt("number"))
            }
            val eArr = meta.getJSONArray("events")
            val events = ArrayList<MatchEvent>(eArr.length())
            for (i in 0 until eArr.length()) {
                val o = eArr.getJSONObject(i)
                events += MatchEvent(
                    team = parseTeam(o.getString("team")),
                    type = o.getString("type"),
                    subtype = o.optString("subtype", ""),
                    start = o.getDouble("start"),
                    end = o.getDouble("end"),
                    from = o.optString("from", ""),
                    to = o.optString("to", ""),
                    sx = optDouble(o, "sx"), sy = optDouble(o, "sy"),
                    ex = optDouble(o, "ex"), ey = optDouble(o, "ey"),
                )
            }

            val n = players.size
            val perFrame = (n + 1) * 2
            val expected = fc.toLong() * perFrame * 2
            require(tracking.size.toLong() == expected) { "tracking.bin size ${tracking.size} != $expected" }

            val fps = fpsInt.toDouble()
            val scale = scaleD.toFloat()
            fun raw(idx: Int): Int {
                val lo = tracking[idx * 2].toInt() and 0xFF
                val hi = tracking[idx * 2 + 1].toInt()
                return (hi shl 8) or lo   // signed Int16 little-endian
            }

            fun series(e: Int): Array<Vec2> {
                val out = arrayOfNulls<Vec2>(fc)
                for (f in 0 until fc) {
                    val base = f * perFrame + e * 2
                    val x = raw(base)
                    val y = raw(base + 1)
                    if (x == missing || y == missing) continue
                    out[f] = Vec2(
                        x / scale * PITCH_LENGTH - PITCH_LENGTH / 2,
                        y / scale * PITCH_WIDTH - PITCH_WIDTH / 2,
                    )
                }
                var last: Vec2? = null
                for (f in 0 until fc) { val v = out[f]; if (v != null) last = v else out[f] = last }
                var next: Vec2? = null
                for (f in fc - 1 downTo 0) { val v = out[f]; if (v != null) next = v else out[f] = next }
                return Array(fc) { out[it] ?: Vec2.ZERO }
            }

            val ballSeries = series(0)
            val pos = Array(fc * n) { Vec2.ZERO }
            val smooth = ArrayList<Array<Vec2>>(n)
            for (i in 0 until n) {
                val s = series(i + 1)
                for (f in 0 until fc) pos[f * n + i] = s[f]
                smooth += movingAverage(s, 2)
            }

            val dt = (1.0 / fps).toFloat()
            val span = 3
            val sp = FloatArray(fc * n)
            val cd = FloatArray(fc * n)
            val tp = FloatArray(fc * n)
            for (i in 0 until n) {
                val s = smooth[i]
                var dist = 0f
                var top = 0f
                for (f in 0 until fc) {
                    val a = max(0, f - span)
                    val b = min(fc - 1, f + span)
                    val v = if (b > a) (s[b] - s[a]).length() / ((b - a) * dt) else 0f
                    val clamped = min(v, MAX_HUMAN_SPEED)
                    sp[f * n + i] = clamped
                    if (f > 0) dist += (s[f] - s[f - 1]).length()
                    cd[f * n + i] = dist
                    top = max(top, clamped)
                    tp[f * n + i] = top
                }
            }
            val bs = movingAverage(ballSeries, 1)
            val bsp = FloatArray(fc)
            for (f in 0 until fc) {
                val a = max(0, f - 2)
                val b = min(fc - 1, f + 2)
                bsp[f] = if (b > a) min(40f, (bs[b] - bs[a]).length() / ((b - a) * dt)) else 0f
            }
            return MatchData(players, events, fc, fps, source, pos, ballSeries, sp, cd, tp, bsp)
        }
    }
}
