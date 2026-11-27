package com.caravanlevel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/** The bubble level for the vehicle picked at the top of the screen. */
class LevelActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var bubbleView: BubbleView
    private lateinit var voiceButton: LevelButton
    private lateinit var styleButton: LevelButton
    private lateinit var sizesButton: LevelButton
    private lateinit var ui: Ui
    private lateinit var garage: Garage
    private lateinit var vehicle: Vehicle
    private var inches = false
    private lateinit var barHolder: LinearLayout

    // Smoothed raw angles in degrees, before calibration offsets are applied
    private var rawRoll = 0f
    private var rawPitch = 0f
    private var hasReading = false

    // Voice: off every time the app starts, switched on with the button
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceOn = false
    private var lastSpokenAt = 0L
    private var levelAnnounced = false
    private val SPEAK_EVERY_MS = 3000L

    // How often the written figures refresh (the bubble itself stays smooth)
    private val TEXT_UPDATE_MS = 1000L
    private var lastTextUpdate = 0L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private var wasDark = true

    override fun onCreate(savedInstanceState: Bundle?) {
        wasDark = Theme.apply(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        ui = Ui(this)
        bubbleView = BubbleView(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                ttsReady = true
            }
        }

        val calibrate = makeButton("Calibrate", Icon.CALIBRATE) {
            ui.confirm(
                "Calibrate the level?",
                "This sets the phone's current position as level for ${vehicle.name}.\n\n" +
                    "Only do it with the phone lying flat in its usual place on a surface you know is level, " +
                    "checked with a proper spirit level. Otherwise every reading afterwards will be out.",
                "Calibrate"
            ) {
                vehicle.rollOffset = rawRoll
                vehicle.pitchOffset = rawPitch
                GarageStore.save(this, garage)
                Toast.makeText(this, "Calibrated", Toast.LENGTH_SHORT).show()
            }
        }
        voiceButton = makeButton("Voice off", Icon.VOICE) {
            if (!voiceOn && !ttsReady) {
                Toast.makeText(this, "Voice not available on this phone", Toast.LENGTH_LONG).show()
            } else {
                voiceOn = !voiceOn
                lastSpokenAt = 0L
                levelAnnounced = false
                showVoice()
                if (!voiceOn) tts?.stop()
            }
        }
        bubbleView.separate = AppPrefs.separateBubbles(this)
        styleButton = makeButton("", Icon.VIALS) {
            bubbleView.separate = !bubbleView.separate
            AppPrefs.setSeparateBubbles(this, bubbleView.separate)
            showStyle()
            bubbleView.invalidate()
        }
        showStyle()
        sizesButton = makeButton("Sizes", Icon.SIZES) {
            startActivity(Intent(this, VehicleActivity::class.java))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(calibrate.view)
            addView(styleButton.view)
            addView(voiceButton.view)
            addView(sizesButton.view)
        }

        val root = FrameLayout(this)
        root.addView(
            bubbleView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        root.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                setMargins(dp(8), dp(8), dp(8), dp(24))
            }
        )
        barHolder = ui.column().apply { setPadding(ui.dp(12), ui.dp(4), ui.dp(12), 0) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Colours.BG)
            addView(barHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        page.fitSystemBars()
        setContentView(page)
        Theme.barIcons(this)
    }

    /** Load the chosen vehicle and show the picker at the top. */
    private fun loadVehicle() {
        garage = GarageStore.load(this)
        vehicle = GarageStore.active(this, garage)
        inches = AppPrefs.inches(this)
        // The Sizes button is only needed until the levelling sizes are set
        val sized = vehicle.trackCm > 0f && vehicle.lengthForLevel > 0f
        sizesButton.view.visibility = if (sized) View.GONE else View.VISIBLE
        // Without sizes the orange lines already give the degrees, so don't repeat them
        bubbleView.showAngles = sized
        bubbleView.hint = ""
        barHolder.removeAllViews()
        barHolder.addView(VehicleBar.make(ui, garage, vehicle) {
            AppPrefs.setActiveVehicle(this, it.id)
            loadVehicle()
            lastSpokenAt = 0L
            levelAnnounced = false
        })
    }

    /** A small tile like the home page ones: an icon with its name underneath. */
    private class LevelButton(val view: LinearLayout, val icon: IconView, val label: TextView)

    private fun makeButton(label: String, icon: Icon, onClick: () -> Unit): LevelButton {
        val iv = IconView(this, icon).apply { layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)) }
        val tv = ui.text(label, 14f, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
        }
        val col = ui.column(6).apply {
            gravity = Gravity.CENTER
            background = ui.rounded(Colours.TILE, 14)
            isClickable = true
            setOnClickListener { onClick() }
            addView(iv); addView(tv)
            layoutParams = LinearLayout.LayoutParams(0, dp(78), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
        }
        return LevelButton(col, iv, tv)
    }

    private fun showVoice() {
        voiceButton.label.text = if (voiceOn) "Voice on" else "Voice off"
        voiceButton.icon.colour = if (voiceOn) Colours.ACCENT else Colours.TEXT
        voiceButton.icon.invalidate()
    }

    /** The button offers the other style, so it shows that one's picture. */
    private fun showStyle() {
        styleButton.label.text = if (bubbleView.separate) "One bubble" else "Two bubbles"
        styleButton.icon.icon = if (bubbleView.separate) Icon.BUBBLE else Icon.VIALS
        styleButton.icon.invalidate()
    }

    override fun onResume() {
        super.onResume()
        Theme.recreateIfChanged(this, wasDark)
        AppPrefs.setLastSection(this, Section.LEVEL.name)
        loadVehicle() // pick up changes made on the other pages
        lastSpokenAt = 0L
        levelAnnounced = false
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            Toast.makeText(this, "No accelerometer found", Toast.LENGTH_LONG).show()
        } else {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        tts?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // Phone flat, screen up, top edge toward the front of the caravan.
        // The accelerometer reads "up", so right edge raised gives positive ax.
        val roll = Math.toDegrees(atan2(ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))).toFloat()
        val pitch = Math.toDegrees(atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))).toFloat()

        // Low-pass filter so the bubble doesn't jitter
        val alpha = 0.1f
        if (!hasReading) {
            rawRoll = roll
            rawPitch = pitch
            hasReading = true
        } else {
            rawRoll += alpha * (roll - rawRoll)
            rawPitch += alpha * (pitch - rawPitch)
        }

        val v = vehicle
        val r = rawRoll - v.rollOffset
        val pt = rawPitch - v.pitchOffset
        bubbleView.rollDeg = r
        bubbleView.pitchDeg = pt

        val result = LevelText.work(v, r, pt, inches)

        // The written figures only refresh every TEXT_UPDATE_MS so they can be read
        val now = SystemClock.elapsedRealtime()
        if (now - lastTextUpdate >= TEXT_UPDATE_MS) {
            lastTextUpdate = now
            bubbleView.shownRoll = r
            bubbleView.shownPitch = pt
            bubbleView.sideText = result.side
            bubbleView.frontText = result.front
            bubbleView.hint = result.hint
        }

        if (voiceOn && ttsReady && result.spoken.isNotEmpty()) speakRepeating(result.spoken)
        bubbleView.invalidate()
    }

    /**
     * Repeat the current adjustment every SPEAK_EVERY_MS (start to start). If the last
     * message is still being spoken it waits rather than cutting itself off.
     * "Level" is announced once, then it goes quiet until you move out of level again.
     */
    private fun speakRepeating(message: String) {
        val t = tts ?: return
        val now = SystemClock.elapsedRealtime()
        if (message == "Level") {
            if (!levelAnnounced) {
                t.speak(message, TextToSpeech.QUEUE_FLUSH, null, "level")
                levelAnnounced = true
            }
            return
        }
        levelAnnounced = false
        if (now - lastSpokenAt >= SPEAK_EVERY_MS && !t.isSpeaking) {
            t.speak(message, TextToSpeech.QUEUE_FLUSH, null, "level")
            lastSpokenAt = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
