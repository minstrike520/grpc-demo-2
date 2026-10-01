package com.example.grpcsim

/**
 * Pure tilt -> (vx, vy) mapping, separated from the sensor plumbing so it can
 * be unit-tested on the JVM.
 *
 * The phone reports orientation (from the rotation vector sensor) as:
 *   roll  : rotation around the phone's long axis (left/right tilt)
 *   pitch : rotation around the phone's short axis (forward/back tilt)
 *
 * The mapping is linear and clamped: a tilt of FULL_RANGE_DEG produces the
 * max value ±1 for that axis (the server then scales by its own max speed).
 *
 * Signs are hardware/holding-convention dependent; flip SIGN_VX / SIGN_VY on
 * a real device if an axis feels inverted.
 */
object TiltMapping {
    /** Tilt (degrees) that maps to a full ±1 command. */
    const val FULL_RANGE_DEG = 25f

    const val SIGN_VX = -1f
    const val SIGN_VY = -1f

    fun map(rollDeg: Double, pitchDeg: Double): Pair<Float, Float> {
        val vx = (SIGN_VX * rollDeg / FULL_RANGE_DEG).toFloat().coerceIn(-1f, 1f)
        val vy = (SIGN_VY * pitchDeg / FULL_RANGE_DEG).toFloat().coerceIn(-1f, 1f)
        return vx to vy
    }
}