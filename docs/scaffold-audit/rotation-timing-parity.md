# Rotation Timing 对齐

## 当前实现

| 模式 | provider | strict 后 packet | 放置后 |
|---|---|---|---|
| Normal | 持续 | `PosRot(framePos,target) -> useItemOn -> PosRot(framePos,player)` | provider 保持，后续 vanilla flying 携带 target rotation |
| On Tick | 无 | `PosRot(framePos,target) -> useItemOn -> PosRot(framePos,player)` | 无 owner 残留 |
| On Tick Snap | target packet 后激活 | `PosRot(framePos,target) -> provider -> useItemOn` | 后续 rotation 由 provider 接管 |

三种模式都使用同一个 `ScaffoldTickFrame`，并在 player AABB、exact block/face/minY、MinDist 和 late slot gate 之后才允许 slot、packet 或 interaction 副作用。

## 与 liquidSRC 的本地适配

liquidSRC 的 Normal 不手动发送 rotation packet：先放置，再由本 tick vanilla flying 携 provider rotation。OpenZen 的 frame 在 `Minecraft.tick()` HEAD 固定，而 vanilla flying 到 `aiStep` 后才采样位置；实机日志已证明两次 eye 不一致会触发 `RotationPlace post-flying`，随后形成连续 pre-flying buffer。因此本地适配让 Normal 复用同坐标 transaction，同时保留持续 provider。

所有 transaction 都复用 `frame.playerPosition()`；target 与 restore 使用完全相同坐标和 onGround，不再次读取玩家位置。该重复坐标用于触发 1.17+ duplicate flying 语义，不是新的 position sample。

## 零副作用矩阵

| 场景 | Normal | On Tick | On Tick Snap |
|---|---|---|---|
| stale/invalid frame | 无事务 | 无事务 | 无事务 |
| player-target AABB 相交 | 无 raycast/slot/packet/use | 同左 | 同左 |
| wrong block/face/minY | 无 slot/transaction packet/use | 同左 | 同左 |
| MinDist fail | 无 slot/transaction packet/use | 同左 | 同左 |
| external owner | conflict | conflict | conflict |
| success | duplicate target/use/duplicate player + provider held | duplicate target/use/duplicate player | duplicate target/use + snap held |

“无事务”不等于禁止 vanilla 自身发送必要 position/status packet。验收关注 Normal/OnTick 的 `PosRot` 坐标必须与 frame 固定坐标一致，target/restore 不得各取一次新 position sample。

## 待实机

- Normal/OnTick/OnTickSnap 的同坐标 duplicate `PosRot` 是否消除 RotationPlace/Timer/Simulation。
- Blink 下 OnTick 强制 restore 的最终 FIFO 顺序。
- OnTickSnap 后续 provider rotation。
- 多圈转头后 Grim AimModulo360。
- Grim RotationPlace、PositionPlace、Timer、Simulation、InvalidOrder。

状态：`AUTOMATED_PASS / WAITING_USER_PASS`。
