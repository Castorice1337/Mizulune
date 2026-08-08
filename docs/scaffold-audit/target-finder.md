# Target Finder 审计

## 搜索入口

`Scaffold` 先由 `ScaffoldSameYPolicy` 解析本 tick 的 `targetedPosition`，再把当前 Technique 的 `targetOffsets(...)` 原样传入 `ScaffoldTargetFinder.find(...)`。Tower 规划与 Tower 收尾统一强制使用 Normal finder，避免 Tower 继承 Expand/GodBridge/Breezily 的搜索域。

Normal 默认 root 已是可站立 solid block 时，finder 返回 `base-solid`，避免在完整地面上扩展并铺地毯。Technique 自定义 root 则逐项检查，不能套用该 Normal 快速退出。

## 搜索域

| `SearchOffsets` | 搜索域 | 数量 | 使用场景 |
|---|---|---:|---|
| `NORMAL` | X/Z 为 `0,-1,1`，Y 为 `0,-1` | 18 | Normal、GodBridge、Breezily |
| `DOWN` | X/Z 为 `0,-1,1,-2,2`，Y 为 `0,-1` | 50 | Down 对 Technique 搜索域的覆盖 |
| `EXACT` | 仅 `BlockPos.ZERO` | 1 | Expand 的逐距离 root |

`Technique.TargetOffset` 同时携带 root offset、搜索域、排序方式、AimMode 和 facing-away 许可。当前已接通 Normal、Expand、GodBridge、Breezily，以及 `CENTER`、`RANDOM`、`STABILIZED`、`NEAREST_ROTATION`、`REVERSE_YAW`、`DIAGONAL_YAW`、`ANGLE_YAW`、`EDGE_POINT` 八种 AimMode。

排序规则为：

- `LINE_OR_POSITION` 且存在 movement line：按候选 outline box 到 line 的距离；
- `POSITION` 或没有 movement line：按候选 outline box 到 predicted position 的距离。

finder 每 tick 重算，不增加多 tick target latch、远端 shell、lane hard reject 或 constructed fallback。

## Target Plan

对每个候选 place position：

1. solid 则跳过；
2. air/fluid 使用邻块放置，其他可替换块使用 replace mode；
3. 枚举六个 direction；
4. 默认排除 support 可替换或 face 朝离玩家的计划；Down/Technique 显式许可时才接受 facing-away face；
5. 按 `RotationHandler.actualServerRotation` 到 face center rotation 的角差选择 plan；
6. 从 support 的真实 voxel shape AABB 提取对应 face；
7. side face 按 LB 规则保留 `y >= 0.6`，不再使用 phase 0051 的 0.85 上界；
8. 由所选 AimMode 生成 target point；
9. 生成 exact support block、face 与 minY target。

## Placement Preflight

玩家 AABB 检查已经落在 `ScaffoldPlacementPipeline` 的共同入口：它使用 frame 固定的 `playerPosition` 与 `pose` 构造玩家包围盒，并在 strict raycast、切槽、rotation packet 和 `useItemOn` 前拒绝与 `placedBlockPos` 相交的事务。

因此 phase 0051 的 45 次 self-intersection `useItemOn:FAIL` 是历史失败基线，不再是当前缺口。真实运行仍需确认是否存在其他服务端拒绝原因。

## 不应采用的修复

- 接受同 support 的错误 face；
- constructed `BlockHitResult`；
- no-hit 时调用 `useItemOn` 或发送 rotation packet；
- 用 target rotation 冒充 server committed rotation；
- 为 Telly 创建第二套 finder；
- 把 Clutch/rescue target 搜索放回 Scaffold。

## 验收

自动化已覆盖 18/50/1 搜索域、Technique priority、facing-away、八种 AimMode、真实 shape face 与 AABB preflight。实机 trace 继续记录 root、offset、support、face、target point、planning eye、frame id 和 preflight 结果。
