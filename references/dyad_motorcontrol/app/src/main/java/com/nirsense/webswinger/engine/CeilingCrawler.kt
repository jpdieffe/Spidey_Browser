package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * Ceiling crawler – a crab-like enemy that patrols on ceilings.
 * Kills the player on contact. Can be frozen by a web hit.
 */
class CeilingCrawler(
    startX: Float,
    ceilingY: Float,             // bottom of ceiling surface
    private val leftBound: Float,
    private val rightBound: Float
) {
    val width  = 70f
    val height = 50f

    var pos = Vec2(startX, ceilingY + height / 2)   // centre
    var speed = 100f + (Math.random().toFloat() * 60f)
    var facingRight = Math.random() > 0.5

    var frozen = false; private set
    var cryoFrozen = false; private set
    private var freezeTimer = 0f
    private val freezeDuration = 11f

    private var walkCycle = 0f

    // paints
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(140, 60, 160) }
    private val legPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 40, 140); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    private val eyePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 200, 50) }
    private val frozenShell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 140, 200) }
    private val frozenLeg   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 120, 180); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    private val frozenEye   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 180, 255) }

    fun boundingRect(): RectF =
        RectF(pos.x - width / 2, pos.y - height / 2, pos.x + width / 2, pos.y + height / 2)

    fun update(dt: Float) {
        if (frozen) {
            freezeTimer -= dt
            if (freezeTimer <= 0f) { frozen = false; cryoFrozen = false; freezeTimer = 0f }
            return
        }

        val dir = if (facingRight) 1f else -1f
        pos.x += dir * speed * dt

        if (pos.x > rightBound - width / 2) { pos.x = rightBound - width / 2; facingRight = false }
        if (pos.x < leftBound + width / 2)  { pos.x = leftBound + width / 2; facingRight = true }

        walkCycle += dt * 10f
    }

    fun freeze() { frozen = true; cryoFrozen = false; freezeTimer = freezeDuration }
    fun freezeLong(duration: Float) { frozen = true; cryoFrozen = true; freezeTimer = duration }

    /** Solid ice block rect when frozen. */
    fun iceBlockRect(): RectF {
        val pad = 6f
        return RectF(pos.x - width / 2 - pad, pos.y - height / 2 - pad,
                     pos.x + width / 2 + pad, pos.y + height / 2 + pad)
    }

    fun draw(canvas: Canvas) {
        val cx = pos.x
        val cy = pos.y
        val hw = width / 2
        val hh = height / 2

        val sp = if (frozen) frozenShell else shellPaint
        val lp = if (frozen) frozenLeg else legPaint
        val ep = if (frozen) frozenEye else eyePaint

        // legs (3 per side, hanging down from ceiling)
        val legOff = if (frozen) 0f else Math.sin(walkCycle.toDouble()).toFloat() * 6f
        for (i in -1..1) {
            val lx = cx + i * 18f
            canvas.drawLine(lx, cy, lx - 8f + legOff * i, cy + hh + 4f, lp)
            canvas.drawLine(lx, cy, lx + 8f - legOff * i, cy + hh + 4f, lp)
        }

        // shell (oval body)
        val shellRect = RectF(cx - hw, cy - hh, cx + hw, cy + 6f)
        canvas.drawRoundRect(shellRect, hw, 14f, sp)

        // eyes
        val eyeDir = if (facingRight) 5f else -5f
        canvas.drawCircle(cx - 10f + eyeDir, cy - 2f, 5f, ep)
        canvas.drawCircle(cx + 10f + eyeDir, cy - 2f, 5f, ep)

        // pincers
        val pDir = if (facingRight) 1f else -1f
        canvas.drawLine(cx + pDir * hw, cy + 4f, cx + pDir * (hw + 14f), cy + hh + 2f, lp)
        canvas.drawLine(cx + pDir * hw, cy + 4f, cx + pDir * (hw + 8f), cy + hh + 8f, lp)

        // normal frozen sparkles
        if (frozen && !cryoFrozen) {
            val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 200, 230, 255) }
            val t = (freezeDuration - freezeTimer) * 4f
            for (i in 0..3) {
                val ang = t + i * 1.5f
                val sr = 16f + (i % 2) * 10f
                val sx = cx + sr * Math.cos(ang.toDouble()).toFloat()
                val sy = cy + sr * Math.sin(ang.toDouble()).toFloat()
                canvas.drawCircle(sx, sy, 2f, sparkPaint)
            }
        }
        // cryo ice block + sparkles
        if (cryoFrozen) {
            val iceRect = iceBlockRect()
            val iceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 140, 210, 255); style = Paint.Style.FILL }
            canvas.drawRoundRect(iceRect, 8f, 8f, iceFill)
            val iceBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 180, 230, 255); style = Paint.Style.STROKE; strokeWidth = 2.5f }
            canvas.drawRoundRect(iceRect, 8f, 8f, iceBorder)
            val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255); strokeWidth = 1.5f; style = Paint.Style.STROKE }
            canvas.drawLine(iceRect.left + 6f, iceRect.top + 8f, iceRect.left + iceRect.width() * 0.4f, iceRect.top + 5f, hlPaint)

            val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 200, 230, 255) }
            val t = (freezeDuration - freezeTimer) * 4f
            for (i in 0..3) {
                val ang = t + i * 1.5f
                val sr = 16f + (i % 2) * 10f
                val sx = cx + sr * Math.cos(ang.toDouble()).toFloat()
                val sy = cy + sr * Math.sin(ang.toDouble()).toFloat()
                canvas.drawCircle(sx, sy, 2f, sparkPaint)
            }
        }
    }
}
