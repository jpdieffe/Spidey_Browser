package com.nirsense.webswinger.engine

/**
 * Web line: flight, attachment, and pendulum-swing state.
 *
 * Once attached the web acts as a fixed-length rope.
 * GameView runs the pendulum physics; this class stores rope state.
 */
class WebLine {
    enum class State { NONE, FLYING, ATTACHED }

    var state: State = State.NONE
    var origin = Vec2()
    var target = Vec2()
    var direction = Vec2()
    var attachPoint = Vec2()
    var speed = 2800f
    var maxLength = 2000f
    var traveled = 0f

    // ---- pendulum / rope ----
    var ropeLength = 0f            // current rope length (player can shorten / lengthen)
    var ropeAngle = 0f             // radians, 0 = straight down
    var ropeAngularVel = 0f        // rad / s
    val minRopeLength = 80f
    val maxRopeLength = 1200f
    val climbSpeed = 400f          // px / s rope shortening speed

    fun shoot(from: Vec2, dir: Vec2) {
        state = State.FLYING
        origin.set(from)
        target.set(from)
        direction.set(dir.normalized())
        traveled = 0f
    }

    fun release() {
        state = State.NONE
        ropeAngularVel = 0f
    }

    fun updateFlight(dt: Float): Boolean {
        if (state != State.FLYING) return false
        val step = speed * dt
        target.x += direction.x * step
        target.y += direction.y * step
        traveled += step
        if (traveled >= maxLength) { release(); return false }
        return true
    }

    fun attach(point: Vec2, playerPos: Vec2) {
        state = State.ATTACHED
        attachPoint.set(point)
        target.set(point)

        // initialise rope from current distance
        val dx = playerPos.x - point.x
        val dy = playerPos.y - point.y
        ropeLength = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            .coerceIn(minRopeLength, maxRopeLength)

        // angle: 0 = straight down from attach point
        ropeAngle = Math.atan2(dx.toDouble(), dy.toDouble()).toFloat()
        ropeAngularVel = 0f
    }
}
