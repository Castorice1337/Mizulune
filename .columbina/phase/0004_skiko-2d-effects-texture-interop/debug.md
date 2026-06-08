# Phase 0004 Debug Notes

## 2026-06-08：new ClickGUI 周围半透明黑色圆角污染 / 删除 RenderBackendProbe

### 现象

- 用户截图对比显示：Skiko 下 new ClickGUI 每个面板周围有一圈明显半透明黑色圆角矩形；原版 Zen 视觉只有更柔和的阴影。
- 用户说明：问题不是青色启用文字 glow，而是面板周围的黑色圆角污染。
- 用户要求删除 0003 阶段临时写的 `RenderBackendProbe` 测试类。

### 根因分析

- `CategoryPanel.render(...)` 使用 `RenderUtil.drawRoundedRect(..., radius, smoothness, color)` 绘制面板阴影底。
- legacy `rounded_rect.fsh` 中 `Smoothness` 是 SDF 边缘羽化宽度；Skiko 初版忽略 `smoothness`，把该调用当成普通实心圆角矩形绘制。
- 因为 new ClickGUI 的阴影矩形本身比面板大一圈，Skiko 实心绘制会显示成明显黑色外框。
- `RenderBackendProbe` 已完成 0003 active backend 验证，不再适合作为 0004 视觉对比阶段的默认工具。

### 最终修复

- `RenderUtil.drawRoundedRect(... smoothness ...)` 在 Skiko active 且 `smoothness > 1.01f` 时，改用 Skia blur mask 软边绘制，恢复旧 shader 的边缘羽化语义。
- 普通 `smoothness=1` 的圆角矩形仍走 Skia 实心 `drawRoundedRect`。
- 删除 `src/main/java/shit/zen/render/RenderBackendProbe.java`。
- 移除 `Renderer.renderInternal(...)` 中的 `RenderBackendProbe.render(...)` 调用。
- 移除 `build.gradle` 的 `openzenRenderProbe` / `openzen.render.probe` JVM 参数透传。

### 修改文件

| 文件 | 改动 |
|---|---|
| `src/main/java/shit/zen/utils/render/RenderUtil.java` | Skiko 下恢复 `smoothness` 软边语义；`drawShadow` 不再把 blur radius 当实体 spread |
| `src/main/java/shit/zen/render/Renderer.java` | 删除 probe 渲染调用 |
| `src/main/java/shit/zen/render/RenderBackendProbe.java` | 删除临时测试类 |
| `build.gradle` | 删除 `openzenRenderProbe` 参数透传 |

### 回归测试

