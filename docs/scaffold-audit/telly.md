# Telly 审计

## 边界

Telly 是 Normal Technique 的 jump/aim timing feature。它复用同一 planner、prediction、finder、geometry、frame 与 placement pipeline，不创建第二套 target 或 rescue。

## 状态

- grounded GameTick 递增 `ticksUntilJump`。
- `PlayerAfterJumpEvent` 清零计数并重采样 `Jump` range。
- `Straight` 控制起跳后的 do-not-aim tick 数。
- Reset 窗口返回 null request。
- Reverse 窗口返回 45 度量化 yaw 与至少 45 度 pitch。
- Tower 时可由 `AimOnTower` 禁止进入 do-not-aim。

## 本项目严格约束

phase 0052 明确采用：Telly 无瞄准窗口直接跳过 placement，不使用 previous/reset/player rotation fallback 强放。

这比 liquidSRC 的 `currentRotation ?: player.rotation` 更严格，是为满足当前项目的 exact block/face/minY 与 no-hit 零事务包约束。后续不得把 null request 重新解释为 fallback placement 权限。

## 跳跃放置时序

默认 `Straight=0` 时：

1. 落地 tick 可触发 jump，并进入 do-not-aim，当前 frame 不放置。
2. `PlayerAfterJumpEvent` 重置 cycle。
3. 下一 airborne frame 重新生成 target/rotation并执行 strict placement。
4. SafeWalk/Ledge 在 rotation 或 target 尚未准备好时负责临边保护。

## 验收

- Normal 与 Telly 使用同一 target finder。
- Straight/Jump 范围内计数每 tick 只更新一次。
- no-aim frame 为 `place:no-rotation-window` 且无 placement transaction packet。
- 第一个合法 airborne aim frame 能及时向桥面放置。
- Reset 与 Reverse 分别完成 25 格、WA/WD、跳跃和转头测试。
