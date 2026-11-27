package com.caravanlevel

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Android Auto: text-only screens (Android Auto doesn't let apps like this draw their own
 * pictures). The level and compass use the phone's sensors, so the phone must sit in a fixed
 * holder and be calibrated there once, with the vehicle on level ground.
 * Only shows in the car if Android Auto's developer setting "Unknown sources" is on.
 */
class CarService : CarAppService() {
    // Not from the Play Store, so any Android Auto host is accepted
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = CarHome(carContext)
    }
}

private fun vehicle(ctx: Context) = GarageStore.active(ctx, GarageStore.load(ctx))

class CarHome(ctx: CarContext) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val v = vehicle(carContext)
        fun row(title: String, text: String, open: () -> Screen) = Row.Builder().setTitle(title).addText(text)
            .setBrowsable(true).setOnClickListener { screenManager.push(open()) }.build()
        val list = ItemList.Builder()
            .addItem(row("Vehicle sizes", "Height, width, length and weights") { CarSizes(carContext) })
            .addItem(row("Level", "Which side and end to raise") { CarLevel(carContext) })
            .addItem(row("Compass", "Which way you're facing") { CarCompass(carContext) })
            .addItem(row("Checklists", "Tick off as you go") { CarChecklists(carContext) })
            .build()
        return ListTemplate.Builder().setTitle(v.name).setHeaderAction(Action.APP_ICON).setSingleList(list).build()
    }
}

/** Metres plus feet and inches, as UK height and width signs show both. */
private fun both(cm: Float): String {
    val inches = cm / 2.54f
    val ft = (inches / 12).toInt()
    val inch = Math.round(inches - ft * 12)
    return "%.2f m  (%d ft %d in)".format(cm / 100f, if (inch == 12) ft + 1 else ft, if (inch == 12) 0 else inch)
}

class CarSizes(ctx: CarContext) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val v = vehicle(carContext)
        val b = ItemList.Builder()
        fun add(title: String, text: String) { if (text.isNotBlank()) b.addItem(Row.Builder().setTitle(title).addText(text).build()) }
        add("Height", if (v.heightCm > 0) both(v.heightCm) else "")
        add("Width", if (v.widthCm > 0) both(v.widthCm) else "")
        add("Width with mirrors", if (v.mirrorWidthCm > 0) both(v.mirrorWidthCm) else "")
        add("Length", if (v.lengthCm > 0) both(v.lengthCm) else "")
        add(if (v.type == VehicleType.CAR) "Gross weight" else "MTPLM (max weight)",
            if (v.mtplmKg > 0) "${v.mtplmKg} kg  (%.2f t)".format(v.mtplmKg / 1000f) else "")
        add("Loaded weight", if (v.loadedKg > 0) "${v.loadedKg} kg" else "")
        val list = b.build()
        return ListTemplate.Builder().setTitle("${v.name} sizes").setHeaderAction(Action.BACK).apply {
            if (list.items.isEmpty()) setSingleList(ItemList.Builder().setNoItemsMessage("Add sizes in Vehicle info on the phone").build())
            else setSingleList(list)
        }.build()
    }
}

/**
 * The phone's tilt turned into the vehicle's tilt. Calibrating stores which way is "down"
 * with the phone in its holder; forward is taken as out of the back of the phone (phone
 * facing you, upright) or the top of the phone (lying flat).
 */
object CarTilt {
    private const val PREFS = "carlevel"

    fun calibrated(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("ux")

    fun calibrate(ctx: Context, up: FloatArray) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("ux", up[0]).putFloat("uy", up[1]).putFloat("uz", up[2]).apply()
    }

