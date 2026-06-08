# Phase 0005: Skiko Font 与 StencilHelper 迁移

## 状态

partial

## 本阶段目标

迁移 0004 后剩余的两个主要 2D legacy GL 兼容边界：`FontStore` / `CustomFont` 字体绘制，以及当前实际使用的 `StencilHelper.beginWrite/beginRead` mask 场景。

## 实际完成内容

- 新增 `SkikoFonts`，集中处理 `CustomFont` 在 Skiko active 下的资源字体加载、cache、`§` 颜色码分段、newline、letterSpacing 和旧式 top/baseline 对齐。
- `RenderBackend` 增加 `drawCustomFontText(...)` 可选能力，`SkikoBackend` 实现该能力并在绘制前应用传入 `PoseStack`。
- `CustomFont.drawStringRGBFull(...)` 改为 Skiko active 时优先走 Skiko 字体绘制；资源名缺失、backend inactive、stencil active 或 Skiko 绘制失败时局部 fallback 旧 GL glyph atlas。
- `CustomFont.getStringWidth(...)` / `getStringHeight(...)` 改为使用与旧 atlas 同源的 AWT bounds 测量，避免测宽阶段上传 GL glyph texture，同时尽量保留旧布局数值。
- `FontStore` / `Fonts.getCustomFont(...)` 构造 `CustomFont` 时传入字体资源名，使 `FontStore.*` 旧入口可被 Skiko 字体路径接管。
- `SettingsPanel` 在 Skiko 可用时使用 `DrawContext.clipRoundedRect(...)` 替代 GL stencil，legacy 或不适合 Skiko 时保留旧 `StencilHelper` fallback。
- new ClickGUI `CategoryPanel` 在 Skiko 可用时使用 Skia clip 裁剪 header 渐变和 body 模块列表；body 内部使用 identity `PoseStack` 避免子元素重复应用 panel 变换。
- 完成阶段结束渲染系统例行检查，确认剩余 `StencilHelper.beginWrite/beginRead` 只在 fallback 分支；`FontStore` 调用保留但会优先进入 Skiko 字体路径。
- **品牌迁移 (附加)**：将 OpenZen / Zen 品牌名迁移为 Mizulune Client / Mizulune / MZL，更新了客户端常数、UI 显示、`assets/mizulune` 目录结构和 Native Loader 的界面文本。HUD 开屏动画更新为动态拼接 "M" + "izulune"。修复了因品牌字符清空导致的聊天框模块提示/命令输出 `[]` 为 `[Mizulune]` 前缀。

## 改动文件

| 文件 | 改动说明 |
|---|---|
| `src/main/java/shit/zen/render/backend/SkikoFonts.java` | 新增 `CustomFont` 的 Skiko 字体绘制 helper |
| `src/main/java/shit/zen/render/backend/RenderBackend.java` | 新增 `drawCustomFontText(...)` 后端能力 |
| `src/main/java/shit/zen/render/backend/SkikoBackend.java` | 接入 `SkikoFonts`，实现 `CustomFont` Skiko 绘制并应用 `PoseStack` |
| `src/main/java/shit/zen/render/CustomFont.java` | 增加资源名、Skiko 优先绘制、无 GL 测宽/测高和局部 fallback |
| `src/main/java/shit/zen/render/FontStore.java` | 加载字体时传递资源名 |
| `src/main/java/shit/zen/render/Fonts.java` | `Fonts.getCustomFont(...)` 创建 `CustomFont` 时传递资源名 |
| `src/main/java/shit/zen/gui/panel/SettingsPanel.java` | Skiko active 时用 Skia clip 替代 stencil mask |
| `src/main/java/shit/zen/gui/newclickgui/CategoryPanel.java` | Skiko active 时用 Skia clip 替代 header/body stencil mask |

## 新增/修改的核心类

| 类/模块 | 作用 |
|---|---|
| `SkikoFonts` | `CustomFont` 的 Skiko 字体资源、cache 和绘制逻辑 |
| `CustomFont` | 旧 `FontStore` API 的 Skiko 接管点和 legacy fallback 点 |
| `RenderBackend` | 暴露 `CustomFont` 后端绘制能力 |
| `CategoryPanel` / `SettingsPanel` | 当前实际 stencil 调用点的 Skia clip 迁移位置 |

## 关键实现决策

- 不改 GUI/HUD 上层调用，仍通过 `FontStore.*.drawString(...)` / `CustomFont` public API 使用字体。
- `CustomFont` Skiko 绘制失败只局部 fallback 旧 GL，不触发 `Renderer` 全局 backend fallback。
- `CustomFont` 测宽/测高使用 AWT bounds，不再依赖 `GlyphPage.getGlyph(...)` 上传 atlas。
- `CategoryPanel` 的 Skiko body clip 内部使用 identity `PoseStack` 渲染子元素，外层由 Skia canvas 应用 panel 变换，避免矩阵重复。
- `StencilHelper` 暂不删除，保留给 legacy fallback 和未调用的 `applyStencil(...)`。

## 阶段结束渲染系统例行检查

- `StencilHelper.beginWrite/beginRead` 剩余调用：仅在 `SettingsPanel` / `CategoryPanel` 的 non-Skiko fallback 分支。
- `FontStore.*` 剩余调用：仍大量存在于 legacy/new ClickGUI，但入口已通过 `CustomFont` 优先走 Skiko 字体。
- `GlyphPage` / `CustomFont.drawStringRGBFullLegacy(...)`：保留为字体资源缺失、stencil active 或 legacy backend fallback。
- `RenderUtil` 中的 GL/BufferBuilder：普通 2D wrapper 已有 Skiko 分流；剩余主要是 legacy fallback、GL-only texture id、旧 shader fallback 和 3D/world 绘制。
- `ChestESP` / `ESP` / `Projectiles` / `XRay` 等 world 渲染：仍按计划保留 OpenGL。

## 边界

- 保留 `Renderer` / `DrawContext` / `FontStore` / `CustomFont` / `RenderUtil` 上层调用形态。
- 不迁移 3D/world 渲染。
- 不处理 DLL/native runtime packaging。
- 阶段结束时对渲染系统做例行检查，记录仍需迁移或保留的渲染路径。

## 未完成内容

- DLL/native runtime packaging 仍后置。

## 测试状态

PASS
