package com.caravanlevel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/** Every site in the trip diary as a pin on an OpenStreetMap map. Tap a pin to see it. */
class SitesMapActivity : Activity() {
    companion object {
        /** Open to place one trip's pin: tap the map, then Use this spot. */
        const val EXTRA_PICK_TRIP = "pickTrip"
    }

    private lateinit var ui: Ui
    private var pickId: String? = null
    private lateinit var map: SiteMapView
    private lateinit var info: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        pickId = intent.getStringExtra(EXTRA_PICK_TRIP)
        map = SiteMapView(this)
        info = ui.card().apply { visibility = View.GONE }

        val zoomIn = ui.button("+") { map.zoomBy(1) }.apply { textSize = 26f }
        val zoomOut = ui.button("-") { map.zoomBy(-1) }.apply { textSize = 26f }
        val zooms = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(zoomIn, LinearLayout.LayoutParams(ui.dp(60), ui.dp(60)))
            addView(zoomOut, LinearLayout.LayoutParams(ui.dp(60), ui.dp(60)))
        }
        val frame = FrameLayout(this).apply {
            addView(map)
            addView(zooms, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.TOP; setMargins(0, ui.dp(8), ui.dp(8), 0)
            })
            addView(info, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM; setMargins(ui.dp(12), 0, ui.dp(12), ui.dp(28))
            })
        }
        val page = ui.column().apply {
            setBackgroundColor(Colours.BG)
            addView(ui.text(if (pickId != null) "Place the pin" else "Sites map", 24f, bold = true).apply { setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(8)) })
            addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        page.fitSystemBars()
        setContentView(page)
        Theme.barIcons(this)
        map.onPick = { show(it) }
        if (pickId != null) map.onTapPoint = { lat, lon -> map.marker = lat to lon; showPick() }
    }

    override fun onResume() {
        super.onResume()
        val pid = pickId
        if (pid != null) {
            val t = GarageStore.load(this).trips.firstOrNull { it.id == pid }
            if (map.marker == null && t != null && t.hasPlace) map.marker = t.lat!! to t.lon!!
            map.setTrips(emptyList(), Here.last(this))
            showPick()
            return
        }
        val trips = GarageStore.load(this).trips.filter { it.hasPlace }
        map.setTrips(trips, Here.last(this))
        info.visibility = View.GONE
    }

    private fun showPick() {
        info.removeAllViews()
        info.visibility = View.VISIBLE
        val m = map.marker
        info.addView(ui.text(if (m == null) "Tap the map where the site is. Drag and pinch to move around." else "Tap again to move the pin.", 17f))
        if (m != null) info.addView(ui.button("Use this spot", Colours.ACCENT) {
            val g = GarageStore.load(this)
            g.trips.firstOrNull { it.id == pickId }?.let { it.lat = m.first; it.lon = m.second; GarageStore.save(this, g) }
            setResult(RESULT_OK, Intent().putExtra("lat", m.first).putExtra("lon", m.second))
            finish()
        })
    }

    private fun show(t: Trip?) {
        info.removeAllViews()
        if (t == null) { info.visibility = View.GONE; return }
        info.visibility = View.VISIBLE
        info.addView(ui.text(t.site.ifBlank { "Trip" }, 20f, bold = true))
        info.addView(ui.text(listOf(Units.date(t.startMs), t.place).filter { it.isNotBlank() }.joinToString("  ·  "), 16f, Colours.MUTED))
        if (t.rating > 0) info.addView(Stars.row(this, ui, t.rating, 22, null))
        info.addView(ui.row(
            ui.rowButton("Open trip", Colours.ACCENT) {
                startActivity(Intent(this, DiaryEditActivity::class.java).putExtra(DiaryEditActivity.EXTRA_TRIP, t.id))
            },
            ui.rowButton("Directions") {
                val u = android.net.Uri.parse("geo:${t.lat},${t.lon}?q=${t.lat},${t.lon}(${android.net.Uri.encode(t.site)})")
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
            }
        ))
    }
}

/**
 * A small slippy map: OpenStreetMap tiles, dragged and pinched, with a pin per site.
 * Tiles are kept on the phone for 30 days so the map isn't fetched again each time.
 */
