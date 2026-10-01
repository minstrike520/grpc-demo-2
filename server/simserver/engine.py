"""Async engine: owns the Simulation and drives it on the event loop.

- steps the physics at a fixed rate
- tracks exactly one active control source (the phone's main stream)
- broadcasts simulation events (collisions + periodic snapshots) to subscribers
- accepts commands (reset) from the gRPC service

All sim state mutation happens inside the engine task, so there is exactly one
owner of the Simulation object; other components only read snapshots.
"""

from __future__ import annotations

import asyncio
import time

import sim_pb2

from .config import SimConfig
from .physics import Simulation


class Engine:
    def __init__(self, cfg: SimConfig) -> None:
        self.cfg = cfg
        self.sim = Simulation(
            cfg.platform_width,
            cfg.platform_height,
            [list(o) for o in cfg.obstacles],
            max_speed=cfg.max_speed,
            acceleration=cfg.acceleration,
            friction_keep=cfg.friction_keep,
            restitution=cfg.restitution,
            car_radius=cfg.car_radius,
            collision_debounce_s=cfg.collision_debounce_s,
        )
        self.started_at = time.monotonic()

        # control source management (single writer wins)
        self._control = (0.0, 0.0)
        self._control_stream: object | None = None

        self._subscribers: dict[int, asyncio.Queue[sim_pb2.SimEvent]] = {}
        self._next_sub_id = 0
        self._last_state_push = 0.0

    # ---------------------------------------------------------------- control
    def set_control(self, stream: object, vx: float, vy: float) -> None:
        """Called by the Control RPC handler on every message."""
        if stream is self._control_stream:
            self._control = (max(-1.0, min(1.0, vx)), max(-1.0, min(1.0, vy)))

    def attach_control(self, stream: object) -> None:
        """New main stream attached - it becomes the single control source."""
        self._control_stream = stream

    def detach_control(self, stream: object) -> None:
        """Stream ended. If it was the current source, zero the control so the
        car coasts to a stop (spec: no runaway car on disconnect)."""
        if stream is self._control_stream:
            self._control_stream = None
            self._control = (0.0, 0.0)

    @property
    def control(self) -> tuple[float, float]:
        return self._control

    @property
    def control_stream_active(self) -> bool:
        return self._control_stream is not None

    # ------------------------------------------------------------ subscribers
    def subscribe(self) -> tuple[int, asyncio.Queue[sim_pb2.SimEvent]]:
        self._next_sub_id += 1
        q: asyncio.Queue[sim_pb2.SimEvent] = asyncio.Queue(maxsize=256)
        self._subscribers[self._next_sub_id] = q
        return self._next_sub_id, q

    def unsubscribe(self, sub_id: int) -> None:
        self._subscribers.pop(sub_id, None)

    def _broadcast(self, ev: sim_pb2.SimEvent) -> None:
        for q in list(self._subscribers.values()):
            try:
                q.put_nowait(ev)
            except asyncio.QueueFull:
                # slow consumer: drop the oldest snapshots to keep it responsive
                try:
                    q.get_nowait()
                    q.put_nowait(ev)
                except asyncio.QueueEmpty:
                    pass
                except asyncio.QueueFull:
                    pass

    def reset(self) -> None:
        """Reset the simulation to its initial state.

        Safe to call from any task on this event loop: Simulation.step() and
        the engine tick are synchronous (no await between start and end of a
        tick), so this never interleaves with a step.
        """
        self.sim.reset()
        self._last_state_push = 0.0

    # ------------------------------------------------------------------ run
    async def run(self) -> None:
        dt = 1.0 / self.cfg.physics_hz
        state_interval = 1.0 / self.cfg.state_hz
        while True:
            vx, vy = self._control
            collisions = self.sim.step(vx, vy, dt)

            for c in collisions:
                self._broadcast(
                    sim_pb2.SimEvent(
                        collision=sim_pb2.CollisionEvent(
                            force=round(c.force, 4),
                            what=c.what,
                            timestamp_ms=int(time.time() * 1000),
                        )
                    )
                )

            if self.sim.sim_time_s - self._last_state_push >= state_interval:
                self._last_state_push = self.sim.sim_time_s
                self._broadcast(self.state_event())

            await asyncio.sleep(dt)

    # --------------------------------------------------------------- status
    def state_event(self) -> sim_pb2.SimEvent:
        return sim_pb2.SimEvent(state=self.make_state())

    def make_state(self) -> sim_pb2.StateSnapshot:
        s = self.sim
        return sim_pb2.StateSnapshot(
            x=s.x,
            y=s.y,
            vx=s.vx,
            vy=s.vy,
            platform_width=self.cfg.platform_width,
            platform_height=self.cfg.platform_height,
            obstacles=[
                sim_pb2.Obstacle(id=o.id, x=o.x, y=o.y, radius=o.radius)
                for o in s.obstacles
            ],
            car_radius=s.car_radius,
            control_vx=self._control[0],
            control_vy=self._control[1],
        )

    def uptime_ms(self) -> int:
        return int((time.monotonic() - self.started_at) * 1000)

    def recent_collisions(self, n: int) -> list[sim_pb2.CollisionEvent]:
        out: list[sim_pb2.CollisionEvent] = []
        for c in self.sim.recent_collisions[-n:]:
            out.append(
                sim_pb2.CollisionEvent(
                    force=round(c.force, 4),
                    what=c.what,
                    timestamp_ms=int(self.sim.sim_time_s * 1000),
                )
            )
        return out