package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * Turret-style enemy that stands on the ground and periodically shoots
 * a projectile toward the player.  Can be frozen by a web hit.
 * Projectiles kill the player on contact.
 */
class ShooterEnemy(
    startX: Float,
    groundY: Float           // top of ground surface
) {
    val width  = 80f
    val height = 100f

    var pos = Vec2(startX, groundY - height / 2)   // centre
    var frozen = false; private set
    var cryoFrozen = false; private set
    private var freezeTimer = 0f
    private val freezeDuration = 11f

    // shooting
    val bullets = mutableListOf<Bullet>()
    private var shootTimer = 0f
    var shootInterval = 2.0f       // seconds between shots
    var bulletSpeed = 500f

    // paints
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 100, 120) }
    private val barrelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 70, 90); strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 120, 140) }
    private val eyePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 100, 30) }
    private val frozenBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 140, 200) }
    private val frozenBarrel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 120, 180); strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
    }
    private val frozenEye  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 180, 255) }

    private var aimAngle = 0f   // radians, for barrel direction

    fun boundingRect(): RectF =
        RectF(pos.x - width / 2, pos.y - height / 2, pos.x + width / 2, pos.y + height / 2)

    fun update(dt: Float, playerPos: Vec2) {
        if (frozen) {
            freezeTimer -= dt
            if (freezeTimer <= 0f) { frozen = false; cryoFrozen = false; freezeTimer = 0f }
            // bullets still fly while frozen
        } else {
            // aim toward player
            val dx = playerPos.x - pos.x
            val dy = playerPos.y - pos.y
            aimAngle = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()

            shootTimer += dt
            if (shootTimer >= shootInterval) {
                shootTimer = 0f
                // only shoot if player is within range (~800px)
                val dist = Math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
                if (dist < 900f) {
                    val bx = pos.x + Math.cos(aimAngle.toDouble()).toFloat() * 40f
                    val by = pos.y - height * 0.25f + Math.sin(aimAngle.toDouble()).toFloat() * 40f
                    bullets.add(Bullet(bx, by, aimAngle, bulletSpeed))
                }
            }
        }

        // update bullets
        val iter = bullets.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            b.update(dt)
            if (b.lifetime > 3f) iter.remove()   // despawn after 3s
        }
    }

    fun freeze() { frozen = true; cryoFrozen = false; freezeTimer = freezeDuration }
    fun freezeLong(duration: Float) { frozen = true; cryoFrozen = true; freezeTimer = duration }

    /** Solid ice block rect when frozen. */
    fun iceBlockRect(): RectF {
        val pad = 8f
        return RectF(pos.x - width / 2 - pad, pos.y - height / 2 - pad,
                     pos.x + width / 2 + pad, pos.y + height / 2 + pad)
    }

    fun draw(canvas: Canvas) {
        val cx = pos.x
        val cy = pos.y
        val hh = height / 2

        val bp = if (frozen) frozenBody else bodyPaint
        val brp = if (frozen) frozenBarrel else barrelPaint
        val ep = if (frozen) frozenEye else eyePaint

        // body (trapezoidal base)
        val base = RectF(cx - width / 2, cy, cx + width / 2, cy + hh)
        canvas.drawRoundRect(base, 6f, 6f, bp)

        // turret head
        val headR = 22f
        val headY = cy - 8f
        canvas.drawCircle(cx, headY, headR, if (frozen) frozenBody else headPaint)

        // barrel
        val barrelLen = 36f
        val bx = cx + Math.cos(aimAngle.toDouble()).toFloat() * barrelLen
        val by = headY + Math.sin(aimAngle.toDouble()).toFloat() * barrelLen
        canvas.drawLine(cx, headY, bx, by, brp)

        // eye
        canvas.drawCircle(cx, headY, 6f, ep)

        // warning triangle on body
        val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 200, 0); textSize = 22f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("⚠", cx, cy + hh - 10f, warnPaint)

        // draw bullets
        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (frozen) Color.rgb(100, 180, 255) else Color.rgb(255, 130, 30)
        }
        val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (frozen) Color.argb(80, 100, 180, 255) else Color.argb(80, 255, 130, 30)
        }
        for (b in bullets) {
            canvas.drawCircle(b.pos.x, b.pos.y, 6f, bulletPaint)
            canvas.drawCircle(b.pos.x - b.vel.x * 0.02f, b.pos.y - b.vel.y * 0.02f, 4f, trailPaint)
        }

        // normal frozen sparkles
        if (frozen && !cryoFrozen) {
            val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 200, 230, 255) }
            val t = (freezeDuration - freezeTimer) * 4f
            for (i in 0..4) {
                val ang = t + i * 1.2f
                val sr = 18f + (i % 3) * 10f
                val sx = cx + sr * Math.cos(ang.toDouble()).toFloat()
                val sy = cy + sr * Math.sin(ang.toDouble()).toFloat()
                canvas.drawCircle(sx, sy, 2f, sparkPaint)
            }
        }
        // cryo ice block + sparkles
        if (cryoFrozen) {
            val iceRect = iceBlockRect()
            val iceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 140, 210, 255); style = Paint.Style.FILL }
            canvas.drawRoundRect(iceRect, 10f, 10f, iceFill)
            val iceBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 180, 230, 255); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            canvas.drawRoundRect(iceRect, 10f, 10f, iceBorder)
            val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255); strokeWidth = 1.5f; style = Paint.Style.STROKE }
            canvas.drawLine(iceRect.left + 8f, iceRect.top + 12f, iceRect.left + iceRect.width() * 0.4f, iceRect.top + 8f, hlPaint)

            val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 200, 230, 255) }
            val t = (freezeDuration - freezeTimer) * 4f
            for (i in 0..4) {
                val ang = t + i * 1.2f
                val sr = 18f + (i % 3) * 10f
                val sx = cx + sr * Math.cos(ang.toDouble()).toFloat()
                val sy = cy + sr * Math.sin(ang.toDouble()).toFloat()
                canvas.drawCircle(sx, sy, 2f, sparkPaint)
            }
        }
    }

    /** Simple bullet projectile. */
    class Bullet(
        x: Float, y: Float,
        angle: Float,
        speed: Float
    ) {
        val pos = Vec2(x, y)
        val vel = Vec2(
            Math.cos(angle.toDouble()).toFloat() * speed,
            Math.sin(angle.toDouble()).toFloat() * speed
        )
        val radius = 6f
        var lifetime = 0f

        fun update(dt: Float) {
            pos.x += vel.x * dt
            pos.y += vel.y * dt
            lifetime += dt
        }

        fun boundingRect(): RectF =
            RectF(pos.x - radius, pos.y - radius, pos.x + radius, pos.y + radius)
    }
}
