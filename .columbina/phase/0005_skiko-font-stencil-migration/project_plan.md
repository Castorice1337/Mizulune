# Phase 0005: Skiko Font 与 StencilHelper 迁移计划

## Summary

- 目标：在 0004 `active=SKIKO` 且 2D effects/texture PASS 的基础上，继续减少剩余 2D legacy GL 兼容边界。
- 本阶段只做两件事：`FontStore` / `CustomFont` 字体绘制迁移到 Skiko，以及当前实际使用的 `StencilHelper.beginWrite/beginRead` 场景迁移到 Skia clip。
- 视觉目标：font 渲染与原版 Zen 基本一致，允许小幅抗锯齿差异，但不能出现明显 baseline、图标、居中、shadow 或测宽偏移。
- 阶段结束时必须例行检查渲染系统，确认是否还有需要迁移的 2D legacy GL 路径，并记录剩余边界。

## Key Changes

- Font 迁移：
  - 保留 `CustomFont` 和 `FontStore` public API，不要求上层 ClickGUI/HUD 重构。
  - `CustomFont.drawString...(...)` 在 Skiko active 时优先走 Skiko 字体路径，失败或 backend inactive 时局部 fallback 旧 GL glyph atlas。
  - 增加后端能力接口用于绘制/测量 `CustomFont`，Skiko 实现资源字体、`§` 颜色码、newline、letterSpacing、shadow、centered/rainbow 兼容语义。
  - 字体资源名从 `FontStore` / `Fonts.getCustomFont(...)` 传入 `CustomFont`，资源名缺失时保留旧 GL fallback。

- StencilHelper 迁移：
  - `SettingsPanel` 用 `DrawContext.clipRect/clipRoundedRect` 替换 GL stencil mask。
  - new ClickGUI `CategoryPanel` header/body 用 Skia clip 裁剪，保持顶部渐变线和模块滚动截断。
  - `StencilHelper` 类暂不删除，保留作为 legacy fallback 和未调用的 `applyStencil(...)` 兼容能力。

- 阶段结束例行检查：
  - 搜索 `RenderSystem` / `GL11` / `BufferBuilder` / `Tesselator` / `StencilHelper` / `CustomFont` / `FontStore` 的剩余 2D 调用。
  - 将剩余项分成：已迁 Skiko、受控 legacy fallback、3D/world 渲染、后续 phase 候选。

## Test Plan

- 构建：`.\gradlew.bat jar`
- dry-run：`.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO --dry-run`
- 手测：`.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO`
- 字体验收：
  - PanelClickGui、old ClickGUI、new ClickGUI 字体全部显示。
  - OpenSans、Axiforma、Material Icons、Zen icon、中文、`§` 颜色码正常。
  - centered、shadow、rainbow、icon 字符与原版视觉基本一致。
- clip/stencil 验收：
  - new ClickGUI 模块列表正确截断，无白色 mask、无额外黑色外圈污染。
  - SettingsPanel 滚动内容不泄漏，切换模块和动画不污染后续 UI。
- 回归：
  - Watermark、DynamicIsland、Hotkeys、HUD 文本正常。
  - MC 原版物品栏和菜单无半透明几何污染。
  - `run/logs/latest.log` 不刷 `Render backend SKIKO failed`、`Scissor stack underflow`、`Array object is not active`。

## Assumptions

- 本阶段不处理 DLL/native runtime packaging。
- 本阶段不删除 legacy GL font/stencil 代码，只让 Skiko active 下主路径优先走 Skia。
- 如果 Skiko 字体绘制失败，必须局部 fallback，不允许触发全局 backend fallback。
- 字体视觉以“基本一致”为目标，不追求像素级一致。
