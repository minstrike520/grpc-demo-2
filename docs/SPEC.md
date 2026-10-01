# gRPC demo with Android and simulated environment

To agents: YOU SHALL NOT EDIT THIS FILE. Please write to new files if you feel like writing anything.
## Human's TODO list

- [ ] 設置電腦—手機的 adb 連線

## Dev Requirements

這個章節的指定是只針對 minstrike520（本專案主要開發者）的環境設置的。與本章節相關的常數
、配置盡量不要寫死在程式裡。

- 裝置互聯的需求使用 tailscale 滿足。我的 tailscale magic dns 網域名會是 `(tailscale name).tail544a05.ts.net`。
- 開發會在 Arch Linux（tailscale name: laptop）上。
- 模擬環境在 prod 時會在我的 macbook（tailscale name: macbook-neo）上。
- 在 使用 adb 連接手機進行測試。使用我的手機（tailscale name: xiaomi-13t-pro）。
- 在 laptop 上有定義 ssh 連線目標在 `~/.ssh/config` 上。macbook-neo 與 xiaomi-13t-pro 都設置好了 pubkey，理論上可以直接連通。但是在開發時請都把模擬環境跑在 laptop 上，開發時我會把 macbook-neo 關著。然後需要用到 xiaomi-13t-pro 時，請立刻暫停動作，並提示我連線。
- know your limits:
	- （與本專案無直接關係）你最擅長的是 http 測試與 web 應用開發，因為測試起來最容易、最輕量。
	- 不要嘗試用視覺觀察 app layout 或模擬環境的狀態。
	- 你無法操作真實手機的傾斜角度。
	- 當某些測試或動作超出你的能力，請先將不相關的做完，然後暫停動作，並提示我進行實機測試。此時要是可以正常編譯的狀態；並且也要告訴我具體要做哪些事情。
## Project's Focus

- 展現 gRPC 能力與特性的 demo
- 呼應後續「gRPC 應用於機器人訓練虛實整合環境」的主題

## System Outline

- 使用「感測裝置」（此為 Android 手機）連接到「模擬環境」（此為 Python Server）進行設計。
- 使用感測裝置對模擬環境進行干預，而模擬環境的狀態也回饋到感測裝置上。

## Sensing Device

- 程式語言使用 Kotlin。
	- 使用 grpc-kotlin
- 使用手機的傾角感測器進行觀測。
	- 使用 rotation vector 換算 roll/pitch 傾角。使用角度而非角速度。
	- roll/petch 使用 `(vx, vy)`，線性映射並夾在 max speed
- 小車碰到物品邊界時，手機振動一次。

## Simulation Server

- 使用 python。使用 uv 管理。
- 模擬一個具有若干障礙物的平台，平台邊界有牆壁；平台上有一台可以往前後左右 360deg 平移、但不能轉彎（沒有轉彎概念）的小車。
- 提供模擬環境的視覺渲染監視器，將環境狀態完整且即時地展現。web or python-native interface，請任選一個你覺得實作上最輕鬆的展示方式。注意，這個監視器可能跟 server 在同一台裝置，也可能可以在別台裝置，但總之不會是跑在感測裝置的應用程式上。

## gRPC


 - 手機和伺服器之間開一條「兩邊可以同時互相送訊息」的通道，另外再加兩條「你問一句、我回一句」的單次問答通道。三條通道各管一件事：主通道送控制和收碰撞通知，兩條問答通道負責查資訊和重置模擬。
 - 要處理 gRPC 的三個附加功能。metadata 是傳訊息時夾帶的額外資訊；deadline 是限制伺服器要在多久內回應；錯誤碼是失敗時伺服器回報的原因。
 - 撞到東西後 0.3 秒內不重複通知，避免手機連續震個不停。
 - 碰撞力道用 0 到 1 的數字表示。手機拿這個數字決定震多久、震多強；數字越大震越久越大力。
 - 連線斷掉時，伺服器把控制量歸零，小車靠慣性慢慢停下來。這樣不會帶著手機斷線前最後一個指令一直往前衝。
 - 斷線後自動重連
 - 手機和伺服器的連線，要掛在整個 App 層級，不要掛在單一畫面上。這樣手機轉個方向、畫面被重建時，連線不會因此斷掉，也不用重新連。
