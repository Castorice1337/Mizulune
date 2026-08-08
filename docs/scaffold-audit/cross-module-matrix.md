# 跨模块矩阵

## 所有权与边界

| 组件 | 拥有的职责 | 不应拥有的职责 | Scaffold 交互 |
|---|---|---|---|
| `Scaffold` | 普通/Telly timing、frame、target、placement 编排 | Clutch rescue、position hold 创建、第二套 finder | 主体 |
| `RotationHandler` | provider 仲裁、smoothing、ACTIVE/RESET/IDLE、duplicate `PosRot` commit 与最终 rotation facts | Scaffold target 搜索 | Scaffold priority 50；OnTick 不持续占 owner |
| `Clutch` | 坠落救援 target/rotation/place | 普通搭路 | priority 60，可抢占 Scaffold |
| `PlayerPositionHold` | 外部救援位置冻结状态 | Scaffold target/rotation | active 时 Scaffold skip place |
| `GodBridgeAssist` | 独立 Ghost bridge assist | Scaffold v2 内核 | Scaffold enable 时关闭，priority 40 |
| `BlockIn` | 自困 placement | Scaffold 普通搭路 | Scaffold enable 时关闭 |
| `KeyboardInputPatch` | input event 与 sprint INPUT hook | target finder | Scaffold stabilizer 后接 SILENT correction |
| `LocalPlayerPatch` | movement/network sprint 与 vanilla sendPosition hook | target lifecycle、Scaffold placement | phase 0050 曾因 patch 失败影响 rotation；当前 placement 不在 sendPosition hook |
| `ScaffoldSprintControl` | client/server sprint policy | movement line | success placement 后更新 `wasPlaced` |
| Inventory/slot | 当前可放置 stack | target geometry | frame 绑定 slot/stack，place 前复核 live stack |
| `DynamicIsland`/HUD | block counter 展示 | placement 决策 | 只读 block count/item |

## owner 优先级

| Provider | Priority | 预期 |
|---|---:|---|
| Clutch | 60 | 生命安全救援优先 |
| Scaffold | 50 | 普通搭路 |
| GodBridgeAssist | 40 | Ghost 辅助 |

Scaffold 与 Clutch 同开时，RotationHandler 必须选择 Clutch；Scaffold 同 tick 返回 conflict且不发 packet/place。Scaffold 不创建或接管 `PlayerPositionHold`。

## 输入顺序

```text
物理按键
  -> Scaffold 记录 rawInput / planner line
  -> ScaffoldStabilizeMovement 补充空闲轴
  -> RotationHandler SILENT correction
  -> vanilla movement
```

planner 不得消费 correction 后 input。

## placement 仲裁

```text
frame target exists
  -> requested rotation window exists
  -> GUI closed / PlayerPositionHold inactive / Delay ready
  -> no external rotation owner
  -> frame player AABB does not intersect intended block
  -> exact block/face/minY + MinDist
  -> live hand/slot/stack valid
  -> Normal: no transaction flying / OnTick: duplicate PosRot
  -> useItemOn
```

Normal 还要求 Scaffold active provider，并执行同坐标 target/use/player transaction 后保持 provider；OnTick/OnTickSnap 只要求无外部 owner，并分别执行同坐标临时回正或 snap provider。Telly null rotation window 在仲裁前直接跳过，不使用 reset/player fallback。任何前置条件失败都不能调用 `useItemOn` 或发送 transaction rotation packet。

## 并行修复注意

Scaffold 审计文档不应驱动对 Clutch、BlockIn、GodBridgeAssist 或 patch loader 的回退。若这些文件同时变化，应通过 owner contract 与日志验证兼容，而不是恢复旧实现。
