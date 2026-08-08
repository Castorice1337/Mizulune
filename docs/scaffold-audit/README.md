# Scaffold Audit

本目录记录 Scaffold 从 phase 0050/0051 失败基线到 phase 0052 LB 对齐实现的源码、事务与验证证据。

## 当前状态

`AUTOMATED_PASS / WAITING_USER_PASS`

- 当前代码已完成 frame transaction、LB 功能接线、网络时序和自动化测试收口。
- 最新 corrected snapshot 已通过 229/229 tests、`compileJava`、包含 reobf/836 类混淆的完整 `build` 与 `git diff --check`。
- phase 0050/0051 的运行日志只作为历史失败基线。
- phase 0052 已有两轮用户 FAIL：Rot-only 触发 Timer/Simulation，vanilla-only Normal 触发 RotationPlace，并确认 AimModulo360 与创造飞行根因；最新修复仍待复测。

## 入口

| 文档 | 内容 |
|---|---|
| `executive-summary.md` | 当前结论与范围 |
| `placement-transaction.md` | frame、strict gate 与 placement 不变量 |
| `packet-flow.md` | vanilla flying、duplicate PosRot、Blink、listener 与 final-write trace |
| `event-timeline.md` | 事件顺序 |
| `target-finder.md` | finder、geometry 与 offsets |
| `rotation-timing-parity.md` | Normal/OnTick/OnTickSnap |
| `remediation-plan.md` | 已完成项与用户验收矩阵 |
| `runtime-traces/` | phase 0052 的真实运行 trace |

## 状态词

| 状态 | 含义 |
|---|---|
| `HISTORICAL_FAIL` | 旧实现已确认失败，只作根因证据 |
| `AUTOMATED_PASS` | 编译/测试/静态约束通过 |
| `WAITING_USER_PASS` | 尚待用户实机确认 |
| `PASS` | 用户明确确认通过 |

任何 build 或 JUnit 结果都不能单独把 Scaffold 标记为游戏内 PASS。
