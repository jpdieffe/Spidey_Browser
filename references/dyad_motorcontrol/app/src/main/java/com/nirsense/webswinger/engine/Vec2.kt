package com.nirsense.webswinger.engine

/**
 * Simple 2D vector class used throughout the game engine.
 */
data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)

    fun length(): Float = Math.sqrt((x * x + y * y).toDouble()).toFloat()

    fun normalized(): Vec2 {
        val len = length()
        return if (len > 0.0001f) Vec2(x / len, y / len) else Vec2(0f, 0f)
    }

    fun dot(other: Vec2): Float = x * other.x + y * other.y

    fun set(nx: Float, ny: Float) { x = nx; y = ny }
    fun set(other: Vec2) { x = other.x; y = other.y }
}
