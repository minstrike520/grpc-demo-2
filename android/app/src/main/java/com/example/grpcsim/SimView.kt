package com.example.grpcsim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import sim.StateSnapshot

/**
 * Draws the simulated platform (walls, obstacles, car) from the latest
 * [StateSnapshot] pushed over the main gRPC channel — the "simulation feeds
 * back to the sensing device" half of the loop.
 */
class SimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        color = 0xFF7D8892.toInt()
    }
    private val obstaclePaint = Paint().apply { color = 0xFF55606B.toInt() }
    private val obstacleBorder = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF7D8892.toInt()
    }
    private val carPaint = Paint().apply { color = 0xFF4FC3F7.toInt() }
    private val carBorder = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFF16404F.toInt()
    }
    private val arrowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFD54F.toInt()
    }
    private val flashPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        color = 0xFFFF5B5B.toInt()
    }
    private val textPaint = Paint().apply {
        color = 0xFF9AA4AE.toInt()
        textSize = 11f * density
    }

    var snapshot: StateSnapshot? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Collision flash until this uptime (ms). */
    var flashUntilMs: Long = 0L
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val snap = snapshot
        if (snap == null) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("等待伺服器快照…", width / 2f, height / 2f, textPaint)
            return
        }

        val pad = 24f * density
        val availW = width - 2 * pad
        val availH = height - 2 * pad
        val scale = min(
            availW / snap.platformWidth,
            availH / snap.platformHeight,
        ).toFloat()
        val originX = pad + (availW - snap.platformWidth * scale) / 2f
        val originY = pad + (availH - snap.platformHeight * scale) / 2f
        fun tx(x: Double) = (originX + x * scale).toFloat()
        fun ty(y: Double) = (originY + (snap.platformHeight - y) * scale).toFloat()

        // platform boundary (walls)
        canvas.drawRect(
            tx(0.0), ty(snap.platformHeight),
            tx(snap.platformWidth), ty(0.0),
            borderPaint,
        )

        // obstacles
        textPaint.textAlign = Paint.Align.CENTER
        for (o in snap.obstaclesList) {
            val r = (o.radius * scale).toFloat()
            canvas.drawCircle(tx(o.x), ty(o.y), r, obstaclePaint)
            canvas.drawCircle(tx(o.x), ty(o.y), r, obstacleBorder)
            canvas.drawText(o.id, tx(o.x), ty(o.y) - r - 6f * density, textPaint)
        }

        // car (a square — the car has no heading)
        val r = (snap.carRadius * scale).toFloat()
        val cx = tx(snap.x)
        val cy = ty(snap.y)
        canvas.drawRect(cx - r, cy - r, cx + r, cy + r, carPaint)
        canvas.drawRect(cx - r, cy - r, cx + r, cy + r, carBorder)

        // current control as an arrow (also shows the server zeroing it on disconnect)
        if (abs(snap.controlVx) + abs(snap.controlVy) > 0.01f) {
            canvas.drawLine(
                cx, cy,
                cx + snap.controlVx * 2.5f * r,
                cy - snap.controlVy * 2.5f * r,
                arrowPaint,
            )
        }

        // collision flash
        if (SystemClock.elapsedRealtime() < flashUntilMs) {
            canvas.drawCircle(cx, cy, r + 8f * density, flashPaint)
        }
    }
}