# Geometry 审计

## 组件

`ScaffoldGeometry` 是本地 LB 几何对象的 Java 移植，包含：

- `Line`
- `LineSegment`
- `NormalizedPlane`
- `AlignedFace`
- nearest point、plane intersection、box/line distance 等运算

`ScaffoldFacePointFactory` 使用这些对象实现 STABILIZED target point。

## STABILIZED 流程

1. 从 block state 的真实 voxel shape AABB 提取 interaction face。
2. 对高侧面按 LB 规则应用 `y >= 0.6` 的上半区限制，不设置 0.85 上界。
3. 对 face 每个维度裁掉 15%，避免边缘点。
4. 如果存在 movement line，用玩家到 line 的偏移方向裁切目标 face。
5. 从 planning eye 沿当前 server rotation 建 rotation line。
6. 取目标 face 上离 rotation line 最近的点。

最终 target 保存原始真实 face 的 minY，而不是 search/crop face 的临时边界。

## parity 与偏差

当前 side-face 范围已撤销 phase 0051 的 0.85 偏差，恢复本地 LB `FaceTargetPositionFactory.kt` 的 `y >= 0.6` 与 15% trim。`ScaffoldFacePointFactory` 还统一承载八种 AimMode，Technique 不再建立平行几何路径。

## strict gate

`ScaffoldPlacementPipeline.matches(...)` 要求：

- hit type 为 block；
- exact support block；
- exact face；
- hit Y 不低于 minY。

phase 0051 历史 no-hit 分类为 260 null、21 wrong block、18 wrong face、0 minY-only。它说明当时 strict gate 主要在阻止完全未对准的 rotation，而不是被 minY epsilon 卡死；phase 0052 仍需新日志重新分类。

## 风险

- `ScaffoldFacePointFactory` 同时使用 planning eye、current server rotation 和 player position；这些值必须属于同一 frame 规划轮次。
- placement 必须消费同一 frame，不能恢复 motion/post-send retarget。
- 不能通过扩大 search face 或接受 wrong face 来补偿 rotation 时序错误。
- 半砖/台阶的真实 shape 和 minY 必须保持 exact。
- 玩家 AABB 与 intended block AABB 的检查属于 placement preflight，不应塞进 AimMode 几何。

## 验收

1. 纯几何测试继续覆盖退化 face、trim、nearest point 和 plane intersection。
2. 游戏内记录 target point 到 face 边界的最小距离。
3. 将 no-hit 按 null/wrong-block/wrong-face/minY 分类。
4. 只有 phase 0052 新日志证明持续 wrong-face，才考虑调整 LB 几何；不得恢复无证据的 0.85 特例。
