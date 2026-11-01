package com.sjunka.spectrumar.core

import org.junit.Assert.*
import org.junit.Test

class ReplayClockTest {
    @Test fun advanceMovesTimeBySpeed() {
        val c = ReplayClock(10.0)
        c.advance(0.5)
        assertEquals(0.5, c.time, 1e-9)
        c.setSpeed(2.0)
        c.advance(0.5)
        assertEquals(1.5, c.time, 1e-9)
        assertEquals(0.15, c.progress, 1e-9)
    }

    @Test fun loopWrapsPastEnd() {
        val c = ReplayClock(10.0)
        c.seek(9.5)
        c.advance(1.0)
        assertEquals(0.5, c.time, 1e-9)
        assertTrue(c.playing)
    }

    @Test fun nonLoopClampsAndStops() {
        val c = ReplayClock(10.0)
        c.loop = false
        c.seek(9.5)
        c.advance(1.0)
        assertEquals(10.0, c.time, 0.0)
        assertFalse(c.playing)
    }

    @Test fun pausedDoesNotAdvance() {
        val c = ReplayClock(10.0)
        c.togglePlay()
        assertFalse(c.playing)
        c.advance(1.0)
        assertEquals(0.0, c.time, 0.0)
        c.togglePlay()
        assertTrue(c.playing)
    }

    @Test fun seekClamps() {
        val c = ReplayClock(10.0)
        c.seek(-4.0); assertEquals(0.0, c.time, 0.0)
        c.seek(99.0); assertEquals(10.0, c.time, 0.0)
        c.seek(3.0); assertEquals(3.0, c.time, 0.0)
    }

    @Test fun setSpeedSnapsToNearest() {
        val c = ReplayClock(10.0)
        c.setSpeed(1.4); assertEquals(1.0, c.speed, 0.0)
        c.setSpeed(1.6); assertEquals(2.0, c.speed, 0.0)
        c.setSpeed(100.0); assertEquals(4.0, c.speed, 0.0)
        c.setSpeed(0.0); assertEquals(0.25, c.speed, 0.0)
    }

    @Test fun cycleSpeedOrderAndWrap() {
        val c = ReplayClock(10.0)
        val seen = (1..5).map { c.cycleSpeed(); c.speed }
        assertEquals(listOf(2.0, 4.0, 0.25, 0.5, 1.0), seen)
    }

    @Test fun defaults() {
        val c = ReplayClock(5.0)
        assertEquals(1.0, c.speed, 0.0)
        assertTrue(c.playing); assertTrue(c.loop)
        assertEquals(0.0, c.progress, 0.0)
    }
}
