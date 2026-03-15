package com.nirsense.webswinger.engine

import android.graphics.*
import android.graphics.RectF

/**
 * Robot enemy that patrols on the ground between two x-bounds.
 * Hit by a web projectile → frozen for 3 seconds.
 */
class Enemy(
    startX: Float,
    startY: Float,           // bottom-centre of sprite (ground level)
    private val leftBound: Float,
    private val rightBound: Float
) {
    // ---- dimensions ----
    val width  = 90f
    val height = 120f

    // ---- position / movement ----
    var pos = Vec2(startX, startY - height / 2)   // centre
    var speed = 120f + (Math.random().toFloat() * 80f)
    var facingRight = Math.random() > 0.5

    // ---- freeze state ----
    var frozen = false; private set
    var cryoFrozen = false; private set   // true only when frozen by cryo web
    private var freezeTimer = 0f
    private val freezeDuration = 11f

    // ---- paints ----
    private val bodyPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(180, 60, 60) }
    private val headPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 80, 80) }
    private val eyePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 50, 50) }
    private val limbPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(140, 50, 50); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val frozenTint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 180, 255) }
    private val frozenBody  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 140, 200) }
    private val frozenHead  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 160, 220) }
    private val frozenLimb  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 120, 180); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }

    // ---- animation ----
    private var walkCycle = 0f

    fun boundingRect(): RectF =
        RectF(pos.x - width / 2, pos.y - height / 2, pos.x + width / 2, pos.y + height / 2)

    // ================================================================
    //  UPDATE
    // ================================================================
    fun update(dt: Float, platforms: List<Platform> = emptyList()) {
        if (frozen) {
            freezeTimer -= dt
            if (freezeTimer <= 0f) { frozen = false; cryoFrozen = false; freezeTimer = 0f }
            return
        }

        // patrol
        val dir = if (facingRight) 1f else -1f
        pos.x += dir * speed * dt

        // reverse at bounds
        if (pos.x > rightBound - width / 2) { pos.x = rightBound - width / 2; facingRight = false }
        if (pos.x < leftBound + width / 2)  { pos.x = leftBound + width / 2; facingRight = true }

        // check wall collisions with platforms
        val myRect = boundingRect()
        for (plat in platforms) {
            val r = plat.rect
            if (!RectF.intersects(myRect, r)) continue
            val overlapLeft  = myRect.right - r.left
            val overlapRight = r.right - myRect.left
            val overlapTop   = myRect.bottom - r.top
            val overlapBot   = r.bottom - myRect.top
            val minOX = minOf(overlapLeft, overlapRight)
            val minOY = minOf(overlapTop, overlapBot)
            if (minOX < minOY) {
                // wall hit — reverse
                if (overlapLeft < overlapRight) {
                    pos.x = r.left - width / 2
                    facingRight = false
                } else {
                    pos.x = r.right + width / 2
                    facingRight = true
                }
            }
        }

        walkCycle += dt * 8f
    }

    fun freeze() {
        frozen = true; cryoFrozen = false
        freezeTimer = freezeDuration
    }

    fun freezeLong(duration: Float) {
        frozen = true; cryoFrozen = true
        freezeTimer = duration
    }

    /** Solid ice block rect when frozen – slightly larger than bounding rect. */
    fun iceBlockRect(): RectF {
        val pad = 8f
        return RectF(pos.x - width / 2 - pad, pos.y - height / 2 - pad,
                     pos.x + width / 2 + pad, pos.y + height / 2 + pad)
    }

    // ================================================================
    //  DRAW
    // ================================================================
    fun draw(canvas: Canvas) {
        val cx = pos.x
        val cy = pos.y
        val hw = width / 2
        val hh = height / 2

        val bPaint = if (frozen) frozenBody else bodyPaint
        val hPaint = if (frozen) frozenHead else headPaint
        val lPaint = if (frozen) frozenLimb else limbPaint
        val ePaint = if (frozen) frozenTint else eyePaint

        // legs
        val legSwing = if (frozen) 0f else Math.sin(walkCycle.toDouble()).toFloat() * 12f
        canvas.drawLine(cx - 12f, cy + 15f, cx - 18f - legSwing, cy + hh, lPaint)
        canvas.drawLine(cx + 12f, cy + 15f, cx + 18f + legSwing, cy + hh, lPaint)

        // body (rounded rect)
        val bodyRect = RectF(cx - hw * 0.6f, cy - hh * 0.35f, cx + hw * 0.6f, cy + 20f)
        canvas.drawRoundRect(bodyRect, 8f, 8f, bPaint)

        // arms
        val armSwing = if (frozen) 0f else Math.sin(walkCycle.toDouble()).toFloat() * 8f
        canvas.drawLine(cx - hw * 0.6f, cy - 5f, cx - hw - 4f, cy + 10f + armSwing, lPaint)
        canvas.drawLine(cx + hw * 0.6f, cy - 5f, cx + hw + 4f, cy + 10f - armSwing, lPaint)

        // head
        val headR = 16f
        canvas.drawCircle(cx, cy - hh * 0.35f - headR + 2f, headR, hPaint)

        // eyes (glow red, or blue when frozen)
        val eyeY = cy - hh * 0.35f - headR + 2f
        val eyeOff = if (facingRight) 3f else -3f
        canvas.drawCircle(cx - 5f + eyeOff, eyeY - 2f, 3.5f, ePaint)
        canvas.drawCircle(cx + 5f + eyeOff, eyeY - 2f, 3.5f, ePaint)

        // antenna
        canvas.drawLine(cx, cy - hh * 0.35f - headR * 2 + 2f, cx, cy - hh * 0.35f - headR * 2 - 10f, lPaint)
        canvas.drawCircle(cx, cy - hh * 0.35f - headR * 2 - 12f, 3f, ePaint)

        // frozen sparkles (always when frozen)
        if (frozen && !cryoFrozen) {
            val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 200, 230, 255) }
            val t = (freezeDuration - freezeTimer) * 4f
            for (i in 0..5) {
                val ang = t + i * 1.05f
                val sr = 20f + (i % 3) * 12f
                val sx = cx + sr * Math.cos(ang.toDouble()).toFloat()
                val sy = cy + sr * Math.sin(ang.toDouble()).toFloat() - 10f
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
            // frost highlight line
            val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255); strokeWidth = 1.5f; style = Paint.Style.STROKE }
            canvas.drawLine(iceRect.left + 8f, iceRect.top + 12f, iceRect.left + iceRect.width() * 0.4f, iceRect.top + 8f, hlPaint)
            canvas.drawLine(iceRect.left + 14f, iceRect.top + 20f, iceRect.left + iceRect.width() * 0.55f, iceRect.top + 16f, hlPaint)

            val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 200, 230, 255) }
            val t = (freezeDuration - freezeTimer) * 4f
            for (i in 0..5) {
                val ang = t + i * 1.05f
                val sr = 20f + (i % 3) * 12f
                val sx = cx + sr * Math.cos(ang.toDouble()).toFloat()
                val sy = cy + sr * Math.sin(ang.toDouble()).toFloat() - 10f
                canvas.drawCircle(sx, sy, 2f, sparkPaint)
            }
        }
    }
}
