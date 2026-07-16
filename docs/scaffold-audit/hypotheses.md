# 假设登记

## 历史已确认

以下结论只描述 phase 0050/0051 的失败实现，不代表 phase 0052 当前工作树。

### H1：不同时间切片被混用

状态：`HISTORICAL / CONFIRMED_LOG + CONFIRMED_SOURCE`。

target 在 `Minecraft.tick()` HEAD 选择，placement 在 `sendPosition()` 后执行；玩家位置和 eye 已变化，motion-time 只重算 rotation、不重跑 target transaction。

### H2：12° retarget gate 拒绝了多数必要修正

状态：`HISTORICAL / CONFIRMED_LOG`。

246/299 no-hit 的 planned/committed 最大轴差大于 12°，占 82.3%。

### H3：post-send self-intersection 导致 `useItemOn:FAIL`

状态：`HISTORICAL / CONFIRMED_LOG`。

45/45 FAIL 的 placed block 与同 tick player AABB 相交；strict hit 已通过，因此 rotation/facing 不是这 45 次的直接原因。

### H4：PatchAgent 30/31 曾导致 rotation 不提交

状态：`HISTORICAL / CONFIRMED_LOG`。

phase 0050 已修复并确认 31/31；phase 0051 的后续失败不是 patch 缺失场景。

### H5：strict gate 正在阻止错误 placement

状态：`HISTORICAL / CONFIRMED_LOG`。

299 次 no-hit 没有进入 `useItemOn`。放宽 wrong face 或 constructed fallback 只会把时序错误转化为错误放置和反作弊风险。

## Phase 0052 已确认

### C1：原子 frame 和单一消费点已落地

状态：`CONFIRMED_SOURCE + AUTOMATED_TEST`。

`ScaffoldTickFrame` 固定 frame id、player tick、position、eye、pose、input、movement line、prediction、target、rotation、hand/slot/stack；`RotationResolvedEvent` 同步且每 frame 最多消费一次。motion-time retarget 与 post-send placement 已删除。

### C2：no-hit 在事务前终止

状态：`CONFIRMED_SOURCE + AUTOMATED_TEST`。

exact block/face/minY、MinDist、AABB 和 late slot gate 都位于 rotation packet 与 `useItemOn` 前。三种 RotationTiming 的 no-hit 测试均确认没有 transaction packet 或 interaction。

### C3：Telly null request 的语义已固定

状态：`CONFIRMED_SOURCE + PRODUCT_CONSTRAINT`。

Telly 无瞄准窗口时直接记录 `place:no-rotation-window` 并跳过放置。不得恢复 ACTIVE/RESET/PLAYER fallback 强放；这项约束有意比 liquidSRC 的 current/player fallback 更严格。

### C4：AABB preflight 已实现

状态：`CONFIRMED_SOURCE + AUTOMATED_TEST`。

共同 pipeline 入口使用 frame position/pose 检查玩家与 intended placed block，相交时在 raycast、切槽、packet 和 interaction 前返回。

### C5：Rot-only transaction 会被 Grim 计为额外客户端 tick

状态：`CONFIRMED_RUNTIME_LOG + CONFIRMED_GRIM_BYTECODE + CONFIRMED_LIQUIDSRC`。

2026-07-11 17:20:27 的 phase 0052 会话有 16 次成功放置和恰好 32 个 transaction `Rot`，即每次 target/restore 各一个；同会话触发 Timer 252、Simulation 213。Grim `Timer` 对非 1.17+ duplicate flying 增加 50 ms，Rot-only 因无 position 不能成为 duplicate。liquidSRC OnTick 使用同坐标完整 `PosRot`，Normal 则不手发 rotation packet。

### C6：OpenZen 的 vanilla-only Normal 不满足固定 eye 不变量

状态：`CONFIRMED_RUNTIME_LOG + CONFIRMED_EVENT_ORDER`。

frame 在 `Minecraft.tick()` HEAD 固定，但 vanilla flying 在 `aiStep` 后读取新 position。frame 96 与 282 均证明 strict 成功后，later flying eye 的横向/纵向位移足以让同一 rotation miss support，触发新的 `RotationPlace post-flying`，随后 pre-flying buffer 连锁增长。

### C7：AimModulo360 来自 raw/bounded yaw 分支混发

状态：`CONFIRMED_RUNTIME_LOG + CONFIRMED_GRIM_BYTECODE`。

旧 ephemeral packet 把 target/restore wrap 到 `[-180,180]`，vanilla yaw 却已累计到约 `-1087`；指定窗口 21 个 flag 与 Grim 谓词离线重放 21/21 对应。logical/actual rotation 必须保留 raw yaw，等价性比较才使用 modulo。

### C8：本轮搭高乱飞是创造飞行，不是 Tower velocity

状态：`CONFIRMED_RUNTIME_LOG + CONFIRMED_VANILLA_PHYSICS`。

两次 `ServerboundPlayerAbilitiesPacket` 后 Y 增量按 `deltaY[n+1] = 0.6 * deltaY[n] + 0.15` 收敛到 `0.375/tick`；日志没有任何 Tower mode 指纹，配置为 `Tower=None`。Scaffold 只需在 flying 时停止 Tower/竖直目标，不应擅自关闭创造飞行。

## 运行时开放项

### O1：同坐标 Normal transaction 能否消除 RotationPlace 链

状态：`WAITING_RUNTIME_TRACE`。

vanilla-only Normal 已由运行日志否定。当前实现改为 frame 同坐标 `PosRot(target) -> use -> PosRot(player)` 并保留 provider；需确认每个 success 的服务端方块真实保留，且 RotationPlace pre/post、Simulation 与 GroundSpoof 零新增。

### O2：修正后的三种时序与服务器/Grim 是否兼容

状态：`WAITING_RUNTIME_TRACE`。

旧 Rot-only 与 vanilla-only Normal 均已由真实日志否定。需复测 Normal/OnTick 同坐标 target/use/player、OnTickSnap，并确认 Timer、Simulation、PositionPlace、RotationPlace、AimModulo360、InvalidOrder 归零。

### O3：AABB preflight 是否消除旧 45 类 FAIL

状态：`WAITING_RUNTIME_TRACE`。

历史 45/45 支持该修复，但未来仍可能有 stack、world state 或服务端规则造成的其他 `useItemOn:FAIL`，不能把所有失败都归因于碰撞。

### O4：prediction eye 与 frame eye 的正常差异

状态：`WAITING_RUNTIME_TRACE`。

LB 允许 predicted eye 生成 target rotation，再由 frame eye 做 strict gate。修复目标不是让每个规划 tick 都命中，而是确保 no-hit 无事务副作用且下一 tick 完整重算。
