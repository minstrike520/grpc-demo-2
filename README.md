# gRPC Demo — Android 感測裝置 × Python 模擬環境

呼應「gRPC 應用於機器人訓練虛實整合環境」的 demo：**感測裝置（Android 手機）**透過
gRPC 連到**模擬環境（Python server）**——手機傾角控制平台上的小車，小車的狀態即時回饋
到手機畫面上，撞到障礙物/牆壁時手機震動。

```
┌────────────────────┐                    ┌──────────────────────────┐
│  Android (Kotlin)  │   gRPC (3 條通道)  │  Python 模擬環境 (uv)     │
│                    │                     │                          │
│  傾角感測器          │  ── main ────────► │  10×6m 平台 + 障礙物       │
│  (rotation vector   │   bidirectional     │  小車 (360° 平移, 不轉彎)  │
│   → roll/pitch      │   control+snapshot  │  碰撞偵測 + 0.3s 去抖     │
│   → vx,vy)          │  ◄── collision ──── │  慣性/摩擦 (斷線滑行停止)  │
│                    │                     │                          │
│  顯示狀態/小車位置     │  ── GetStatus ────► │                          │
│  碰撞震動(力道 0..1)  │  ◄── 狀態資訊 ───── │  Web 監視器              │
│                    │  ── Reset ─────────►│   (Canvas + WebSocket)    │
│  App 層級連線,自動重連│  ◄── 重置結果 ───── │   http://<server>:8080    │
└────────────────────┘                     └──────────────────────────┘
```

**gRPC 三條通道（各管一件事）：**

| 通道 | RPC | 用途 |
|---|---|---|
| 主通道 | `Control` (bidirectional stream) | 手機送控制量 `(vx, vy)`，server 回碰撞通知 + 狀態快照 |
| 查詢通道 | `GetStatus` (unary) | 查整份模擬狀態（client 設 2s deadline） |
| 重置通道 | `ResetSimulation` (unary) | 重置模擬（需 `confirm=true`，否則 `FAILED_PRECONDITION`） |

**gRPC 三個附加功能都有處理到：**

- **metadata**：每次呼叫夾帶 `x-device-id` / `x-app-version`。server 讀取、log、
  並在 `StatusReply.client_device_id` 回傳，手機畫面上看得到「server 認得我是誰」；
  server 的回應也帶 `x-server-version` metadata。
- **deadline**：unary 兩條通道都在 client 端設 2 秒 deadline（逾時 → `DEADLINE_EXCEEDED`）。
  手機 UI 有「慢速查詢」按鈕（server 模擬延遲 10 秒）可以實際看到 deadline 生效。
  server 端也會檢查 `context.time_remaining()`，來不及就提前中止。
- **錯誤碼**：`FAILED_PRECONDITION`（重置沒確認）、`INVALID_ARGUMENT`
  （控制量超出 [-1,1] 會被拒收，以事件回報、不殺掉主通道）、`DEADLINE_EXCEEDED`
  （deadline）、`UNAVAILABLE`（連不上 server → 觸發自動重連）。

**其他規格項目：**

- 碰撞通知 server 端去抖 **0.3 秒**，手機不會連續震個不停。
- 碰撞力道是 **0..1** 的數字，手機拿它決定震動時間與強度（越大越久越強）。
- **斷線時 server 把控制量歸零**，小車靠慣性/摩擦慢慢停下來（不會帶著最後指令狂奔）。
- 手機端**自動重連**（指數 backoff 1s→10s）。
- **連線掛在 App 層級**（`SimApplication` + `GrpcManager`），旋轉螢幕/畫面重建不會斷線。

---

## 目錄結構

```
proto/sim.proto           唯一的 proto 定義（3 個 RPC）
server/                   Python 模擬環境 (uv, grpcio aio)
  simserver/              config / physics / engine / service (gRPC) / monitor (web) / __main__
  tests/                  physics + gRPC 整合測試 (pytest)
  sim_pb2*.py             生成的 stub（已提交）
android/                  Android App (Kotlin, grpc-kotlin)
  app/src/main/java/sim/  生成的 message + stub（已提交，不需要 protoc 再跑）
  app/src/main/java/com/example/grpcsim/   應用程式碼
tools/
  gen_proto.sh            重新產生 stub（python 與 android）的腳本
  smoke_client.py         模擬手機行為的 CLI 測試客戶端
```

## 快速開始

### 1. 跑模擬環境（開發時在 laptop 上跑）

```bash
cd server
uv sync
uv run python -m simserver          # gRPC :50051, web :8080
```

- Web 監視器：http://laptop.tail544a05.ts.net:8080（或 `http://<任何設備>:8080`）
  —— 即時 Canvas 畫面 + `/api/state` JSON + `/ws` WebSocket 推播。
- 常用環境變數（都不寫死在程式裡）：

