# Sprint Control 审计

## 功能边界

Sprint Control 默认关闭，只控制 Scaffold 开启时的 client/server sprint 决策，不负责 target、rotation 或 placement。

| 模式 | 语义 |
|---|---|
| `DO_NOT_CHANGE` | 保留当前结果 |
| `FORCE_SPRINT` | 有移动输入时强制 sprint |
| `FORCE_NO_SPRINT` | 强制停止 sprint |
| `NO_SPRINT_ON_PLACE` | 本 tick 成功放置后停止 sprint |
| `NO_SPRINT_ON_GROUND` | 落地时不 sprint |

## hook 点

- `KeyboardInputPatch`：INPUT source，可同时应用 client/server policy。
- `LocalPlayerPatch` movement tick：修改 vanilla 启停 sprint 的真实判断点。
- `LocalPlayerPatch.sendIsSprintingIfNeeded()`：NETWORK source，修改 vanilla 读取 `isSprinting()` 的结果。

该设计不取消已经构造的 START/STOP packet，因此不会让 vanilla `wasSprinting` 与实际发送状态分叉。

## placement 关联

`ScaffoldSprintControl.wasPlaced` 在 GameTick 开头清零，只有 `completeSuccessfulPlacement(...)` 才置为 true。`RotationResolvedEvent` 在同一 TickEvent 链内完成 placement，早于后续 vanilla tick body 的 movement/network hook。

- phase 0050/0051 的历史 post-send placement 较晚，可能错过当时 tick 的部分 sprint 判断。
- phase 0052 当前事件顺序允许后续 input/player/network hook 看到 `wasPlaced=true`。

该顺序已由源码和单元测试覆盖，但 sprint 手感与实际网络行为仍未实机确认，不能标记功能 PASS。

## 风险与边界

1. placement 失败不能置 `wasPlaced`。
2. strict no-hit、AABB 拒绝和 owner conflict 都不能触发 `NO_SPRINT_ON_PLACE`。
3. 一个 frame 即使收到重复 resolved event，也只能更新一次 `wasPlaced`。
4. Scaffold disable 必须恢复物理 sprint key 状态。
5. Sprint Control 不得改变 planner 使用的物理 raw input。

## 验收

- 对五种 client/server 组合分别记录 INPUT、MOVEMENT_TICK、NETWORK 三个 source。
- 确认成功 placement 后同 tick network decision 符合配置。
- 确认失败 placement 不抑制 sprint。
- 确认关闭模块后不遗留 key state 或 network sprint suppression。
