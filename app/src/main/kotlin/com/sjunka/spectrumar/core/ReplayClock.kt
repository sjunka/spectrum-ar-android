package com.sjunka.spectrumar.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ReplayClock(var duration: Double) {
    var time: Double = 0.0
    var speed: Double = 1.0
    var playing: Boolean = true
    var loop: Boolean = true

    val progress: Double get() = if (duration > 0) time / duration else 0.0

    fun advance(dt: Double) {
        if (!playing) return
        time += dt * speed
        if (time >= duration) {
            if (loop) {
                time = if (duration > 0) time % duration else 0.0
            } else {
                time = duration
                playing = false
            }
        } else if (time < 0) {
            time = if (loop && duration > 0) duration + time % duration else 0.0
        }
    }

    fun seek(t: Double) {
        time = min(max(t, 0.0), duration)
    }

    fun togglePlay() {
        playing = !playing
    }

    fun cycleSpeed() {
        val i = SPEEDS.indexOf(nearest(speed))
        speed = SPEEDS[(i + 1) % SPEEDS.size]
    }

    @JvmName("snapSpeed")
    fun setSpeed(s: Double) {
        speed = nearest(s)
    }

    private fun nearest(s: Double): Double = SPEEDS.minByOrNull { abs(it - s) }!!

    companion object {
        val SPEEDS = listOf(0.25, 0.5, 1.0, 2.0, 4.0)
    }
}
