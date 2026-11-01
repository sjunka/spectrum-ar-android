package com.sjunka.spectrumar.core

import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    operator fun div(s: Float) = Vec2(x / s, y / s)
    operator fun unaryMinus() = Vec2(-x, -y)

    fun lengthSq(): Float = x * x + y * y
    fun length(): Float = sqrt(lengthSq())
    fun distanceTo(o: Vec2): Float = (this - o).length()
    fun dot(o: Vec2): Float = x * o.x + y * o.y

    companion object {
        val ZERO = Vec2(0f, 0f)
    }
}