class SiteMapView(context: Context) : View(context) {
    var onPick: ((Trip?) -> Unit)? = null
    /** In "place the pin" mode: called with the tapped spot. */
    var onTapPoint: ((Double, Double) -> Unit)? = null
    /** A single pin being placed. */
    var marker: Pair<Double, Double>? = null
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private var trips: List<Trip> = emptyList()
    private var here: android.location.Location? = null
    private var zoom = 6
    // Centre of the view in world pixels at the current zoom
    private var cxW = 0.0
    private var cyW = 0.0
    private var fitted = false

    private val pin = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * d; typeface = Typeface.DEFAULT_BOLD }
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cache = LruCache<String, Bitmap>(80)
    private val loading = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())

    companion object {
        private val pool = Executors.newFixedThreadPool(2) // OpenStreetMap asks for no more than 2 at once
        private const val TILE = 256
        private const val MAX_AGE_MS = 30L * 24 * 3600 * 1000
    }

    fun setTrips(list: List<Trip>, me: android.location.Location?) {
        trips = list; here = me; fitted = false; invalidate()
    }

    private fun worldX(lon: Double, z: Int) = (lon + 180.0) / 360.0 * TILE * 2.0.pow(z)
    private fun worldY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * TILE * 2.0.pow(z)
    }
    private fun lonOf(x: Double, z: Int) = x / (TILE * 2.0.pow(z)) * 360.0 - 180.0
    private fun latOf(y: Double, z: Int): Double {
        val n = PI - 2 * PI * y / (TILE * 2.0.pow(z))
        return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    }

    /** Show every pin (or the UK if there are none). */
    private fun fit() {
        if (width == 0) return
        fitted = true
        marker?.let { centreOn(it.first, it.second, 16); return }
        val pts = trips.map { it.lat!! to it.lon!! }
        if (pts.isEmpty()) {
            here?.let { centreOn(it.latitude, it.longitude, if (onTapPoint != null) 14 else 10); return }
            centreOn(54.5, -3.0, 5); return
        }
        var z = 15
        while (z > 2) {
            val xs = pts.map { worldX(it.second, z) }; val ys = pts.map { worldY(it.first, z) }
            if (xs.max() - xs.min() < width - 120 * d && ys.max() - ys.min() < height - 220 * d) break
            z--
        }
        val lat = (pts.maxOf { it.first } + pts.minOf { it.first }) / 2
        val lon = (pts.maxOf { it.second } + pts.minOf { it.second }) / 2
        centreOn(lat, lon, if (pts.size == 1) 12 else z)
    }

    private fun centreOn(lat: Double, lon: Double, z: Int) {
        zoom = z; cxW = worldX(lon, z); cyW = worldY(lat, z); invalidate()
    }

    fun zoomBy(step: Int, fx: Float = width / 2f, fy: Float = height / 2f) {
        val nz = (zoom + step).coerceIn(2, 18)
        if (nz == zoom) return
        // Keep the point under the finger where it is
        val lat = latOf(cyW + (fy - height / 2f), zoom)
        val lon = lonOf(cxW + (fx - width / 2f), zoom)
        zoom = nz
        cxW = worldX(lon, zoom) - (fx - width / 2f)
        cyW = worldY(lat, zoom) - (fy - height / 2f)
        invalidate()
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            cxW += dx; cyW += dy; invalidate(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoomBy(1, e.x, e.y); return true }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTapPoint?.let { cb ->
                cb(latOf(cyW + (e.y - height / 2f), zoom), lonOf(cxW + (e.x - width / 2f), zoom)); return true
            }
            val hit = trips.minByOrNull { t -> dist(t, e.x, e.y) }?.takeIf { dist(it, e.x, e.y) < 36 * d }
            onPick?.invoke(hit); return true
        }
    })

    private var scaleAcc = 1f
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaleAcc = 1f; return true }
        override fun onScale(det: ScaleGestureDetector): Boolean {
            scaleAcc *= det.scaleFactor
            if (scaleAcc > 1.6f) { zoomBy(1, det.focusX, det.focusY); scaleAcc = 1f }
            if (scaleAcc < 0.62f) { zoomBy(-1, det.focusX, det.focusY); scaleAcc = 1f }
            return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (!scaler.isInProgress) gestures.onTouchEvent(e)
        return true
    }

    private fun screenX(lon: Double) = (worldX(lon, zoom) - cxW + width / 2.0).toFloat()
    private fun screenY(lat: Double) = (worldY(lat, zoom) - cyW + height / 2.0).toFloat()
    private fun dist(t: Trip, x: Float, y: Float): Float {
        val dx = screenX(t.lon!!) - x; val dy = screenY(t.lat!!) - y + 18 * d
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    override fun onDraw(c: Canvas) {
        if (!fitted) fit()
        c.drawColor(if (Colours.dark) 0xFF2A2F33.toInt() else 0xFFE8E4DC.toInt())
        val n = 1 shl zoom
        val left = cxW - width / 2.0
        val top = cyW - height / 2.0
        val tx0 = floor(left / TILE).toInt(); val tx1 = floor((left + width) / TILE).toInt()
        val ty0 = max(0, floor(top / TILE).toInt()); val ty1 = min(n - 1, floor((top + height) / TILE).toInt())
        for (ty in ty0..ty1) for (tx in tx0..tx1) {
            val wx = ((tx % n) + n) % n
            val bmp = tile(zoom, wx, ty) ?: continue
            val x = (tx * TILE - left).toFloat(); val y = (ty * TILE - top).toFloat()
            c.drawBitmap(bmp, null, android.graphics.RectF(x, y, x + TILE, y + TILE), tilePaint)
        }

        // You are here
        here?.let {
            pin.color = 0xFF1E6EC8.toInt()
            c.drawCircle(screenX(it.longitude), screenY(it.latitude), 9 * d, pin)
            pin.color = android.graphics.Color.WHITE
            c.drawCircle(screenX(it.longitude), screenY(it.latitude), 4 * d, pin)
        }

        // Site pins, best rated drawn last so they're on top
        trips.sortedBy { it.rating }.forEach { t ->
            val x = screenX(t.lon!!); val y = screenY(t.lat!!)
            pin.color = Colours.ACCENT
            c.drawCircle(x, y - 22 * d, 12 * d, pin)
            c.drawPath(android.graphics.Path().apply {
                moveTo(x - 9 * d, y - 16 * d); lineTo(x + 9 * d, y - 16 * d); lineTo(x, y); close()
            }, pin)
            pin.color = android.graphics.Color.WHITE
            c.drawCircle(x, y - 22 * d, 5 * d, pin)
            if (zoom >= 9 && t.site.isNotBlank()) {
                text.color = android.graphics.Color.BLACK
                text.setShadowLayer(4 * d, 0f, 0f, android.graphics.Color.WHITE)
                c.drawText(t.site, x + 14 * d, y - 18 * d, text)
            }
        }

        marker?.let { (lat, lon) ->
            val x = screenX(lon); val y = screenY(lat)
            pin.color = Colours.RED
            c.drawCircle(x, y - 22 * d, 12 * d, pin)
            c.drawPath(android.graphics.Path().apply {
                moveTo(x - 9 * d, y - 16 * d); lineTo(x + 9 * d, y - 16 * d); lineTo(x, y); close()
            }, pin)
            pin.color = android.graphics.Color.WHITE
            c.drawCircle(x, y - 22 * d, 5 * d, pin)
        }

        // Required credit for the map data
        text.clearShadowLayer()
        text.color = 0xFF333333.toInt()
        val credit = "© OpenStreetMap contributors"
        val w = text.measureText(credit)
        pin.color = 0xCCFFFFFF.toInt()
        c.drawRect(width - w - 12 * d, height - 20 * d, width.toFloat(), height.toFloat(), pin)
        c.drawText(credit, width - w - 6 * d, height - 6 * d, text)
    }

    private fun tile(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        cache.get(key)?.let { return it }
        if (key in loading) return null
        loading.add(key)
        val dir = File(context.cacheDir, "tiles")
        pool.execute {
            val f = File(dir, "$z-$x-$y.png")
            var bmp: Bitmap? = null
            try {
                if (f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS) bmp = BitmapFactory.decodeFile(f.path)
                if (bmp == null) {
                    val conn = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "CaravanLeveller (Android app; github.com/minisonmars/android-test)")
                    conn.connectTimeout = 10_000; conn.readTimeout = 15_000
                    if (conn.responseCode == 200) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        dir.mkdirs(); f.writeBytes(bytes)
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    conn.disconnect()
                }
            } catch (_: Exception) {}
            main.post {
                loading.remove(key)
                if (bmp != null) { cache.put(key, bmp); invalidate() }
            }
        }
        return null
    }

}
