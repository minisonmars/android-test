package com.caravanlevel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A compass card that turns so north points north. The line at the top is where the top of
 * the phone points. Markers round the edge show sunrise, sunset, the TV mast and the satellite.
 */
class CompassView(context: Context) : View(context) {
    class Marker(val deg: Double, val label: String, val colour: Int)

    /** Where the top of the phone points, degrees from true north. */
    var heading = 0f
        set(v) { field = v; invalidate() }
    var markers: List<Marker> = emptyList()
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, min(w, (360 * d).toInt()))
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 34 * d

        // Fixed pointer: where the phone is pointing
        fill.color = Colours.ACCENT
        c.drawPath(Path().apply {
            moveTo(cx, cy - r - 30 * d); lineTo(cx - 10 * d, cy - r - 12 * d); lineTo(cx + 10 * d, cy - r - 12 * d); close()
        }, fill)

        c.save()
        c.rotate(-heading, cx, cy)
        ring.color = Colours.MUTED
        ring.strokeWidth = 3 * d
        c.drawCircle(cx, cy, r, ring)
        for (i in 0 until 72) {
            val a = Math.toRadians(i * 5.0)
            val long = i % 6 == 0
            val inner = r - (if (long) 16f else 8f) * d
            ring.strokeWidth = (if (long) 3f else 1.5f) * d
            c.drawLine(cx + inner * sin(a).toFloat(), cy - inner * cos(a).toFloat(),
                cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat(), ring)
        }
        // Letters stay upright as the card turns
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 26 * d
        listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W").forEach { (deg, s) ->
            val a = Math.toRadians(deg.toDouble())
            val tr = r - 40 * d
            val x = cx + tr * sin(a).toFloat()
            val y = cy - tr * cos(a).toFloat()
            text.color = if (s == "N") Colours.RED else Colours.TEXT
            c.save(); c.rotate(heading, x, y)
            c.drawText(s, x, y + 9 * d, text)
            c.restore()
        }
        // Markers just outside the ring, with a line in from the edge
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 13 * d
        markers.forEach { m ->
            val a = Math.toRadians(m.deg)
            ring.color = m.colour
            ring.strokeWidth = 4 * d
            val sx = sin(a).toFloat(); val sy = cos(a).toFloat()
            c.drawLine(cx + (r - 60 * d) * sx, cy - (r - 60 * d) * sy, cx + r * sx, cy - r * sy, ring)
            fill.color = m.colour
            c.drawCircle(cx + r * sx, cy - r * sy, 7 * d, fill)
            val lx = cx + (r + 20 * d) * sx
            val ly = cy - (r + 20 * d) * sy
            text.color = m.colour
            c.save(); c.rotate(heading, lx, ly)
            c.drawText(m.label, lx, ly + 5 * d, text)
            c.restore()
        }
        c.restore()

        // Middle: the heading in figures
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 30 * d
        text.color = Colours.TEXT
        c.drawText("%.0f°".format(((heading % 360) + 360) % 360), cx, cy + 4 * d, text)
        text.typeface = Typeface.DEFAULT
        text.textSize = 16 * d
        text.color = Colours.MUTED
        c.drawText(Geo.point(heading.toDouble()), cx, cy + 28 * d, text)
    }
}
