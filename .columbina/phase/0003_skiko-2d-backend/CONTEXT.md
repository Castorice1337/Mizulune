# Phase 0003: Skiko 2D 渲染后端骨架

## 状态

partial

## 本阶段目标

为 OpenZen 的 2D UI/HUD 渲染增加 Skiko/Skia 后端骨架，保留 `Renderer` / `DrawContext` / `GlHelper` / `RenderUtil` 上层调用形态。默认仍使用 legacy OpenGL，Skiko 通过运行参数启用，3D 世界空间渲染暂不迁移。

## 实际完成内容

- 新增 `RenderBackend` 后端接口和 `BackendType`，建立 `OPENGL_LEGACY` / `SKIKO` 后端选择模型。
- 新增 `LegacyGlBackend` 作为默认 fallback，保持旧渲染路径零行为迁移。
- 新增 `SkikoBackend`，使用当前 OpenGL framebuffer 创建 Skia `DirectContext`、`BackendRenderTarget`、`Surface`、`Canvas`。
- `Renderer.render(...)` 改为后端调度，支持 `openzen.render.backend=SKIKO`，Skiko 初始化或渲染异常时自动回退 legacy。
- `DrawContext` 保留原 public API，并在 Skiko 后端开启时委托 rect、rounded rect、line、arc、path、text、texture、clip、shadow/blur 入口。
- `RenderUtil` 的 2D 纯形状入口接入 Skiko：`drawGradientV/H`、`drawRoundedRect`、`drawRoundedRectCorners`、`drawFilledRect(color)`、`drawShadow`。
- 增加 Skiko/legacy GL 混画 flush 钩子，玩家头像和 `RenderUtil.drawTexture(int...)` 仍走 legacy GL，但会在调用前 flush Skia、调用后标记 GL state dirty。
- 新增 `RenderBackendProbe` 测试面板，可用 `-PopenzenRenderProbe=true` 在游戏内显示 configured/active backend、fallback 状态和 Skiko debug summary。
- `build.gradle` 增加 Windows x64 Skiko runtime 和 Kotlin stdlib 依赖，同时挂到 `minecraftLibrary`，让 Forge/ModLauncher 游戏运行期可见；`runClient0` 可通过 `-PopenzenRenderBackend=SKIKO` 转发后端开关到游戏 JVM。
- `build.gradle` 新增 `skikoNativeRuntime` 配置和 `extractSkikoRuntime` 任务，将 `skiko-windows-x64.dll`、`skiko-windows-x64.dll.sha256`、`icudtl.dat` 解到 `build/skiko-runtime/windows-x64`，并给 client JVM 设置 `-Dskiko.library.path=...`。
- 修复 Skiko active 后文字与图形割裂的首轮问题：Skia font size 对齐 legacy atlas 的 `size / 2.0f` 行为。
- 继续修复 Skiko 字体/图标基线偏移：`SkikoBackend.drawString(...)` 按旧 `DrawContext -> CustomFont` 的 atlas top、`--y` 和 0.1 像素取整语义计算 baseline，不再用 Skia font-wide ascent 反推。
- 移除 Skiko 文本绘制中的 per-segment legacy `FontRenderer.getWidth(...)`、中途 `flush()` / `resetGLAll()` 和横向 `scaleX`，避免文字绘制阶段污染 Skia/GL 状态。
- 回撤上一轮高风险的全局 `FontRenderer.getWidth(...)` / `getBounds(...)` CPU AWT 测宽改动和 `GlHelper` GUI scale 缓存 key 改动，避免改变 legacy 布局基线。
- 新增 `RenderBackend.measureTextWidth(...)`；Skiko active 时 `GlHelper.getStringWidth(...)` 走 Skia font 测宽，legacy 路径继续用旧缓存和旧 `FontRenderer`。
- `SkikoBackend` 增加 `GlStateGuard`，在 begin/end 和 legacy GL 混画边界恢复 FBO、VAO/VBO、shader program、texture unit、viewport、scissor、blend/depth/color mask 等关键 OpenGL 状态。
- `Renderer.renderWithPose(...)` 在 Skiko 路径下临时 concat 外部 `PoseStack`；`RenderUtil` 通过 `Renderer.canUseSkiko2D(poseStack)` 限制 Skiko 分流，避免 world/3D pose 误进入 Skia 2D surface。

