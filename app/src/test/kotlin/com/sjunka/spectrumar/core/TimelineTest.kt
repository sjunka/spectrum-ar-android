package com.sjunka.spectrumar.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TimelineTest {
    private fun ev(type: String, start: Double, sub: String = "", team: Team = Team.HOME) =
        MatchEvent(team, type, sub, start, start, "", "", null, null, null, null)

    private val events = listOf(
        ev("PASS", 1.0),
        ev("SHOT", 30.0, "ON TARGET-GOAL"),
        ev("BALL LOST", 5.0, "INTERCEPTION", Team.AWAY),
        ev("RECOVERY", 6.0),
        ev("SET PIECE", 20.0, "CORNER KICK"),
        ev("SHOT", 12.0, "OFF TARGET"),
        ev("CHALLENGE", 40.0),
    )

    @Test fun filtersAndSorts() {
        val m = Timeline.markers(events)
        assertEquals(listOf(5.0, 6.0, 12.0, 20.0, 30.0), m.map { it.time })
        assertEquals(
            listOf(MarkerKind.LOSS, MarkerKind.RECOVERY, MarkerKind.SHOT, MarkerKind.SET_PIECE, MarkerKind.GOAL),
            m.map { it.kind },
        )
        assertEquals(Team.AWAY, m[0].team)
    }

    @Test fun nextAndPrev() {
        val m = Timeline.markers(events)
        assertEquals(12.0, Timeline.nextMarkerTime(m, 6.0)!!, 0.0)
        assertEquals(5.0, Timeline.nextMarkerTime(m, 0.0)!!, 0.0)
        assertNull(Timeline.nextMarkerTime(m, 30.0))
        assertEquals(6.0, Timeline.prevMarkerTime(m, 12.0)!!, 0.0)
        assertEquals(30.0, Timeline.prevMarkerTime(m, 64.0)!!, 0.0)
        assertNull(Timeline.prevMarkerTime(m, 5.0))
    }

    @Test fun emptyEvents() {
        assertTrue(Timeline.markers(emptyList()).isEmpty())
        assertNull(Timeline.nextMarkerTime(emptyList(), 0.0))
    }

    @Test fun clockText() {
        assertEquals("0:00", Timeline.clockText(0.0))
        assertEquals("0:09", Timeline.clockText(9.99))
        assertEquals("1:05", Timeline.clockText(65.0))
        assertEquals("12:00", Timeline.clockText(720.0))
        assertEquals("0:00", Timeline.clockText(-3.0))
    }

    @Test fun clockTextWithTenths() {
        assertEquals("0:00.0", Timeline.clockTextWithTenths(0.0))
        assertEquals("1:05.4", Timeline.clockTextWithTenths(65.43))
        assertEquals("0:02.4", Timeline.clockTextWithTenths(2.4))
        assertEquals("1:04.0", Timeline.clockTextWithTenths(64.0))
    }

    @Test fun realEventsProduceSortedMarkers() {
        val m = MatchData.fromJson(
            File("src/main/assets/match.json").readText(),
            File("src/main/assets/tracking.bin").readBytes(),
        )
        val ms = Timeline.markers(m.events)
        assertTrue(ms.isNotEmpty())
        assertTrue(ms.zipWithNext().all { (a, b) -> a.time <= b.time })
        assertTrue(ms.any { it.kind == MarkerKind.GOAL })
    }
}
