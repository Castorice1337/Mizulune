# Packet 流审计

## 服务端事实

`RotationHandler.actualServerRotation` 只在 `Connection.doSendPacket()` 的 final-write observer 中更新。target、camera、`sentRotation` 或仅进入 Blink 队列的 packet 都不能提前改写它；取消的 packet 不会进入 observer。actual/theoretical 都保留 packet 的 raw continuous yaw，不再 wrap 到 `[-180,180]`。

`theoreticalServerRotation` 在 packet 通过 cancellation gate 并被当前发送链接受后更新；它包含 Scaffold Blink 队尾 rotation。OnTick 去重读取 logical(theoretical/actual fallback)，finder 与最终证据仍读取 actual。

## Scaffold packet 类型

| 类型 | 位置样本 | 用途 |
|---|---:|---|
| vanilla `Pos/PosRot/Rot/StatusOnly` | 按原版 | Normal/OnTickSnap provider 的后续 movement rotation |
| Scaffold `PosRot` | 是，重复 frame 坐标 | Normal/OnTick target 与回正、OnTickSnap target |
| `UseItemOn` | 否 | strict gate 通过后的唯一 placement interaction |
| `SetCarriedItem` | 否 | AutoBlock 热键栏同步 |
| `Swing` | 否 | Swing policy |

Scaffold 不手动发送 Rot-only flying。完整 `PosRot` 必须复用 `ScaffoldTickFrame.playerPosition()`；target/restore 共享同一个坐标与 onGround 快照。ephemeral yaw 以 logical server raw yaw 为 reference 选择最近等价分支，避免多圈转头后的 `AimModulo360`。

## 三种时序

```text
Normal:
  strict -> optional SetCarriedItem -> PosRot(framePos,target)
  -> UseItemOn -> Swing -> PosRot(framePos,player)
  -> vanilla flying(target), provider remains active

On Tick:
  strict -> optional SetCarriedItem -> PosRot(framePos,target)
  -> UseItemOn -> Swing -> PosRot(framePos,player)

On Tick Snap:
  strict -> optional SetCarriedItem -> PosRot(framePos,target)
  -> activate provider -> UseItemOn -> Swing
```

如果 target rotation 已是 logical server rotation，可省略 target `PosRot`；Normal/OnTick player rotation 与 target 相同时可省略回正。

## 为什么不能使用 Rot-only

Grim `Timer` 对每个非 duplicate flying 增加一个 50 ms 客户端 tick。Rot-only 没有 position，不能满足 1.17+ duplicate 判定；一次 OnTick 成功若额外发送 target/restore 两个 `Rot`，就会把一次客户端 tick计成三次，并下游触发 Timer、Simulation、setback 与坠落。

完整 `PosRot` 并不意味着允许新 position sample。target 和 restore 都使用 frame 在 `Minecraft.tick()` HEAD 固定的同一坐标，使其与上一 vanilla position 对齐，并让 restore 成为 target 的 duplicate。

## Blink

`ScaffoldPacketBuffer` 只属于 Scaffold，不复用 `LagManager`。它按 FIFO 保存 packet 与原始 `PacketSendListener`，flush 通过 `PacketUtil.sendBuffered(...)` 重放，并绕过第二次业务事件但仍经过 final-write observer。

当 target `PosRot` 仍在 Blink 队列中时，`actualServerRotation` 仍可能等于玩家视角。pipeline 因此基于事务内 target commit 强制排入 player `PosRot`，不能仅依赖 final server state 去重：

```text
PosRot(framePos,target) [buffered]
  -> UseItemOn [buffered]
  -> PosRot(framePos,player) [forced, buffered]
```

## Trace

- `[ScaffoldDebug]` 记录 frame id、frame eye、planning eye、target、requested/active/server rotation 与终态。
- `[ScaffoldNetTrace]` 在 final write 记录 sequence、tick、frame id、owner、target 与 packet 内容。
- 成功后保留 `currentFrame` 到下一次 TickEvent，使后续 vanilla flying 仍能关联 placement frame；`consumedFrameId` 防止重复放置。
- packet context 使用 weak identity map，支持 Blink 跨线程和延迟 flush。

`runtime-traces/2026-07-11-172027-rot-only-fail.md` 与 `runtime-traces/2026-07-11-normal-aim-flight-fail.md` 保存真实失败证据。修复后的服务器/Grim trace 仍待用户复测；静态和单元测试不能替代该结论。
