# Test Record: Phase 0005

## 测试状态

PASS

## 测试目标

验证 phase0005 的 Skiko `CustomFont` 迁移和 Skia clip 替换可以通过构建、开发运行任务链配置，并等待用户做完整游戏内视觉确认。

## 测试步骤

1. 运行 `.\gradlew.bat jar`。
2. 运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO --dry-run`。
3. 运行 `git diff --check`。
4. 静态搜索剩余 `StencilHelper`、`FontStore`、`RenderSystem` / `BufferBuilder` 路径，做阶段结束渲染系统例行检查。
5. 等待用户运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO` 做完整视觉验收。

## 期望结果

- `compileJava`、`reobfJar`、`obfuscateClasses` 成功。
- `runClient0` dry-run 任务链包含 Skiko runtime 提取和 client run 配置。
- `FontStore` / `CustomFont` 字体在 Skiko active 下显示并与原版基本一致。
- new ClickGUI 和 `SettingsPanel` 不再依赖 Skiko 路径中的 GL stencil mask。
- 没有 `Scissor stack underflow`、`Array object is not active` 或 `Render backend SKIKO failed`。

## 实际结果

- 首次沙箱内运行 `.\gradlew.bat jar` / dry-run 被 Gradle wrapper 网络限制拦截：`java.net.SocketException: Permission denied: getsockopt`。
- 放行后运行 `.\gradlew.bat jar`：PASS，`compileJava`、`reobfJar`、`obfuscateClasses` 成功，输出 `obfuscateJar: renamed 441 classes`。
- 放行后运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO --dry-run`：PASS，任务链配置成功，所有任务 dry-run `SKIPPED`。
- `git diff --check`：PASS，仅有 CRLF 工作区提示，无 whitespace error。
- 阶段结束静态检查：PASS，`StencilHelper.beginWrite/beginRead` 只剩 fallback 分支；`FontStore` 调用保留但已接入 `CustomFont` Skiko 优先路径。
- 完整游戏内视觉验收：PASS，用户确认全部正常。

## 用户确认

- 结果：PASS
- 用户输入：PASS，全部正常
- 确认时间：2026-06-08
