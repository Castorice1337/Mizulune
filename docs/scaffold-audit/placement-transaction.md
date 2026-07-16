# Placement Transaction 审计

## Frame 不变量

一次 placement 只允许消费一个 `ScaffoldTickFrame`，其中固定：

- player tick 与 frame id；
- player position、eye position 与 predicted pose；
- physical input、movement line 与 prediction；
- target/support/face/point/minY；
- requested/resolved rotation；
- hand、hotbar slot、ItemStack 与 placementY。

`consumedFrameId` 保证每个 frame 最多进入一次终态。

## 统一前置顺序

```text
current frame
  -> screen closed
  -> PlayerPositionHold inactive
  -> Delay elapsed
  -> target / rotation / live stack valid
  -> placed-block AABB does not intersect frame player AABB
  -> strict raycast from frame eye
  -> exact support block + face + minY
  -> MinDist
  -> late slot selection when AutoBlock Always=false
```

任一 gate 失败均不调用 `useItemOn`，不更新 placement history，也不推进 Delay。

## RotationTiming

| 模式 | 成功事务 |
|---|---|
| Normal | `PosRot(framePos,target) -> useItemOn -> PosRot(framePos,player)`，Scaffold provider 保持 |
| On Tick | `PosRot(framePos,target) -> useItemOn -> PosRot(framePos,player)`，不创建持续 provider |
| On Tick Snap | `PosRot(framePos,target) -> 激活 snap provider -> useItemOn` |

三种模式都在 strict gate 后才允许 slot/rotation/interaction 副作用。Normal/OnTick 的 target/restore 只能使用 frame 固定坐标与同一 onGround，不得在两次 commit 时重新采样玩家位置。

## AutoBlock

- `Always=true` 可在 finder 前持有最佳热键栏方块。
- `Always=false` 只在 strict target 与 MinDist 通过后切槽。
- offhand 有合法方块时优先直接使用 offhand，不强制切主手。
- SlotResetDelay 到期后仅在玩家仍停留于 Scaffold 选择槽时恢复，避免覆盖用户手动切槽。
- `DoNotUseBelowCount` 保留 LB 语义：优先选择高于阈值的堆叠，无可用候选时回退到合法堆叠。

## 成功与失败

| 结果 | rotation transaction | `useItemOn` | history / Delay |
|---|---:|---:|---:|
| invalid/stale/hold/screen/delay | 否 | 否 | 否 |
| AABB intersection | 否 | 否 | 否 |
| strict mismatch / MinDist | 否 | 否 | 否 |
| owner/commit conflict | 否；snap provider 先完成仲裁 | 否 | 否 |
| `useItemOn:FAIL` | 已提交 | 一次 | 否 |
| success | 按 RotationTiming | 一次 | 一次 |

SimulatePlacementAttempts 仍保留 LB 的失败点击语义，但现在与真实 placement 共用 screen、external hold 和 Delay 入口；模拟成功同样只更新一次 history 与 Delay。

## 禁止项

- 不接受 wrong face。
- 不使用 constructed/planned fallback。
- 不在 motion 或 post-send 阶段 retarget/place。
- 不调用 `PlayerPositionHold.hold(...)`。
- 不把 Clutch 或坠落救援放入 Scaffold。