    private fun unit(a: FloatArray): FloatArray {
        val n = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).takeIf { it > 0f } ?: 1f
        return floatArrayOf(a[0] / n, a[1] / n, a[2] / n)
    }
    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: FloatArray, b: FloatArray) =
        floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    /** Vehicle forward and right in phone coordinates, from the calibrated "up". */
    fun axes(ctx: Context): Pair<FloatArray, FloatArray>? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("ux")) return null
        val up = unit(floatArrayOf(p.getFloat("ux", 0f), p.getFloat("uy", 0f), p.getFloat("uz", 1f)))
        // upright phone facing the driver: forward = out of its back (-z); flat phone: its top (+y)
        val raw = if (abs(up[2]) < 0.7f) floatArrayOf(0f, 0f, -1f) else floatArrayOf(0f, 1f, 0f)
        val d = dot(raw, up)
        val fwd = unit(floatArrayOf(raw[0] - d * up[0], raw[1] - d * up[1], raw[2] - d * up[2]))
        return fwd to unit(cross(fwd, up))
    }

    /** Roll (+ = left low) and pitch (+ = front high) in degrees. */
    fun angles(ctx: Context, gravity: FloatArray): Pair<Float, Float>? {
        val (fwd, right) = axes(ctx) ?: return null
        val u = unit(gravity)
        val roll = Math.toDegrees(asin(dot(u, right).coerceIn(-1f, 1f).toDouble())).toFloat()
        val pitch = Math.toDegrees(asin(dot(u, fwd).coerceIn(-1f, 1f).toDouble())).toFloat()
        return roll to pitch
    }
}

/** Listens to a sensor while the screen is showing and redraws now and then. */
abstract class SensorScreen(ctx: CarContext, private val type: Int) : Screen(ctx), SensorEventListener {
    protected var values = FloatArray(3)
    protected var has = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { if (has) invalidate(); handler.postDelayed(this, 1500) }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val sm = carContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                sm.getDefaultSensor(type)?.let { sm.registerListener(this@SensorScreen, it, SensorManager.SENSOR_DELAY_UI) }
                handler.postDelayed(tick, 1500)
            }
            override fun onStop(owner: LifecycleOwner) {
                (carContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this@SensorScreen)
                handler.removeCallbacks(tick)
            }
        })
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (!has) { values = e.values.copyOf(3); has = true; return }
        for (i in 0..2) values[i] = values[i] * 0.85f + e.values[i] * 0.15f // smooth
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

class CarLevel(ctx: CarContext) : SensorScreen(ctx, Sensor.TYPE_ACCELEROMETER) {
    override fun onGetTemplate(): Template {
        val v = vehicle(carContext)
        val pane = Pane.Builder()
        val angles = if (has) CarTilt.angles(carContext, values) else null
        when {
            !CarTilt.calibrated(carContext) -> {
                pane.addRow(Row.Builder().setTitle("Calibrate in the holder first")
                    .addText("Park on level ground with the phone in its holder, then tap Calibrate.").build())
            }
            angles == null -> pane.setLoading(true)
            else -> {
                val (roll, pitch) = angles
                val r = LevelText.work(v, roll, pitch, AppPrefs.inches(carContext))
                val level = abs(roll) <= BubbleView.LEVEL_DEG && abs(pitch) <= BubbleView.LEVEL_DEG
                pane.addRow(Row.Builder().setTitle("Side to side").addText(r.side.ifBlank { "Level" }).build())
                pane.addRow(Row.Builder().setTitle("Front to back").addText(r.front.ifBlank { "Level" }).build())
                pane.addRow(Row.Builder().setTitle(if (level) "Level" else "Angles")
                    .addText("Side %.1f°, front %s %.1f°".format(abs(roll), if (pitch >= 0) "high" else "low", abs(pitch))).build())
            }
        }
        pane.addAction(Action.Builder().setTitle("Calibrate").setOnClickListener {
            if (has) {
                CarTilt.calibrate(carContext, values)
                CarToast.makeText(carContext, "Calibrated: this is now level", CarToast.LENGTH_LONG).show()
                invalidate()
            }
        }.build())
        return PaneTemplate.Builder(pane.build()).setTitle("Level: ${v.name}").setHeaderAction(Action.BACK).build()
    }
}

