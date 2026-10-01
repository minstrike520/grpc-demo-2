#!/usr/bin/env python3
"""Smoke-test client that mimics what the phone does.

Run from anywhere:
    server/uv run python ../tools/smoke_client.py [host:port]
"""

import asyncio
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "server"))

import grpc
import sim_pb2, sim_pb2_grpc


async def main(addr: str) -> None:
    ch = grpc.aio.insecure_channel(addr)
    await ch.channel_ready()
    stub = sim_pb2_grpc.SimServiceStub(ch)

    # metadata + unary status
    reply = await stub.GetStatus(sim_pb2.StatusRequest(), metadata=(
        ("x-device-id", "smoke-client"), ("x-app-version", "0.1.0"),
    ))
    print(f"status: v{reply.server_version} | device echo: {reply.client_device_id} | "
          f"collisions so far: {reply.collision_count}")

    # main channel: drive into the right wall, collect collisions
    call = stub.Control(metadata=(("x-device-id", "smoke-client"),))
    await call.write(sim_pb2.ControlMessage(vx=1.0, vy=0.0, seq=1))
    loop = asyncio.get_running_loop()
    deadline = loop.time() + 4.0
    hits = 0
    try:
        async for ev in call:
            if ev.HasField("collision"):
                hits += 1
                print(f"collision: {ev.collision.what} force={ev.collision.force:.2f}")
                if hits >= 3:
                    break
            if loop.time() > deadline:
                break
    except grpc.aio.AioRpcError as e:
        print("stream error:", e.code())
    call.cancel()
    await asyncio.sleep(0.3)

    # after disconnect the server must zero the control
    r2 = await stub.GetStatus(sim_pb2.StatusRequest())
    print(f"after disconnect: active={r2.control_stream_active} "
          f"control=({r2.state.control_vx}, {r2.state.control_vy})")

    # reset: FAILED_PRECONDITION without confirm, then success
    try:
        await stub.ResetSimulation(sim_pb2.ResetRequest(confirm=False))
        print("!! unconfirmed reset accepted")
    except grpc.aio.AioRpcError as e:
        print(f"unconfirmed reset -> {e.code().name}")
    ok = await stub.ResetSimulation(sim_pb2.ResetRequest(confirm=True))
    r3 = await stub.GetStatus(sim_pb2.StatusRequest())
    print(f"confirmed reset ok={ok.ok} | car back at ({r3.state.x:.2f}, {r3.state.y:.2f})")

    # deadline demo
    try:
        await stub.GetStatus(sim_pb2.StatusRequest(simulate_latency_ms=10_000), timeout=0.5)
        print("!! slow query not cut off")
    except grpc.aio.AioRpcError as e:
        print(f"slow query (0.5s deadline) -> {e.code().name}")

    await ch.close()
    print("smoke test OK")


if __name__ == "__main__":
    addr = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1:50051"
    asyncio.run(main(addr))
