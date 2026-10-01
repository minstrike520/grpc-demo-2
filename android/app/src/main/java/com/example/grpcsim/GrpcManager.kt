package com.example.grpcsim

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sim.CollisionEvent
import sim.ControlMessage
import sim.ResetReply
import sim.ResetRequest
import sim.SimEvent
import sim.SimServiceGrpcKt
import sim.StateSnapshot
import sim.StatusReply
import sim.StatusRequest

/**
 * Owns the gRPC connection for the whole app (created by [SimApplication],
 * never tied to a screen), so rotating / recreating the UI keeps the
 * connection alive and never reconnects.
 *
 * Channels (per spec):
 *   - Control        : bidirectional stream — our tilt controls out, the
 *                      server's collision notifications + state snapshots in.
 *   - GetStatus      : unary query (client deadline).
 *   - ResetSimulation: unary reset  (client deadline; needs confirm=true).
 *
 * gRPC extras:
 *   - metadata : x-device-id / x-app-version are sent on every call; the
 *                server echoes the device id back inside StatusReply.
 *   - deadline : 2 s on the unary channels (see DEADLINE_S).
 *   - errors   : gRPC status codes are surfaced via [describeError].
 */
class GrpcManager(private val app: Context) {

    companion object {
        private const val PREFS = "grpc_sim"
        private const val KEY_ADDR = "server_address"

        /** Default when nothing configured: emulator -> host loopback. */
        const val DEFAULT_ADDRESS = "10.0.2.2:50051"

        /** Unary calls must answer within this long, else DEADLINE_EXCEEDED. */
        const val DEADLINE_S = 2L

        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 10_000L
    }

    // ------------------------------------------------------------- UI state
    private val _connState = MutableStateFlow<ConnState>(ConnState.Idle)
    val connState: StateFlow<ConnState> = _connState.asStateFlow()

    private val _snapshot = MutableStateFlow<StateSnapshot?>(null)
    val snapshot: StateFlow<StateSnapshot?> = _snapshot.asStateFlow()

    private val _collisions = MutableSharedFlow<CollisionEvent>(extraBufferCapacity = 16)
    val collisions: SharedFlow<CollisionEvent> = _collisions.asSharedFlow()

    // ------------------------------------------------------------- plumbing
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channel: ManagedChannel? = null

    // outbound controls for the main stream; DROP_OLDEST keeps the stream
    // from stalling when the network is slow (latest command wins)
    private val outbound = MutableSharedFlow<ControlMessage>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var streamJob: Job? = null
    private var seq = 0L

    private val deviceId: String =
        (Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown") + " | " + Build.MANUFACTURER + " " + Build.MODEL

    private val DEVICE_KEY = Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER)
    private val APP_KEY = Metadata.Key.of("x-app-version", Metadata.ASCII_STRING_MARSHALLER)

