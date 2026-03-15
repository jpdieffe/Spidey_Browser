package com.nirsense.webswinger.engine

import android.graphics.*

/** A gate that blocks the player until the matching key is collected. */
class Gate(
    val rect: RectF,
    val requiredKey: Key.KeyColor
) {
    var open = false

    private val closedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f; style = Paint.Style.STROKE
    }

    fun draw(canvas: Canvas) {
        if (open) return
        val baseColor = when (requiredKey) {
            Key.KeyColor.BLUE -> Color.rgb(40, 100, 200)
            Key.KeyColor.RED  -> Color.rgb(200, 40, 40)
        }
        val barColor = when (requiredKey) {
            Key.KeyColor.BLUE -> Color.rgb(80, 160, 255)
            Key.KeyColor.RED  -> Color.rgb(255, 100, 80)
        }
        closedPaint.color = Color.argb(140, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        barPaint.color = barColor

        canvas.drawRect(rect, closedPaint)
        // draw vertical bars
        val barSpacing = 18f
        var bx = rect.left + barSpacing / 2
        while (bx < rect.right) {
            canvas.drawLine(bx, rect.top, bx, rect.bottom, barPaint)
            bx += barSpacing
        }
        // border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = barColor; style = Paint.Style.STROKE; strokeWidth = 3f
        }
        canvas.drawRect(rect, borderPaint)

        // lock icon in centre
        val cx = rect.centerX()
        val cy = rect.centerY()
        val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor }
        canvas.drawRect(cx - 10f, cy - 4f, cx + 10f, cy + 12f, lockPaint)
        canvas.drawArc(cx - 7f, cy - 16f, cx + 7f, cy, 180f, 180f, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = barColor; style = Paint.Style.STROKE; strokeWidth = 3f
            })
    }
}
