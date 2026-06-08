# Test Record: Phase 0004

## 测试状态

PASS

## 测试目标

验证 Skiko 2D effects/texture interop 改动可以通过 Java 编译、Forge reobf 和 OpenZen obfuscation，并确认 `runClient0` 的 Skiko backend 任务链仍可配置。完整游戏内视觉验收等待用户运行客户端确认。

## 测试步骤

1. 运行 `.\gradlew.bat jar`。
2. 运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO --dry-run`。
3. 等待用户运行完整 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO` 做游戏内视觉验收。

## 期望结果

- `compileJava` 成功。
- `reobfJar` 成功。
- `obfuscateClasses` 成功并生成 mapping。
- `runClient0` dry-run 任务链包含 Skiko runtime 抽取和 client run 配置。
- 完整游戏内 Skiko active 后，Watermark / DynamicIsland / KeyBinds / PlayerList / TargetHUD / ClickGUI 无明显错位、clip 泄漏或 GL 状态污染。
- 普通 `ResourceLocation` 可解码时走 Skia，无法解码或 GL-only texture 明确 fallback legacy。

## 实际结果

- 首次沙箱内运行 `.\gradlew.bat jar` 时，Gradle wrapper 下载 Gradle 8.8 被网络限制拦截：`java.net.SocketException: Permission denied: getsockopt`。
- 放行后重新运行 `.\gradlew.bat jar`：PASS，`compileJava`、`reobfJar`、`obfuscateClasses` 成功，输出 `obfuscateJar: renamed 441 classes`。
- 沙箱内运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run` 同样因 wrapper 下载被网络限制拦截。
- 放行后重新运行 dry-run：PASS，任务图成功，`runClient0` / `runClient` 等任务被 dry-run 标记为 `SKIPPED`。
- `git diff --check`：PASS，仅有 CRLF 工作区提示，无 whitespace error。
- resize/viewport 修复后重新运行 `.\gradlew.bat jar`：PASS，`compileJava`、`reobfJar`、`obfuscateClasses` 成功。
- resize/viewport 修复后重新运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- 用户复测 resize/viewport 后：old ClickGUI 模式正常；PanelClickGui 仍无字体；再次打开 GUI 后客户端原有 UI/HUD 全部显示，无法继续测试。
- 针对 Panel 字体缺失 / stencil 调度 / VAO 状态污染修复后重新运行 `.\gradlew.bat jar`：PASS，`compileJava`、`reobfJar`、`obfuscateClasses` 成功。
- 针对本轮修复重新运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- 针对本轮修复重新运行 `git diff --check`：PASS，仅有 CRLF 工作区提示。
- 完整游戏内视觉验收：等待用户测试。
- 用户反馈客户端崩端；`run/hs_err_pid34504.log` 显示 native crash 落在 `nvoglv64.dll`，Java 栈为 `LieDetector.onGlRender(...) -> RenderUtil.drawTexture(int, ...) -> BufferUploader.drawWithShader`。
- 针对 `GlRenderEvent` legacy GL 边界和 Skiko 外部 GL 嵌套修复后重新运行 `.\gradlew.bat jar`：PASS。
- 针对 native crash 修复后重新运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- 针对 native crash 修复后重新运行 `git diff --check`：PASS，仅有 CRLF 工作区提示。
- 补充 `GameRendererPatch` 的 `PoseStack` `try/finally` 收口后再次运行 `.\gradlew.bat jar`：PASS。
- 补充 `PoseStack` 收口后再次运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- 完整 LieDetector / HUD / ClickGUI 游戏内崩溃回归验收：WAITING_USER_PASS。
- 用户复测反馈：Panel 模式仍无文字；再次打开 GUI 后客户端原有 UI 全部消失；UI 消失时 Hotkeys 圆角矩形仍能渲染但没有字。
- `run/logs/latest.log` 显示首个真实异常为 `Render backend SKIKO failed, falling back to legacy OpenGL`，原因是 `Scissor stack underflow`，栈落在 `DrawContext.restore(...) -> ModuleListPanel.renderModuleList(...) -> PanelClickGui.render(...)`。
- 修复 Skiko clip 与 MC scissor 恢复边界后重新运行 `.\gradlew.bat jar`：PASS。
- 修复 scissor underflow 后重新运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- 修复 scissor underflow 后重新运行 `git diff --check`：PASS，仅有 CRLF 工作区提示。
- 完整 PanelClickGui 文字恢复、HUD 字体恢复、Skiko backend 不 fallback 验收：WAITING_USER_PASS。
- 用户截图反馈：new ClickGUI 的 Skiko 视觉在面板周围有明显半透明黑色圆角外圈；用户确认问题不是青色文字 glow。
- 删除 `RenderBackendProbe` 测试类、移除 probe Gradle/JVM 开关、修复 Skiko `drawRoundedRect(... smoothness ...)` 软边语义后重新运行 `.\gradlew.bat jar`：PASS，`obfuscateJar: renamed 440 classes`。
- 删除 probe 后重新运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO --dry-run`：PASS。
- 删除 probe 后源码/Gradle `rg RenderBackendProbe|openzen.render.probe|openzenRenderProbe`：无匹配。
- 完整 new ClickGUI 黑色外圈视觉对比验收：PASS，用户反馈最终复测通过。

## 用户确认

- 结果：PASS
- 用户输入：PASS，最后校验一下，是不是基本上所有2D渲染都切到skiko上面了
- 确认时间：2026-06-08
