package com.caravanlevel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var bubbleView: BubbleView
    private lateinit var voiceButton: Button
    private lateinit var state: State

    // Smoothed raw angles in degrees, before calibration offsets are applied
    private var rawRoll = 0f
    private var rawPitch = 0f
    private var hasReading = false

    // Voice: off every time the app starts, switched on with the button
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceOn = false
    private var candidate: String? = null
    private var candidateSince = 0L
    private var lastSpoken: String? = null

    // How often the written figures refresh (the bubble itself stays smooth)
    private val TEXT_UPDATE_MS = 1000L
    private var lastTextUpdate = 0L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        state = Store.load(this)
        bubbleView = BubbleView(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                ttsReady = true
            }
        }

        val calibrate = makeButton("Calibrate") {
            val p = state.profiles[state.active]
            p.rollOffset = rawRoll
            p.pitchOffset = rawPitch
            Store.save(this, state)
            Toast.makeText(this, "Zeroed", Toast.LENGTH_SHORT).show()
        }
        voiceButton = makeButton("Voice: OFF") {
            if (!voiceOn && !ttsReady) {
                Toast.makeText(this, "Voice not available on this phone", Toast.LENGTH_LONG).show()
            } else {
                voiceOn = !voiceOn
                lastSpoken = null
                candidate = null
                voiceButton.text = if (voiceOn) "Voice: ON" else "Voice: OFF"
                if (!voiceOn) tts?.stop()
            }
        }
        val settings = makeButton("Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(calibrate)
            addView(voiceButton)
            addView(settings)
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
        setContentView(root)
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

    override fun onResume() {
        super.onResume()
        state = Store.load(this) // pick up changes made in Settings
        lastSpoken = null
        candidate = null
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

        val p = state.profiles[state.active]
        val r = rawRoll - p.rollOffset
        val pt = rawPitch - p.pitchOffset
        bubbleView.rollDeg = r
        bubbleView.pitchDeg = pt
        bubbleView.profileLabel = p.name

        val result = work(p, r, pt)

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

        if (voiceOn && ttsReady && result.spoken.isNotEmpty()) speakWhenSettled(result.spoken)
        bubbleView.invalidate()
    }

    /** Round to a sensible step for the chosen unit: 0.5 cm or 0.25 in. */
    private fun snap(valueInUnit: Float): Float {
        val step = if (state.inches) 0.25f else 0.5f
        return (valueInUnit / step).roundToInt() * step
    }

    private class Result(val side: String, val front: String, val hint: String, val spoken: String)

    private fun work(p: Profile, roll: Float, pitch: Float): Result {
        val levelSide = abs(roll) <= BubbleView.LEVEL_DEG
        val levelFront = abs(pitch) <= BubbleView.LEVEL_DEG

        if (p.trackCm <= 0f || p.hitchCm <= 0f) {
            return Result("", "", "Set caravan size in Settings for amounts", "")
        }

        val unitShort = if (state.inches) "in" else "cm"
        val unitSpoken = if (state.inches) "inches" else "centimetres"
        val perUnit = if (state.inches) Store.CM_PER_INCH else 1f

        var sideText = ""
        var frontText = ""
        val spoken = mutableListOf<String>()

        if (!levelSide) {
            val cm = p.trackCm * tan(Math.toRadians(abs(roll).toDouble())).toFloat()
            val amount = snap(cm / perUnit)
            val which = if (roll > 0) "left" else "right" // raise the low side
            sideText = "Raise ${which.uppercase()} side ${Store.fixed(amount)} $unitShort"
            spoken.add("Raise $which side ${Store.num(amount)} $unitSpoken")
        }
        if (!levelFront) {
            val cm = p.hitchCm * tan(Math.toRadians(abs(pitch).toDouble())).toFloat()
            val amount = snap(cm / perUnit)
            val verb = if (pitch > 0) "Lower" else "Raise" // front high -> lower the front
            frontText = "$verb FRONT ${Store.fixed(amount)} $unitShort"
            spoken.add("$verb front ${Store.num(amount)} $unitSpoken")
        }
        if (levelSide && levelFront) spoken.add("Level")

        return Result(sideText, frontText, "", spoken.joinToString(". "))
    }

    /** Speak a message once it has stayed the same for two seconds. */
    private fun speakWhenSettled(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (message != candidate) {
            candidate = message
            candidateSince = now
        } else if (now - candidateSince >= 2000 && message != lastSpoken) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "level")
            lastSpoken = message
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
