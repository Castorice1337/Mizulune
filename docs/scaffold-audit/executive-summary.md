# 执行摘要

## 当前结论

phase 0052 的 Rot-only 版本与后续 vanilla-only Normal 均已被用户实机判定 FAIL。当前工作树让 Normal/OnTick 统一使用 frame 同坐标 duplicate `PosRot`，并修复 raw yaw 分支与创造飞行边界；在用户重新走图前，功能状态必须保持 `WAITING_USER_PASS`。

最终自动化结果：229/229 tests，0 failures/errors/skipped；`compileJava`、包含 reobf/836 类混淆的完整 `build` 与 `git diff --check` 均通过。

phase 0050/0051 的 `194 success / 299 strict no-hit / 45 useItemOn:FAIL` 是更早的失败基线。phase 0052 Rot-only 会话证明额外 flying 会触发 Timer/Simulation；后续日志又确认 Normal frame eye 与 later flying eye 漂移会触发 RotationPlace，bounded/raw yaw 混用会触发 AimModulo360，所谓“搭高乱飞”则是创造飞行被开启。

## 已确认根因

1. target、eye、rotation 和 placement 分散在 Tick HEAD、motion 与 `sendPosition()` 后，形成跨时刻事务。
2. phase 0051 的 `12°` retarget gate 拒绝了多数必要修正。
3. post-send placement 时玩家已进入 intended placed block，导致 strict hit 后 `useItemOn:FAIL`。
4. phase 0052 错把 liquidSRC OnTick 的完整 `PosRot` 改成 Rot-only；每次成功额外发送 target/restore 两个 flying，Grim 将其计为额外客户端 tick。
5. OpenZen 在 `Minecraft.tick()` HEAD 固定 frame eye，但 vanilla flying 在 `aiStep` 后采样新位置；vanilla-only Normal 的 pre/post ray 原点不一致。
6. accepted/final server yaw 被 wrap 到 `[-180,180]`，与 vanilla 多圈 raw yaw 混发，形成约 1080 度跳变。
7. 创造飞行开启后，stationary Jump 仍被解释为脚下搭高；日志中的 `0.375/tick` 上升不是 Tower velocity。

前三条历史路径和当前 Rot-only transaction 都已删除，不能作为 fallback 恢复。

## 当前原子事务

```text
TickEvent HIGH
  -> 捕获 ScaffoldTickFrame
  -> finder 生成 target
RotationHandler LOWEST
  -> resolve owner / smoothing
  -> RotationResolvedEvent
Scaffold
  -> 单次消费 frame
  -> screen / PlayerPositionHold / Delay gate
  -> player AABB preflight
  -> frame eye strict block/face/minY/MinDist raycast
  -> late AutoBlock slot selection
  -> Normal: PosRot(framePos,target) -> useItemOn -> PosRot(framePos,player)
     provider 保持 ACTIVE，随后 vanilla flying(target)
     OnTick: 同坐标 target -> use -> player，结束后无 provider
  -> success-only history / Delay / Sprint / Eagle / Blink update
```

## 已接入范围

| 范围 | 当前实现 |
|---|---|
| Technique | Normal、Expand、GodBridge、Breezily 与八种 AimMode 已进入 finder/runtime |
| Tower | 六种模式已接入；当前 Tower 和 `wasTowering` 收尾强制 Normal finder |
| Placement | Delay、MinDist、AABB preflight、strict block/face/minY、三种 RotationTiming |
| AutoBlock | 默认过滤 TNT/Cobweb/NetherPortal，unfavorable 降权，late/always 切槽与恢复 |
| Normal features | SameY 四态、Down 50-offset 搜索、Eagle predicted pose、Ceiling、HeadHitter、Telly |
| Safety | SafeWalk、OnEdge、Ledge 与 LB `forceSneak` 倒计时 |
| Motion | Acceleration、Strafe、StrafeOnJump、SpeedLimiter、AutoSpeed 适配 |
| Network | Scaffold 私有 Blink buffer、listener 保留、SimulatePlacementAttempts、final-write trace |
| Config | 普通 `mode` Value 可读取，旧 `Mode=Telly` 迁移为 Normal + `Telly=true` |

## 本轮终审补充修复

- Normal/OnTick 使用 frame 同坐标完整 `PosRot(target) -> use -> PosRot(player)`；Normal provider 保持 ACTIVE，Blink 下强制保留同坐标回正包。
- actual/theoretical server rotation 保留 raw continuous yaw；ephemeral target 选择距离 logical yaw 最近的等价分支，OnTickSnap provider 接收 committed yaw。
- 创造飞行时禁用 Tower motion、towering/wasTowering 与 stationary-jump 脚下目标，不擅自关闭用户飞行状态。
- debug trace 增加 Tower、flying、position、velocity、onGround 与 packet offset。
- `KeyboardInputPatch` 以 vanilla `input.shiftKeyDown` 初始化 sneak，`slowDown` 只控制移动倍率，修复进服后 Shift 失效或潜行自锁。
- 成功后保留 frame 到下一 TickEvent，使 Normal 后续 vanilla flying 继续关联原 placement trace。
- Ledge/GodBridge `sneakTime` 改为 LB 式跨 tick `forceSneak` 倒计时。
- Down 从 Normal 的 18 offsets 补齐为 LB 的 50 个 `DOWN` offsets。
- idle input 仍生成 LB 八向 movement line。
- Eagle 在 finder 前计算 predicted crouching pose，同时保持 Down 最终反对权。
- Ceiling/HeadHitter 只在 Normal Technique 生效。
- Tower Ledge 在当前 Tower 时强制 Normal，不泄漏 GodBridge extension。
- SimulatePlacementAttempts 不再绕过 GUI、`PlayerPositionHold` 和 Delay gate。
- 配置热加载与跨世界会清理旧 frame、slot、Delay、Ledge、Telly、Eagle、strafe 和 sprint 状态。
- 删除 phase 0047 未注册的旧 planner/prediction/finder/coordinator，并移除 RESET/PLAYER placement fallback 死 API；`scaffold/v2` 是唯一 Scaffold 内核。

## 验证边界

自动化可以证明策略、配置迁移、ASM、packet 顺序和零副作用 gate；它不能证明真实移动手感、25 格连续搭路或 GrimAC 结果。

当前已有两份 FAIL trace，但缺少最新修复后的 `[ScaffoldDebug]` / `[ScaffoldNetTrace]`。用户复测后再写入新的 runtime trace，届时才能从 `WAITING_USER_PASS` 升级。
