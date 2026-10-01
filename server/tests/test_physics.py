"""Physics unit tests: boundaries, obstacles, debounce, coast-to-stop."""

from __future__ import annotations

import math

from simserver.physics import Simulation


def make_sim(**kw) -> Simulation:
    defaults = dict(
        width=10.0,
        height=6.0,
        obstacles=[(3.0, 3.0, 1.0)],
        max_speed=3.0,
        acceleration=4.0,
        friction_keep=0.35,
        restitution=0.25,
        car_radius=0.35,
        collision_debounce_s=0.3,
    )
    defaults.update(kw)
    return Simulation(**defaults)


def run(sim: Simulation, vx: float, vy: float, seconds: float, dt: float = 0.01) -> list:
    events = []
    steps = int(seconds / dt)
    for _ in range(steps):
        events += sim.step(vx, vy, dt)
    return events


def test_coast_to_stop_with_zero_control():
    """With zeroed control the car must keep its velocity but decay to ~0."""
    sim = make_sim()
    run(sim, 1.0, 0.0, 1.0)          # accelerate right
    v_before = (sim.vx ** 2 + sim.vy ** 2) ** 0.5
    assert v_before > 1.0            # reached near max speed
    run(sim, 0.0, 0.0, 8.0)          # control cut -> inertia decays
    assert sim.vx < 1e-2
    assert sim.vy == 0.0


def test_wall_stops_car_and_notifies():
    sim = make_sim()
    # drive straight into the right wall from center
    events = run(sim, 1.0, 0.0, 10.0)
    assert sim.x <= sim.width - sim.car_radius + 1e-6
    collision = [e for e in events if e.what == "wall-right"]
    assert collision, "expected a wall-right collision event"
    assert 0.0 <= collision[0].force <= 1.0
    assert sim.collision_count >= 1


def test_collision_force_scales_with_speed():
    slow = make_sim(max_speed=3.0)
    fast = make_sim(max_speed=3.0)
    # heading into the left wall at different speeds
    slow.x, fast.x = 0.4, 0.6
    slow.vx = -0.5
    fast.vx = -3.0
    s_ev = run(slow, 0.0, 0.0, 1.0)
    f_ev = run(fast, 0.0, 0.0, 1.0)
    s_hits = [e for e in s_ev if e.what == "wall-left"]
    f_hits = [e for e in f_ev if e.what == "wall-left"]
    assert s_hits and f_hits
    s_force = s_hits[0].force
    f_force = f_hits[0].force
    assert s_force < f_force, f"slower impact should be weaker, got {s_force} vs {f_force}"
    assert f_force <= 1.0


def test_debounce_prevents_rapid_notifications():
    sim = make_sim()
    # push car against a wall and keep control into it: collisions every step
    sim.x = 0.5
    sim.vx = -3.0
    events = run(sim, -1.0, 0.0, 1.0, dt=0.01)  # 100 steps
    notified = [e for e in events if e.what == "wall-left"]
    # debounce 0.3 s at 100 Hz => at most ~4 notifications in 1 s
    assert len(notified) <= 5, f"debounce not working: {len(notified)} events in 1s"
    assert sim.collision_count == len(notified)
    # and they are spaced ~0.3 s apart
    times = [e.sim_time_s for e in notified]
    for a, b in zip(times, times[1:]):
        assert b - a >= 0.29


def test_obstacle_collision_pushes_out():
    sim = make_sim()
    # start just left of the obstacle, driving straight into it
    sim.x, sim.y = 3.0 - 1.0 - 0.35 - 0.1, 3.0
    sim.vx, sim.vy = 1.0, 0.0
    events = run(sim, 1.0, 0.0, 2.0)
    obs_hits = [e for e in events if e.what == "obs-0"]
    assert obs_hits
    # car should not be inside the obstacle
    dx = sim.x - 3.0
    dy = sim.y - 3.0
    assert math.hypot(dx, dy) >= 1.0 + 0.35 - 1e-6


def test_reset_restores_center():
    sim = make_sim()
    run(sim, 1.0, 0.0, 5.0)
    sim.reset()
    assert sim.x == 5.0 and sim.y == 3.0
    assert sim.vx == 0.0 and sim.vy == 0.0
    assert sim.collision_count == 0


def test_control_clamped_to_unit():
    sim = make_sim()
    run(sim, 5.0, 5.0, 1.0)
    speed = (sim.vx ** 2 + sim.vy ** 2) ** 0.5
    assert speed <= sim.max_speed * math.sqrt(2) + 1e-6