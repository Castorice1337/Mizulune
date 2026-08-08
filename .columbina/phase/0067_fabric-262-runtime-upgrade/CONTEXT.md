# Phase 0067：Fabric 26.2 runtime 升级与 Sodium/Iris 适配

## 状态

complete

## 阶段目标

在不改变根 Forge/Patchify/ASM Minecraft 1.20.1 / Java 17 合同和既有功能算法的前提下，将独立 `fabricmod/` 移植到 Minecraft 26.2 / Java 25，并以可复现、非嵌套方式引入 Sodium、Iris 和 ViaFabricPlus。验收标准是 Fabric Mod 构建成功、`runClient` 可进入世界且退出正常，并由用户完成人工渲染回归。

## 完成结论

- Fabric 26.2 已完成编译、构建和客户端启动：Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.156.0+26.2、Java 25。
- Sodium `0.9.1+mc26.2`、Iris `1.11.2+mc26.2`、ViaFabricPlus `4.6.1` 已按 immutable ID 和 SHA-512 固定，作为并列 runtime Mod 加载，不嵌入 Mizulune JAR。
- `runClient` 实际加载 96 个 Mod，可进入单人世界并正常退出；无 Mixin apply error、Skiko deferred replay error 或客户端崩溃。
- 原 1.20.1 Skiko blur、liquid glass、glow、圆角矩形、裁剪、纹理/icon 和 2D UI 调用顺序已通过 Fabric 26.2 adapter 恢复，没有重写原 SKSL 算法。
- Fabric 26.2 的 3D delayed submit 会在提交时冻结颜色状态，Scaffold 放置预览恢复预期的半透明颜色，不再被后续 reset 污染为白块。
- 根 Forge/ASM 1.20.1 完整构建继续 PASS；用户于 2026-08-08 对 UI/GPU 渲染模块回归明确确认 `PASS`。
- 该 PASS 后发现 Fabric 26.2 本地玩家 head yaw 未迁移，表现为 Scaffold/KillAura 转身但头部不随 silent rotation；现已补齐原 1.20.1 head-yaw 插值 hook，并由用户复测通过。
- OldHitting 已接入 26.2 `ItemInHandRenderer.submitArmWithItem` submit-collector 边界；FullBright 已接入 `LightmapRenderStateExtractor`，不再伪造缺少 effect instance 的 night-vision boolean。两项均由用户复测通过。

## 已落地内容

### 工具链与分发

- Fabric wrapper 升级到 Gradle 9.5.1、Loom 1.17.19，启用 Java 25 toolchain。
- 版本矩阵升级到 Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.156.0+26.2。
- access widener 迁移为 official-name `classTweaker v1 official`。
- `fabric26-source-compat.gradle` 提供构建期 generated source view，仅承载机械名称迁移；根 1.20.1 canonical source 不被批量改写。
- Sodium、Iris、ViaFabricPlus 使用固定工件校验和暂存；`runtimeOnly` 只服务开发运行，成品 Mizulune JAR 不 nest 外部 Mod。

### 26.2 语义适配

- 新增 `ItemCompat` 和 Fabric source overlay，适配 26.2 item tags/data components、packet、interaction、render-state 等 API，同时保持共享业务逻辑的 1.20.1 语义。
- 26.2 Fabric Mixin 改接真实 extract/submit、GUI draw 和 packet boundary；根双运行时架构测试显式登记这些直接边界。
- `ViaProtocolBridge` 改用 ViaFabricPlus 4.6.1 public API，并通过 endpoint lease 保存和恢复全局 target，FantNEL 返回主界面的生命周期已修复。
- Fabric 26.2 明确限定 OpenGL；不兼容的 Vulkan/raw-GL 组合 fail fast，Java 17 `jvm.dll` official ID114 native sink 不在 Java 25 Fabric 中加载。

### 原算法渲染移植

- `FabricDeferredSkiko` 在 26.2 GUI 真正提交时获取世界主 framebuffer，并将原 `SkikoBackend` 调用重放到带 depth 的透明 GUI FBO。
- `FabricBlurCompositor` / `GuiRendererMixin` 在 `GuiRenderer.draw()` 的实际主 target 边界完成 premultiplied composite，避免过早抓帧、depth clear 崩溃和世界状态泄漏。
- `FabricSubmissionBackend` 将普通矩形、圆角矩形、线/弧/路径、字体/glow text、纹理和头像都按原调用顺序进入同一 deferred queue，修复 ModuleList 背景、Notification icon、glow 和裁剪穿透。
- `SkikoBackend` 完整保存/恢复 framebuffer、viewport/scissor、blend/depth/cull、program/VAO、全部 texture unit/sampler、pixel pack/unpack、sRGB/stencil/dither/multisample 状态。
- 3D `FabricRenderBridge` 在延迟 lambda 创建时快照 RGBA，修复 Scaffold 预览颜色被后续全局状态重置覆盖的问题。

### Post-PASS 头身旋转回归

