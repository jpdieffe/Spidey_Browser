package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * Lava hazard – red glowing terrain that kills the player on contact.
 * Animated with bubbling and glow effects.
 */
class Lava(val rect: RectF) {

    private var bubbleTime = (Math.random() * Math.PI * 2).toFloat()
    private var glowTime = 0f

    // pre-made bubble positions (random but deterministic per instance)
    private val bubbles = Array(6) {
        BubbleInfo(
            xFrac = Math.random().toFloat(),
            speed = 0.8f + Math.random().toFloat() * 1.2f,
            size = 3f + Math.random().toFloat() * 5f,
            phase = (Math.random() * Math.PI * 2).toFloat()
        )
    }

    fun update(dt: Float) {
        bubbleTime += dt
        glowTime += dt * 3f
    }

    fun draw(canvas: Canvas) {
        // base lava fill
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 40, 10); style = Paint.Style.FILL
        }
        canvas.drawRect(rect, basePaint)

        // darker underlayer
        val darkRect = RectF(rect.left, rect.top + rect.height() * 0.3f, rect.right, rect.bottom)
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(160, 20, 5); style = Paint.Style.FILL
        }
        canvas.drawRect(darkRect, darkPaint)

        // glowing surface line
        val surfaceGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val ga = (180 + 60 * Math.sin(glowTime.toDouble())).toInt().coerceIn(140, 240)
            color = Color.argb(ga, 255, 160, 30); strokeWidth = 3f; style = Paint.Style.STROKE
        }
        val path = Path()
        path.moveTo(rect.left, rect.top)
        val segments = 12
        val segW = rect.width() / segments
        for (i in 0..segments) {
            val x = rect.left + i * segW
            val wave = Math.sin((bubbleTime * 2f + i * 0.8f).toDouble()).toFloat() * 4f
            if (i == 0) path.moveTo(x, rect.top + wave)
            else path.lineTo(x, rect.top + wave)
        }
        canvas.drawPath(path, surfaceGlow)

        // bright hot spots
        val hotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 255, 200, 50)
        }
        for (i in 0..2) {
            val hx = rect.left + rect.width() * (0.2f + i * 0.3f)
            val hy = rect.top + 8f + Math.sin((glowTime + i * 1.5f).toDouble()).toFloat() * 3f
            canvas.drawCircle(hx, hy, 6f + Math.sin((glowTime * 2 + i).toDouble()).toFloat() * 3f, hotPaint)
        }

        // bubbles
        for (b in bubbles) {
            val bx = rect.left + b.xFrac * rect.width()
            val cycle = (bubbleTime * b.speed + b.phase) % 2f
            if (cycle < 1.2f) {
                val by = rect.top + rect.height() * 0.2f - cycle * rect.height() * 0.15f
                val alpha = ((1f - cycle / 1.2f) * 200).toInt().coerceIn(0, 200)
                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(alpha, 255, 140, 40)
                }
                canvas.drawCircle(bx, by, b.size * (1f - cycle / 2f), bp)
            }
        }

        // top glow aura
        val glowAura = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val ga = (60 + 30 * Math.sin(glowTime.toDouble())).toInt()
            color = Color.argb(ga, 255, 100, 20)
        }
        canvas.drawRect(rect.left, rect.top - 12f, rect.right, rect.top, glowAura)
    }

    private data class BubbleInfo(
        val xFrac: Float,
        val speed: Float,
        val size: Float,
        val phase: Float
    )
}