## 改动文件

| 文件 | 改动说明 |
|---|---|
| `build.gradle` | 增加 Skiko/Kotlin `implementation` 与 `minecraftLibrary` 依赖、`openzenRenderBackend` run config 转发、Skiko native runtime 抽取任务和 `skiko.library.path` |
| `src/main/java/shit/zen/render/Renderer.java` | 增加后端选择、fallback、`renderWithPose(...)` 和 begin/end 调度 |
| `src/main/java/shit/zen/render/RenderBackendProbe.java` | 可开关的游戏内后端验证面板 |
| `src/main/java/shit/zen/render/DrawContext.java` | 保留旧实现，增加可选 `RenderBackend` 委托 |
| `src/main/java/shit/zen/render/GlHelper.java` | 玩家头像 legacy GL 绘制前后通知后端 flush/reset；Skiko active 时用后端安全测宽，避免触发旧 glyph atlas 上传 |
| `src/main/java/shit/zen/utils/render/RenderUtil.java` | 部分 2D helper 在 Skiko 后端开启时走 `DrawContext` |
| `src/main/java/shit/zen/render/backend/BackendType.java` | 后端类型枚举和 system property 解析 |
| `src/main/java/shit/zen/render/backend/RenderBackend.java` | 后端接口，包含外部 `PoseStack` 临时应用和安全测宽入口 |
| `src/main/java/shit/zen/render/backend/LegacyGlBackend.java` | legacy fallback 后端 |
| `src/main/java/shit/zen/render/backend/SkikoBackend.java` | Skiko/Skia OpenGL framebuffer 绑定、2D draw 实现、字体基线适配、GL state guard 和 PoseStack concat |

## 新增/修改的核心类

| 类/模块 | 作用 |
|---|---|
| `RenderBackend` | 抽象 2D begin/end、transform、clip、shape、text、texture、blur/shadow |
| `LegacyGlBackend` | 默认旧后端，不处理 2D，让 `DrawContext` 继续执行原 OpenGL 代码 |
| `SkikoBackend` | Skia GPU surface 后端，绑定当前 Minecraft framebuffer |
| `RenderBackendProbe` | 运行期验证实际 active backend 是否为 Skiko |
| `Renderer` | 后端生命周期、fallback 和当前 `DrawContext` 管理 |
| `DrawContext` | 上层 API 与后端委托边界 |

## 关键实现决策

- 默认后端仍是 `OPENGL_LEGACY`，避免 Skiko native/runtime 问题影响现有客户端。
- Skiko 仅作为 2D UI/HUD 后端；ESP 3D、AABB、world box、trajectory、XRay 等世界空间渲染不迁移。
- Skiko 运行开关通过游戏 JVM system property 读取：`openzen.render.backend=SKIKO`；Gradle 开发期可用 `-PopenzenRenderBackend=SKIKO` 传入。
- 测试面板开关通过 `openzen.render.probe=true` 读取；Gradle 开发期可用 `-PopenzenRenderProbe=true` 传入。
- Skiko/Kotlin 依赖必须同时在 `implementation` 和 `minecraftLibrary` 中声明；仅有 `implementation` 会编译通过，但游戏运行期 `ModuleClassLoader` 看不到 `org.jetbrains.skia.DirectContext`。
- `minecraftLibrary` 只能解决 Java class 可见性；Skiko native loader 在 ModLauncher 环境下仍可能读不到 runtime jar 根目录资源，开发期通过 `-Dskiko.library.path=build/skiko-runtime/windows-x64` 指向已抽取 native 文件。
- Skiko 字体不能直接使用 `FontRenderer.getSize()`；legacy `Fonts.getCustomFont(...)` 会按 `size / 2.0f` 创建 `CustomFont`，Skiko 字号先按该行为对齐。
- Skiko 文本绘制和 `GlHelper` 分段布局期间不能调用可能上传 glyph atlas 的 legacy 测宽路径；Skiko 帧内测宽必须走 `RenderBackend.measureTextWidth(...)`。
- `RenderUtil` 的 2D helper 不能只看 `Renderer.isSkikoEnabled()`，必须带 `PoseStack` 边界判定，防止 3D/world pose 被画到 Skia 2D surface。
- Skiko 后端失败只影响后续帧，`Renderer` 会标记失败并回退 legacy。
- Skiko 与 legacy GL texture/player head 混画时先 flush Skia，再执行 legacy GL，之后调用 `DirectContext.resetGLAll()`。
- 文字首版实现 `§` 颜色码分段绘制和换行推进；text glow 仍保持“先画普通文字”的首版语义。
- 背景毛玻璃 blur 和 GL texture/player head interop 不在本阶段强迁，避免把 framebuffer readback 和 skin 动态贴图问题混进首版。