- 26.2 `LivingEntityRenderer.extractRenderState` 会先计算绝对 head yaw，再由它派生 `bodyRot` 和头部相对 `yRot`。
- 原 `HumanoidModelMixin` 只接入了 `CameraPitchEvent`，漏掉 1.20.1 `LivingEntityRendererPatch` 对 head-yaw `Mth.rotLerp` 的 `RotationAnimationEvent` 替换，因此 body 使用 silent yaw、head 仍使用 vanilla camera yaw。
- 现在使用 `ModifyExpressionValue` 替换该唯一 head-yaw 插值表达式，并调用原 `LivingEntityRenderHookCallbacks.headYaw(...)`；Scaffold/KillAura 和 1.20.1 继续共享同一旋转语义，没有修改模块算法。
- 新增双运行时架构回归，锁定 26.2 必须同时保留 head yaw 与 pitch hook；Fabric build、Mixin apply、进世界、退出和用户第三人称复测均已通过。

### Post-PASS OldHitting / FullBright 回归

- OldHitting 的共享启用条件和原 `PoseStack` 动画保持不变；Fabric overlay 只把最终物品绘制从 1.20.1 `MultiBufferSource` 适配到 26.2 `SubmitNodeCollector`。
- `ItemInHandRendererMixin` 在 `submitArmWithItem` 的原 pose scope 开头调用适配器，命中时执行原动画、提交物品并取消 vanilla 分支；`tick` 的 held-item event 同时恢复。
- FullBright 不再通过 Fabric `LivingEntity.hasEffect` 返回一个不存在的 night-vision effect；`LightmapRenderStateExtractorMixin` 在 lightmap state 真正更新时写入原亮度百分比对应的 `nightVisionEffectIntensity`。
- Fabric `runClient` 加载 96 个 Mod、进入世界并正常停止，无 Mixin apply error；用户确认头身旋转、OldHitting 和 FullBright 均已恢复。

## 关键文件

- `fabricmod/build.gradle`
- `fabricmod/gradle/fabric26-source-compat.gradle`
- `fabricmod/src/main/resources/mizulune.classtweaker`
- `fabricmod/src/fabric26/java/shit/zen/fabric/render/FabricDeferredSkiko.java`
- `fabricmod/src/fabric26/java/shit/zen/fabric/render/FabricBlurCompositor.java`
- `fabricmod/src/fabric26/java/shit/zen/fabric/render/FabricSubmissionBackend.java`
- `fabricmod/src/fabric26/java/shit/zen/fabric/render/FabricRenderBridge.java`
- `src/main/java/shit/zen/render/backend/SkikoBackend.java`
- `src/main/java/shit/zen/platform/ItemCompat.java`
- `src/main/java/shit/zen/fantnel/ViaProtocolBridge.java`
- `src/test/java/shit/zen/patch/DualRuntimeMixinCoverageTest.java`

## 固定 runtime 工件

| Mod | 版本 | Modrinth version ID | SHA-512 |
|---|---|---|---|
| Sodium | `mc26.2-0.9.1-fabric` | `2Yom1N68` | `627fbf9625a4b94693c789c84a0686ffe558c0b1ecbeccf2602a903caafa9e126548b644282aa9c391dc798930d378724a383bea6b4397c5294d59ba5c0a6936` |
| Iris | `1.11.2+26.2-fabric` | `oaD6KQls` | `c1b46bcd1a0068deab3ae364a7229d31e27b7d45aea960e47503b8514354426badf83f354529db35d83e6b55727a53895fb9f13085e51d8148ee6765affac924` |
| ViaFabricPlus | `4.6.1` | `NVFW4VRx` | `41add6788f1df8b64f1c606f71e8ebeef2e4435a2a85c026eee5bfcaf6fc0f4ee28a30a4481e757a88007c62b32b8e8aec0d968dede0a51be95e72f6ddaa85e8` |

## 项目边界

- 根 Forge/ASM 继续固定 Minecraft 1.20.1 / Java 17；Fabric 26.2 的类型和生命周期通过 adapter/overlay 隔离。
- 本阶段是移植，不重写 UI、blur、glow 或 liquid glass 算法；原 Skiko/SKSL 为唯一实现，Fabric 代码只负责 26.2 framebuffer、提交时机和 GL state 对接。
- 不 Mixin Sodium/Iris implementation class，只依赖 Minecraft/Fabric 的稳定边界。
- OpenGL 是本阶段唯一承诺的 Skiko backend；Vulkan 是独立后续项目。
- official ID114 native sink 仍归 Forge/ASM Java 17 分发；Fabric 仅保留 logical metadata/lifecycle。
- ViaFabricPlus target 是全局状态；endpoint lease 支持单 active endpoint 的保存/恢复，不承诺不同目标的并发连接。

## 非阻塞发布加固项

- 补充代表性 Iris shader-pack 矩阵和窗口 resize/全屏切换压力测试。
- 补充 ViaFabricPlus 代表性旧版本服务器连接矩阵。
- Vulkan 维持明确 fail-fast；真正兼容需要独立 backend，不属于本阶段验收。

## 测试状态

PASS。Fabric build、96-Mod `runClient`、根 Forge/ASM build、UI/GPU 渲染、Scaffold/KillAura 头身旋转、OldHitting 与 FullBright 均通过；详见 `test.md` 与 `debug.md`。