class CarCompass(ctx: CarContext) : SensorScreen(ctx, Sensor.TYPE_ROTATION_VECTOR) {
    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
        val axes = CarTilt.axes(carContext)
        if (!has || axes == null) {
            if (axes == null) pane.addRow(Row.Builder().setTitle("Calibrate the level first")
                .addText("The compass uses the same calibration to know which way the vehicle points.").build())
            else pane.setLoading(true)
        } else {
            // vehicle forward in world terms: rotation matrix maps phone -> east/north/up
            val m = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(m, values)
            val f = axes.first
            val east = m[0] * f[0] + m[1] * f[1] + m[2] * f[2]
            val north = m[3] * f[0] + m[4] * f[1] + m[5] * f[2]
            var heading = Math.toDegrees(atan2(east.toDouble(), north.toDouble()))
            val here = Here.last(carContext)
            if (here != null) {
                heading += GeomagneticField(here.latitude.toFloat(), here.longitude.toFloat(),
                    here.altitude.toFloat(), System.currentTimeMillis()).declination
            }
            pane.addRow(Row.Builder().setTitle("Facing " + Geo.dirText(heading))
                .addText(if (here != null) "True north" else "Magnetic north (no location yet)").build())
            if (here != null) {
                val sky = Sun.day(here.latitude, here.longitude, System.currentTimeMillis())
                val bits = listOfNotNull(sky.riseAz?.let { "Sunrise " + Geo.dirText(it) }, sky.setAz?.let { "Sunset " + Geo.dirText(it) })
                if (bits.isNotEmpty()) pane.addRow(Row.Builder().setTitle("Sun").addText(bits.joinToString(", ")).build())
                val sat = Satellite.aim(here.latitude, here.longitude)
                pane.addRow(Row.Builder().setTitle("Astra 28.2E dish")
                    .addText("Point " + Geo.dirText(sat.azimuth) + ", up %.1f°".format(sat.elevation)).build())
            }
            pane.addRow(Row.Builder().setTitle("Tip").addText("A compass can be pulled off by the vehicle's metal and electrics.").build())
        }
        return PaneTemplate.Builder(pane.build()).setTitle("Compass").setHeaderAction(Action.BACK).build()
    }
}

class CarChecklists(ctx: CarContext) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val v = vehicle(carContext)
        val b = ItemList.Builder().setNoItemsMessage("No checklists yet")
        v.checklists.forEach { c ->
            val done = c.items.count { it.ticked }
            b.addItem(Row.Builder().setTitle(c.name).addText("$done of ${c.items.size} done").setBrowsable(true)
                .setOnClickListener { screenManager.push(CarChecklist(carContext, c.id)) }.build())
        }
        return ListTemplate.Builder().setTitle("Checklists").setHeaderAction(Action.BACK).setSingleList(b.build()).build()
    }
}

class CarChecklist(ctx: CarContext, private val listId: String) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val g = GarageStore.load(carContext)
        val v = GarageStore.active(carContext, g)
        val list = v.checklists.firstOrNull { it.id == listId }
        val b = ItemList.Builder().setNoItemsMessage("Nothing in this list")
        list?.items?.forEach { item ->
            b.addItem(Row.Builder().setTitle(item.text).setToggle(Toggle.Builder { on ->
                val g2 = GarageStore.load(carContext)
                val l2 = GarageStore.active(carContext, g2).checklists.firstOrNull { it.id == listId } ?: return@Builder
                l2.items.firstOrNull { it.id == item.id }?.ticked = on
                l2.completedAt = if (l2.finished) System.currentTimeMillis() else 0L
                GarageStore.save(carContext, g2)
                invalidate()
            }.setChecked(item.ticked).build()).build())
        }
        return ListTemplate.Builder().setTitle(list?.name ?: "Checklist").setHeaderAction(Action.BACK)
            .setSingleList(b.build()).build()
    }
}
