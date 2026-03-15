package com.nirsense.webswinger.engine

import android.graphics.*

/** Exit door – player must reach this to complete the level. */
class Door(
    val x: Float,
    val y: Float     // bottom-centre (ground level)
) {
    val width = 60f
    val height = 90f
    private var glowTime = 0f

    fun update(dt: Float) { glowTime += dt * 2.5f }

    fun boundingRect(): RectF =
        RectF(x - width / 2, y - height, x + width / 2, y)

    fun draw(canvas: Canvas) {
        val cx = x
        val top = y - height

        // glow pulse
        val pulse = (Math.sin(glowTime.toDouble()).toFloat() * 0.3f + 0.7f)
        val glowA = (60 * pulse).toInt()
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(glowA, 100, 255, 100)
        }
        canvas.drawRect(cx - width / 2 - 8f, top - 8f, cx + width / 2 + 8f, y + 4f, glowPaint)

        // door frame
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(80, 60, 30)
        }
        canvas.drawRect(cx - width / 2, top, cx + width / 2, y, framePaint)

        // door face
        val doorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(50, 160, 50)
        }
        canvas.drawRect(cx - width / 2 + 5f, top + 5f, cx + width / 2 - 5f, y - 3f, doorPaint)

        // panels
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 130, 40); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        val pw = width - 16f
        val ph = (height - 12f) / 2 - 4f
        canvas.drawRect(cx - pw / 2, top + 8f, cx + pw / 2, top + 8f + ph, panelPaint)
        canvas.drawRect(cx - pw / 2, top + 16f + ph, cx + pw / 2, top + 16f + ph * 2, panelPaint)

        // handle
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 190, 60)
        }
        canvas.drawCircle(cx + width / 2 - 14f, y - height / 2, 4f, handlePaint)

        // EXIT text above
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 255, 100); textSize = 20f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("EXIT", cx, top - 10f, textPaint)
    }
}
