package com.example.grpcsim

import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val grpc by lazy { (application as SimApplication).grpc }

    private lateinit var tilt: TiltController
    private lateinit var simView: SimView
    private lateinit var statusText: TextView
    private lateinit var collisionText: TextView
    private lateinit var tiltText: TextView
    private lateinit var posText: TextView
    private lateinit var addrText: TextView
    private lateinit var infoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        simView = findViewById(R.id.sim_view)
        statusText = findViewById(R.id.txt_status)
        collisionText = findViewById(R.id.txt_last_collision)
        tiltText = findViewById(R.id.txt_tilt)
        posText = findViewById(R.id.txt_pos)
        addrText = findViewById(R.id.txt_addr)
        infoText = findViewById(R.id.txt_info)

        addrText.text = "伺服器位址（點我修改）：${grpc.serverAddress}"
        addrText.setOnClickListener { showAddressDialog() }

        findViewById<Button>(R.id.btn_query).setOnClickListener { query(slow = false) }
        findViewById<Button>(R.id.btn_query_slow).setOnClickListener { query(slow = true) }
        findViewById<Button>(R.id.btn_reset).setOnClickListener { confirmAndReset() }
        findViewById<Button>(R.id.btn_reset_noconfirm).setOnClickListener { resetNoConfirm() }

        // tilt sensor -> control commands (connection survives rotation,
        // the sensor is re-registered here on each lifecycle resume)
        tilt = TiltController(this) { vx, vy -> grpc.sendControl(vx, vy) }

        // ---- observe app-level gRPC state ------------------------------------
        lifecycleScope.launch {
            grpc.connState.collect { state ->
                statusText.text = when (state) {
                    ConnState.Idle -> "未連線"
                    ConnState.Connecting -> "連線中…（自動重連）"
                    ConnState.Connected -> "已連線：主通道（控制 + 碰撞通知）"
                    is ConnState.Disconnected ->
                        "已斷線：${state.reason ?: "未知原因"} — 自動重連中…"
                }
            }
        }
        lifecycleScope.launch {
            grpc.snapshot.collect { snap ->
                simView.snapshot = snap
                if (snap != null) {
                    posText.text = String.format(
                        Locale.US,
                        "小車：(%.2f, %.2f) m　速度 (%.2f, %.2f) m/s",
                        snap.x, snap.y, snap.vx, snap.vy,
                    )
                } else {
                    posText.text = "小車：—"
                }
            }
        }
        lifecycleScope.launch {
            grpc.collisions.collect { c ->
                simView.flashUntilMs = SystemClock.elapsedRealtime() + 350
                collisionText.text =
                    "碰撞：${c.what}　力道 ${String.format(Locale.US, "%.2f", c.force)}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tilt.start()
    }

    override fun onPause() {
        tilt.stop()
        super.onPause()
    }

    // ------------------------------------------------------------------ UI
    private fun query(slow: Boolean) {
        infoText.text = if (slow) "以 10 秒延遲查詢（deadline 2 秒 → 預期逾時）…" else "查詢中…"
        lifecycleScope.launch {
            try {
                val reply = grpc.getStatus(recentEvents = 5, simulateLatencyMs = if (slow) 10_000 else 0)
                infoText.text = buildString {
                    append("伺服器版本 v${reply.serverVersion}")
                    append("　運作 ${reply.uptimeMs / 1000} s")
                    append("　碰撞 ${reply.collisionCount} 次")
                    append("\n伺服器看到的裝置（metadata）：${reply.clientDeviceId}")
                    append("\n控制通道(on server)：${if (reply.controlStreamActive) "開" else "關"}")
                    append("\n小車：(${String.format(Locale.US, "%.2f", reply.state.x)}, ${String.format(Locale.US, "%.2f", reply.state.y)})")
                    if (slow) append("\n（慢速查詢意外成功？）")
                }
            } catch (e: Exception) {
                infoText.text = "查詢失敗：${grpc.describeError(e)}"
            }
        }
    }

    private fun confirmAndReset() {
        AlertDialog.Builder(this)
            .setTitle("重置模擬")
            .setMessage("確定把平台恢復到初始狀態？")
            .setNegativeButton("取消", null)
            .setPositiveButton("重置") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val reply = grpc.resetSimulation(confirm = true)
                        infoText.text = "重置成功：${reply.message}"
                    } catch (e: Exception) {
                        infoText.text = "重置失敗：${grpc.describeError(e)}"
                    }
                }
            }
            .show()
    }

    /** Sends confirm=false on purpose — the server must reject with
     *  FAILED_PRECONDITION (error-code demo). */
    private fun resetNoConfirm() {
        lifecycleScope.launch {
            try {
                val reply = grpc.resetSimulation(confirm = false)
                infoText.text = "重置（無確認）意外成功：${reply.message}"
            } catch (e: Exception) {
                infoText.text = "重置被拒（示範錯誤碼 FAILED_PRECONDITION）：\n${grpc.describeError(e)}"
            }
        }
    }

    private fun showAddressDialog() {
        val input = EditText(this).apply {
            setText(grpc.serverAddress)
            hint = "例如 10.0.2.2:50051 或 laptop.tail544a05.ts.net:50051"
        }
        AlertDialog.Builder(this)
            .setTitle("伺服器位址（gRPC）")
            .setMessage("改為運行中的模擬環境（開發時在 laptop 上）")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("套用") { _, _ ->
                val addr = input.text.toString().trim()
                if (addr.isNotEmpty()) {
                    grpc.setServerAddress(addr)
                    addrText.text = "伺服器位址（點我修改）：${grpc.serverAddress}"
                    infoText.text = "已切換伺服器，重新連線中…"
                }
            }
            .show()
    }
}