# Normal、AimModulo360 与创造飞行 FAIL Trace

## 状态

`USER FAIL / FIXED IN CODE / WAITING_USER_PASS`

本记录保存 2026-07-11 后续实测中的三个残留问题。日志只证明旧快照失败，不代表当前代码已经通过实机验收。

## Normal RotationPlace 与坠落

- 检查窗口：`run/logs/latest.log` 约第 16420-22874 行。
- 统计：186 `place:success`、102 `place:no-hit`、53 `RotationPlace`、86 `Simulation`、8 `GroundSpoof`。
- frame 96 的 strict eye 为 `(-1254.302,71.121,-481.422)`；后续 vanilla flying 位置变为 `(-1254.294,71.000,-481.677)`，同一 rotation 从新眼位射出后 miss，触发 `RotationPlace post-flying`。
- frame 282 同样在 vanilla flying 前横移约 `0.219`，产生新的 post-flying seed。
- Grim 首次 post miss 会建立 `flagBuffer`；后续 pre miss 设置 `ignorePost`，服务端可在 cancel VL 后撤销客户端显示成功的方块，最终形成缺口和坠落。

根因不是 strict face 或 finder，而是 OpenZen 在 `Minecraft.tick()` HEAD 固定 frame eye，vanilla `sendPosition()` 却读取 `aiStep` 后的位置。旧 Normal 的 `useItemOn -> later vanilla flying` 没有保持 LB 实现所依赖的射线原点不变量。

当前修复：Normal 在所有 strict/gate 通过后使用同一 frame 坐标执行 `PosRot(target) -> UseItemOn -> PosRot(player)`，Normal provider 保持 ACTIVE，后续 vanilla flying 继续携带 target rotation。

## OnTick AimModulo360

- `latest.log:45078`：vanilla yaw `-1086.8812`。
- `latest.log:45084`：bounded target yaw `-53.24615`，raw delta `+1033.63505`。
- `latest.log:45090-45091`：restore `-7.031307` 后紧接 vanilla `-1087.0312`，raw delta 约 `-1080`。
- 指定窗口 21 个 `AimModulo360` 与 Grim 反编译谓词离线重放 `21/21` 对应。

根因是 `RotationHandler` 把 packet/server yaw wrap 到 `[-180,180]`，而 vanilla 玩家 yaw 保持多圈连续值。当前修复保留 packet raw yaw，并将 ephemeral target 投影到距离 logical server yaw 最近的等价角；OnTickSnap provider 接收 committed continuous rotation。

## 搭高时“短距离乱飞”

- `latest.log:18335` 与 `latest.log:22768` 各出现一次 `ServerboundPlayerAbilitiesPacket`。
- 开启后 Y 增量满足 `deltaY[n+1] = 0.6 * deltaY[n] + 0.15`，并收敛到 `0.375/tick`，这是 vanilla 创造飞行物理。
- 日志没有 Motion 整数 Y snap、Pulldown/Karhu `-1`、Vulcan `X/Z +0.1` 或 Hypixel 三 tick 速度指纹。
- 当前配置及同时间备份均为 `Tower=None`。

当前修复不会擅自关闭创造飞行；当 `abilities.flying=true` 时，Scaffold 不启动 Tower、不执行 Tower motion，也不把 stationary Jump 解释为脚下竖直搭高。

## 自动化覆盖

- Normal target/use/restore 同坐标与 onGround。
- strict/no-hit/gate 失败零 transaction 副作用。
- raw yaw `+-360/720/1080/1440` 最近等价分支。
- OnTickSnap provider 使用 committed continuous yaw。
- 创造飞行禁用 Tower 与 stationary-jump 脚下目标。

当前状态仍为 `WAITING_USER_PASS`；必须用修复后的新日志验证 Grim 零新增和服务端真实方块连续性。
