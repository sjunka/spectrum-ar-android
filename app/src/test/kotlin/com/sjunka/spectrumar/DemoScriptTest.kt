package com.sjunka.spectrumar

import com.sjunka.spectrumar.app.DemoAction
import com.sjunka.spectrumar.app.DemoScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScriptTest {
    @Test fun stepsAreOrderedAndWithinLoop() {
        val t = DemoScript.steps.map { it.at }
        assertEquals(t.sorted(), t)
        assertTrue(t.last() < DemoScript.LOOP_SECONDS)
    }

    @Test fun dueReturnsEachStepExactlyOnceAcrossTicks() {
        var prev = -1.0
        var count = 0
        var now = 0.0
        while (now < DemoScript.LOOP_SECONDS) {
            now += 1.0 / 60.0
            count += DemoScript.due(prev, now).size
            prev = now
        }
        assertEquals(DemoScript.steps.size, count)
    }

    @Test fun goalIsInSlowMotion() {
        val slow = DemoScript.steps.first { it.action == DemoAction.Speed(0.25) }
        val caption = DemoScript.steps.first { it.action == DemoAction.Caption("GOAL") }
        assertEquals(slow.at, caption.at, 0.0)
    }

    @Test fun whatIfIsEnabledOnlyWhilePaused() {
        val play = DemoScript.steps.first { it.action == DemoAction.Play(false) }
        val on = DemoScript.steps.first { it.action == DemoAction.WhatIf(true) }
        assertTrue(on.at >= play.at)
    }

    @Test fun yawBiasBounded() {
        for (i in 0..600) assertTrue(kotlin.math.abs(DemoScript.yawBias(i / 10.0)) <= 0.36f)
    }
}
