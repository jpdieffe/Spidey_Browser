package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * Visual explosion effect when BOOM_WEB hits an enemy.
 * Purely cosmetic – the enemy is removed by GameView logic.
 */
class Explosion(
    x: Float, y: Float,
    val radius: Float = 80f
) {
    val pos = Vec2(x, y)
    var time = 0f
    val duration = 0.6f
    val alive get() = time < duration

    // particle system
    private val particles = Array(16) {
        val angle = (it / 16f) * Math.PI.toFloat() * 2f + (Math.random().toFloat() * 0.4f)
        val speed = 150f + Math.random().toFloat() * 250f
        Particle(
            Vec2(x, y),
            Vec2(Math.cos(angle.toDouble()).toFloat() * speed,
                 Math.sin(angle.toDouble()).toFloat() * speed),
            6f + Math.random().toFloat() * 6f
        )
    }

    fun update(dt: Float) {
        time += dt
        for (p in particles) {
            p.pos.x += p.vel.x * dt
            p.pos.y += p.vel.y * dt
            p.vel.y += 400f * dt   // gravity on particles
            p.vel.x *= 0.97f
        }
    }

    fun draw(canvas: Canvas) {
        val progress = (time / duration).coerceIn(0f, 1f)
        val alpha = ((1f - progress) * 255).toInt()

        // shockwave ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((alpha * 0.4f).toInt(), 255, 200, 50)
            style = Paint.Style.STROKE
            strokeWidth = 4f * (1f - progress)
        }
        canvas.drawCircle(pos.x, pos.y, radius * progress * 1.5f, ringPaint)

        // inner flash
        if (progress < 0.3f) {
            val flashAlpha = ((1f - progress / 0.3f) * 200).toInt()
            val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(flashAlpha, 255, 255, 200)
            }
            canvas.drawCircle(pos.x, pos.y, radius * 0.6f * (1f - progress), flashPaint)
        }

        // particles
        for (p in particles) {
            val pAlpha = (alpha * 0.8f).toInt().coerceIn(0, 255)
            val pSize = p.size * (1f - progress * 0.7f)

            // orange-red core
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(pAlpha, 255, (100 + (155 * (1f - progress))).toInt().coerceIn(0, 255), 30)
            }
            canvas.drawCircle(p.pos.x, p.pos.y, pSize, corePaint)

            // bright center
            val brightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((pAlpha * 0.6f).toInt(), 255, 255, 150)
            }
            canvas.drawCircle(p.pos.x, p.pos.y, pSize * 0.4f, brightPaint)
        }

        // smoke puffs (slower, darker)
        if (progress > 0.2f) {
            val smokeAlpha = ((1f - progress) * 80).toInt()
            val smokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(smokeAlpha, 80, 80, 80)
            }
            for (i in 0..3) {
                val ang = i * 1.6f + time * 2f
                val sr = radius * progress * 0.8f
                val sx = pos.x + sr * Math.cos(ang.toDouble()).toFloat()
                val sy = pos.y - progress * 40f + sr * Math.sin(ang.toDouble()).toFloat() * 0.5f
                canvas.drawCircle(sx, sy, 8f + progress * 12f, smokePaint)
            }
        }
    }

    private class Particle(
        val pos: Vec2,
        val vel: Vec2,
        val size: Float
    )
}
