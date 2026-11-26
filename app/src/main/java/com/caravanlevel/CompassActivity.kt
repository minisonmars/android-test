package com.caravanlevel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyManager
import kotlin.math.abs

/**
 * Compass with true north, where the sun rises and sets today, the nearest main TV mast,
 * the TV satellite (Freesat/Sky), and the phone's own mobile signal.
 */
class CompassActivity : Page(), SensorEventListener {
    override val section = Section.COMPASS

    companion object {
        private const val LOCATION = 1
        private const val SAT_COLOUR = 0xFF8E44AD.toInt()
    }

    private var compass: CompassView? = null
    private lateinit var sensors: SensorManager
    private var smoothed: Float? = null
    private var lowAccuracy = false
    private var location: Location? = null
    private var listener: LocationListener? = null
    private var signalView: android.widget.TextView? = null
    private var lastStrength: android.telephony.SignalStrength? = null
    private var displayType: Int? = null // TelephonyDisplayInfo override type (5G shown over 4G)
    private var phoneCallback: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        location = savedLocation()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        if (locationAllowed()) startLocation()
        startSignal()
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
        stopLocation()
        stopSignal()
    }

    // ---- Mobile signal, kept up to date while the page is open ----

    private fun startSignal() {
        if (Build.VERSION.SDK_INT < 31 || phoneCallback != null) return
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val cb = SignalCallback()
        try {
            tm.registerTelephonyCallback(mainExecutor, cb)
            phoneCallback = cb
        } catch (_: Exception) {
            // Some phones want phone-state permission for the 5G display info: just the signal then
            try {
                val only = SignalOnlyCallback()
                tm.registerTelephonyCallback(mainExecutor, only)
                phoneCallback = only
            } catch (_: Exception) {}
        }
    }

    private fun stopSignal() {
        if (Build.VERSION.SDK_INT < 31) return
        val cb = phoneCallback as? android.telephony.TelephonyCallback ?: return
        try { (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).unregisterTelephonyCallback(cb) } catch (_: Exception) {}
        phoneCallback = null
    }

    private fun showSignal() { signalView?.text = signalText() }

    @android.annotation.TargetApi(31)
    private inner class SignalCallback : android.telephony.TelephonyCallback(),
        android.telephony.TelephonyCallback.SignalStrengthsListener,
        android.telephony.TelephonyCallback.DisplayInfoListener {
        override fun onSignalStrengthsChanged(s: android.telephony.SignalStrength) { lastStrength = s; showSignal() }
        override fun onDisplayInfoChanged(info: android.telephony.TelephonyDisplayInfo) {
            displayType = info.overrideNetworkType; showSignal()
        }
    }

    @android.annotation.TargetApi(31)
    private inner class SignalOnlyCallback : android.telephony.TelephonyCallback(),
        android.telephony.TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(s: android.telephony.SignalStrength) { lastStrength = s; showSignal() }
    }

    // ---- Heading ----

    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val angles = FloatArray(3)

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, angles)
        // Held upright (looking at the screen like a camera), use the back of the phone instead
        val m = if (abs(Math.toDegrees(angles[1].toDouble())) > 50) {
            SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            SensorManager.getOrientation(remapped, angles); angles[0]
        } else angles[0]
        var deg = Math.toDegrees(m.toDouble()).toFloat()
        location?.let { deg += declination(it) } // magnetic north -> true north
        val prev = smoothed
        smoothed = if (prev == null) deg else {
            var diff = deg - prev
            while (diff > 180) diff -= 360
            while (diff < -180) diff += 360
            prev + diff * 0.15f
        }
        compass?.heading = smoothed!!
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val low = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        if (low != lowAccuracy) { lowAccuracy = low; rebuild() }
    }

    private fun declination(l: Location) =
        GeomagneticField(l.latitude.toFloat(), l.longitude.toFloat(), l.altitude.toFloat(), System.currentTimeMillis()).declination

    // ---- Location ----

    private fun locationAllowed() =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocation() {
        if (listener != null) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = LocationListener { got(it) }
        listener = l
        val providers = try { lm.getProviders(true) } catch (_: Exception) { emptyList<String>() }
        providers.forEach { p ->
            try { lm.getLastKnownLocation(p)?.let { got(it) } } catch (_: SecurityException) {}
            try { lm.requestLocationUpdates(p, 60_000L, 100f, l, Looper.getMainLooper()) } catch (_: Exception) {}
        }
    }

    private fun stopLocation() {
        val l = listener ?: return
        try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(l) } catch (_: Exception) {}
        listener = null
    }

    private fun got(l: Location) {
        val old = location
        if (old != null && old.time > l.time && old.distanceTo(l) < 1000) return
        location = l
        getSharedPreferences("compass", MODE_PRIVATE).edit()
            .putFloat("lat", l.latitude.toFloat()).putFloat("lon", l.longitude.toFloat()).putLong("time", l.time).apply()
        // Only redraw the page when the place really changed, so reading the page isn't disturbed
        if (old == null || old.distanceTo(l) > 500) rebuild()
    }

    private fun savedLocation(): Location? {
        val p = getSharedPreferences("compass", MODE_PRIVATE)
        if (!p.contains("lat")) return null
        return Location("saved").apply {
            latitude = p.getFloat("lat", 0f).toDouble(); longitude = p.getFloat("lon", 0f).toDouble(); time = p.getLong("time", 0L)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (locationAllowed()) startLocation()
        rebuild()
    }

    // ---- Page ----

    override fun build() {
        val l = location
        val sun = l?.let { Sun.day(it.latitude, it.longitude, System.currentTimeMillis()) }
        val showTv = shown("tv")
        val showSat = shown("sat")
        val masts = l?.let { Transmitters.nearest(this, it.latitude, it.longitude) } ?: emptyList()
        val sat = l?.let { Satellite.aim(it.latitude, it.longitude) }

        val view = CompassView(this).apply { heading = smoothed ?: 0f }
        view.markers = listOfNotNull(
            sun?.riseAz?.let { CompassView.Marker(it, "Rise", Colours.AMBER) },
            sun?.setAz?.let { CompassView.Marker(it, "Set", Colours.AMBER) },
            masts.firstOrNull()?.takeIf { showTv }?.let { CompassView.Marker(Geo.bearing(l!!.latitude, l.longitude, it.lat, it.lon), "TV", Colours.GREEN) },
            sat?.takeIf { showSat }?.let { CompassView.Marker(it.azimuth, "Sat", SAT_COLOUR) }
        )
        compass = view
        fixed.addView(view) // stays on screen while the text below scrolls
        content.addView(ui.text(
            "Lay the phone flat, or hold it upright facing the way you want to check. The arrow at the top is where the phone points. North is true north.",
            15f, Colours.MUTED
        ))
        if (sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) {
            content.addView(ui.text("This phone has no compass sensor, so the card can't turn. The directions below are still right.", 16f, Colours.RED))
        } else if (lowAccuracy) {
            content.addView(ui.text("The compass needs calibrating: move the phone in a figure of 8 a few times, away from metal and the car.", 16f, Colours.RED))
        }

        if (!locationAllowed() && l == null) {
            content.addView(ui.heading("Your location"))
            content.addView(ui.text("Sun, TV mast and satellite directions need to know roughly where you are. Your location stays on this phone.", 16f))
            content.addView(ui.button("Use my location", Colours.ACCENT) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION)
            })
        } else if (l == null) {
            content.addView(ui.text("Finding where you are... (turn on Location in the phone's quick settings if this doesn't change)", 16f, Colours.MUTED))
        } else if (!locationAllowed()) {
            content.addView(ui.text("Using the last place you used the compass. Allow location to update it.", 15f, Colours.MUTED))
        }

        // Sun
        if (sun != null) {
            content.addView(ui.heading("Sun today"))
            val card = ui.card()
            if (sun.riseMs == null) card.addView(ui.text("The sun doesn't rise or set here today.", 17f))
            else {
                card.addView(ui.text("Rises ${time(sun.riseMs)}, towards ${Geo.dirText(sun.riseAz!!)}", 18f))
                card.addView(ui.text("Sets ${time(sun.setMs!!)}, towards ${Geo.dirText(sun.setAz!!)}", 18f))
                card.addView(ui.text("Handy for where to put the awning or solar panel.", 15f, Colours.MUTED))
            }
            content.addView(card)
        }

        // TV mast
        if (l != null && masts.isNotEmpty()) {
            collapsible("TV aerial (Freeview)", "tv")
        }
        if (l != null && masts.isNotEmpty() && showTv) {
            val card = ui.card()
            masts.forEachIndexed { i, m ->
                val km = Geo.km(l.latitude, l.longitude, m.lat, m.lon)
                val dist = if (AppPrefs.km(this)) "%.0f km".format(km) else "%.0f miles".format(km / Units.KM_PER_MILE)
                card.addView(ui.text("${m.name}: ${Geo.dirText(Geo.bearing(l.latitude, l.longitude, m.lat, m.lon))}, $dist away",
                    if (i == 0) 18f else 16f, bold = i == 0))
            }
            card.addView(ui.text(
                "Main transmitters only, positions approximate. A nearer relay mast may be better: look at which way other aerials nearby point, or check your postcode online.",
                15f, Colours.MUTED
            ))
            content.addView(card)
        }

        // Satellite
        if (sat != null) {
            collapsible("Satellite dish (Freesat / Sky)", "sat")
        }
        if (sat != null && showSat) {
            val card = ui.card()
            card.addView(ui.text("Astra 2 satellites at 28.2° East", 16f, Colours.MUTED))
            card.addView(ui.text("Point it towards ${Geo.dirText(sat.azimuth)}", 18f, bold = true))
            card.addView(ui.text("Tilt it up %.1f° from level".format(sat.elevation), 18f, bold = true))
            card.addView(ui.text("LNB skew about %.0f°".format(abs(sat.skew)), 18f))
            card.addView(ui.text(
                "Your dish's own scale may already allow for its offset, so follow the dish's instructions for how to set the up angle. Trees and buildings in that direction will block the signal.",
                15f, Colours.MUTED
            ))
            if (sat.elevation < 5) card.addView(ui.text("The satellite is very low here, so reception may not be possible.", 16f, Colours.RED))
            content.addView(card)
        }

        // Mobile signal
        content.addView(ui.heading("Mobile signal"))
        val card = ui.card()
        val sig = ui.text(signalText(), 18f)
        signalView = sig
        card.addView(sig)
        card.addView(ui.text("Phones count bars in their own way, so this can differ a little from the status bar.", 14f, Colours.MUTED))
        card.addView(ui.button("Check coverage on Ofcom's map") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ofcom.org.uk/mobile-coverage-checker"))) } catch (_: Exception) {}
        })
        content.addView(card)
    }

    private fun shown(key: String) = getSharedPreferences("compass", MODE_PRIVATE).getBoolean("show_$key", true)

    /** A heading with a Hide/Show button; hiding also takes its marker off the compass. */
    private fun collapsible(title: String, key: String) {
        val on = shown(key)
        val h = ui.heading(title).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val b = ui.rowButton(if (on) "Hide" else "Show") {
            getSharedPreferences("compass", MODE_PRIVATE).edit().putBoolean("show_$key", !on).apply()
            rebuild()
        }.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(ui.dp(100), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        content.addView(ui.row(h, b))
    }

    private fun time(ms: Long) = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(ms))

    /** The phone's own signal right now, if Android will say. */
    private fun signalText(): String {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return "No mobile network on this device."
        val operator = tm.networkOperatorName?.takeIf { it.isNotBlank() } ?: "No network"
        if (Build.VERSION.SDK_INT < 28) return "Network: $operator"
        val s = lastStrength ?: try { tm.signalStrength } catch (_: Exception) { null } ?: return "Network: $operator"
        var bars = s.level // 0 to 4
        var type = ""
        if (Build.VERSION.SDK_INT >= 29) {
            val c = s.cellSignalStrengths
            // The status bar shows the best of the connections in use (4G and 5G together on "5G")
            c.maxOfOrNull { it.level }?.let { bars = maxOf(bars, it) }
            type = when {
                c.any { it is CellSignalStrengthNr } -> "5G"
                c.any { it is CellSignalStrengthLte } -> "4G"
                c.any { it is CellSignalStrengthWcdma } -> "3G"
                c.any { it is CellSignalStrengthGsm } -> "2G"
                else -> ""
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            type = when (displayType) {
                android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G"
                android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+"
                android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
                android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> if (type == "4G") "4G+" else type
                else -> type
            }
        }
        bars = bars.coerceIn(0, 4)
        val words = arrayOf("none or very poor", "poor", "fair", "good", "very good")[bars]
        return "$operator${if (type.isNotEmpty()) " $type" else ""}: $bars of 4 bars ($words)"
    }
}
