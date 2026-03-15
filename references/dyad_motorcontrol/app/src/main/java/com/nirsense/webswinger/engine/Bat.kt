package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * Bat enemy that flies toward the player. Can be killed by a normal web hit.
 * Bobs up and down while chasing, creating a menacing flight pattern.
 */
class Bat(
    startX: Float,
    startY: Float,
    private val patrolLeft: Float,
    private val patrolRight: Float
) {
    val width = 50f
    val height = 30f

    var pos = Vec2(startX, startY)
    var vel = Vec2(0f, 0f)
    var alive = true
    var chaseSpeed = 180f + Math.random().toFloat() * 60f
    private var homeY = startY

    // patrol state - chases player when nearby, otherwise patrols
    private var chaseRange = 500f
    private var patrolSpeed = 100f + Math.random().toFloat() * 40f
    private var patrolRight_ = true

    // animation
    private var wingCycle = (Math.random() * Math.PI * 2).toFloat()
    private var bobCycle = (Math.random() * Math.PI * 2).toFloat()
    private var eyeGlow = 0f

    // paints
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 30, 60) }
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 40, 80) }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 60, 30) }

    fun boundingRect(): RectF =
        RectF(pos.x - width / 2, pos.y - height / 2, pos.x + width / 2, pos.y + height / 2)

    fun update(dt: Float, playerPos: Vec2) {
        if (!alive) return

        wingCycle += dt * 14f
        bobCycle += dt * 3f
        eyeGlow += dt * 6f

        val dx = playerPos.x - pos.x
        val dy = playerPos.y - pos.y
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (dist < chaseRange) {
            // chase player
            if (dist > 1f) {
                val nx = dx / dist
                val ny = dy / dist
                vel.x += nx * chaseSpeed * 2f * dt
                vel.y += ny * chaseSpeed * 2f * dt
                // limit speed
                val spd = Math.sqrt((vel.x * vel.x + vel.y * vel.y).toDouble()).toFloat()
                if (spd > chaseSpeed) {
                    vel.x = vel.x / spd * chaseSpeed
                    vel.y = vel.y / spd * chaseSpeed
                }
            }
        } else {
            // patrol horizontally, bob vertically
            val patrolDir = if (patrolRight_) 1f else -1f
            vel.x = patrolDir * patrolSpeed
            val bobTarget = homeY + Math.sin(bobCycle.toDouble()).toFloat() * 40f
            vel.y = (bobTarget - pos.y) * 3f

            if (pos.x > patrolRight) patrolRight_ = false
            if (pos.x < patrolLeft) patrolRight_ = true
        }

        pos.x += vel.x * dt
        pos.y += vel.y * dt

        // keep in patrol bounds loosely
        pos.x = pos.x.coerceIn(patrolLeft - 100f, patrolRight + 100f)
    }

    fun draw(canvas: Canvas) {
        if (!alive) return
        val cx = pos.x
        val cy = pos.y
        val wingFlap = Math.sin(wingCycle.toDouble()).toFloat()

        // wings
        val wingSpan = width * 0.55f
        val wingAngle = wingFlap * 25f
        val leftWingTip = Vec2(cx - wingSpan, cy + wingAngle)
        val rightWingTip = Vec2(cx + wingSpan, cy + wingAngle)
        val leftMid = Vec2(cx - wingSpan * 0.5f, cy - 8f + wingAngle * 0.5f)
        val rightMid = Vec2(cx + wingSpan * 0.5f, cy - 8f + wingAngle * 0.5f)

        // wing paths
        val leftPath = Path().apply {
            moveTo(cx - 6f, cy - 4f)
            quadTo(leftMid.x, leftMid.y - 10f, leftWingTip.x, leftWingTip.y)
            quadTo(leftMid.x, leftMid.y + 5f, cx - 6f, cy + 2f)
            close()
        }
        val rightPath = Path().apply {
            moveTo(cx + 6f, cy - 4f)
            quadTo(rightMid.x, rightMid.y - 10f, rightWingTip.x, rightWingTip.y)
            quadTo(rightMid.x, rightMid.y + 5f, cx + 6f, cy + 2f)
            close()
        }
        canvas.drawPath(leftPath, wingPaint)
        canvas.drawPath(rightPath, wingPaint)

        // body (small oval)
        val bodyRect = RectF(cx - 10f, cy - 8f, cx + 10f, cy + 6f)
        canvas.drawOval(bodyRect, bodyPaint)

        // head
        canvas.drawCircle(cx, cy - 6f, 7f, bodyPaint)

        // ears (pointy)
        val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 35, 70) }
        canvas.drawLine(cx - 5f, cy - 12f, cx - 8f, cy - 22f, earPaint.apply { strokeWidth = 3f })
        canvas.drawLine(cx + 5f, cy - 12f, cx + 8f, cy - 22f, earPaint)

        // eyes (glowing red)
        val glowAlpha = (180 + 75 * Math.sin(eyeGlow.toDouble())).toInt().coerceIn(100, 255)
        val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(glowAlpha, 255, 60, 30) }
        canvas.drawCircle(cx - 4f, cy - 8f, 2.5f, ep)
        canvas.drawCircle(cx + 4f, cy - 8f, 2.5f, ep)

        // fangs
        val fangPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - 2f, cy - 1f, cx - 2f, cy + 4f, fangPaint)
        canvas.drawLine(cx + 2f, cy - 1f, cx + 2f, cy + 4f, fangPaint)
    }
}
