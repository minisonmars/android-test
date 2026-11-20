package com.caravanlevel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws a bubble level. rollDeg = side-to-side tilt, pitchDeg = front-to-back tilt.
 * Positive roll = right side high, positive pitch = front (top of phone) high.
 * The bubble moves toward the high side, like a real spirit level.
 */
class BubbleView(context: Context) : View(context) {

    var rollDeg = 0f
    var pitchDeg = 0f

    private val levelThreshold = 0.5f // degrees counted as "level"
    private val maxDisplayDeg = 5f    // tilt at which bubble hits the edge

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.GRAY
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.DKGRAY
    }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 64f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        val cx = width / 2f
        val cy = height * 0.42f
        val radius = min(width, height) * 0.38f
        val bubbleR = radius * 0.14f

        val isLevel = abs(rollDeg) <= levelThreshold && abs(pitchDeg) <= levelThreshold
        bubble.color = if (isLevel) Color.rgb(60, 220, 90) else Color.rgb(255, 170, 0)
        ring.color = if (isLevel) Color.rgb(60, 220, 90) else Color.GRAY

        // Target ring and crosshair
        canvas.drawCircle(cx, cy, radius, ring)
        canvas.drawCircle(cx, cy, bubbleR * 1.6f, cross)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, cross)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, cross)

        // Bubble position, clamped inside the ring
        val maxOffset = radius - bubbleR
        var dx = rollDeg / maxDisplayDeg * maxOffset
        var dy = -pitchDeg / maxDisplayDeg * maxOffset // screen y grows downward
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > maxOffset) {
            dx *= maxOffset / dist
            dy *= maxOffset / dist
        }
        canvas.drawCircle(cx + dx, cy + dy, bubbleR, bubble)

        // Readouts
        text.textSize = 56f
        val rollSide = if (abs(rollDeg) < 0.05f) "" else if (rollDeg > 0) "  (right high)" else "  (left high)"
        val pitchSide = if (abs(pitchDeg) < 0.05f) "" else if (pitchDeg > 0) "  (front high)" else "  (rear high)"
        val y0 = cy + radius + 110f
        canvas.drawText("Side: %.1f°%s".format(abs(rollDeg), rollSide), cx, y0, text)
        canvas.drawText("Front/back: %.1f°%s".format(abs(pitchDeg), pitchSide), cx, y0 + 80f, text)
        text.textSize = 72f
        canvas.drawText(if (isLevel) "LEVEL" else "", cx, y0 + 190f, text)
    }
}
