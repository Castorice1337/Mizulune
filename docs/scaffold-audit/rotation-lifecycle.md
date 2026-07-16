# Rotation 生命周期

## 状态模型

| Phase | owner | 语义 |
|---|---|---|
| ACTIVE | active owner | provider 本轮 request 经 smoother 得到 |
| RESET | reset owner | previous target 朝玩家视角回正 |
| IDLE | 无 | `getCurrentRotation()` 必须为 null |

Scaffold priority 50，Clutch priority 60，GodBridgeAssist priority 40。外部 owner 存在时 Scaffold placement 以 conflict 结束，不抢 owner。

## 模式生命周期

- Normal 注册持续 provider；disable 使用 `releaseProvider()` 保留合法 reset。
- On Tick 每次只做同坐标 ephemeral `PosRot` transaction，不创建持续 provider。
- On Tick Snap 在 strict gate 与 provider preflight 通过后先发送 target `PosRot`，再激活 Scaffold provider，由 provider 接管后续 rotation。
- Telly Reset 的 null request 按 phase 0052 约束直接跳过放置。

## 服务端 rotation

`actualServerRotation` 只由 final `writeAndFlush` observer 更新。ephemeral commit、Blink enqueue 和 camera rotation 都不能提前更新该字段。

`theoreticalServerRotation` 在 packet 被发送链接受或进入 Scaffold Blink 队列时更新，代表有序队列尾部的逻辑 rotation。去重使用 theoretical/actual fallback；finder 与 runtime 证据仍读取 actual。

OnTick restore 不能只比较 `actualServerRotation`：target packet 可能正在 Blink 队列中。ephemeral commit 一次性返回 `dispatchRequested`，pipeline 据此保留 restore，避免判断与发送之间重复读取状态。

## 必须保持

1. IDLE 不泄漏历史 synthetic rotation。
2. active/reset/external owner 可区分。
3. no-hit 不调用 managed/ephemeral commit。
4. Normal 不手动发送 flying；OnTick 只发送 frame 坐标固定的 duplicate `PosRot`，禁止 Rot-only transaction。
5. placement owner 与 movement correction owner 来自同一仲裁结果。
6. frame 只消费本轮 resolved rotation。

phase 0050 的 PatchAgent `30/31` 故障已修复，相关 ASM 与 inherited method resolution 有自动化覆盖；当前仍需用户运行确认实际 patch target 数和游戏行为。
