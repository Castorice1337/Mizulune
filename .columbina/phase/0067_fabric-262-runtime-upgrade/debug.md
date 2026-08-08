# Debug Record: Phase 0067

## Debug 类型

phase / migration / build / render-state / framebuffer

## 故障链

1. Fabric 工具链升级后，共享 1.20.1 source 首轮出现 111 个 26.2 API 编译错误；机械名称迁移后仍有 66 个 item、packet、render-state 和 GPU 语义错误。
2. 编译闭合后，FantNEL 页面无法返回主界面。
3. ClickGUI 首轮会空白、卡死并把世界染红；blur、liquid glass、glow、圆角矩形、背景、进度条、icon 和裁剪陆续出现缺失或错位。
4. GUI FBO 没有 depth attachment 时，ClickGUI 的 depth clear 会触发客户端崩溃。
5. 部分图元 deferred、部分图元 immediate，破坏了原 Skiko 调用顺序，ModuleList 背景、Notification icon、glow 和文字裁剪相互覆盖。
6. 3D Scaffold 预览 delayed submit 捕获了可变全局颜色状态；真正绘制前状态已被 reset 为白色，导致半透明蓝块变成实心白块。
7. UI/GPU 渲染 PASS 后，第三人称本地玩家在 Scaffold 和 KillAura silent rotation 期间出现身体转向但头部仍朝 vanilla camera yaw 的异常扭转。
8. 头身旋转修复后，OldHitting 虽可启用但第一人称动画不执行；FullBright 也无法改变 26.2 世界 lightmap。

## 根因

### Minecraft 26.2 API 语义变化

- entity/world/hand renderer 由旧 immediate path 转为 render-state + submit collector 生命周期。
- 多个 item subclass、packet 和 interaction contract 被 tags/data components 或新类型替代。
- 根 1.20.1 canonical source 不能直接写入 26.2 类型，需要 generated source view 与显式 adapter/overlay。

### GUI 抓帧和提交时机错误

- 早期实现从错误的生命周期抓取 framebuffer，拿到的不是 GUI 真正绘制时的干净世界 backdrop。
- GUI 实际 target、Skiko replay 和最终 composite 不在同一个明确边界，导致空白、灰雾、背景错帧和世界状态泄漏。
- 透明 GUI overlay 缺少 depth，原 ClickGUI 的 depth clear 与 26.2 FBO 合同冲突。

### OpenGL 状态恢复不完整

- 仅恢复少量 blend/texture 状态不足以与 Sodium/Iris/Minecraft 26.2 共存；texture unit、sampler、pixel store、sRGB、stencil、dither、multisample 等遗留状态会污染后续世界渲染。

### 延迟提交破坏顺序或捕获可变状态

- 只 defer liquid/blur/glow 而让普通图元 immediate，改变了原 UI draw order。
- 3D geometry lambda 引用 `LegacyRenderSystem.State` 而非提交时快照，执行时读取到后续调用修改后的颜色。

### 26.2 render-state 漏迁 head yaw

- 1.20.1 `LivingEntityRendererPatch` 会包装 head yaw 的 `Mth.rotLerp(partialTick, yHeadRotO, yHeadRot)`，通过 `LivingEntityRenderHookCallbacks.headYaw(...)` 派发 `RotationAnimationEvent`。
- 26.2 `LivingEntityRenderer.extractRenderState` 仍先做同一 head-yaw 插值，再以结果计算 `state.bodyRot` 和头部相对 `state.yRot`。
- Fabric `HumanoidModelMixin` 当时只重定向 `getXRot(partialTick)` 以保留 pitch，没有替换 head-yaw 插值；`LivingEntityMixin.tickHeadTurn` 已让 body 使用 silent yaw，因此最终形成“转身不转头”。

### held-item 与 lightmap 边界仍停留在 1.20.1

- 26.2 已将第一人称物品绘制改为 `submitHandsWithItems -> submitArmWithItem -> SubmitNodeCollector`；旧 `ItemInHandRendererMixin` 被兼容层排除且未注册，因此 OldHitting 没有执行入口。
- 26.2 将 LightTexture 更新改为 `LightmapRenderStateExtractor.extract`。Fabric 仍通过 `LivingEntity.hasEffect(NIGHT_VISION)` 伪造 boolean，但 `GameRenderer.nightVisionScale` 随后直接取得并使用 `MobEffectInstance`；只有 boolean 而没有实例不再是有效合同。

## 修复

