"""End-to-end gRPC integration tests: spin up the real server in-process and
talk to it like the phone would.

Covers the spec's gRPC items:
  - bidirectional control channel (controls out, collisions+snapshots in)
  - unary query channel (GetStatus) with client deadline
  - unary reset channel (ResetSimulation) with confirmation + error codes
  - metadata round-trip (x-device-id)
  - errors: FAILED_PRECONDITION, DEADLINE_EXCEEDED, UNAVAILABLE
  - disconnect => server zeroes the control so the car coasts to a stop
"""

from __future__ import annotations

import asyncio

import grpc
import pytest

import sim_pb2
import sim_pb2_grpc
from simserver.config import SimConfig
from simserver.engine import Engine
from simserver.service import SimService

pytestmark = pytest.mark.asyncio


@pytest.fixture
async def env():
    cfg = SimConfig()
    engine = Engine(cfg)
    engine_task = asyncio.create_task(engine.run())
    server = grpc.aio.server()
    sim_pb2_grpc.add_SimServiceServicer_to_server(SimService(engine, cfg.server_version), server)
    port = server.add_insecure_port("127.0.0.1:0")
    await server.start()
    channel = grpc.aio.insecure_channel(f"127.0.0.1:{port}")
    await channel.channel_ready()
    stub = sim_pb2_grpc.SimServiceStub(channel)
    try:
        yield engine, stub
    finally:
        await channel.close()
        await server.stop(grace=0.5)
        engine_task.cancel()


def meta(device="xiaomi-13t-pro", app="1.0.0"):
    return (
        ("x-device-id", device),
        ("x-app-version", app),
    )


async def collect(call, seconds: float, predicate=None):
    """Collect events from a bidi call for `seconds`, optionally filtered."""
    out = []
    loop = asyncio.get_running_loop()
    deadline = loop.time() + seconds
    try:
        async for ev in call:
            if loop.time() > deadline:
                break
            if predicate is None or predicate(ev):
                out.append(ev)
    except (StopAsyncIteration, asyncio.TimeoutError, grpc.aio.AioRpcError):
        pass
    return out


async def test_metadata_round_trip(env):
    _, stub = env
    reply = await stub.GetStatus(
        sim_pb2.StatusRequest(), metadata=meta(device="pixel-7-pro")
    )
    assert reply.client_device_id == "pixel-7-pro"
    assert reply.server_version == SimConfig().server_version


async def test_bidi_stream_control_and_snapshots(env):
    _, stub = env
    call = stub.Control(metadata=meta())
    await call.write(sim_pb2.ControlMessage(vx=0.5, vy=0.0, seq=0))
    await call.write(sim_pb2.ControlMessage(vx=0.5, vy=0.5, seq=1))

    states = await collect(call, 0.6, predicate=lambda ev: ev.HasField("state"))
    assert states
    # the server applies our control to the car
    assert abs(states[-1].state.control_vx - 0.5) < 1e-4
    assert states[-1].state.vx > 0.0
    # platform data reaches the phone so it can render the same world
    assert states[-1].state.platform_width == 10.0
    assert states[-1].state.obstacles

    call.cancel()


async def test_disconnect_zeroes_control(env):
    engine, stub = env
    del engine  # unused, keep fixture simple
    call = stub.Control(metadata=meta())
    await call.write(sim_pb2.ControlMessage(vx=1.0, vy=0.0, seq=1))
    await asyncio.sleep(0.2)
    call.cancel()  # abrupt client disconnect
    await asyncio.sleep(0.3)  # let the server notice

    reply = await stub.GetStatus(sim_pb2.StatusRequest())
    assert reply.control_stream_active is False
    assert reply.state.control_vx == 0.0
    assert reply.state.control_vy == 0.0
    # car still coasts (keeps moving despite zeroed control)
    assert reply.state.vx > 0.0


async def test_reset_requires_confirm(env):
    _, stub = env
    # error code: FAILED_PRECONDITION when not confirmed
    with pytest.raises(grpc.aio.AioRpcError) as excinfo:
        await stub.ResetSimulation(sim_pb2.ResetRequest(confirm=False))
    assert excinfo.value.code() == grpc.StatusCode.FAILED_PRECONDITION

    reply = await stub.ResetSimulation(sim_pb2.ResetRequest(confirm=True))
    assert reply.ok
    status = await stub.GetStatus(sim_pb2.StatusRequest())
    assert status.state.x == 5.0 and status.state.y == 3.0  # back at center


async def test_deadline_exceeded(env):
    _, stub = env
    # server pretends to take 10 s; the client deadline cuts it off
    with pytest.raises(grpc.aio.AioRpcError) as excinfo:
        await stub.GetStatus(
            sim_pb2.StatusRequest(simulate_latency_ms=10_000),
            timeout=0.5,
        )
    assert excinfo.value.code() == grpc.StatusCode.DEADLINE_EXCEEDED


async def test_out_of_range_control_rejected_not_fatal(env):
    _, stub = env
    call = stub.Control(metadata=meta())
    await call.write(sim_pb2.ControlMessage(vx=99.0, vy=0.0, seq=2))
    events = await collect(call, 0.5)
    rejected = [
        e for e in events
        if e.HasField("collision") and "control-out-of-range" in e.collision.what
    ]
    assert rejected, "expected an out-of-range rejection event"
    # stream stays usable afterwards
    await call.write(sim_pb2.ControlMessage(vx=-0.5, vy=0.0, seq=3))
    states = await collect(call, 0.4, predicate=lambda ev: ev.HasField("state"))
    assert states and abs(states[-1].state.control_vx - (-0.5)) < 1e-4
    call.cancel()


async def test_unavailable_when_server_down(env):
    _, stub = env
    del stub
    bogus = grpc.aio.insecure_channel("127.0.0.1:1")
    bogus_stub = sim_pb2_grpc.SimServiceStub(bogus)
    try:
        with pytest.raises(grpc.aio.AioRpcError) as excinfo:
            await bogus_stub.GetStatus(sim_pb2.StatusRequest())
        assert excinfo.value.code() == grpc.StatusCode.UNAVAILABLE
    finally:
        await bogus.close()