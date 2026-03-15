package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * Heavy crate that sits on platforms.  Can be pulled off a ledge by attaching
 * a web and swinging / pulling.  Falls with gravity, crushes enemies on contact.
 */
class Crate(
    startX: Float,
    startY: Float      // bottom-centre (ledge surface)
) {
    val size = 70f
    var pos = Vec2(startX, startY - size / 2)   // centre
    var vel = Vec2(0f, 0f)
    var onGround = false
    var crushed = false     // set true once it has squashed something (cosmetic)

    private val gravity = 2200f

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(130, 100, 60)
    }
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 150, 50); strokeWidth = 3f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 70, 40); style = Paint.Style.STROKE; strokeWidth = 3f
    }

    fun boundingRect(): RectF =
        RectF(pos.x - size / 2, pos.y - size / 2, pos.x + size / 2, pos.y + size / 2)

    fun update(dt: Float, platforms: List<Platform>) {
        if (crushed) return
        if (!onGround) {
            vel.y += gravity * dt
            pos.y += vel.y * dt

            // check landing
            onGround = false
            val myRect = boundingRect()
            for (plat in platforms) {
                val r = plat.rect
                if (!RectF.intersects(myRect, r)) continue
                val overlapTop = myRect.bottom - r.top
                val overlapBot = r.bottom - myRect.top
                val overlapL   = myRect.right - r.left
                val overlapR   = r.right - myRect.left
                val minOX = minOf(overlapL, overlapR)
                val minOY = minOf(overlapTop, overlapBot)
                if (minOY < minOX && overlapTop < overlapBot && overlapTop > 0) {
                    pos.y = r.top - size / 2
                    vel.y = 0f
                    onGround = true
                }
            }
        }
    }

    /** Check if this crate (while falling) crushes an enemy. */
    fun checkCrushEnemy(enemies: List<Enemy>): Boolean {
        if (vel.y < 200f) return false   // must be falling fast enough
        val myRect = boundingRect()
        for (enemy in enemies) {
            if (enemy.frozen && RectF.intersects(myRect, enemy.boundingRect())) {
                // only crushes frozen enemies (they can't dodge)
                return true
            }
            if (!enemy.frozen && RectF.intersects(myRect, enemy.boundingRect())) {
                enemy.freeze()   // un-frozen enemies just get knocked/frozen
                return false
            }
        }
        return false
    }

    /** Try to pull this crate sideways (by web). */
    fun pull(dirX: Float, strength: Float, dt: Float) {
        if (crushed) return
        pos.x += dirX * strength * dt
        // knocking it off its ledge
        onGround = false
    }

    fun draw(canvas: Canvas) {
        if (crushed) return
        val r = boundingRect()

        canvas.drawRect(r, bodyPaint)
        // diagonal warning stripes
        var sx = r.left
        while (sx < r.right + size) {
            canvas.drawLine(sx, r.top, sx - size * 0.4f, r.bottom, stripePaint)
            sx += 16f
        }
        canvas.drawRect(r, borderPaint)

        // "HEAVY" label
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(60, 40, 20); textSize = 14f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("HEAVY", pos.x, pos.y + 5f, textPaint)
    }
}
