package com.sjunka.spectrumar.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Expected threat (xT) lookup and pass option ranking.
 *
 * The 12 x 8 grid is the public xT surface published by Karun Singh,
 * Expected Threat, https://karun.in/blog/expected-threat.html (open xT 12x8 grid,
 * file open_xt_12x8_v1.json). Rows run across the pitch width (8), columns along its
 * length (12), oriented so that column 11 is the one closest to the goal being attacked.
 * Values are the probability that possession in that zone leads to a goal in the next few actions.
 */
object ExpectedThreat {
    const val COLS = 12
    const val ROWS = 8

    val grid: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.00638303, 0.00779616, 0.00844854, 0.00977659, 0.01126267, 0.01248344, 0.01473596, 0.0174506, 0.02122129, 0.02756312, 0.03485072, 0.0379259),
        doubleArrayOf(0.00750072, 0.00878589, 0.00942382, 0.0105949, 0.01214719, 0.0138454, 0.01611813, 0.01870347, 0.02401521, 0.02953272, 0.04066992, 0.04647721),
        doubleArrayOf(0.0088799, 0.00977745, 0.01001304, 0.01110462, 0.01269174, 0.01429128, 0.01685596, 0.01935132, 0.0241224, 0.02855202, 0.05491138, 0.06442595),
        doubleArrayOf(0.00941056, 0.01082722, 0.01016549, 0.01132376, 0.01262646, 0.01484598, 0.01689528, 0.0199707, 0.02385149, 0.03511326, 0.10805102, 0.25745362),
        doubleArrayOf(0.00941056, 0.01082722, 0.01016549, 0.01132376, 0.01262646, 0.01484598, 0.01689528, 0.0199707, 0.02385149, 0.03511326, 0.10805102, 0.25745362),
        doubleArrayOf(0.0088799, 0.00977745, 0.01001304, 0.01110462, 0.01269174, 0.01429128, 0.01685596, 0.01935132, 0.0241224, 0.02855202, 0.05491138, 0.06442595),
        doubleArrayOf(0.00750072, 0.00878589, 0.00942382, 0.0105949, 0.01214719, 0.0138454, 0.01611813, 0.01870347, 0.02401521, 0.02953272, 0.04066992, 0.04647721),
        doubleArrayOf(0.00638303, 0.00779616, 0.00844854, 0.00977659, 0.01126267, 0.01248344, 0.01473596, 0.0174506, 0.02122129, 0.02756312, 0.03485072, 0.0379259),
    )

    /** xT for a world position (metres, centred) for a team attacking towards +X (attackSign 1) or -X. */
    fun value(p: Vec2, attackSign: Float): Double {
        val nx = (p.x * attackSign + MatchData.PITCH_LENGTH / 2) / MatchData.PITCH_LENGTH
        val nz = (p.y + MatchData.PITCH_WIDTH / 2) / MatchData.PITCH_WIDTH
        val c = min(max((nx * COLS).toInt(), 0), COLS - 1)
        val r = min(max((nz * ROWS).toInt(), 0), ROWS - 1)
        return grid[r][c]
    }

    fun value(p: Vec2, team: Team): Double = value(p, team.attackSign)
}

data class PassOption(
    val target: Int,            // player index
    val targetValue: Double,    // xT of the receiving zone
    val gain: Double,           // targetValue - carrier zone value
    val probability: Double,    // estimated completion probability
    val expectedValue: Double,  // probability * targetValue
)

object PassEvaluator {
    /** Distance of point p from segment a-b, plus the projection parameter. */
    fun segmentDistance(p: Vec2, a: Vec2, b: Vec2): Pair<Float, Float> {
        val ab = b - a
        val len2 = ab.lengthSq()
        if (len2 <= 1e-6f) return Pair(p.distanceTo(a), 0f)
        val t = (p - a).dot(ab) / len2
        val c = a + ab * min(max(t, 0f), 1f)
        return Pair(p.distanceTo(c), t)
    }

    fun probability(from: Vec2, to: Vec2, opponents: List<Vec2>): Double {
        val d = from.distanceTo(to).toDouble()
        var p = exp(-max(0.0, d - 8) / 38)                    // range penalty
        for (o in opponents) {
            val (dist, t) = segmentDistance(o, from, to)
            if (t > 0.02f && t < 1.0f) {                       // opponent between passer and receiver
                p *= 1 - 0.85 * max(0.0, 1 - dist.toDouble() / 3.2)
            }
            val atReceiver = o.distanceTo(to).toDouble()       // receiver under pressure
            p *= 1 - 0.35 * max(0.0, 1 - atReceiver / 3.0)
        }
        return min(max(p, 0.02), 0.98)
    }

    /** Ball carrier: closest player to the ball (within maxDistance), null if the ball is loose. */
    fun carrier(players: List<PlayerSnapshot>, ball: Vec2, maxDistance: Float = 6f): Int? {
        val best = players.minByOrNull { it.position.distanceTo(ball) } ?: return null
        return if (best.position.distanceTo(ball) <= maxDistance) best.index else null
    }

    /** All pass options for the carrier, best expected value first. */
    fun options(carrier: Int, players: List<PlayerSnapshot>): List<PassOption> {
        val me = players.firstOrNull { it.index == carrier } ?: return emptyList()
        val opponents = players.filter { it.team != me.team }.map { it.position }
        val here = ExpectedThreat.value(me.position, me.team)
        return players.filter { it.team == me.team && it.index != carrier }.map { mate ->
            val v = ExpectedThreat.value(mate.position, me.team)
            val p = probability(me.position, mate.position, opponents)
            PassOption(mate.index, v, v - here, p, p * v)
        }.sortedByDescending { it.expectedValue }
    }

    fun best(carrier: Int, players: List<PlayerSnapshot>): PassOption? =
        options(carrier, players).firstOrNull()
}
