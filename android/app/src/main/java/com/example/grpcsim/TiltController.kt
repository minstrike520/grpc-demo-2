package com.example.grpcsim

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.roundToInt

/**
 * Reads the rotation vector sensor and converts it to roll/pitch, then to a
 * normalized (vx, vy) command via [TiltMapping].
 *
 * Registered for the lifetime of the activity (sensors only matter while the
 * app is in the foreground); the gRPC connection itself lives at the app level.
 */
class TiltController(
    context: Context,
    private val onTilt: (Float, Float) -> Unit,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var running = false

    /** Latest tilt readings, for the on-screen readout. */
    val latestRollPitch: Pair<Float, Float> get() = _latestRollPitch
    private var _latestRollPitch = 0f to 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // rotation vector -> rotation matrix -> roll/pitch (radians)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val rollDeg = Math.toDegrees(orientation[2].toDouble())
            val pitchDeg = Math.toDegrees(orientation[1].toDouble())
            _latestRollPitch = rollDeg.roundToInt().toFloat() to pitchDeg.roundToInt().toFloat()
            val (vx, vy) = TiltMapping.map(rollDeg, pitchDeg)
            onTilt(vx, vy)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (running || rotationSensor == null) return
        sensorManager.registerListener(
            listener,
            rotationSensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
        running = true
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(listener)
        running = false
    }
}