# 事件时间线

## 当前同 GameTick 链

| 顺序 | 事件 | Scaffold 行为 |
|---:|---|---|
| 1 | `Minecraft.tick()` HEAD | 派发 `TickEvent` |
| 2 | `Scaffold.onTick(HIGH)` | motion feature、physical input、planner、prediction、finder、frame |
| 3 | `RotationHandler.onTickHigh(LOWEST)` | provider 仲裁、smoothing、ACTIVE/RESET |
| 4 | 同步 `RotationResolvedEvent` | Scaffold 单次消费本 frame；Normal/OnTick 执行同坐标 `PosRot` transaction，OnTickSnap 执行 target transaction |
| 5 | vanilla tick body | input、movement、`sendPosition()` 等继续；Normal/OnTickSnap provider rotation 在后续 vanilla flying 中保持 |
| 6 | tick 尾 `PostMotionEvent` | ground/air counter 与 packet summary |

`EventBus` 按 HIGHEST 到 LOWEST 同步执行，所以 frame 在 rotation resolve 前已经完整生成。

## Placement 时间点

placement 只发生在 `RotationResolvedEvent`。以下历史路径已删除：

- PreMotion 手动 `PosRot`；
- `sendPosition()` start 的 eye reproject；
- `12°` motion-time retarget；
- `sendPosition()` 后的 strict place；
- no-hit 后 fallback interaction。

## Input 优先级适配

当前 `StrafeEvent` 内顺序对齐 LB priority：

```text
Telly/default technique input
  -> StabilizeMovement (MODEL_STATE)
  -> Ledge + forceSneak / SpeedLimiter (SAFETY_FEATURE)
  -> SafeWalk OnEdge
  -> Down final override (OBJECTION_AGAINST_EVERYTHING)
```

Eagle decision 在 finder 前计算 predicted pose，但实际 sneak 在 Down 判定完成后应用，避免 Eagle synthetic shift 误触 Down。

## Tower

Tower motion 可修改玩家 velocity、position snap、timer 或本次 vanilla move packet 的位置字段。transaction 仍只允许 frame 固定坐标的 duplicate `PosRot`，不得为 Tower 另取 position sample。当前 Tower 与 `wasTowering` finder 收尾使用 Normal offsets；只有当前正在 Tower 时 Ledge extension 强制 Normal。`abilities.flying=true` 时 Tower motion、towering 状态和 stationary-jump 脚下目标全部禁用。

## 运行验证

`[ScaffoldDebug]` 的 frame id/eye、Tower/flying/position/velocity 与 `[ScaffoldNetTrace]` final packet sequence 用于证明：placement frame 只在创建 tick 消费一次、no-hit 无 Scaffold transaction、成功 packet 顺序正确。为关联后续 vanilla flying 或 Blink 延迟 flush，packet context 可以在后续 tick final-write，这不代表 placement 被跨 tick 重复执行。
