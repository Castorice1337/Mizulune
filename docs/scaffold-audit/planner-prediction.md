# Planner 与 Prediction 审计

## Movement Planner

`ScaffoldMovementPlanner` 的输入是物理 `DirectionalInput`，核心步骤为：

1. 按玩家 camera yaw 与 W/A/S/D 计算移动 yaw。
2. 使用 ties-to-even 量化为八方向单位向量。
3. 在玩家脚下 `0.301 / 0 / -0.301` 的 3×3 采样中寻找真实碰撞支撑。
4. 优先延续最后 placed block 或上一次 support。
5. 最近四个 placed block 用于历史缓存，实际 movement line 取最后两个不同块拟合。
6. 历史线与当前输入方向点积小于 0.5 时放弃历史线。

`DirectionalInput.NONE` 不会让 planner 直接返回 null；它按玩家 camera yaw 生成同样的八向 movement line。这与 liquidSRC 一致，并避免松开按键的瞬间丢失 finder 排序基准。

这些公式来自本地 LB，自动化覆盖量化、idle input、历史 deque 与方向偏差。但游戏内连续搭路仍需 phase 0052 新运行重新验证。

## Movement Prediction

Prediction 只在存在 movement line 且玩家尚未接近边缘时工作：

- 沿 line 查找玩家碰撞箱将离开支撑的 fall-off point；
- 若已有成功放置历史，使用最近四次 line-local placement offset 的平均值；
- 否则使用 fall-off point；
- 成功 placement 后才记录新的 offset。

Prediction 是 target 的“预计放置时位置”，不是当前真实 eye。LB 允许用 predicted eye 生成 target rotation，再用当前玩家 crosshair 做最终 strict gate。

## 历史跨时刻问题

phase 0050/0051 的错误不在 prediction 数学本身，而在它产出的 target 被保留到 post-send 时刻：

- target/targetPoint 来自 Tick HEAD 的 predicted state；
- player 已在 tick body 中移动；
- rotation 仅在 12° 内允许 reproject；
- placement 再使用新 eye。

这使 prediction 从“同 tick 的提前规划”变成了“跨时刻 stale transaction”。

该路径已在 phase 0052 删除，只保留为失败根因记录。

## 当前原子帧

`ScaffoldTickFrame` 同时保存 movement line、prediction、target、eye、slot 和 stack；成功后 `movementPrediction.onPlace(...)` 使用 frame 自带 line，而不是读取可能已经更新的全局 line。

当前事务边界已经由源码和自动化确认：

- target rotation 仍可能基于 predicted eye，strict raycast 使用 frame 当前 eye；这是 LB 的预判语义，no-hit 应被视为正常等待，而不是放宽 strict gate。
- frame 只在相同 `player.tickCount` 的 `RotationResolvedEvent` 消费一次，不能跨 tick 重试。
- target block 与 frame player AABB 相交时在共同 pipeline preflight 拒绝。
- Delay、strict no-hit、AABB 拒绝和其他失败不会更新 placement offset 历史；只有 `completeSuccessfulPlacement(...)` 调用 `onPlace(...)`。

## 验收

1. 每条 placement log 同时输出 frame id、player eye、planning eye、predicted position 和 target。
2. 成功 placement 的 history update 必须引用同一 frame id。
3. no-hit 不得锁 target 到下一 tick；下一 tick完整重算。
4. 原地、直搭、45° 斜搭、转向、跳跃分别检查 movement line 与 predicted point。
5. collision/AABB 拒绝不能写入 placement offset 历史。
6. 实机验收仍保持 `WAITING_USER_PASS`，不能用算法测试代替连续搭路结果。