- 建立 `ItemCompat`、Fabric source overlay 和 26.2 直接 Mixin boundary，完成 item/packet/render-state 语义移植，不向根 1.20.1 API 泄漏。
- 使用 ViaFabricPlus 4.6.1 public API 和 endpoint target lease 恢复 FantNEL screen/connection 生命周期。
- 参考现有成熟客户端的 framebuffer 获取模式，但保留 Mizulune 原 `SkikoBackend`、`SkikoEffects`、`SkikoLiquidGlass` 和 SKSL；未重写 blur/liquid 算法。
- `FabricDeferredSkiko` 在 26.2 GUI 真正绘制边界抓取主世界 framebuffer，并在带 depth 的透明 GUI FBO 中按原命令顺序 replay。
- `FabricBlurCompositor` 在 `GuiRenderer.draw()` 的真实 main-target 边界执行 premultiplied composite。
- `FabricSubmissionBackend` 统一 defer 矩形、圆角矩形、线/弧/路径、文字/glow text、纹理和头像，恢复调用顺序；mutable path 在入队时复制。
- `SkikoBackend` 扩展为完整 GL state 保存/恢复，覆盖 framebuffer、viewport/scissor、program/VAO、blend/depth/cull、所有 texture unit/sampler、pixel pack/unpack、sRGB/stencil/dither/multisample。
- `FabricRenderBridge.submit` 在 lambda 创建时快照 RGBA，消除 3D delayed draw 的可变状态污染。
- `HumanoidModelMixin` 使用 `ModifyExpressionValue` 包装 `extractRenderState` 内唯一的 `Mth.rotLerp(FFF)F`，调用原 `LivingEntityRenderHookCallbacks.headYaw(...)`；26.2 随后按 vanilla 顺序从该结果派生 body-relative `yRot`，没有复制或重写 body rotation 算法。
- `DualRuntimeMixinCoverageTest` 新增 head-yaw/pitch parity 断言，防止后续升级再次只迁 pitch。
- 将 OldHitting 的启用判定收敛到共享 `OldHitting.shouldApply(...)`；1.20.1 和 26.2 只分别保留 buffer/collector 类型不同的最终提交 adapter。
- 新增 Fabric 26.2 `ItemInHandRendererMixin` 和同名 hook overlay，在 `submitArmWithItem` 的原 pose scope 复用 `applyHitAnimation(...)`，再用 `SubmitNodeCollector` 提交物品并取消 vanilla 分支。
- 新增 `GameRendererHookCallbacks.onFullBrightScale()` 与 `LightmapRenderStateExtractorMixin`，只在真实 lightmap state update 时覆盖 `nightVisionEffectIntensity`；删除 Fabric `hasEffect` 假状态注入。

## 回归证据

- Fabric `compileJava processResources` PASS。
- Fabric `build` PASS，产出 `mizulune-fabric-1.2+mc26.2.jar`。
- Fabric `runClient` PASS：96 个 Mod 同时加载、进入世界、正常退出，无 Mixin/Skiko replay 错误。
- 根 `gradlew build` PASS：Forge/ASM 1.20.1 全量构建与双运行时边界测试通过。
- 用户多轮截图回归覆盖 ClickGUI、ModuleList、Notification、HUD、blur、liquid glass、glow、裁剪和 Scaffold 3D preview；最终确认 `PASS`。
- Post-PASS head-yaw 修复后，Fabric compile/build PASS、双运行时定向测试 PASS、96-Mod `runClient` 的 Mixin apply/进世界/退出 PASS；第三人称 Scaffold/KillAura 视觉回归等待用户确认。
- 用户确认 Scaffold/KillAura 头身旋转 PASS。
- OldHitting/FullBright 修复后，Fabric compile/build、根完整 build、定向边界测试和 96-Mod `runClient` 均 PASS；用户最终确认两项 PASS。

## 未采用路线

- 未新增空壳 renderer/item 类型骗过编译。
- 未用浅色矩形、普通 blur 或新 shader 冒充原 glow/liquid glass。
- 未把复杂行为塞进 Gradle 字符串替换脚本。
- 未 Mixin Sodium/Iris implementation class。
- 未改变根 Forge/ASM 1.20.1 的算法和 native ABI。

## 结论

UI/GPU 问题不是原 SKSL 算法失效，而是 Fabric 26.2 的 framebuffer 生命周期、GUI 提交时机、GL 状态恢复、绘制顺序和 delayed state capture 均发生了边界变化。后续头身、OldHitting 和 FullBright 问题分别来自 render-state head-yaw、held-item submit 和 lightmap extraction 三个漏迁边界。所有修复均复用原 1.20.1 事件、动画和亮度语义，只做 26.2 生命周期/类型适配；自动化与用户复测全部 PASS。
