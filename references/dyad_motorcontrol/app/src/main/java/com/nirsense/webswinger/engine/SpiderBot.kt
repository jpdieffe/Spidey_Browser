package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * A small robot spider deployed by the player.
 * Runs along the ground, freezing any enemy it contacts.
 * Has a limited lifetime before it self-destructs.
 */
class SpiderBot(
    startX: Float,
    groundY: Float,           // top of ground
    private val goRight: Boolean
) {
    val width = 40f
    val height = 30f

    var pos = Vec2(startX, groundY - height / 2)
    var speed = 280f
    var alive = true
    var lifetime = 0f
    private val maxLifetime = 12f   // self-destructs after 12 seconds
    private var onGround = false
    private val gravity = 2200f
    private var velY = 0f

    private var legCycle = 0f
    private var sparkleTime = 0f

    // paints
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(160, 80, 200) }
    private val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(130, 50, 170); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 255, 200) }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 160, 80, 200) }

    fun boundingRect(): RectF =
        RectF(pos.x - width / 2, pos.y - height / 2, pos.x + width / 2, pos.y + height / 2)

    fun update(dt: Float, platforms: List<Platform>) {
        if (!alive) return
        lifetime += dt
        if (lifetime > maxLifetime) { alive = false; return }

        // gravity – fall until on ground
        if (!onGround) {
            velY += gravity * dt
            pos.y += velY * dt
            val myRect = boundingRect()
            for (plat in platforms) {
                val r = plat.rect
                if (!RectF.intersects(myRect, r)) continue
                val overlapT = myRect.bottom - r.top
                val overlapB = r.bottom - myRect.top
                if (overlapT in 0f..overlapB) {
                    pos.y = r.top - height / 2
                    velY = 0f
                    onGround = true
                    break
                }
            }
            return  // don't patrol while falling
        }

        val dir = if (goRight) 1f else -1f
        pos.x += dir * speed * dt
        legCycle += dt * 16f
        sparkleTime += dt * 6f

        // check if still on ground (edge detection)
        onGround = false
        val footCheck = RectF(pos.x - width / 2, pos.y + height / 2 - 2f,
                              pos.x + width / 2, pos.y + height / 2 + 6f)
        for (plat in platforms) {
            if (RectF.intersects(footCheck, plat.rect)) { onGround = true; break }
        }

        // check wall collisions -> reverse direction
        val myRect = boundingRect()
        for (plat in platforms) {
            val r = plat.rect
            if (!RectF.intersects(myRect, r)) continue
            val overlapL = myRect.right - r.left
            val overlapR = r.right - myRect.left
            val overlapT = myRect.bottom - r.top
            val overlapB = r.bottom - myRect.top
            val minOX = minOf(overlapL, overlapR)
            val minOY = minOf(overlapT, overlapB)
            if (minOX < minOY) {
                // hit a wall – bounce back
                speed = -speed
                if (overlapL < overlapR) pos.x = r.left - width / 2
                else pos.x = r.right + width / 2
            }
        }
    }

    /**
     * Check if the spider touches any enemy and freeze them (30s).
     * Returns true if it froze something (spider keeps going).
     */
    fun checkFreezeEnemies(
        enemies: List<Enemy>,
        ceilingCrawlers: List<CeilingCrawler>,
        shooterEnemies: List<ShooterEnemy>
    ): Int {
        if (!alive) return 0
        var count = 0
        val myRect = boundingRect()
        for (e in enemies) {
            if (e.frozen) continue
            if (RectF.intersects(myRect, e.boundingRect())) {
                e.freeze(); count++
            }
        }
        // Spider bots are on the ground so they won't normally reach ceiling crawlers,
        // but check anyway in case ceiling is low
        for (cc in ceilingCrawlers) {
            if (cc.frozen) continue
            if (RectF.intersects(myRect, cc.boundingRect())) {
                cc.freeze(); count++
            }
        }
        for (se in shooterEnemies) {
            if (se.frozen) continue
            if (RectF.intersects(myRect, se.boundingRect())) {
                se.freeze(); count++
            }
        }
        return count
    }

    fun draw(canvas: Canvas) {
        if (!alive) return
        val cx = pos.x
        val cy = pos.y
        val hw = width / 2
        val hh = height / 2

        // glow
        canvas.drawCircle(cx, cy, width * 0.6f, glowPaint)

        // legs (4 per side)
        val legSwing = Math.sin(legCycle.toDouble()).toFloat() * 5f
        for (i in -1..1) {
            val lx = cx + i * 10f
            canvas.drawLine(lx, cy + 2f, lx - 8f + legSwing * i, cy + hh + 3f, legPaint)
            canvas.drawLine(lx, cy + 2f, lx + 8f - legSwing * i, cy + hh + 3f, legPaint)
        }

        // body (oval)
        val bodyRect = RectF(cx - hw, cy - hh * 0.6f, cx + hw, cy + hh * 0.4f)
        canvas.drawRoundRect(bodyRect, hw, 10f, bodyPaint)

        // eyes
        val eyeOff = if (speed > 0) 4f else -4f
        canvas.drawCircle(cx - 6f + eyeOff, cy - 4f, 3.5f, eyePaint)
        canvas.drawCircle(cx + 6f + eyeOff, cy - 4f, 3.5f, eyePaint)

        // energy sparkles
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 160, 80, 200) }
        for (i in 0..2) {
            val ang = sparkleTime + i * 2.1f
            val sr = 12f + i * 4f
            val sx = cx + sr * Math.cos(ang.toDouble()).toFloat()
            val sy = cy + sr * Math.sin(ang.toDouble()).toFloat()
            canvas.drawCircle(sx, sy, 1.5f, sp)
        }

        // fading indicator near end of life
        if (lifetime > maxLifetime - 3f) {
            val blink = ((maxLifetime - lifetime) * 4f).toInt() % 2 == 0
            if (blink) {
                val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(150, 255, 100, 100); textSize = 16f; textAlign = Paint.Align.CENTER
                }
                canvas.drawText("!", cx, cy - hh - 4f, warnPaint)
            }
        }
    }
}
