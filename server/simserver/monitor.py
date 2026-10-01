"""Visual monitor for the simulation — a small web app.

- `/`          : a single HTML page that renders the platform, obstacles and
                 the car on a <canvas> in real time (WebSocket feed).
- `/ws`        : websocket pushing JSON state ~20 Hz.
- `/api/state` : same JSON state, one shot (handy for curl / debugging).

The monitor can run on any machine that can reach the server (e.g. the dev
laptop or the prod macbook) — never on the phone app itself.
"""

from __future__ import annotations

import asyncio
import json
import time

from aiohttp import web
from aiohttp import WSMsgType

from .engine import Engine

INDEX_HTML = """<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<title>gRPC sim monitor</title>
<style>
  body { font-family: system-ui, sans-serif; background:#111; color:#ddd; margin:0; padding:24px; }
  h1 { font-size: 16px; color:#888; font-weight: 500; }
  #wrap { display:inline-block; position:relative; }
  canvas { background:#1c2024; border:2px solid #39414a; border-radius:8px; }
  #hud { margin-top:10px; font-size:13px; color:#9aa4ae; }
  #hud b { color:#e8edf2; font-variant-numeric: tabular-nums; }
  .flash { stroke:#ff5b5b !important; stroke-width:3; }
  .obstacle { fill:#55606b; }
  .car { fill:#4fc3f7; stroke:#16404f; }
  .wall { stroke:#7d8892; stroke-width:3; fill:none; }
</style>
</head>
<body>
<h1>gRPC demo — 模擬環境監視器</h1>
<div id="wrap">
  <canvas id="cv" width="800" height="480"></canvas>
</div>
<div id="hud">
  car <b id="pos">-,-</b> | vel <b id="vel">-,-</b> m/s |
  control <b id="ctl">-,-</b> | collision <b id="col">—</b>
</div>
<script>
const cv = document.getElementById('cv'), ctx = cv.getContext('2d');
let state = null;

function draw() {
  if (!state) return;
  const W = state.platform_width, H = state.platform_height;
  const k = Math.min(cv.width / W, cv.height / H);
  const ox = (cv.width - W*k)/2, oy = (cv.height - H*k)/2;
  const tx = x => ox + x*k, ty = y => oy + (H - y)*k; // y flipped: world y up

  ctx.clearRect(0, 0, cv.width, cv.height);
  // platform / walls
  ctx.strokeStyle = '#7d8892'; ctx.lineWidth = 3;
  ctx.strokeRect(tx(0), ty(H)-0, W*k, H*k);
  // obstacles
  for (const o of state.obstacles) {
    ctx.beginPath();
    ctx.arc(tx(o.x), ty(o.y), o.radius*k, 0, 2*Math.PI);
    ctx.fillStyle = '#55606b'; ctx.fill();
    ctx.strokeStyle = '#7d8892'; ctx.lineWidth = 1; ctx.stroke();
    ctx.fillStyle = '#8fa0ad'; ctx.font = '11px sans-serif'; ctx.textAlign='center';
    ctx.fillText(o.id, tx(o.x), ty(o.y)-o.radius*k-4);
  }
  // car (square, no heading)
  const r = state.car_radius*k;
  ctx.fillStyle = '#4fc3f7';
  ctx.strokeStyle = state.flash ? '#ff5b5b' : '#16404f';
  ctx.lineWidth = state.flash ? 3 : 2;
  ctx.fillRect(tx(state.x)-r, ty(state.y)-r, r*2, r*2);
  ctx.strokeRect(tx(state.x)-r, ty(state.y)-r, r*2, r*2);
  // control vector hint
  if (Math.abs(state.control_vx) + Math.abs(state.control_vy) > 0.01) {
    ctx.strokeStyle = '#ffd54f'; ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(tx(state.x), ty(state.y));
    ctx.lineTo(tx(state.x) + state.control_vx*2*k, ty(state.y) - state.control_vy*2*k);
    ctx.stroke();
  }
  // hud
  document.getElementById('pos').textContent = state.x.toFixed(2) + ',' + state.y.toFixed(2);
  document.getElementById('vel').textContent = state.vx.toFixed(2) + ',' + state.vy.toFixed(2);
  document.getElementById('ctl').textContent = state.control_vx.toFixed(2) + ',' + state.control_vy.toFixed(2);
  if (state.last_collision) {
    document.getElementById('col').textContent = state.last_collision;
    setTimeout(() => { if (state) state.last_collision = null; }, 400);
  }
  state.flash = !!state.last_collision;
}

function wsConnect() {
  const ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
  ws.onmessage = e => { state = JSON.parse(e.data); draw(); };
  ws.onclose = () => setTimeout(wsConnect, 1000);
}
wsConnect();
</script>
</body>
</html>
"""


def _json_state(engine: Engine) -> dict:
    s = engine.sim
    control_vx, control_vy = engine.control
    last = s.recent_collisions[-1] if s.recent_collisions else None
    return {
        "platform_width": engine.cfg.platform_width,
        "platform_height": engine.cfg.platform_height,
        "x": s.x,
        "y": s.y,
        "vx": s.vx,
        "vy": s.vy,
        "car_radius": s.car_radius,
        "control_vx": control_vx,
        "control_vy": control_vy,
        "control_stream_active": engine.control_stream_active,
        "collision_count": s.collision_count,
        "last_collision": None if last is None else {
            "what": last.what,
            "force": round(last.force, 3),
        },
        "obstacles": [
            {"id": o.id, "x": o.x, "y": o.y, "radius": o.radius} for o in s.obstacles
        ],
    }


def build_app(engine: Engine) -> web.Application:
    app = web.Application()

    async def index(_: web.Request) -> web.Response:
        return web.Response(text=INDEX_HTML, content_type="text/html")

    async def api_state(_: web.Request) -> web.Response:
        return web.json_response(_json_state(engine))

    async def ws_handler(request: web.Request) -> web.WebSocketResponse:
        ws = web.WebSocketResponse(heartbeat=5)
        await ws.prepare(request)
        try:
            while True:
                await ws.send_json(_json_state(engine))
                await asyncio.sleep(0.05)
        except (asyncio.CancelledError, ConnectionResetError):
            pass
        finally:
            if not ws.closed:
                await ws.close()
        return ws

    app.router.add_get("/", index)
    app.router.add_get("/api/state", api_state)
    app.router.add_get("/ws", ws_handler)
    return app


async def run_monitor(engine: Engine, host: str, port: int) -> None:
    runner = web.AppRunner(build_app(engine))
    await runner.setup()
    site = web.TCPSite(runner, host, port)
    await site.start()
    # note: never returns; keep a reference so the runner is not GC'd
    global _runner
    _runner = runner


_runner = None