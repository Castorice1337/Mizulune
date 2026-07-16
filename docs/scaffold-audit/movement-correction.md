# Movement Correction 审计

## 四态语义

| 模式 | 输入重映射 | 物理移动 yaw | jump yaw | 客户端视角 |
|---|---:|---:|---:|---:|
| `OFF` | 否 | player yaw | player yaw | 不改 |
| `STRICT` | 否 | server/synthetic yaw | server/synthetic yaw | 不改 |
| `SILENT` | 是 | server/synthetic yaw | server/synthetic yaw | 不改 |
| `CHANGE_LOOK` | 否 | 旋转后的视角 | 旋转后的视角 | 改 |

`RotationApplyMode` 与 `MovementCorrection` 是两个独立维度，不能重新压缩成 `fixMovement` boolean。

## 当前数据流

1. Scaffold 从物理 W/A/S/D 捕获 `rawInput`。
2. planner 使用 correction 前的物理方向生成 movement line。
3. `ScaffoldStabilizeMovement` 只补充未被玩家占用的输入轴。
4. RotationHandler 在后续 `StrafeEvent` 中对 SILENT 做离散输入旋转。
5. `RotationEvent` / jump hook 使用当前 owner 的 synthetic yaw 影响物理移动。

该顺序与 phase 0049 的边界一致：finder 不能读取 correction 后的方向，否则 rotation 会反向改变 planner，再由 planner 改 target，形成反馈环。

## 历史日志含义

最新日志中经常出现 raw forward 被映射为 corrected backward/side。它说明 committed rotation 与玩家 camera yaw 差异很大，但不能单独证明 correction 矩阵错误。

更直接的历史证据是：phase 0051 的 299 次 no-hit 中，246 次 planned/committed 最大轴差大于 12°。在这些 tick，SILENT correction 跟随的是未追上新 target 的 committed rotation；movement 手感异常是旧 rotation 时序错误的下游表现。该日志不代表 phase 0052 当前实现。

## 当前原子帧

frame 保存 correction 前的 `rawInput`、movement line 和 prediction。RotationHandler 在同一 TickEvent 后段 resolve owner/correction，再同步触发 placement，成功后使用 frame 自带 movement line 更新历史。

源码与自动化已确认：

- frame 成功后 `onPlace(...)` 使用 frame 自带 movement line；
- 外部 Clutch owner 抢占时 Scaffold correction 同 tick 停止；
- Telly null request 直接跳过 placement，不使用 reset/player rotation fallback 强放；
- On Tick 不创建持续 provider，OnTickSnap 仅在 strict target 成立后建立 snap provider。

仍需实机确认 correction 后的操作手感、转向和 Grim 行为；自动化通过不等于连续搭路 PASS。

## 验收用例

1. 0/45/90/135/180 度 yaw 差下，W/A/S/D 与斜向输入世界方向不变。
2. planner debug 的 `rawInput` 始终等于物理键，不等于 corrected input。
3. Stabilizer 不覆盖玩家已经按下的轴。
4. Clutch priority 60 抢占 Scaffold priority 50 后，correction 与 placement 同时切换 owner。
5. rotation 进入 RESET/IDLE 后，输入在同一 tick 内恢复，不保留 stale yaw。
