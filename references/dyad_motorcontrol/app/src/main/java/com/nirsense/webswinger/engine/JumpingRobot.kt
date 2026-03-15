package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * A ground-patrolling robot that periodically jumps high into the air.
 * Harder to avoid than regular enemies. Freezable by web hit.
 */
class JumpingRobot(
    startX: Float,
    startY: Float,           // ground level (bottom)
    private val leftBound: Float,
    private val rightBound: Float
) {
    val width = 70f
    val height = 90f

    var pos = Vec2(startX, startY - height / 2)
    var vel = Vec2(0f, 0f)
    var speed = 100f + Math.random().toFloat() * 60f
    var facingRight = Math.random() > 0.5
    val gravity = 2200f

    // freeze state
    var frozen = false; private set
    var cryoFrozen = false; private set
    private var freezeTimer = 0f
    private val freezeDuration = 11f

    // jump state
    private var onGround = true
    private var jumpTimer = 1.5f + Math.random().toFloat() * 2f
    private val jumpInterval = 2f + Math.random().toFloat() * 1.5f
    private val jumpVel = -1200f - Math.random().toFloat() * 300f

    // animation
    private var legCycle = 0f
    private var squashTimer = 0f
    private var springGlow = 0f

    // paints
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 160, 80) }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 180, 100) }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 255, 80) }
    private val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 130, 60); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
    }
    private val springPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 200, 50); strokeWidth = 4f; style = Paint.Style.STROKE
    }
    private val frozenBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 140, 200) }
    private val frozenHead = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 160, 220) }
    private val frozenLimb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 120, 180); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
    }
    private val frozenEye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 180, 255) }

    fun boundingRect(): RectF =
        RectF(pos.x - width / 2, pos.y - height / 2, pos.x + width / 2, pos.y + height / 2)

    fun iceBlockRect(): RectF {
        val pad = 8f
        return RectF(pos.x - width / 2 - pad, pos.y - height / 2 - pad,
                     pos.x + width / 2 + pad, pos.y + height / 2 + pad)
    }

    fun update(dt: Float, platforms: List<Platform>) {
        if (frozen) {
            freezeTimer -= dt
            if (freezeTimer <= 0f) { frozen = false; cryoFrozen = false; freezeTimer = 0f }
            // still apply gravity when frozen in air
            if (!onGround) {
                vel.y += gravity * dt
                pos.y += vel.y * dt
                checkGroundCollisions(platforms)
            }
            return
        }

        springGlow += dt * 8f

        // patrol
        val dir = if (facingRight) 1f else -1f
        if (onGround) {
            vel.x = dir * speed
        }

        // jump timer
        if (onGround) {
            jumpTimer -= dt
            if (jumpTimer <= 0f) {
                vel.y = jumpVel
                onGround = false
                squashTimer = 0.15f
                jumpTimer = jumpInterval
            }
        }

        // gravity
        if (!onGround) {
            vel.y += gravity * dt
        }

        pos.x += vel.x * dt
        pos.y += vel.y * dt

        // reverse at bounds
        if (pos.x > rightBound - width / 2) { pos.x = rightBound - width / 2; facingRight = false }
        if (pos.x < leftBound + width / 2) { pos.x = leftBound + width / 2; facingRight = true }

        // ground collision
        checkGroundCollisions(platforms)

        if (squashTimer > 0f) squashTimer -= dt
        legCycle += dt * 8f
    }

    private fun checkGroundCollisions(platforms: List<Platform>) {
        val myRect = boundingRect()
        for (plat in platforms) {
            val r = plat.rect
            if (!RectF.intersects(myRect, r)) continue
            val overlapTop = myRect.bottom - r.top
            val overlapBot = r.bottom - myRect.top
            if (overlapTop in 0f..overlapBot && overlapTop < 40f) {
                pos.y = r.top - height / 2
                vel.y = 0f
                onGround = true
            }
            // wall bounce
            val overlapL = myRect.right - r.left
            val overlapR = r.right - myRect.left
            val minOX = minOf(overlapL, overlapR)
            val minOY = minOf(overlapTop, overlapBot)
            if (minOX < minOY) {
                if (overlapL < overlapR) { pos.x = r.left - width / 2; facingRight = false }
                else { pos.x = r.right + width / 2; facingRight = true }
            }
        }
    }

    fun freeze() { frozen = true; cryoFrozen = false; freezeTimer = freezeDuration }
    fun freezeLong(duration: Float) { frozen = true; cryoFrozen = true; freezeTimer = duration }

    fun draw(canvas: Canvas) {
        val cx = pos.x; val cy = pos.y
        val hw = width / 2; val hh = height / 2

        val bP = if (frozen) frozenBody else bodyPaint
        val hP = if (frozen) frozenHead else headPaint
        val lP = if (frozen) frozenLimb else limbPaint
        val eP = if (frozen) frozenEye else eyePaint

        // squash/stretch
        val scaleY = if (squashTimer > 0f) 0.8f else 1f
        val scaleX = if (squashTimer > 0f) 1.2f else 1f

        canvas.save()
        canvas.scale(scaleX, scaleY, cx, cy + hh)

        // spring legs
        val legSwing = if (frozen) 0f else Math.sin(legCycle.toDouble()).toFloat() * 8f
        val springY = cy + 10f
        // left spring coil
        for (i in 0..3) {
            val coilY = springY + i * 8f
            val coilX = cx - 15f + (if (i % 2 == 0) -4f else 4f)
            canvas.drawLine(coilX - 4f + legSwing, coilY, coilX + 4f + legSwing, coilY + 7f,
                if (frozen) frozenLimb else springPaint)
        }
        // right spring coil
        for (i in 0..3) {
            val coilY = springY + i * 8f
            val coilX = cx + 15f + (if (i % 2 == 0) -4f else 4f)
            canvas.drawLine(coilX - 4f - legSwing, coilY, coilX + 4f - legSwing, coilY + 7f,
                if (frozen) frozenLimb else springPaint)
        }

        // body (boxy robot)
        val bodyRect = RectF(cx - hw * 0.65f, cy - hh * 0.3f, cx + hw * 0.65f, cy + 15f)
        canvas.drawRoundRect(bodyRect, 6f, 6f, bP)

        // chest detail
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (frozen) Color.rgb(70, 120, 170) else Color.rgb(60, 130, 60)
            style = Paint.Style.STROKE; strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(cx - 12f, cy - 5f, cx + 12f, cy + 10f), 3f, 3f, detailPaint)

        // arms
        val armSwing = if (frozen) 0f else Math.sin(legCycle.toDouble()).toFloat() * 6f
        canvas.drawLine(cx - hw * 0.65f, cy - 2f, cx - hw - 2f, cy + 8f + armSwing, lP)
        canvas.drawLine(cx + hw * 0.65f, cy - 2f, cx + hw + 2f, cy + 8f - armSwing, lP)

        // head
        val headR = 14f
        canvas.drawCircle(cx, cy - hh * 0.3f - headR + 2f, headR, hP)

        // eyes
        val eyeY = cy - hh * 0.3f - headR + 2f
        val eyeOff = if (facingRight) 2f else -2f
        canvas.drawCircle(cx - 5f + eyeOff, eyeY - 1f, 3.5f, eP)
        canvas.drawCircle(cx + 5f + eyeOff, eyeY - 1f, 3.5f, eP)

        // spring glow when about to jump
        if (!frozen && onGround && jumpTimer < 0.5f) {
            val ga = (0.5f - jumpTimer) / 0.5f
            val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((ga * 120).toInt(), 255, 255, 80)
            }
            canvas.drawCircle(cx, cy + hh * 0.5f, 15f + ga * 10f, glowP)
        }

        canvas.restore()

        // cryo ice block
        if (cryoFrozen) {
            val iceRect = iceBlockRect()
            val iceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 140, 210, 255); style = Paint.Style.FILL }
            canvas.drawRoundRect(iceRect, 10f, 10f, iceFill)
            val iceBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 180, 230, 255); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            canvas.drawRoundRect(iceRect, 10f, 10f, iceBorder)
        }

        // frozen sparkles
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
    }
}
