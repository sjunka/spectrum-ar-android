package com.sjunka.spectrumar.core

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Grid based pitch control: for every cell, who can get there first.
 * Home control probability in 0..1, row-major, row 0 at -Y.
 */
class PitchControlGrid(val cols: Int, val rows: Int, val values: FloatArray) {
    fun value(col: Int, row: Int): Float = values[row * cols + col]

    /** Mean home control over the pitch (0..1). */
    val homeShare: Float
        get() {
            if (values.isEmpty()) return 0.5f
            var s = 0f
            for (v in values) s += v
            return s / values.size
        }
}

object PitchControl {
    const val REACTION_TIME = 0.3f
    const val RUN_SPEED = 7f
    const val SHARPNESS = 2.4f   // 1/s of arrival-time difference

    fun cellCenter(col: Int, row: Int, cols: Int, rows: Int): Vec2 = Vec2(
        (col + 0.5f) / cols * MatchData.PITCH_LENGTH - MatchData.PITCH_LENGTH / 2,
        (row + 0.5f) / rows * MatchData.PITCH_WIDTH - MatchData.PITCH_WIDTH / 2,
    )

    fun arrivalTime(p: PlayerSnapshot, cell: Vec2): Float {
        val predicted = p.position + p.velocity * REACTION_TIME
        return REACTION_TIME + predicted.distanceTo(cell) / RUN_SPEED
    }

    fun compute(players: List<PlayerSnapshot>, cols: Int = 105, rows: Int = 68): PitchControlGrid {
        val out = FloatArray(cols * rows)
        var nh = 0
        var na = 0
        for (p in players) if (p.team == Team.HOME) nh++ else na++
        if (nh == 0 || na == 0) {
            out.fill(if (nh == 0) 0f else 1f)
            return PitchControlGrid(cols, rows, out)
        }
        val hx = FloatArray(nh); val hy = FloatArray(nh)
        val ax = FloatArray(na); val ay = FloatArray(na)
        var hi = 0
        var ai = 0
        for (p in players) {
            val px = p.position.x + p.velocity.x * REACTION_TIME
            val py = p.position.y + p.velocity.y * REACTION_TIME
            if (p.team == Team.HOME) { hx[hi] = px; hy[hi] = py; hi++ } else { ax[ai] = px; ay[ai] = py; ai++ }
        }
        for (r in 0 until rows) {
            val cy = (r + 0.5f) / rows * MatchData.PITCH_WIDTH - MatchData.PITCH_WIDTH / 2
            for (c in 0 until cols) {
                val cx = (c + 0.5f) / cols * MatchData.PITCH_LENGTH - MatchData.PITCH_LENGTH / 2
                var th = Float.MAX_VALUE
                var ta = Float.MAX_VALUE
                for (i in 0 until nh) {
                    val dx = hx[i] - cx; val dy = hy[i] - cy
                    val d = dx * dx + dy * dy
                    if (d < th) th = d
                }
                for (i in 0 until na) {
                    val dx = ax[i] - cx; val dy = ay[i] - cy
                    val d = dx * dx + dy * dy
                    if (d < ta) ta = d
                }
                val dh = sqrt(th) / RUN_SPEED
                val da = sqrt(ta) / RUN_SPEED
                out[r * cols + c] = 1f / (1f + exp(-(da - dh) * SHARPNESS))
            }
        }
        return PitchControlGrid(cols, rows, out)
    }
}