| 變數 | 預設 | 說明 |
|---|---|---|
| `SIM_GRPC_HOST` / `SIM_GRPC_PORT` | `0.0.0.0` / `50051` | gRPC 綁定位址 |
| `SIM_WEB_HOST` / `SIM_WEB_PORT` | `0.0.0.0` / `8080` | 監視器位址 |
| `SIM_MAX_SPEED` | `3.0` | 最大速度 m/s（控制量 × 此值） |
| `SIM_COLLISION_DEBOUNCE_S` | `0.3` | 碰撞通知去抖秒數 |
| `SIM_PLATFORM_WIDTH/HEIGHT` `SIM_OBSTACLES` `SIM_CAR_RADIUS` `SIM_FRICTION_KEEP` `SIM_RESTITUTION` `SIM_PHYSICS_HZ` `SIM_STATE_HZ` | 見 `config.py` | 世界/物理參數 |

> prod 時模擬環境改跑在 macbook-neo 上：在那台跑同樣指令即可，手機端改連
> `macbook-neo.tail544a05.ts.net:50051`。

### 2. 測試 server（不需要手機）

```bash
cd server
uv run pytest            # 7 個 physics test + 7 個 gRPC 整合 test
uv run python ../tools/smoke_client.py 127.0.0.1:50051
```

`smoke_client.py` 會模擬手機行為：metadata 回程、驅車撞牆收碰撞事件、斷線後控制歸零、
無確認重置被拒、慢速查詢觸發 deadline。

### 3. 手機 App

```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
# 產出 app/build/outputs/apk/debug/app-debug.apk
```

**伺服器位址**（不寫死；tailscale/主機名都不在程式碼裡）：

- 編譯時：`./gradlew -Psim.server.address=laptop.tail544a05.ts.net:50051 assembleDebug`
- 執行時：App 內點「伺服器位址」直接改（存 SharedPreferences，立即重連）
- 預設：`10.0.2.2:50051`（模擬器 → host loopback）

**裝到手機**（需要實機，見「需要您操作的步驟」）：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# 或直接把 .apk 傳到手機
```

## 手機上的操作

- 開啟 App：傾斜手機即控制（`roll → vx`、`pitch → vy`，線性且夾在 ±1）。
  若方向相反，改 `TiltMapping.SIGN_VX/SIGN_VY`（`FULL_RANGE_DEG` 可調靈敏度）。
- 撞牆/撞障礙物：手機依照力道震動（60–400ms、40–255 強度）。
- 按鈕：
  - **查詢模擬資訊** → GetStatus（unary + 2s deadline）
  - **慢速查詢** → 示範 `DEADLINE_EXCEEDED`
  - **重置模擬** → 帶確認對話框（`confirm=true`）
  - **重置（無確認）** → 示範 `FAILED_PRECONDITION`
- 畫面上有平台/障礙物/小車的即時渲染（來自 server 推送的 state snapshot），
  中央黃色箭頭是 server 目前套用的控制量（斷線會變成消失 → 表示已歸零）。

## 原理說明（寫 code 時對應的規格）

- **主通道 = bidirectional stream**：`Control(stream ControlMessage) returns (stream SimEvent)`。
  手機端 `GrpcManager` 用 `MutableSharedFlow` 做 outbound（慢網路時 DROP_OLDEST，最新指令優先），
  server 用 per-client `asyncio.Queue` 推事件（碰撞即時 + 快照 10Hz）。
- **斷線 → 控制歸零**：server 端 `Control` handler 的 `finally` 呼叫
  `engine.detach_control(stream_id)`（只有「當前控制源」斷開才會歸零，舊重連殘留 stream 不會誤清）。
- **慣性滑行**：物理模擬每步 `v *= friction_keep^dt`，控制為 0 時速度自然衰減到停。
- **自動重連**：`GrpcManager.runLoop()` 收集 control stream；`UNAVAILABLE`/異常 → backoff 重開。
- **App 層級連線**：`SimApplication.onCreate` 建立 `GrpcManager` 並啟動；
  `MainActivity` 只訂閱其 `StateFlow`/`SharedFlow`。旋轉時 Activity 重建、感測器重註冊，連線不動。

## 重新產生 stub

```bash
./tools/gen_proto.sh     # 需要 uv + curl + unzip + java；產出已提交，一般不需要重跑
```

## 需要您操作的步驟（know your limits）

以下超出代理環境能力、需要真人/實機：

1. **設定電腦—手機的 adb 連線**（SPEC 的 Human TODO）：用 USB 或無線 adb 連到
   xiaomi-13t-pro；`adb devices` 看得到即完成。
2. 把手機連到 laptop 的 tailscale（手機裝 Tailscale，加入同一個 tailnet）。
3. 在手機上把伺服器位址改為 `laptop.tail544a05.ts.net:50051`（laptop 上跑著 server）。
4. 實機測試傾斜控制方向：若 vx/vy 相反，改 `TiltMapping` 的 `SIGN_VX`/`SIGN_VY` 重新組裝。
5. 若要觀察監視器：手機/電腦開 http://laptop.tail544a05.ts.net:8080。

> agent 無法：視覺觀察 app 佈局/模擬器畫面、操作真實手機傾角、執行需要實機的測試。
> 到該階段請暫停並由真人接手（server 已由 pytest/smoke client 完整驗證）。