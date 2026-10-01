"""Deterministic 2D physics for the demo.

World: a rectangular platform with walls at the boundary and circular obstacles.
The car is a point-with-radius that translates freely (no heading) — "360 deg
translation, no turning".

Control input is a normalized (vx, vy) in [-1, 1]; velocity chases the control
target smoothly, and when control is zeroed (which the gRPC service does on
disconnect) friction makes the car coast to a stop.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

FLOAT_EPS = 1e-9


@dataclass
class Obstacle:
    id: str
    x: float
    y: float
    radius: float


@dataclass
class Collision:
    force: float  # normalized 0..1
    what: str  # "wall" or obstacle id
    sim_time_s: float


@dataclass
class State:
    x: float
    y: float
    vx: float
    vy: float
    control_vx: float
    control_vy: float


class Simulation:
    def __init__(
        self,
        width: float,
        height: float,
        obstacles: list[tuple[float, float, float]] | list[list[float]],
        *,
        max_speed: float = 3.0,
        acceleration: float = 4.0,
        friction_keep: float = 0.35,
        restitution: float = 0.25,
        car_radius: float = 0.35,
        collision_debounce_s: float = 0.3,
        rng_seed: int = 42,
    ) -> None:
        self.width = width
        self.height = height
        self.max_speed = max_speed
        self.acceleration = acceleration
        self.friction_keep = friction_keep
        self.restitution = restitution
        self.car_radius = car_radius
        self.debounce_s = collision_debounce_s
        self.obstacles = [Obstacle(f"obs-{i}", *o[:3]) for i, o in enumerate(obstacles)]

        self._rng = __import__("random").Random(rng_seed)
        self.reset()

    # ------------------------------------------------------------------ api
    def reset(self, with_car: bool = True) -> None:
        """Reset the world. with_car=False keeps the current car position (used
        to replay a scenario)."""
        if with_car:
            self.x = self.width / 2.0
            self.y = self.height / 2.0
            self.vx = 0.0
            self.vy = 0.0
        self.sim_time_s = 0.0
        self.collision_count = 0
        self.last_notify_s = -1e9
        self.recent_collisions: list[Collision] = []
        self._clear_spawn_overlap()

    def step(self, control_vx: float, control_vy: float, dt: float) -> list[Collision]:
        """Advance one fixed timestep. Returns collision events to notify."""
        # smooth approach to the control target velocity
        target_vx = max(-1.0, min(1.0, control_vx)) * self.max_speed
        target_vy = max(-1.0, min(1.0, control_vy)) * self.max_speed
        k = self.acceleration * dt
        self.vx += (target_vx - self.vx) * min(1.0, k)
        self.vy += (target_vy - self.vy) * min(1.0, k)

        # friction so that zero input => coast to a stop
        keep = self.friction_keep ** dt
        self.vx *= keep
        self.vy *= keep

        self.x += self.vx * dt
        self.y += self.vy * dt

        events = self._resolve_collisions()
        self.sim_time_s += dt
        return events

    # --------------------------------------------------------------- state
    def snapshot(self) -> State:
        return State(
            x=self.x,
            y=self.y,
            vx=self.vx,
            vy=self.vy,
            control_vx=0.0,
            control_vy=0.0,
        )

    def car_polygon(self) -> list[tuple[float, float]]:
        """Square around the car for rendering (no heading concept)."""
        r = self.car_radius
        return [
            (self.x - r, self.y - r),
            (self.x + r, self.y - r),
            (self.x + r, self.y + r),
            (self.x - r, self.y + r),
        ]

    # ------------------------------------------------------------- collision
    def _resolve_collisions(self) -> list[Collision]:
        hits: list[tuple[float, str]] = []  # (impact_speed, what)

        # walls (axis-aligned, two axes)
        if self.x - self.car_radius < 0.0:
            self.x = self.car_radius
            if self.vx < 0:
                impact = abs(self.vx)
                self.vx = -self.vx * self.restitution
                hits.append((impact, "wall-left"))
        elif self.x + self.car_radius > self.width:
            self.x = self.width - self.car_radius
            if self.vx > 0:
                impact = abs(self.vx)
                self.vx = -self.vx * self.restitution
                hits.append((impact, "wall-right"))
        if self.y - self.car_radius < 0.0:
            self.y = self.car_radius
            if self.vy < 0:
                impact = abs(self.vy)
                self.vy = -self.vy * self.restitution
                hits.append((impact, "wall-bottom"))
        elif self.y + self.car_radius > self.height:
            self.y = self.height - self.car_radius
            if self.vy > 0:
                impact = abs(self.vy)
                self.vy = -self.vy * self.restitution
                hits.append((impact, "wall-top"))

        # obstacles
        for o in self.obstacles:
            dx = self.x - o.x
            dy = self.y - o.y
            dist = math.hypot(dx, dy)
            min_dist = o.radius + self.car_radius
            if dist < min_dist and dist > FLOAT_EPS:
                nx, ny = dx / dist, dy / dist
                overlap = min_dist - dist
                self.x += nx * overlap
                self.y += ny * overlap
                vn = self.vx * nx + self.vy * ny
                if vn < 0:
                    self.vx -= (1 + self.restitution) * vn * nx
                    self.vy -= (1 + self.restitution) * vn * ny
                    impact = abs(vn)
                    hits.append((impact, o.id))

        return [c for speed, what in hits if (c := self._notify(speed, what)) is not None]

    def _notify(self, impact_speed: float, what: str) -> Collision | None:
        if impact_speed <= 1e-6:
            return None
        if self.sim_time_s - self.last_notify_s < self.debounce_s:
            return None
        self.last_notify_s = self.sim_time_s
        force = min(1.0, impact_speed / self.max_speed)
        c = Collision(force=force, what=what, sim_time_s=self.sim_time_s)
        self.collision_count += 1
        self.recent_collisions.append(c)
        if len(self.recent_collisions) > 20:
            self.recent_collisions.pop(0)
        return c

    def _clear_spawn_overlap(self) -> None:
        # nudge the car to a non-overlapping spot (nothing overlapping at the
        # default spawn, but be safe for custom obstacle layouts)
        for _ in range(50):
            stuck = False
            for o in self.obstacles:
                if math.hypot(self.x - o.x, self.y - o.y) < o.radius + self.car_radius + 1e-6:
                    stuck = True
                    break
            if not stuck:
                return
            self.x = self._rng.uniform(self.car_radius, self.width - self.car_radius)
            self.y = self._rng.uniform(self.car_radius, self.height - self.car_radius)