package sim

import io.grpc.CallOptions
import io.grpc.CallOptions.DEFAULT
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.ServerServiceDefinition.builder
import io.grpc.ServiceDescriptor
import io.grpc.Status.UNIMPLEMENTED
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls.bidiStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls.bidiStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow
import sim.SimServiceGrpc.getServiceDescriptor

/**
 * Holder for Kotlin coroutine-based client and server APIs for sim.SimService.
 */
public object SimServiceGrpcKt {
  public const val SERVICE_NAME: String = SimServiceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = getServiceDescriptor()

  public val controlMethod: MethodDescriptor<ControlMessage, SimEvent>
    @JvmStatic
    get() = SimServiceGrpc.getControlMethod()

  public val getStatusMethod: MethodDescriptor<StatusRequest, StatusReply>
    @JvmStatic
    get() = SimServiceGrpc.getGetStatusMethod()

  public val resetSimulationMethod: MethodDescriptor<ResetRequest, ResetReply>
    @JvmStatic
    get() = SimServiceGrpc.getResetSimulationMethod()

  /**
   * A stub for issuing RPCs to a(n) sim.SimService service as suspending coroutines.
   */
  @StubFor(SimServiceGrpc::class)
  public class SimServiceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<SimServiceCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): SimServiceCoroutineStub =
        SimServiceCoroutineStub(channel, callOptions)

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][io.grpc.Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * The [Flow] of requests is collected once each time the [Flow] of responses is
     * collected. If collection of the [Flow] of responses completes normally or
     * exceptionally before collection of `requests` completes, the collection of
     * `requests` is cancelled.  If the collection of `requests` completes
     * exceptionally for any other reason, then the collection of the [Flow] of responses
     * completes exceptionally for the same reason and the RPC is cancelled with that reason.
     *
     * @param requests A [Flow] of request messages.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun control(requests: Flow<ControlMessage>, headers: Metadata = Metadata()):
        Flow<SimEvent> = bidiStreamingRpc(
      channel,
      SimServiceGrpc.getControlMethod(),
      requests,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun getStatus(request: StatusRequest, headers: Metadata = Metadata()):
        StatusReply = unaryRpc(
      channel,
      SimServiceGrpc.getGetStatusMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun resetSimulation(request: ResetRequest, headers: Metadata = Metadata()):
        ResetReply = unaryRpc(
      channel,
      SimServiceGrpc.getResetSimulationMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the sim.SimService service based on Kotlin coroutines.
   */
  public abstract class SimServiceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns a [Flow] of responses to an RPC for sim.SimService.Control.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param requests A [Flow] of requests from the client.  This flow can be
     *        collected only once and throws [java.lang.IllegalStateException] on attempts to
     * collect
     *        it more than once.
     */
    public open fun control(requests: Flow<ControlMessage>): Flow<SimEvent> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method sim.SimService.Control is unimplemented"))

    /**
     * Returns the response to an RPC for sim.SimService.GetStatus.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getStatus(request: StatusRequest): StatusReply = throw
        StatusException(UNIMPLEMENTED.withDescription("Method sim.SimService.GetStatus is unimplemented"))

    /**
     * Returns the response to an RPC for sim.SimService.ResetSimulation.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun resetSimulation(request: ResetRequest): ResetReply = throw
        StatusException(UNIMPLEMENTED.withDescription("Method sim.SimService.ResetSimulation is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(bidiStreamingServerMethodDefinition(
      context = this.context,
      descriptor = SimServiceGrpc.getControlMethod(),
      implementation = ::control
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SimServiceGrpc.getGetStatusMethod(),
      implementation = ::getStatus
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = SimServiceGrpc.getResetSimulationMethod(),
      implementation = ::resetSimulation
    )).build()
  }
}
