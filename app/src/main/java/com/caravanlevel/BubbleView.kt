package com.caravanlevel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws the bubble level, a "front" arrow and the raise/lower instructions.
 * rollDeg = side-to-side tilt (positive = right side high).
 * pitchDeg = front-to-back tilt (positive = front high).
 * The bubble moves toward the high side, like a real spirit level.
 */
class BubbleView(context: Context) : View(context) {

    companion object {
        const val LEVEL_DEG = 0.5f // degrees counted as "level"
    }

    var rollDeg = 0f   // live values, drive the bubble
    var pitchDeg = 0f
    var shownRoll = 0f // slowed-down values, drive the written figures
    var shownPitch = 0f
    var sideText = ""
    var frontText = ""
    var hint = ""
    var profileLabel = ""

    /** false = one round bubble for both directions; true = two straight vials, one per direction. */
    var separate = false

    private val maxDisplayDeg = 5f // tilt at which the bubble hits the edge
    private val d = resources.displayMetrics.density
    private val green = Colours.LEVEL_GREEN
    private val orange = Colours.LEVEL_ORANGE

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = if (Colours.dark) Color.DKGRAY else Color.LTGRAY
    }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Colours.TEXT
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rightText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = Colours.MUTED
    }
    private val arrow = Path()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Colours.BG)

        val w = width.toFloat()
        val cx = w / 2f

        // Arrow showing which way the top of the phone must point
        val ay = 20 * d
        arrow.reset()
        arrow.moveTo(cx, ay)
        arrow.lineTo(cx - 26 * d, ay + 36 * d)
        arrow.lineTo(cx - 10 * d, ay + 36 * d)
        arrow.lineTo(cx - 10 * d, ay + 64 * d)
        arrow.lineTo(cx + 10 * d, ay + 64 * d)
        arrow.lineTo(cx + 10 * d, ay + 36 * d)
        arrow.lineTo(cx + 26 * d, ay + 36 * d)
        arrow.close()
        canvas.drawPath(arrow, arrowPaint)

        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 18 * d
        text.color = Colours.TEXT
        canvas.drawText("FRONT", cx, ay + 90 * d, text)
        text.typeface = Typeface.DEFAULT
        text.textSize = 12 * d
        text.color = Colours.MUTED
        canvas.drawText("Point the top of the phone toward the front", cx, ay + 108 * d, text)

        rightText.textSize = 12 * d
        canvas.drawText(profileLabel, w - 12 * d, 22 * d, rightText)

        // Level
        val radius = min(w * 0.34f, 140 * d)
        val cy = ay + 130 * d + radius
        val bubbleR = radius * 0.14f

        val isLevel = abs(rollDeg) <= LEVEL_DEG && abs(pitchDeg) <= LEVEL_DEG
        bubble.color = if (isLevel) green else orange
        ring.color = if (isLevel) green else Colours.MUTED

        if (separate) {
            drawVials(canvas, w, cy, radius)
        } else {
            canvas.drawCircle(cx, cy, radius, ring)
            canvas.drawCircle(cx, cy, bubbleR * 1.6f, cross)
            canvas.drawLine(cx - radius, cy, cx + radius, cy, cross)
            canvas.drawLine(cx, cy - radius, cx, cy + radius, cross)

            val maxOffset = radius - bubbleR
            var dx = rollDeg / maxDisplayDeg * maxOffset
            var dy = -pitchDeg / maxDisplayDeg * maxOffset // screen y grows downward
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > maxOffset) {
                dx *= maxOffset / dist
                dy *= maxOffset / dist
            }
            canvas.drawCircle(cx + dx, cy + dy, bubbleR, bubble)
        }

        // Angles
        var y = cy + radius + 34 * d
        val rollSide = if (abs(shownRoll) < 0.05f) "" else if (shownRoll > 0) " (right high)" else " (left high)"
        val pitchSide = if (abs(shownPitch) < 0.05f) "" else if (shownPitch > 0) " (front high)" else " (rear high)"
        text.typeface = Typeface.DEFAULT
        text.textSize = 16 * d
        text.color = Colours.TEXT
        canvas.drawText("Side: %.1f°%s".format(abs(shownRoll), rollSide), cx, y, text)
        y += 24 * d
        canvas.drawText("Front/back: %.1f°%s".format(abs(shownPitch), pitchSide), cx, y, text)
        y += 42 * d

        // Instructions
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 22 * d
        text.color = orange
        if (sideText.isNotEmpty()) {
            canvas.drawText(sideText, cx, y, text)
            y += 32 * d
        }
        if (frontText.isNotEmpty()) {
            canvas.drawText(frontText, cx, y, text)
            y += 32 * d
        }
        if (abs(shownRoll) <= LEVEL_DEG && abs(shownPitch) <= LEVEL_DEG) {
            text.color = green
            text.textSize = 34 * d
            canvas.drawText("LEVEL", cx, y, text)
            y += 40 * d
        }
        if (hint.isNotEmpty()) {
            text.typeface = Typeface.DEFAULT
            text.textSize = 13 * d
            text.color = Colours.MUTED
            canvas.drawText(hint, cx, y, text)
        }
    }

    /**
     * Two spirit-level vials in the same space as the circle, one above the other: side to
     * side on top, front to back underneath (front on the right). Each bubble moves toward
     * the high side and turns green when that direction is level.
     */
    private fun drawVials(canvas: Canvas, w: Float, cy: Float, radius: Float) {
        val tube = 48 * d
        val bubbleR = tube * 0.36f
        val margin = 20 * d
        val left = margin
        val right = w - margin
        val mid = (left + right) / 2f
        val maxMove = (right - left) / 2f - tube / 2f

        text.typeface = Typeface.DEFAULT
        text.textSize = 13 * d

        fun vial(y: Float, deg: Float, title: String, lowEnd: String, highEnd: String) {
            val level = abs(deg) <= LEVEL_DEG
            ring.color = if (level) green else Colours.MUTED
            canvas.drawRoundRect(left, y - tube / 2f, right, y + tube / 2f, tube / 2f, tube / 2f, ring)
            canvas.drawLine(mid - bubbleR * 1.3f, y - tube / 2f, mid - bubbleR * 1.3f, y + tube / 2f, cross)
            canvas.drawLine(mid + bubbleR * 1.3f, y - tube / 2f, mid + bubbleR * 1.3f, y + tube / 2f, cross)
            val dx = (deg / maxDisplayDeg * maxMove).coerceIn(-maxMove, maxMove)
            bubble.color = if (level) green else orange
            canvas.drawCircle(mid + dx, y, bubbleR, bubble)

            text.color = Colours.MUTED
            val ty = y - tube / 2f - 8 * d
            canvas.drawText(title, mid, ty, text)
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(lowEnd, left + 4 * d, ty, text)
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText(highEnd, right - 4 * d, ty, text)
            text.textAlign = Paint.Align.CENTER
        }

        vial(cy - radius * 0.42f, rollDeg, "Side to side", "Left", "Right")
        vial(cy + radius * 0.58f, pitchDeg, "Front to back", "Back", "Front")
    }
}
