# Phase 0004: Skiko 2D 视觉算法与 Texture Interop

## 状态

partial

## 本阶段目标

承接 0003 的 Skiko 2D 后端骨架和状态隔离 PASS 结果，继续迁移 2D UI/HUD 的视觉算法与 texture interop。重点处理 shadow、text glow、元素自身 blur、纯 Skia 背景毛玻璃 blur、普通 `ResourceLocation` 解码、玩家头像 Skia 绘制尝试，以及 fallback/兼容边界。

## 实际完成内容

- 新增 `SkikoEffects`，集中封装 Skia shadow、text glow 和元素自身 blur 的实现。
- 新增 `SkikoTextures`，通过 Minecraft resource manager 解码普通 `ResourceLocation`，并维护 Skia `Image` cache / miss 统计。
- `SkikoBackend` 复用 `SkikoEffects` / `SkikoTextures`，并在 `debugSummary()` 中暴露 image cache/miss 数。
- `SkikoBackend` 支持玩家头像 Skia 绘制尝试：从 skin `ResourceLocation` 读取原图，按 base face 和 hat UV 裁剪绘制；失败时由上层 fallback legacy GL。
- `TextGlow.drawGlowText(...)` 在 Skiko active 时走后端 glow 绘制，legacy 下保持旧的普通文字行为。
- `DrawContext.drawTexture(...)` 在 Skiko 无法处理 GL id 或无法解码资源时自动包裹 legacy GL fallback，避免贴图静默消失。
- `RenderUtil.drawTexture(ResourceLocation, ...)` 在 Skiko 可解码资源时走 Skia，不能解码时继续走原 GL texture id 路径。
- `RenderUtil.drawBlurredRect(...)` 在 Skiko active 时改为走纯 Skia framebuffer snapshot/backdrop blur，不再安全降级为半透明圆角背景。
- `StencilHelper` / `Renderer.canUseSkiko2D(...)` 需要识别 stencil write/read 段，避免 Skia 绕过 GL color mask/stencil 导致 mask 泄漏。
- `CustomFont.drawStringRGBFull(...)` 需要集中包裹 Skiko 外部 GL 绘制保护，修复 `FontStore` 直接 GL 字体路径。
- `SkikoBackend.begin(...)` 每帧捕获 backdrop snapshot，`SkikoBackend.end(...)` 释放 snapshot，降低 resize/异常后的旧图和资源泄漏风险。
- `Renderer.resetRenderState()` 和 `MinecraftPatch.onResizeDisplay(...)` 恢复当前窗口物理 viewport/scissor，修复主菜单小界面和红色背景 resize 污染。
- `SkikoBackend.toRRect(...)` 对圆角半径按元素最短边钳制，降低动画小尺寸和复杂四角半径下的异常几何风险。
- `SkikoBackend.beforeExternalGlDraw()` 在恢复捕获 GL 状态后绑定受控 fallback VAO，修复 Skiko 帧内 legacy GL 混画触发 `Array object is not active` 的状态污染。
- `Renderer.render(...)` 的实际后端选择现在尊重 stencil active / pose 边界；`Renderer.render(guiGraphics, ...)` 使用当前 `guiGraphics.pose()` 作为初始 pose，修复 PanelClickGui 字体和图形变换不一致风险。
- `SettingsPanel` 和 new ClickGUI `CategoryPanel` 的 stencil 生命周期改为 `try/finally` 收口，降低异常后 stencil/color mask 泄漏风险。

## 改动文件

