package com.nirsense.webswinger.engine

import android.graphics.RectF

/**
 * A rectangular platform in the world. The player can stand on top,
 * collide with its sides, or attach a web to it.
 */
data class Platform(val rect: RectF) {
    /** True if this platform is a wall (taller than wide). */
    val isWall: Boolean get() = rect.height() > rect.width()
}
