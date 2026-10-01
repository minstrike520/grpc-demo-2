package com.example.grpcsim

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Maps collision force (0..1) to a vibration: larger force => longer, stronger.
 * The server already debounces collision notifications (0.3 s), so one event
 * here means exactly one buzz.
 */
object Haptics {

    fun vibrate(context: Context, force: Float) {
        val f = force.coerceIn(0f, 1f)
        if (f <= 0f) return
        val amplitude = (40 + f * 215).toInt()   // 40..255
        val duration = (60 + f * 340).toLong()   // 60..400 ms
        val effect = VibrationEffect.createOneShot(duration, amplitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(effect)
        }
    }
}