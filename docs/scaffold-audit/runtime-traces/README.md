# Runtime Traces

## 状态

`WAITING_USER_PASS`

本目录保存 2026-07-11 phase 0052 的真实 FAIL trace。它们证明旧 packet 时序和状态边界错误，不代表当前修复通过。用户负责启动 `runClient0`；修复后的走图仍需从 `run/logs/latest.log` 提取到本目录。

## 已有记录

| 文件 | 结论 |
|---|---|
| `2026-07-11-172027-rot-only-fail.md` | 16 success 对应 32 Rot；Timer/Simulation 大量触发，Rot-only 已否定 |
| `2026-07-11-normal-aim-flight-fail.md` | Normal frame/flying eye 漂移触发 RotationPlace；bounded yaw 触发 AimModulo360；搭高乱飞为创造飞行 |

## 复测配置

1. 连接 `127.0.0.1:25565`。
2. G 开关 Scaffold。
3. 开启 Scaffold `Debug`，`Debug Interval` 可设为 1。
4. 先测试 `Rotation Timing=Normal`，覆盖直搭、WA/WD、跳跃和 90/180 度转头。
5. 再测试 OnTick 与 OnTickSnap；手动连续转头三圈以上后继续搭路，检查 `AimModulo360`。
6. Tower 验收使用 Survival；另在创造模式开启 flying，确认 Scaffold 不执行竖直搭高/Tower motion。

## 提取内容

```powershell
Select-String -Path run\logs\latest.log -Pattern '\[ScaffoldDebug\]|\[ScaffoldNetTrace\]|Grim|RotationPlace|PositionPlace|Timer|Simulation|InvalidOrder'
```

保存时必须保留原始顺序，不手工改写 packet 行。至少核对：

- 同一 frame 最多一个 placement 终态；
- no-hit 没有对应 Scaffold transaction packet 或 `UseItemOn`；
- Normal 为同坐标 `PosRot(target) -> UseItemOn -> PosRot(player)`，provider 保持 ACTIVE，后续 vanilla flying 关联同一 frame；
- OnTick 为同坐标 `PosRot(target) -> UseItemOn -> PosRot(player)`；
- OnTick target/restore 的 position 完全相同；
- packet raw yaw 不跨越到不同的 360 度分支；
- 零 `useItemOn:FAIL`，Timer/Simulation/PositionPlace/RotationPlace/AimModulo360/InvalidOrder 不新增。
