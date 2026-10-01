package sim;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: sim.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class SimServiceGrpc {

  private SimServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "sim.SimService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<sim.ControlMessage,
      sim.SimEvent> getControlMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Control",
      requestType = sim.ControlMessage.class,
      responseType = sim.SimEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<sim.ControlMessage,
      sim.SimEvent> getControlMethod() {
    io.grpc.MethodDescriptor<sim.ControlMessage, sim.SimEvent> getControlMethod;
    if ((getControlMethod = SimServiceGrpc.getControlMethod) == null) {
      synchronized (SimServiceGrpc.class) {
        if ((getControlMethod = SimServiceGrpc.getControlMethod) == null) {
          SimServiceGrpc.getControlMethod = getControlMethod =
              io.grpc.MethodDescriptor.<sim.ControlMessage, sim.SimEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Control"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  sim.ControlMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  sim.SimEvent.getDefaultInstance()))
              .build();
        }
      }
    }
    return getControlMethod;
  }

  private static volatile io.grpc.MethodDescriptor<sim.StatusRequest,
      sim.StatusReply> getGetStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetStatus",
      requestType = sim.StatusRequest.class,
      responseType = sim.StatusReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<sim.StatusRequest,
      sim.StatusReply> getGetStatusMethod() {
    io.grpc.MethodDescriptor<sim.StatusRequest, sim.StatusReply> getGetStatusMethod;
    if ((getGetStatusMethod = SimServiceGrpc.getGetStatusMethod) == null) {
      synchronized (SimServiceGrpc.class) {
        if ((getGetStatusMethod = SimServiceGrpc.getGetStatusMethod) == null) {
          SimServiceGrpc.getGetStatusMethod = getGetStatusMethod =
              io.grpc.MethodDescriptor.<sim.StatusRequest, sim.StatusReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  sim.StatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  sim.StatusReply.getDefaultInstance()))
              .build();
        }
      }
    }
    return getGetStatusMethod;
  }

  private static volatile io.grpc.MethodDescriptor<sim.ResetRequest,
      sim.ResetReply> getResetSimulationMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ResetSimulation",
      requestType = sim.ResetRequest.class,
      responseType = sim.ResetReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<sim.ResetRequest,
      sim.ResetReply> getResetSimulationMethod() {
    io.grpc.MethodDescriptor<sim.ResetRequest, sim.ResetReply> getResetSimulationMethod;
    if ((getResetSimulationMethod = SimServiceGrpc.getResetSimulationMethod) == null) {
      synchronized (SimServiceGrpc.class) {
        if ((getResetSimulationMethod = SimServiceGrpc.getResetSimulationMethod) == null) {
          SimServiceGrpc.getResetSimulationMethod = getResetSimulationMethod =
              io.grpc.MethodDescriptor.<sim.ResetRequest, sim.ResetReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ResetSimulation"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  sim.ResetRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  sim.ResetReply.getDefaultInstance()))
              .build();
        }
      }
    }
    return getResetSimulationMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static SimServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SimServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SimServiceStub>() {
        @java.lang.Override
        public SimServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SimServiceStub(channel, callOptions);
        }
      };
    return SimServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static SimServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SimServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SimServiceBlockingStub>() {
        @java.lang.Override
        public SimServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SimServiceBlockingStub(channel, callOptions);
        }
      };
    return SimServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static SimServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SimServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SimServiceFutureStub>() {
        @java.lang.Override
        public SimServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SimServiceFutureStub(channel, callOptions);
        }
      };
    return SimServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * Main channel: bidirectional streaming control.
     * </pre>
     */
    default io.grpc.stub.StreamObserver<sim.ControlMessage> control(
        io.grpc.stub.StreamObserver<sim.SimEvent> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getControlMethod(), responseObserver);
    }

    /**
     * <pre>
     * Unary channel: query simulation info.
     * </pre>
     */
    default void getStatus(sim.StatusRequest request,
        io.grpc.stub.StreamObserver<sim.StatusReply> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetStatusMethod(), responseObserver);
    }

    /**
     * <pre>
     * Unary channel: reset the simulation. Requires confirm = true.
     * </pre>
     */
    default void resetSimulation(sim.ResetRequest request,
        io.grpc.stub.StreamObserver<sim.ResetReply> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getResetSimulationMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service SimService.
   */
  public static abstract class SimServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return SimServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service SimService.
   */
  public static final class SimServiceStub
      extends io.grpc.stub.AbstractAsyncStub<SimServiceStub> {
    private SimServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SimServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SimServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Main channel: bidirectional streaming control.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<sim.ControlMessage> control(
        io.grpc.stub.StreamObserver<sim.SimEvent> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getControlMethod(), getCallOptions()), responseObserver);
    }

    /**
     * <pre>
     * Unary channel: query simulation info.
     * </pre>
     */
    public void getStatus(sim.StatusRequest request,
        io.grpc.stub.StreamObserver<sim.StatusReply> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetStatusMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Unary channel: reset the simulation. Requires confirm = true.
     * </pre>
     */
    public void resetSimulation(sim.ResetRequest request,
        io.grpc.stub.StreamObserver<sim.ResetReply> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getResetSimulationMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service SimService.
   */
  public static final class SimServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<SimServiceBlockingStub> {
    private SimServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SimServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SimServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Unary channel: query simulation info.
     * </pre>
     */
    public sim.StatusReply getStatus(sim.StatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetStatusMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Unary channel: reset the simulation. Requires confirm = true.
     * </pre>
     */
    public sim.ResetReply resetSimulation(sim.ResetRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getResetSimulationMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service SimService.
   */
  public static final class SimServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<SimServiceFutureStub> {
    private SimServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SimServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SimServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Unary channel: query simulation info.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<sim.StatusReply> getStatus(
        sim.StatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetStatusMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Unary channel: reset the simulation. Requires confirm = true.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<sim.ResetReply> resetSimulation(
        sim.ResetRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getResetSimulationMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_STATUS = 0;
  private static final int METHODID_RESET_SIMULATION = 1;
  private static final int METHODID_CONTROL = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET_STATUS:
          serviceImpl.getStatus((sim.StatusRequest) request,
              (io.grpc.stub.StreamObserver<sim.StatusReply>) responseObserver);
          break;
        case METHODID_RESET_SIMULATION:
          serviceImpl.resetSimulation((sim.ResetRequest) request,
              (io.grpc.stub.StreamObserver<sim.ResetReply>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CONTROL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.control(
              (io.grpc.stub.StreamObserver<sim.SimEvent>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getControlMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              sim.ControlMessage,
              sim.SimEvent>(
                service, METHODID_CONTROL)))
        .addMethod(
          getGetStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              sim.StatusRequest,
              sim.StatusReply>(
                service, METHODID_GET_STATUS)))
        .addMethod(
          getResetSimulationMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              sim.ResetRequest,
              sim.ResetReply>(
                service, METHODID_RESET_SIMULATION)))
        .build();
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (SimServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .addMethod(getControlMethod())
              .addMethod(getGetStatusMethod())
              .addMethod(getResetSimulationMethod())
              .build();
        }
      }
    }
    return result;
  }
}
