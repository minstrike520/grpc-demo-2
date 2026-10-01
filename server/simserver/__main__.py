"""gRPC sim server — entry point.

Run:  uv run python -m simserver
Env:  SIM_GRPC_HOST / SIM_GRPC_PORT / SIM_WEB_HOST / SIM_WEB_PORT and the other
      SIM_* variables in config.py.
"""

from __future__ import annotations

import asyncio
import logging
import sys

import grpc

from .config import SimConfig
from .engine import Engine
from .monitor import run_monitor
from .service import build_server

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(name)s %(levelname)s %(message)s",
)
log = logging.getLogger("simserver")


async def _full_main() -> None:
    cfg = SimConfig()
    engine = Engine(cfg)
    log.info(
        "platform %.1fx%.1f m, %s obstacles, max_speed %.1f m/s, car_radius %.2f m",
        cfg.platform_width, cfg.platform_height, len(cfg.obstacles),
        cfg.max_speed, cfg.car_radius,
    )

    server = build_server(engine, cfg.server_version)
    server.add_insecure_port(f"{cfg.grpc_host}:{cfg.grpc_port}")
    log.info("gRPC server on %s:%s", cfg.grpc_host, cfg.grpc_port)

    monitor_task = asyncio.create_task(run_monitor(engine, cfg.web_host, cfg.web_port))
    log.info("web monitor on http://%s:%s  (also /api/state and /ws)", cfg.web_host, cfg.web_port)

    engine_task = asyncio.create_task(engine.run())
    try:
        await server.start()
        log.info("ready. press Ctrl-C to stop.")
        await server.wait_for_termination()
    finally:
        engine_task.cancel()
        monitor_task.cancel()
        await asyncio.gather(engine_task, monitor_task, return_exceptions=True)
        await server.stop(grace=1)
        log.info("bye.")


if __name__ == "__main__":
    try:
        asyncio.run(_full_main())
    except KeyboardInterrupt:
        sys.exit(0)