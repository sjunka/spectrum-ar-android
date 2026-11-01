package com.sjunka.spectrumar.core

import kotlin.math.floor

enum class MarkerKind { PASS, SHOT, GOAL, RECOVERY, SET_PIECE, LOSS, OTHER }

data class TimelineMarker(val time: Double, val kind: MarkerKind, val team: Team)

object Timeline {
    private const val EPS = 1e-3

    fun markers(events: List<MatchEvent>): List<TimelineMarker> =
        events.mapNotNull { e ->
            val kind = when (e.type) {
                "SHOT" -> if (e.isGoal) MarkerKind.GOAL else MarkerKind.SHOT
                "RECOVERY" -> MarkerKind.RECOVERY
                "SET PIECE" -> MarkerKind.SET_PIECE
                "BALL LOST" -> MarkerKind.LOSS
                else -> null
            }
            kind?.let { TimelineMarker(e.start, it, e.team) }
        }.sortedBy { it.time }

    fun nextMarkerTime(markers: List<TimelineMarker>, after: Double): Double? =
        markers.firstOrNull { it.time > after + EPS }?.time

    fun prevMarkerTime(markers: List<TimelineMarker>, before: Double): Double? =
        markers.lastOrNull { it.time < before - EPS }?.time

    fun clockText(seconds: Double): String {
        val s = floor(seconds.coerceAtLeast(0.0) + 1e-9).toInt()
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    fun clockTextWithTenths(seconds: Double): String {
        val tenths = floor(seconds.coerceAtLeast(0.0) * 10 + 1e-6).toLong()
        val s = tenths / 10
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}.${tenths % 10}"
    }
}
