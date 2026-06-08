# Debug Record: Phase 0005

## Debug 类型

phase

## 关联问题

0004 后剩余 2D legacy GL 兼容边界主要是 `FontStore` / `CustomFont` 字体绘制和 `StencilHelper.beginWrite/beginRead` mask 裁剪。

## 症状

- `FontStore.*` 在 legacy/new ClickGUI 中大量直接调用 `CustomFont`，此前仍需要 Skiko 帧内外部 GL 保护。
- `SettingsPanel` 和 new ClickGUI `CategoryPanel` 使用 `StencilHelper` 的 GL stencil mask，Skiko 绕过 GL stencil 时曾导致 mask 泄漏。

## 根因分析

- `FontStore` 返回 `CustomFont`，原实现最终通过 `GlyphPage` 生成 AWT glyph atlas，再用 GL quad 绘制；这不是 Skiko 字体路径。
- `StencilHelper.beginWrite/beginRead` 是 GL color mask / stencil state 语义，不能让 Skia canvas 直接参与 mask 写入。
- 0004 已用外部 GL guard 保证稳定，本阶段进一步把主路径迁到 Skiko，减少 mixed GL。

## 最终修复

- 增加 `SkikoFonts`，由 `SkikoBackend.drawCustomFontText(...)` 调用，支持 `CustomFont` 的 Skiko 字体绘制。
- `CustomFont` 保留旧 public API，Skiko active 时优先走 Skiko，失败时局部 fallback 旧 GL glyph atlas。
- `CustomFont` 测宽/测高改为 AWT bounds 计算，避免测量阶段触发 `GlyphPage` 上传 GL texture。
- `SettingsPanel` 和 `CategoryPanel` 的 Skiko active 分支改用 `DrawContext.clipRoundedRect(...)`，只在 non-Skiko fallback 分支保留 `StencilHelper`。

## 修改文件

| 文件 | 改动 |
|---|---|
| `src/main/java/shit/zen/render/backend/SkikoFonts.java` | 新增 Skiko `CustomFont` 绘制 helper |
| `src/main/java/shit/zen/render/backend/RenderBackend.java` | 增加 `drawCustomFontText(...)` |
| `src/main/java/shit/zen/render/backend/SkikoBackend.java` | 接入 `SkikoFonts` |
| `src/main/java/shit/zen/render/CustomFont.java` | Skiko 优先绘制、资源名、无 GL 测宽/测高 |
| `src/main/java/shit/zen/render/FontStore.java` | 传递字体资源名 |
| `src/main/java/shit/zen/render/Fonts.java` | 传递字体资源名 |
| `src/main/java/shit/zen/gui/panel/SettingsPanel.java` | Skiko clip 替代 stencil |
| `src/main/java/shit/zen/gui/newclickgui/CategoryPanel.java` | Skiko clip 替代 stencil |

## 回归测试

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO --dry-run`：PASS。
- `git diff --check`：PASS，仅 CRLF 工作区提示。
- 完整游戏内视觉验收：PASS，用户确认全部正常。

## 后续提醒

- 若字体出现偏上/偏下，优先调整 `SkikoFonts` 的 legacy top/baseline 换算，不要在各个 GUI 组件里散改坐标。
- 若 new ClickGUI 裁剪错位，优先检查 `CategoryPanel` 中外层 `pushExternalPose(poseStack)` 与内部 identity `PoseStack` 的边界。
- `StencilHelper` 仍保留给 fallback；不要直接删除。