- `.\gradlew.bat jar`：PASS，obfuscation class 数为 440，符合删除 probe 类。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO --dry-run`：PASS。
- `git diff --check`：PASS，仅 CRLF 工作区提示。
- 完整 new ClickGUI 黑色外圈视觉对比：WAITING_USER_PASS。

## 2026-06-08：PanelClickGui 触发 Scissor stack underflow 后 Skiko fallback

### 现象

- 用户复测反馈：Panel 模式仍没有文字；再次打开 GUI 后客户端原有 UI/HUD 全部消失，无法继续测试。
- 用户补充观察：UI 消失时 Hotkeys 一栏的圆角矩形仍能正常渲染，但没有文字。
- `run/logs/latest.log` 出现 `Render backend SKIKO failed, falling back to legacy OpenGL`。

### 证据

| 证据 | 说明 |
|---|---|
| `run/logs/latest.log` | 首个真实异常为 `java.lang.IllegalStateException: Scissor stack underflow` |
| `DrawContext.restore(DrawContext.java:115)` | `restore()` 在 Skiko backend active 时仍调用了 `GuiGraphics.disableScissor()` |
| `ModuleListPanel.renderModuleList(ModuleListPanel.java:300)` | Panel 模式模块列表的 `drawContext.restore()` 触发 underflow |
| `PanelClickGui.render(PanelClickGui.java:155)` | Panel 模式首帧渲染触发 Skiko fallback |

### 根因分析

- Skiko backend 的 `clipRect` / `clipRoundedRect` 使用 Skia canvas clip，不会向 Minecraft `GuiGraphics.ScissorStack` push scissor。
- `DrawContext.clipStack` 只记录了“本层存在 clip”，没有区分该 clip 属于 Skia canvas 还是 MC scissor。
- `DrawContext.restore()` 和 `clearClipStack()` 看到 `clipStack=true` 后无条件调用 `guiGraphics.disableScissor()`，导致 MC scissor 栈下溢。
- underflow 被 `Renderer.renderInternal(...)` 捕获后会把 `backendFailed` 置为 true，后续全局 fallback 到 legacy；用户看到的“圆角矩形还在、文字消失”是异常后的连锁状态污染表现。

### 最终修复

- `DrawContext.restore()` 在 `backend.handles2D()` 时只恢复后端 canvas，不再调用 `GuiGraphics.disableScissor()`。
- `DrawContext.clearClipStack()` 在 Skiko backend active 时只清理本地 clip 记录并委托 `backend.clearClipStack()`，不再 pop MC scissor。
- legacy 后端路径保持原语义：只有非 Skiko backend 的 clip 才调用 `GuiGraphics.disableScissor()`。

### 修改文件

| 文件 | 改动 |
|---|---|
| `src/main/java/shit/zen/render/DrawContext.java` | 区分 Skiko clip 与 MC scissor，修复 `restore()` / `clearClipStack()` 的 scissor underflow |

### 回归测试

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- `git diff --check`：PASS，仅 CRLF 工作区提示。
- 完整 PanelClickGui / HUD 字体恢复 / backend 不 fallback 验收：WAITING_USER_PASS。

## 2026-06-08：LieDetector 贴图绘制触发 NVIDIA OpenGL native crash

### 现象

- 用户反馈客户端崩端。
- `run/logs/latest.log` 没有 Java 异常堆栈，最后仍显示 `configured=SKIKO active=SKIKO`。
- `run/hs_err_pid34504.log` 记录 `EXCEPTION_ACCESS_VIOLATION (0xc0000005)`，问题帧为 `C [nvoglv64.dll+0xc2d183]`。

### 证据

| 证据 | 说明 |
|---|---|
| `run/hs_err_pid34504.log` | 崩溃线程是 `Render thread`，native frame 落在 NVIDIA OpenGL driver |
| `org.lwjgl.opengl.GL11C.nglDrawElements` | 崩溃发生在 MC/LWJGL draw call 内，不是 Java catchable exception |
| `shit.zen.utils.render.RenderUtil.drawTexture(int, PoseStack, ...)` | Java 栈显示 legacy texture draw 进入 `BufferUploader.drawWithShader` |
| `shit.zen.hud.LieDetector.onGlRender(...)` | 首个业务入口是 `GlRenderEvent` 下的 LieDetector HUD 贴图绘制 |

### 根因分析

- `GameRendererPatch.onRender(...)` 在 Skiko `Renderer.render(...)` 帧内派发 `GlRenderEvent`，而 `GlRenderEvent` 语义上仍是 legacy GL/MC BufferBuilder 绘制区。
- `RenderUtil.drawTexture(int, ...)` 虽然有局部 `beforeExternalGlDraw()` / `afterExternalGlDraw()` 包裹，但 `SkikoBackend` 没有外部 GL 嵌套深度，多个 legacy GL 组件嵌套进入时会重复 flush/reset，容易污染后续 MC buffer pipeline。
- 从 Skia GPU canvas 切回 MC `BufferUploader.drawWithShader` 前没有重置 `BufferUploader`，在 NVIDIA driver 中表现为 `glDrawElements` 原生访问冲突。

### 最终修复

- `GameRendererPatch` 将整段 `GlRenderEvent` 派发包在 `DrawContext.beforeExternalGlDraw()` / `afterExternalGlDraw()` 内，让该事件保持 legacy GL 兼容边界。
- `GameRendererPatch` 将 `graphics.pose().pushPose()` / `popPose()` 改为 `try/finally` 收口，避免事件异常后 PoseStack 泄漏。
- `SkikoBackend` 增加 `externalGlDepth`，支持外部 GL 边界嵌套；内层调用不再重复重置 Skia/GL 状态。
- `SkikoBackend.beforeExternalGlDraw()` 和 `afterExternalGlDraw()` 在真实进入/退出外部 GL 边界时调用 `BufferUploader.reset()`，避免 Skia 与 MC BufferBuilder/VAO 状态交叉污染。

### 修改文件

| 文件 | 改动 |
|---|---|
| `src/main/java/shit/zen/patch/GameRendererPatch.java` | `GlRenderEvent` 整体作为 legacy GL 段派发 |
| `src/main/java/shit/zen/render/backend/SkikoBackend.java` | 增加外部 GL 嵌套深度和 `BufferUploader.reset()` 边界清理 |

### 回归测试

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- `git diff --check`：PASS，仅 CRLF 工作区提示。
- 完整游戏内 LieDetector / HUD / ClickGUI 验收：WAITING_USER_PASS。

## 2026-06-08：ClickGUI / 毛玻璃 / stencil / 字体问题

### 现象

- PanelClickGui 中 panel 下方字体未渲染。
- legacy/old ClickGUI 背景缺少毛玻璃，只有半透明矩形，和原版 Zen 视觉差异过大。
- new ClickGUI 中模块选项卡没有被正确截断，stencil mask 泄漏成大量白色圆角矩形。
- Watermark/DynamicIsland 曾出现圆角背景间歇消失，日志中重复出现 `Watermark.onRender2D InvocationTargetException`。

### 根因判断

- `RenderUtil.drawBlurredRect(...)` 在 Skiko active 时被 0004 初版安全降级为半透明圆角背景，未实现背景 framebuffer 采样 blur。
- `CategoryPanel` / `SettingsPanel` 使用 `StencilHelper.beginWrite(false)` 后绘制白色 mask；Skia 绕过 GL `colorMask(false)` 和 stencil state，导致 mask 被真实绘制到屏幕。
- `PanelClickGui`、legacy/new ClickGUI 大量字体来自 `FontStore.*.drawString(...)`，最终进入 `CustomFont` 的 GL glyph atlas 路径，没有经过 Skiko 帧内的 `beforeExternalGlDraw()` / `afterExternalGlDraw()` 状态保护。

### 最终修复

- 0004 改为实现纯 Skia framebuffer snapshot/backdrop blur，`RenderUtil.drawBlurredRect(...)` 在 Skiko active 时调用后端 backdrop blur，不再作为正常路径半透明降级。
- `SkikoBackend.begin(...)` 每帧捕获当前 Skia surface snapshot，`end(...)` 关闭 snapshot，避免 resize 后旧图和资源泄漏。
- `SkikoEffects.drawBackdropBlurredRect(...)` 负责 rounded clip、GUI scale 到物理像素的采样换算、`ImageFilter.makeBlur(...)` 和 tint/opacity 叠加。
- `StencilHelper` 暴露 stencil active 状态，并在 stencil write/read 生命周期中负责 Skiko 外部 GL 段进入/退出；`Renderer.canUseSkiko2D(...)` 在 stencil 段内拒绝 Skia 2D，保持旧 GL stencil 语义。
- `CustomFont.drawStringRGBFull(...)` 集中包裹 Skiko 外部 GL 绘制保护；如果已经处于 stencil 段内，则由 `StencilHelper` 统一持有外部 GL 边界，避免重复恢复状态破坏 stencil。
- DLL 注入和 Skiko native runtime packaging 后移到 0005。

### 修改文件

| 文件 | 改动 |
|---|---|
| `src/main/java/shit/zen/render/backend/RenderBackend.java` | 增加 `drawBackdropBlurredRect(...)` 可选能力 |
| `src/main/java/shit/zen/render/backend/SkikoBackend.java` | 增加每帧 backdrop snapshot、debug summary 统计和 Skia backdrop blur 调度 |
| `src/main/java/shit/zen/render/backend/SkikoEffects.java` | 增加纯 Skia backdrop blur 绘制 |
| `src/main/java/shit/zen/utils/render/RenderUtil.java` | `drawBlurredRect(...)` 在 Skiko active 时改走后端 backdrop blur |
| `src/main/java/shit/zen/render/StencilHelper.java` | 增加 stencil active 状态和外部 GL 段边界 |
| `src/main/java/shit/zen/render/Renderer.java` | stencil active 时禁用 Skia 2D 接管 |
| `src/main/java/shit/zen/render/CustomFont.java` | 保护 `FontStore`/`CustomFont` 直接 GL 字体绘制 |

### 回归测试

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- 完整游戏内视觉验收：WAITING_USER_PASS。

## 2026-06-08：主菜单小界面 / 红色背景 / resize 后无法操作

### 现象

- 游戏能启动且有声音，但主菜单只显示在左下角的小区域。
- 窗口可以放大，但实际渲染区域仍停留在小尺寸，周围出现大面积红色背景。
- 鼠标操作和画面区域不匹配，表现为无法正常操作。

### 根因分析

- 现象尺寸约为物理窗口的 GUI scale 缩小比例，符合 GL viewport 或 scissor 被留在 GUI 逻辑尺寸的表现。
- `GameRendererPatch.onRender(...)` 只有 `ZenClient.isReady()` 后才会调用 OpenZen 2D 渲染；主菜单没有 player，`Renderer.resetRenderState()` 的帧尾收口不一定执行。
- 因此只在 OpenZen 2D 渲染结束时恢复 viewport 不能覆盖主菜单/启动阶段。`Minecraft.resizeDisplay()` 尾部也必须恢复当前窗口物理 framebuffer bounds。

### 最终修复

- `Renderer.resetRenderState()` 增加当前窗口物理 viewport 恢复，并禁用 scissor/stencil。
- 新增 `Renderer.resetWindowFramebufferBounds(...)`，集中恢复 `glViewport(0, 0, windowWidth, windowHeight)` 并禁用 scissor。
- `MinecraftPatch.onResizeDisplay(...)` 在更新 GUI scale 后调用 `Renderer.resetWindowFramebufferBounds(...)`，覆盖主菜单和窗口 resize 路径。

### 修改文件

| 文件 | 改动 |
|---|---|
| `src/main/java/shit/zen/render/Renderer.java` | 增加窗口 framebuffer bounds 恢复，帧尾清理 viewport/scissor/stencil |
| `src/main/java/shit/zen/patch/MinecraftPatch.java` | resize tail 里恢复物理 viewport/scissor |

### 回归测试

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- 完整主菜单/resize 视觉验收：WAITING_USER_PASS。

## 2026-06-08：Panel 字体缺失 / 打开 GUI 后原 UI 显示 / VAO 状态污染

### 现象

- 用户复测反馈：old ClickGUI 模式正常。
- PanelClickGui 仍只显示毛玻璃和圆角背景，panel 下方字体未渲染。
- 接着打开 GUI 后，客户端原有 UI/HUD 全部显示，无法继续做 ClickGUI 视觉测试。
- `run/logs/latest.log` 中重复出现 `Watermark.onRender2D InvocationTargetException`，并伴随 OpenGL debug message：`GL_INVALID_OPERATION error generated. Array object is not active.`。

### 根因分析

- `SkikoBackend.beforeExternalGlDraw()` 会先 flush Skia，再恢复进入 Skiko 帧前捕获的 GL 状态；该状态在当前环境下可能包含 `GL_VERTEX_ARRAY_BINDING = 0`。
- `CustomFont`、`RoundedRectShader`、部分 legacy texture/stencil 路径会在 Skiko 帧内继续使用 `BufferBuilder` / `Tesselator` 做受控 GL 混画。OpenGL core profile 下 VAO 为 0 时，这类绘制会触发 `Array object is not active`，导致 Watermark 和后续 UI 绘制中断。
- 0004 初版只让 `Renderer.canUseSkiko2D(...)` 在 stencil active 时返回 false，但 `Renderer.render(...)` 的实际后端选择仍可能创建 Skiko `DrawContext`，导致 stencil 内容仍绕过 GL stencil 语义。
- `Renderer.render(guiGraphics, ...)` 没有使用当前 `guiGraphics.pose()` 作为初始 pose，PanelClickGui 外层开场缩放只作用于 `RenderUtil.renderWithPose(...)` 形状路径，不作用于部分文字路径，造成 Panel 字/图形对齐风险。

### 最终修复

- `SkikoBackend` 增加 `externalGlVao`，在每次 `beforeExternalGlDraw()` 恢复捕获状态后，如果当前 VAO 为 0，则生成并绑定一个受控 fallback VAO。
- `Renderer.getEffectiveBackend(PoseStack)` 在 stencil active 或当前 pose 不适合 Skiko 2D 时返回 `LegacyGlBackend`，让 `Renderer.render(...)` 的实际调度和 `Renderer.canUseSkiko2D(...)` 保持一致。
- `Renderer.render(...)` 在已有 Skiko `currentCanvas` 内需要临时走 legacy 时，使用 `beforeExternalGlDraw()` / `afterExternalGlDraw()` 包住该段；如果 stencil 已经持有外部 GL 段，则不重复包裹。
- `Renderer.render(guiGraphics, ...)` 使用当前 `guiGraphics.pose()` 作为初始 pose，并在 Skiko backend 上 `pushExternalPose(...)`，保证 PanelClickGui 外层缩放/平移同时作用于文字和图形。
- `SettingsPanel` 和 new ClickGUI `CategoryPanel` 的 `StencilHelper.beginWrite/beginRead/end` 改为 `try/finally` 收口，防止子元素异常泄漏 stencil/color mask/外部 GL 状态。

### 修改文件

| 文件 | 改动 |
|---|---|
| `src/main/java/shit/zen/render/backend/SkikoBackend.java` | 增加 Skiko 帧内 legacy GL 混画 fallback VAO |
| `src/main/java/shit/zen/render/Renderer.java` | 让实际后端调度尊重 stencil/pose 边界，并接入 `guiGraphics.pose()` 初始变换 |
| `src/main/java/shit/zen/gui/panel/SettingsPanel.java` | stencil 生命周期改为 `try/finally` |
| `src/main/java/shit/zen/gui/newclickgui/CategoryPanel.java` | header/body stencil 生命周期改为 `try/finally` |

### 回归测试

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- `git diff --check`：PASS，仅 CRLF 工作区提示。
- 完整 PanelClickGui / Watermark / new ClickGUI 游戏内视觉验收：WAITING_USER_PASS。
