"""gRPC service implementation (grpcio async).

Demonstrates the three gRPC "extras" the spec asks for:

- metadata : client sends x-device-id / x-app-version; the server logs them,
             echoes them in StatusReply, and adds response metadata.
- deadline : clients set deadlines; the server also checks
             context.time_remaining() and aborts early when it is too small.
- errors   : FAILED_PRECONDITION (reset without confirm), INVALID_ARGUMENT
             (control values out of [-1, 1] on the main stream — reported via
             an event instead of killing the stream), DEADLINE_EXCEEDED
             (automatic), UNAVAILABLE (transport).
"""

from __future__ import annotations

import asyncio
import logging
import time

import grpc

import sim_pb2
import sim_pb2_grpc

from .engine import Engine

log = logging.getLogger("simserver.service")

APP_VERSION_HEADER = "x-app-version"
DEVICE_ID_HEADER = "x-device-id"


class SimService(sim_pb2_grpc.SimServiceServicer):
    def __init__(self, engine: Engine, server_version: str) -> None:
        self.engine = engine
        self.server_version = server_version

    # ------------------------------------------------------------ main channel
    async def Control(self, request_iterator, context: grpc.aio.ServicerContext):
        stream_id = object()  # identity of this stream (reconnects get a new one)
        metadata = context.invocation_metadata()
        device = _meta(metadata, DEVICE_ID_HEADER)
        app = _meta(metadata, APP_VERSION_HEADER)
        log.info("control stream open device=%s app=%s", device, app)
        await context.send_initial_metadata(
            (("x-server-version", self.server_version), ("x-server-active", "1"))
        )

        self.engine.attach_control(stream_id)
        sub_id, queue = self.engine.subscribe()
        reader = None
        try:
            async def read_inputs() -> None:
                async for req in request_iterator:
                    vx, vy = req.vx, req.vy
                    if vx < -1.0 or vx > 1.0 or vy < -1.0 or vy > 1.0:
                        # error code demo: out-of-range control is rejected
                        # (as an event, so the main channel stays usable)
                        self.engine._broadcast(self._invalid_arg_event(vx, vy))
                        log.warning("control out of range vx=%s vy=%s (rejected)", vx, vy)
                        continue
                    self.engine.set_control(stream_id, vx, vy)

            reader = asyncio.ensure_future(read_inputs())
            while True:
                ev = await queue.get()
                yield ev
        finally:
            # client gone / stream cancelled / generator closed:
            # surrender the stream so the car coasts to a stop
            if reader is not None:
                reader.cancel()
            self.engine.detach_control(stream_id)
            self.engine.unsubscribe(sub_id)
            log.info("control stream closed device=%s", device)

    @staticmethod
    def _invalid_arg_event(vx: float, vy: float) -> sim_pb2.SimEvent:
        return sim_pb2.SimEvent(
            collision=sim_pb2.CollisionEvent(
                force=0.0,
                what=f"control-out-of-range vx={vx} vy={vy}",
                timestamp_ms=int(time.time() * 1000),
            )
        )

    # ---------------------------------------------------------- unary: status
    async def GetStatus(self, request: sim_pb2.StatusRequest, context: grpc.aio.ServicerContext):
        # deadline handling: server aborts early if almost no time is left,
        # instead of doing work that would be thrown away
        if context.time_remaining() is not None and context.time_remaining() < 0.05:
            await context.abort(grpc.StatusCode.DEADLINE_EXCEEDED, "server deadline check")

        if request.simulate_latency_ms > 0:
            # simulated slow server: with a short client deadline this
            # demonstrates DEADLINE_EXCEEDED
            await _sleep_until_timeout(request.simulate_latency_ms / 1000.0, context)

        client_device_id = _meta(context.invocation_metadata(), DEVICE_ID_HEADER)
        log.info("GetStatus device=%s latency=%sms", client_device_id, request.simulate_latency_ms)

        await context.send_initial_metadata(
            (("x-server-version", self.server_version),)
        )
        return sim_pb2.StatusReply(
            state=self.engine.make_state(),
            uptime_ms=self.engine.uptime_ms(),
            client_device_id=client_device_id,
            server_version=self.server_version,
            collision_count=self.engine.sim.collision_count,
            control_stream_active=self.engine.control_stream_active,
            recent_collisions=self.engine.recent_collisions(request.recent_events),
        )

    # --------------------------------------------------------- unary: reset
    async def ResetSimulation(self, request: sim_pb2.ResetRequest, context: grpc.aio.ServicerContext):
        # error code demo: refusing an unconfirmed reset
        if not request.confirm:
            await context.abort(
                grpc.StatusCode.FAILED_PRECONDITION,
                "reset requires confirm=true (the phone must ask the user first)",
            )
        self.engine.reset()
        return sim_pb2.ResetReply(ok=True, message="simulation reset")


def _meta(metadata, key: str) -> str:
    if metadata is None:
        return ""
    for k, v in metadata:
        if k.lower() == key:
            return str(v)
    return ""


async def _sleep_until_timeout(delay_s: float, context: grpc.aio.ServicerContext) -> None:
    """Sleep but bail out as soon as the client deadline expires."""
    remaining = context.time_remaining()
    wait = delay_s if remaining is None else min(delay_s, remaining + 0.05)
    await asyncio.sleep(max(0.0, wait))


def build_server(engine: Engine, server_version: str) -> grpc.aio.Server:
    server = grpc.aio.server()
    sim_pb2_grpc.add_SimServiceServicer_to_server(
        SimService(engine, server_version), server
    )
    return server