# 修复计划与完成状态

## 已完成

- [x] 用 `ScaffoldTickFrame` 冻结同 GameTick target/eye/input/slot/stack。
- [x] 由 `RotationResolvedEvent` 同步消费 frame，删除 motion-time retarget 与 post-send placement。
- [x] strict block/face/minY/MinDist 在 slot、rotation packet 与 `useItemOn` 前执行。
- [x] 加入 player AABB 与 intended block AABB preflight。
- [x] Normal/OnTick 使用 frame 同坐标 duplicate `PosRot(target) -> use -> PosRot(player)`；OnTickSnap 使用 target `PosRot`，删除 Rot-only transaction。
- [x] 保留 actual/theoretical raw continuous yaw，ephemeral target 使用最近等价分支，OnTickSnap provider 使用 committed yaw。
- [x] 创造飞行时禁用 Tower motion、towering 状态与 stationary-jump 脚下目标。
- [x] 修复 `KeyboardInputPatch` 将 `slowDown` 误当 sneak 导致的 Shift 失效/潜行自锁。
- [x] Normal/OnTick/OnTickSnap 的 no-hit pipeline 零事务副作用。
- [x] 接入 Technique offsets、八种 AimMode 与 Tower Normal finder。
- [x] 接入 AutoBlock、方块筛选、slot reset、ConsiderInventory。
- [x] 接入 SameY、Down、Eagle、Ceiling、HeadHitter、统一 Telly。
- [x] 接入 SafeWalk、Ledge 与跨 tick `forceSneak`。
- [x] 接入 Acceleration、Strafe、StrafeOnJump、SpeedLimiter、AutoSpeed、Blink、SimulatePlacementAttempts。
- [x] 修复 `ModeValue` 配置读取并迁移旧 `Mode=Telly`。
- [x] final-write 更新 `actualServerRotation`，Blink flush 保留 listener 与 frame trace。
- [x] 覆盖配置、finder、SameY、AutoBlock、Delay/MinDist、SafeWalk、Tower、RotationTiming、ASM 与 packet 顺序测试。

## 自动化收尾

- [x] `gradlew test`
- [x] 最终 `gradlew compileJava`
- [x] 最终 `gradlew build`
- [x] 最终 `git diff --check`

最终结果：61 个 suite、229 tests、0 failures、0 errors、0 skipped；完整 `build` 包含 `reobfJar` 与 `obfuscateClasses`，混淆 836 classes；`git diff --check` 仅输出工作区 CRLF 转换提示。

## 用户实机验收

- [ ] Normal 连续 25 格直搭。
- [ ] Normal WA/WD 与跳跃搭路。
- [ ] Telly 连续 25 格，Reset/Reverse 与 Straight/Jump 范围。
- [ ] 90 度和 180 度转头后继续搭路。
- [ ] 临边、SafeWalk、Ledge、Eagle。
- [ ] 当前主手、AutoBlock 切槽、仅副手方块、无方块。
- [ ] 三种 RotationTiming packet 顺序。
- [ ] 多圈连续转头后 Grim 无 AimModulo360。
- [ ] Survival 下 Tower；创造 flying 时不触发 Tower/脚下竖直目标。
- [ ] Grim 无 RotationPlace、PositionPlace、Simulation、InvalidOrder。

## 合格标准

- 零缺口、零坠落。
- 默认关闭 SimulatePlacementAttempts 时零 `useItemOn:FAIL`。
- no-hit 零 Scaffold transaction packet。
- Normal/OnTick target/restore 的 position 与 onGround 完全一致，且不重新采样玩家坐标。
- Normal provider 在 transaction 后保持 ACTIVE；不得使用 Rot-only。
- packet raw yaw 始终位于最近连续分支，不出现约 360/720/1080 度跳变。
- placement frame 只在创建 tick 消费一次；延迟 final-write 只保留 trace context，不重复 placement。
- 用户明确确认 PASS。

当前状态：`AUTOMATED_PASS / WAITING_USER_PASS`。在真实 trace 和用户确认前不得改为功能 PASS。