    // --------------------------------------------------------------- address
    val serverAddress: String
        get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ADDR, null)
            ?: BuildConfig.SIM_SERVER_ADDRESS.ifBlank { DEFAULT_ADDRESS }

    /** Change the server address at runtime; tears the channel down and reconnects. */
    fun setServerAddress(addr: String) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ADDR, addr).apply()
        restart()
    }

    fun start() = restart()

    private fun restart() {
        streamJob?.cancel()
        channel?.shutdownNow()
        channel = null
        _connState.value = ConnState.Idle
        streamJob = scope.launch { runLoop() }
    }

    // ------------------------------------------------------------ main loop
    /** Reconnect loop for the main bidirectional channel. */
    private suspend fun runLoop() {
        var backoffMs = INITIAL_BACKOFF_MS
        while (scope.isActive) {
            _connState.value = ConnState.Connecting
            try {
                val chan = channel ?: buildChannel().also { channel = it }
                val stub = SimServiceGrpcKt.SimServiceCoroutineStub(chan)
                _connState.value = ConnState.Connected
                backoffMs = INITIAL_BACKOFF_MS
                stub.control(outbound, makeMetadata()).collect { ev -> handleEvent(ev) }
                // server closed the stream cleanly — reconnect
                _connState.value = ConnState.Disconnected("伺服器關閉了 main channel")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connState.value = ConnState.Disconnected(describeError(e))
            }
            if (!scope.isActive) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun handleEvent(ev: SimEvent) {
        when {
            ev.hasCollision() -> {
                val c = ev.collision
                if (c.force > 0f) {
                    // server already debounced to >= 0.3 s between notifications
                    Haptics.vibrate(app, c.force)
                    _collisions.tryEmit(c)
                }
            }
            ev.hasState() -> _snapshot.value = ev.state
        }
    }

    // --------------------------------------------------------------- control
    /** Called by the tilt sensor at sensor rate. Latest value wins. */
    fun sendControl(vx: Float, vy: Float) {
        val msg = ControlMessage.newBuilder()
            .setVx(vx.coerceIn(-1f, 1f))
            .setVy(vy.coerceIn(-1f, 1f))
            .setSeq(++seq)
            .setTimestampMs(System.currentTimeMillis())
            .build()
        outbound.tryEmit(msg)
    }

    // ------------------------------------------------------------- unary RPCs
    suspend fun getStatus(recentEvents: Int = 5, simulateLatencyMs: Int = 0): StatusReply {
        val stub = currentStub().withDeadlineAfter(DEADLINE_S, TimeUnit.SECONDS)
        return stub.getStatus(
            StatusRequest.newBuilder()
                .setRecentEvents(recentEvents)
                .setSimulateLatencyMs(simulateLatencyMs)
                .build(),
            makeMetadata(),
        )
    }

    /** Reset the simulation. `confirm=false` is rejected by the server
     *  with FAILED_PRECONDITION — a nice error-code demo. */
    suspend fun resetSimulation(confirm: Boolean): ResetReply {
        val stub = currentStub().withDeadlineAfter(DEADLINE_S, TimeUnit.SECONDS)
        return stub.resetSimulation(
            ResetRequest.newBuilder().setConfirm(confirm).build(),
            makeMetadata(),
        )
    }

    // --------------------------------------------------------------- helpers
    private fun currentStub() =
        SimServiceGrpcKt.SimServiceCoroutineStub(channel ?: buildChannel().also { channel = it })

    private fun buildChannel(): ManagedChannel =
        ManagedChannelBuilder.forAddress(serverHost(), serverPort())
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build()

    private fun serverHost(): String = serverAddress.substringBeforeLast(':').ifEmpty { DEFAULT_ADDRESS.substringBeforeLast(':') }
    private fun serverPort(): Int =
        serverAddress.substringAfterLast(':').toIntOrNull() ?: 50051

    private fun makeMetadata(): Metadata = Metadata().apply {
        put(DEVICE_KEY, deviceId)
        put(APP_KEY, BuildConfig.VERSION_NAME)
    }

    /** Turn any RPC failure into a short, user-facing message. */
    fun describeError(e: Exception): String {
        val status = when (e) {
            is StatusException -> e.status
            is StatusRuntimeException -> e.status
            else -> return "連線錯誤：${e.message ?: e.javaClass.simpleName}"
        }
        val detail = status.description.let { if (it.isNullOrBlank()) "" else "（$it）" }
        return when (status.code) {
            Status.Code.UNAVAILABLE -> "伺服器不可達$detail"
            Status.Code.DEADLINE_EXCEEDED -> "伺服器回應逾時（deadline）"
            Status.Code.CANCELLED -> "呼叫被取消"
            Status.Code.FAILED_PRECONDITION -> "要求被伺服器拒絕$detail"
            Status.Code.INVALID_ARGUMENT -> "參數無效$detail"
            Status.Code.UNIMPLEMENTED -> "伺服器不支援此方法"
            else -> "gRPC ${status.code.name}$detail"
        }
    }
}