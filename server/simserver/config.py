"""Configuration for the simulation server — everything from environment variables.

The spec says dev-machine details (tailscale names, hosts) must not be hardcoded;
that costs us nothing here since we bind 0.0.0.0 and let the client choose the address.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field


def _env_list(name: str, default: str) -> list[float]:
    raw = os.environ.get(name)
    if raw is None:
        return [float(x) for x in default.split(",")]
    return [float(x) for x in raw.split(",")]


def _env_obstacles(name: str, default: str) -> list[list[float]]:
    raw = os.environ.get(name)
    if raw is None:
        return [list(map(float, o)) for o in json.loads(default)]
    return [list(map(float, o)) for o in json.loads(raw)]


@dataclass
class SimConfig:
    # --- network -----------------------------------------------------------
    grpc_host: str = os.environ.get("SIM_GRPC_HOST", "0.0.0.0")
    grpc_port: int = int(os.environ.get("SIM_GRPC_PORT", "50051"))
    web_host: str = os.environ.get("SIM_WEB_HOST", "0.0.0.0")
    web_port: int = int(os.environ.get("SIM_WEB_PORT", "8080"))

    # --- world -------------------------------------------------------------
    platform_width: float = float(os.environ.get("SIM_PLATFORM_WIDTH", "10.0"))
    platform_height: float = float(os.environ.get("SIM_PLATFORM_HEIGHT", "6.0"))
    # obstacles as JSON list of [x, y, radius] in meters
    obstacles: list[list[float]] = field(
        default_factory=lambda: _env_obstacles(
            "SIM_OBSTACLES",
            "[[3.0, 2.0, 0.6], [7.0, 1.5, 0.5], [5.0, 4.5, 0.7], [9.0, 4.8, 0.4]]",
        )
    )
    car_radius: float = float(os.environ.get("SIM_CAR_RADIUS", "0.35"))

    # --- motion ------------------------------------------------------------
    max_speed: float = float(os.environ.get("SIM_MAX_SPEED", "3.0"))  # m/s
    # how fast velocity approaches the control target (1/s); higher = snappier
    acceleration: float = float(os.environ.get("SIM_ACCELERATION", "4.0"))
    # velocity kept after each second with zero input (e.g. 0.35 => -65%/s);
    # this is what makes the car "coast to a stop" after disconnect
    friction_keep: float = float(os.environ.get("SIM_FRICTION_KEEP", "0.35"))
    restitution: float = float(os.environ.get("SIM_RESTITUTION", "0.25"))

    # --- events / timing ---------------------------------------------------
    physics_hz: int = int(os.environ.get("SIM_PHYSICS_HZ", "100"))
    state_hz: int = int(os.environ.get("SIM_STATE_HZ", "10"))
    # no collision notification for this many seconds after the last one
    collision_debounce_s: float = float(os.environ.get("SIM_COLLISION_DEBOUNCE_S", "0.3"))

    server_version: str = os.environ.get("SIM_SERVER_VERSION", "0.1.0")