| 文件 | 改动说明 |
|---|---|
| `src/main/java/shit/zen/render/backend/SkikoEffects.java` | 新增 Skia effects helper，封装 blurred rrect、自身 blur layer 和 glow 相关绘制 |
| `src/main/java/shit/zen/render/backend/SkikoTextures.java` | 新增 Skia texture helper，负责 `ResourceLocation` 解码、cache 和 miss 统计 |
| `src/main/java/shit/zen/render/backend/SkikoBackend.java` | 接入 effects/texture helper，新增 text glow、玩家头像、texture cache debug、圆角半径钳制和 legacy GL fallback VAO |
| `src/main/java/shit/zen/render/backend/RenderBackend.java` | 增加 `drawGlowText(...)`、`canDrawResourceTexture(...)`、`drawPlayerHead(...)` 能力接口 |
| `src/main/java/shit/zen/render/DrawContext.java` | `drawTexture(...)` 在 Skiko 无法绘制时包裹 legacy GL fallback |
| `src/main/java/shit/zen/render/GlHelper.java` | 玩家头像优先尝试后端 Skia 绘制，失败时保持 legacy rounded GL shader |
| `src/main/java/shit/zen/render/TextGlow.java` | Skiko active 时启用真正 glow 绘制，legacy 保持旧行为 |
| `src/main/java/shit/zen/render/Renderer.java` | stencil active 时拒绝 Skia 2D 接管，保留旧 GL stencil 语义；实际后端调度接入 stencil/pose 边界和 `guiGraphics.pose()` 初始变换 |
| `src/main/java/shit/zen/patch/MinecraftPatch.java` | resize tail 恢复物理窗口 framebuffer bounds |
| `src/main/java/shit/zen/render/StencilHelper.java` | 增加 stencil active 状态，并在 stencil 生命周期中持有外部 GL 段 |
| `src/main/java/shit/zen/render/CustomFont.java` | 集中保护 `FontStore` 直接 GL 字体绘制 |
| `src/main/java/shit/zen/utils/render/RenderUtil.java` | `ResourceLocation` 贴图接入 Skia 可解码路径，背景 blur 在 Skiko active 时走纯 Skia backdrop blur |
| `src/main/java/shit/zen/gui/panel/SettingsPanel.java` | stencil 生命周期 `try/finally` 收口，避免设置区异常泄漏 GL 状态 |
| `src/main/java/shit/zen/gui/newclickgui/CategoryPanel.java` | header/body stencil 生命周期 `try/finally` 收口 |
| `.columbina/phase/0004_skiko-2d-effects-texture-interop/project_plan.md` | 记录本阶段计划和验收边界 |
| `.columbina/phase/0004_skiko-2d-effects-texture-interop/test.md` | 记录构建、dry-run 和等待人工视觉验收状态 |
| `.columbina/phase/0004_skiko-2d-effects-texture-interop/debug.md` | 记录 ClickGUI blur/stencil/font bug 根因、修复和测试 |

## 新增/修改的核心类

| 类/模块 | 作用 |
|---|---|
| `SkikoEffects` | Skiko 后端内部 effects 算法集中点 |
| `SkikoTextures` | Skiko 后端内部 texture 解码/cache 集中点 |
| `SkikoBackend` | 继续作为唯一 Skia 2D backend，实现 effects、texture、player head 和 debug summary |
| `RenderBackend` | 暴露后端可选能力，legacy 默认不实现 |
| `StencilHelper` | 旧 GL stencil 生命周期所有者，负责 Skiko 外部 GL 段边界 |
| `CustomFont` | `FontStore` 直接 GL 字体绘制的集中保护点 |

## 关键实现决策

- 背景毛玻璃 blur 本阶段使用纯 Skia framebuffer snapshot/backdrop blur；不走旧 GL blur shader，也不再半透明降级。
- stencil 和 `FontStore`/`CustomFont` 仍是受控 legacy GL 兼容边界；这是局部兼容，不代表 Skiko backend 全局 fallback。
- `ResourceLocation` 只有在 Skiko 能通过 resource manager 解码时才走 Skia；否则继续 legacy GL，避免空白贴图。
- 玩家头像只在 skin `ResourceLocation` 可读取原始图片时走 Skia；下载 skin / GL-only texture 继续走旧 rounded GL shader。
- `DrawContext.drawTexture(...)` 在 backend active 但不可处理 texture 时，必须调用 `beforeExternalGlDraw()` / `afterExternalGlDraw()` 包住 legacy 绘制。
- `TextGlow` 只在 Skiko active 时改变为真正 glow；legacy 下保持 0003 前的视觉语义。

## 复用的已有结构

- 继续复用 `Renderer.render(...)` / `Renderer.renderWithPose(...)` 的后端生命周期和状态隔离。
- 继续复用 `DrawContext`、`Paint`、`Texture`、`Rectangle`、`RoundedRectangle` 的上层数据结构。
- 继续复用 `GlHelper.drawPlayerHeadRounded(...)` 作为玩家头像统一入口。
- 继续保留 `RoundedRectShader`、`BlurRenderer`、`RenderUtil` legacy 代码作为 fallback。

## 对后续 AI 的提醒

- 背景 blur 目标是 Skia framebuffer snapshot/backdrop blur；如果出现错位，优先检查 GUI scale 到物理像素的换算。
- 不要在 `StencilHelper.beginWrite/beginRead/end` 段内强行走 Skia 形状绘制，否则白色 mask 会再次泄漏。
- 不要在 GUI/HUD 层散落 Skia texture 解码逻辑；继续扩展 `SkikoTextures`。
- 不要绕过 `DrawContext.beforeExternalGlDraw()` / `afterExternalGlDraw()` 做 Skiko 帧内 legacy GL 混画。
- 若玩家头像不走 Skia，先确认 skin 是否能从 resource manager 读到原始 bytes；GL-only skin fallback 是预期行为。
- 3D/world 渲染仍不迁移到 Skiko。

## 未完成内容

- DLL 注入路径中的 Skiko native runtime 打包未处理，后置到 0005。
- GUI scale 1x/2x/3x、resize、clip 泄漏和玩家头像换肤 cache 失效仍建议后续做更系统的人工回归。

## 测试状态

PASS
