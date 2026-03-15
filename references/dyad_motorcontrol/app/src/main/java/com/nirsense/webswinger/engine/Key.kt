package com.nirsense.webswinger.engine

import android.graphics.*

/** Collectible key – blue or red. */
class Key(
    x: Float, y: Float,
    val color: KeyColor
) {
    enum class KeyColor { BLUE, RED }

    var pos = Vec2(x, y)
    var collected = false
    val size = 36f

    private var bobTime = (Math.random() * Math.PI * 2).toFloat()

    fun update(dt: Float) {
        if (!collected) bobTime += dt * 3f
    }

    fun boundingRect(): RectF =
        RectF(pos.x - size / 2, pos.y - size / 2, pos.x + size / 2, pos.y + size / 2)

    fun draw(canvas: Canvas) {
        if (collected) return
        val bob = Math.sin(bobTime.toDouble()).toFloat() * 6f
        val cx = pos.x
        val cy = pos.y + bob

        val baseColor = when (color) {
            KeyColor.BLUE -> Color.rgb(60, 140, 255)
            KeyColor.RED  -> Color.rgb(255, 60, 60)
        }
        val glowColor = when (color) {
            KeyColor.BLUE -> Color.argb(60, 60, 140, 255)
            KeyColor.RED  -> Color.argb(60, 255, 60, 60)
        }

        // glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = glowColor }
        canvas.drawCircle(cx, cy, size * 0.8f, glowPaint)

        // key body
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = baseColor }
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = baseColor; alpha = 180
        }
        // ring
        canvas.drawCircle(cx - 4f, cy - 6f, 10f, paint)
        canvas.drawCircle(cx - 4f, cy - 6f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.rgb(30, 30, 40)
        })
        // shaft
        canvas.drawRect(cx - 2f, cy - 2f, cx + 14f, cy + 3f, paint)
        // teeth
        canvas.drawRect(cx + 8f, cy + 3f, cx + 12f, cy + 8f, darkPaint)
        canvas.drawRect(cx + 2f, cy + 3f, cx + 6f, cy + 6f, darkPaint)
    }
}
