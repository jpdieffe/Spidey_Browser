package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * Player-placed turret that automatically shoots at nearby enemies.
 * Has a limited lifetime and limited ammo.
 */
class WebTurret(
    x: Float,
    groundY: Float          // top of ground
) {
    val width = 60f
    val height = 70f

    var pos = Vec2(x, groundY - height / 2)
    var alive = true
    var lifetime = 0f
    private val maxLifetime = 15f
    private var ammo = 20
    private var shootTimer = 0f
    private val shootInterval = 0.6f
    private val bulletSpeed = 600f
    private val range = 700f
    private var onGround = false
    private val gravity = 2200f
    private var velY = 0f

    var aimAngle = 0f

    val bullets = mutableListOf<TurretBullet>()

    // paints
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 180, 60) }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 200, 80) }
    private val barrelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(50, 150, 50); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 255, 150) }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(30, 80, 255, 80) }

    fun boundingRect(): RectF =
        RectF(pos.x - width / 2, pos.y - height / 2, pos.x + width / 2, pos.y + height / 2)

    fun update(
        dt: Float,
        enemies: List<Enemy>,
        ceilingCrawlers: List<CeilingCrawler>,
        shooterEnemies: List<ShooterEnemy>,
        platforms: List<Platform>
    ) {
        if (!alive) return
        lifetime += dt
        if (lifetime > maxLifetime || ammo <= 0) { alive = false; return }

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
            return  // don't shoot while falling
        }

        // find nearest unfrozen enemy
        var nearestDist = Float.MAX_VALUE
        var nearestPos: Vec2? = null

        for (e in enemies) {
            if (e.frozen) continue
            val d = dist(pos, e.pos)
            if (d < range && d < nearestDist) { nearestDist = d; nearestPos = e.pos }
        }
        for (cc in ceilingCrawlers) {
            if (cc.frozen) continue
            val d = dist(pos, cc.pos)
            if (d < range && d < nearestDist) { nearestDist = d; nearestPos = cc.pos }
        }
        for (se in shooterEnemies) {
            if (se.frozen) continue
            val d = dist(pos, se.pos)
            if (d < range && d < nearestDist) { nearestDist = d; nearestPos = se.pos }
        }

        nearestPos?.let { target ->
            val dx = target.x - pos.x
            val dy = target.y - pos.y
            aimAngle = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()

            shootTimer += dt
            if (shootTimer >= shootInterval) {
                shootTimer = 0f
                ammo--
                val bx = pos.x + Math.cos(aimAngle.toDouble()).toFloat() * 30f
                val by = pos.y - height * 0.3f + Math.sin(aimAngle.toDouble()).toFloat() * 30f
                bullets.add(TurretBullet(bx, by, aimAngle, bulletSpeed))
            }
        }

        // update bullets
        val iter = bullets.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            b.update(dt)
            if (b.lifetime > 2.5f) { iter.remove(); continue }
            // remove if hit platform
            var hitPlat = false
            for (plat in platforms) {
                if (plat.rect.contains(b.pos.x, b.pos.y)) { hitPlat = true; break }
            }
            if (hitPlat) iter.remove()
        }
    }

    /**
     * Check turret bullets hitting enemies – freeze them.
     */
    fun checkBulletHits(
        enemies: List<Enemy>,
        ceilingCrawlers: List<CeilingCrawler>,
        shooterEnemies: List<ShooterEnemy>
    ) {
        val bIter = bullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            val br = b.boundingRect()
            var hit = false
            for (e in enemies) {
                if (e.frozen) continue
                if (RectF.intersects(br, e.boundingRect())) { e.freeze(); hit = true; break }
            }
            if (!hit) for (cc in ceilingCrawlers) {
                if (cc.frozen) continue
                if (RectF.intersects(br, cc.boundingRect())) { cc.freeze(); hit = true; break }
            }
            if (!hit) for (se in shooterEnemies) {
                if (se.frozen) continue
                if (RectF.intersects(br, se.boundingRect())) { se.freeze(); hit = true; break }
            }
            if (hit) bIter.remove()
        }
    }

    private fun dist(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    fun draw(canvas: Canvas) {
        if (!alive) return
        val cx = pos.x
        val cy = pos.y
        val hh = height / 2

        // glow
        canvas.drawCircle(cx, cy, width * 0.7f, glowPaint)

        // base
        val baseRect = RectF(cx - width / 2, cy + 5f, cx + width / 2, cy + hh)
        canvas.drawRoundRect(baseRect, 8f, 8f, basePaint)

        // head
        val headR = 18f
        val headY = cy - 6f
        canvas.drawCircle(cx, headY, headR, headPaint)

        // barrel
        val barrelLen = 28f
        val bx = cx + Math.cos(aimAngle.toDouble()).toFloat() * barrelLen
        val by = headY + Math.sin(aimAngle.toDouble()).toFloat() * barrelLen
        canvas.drawLine(cx, headY, bx, by, barrelPaint)

        // eye
        canvas.drawCircle(cx, headY, 5f, eyePaint)

        // ammo indicator
        val ammoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 255, 150); textSize = 16f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$ammo", cx, cy + hh + 16f, ammoPaint)

        // draw bullets
        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 255, 80) }
        val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 80, 255, 80) }
        for (b in bullets) {
            canvas.drawCircle(b.pos.x, b.pos.y, 5f, bulletPaint)
            canvas.drawCircle(b.pos.x - b.vel.x * 0.015f, b.pos.y - b.vel.y * 0.015f, 3f, trailPaint)
        }

        // fading warning near end of life
        if (lifetime > maxLifetime - 3f) {
            val blink = ((maxLifetime - lifetime) * 3f).toInt() % 2 == 0
            if (blink) {
                val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(150, 255, 200, 50); textSize = 20f; textAlign = Paint.Align.CENTER
                }
                canvas.drawText("⏱", cx, cy - hh - 8f, warnPaint)
            }
        }
    }

    class TurretBullet(
        x: Float, y: Float, angle: Float, speed: Float
    ) {
        val pos = Vec2(x, y)
        val vel = Vec2(
            Math.cos(angle.toDouble()).toFloat() * speed,
            Math.sin(angle.toDouble()).toFloat() * speed
        )
        val radius = 5f
        var lifetime = 0f

        fun update(dt: Float) {
            pos.x += vel.x * dt; pos.y += vel.y * dt; lifetime += dt
        }

        fun boundingRect(): RectF =
            RectF(pos.x - radius, pos.y - radius, pos.x + radius, pos.y + radius)
    }
}
