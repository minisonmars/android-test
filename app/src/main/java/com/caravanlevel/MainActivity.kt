package com.caravanlevel

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var bubbleView: BubbleView

    // Smoothed raw angles in degrees, before calibration offsets are applied
    private var rawRoll = 0f
    private var rawPitch = 0f
    private var hasReading = false

    private var rollOffset = 0f
    private var pitchOffset = 0f

    private val prefs by lazy { getSharedPreferences("level", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rollOffset = prefs.getFloat("rollOffset", 0f)
        pitchOffset = prefs.getFloat("pitchOffset", 0f)

        bubbleView = BubbleView(this)

        val calibrate = Button(this).apply {
            text = "Calibrate (zero here)"
            setOnClickListener {
                rollOffset = rawRoll
                pitchOffset = rawPitch
                prefs.edit().putFloat("rollOffset", rollOffset).putFloat("pitchOffset", pitchOffset).apply()
                Toast.makeText(context, "Zeroed", Toast.LENGTH_SHORT).show()
            }
        }
        val reset = Button(this).apply {
            text = "Reset"
            setOnClickListener {
                rollOffset = 0f
                pitchOffset = 0f
                prefs.edit().clear().apply()
                Toast.makeText(context, "Calibration cleared", Toast.LENGTH_SHORT).show()
            }
        }

        val root = FrameLayout(this)
        root.addView(
            bubbleView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        root.addView(
            calibrate,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(32, 32, 32, 64)
            }
        )
        root.addView(
            reset,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(32, 32, 32, 64)
            }
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
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

        bubbleView.rollDeg = rawRoll - rollOffset
        bubbleView.pitchDeg = rawPitch - pitchOffset
        bubbleView.invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