## 复用的已有结构

- 继续复用 `Renderer.render(...)` 作为 GUI 渲染入口。
- 继续复用 `DrawContext`、`Paint`、`RoundedRectangle`、`Path`、`Texture` 作为上层数据结构。
- 继续复用 `Renderer.resetRenderState()` 做 Skiko flush 后的 GL state 恢复。
- 继续保留 `RoundedRectShader`、`BlurRenderer`、`RenderUtil` legacy 代码作为 fallback。

## 对后续 AI 的提醒

- 不要删除旧 OpenGL 2D shader/FBO 代码，直到 Skiko backend 经 `runClient0` 和人工视觉验收稳定。
- 不要把 `drawSolidBox`、`drawOutlineBox`、`drawColoredBox`、`drawFilledColoredBox`、`drawSpiralEffect`、`drawBoxVerts` 迁到 Skiko。
- 如需扩展 ResourceLocation/player head interop，优先补 `SkikoBackend` / 后续 `SkikoTextures`，不要在 GUI 层散落解码逻辑。
- 如需改 blur，区分元素自身 blur/shadow 与背景毛玻璃 blur；后者需要 framebuffer snapshot/readback 或继续 legacy。
- 如果后续还有 `Cannot find skiko-windows-x64.dll.sha256` 或 `LibraryLoadException`，优先检查 `extractSkikoRuntime` 是否执行、`build/skiko-runtime/windows-x64` 是否包含 Skiko native 文件，以及 client JVM 是否带有 `-Dskiko.library.path=...`。
- 如果后续文字仍偏移，先调 `SkikoBackend` 的字体尺寸、baseline 和 measure 适配，不要改各 HUD/GUI 调用点的布局参数。
- 如果后续出现圆角背景间歇消失，先检查是否又在 Skiko frame 中间引入了 legacy GL 测宽、texture 上传、重复 flush 或未配对的 scissor/clip，而不是把相关 HUD 背景切回 legacy。

## 未完成内容

- 用户已确认 `runClient0` 下 Skiko probe 可显示 `active=SKIKO`。
- 用户已确认 `runClient0 -PopenzenRenderBackend=SKIKO` 下 `active=SKIKO` 且渲染正常；字体/图标基线、测宽和 Skiko 状态隔离当前人工验收 PASS。
- Skiko texture interop 只是普通 `ResourceLocation` 解码尝试；GL id / player skin 动态贴图当前通过 legacy GL 混画保留，后续仍需要 backend texture 包装。
- 背景毛玻璃 blur 尚未用 Skia snapshot 实现。
- DLL 注入路径尚未处理 Skiko runtime jar/native resource 可见性。
- 未做 GUI scale 1x/2x/3x、resize、clip 泄漏、中文/图标字体视觉验收。

## 测试状态

PASS
