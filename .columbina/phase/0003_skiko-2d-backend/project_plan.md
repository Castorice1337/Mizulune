\# Skiko 替换 OpenZen 2D UI/HUD 渲染底层



\## Summary

\- 目标调整为：\*\*只用 Skiko/Skia 接管游戏内 2D UI/HUD 渲染底层，保留 3D 世界渲染的 Minecraft/OpenGL 路径\*\*。

\- 采用 `org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.148.1`。Maven Central 当前显示该 Windows x64 runtime 为 `0.148.1`；Skiko API 文档提供 `Surface.makeFromBackendRenderTarget(...)`，可把 GPU-backed buffer 包成 Skia `Surface`。

\- 不使用 `SkiaLayer` / AWT 窗口路线；Skiko 后端绑定 Minecraft 当前 OpenGL framebuffer，在 `GameRendererPatch` 的 GUI 后置渲染阶段绘制。



\## Key Changes

\- 新增后端抽象：

&#x20; - `shit.zen.render.backend.RenderBackend`

&#x20; - `BackendType`

&#x20; - `LegacyGlBackend`

&#x20; - `SkikoBackend`

&#x20; - `SkikoFonts`

&#x20; - `SkikoPaints`

&#x20; - `GlStateGuard`

\- 保留上层 public API：`Renderer`、`DrawContext`、`GlHelper`、`Paint`、`Path`、`FontRenderer`、`RenderUtil` 的 2D 方法签名尽量不变。

\- `Renderer.render(...)` 改为选择后端：

&#x20; - 默认先保留 `OPENGL\_LEGACY`

&#x20; - 加开关启用 `SKIKO`

&#x20; - Skiko 崩溃或初始化失败时自动回退 legacy 后端

\- `SkikoBackend.begin(...)` 每帧读取当前 `GL\_FRAMEBUFFER\_BINDING`、窗口物理像素尺寸和 GUI scale，创建/复用 `DirectContext`、`BackendRenderTarget`、`Surface`、`Canvas`。

\- `SkikoBackend.end(...)` 执行 canvas restore、`flushAndSubmit()`，然后调用现有 `Renderer.resetRenderState()`。

\- 迁移全部 2D API：

&#x20; - `DrawContext.drawRect/drawRoundedRect/drawLine/drawArc/drawPath/drawString/drawTexture/clip/clipRoundedRect/drawBlur`

&#x20; - `GlHelper.drawText/drawRoundedRect/drawPlayerHeadRounded` 等 2D helper

&#x20; - `RenderUtil.drawFilledRect/drawGradientV/drawGradientH/drawRoundedRect/drawBlurredRect/drawTexture/drawShadow/pushScissor/popScissor`

\- 暂不迁移 3D API：

&#x20; - `drawSolidBox`

&#x20; - `drawOutlineBox`

&#x20; - `drawColoredBox`

&#x20; - `drawFilledColoredBox`

&#x20; - `drawSpiralEffect`

&#x20; - `drawBoxVerts`

&#x20; - `ESP/ChestESP/Projectiles/XRay/Scaffold` 的世界空间渲染继续走原路径。



\## Dependency And Native Packaging

\- 开发期先在 Gradle 依赖中引入 Skiko Windows x64 runtime，并确认 `.\\gradlew.bat runClient0` 可启动。

\- 发布/注入路径单独处理 Skiko runtime：

&#x20; - 不假设把 dependency 加进 Gradle 就能在 DLL 注入模式正常加载 native。

&#x20; - 优先方案：构建时把 Skiko runtime jar 作为独立依赖资源嵌入 native loader，注入时解出并加入可见 classpath，再启动 `DllBootstrap`。

&#x20; - 若 classpath 注入不可行，再评估 fat jar 或显式解出 native dll 并设置 Skiko native lookup 路径。

\- 保持 `native/zen.jar` 资源路径对项目自有字体和 cloud assets 的兼容。



\## Test Plan

\- 最小验证：

&#x20; - 用 SkikoBackend 只画一个矩形到 GUI overlay。

&#x20; - 确认坐标、GUI scale、窗口 resize 后位置正确。

\- 2D 功能验证：

&#x20; - IntroAnimation

&#x20; - ClickGUI / PanelClickGui / SettingsPanel / SettingsPopup

&#x20; - HUD：TargetHUD、KeyBinds、ModuleList、PlayerList、PotionEffects、LieDetector

&#x20; - 文字：英文、中文、Material Icons、Zen icon、Minecraft `§` 颜色码

&#x20; - clip：滚动列表、设置面板、圆角裁剪

&#x20; - shadow/blur：水印、HUD 背板、弹窗、name tag 背景

&#x20; - texture：普通 PNG、cloud assets、玩家头像

\- 3D 回归：

&#x20; - ChestESP

&#x20; - Scaffold box

&#x20; - Projectiles 3D trajectory

&#x20; - ESP 2D outline + 3D glow

&#x20; - XRay chunk rebuild 行为

\- 命令：

&#x20; - `.\\gradlew.bat jar`

&#x20; - `.\\gradlew.bat runClient0`

&#x20; - native 发布路径改完后再跑 `.\\gradlew.bat dll`



\## Assumptions

\- 第一阶段只支持 Windows x64。

\- WebUI 不参与本次迁移。

\- 旧 OpenGL 2D 后端至少保留到 SkikoBackend 在开发和注入路径都稳定。

\- 参考来源：Skiko Maven Central Windows x64 runtime `0.148.1`，以及 Skiko `Surface.makeFromBackendRenderTarget(...)` API 文档说明该 API 用于包装 GPU-backed buffer。  

&#x20; Sources: \[Maven Central skiko-awt-runtime-windows-x64](https://central.sonatype.com/artifact/org.jetbrains.skiko/skiko-awt-runtime-windows-x64), \[Skiko Surface.makeFromBackendRenderTarget](https://jetbrains.github.io/skiko/skiko/org.jetbrains.skia/-surface/-companion/make-from-backend-render-target.html)



