# Phase 0004：Skiko 2D 视觉算法与 Texture Interop

## Summary

- 目标：在 0003 已确认 `active=SKIKO` 且状态隔离 PASS 的基础上，推进第二阶段 Skiko 2D 质量迁移。
- 重点：重写/稳定 shadow、text glow、元素自身 blur、背景毛玻璃 blur、clip、圆角/渐变细节，并补普通 `ResourceLocation` 与玩家头像 texture interop。
- 不做：不迁移 3D 世界空间渲染，不处理 DLL 注入 Skiko runtime，不删除 legacy OpenGL 2D fallback。

## Key Changes

- 建立后端内部辅助层：
  - `SkikoEffects` 集中处理 blur、shadow、glow。
  - `SkikoTextures` 集中处理 `ResourceLocation` 解码、Skia `Image` cache 和 miss 统计。
- 强化 `SkikoBackend` 的 2D 算法实现：
  - shadow 使用 Skia blur mask 绘制扩展 shape shadow，保留旧 `drawShadow` 输入语义。
  - text glow 在 Skiko 下先绘制 blur layer，再绘制正文；legacy 下保持旧行为。
  - 元素自身 blur 使用 Skia offscreen layer/image filter，不采样背景 framebuffer。
  - clip 继续走 Skia rect/rrect clip，并要求 save/restore 配对，不泄漏到后续 UI。
  - 圆角半径按元素最短边钳制，避免小尺寸动画或复杂四角半径产生异常几何。
- texture interop 分两层：
  - 普通 PNG / `ResourceLocation` 通过 Minecraft resource manager 读取 bytes，Skia `Image` 解码并缓存。
  - 玩家头像优先从 skin `ResourceLocation` 解码 base face + hat UV；读不到原始资源或仅有 GL texture id 时保留 legacy GL 混画 fallback。
- 背景毛玻璃 blur 使用纯 Skia framebuffer snapshot/backdrop blur：
  - `SkikoBackend.begin(...)` 在每帧 Skia 绘制前捕获当前 framebuffer 的 Skia snapshot，作为本帧 backdrop 来源。
  - `RenderUtil.drawBlurredRect(...)` 在 Skiko active 时调用后端 backdrop blur，不再降级为半透明圆角背景。
  - backdrop blur 按 GUI 坐标接收目标区域，内部按 GUI scale 换算物理像素采样区域，避免高 DPI/GUI scale 下错位。
  - stencil 和 `FontStore`/`CustomFont` 的直接 GL 路径属于受控 legacy GL 兼容边界，不代表 Skiko backend fallback。
- 调试信息：
  - 0003 的临时 `RenderBackendProbe` 验证面板已完成使命，本阶段清理该测试类，不再通过 `openzenRenderProbe` 显示游戏内测试 UI。

## Test Plan

- 构建：运行 `.\gradlew.bat jar`。
- 开发运行：运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO`。
- 视觉验收：
  - Watermark / DynamicIsland：圆角背景不消失，文字、图标、shadow/glow 对齐旧视觉。
  - KeyBinds / ModuleList / PlayerList / TargetHUD：shadow、blur、clip、头像位置稳定。
  - PanelClickGui / SettingsPanel / SettingsPopup：滚动列表 clip 不泄漏，圆角裁剪不污染后续元素。
  - 普通 texture / cloud assets / 玩家头像：能 Skia 解码则走 Skiko，不能解码则明确 fallback legacy。
- 回归验收：
  - GUI scale 1x/2x/3x 和窗口 resize 后坐标不偏移。
  - 打开 MC 原版物品栏不出现半透明几何污染。
  - `run/logs/latest.log` 不刷 VAO/VBO/pointer 类 OpenGL error。
  - `run/logs/latest.log` 不刷 `Watermark.onRender2D InvocationTargetException`。
  - ChestESP、Projectiles 3D、XRay、Scaffold box 等 3D 路径不受影响。

## Assumptions

- 0004 是“第二阶段质量迁移”，不是重做 0003 的后端激活工作。
- 默认仍保留 `OPENGL_LEGACY` fallback；Skiko 失败时不能影响客户端可用性。
- 背景毛玻璃 blur 在 0004 实现；DLL native packaging 后置到 0005。
- 当前阶段只要求 Windows x64 开发运行路径稳定。
