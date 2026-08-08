# 证据登记

## 审计边界

本审计只覆盖 `Scaffold` 普通搭路核心及其直接依赖：rotation、movement correction、sprint、planner/prediction、target finder、geometry、placement transaction 和 Telly timing。Clutch、BlockIn、GodBridgeAssist 只记录仲裁边界，不审计其内部功能。

本目录描述 2026-07-11 的 phase 0052 收尾快照。Rot-only 与 vanilla-only Normal 均已有真实用户 FAIL；当前同坐标 `PosRot`、continuous yaw 与 flying 边界修复完成后仍需复测，因此状态是 `AUTOMATED_PASS / WAITING_USER_PASS`，不是功能 PASS。

## 一手本地源码

| 证据 | 用途 | 状态 |
|---|---|---|
| `liquidSRC/src/main/kotlin/.../ModuleScaffold.kt` | LB GameTick 放置事务、Normal / OnTick 时序 | `CONFIRMED_SOURCE` |
| `liquidSRC/src/main/kotlin/.../RotationManager.kt` | GameTick FIRST_PRIORITY 中触发 `RotationUpdateEvent` 并 resolve rotation | `CONFIRMED_SOURCE` |
| `liquidSRC/src/main/kotlin/.../ScaffoldNormalTechnique.kt` | target、Telly null rotation、crosshair gate | `CONFIRMED_SOURCE` |
| `liquidSRC/src/main/kotlin/.../ScaffoldTellyFeature.kt` | Straight / Jump / Reset / Reverse | `CONFIRMED_SOURCE` |
| `liquidSRC/src/main/kotlin/.../TargetFinding.kt` | offset、support face、minY、strict match | `CONFIRMED_SOURCE` |
| `src/main/java/shit/zen/modules/impl/movement/Scaffold.java` | Mizulune orchestration、frame flow 与 feature 接线 | `CONFIRMED_SOURCE` |
| `src/main/java/shit/zen/modules/impl/movement/scaffold/v2/` | planner、prediction、finder、geometry、placement | `CONFIRMED_SOURCE` |
| `src/main/java/shit/zen/modules/impl/movement/scaffold/v2/{feature,motion,normal,technique,tower}/` | LB feature 与策略层 | `CONFIRMED_SOURCE` |
| `src/main/java/shit/zen/utils/rotation/RotationHandler.java` | owner、lifecycle、packet commit、resolved event | `CONFIRMED_SOURCE` |
| `src/main/java/shit/zen/patch/MinecraftPatch.java` | `TickEvent` 位于 `Minecraft.tick()` HEAD | `CONFIRMED_SOURCE` |
| `src/main/java/shit/zen/patch/LocalPlayerPatch.java` | `sendPosition()` 与 sprint hook | `CONFIRMED_SOURCE` |

## 历史记录

| Phase | 审计用途 | 关键状态 |
|---|---|---|
| 0047 | 失败实验、职责膨胀与最终边界收敛 | 多轮 FAIL，后续由 v2 重写替代 |
| 0048 | LB Normal v2 planner/finder/geometry 基线 | 自动化通过，游戏内待确认 |
| 0049 | movement correction、Telly、stabilizer、sprint | 自动化通过，游戏内待确认 |
| 0050 | rotation lifecycle、packet 重复、post-send placement | crash 修复通过；Normal post-send 未完成实机闭环 |
| 0051 | motion-time reproject 与 12° retarget | 旧日志 FAIL；修复只有静态/单测结果 |
| 0052 | 原子 frame、完整 LB feature/Technique/时序对齐 | 两轮实机 FAIL 已归因；最新 transaction/yaw/flying 修复待用户复测 |

历史中的 `BUILD PASS`、`JUNIT PASS`、`PATCH 31/31` 只证明对应检查通过，不证明 Scaffold 连续搭路功能通过。

## 历史失败日志统计口径

以下数据是 phase 0051 调试时从当时的 `run/logs/latest.log` 提取的历史基线；当前 `latest.log` 已被 phase 0052 会话覆盖。

为避免每条 debug 同时被 logger 与聊天镜像记录两次，统计只保留包含 `[Mizulune/]` 和 `[ScaffoldDebug]` 的 logger 行，排除 `ChatComponent` 镜像。

| 结果 | 数量 | 占 538 次 placement 结果 |
|---|---:|---:|
| `place:no-hit` | 299 | 55.6% |
| `place:success` | 194 | 36.1% |
| `useItemOn:FAIL` | 45 | 8.4% |

补充复算：

- 299 次 no-hit 中，246 次 planned/committed rotation 的最大轴角差大于 `12°`，占 82.3%。yaw 使用最短环绕角差，pitch 使用绝对差，再取两轴最大值。
- 45 次 `useItemOn:FAIL` 均可由同 tick `packet-summary` 的玩家位置复算出目标放置块与玩家 AABB 相交，即 `45/45`。
- no-hit 细分为：260 次 `actualHit=null`、21 次 wrong block、18 次 same support wrong face；没有样本只因 minY 失败。
- 该历史日志的 no-hit rotation source 为：250 `ACTIVE_OWNER`、43 `PLAYER_FALLBACK`、6 `RESET_OWNER`；该会话没有 rotation conflict、waiting vanilla 或 external hold 记录。

## Phase 0052 Rot-only 失败日志

当前 `run/logs/latest.log` 在 2026-07-11 17:20:27 开始记录失败配置 `TellyV2 + On Tick + SameY On + SILENT`。完整摘要见 `runtime-traces/2026-07-11-172027-rot-only-fail.md`。

| 结果/packet | 数量 |
|---|---:|
| placement success / no-hit | 16 / 0 |
| transaction `Rot` | 32 |
| Grim Timer / Simulation | 252 / 213 |
| Grim PositionPlace / RotationPlace | 25 / 4 |

每次成功恰好对应 target/restore 两个 Rot-only flying。Grim `Timer` 与 duplicate 判定字节码、liquidSRC 的完整 `PosRot` 实现共同确认：Rot-only 是该会话大量 VL 和 setback 的直接根因。

## Phase 0052 后续残留失败日志

完整摘要见 `runtime-traces/2026-07-11-normal-aim-flight-fail.md`。

| 问题 | 运行证据 | 已确认根因 |
|---|---|---|
| Normal 漏搭/VL | 186 success、102 no-hit、53 RotationPlace、86 Simulation、8 GroundSpoof | frame eye 与 later vanilla flying eye 漂移 |
| OnTick 转向 VL | 指定窗口 21 个 AimModulo360，谓词重放 21/21 | bounded target/restore 与 multi-turn vanilla raw yaw 混发 |
| 搭高短距乱飞 | 两次 Abilities packet 后 Y 速度收敛到 `0.375/tick` | 创造飞行开启，非 Tower motion |

## 结论可信度

| 结论 | 可信度 | 理由 |
|---|---|---|
| phase 0050/0051 存在跨时刻 target/eye/rotation 混用 | 高 | phase 记录、事件位置与日志共同支持 |
| `12°` retarget 限制拒绝了多数必要修正 | 高 | 246/299 可复算 |
| 45 次 `useItemOn:FAIL` 由玩家占用目标块触发 | 高 | 45/45 AABB 相交，且 strict raycast 已通过 |
| geometry strict face 是当前主要失败源 | 低/已否定 | wrong-face 仅 18/299，且多数 no-hit 角差已超过门限 |
| phase 0052 原子帧、AABB、strict/no-hit packet 约束已落地 | 高（静态/自动化） | 当前源码与集成测试共同支持 |
| phase 0052 Rot-only 时序兼容 Grim | 已否定 | 16 success / 32 Rot 与 Timer/Simulation 实机证据 |
| vanilla-only Normal 与 OpenZen 事件时序兼容 | 已否定 | 两个独立 post-flying seed 与连续 pre-flying buffer |
| AimModulo360 来自配置 | 已否定 | 21/21 raw delta 谓词对应，检查不受 experimental 配置控制 |
| 搭高乱飞来自 Tower velocity | 已否定 | Abilities packet 与 vanilla flying 速度指纹，Tower=None |
| 最新 Normal transaction/continuous yaw/flying guard 已解决实机问题 | 未知 | 自动化通过，但尚无新运行日志或用户 PASS |

## 当前运行证据缺口

- 已将两轮 FAIL 会话作为否定证据写入 `runtime-traces/`，不得覆盖或标成 PASS。
- 用户复测后，从同一次新会话提取 `[ScaffoldDebug]` 与 `[ScaffoldNetTrace]` 到新的 trace 文件。
- 真实 trace 必须能关联 frame id、frame eye、planning eye、strict outcome、最终 packet 顺序和 Grim 结果。
