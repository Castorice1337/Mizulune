# Test Record: Phase 0003

## 测试状态

PASS

## 测试目标

验证 Skiko 2D 后端骨架不会破坏 Java 编译、Forge reobf 和项目自定义 obfuscation；后续还需要用户运行 Minecraft 客户端进行视觉验收。

## 测试步骤

- 已运行 `.\gradlew.bat jar`。
- 已通过 `javap` 检查本机 Gradle cache 中 Skiko `0.148.1` 的 Java API 签名，并据此修正 `DirectContext`、`Surface`、`BackendRenderTarget`、`PathBuilder`、`Shader`、`MaskFilter`、`Data`、`Image`、`Typeface` 等调用。
- 用户运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO` 时在 `reobfJar` 阶段遇到 `build/libs/hey-1.0.jar` 文件占用。
- 已运行 `.\gradlew.bat --stop`。
- 已再次运行 `.\gradlew.bat jar`。
- 新增 `RenderBackendProbe` 后已运行 `.\gradlew.bat jar`。
- 用户通过 probe 确认 `configured=SKIKO active=OPENGL_LEGACY skikoActive=false fallback=true`。
- 已运行 `.\gradlew.bat dependencies --configuration minecraftLibrary`。
- 将 Skiko/Kotlin 依赖加入 `minecraftLibrary` 后已再次运行 `.\gradlew.bat jar`。
- 用户再次确认 probe 仍为 `active=OPENGL_LEGACY`，日志显示 `Cannot find skiko-windows-x64.dll.sha256`。
- 已运行 `.\gradlew.bat extractSkikoRuntime`。
- 已检查 `build/skiko-runtime/windows-x64` 包含 `skiko-windows-x64.dll`、`skiko-windows-x64.dll.sha256`、`icudtl.dat`。
- 修复 native runtime 抽取和 `skiko.library.path` 后已运行 `.\gradlew.bat jar`。
- 已运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`。
- 用户截图确认 Skiko 已 active，但文字与图形严重错位、字号明显大于 legacy。
- 已修复 `SkikoBackend` 的 Skia font size 换算和 `§` 分段文字测宽推进。
- 字体尺寸/测宽修复后已运行 `.\gradlew.bat jar`。
- 已补充 `FontRenderer.getWidth(...)` 测宽前后 Skia flush / GL reset 保护，并再次运行 `.\gradlew.bat jar`。
- 用户继续截图确认：Skiko active 后字体/图标仍与 legacy 存在位置偏移，且 Watermark 圆角背景会间歇消失。
- 已移除 Skiko 文本绘制阶段的 legacy `FontRenderer.getWidth(...)`、per-segment `flush()` / `resetGLAll()` 和 `scaleX`。
- 用户反馈上一轮修复后出现严重状态污染：字和图形不渲染、世界出现大块半透明三角/扇形、MC 原版物品栏也被污染。
- 已检查 `run/logs/latest.log`，日志持续出现 `Array object is not active`、`Invalid VAO/VBO/pointer usage`、`Invalid offset and/or size`。
- 已回撤上一轮高风险的全局 `FontRenderer.getWidth(...)` / `getBounds(...)` CPU AWT 测宽改动和 `GlHelper` GUI scale 缓存 key 改动。
- 已新增 `GlStateGuard` 恢复 FBO、VAO/VBO、shader、texture、viewport、scissor、blend/depth/color mask 等状态。
- 已新增 `RenderBackend.measureTextWidth(...)`，并让 `GlHelper.getStringWidth(...)` 在 Skiko active 时走后端安全测宽。
- 已新增外部 `PoseStack` concat/restore，并让 `RenderUtil` 通过 `Renderer.canUseSkiko2D(poseStack)` 限制 Skiko 分流。
- 本轮 GL 状态隔离和 2D/3D 分流修复后已运行 `.\gradlew.bat jar`。
- 已运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`。

## 期望结果

- `compileJava` 成功。
- `reobfJar` 成功。
- `obfuscateClasses` 成功并生成 `build/rename-mapping.txt`。
- 默认 legacy backend 不改变现有 UI/HUD 行为。
- Skiko backend 可通过 `-PopenzenRenderBackend=SKIKO` 进入后续人工验证。
- 启动前 Skiko native runtime 文件已存在，避免 `Cannot find skiko-windows-x64.dll.sha256`。
- Skiko active 后文字尺寸接近 legacy，且按旧 `FontRenderer.getWidth/getMetrics` 布局。
- Skiko active 后 Watermark / DynamicIsland / KeyBinds 的字体和图标位置更接近 legacy。
- Skiko active 后不再因文字布局测宽触发 legacy glyph atlas GL 上传。
- Skiko active 后 world/3D pose 不会误进 Skia 2D surface。
- Skiko active 后打开 MC 原版物品栏不应出现大块半透明几何、遮罩或 VAO/VBO 状态污染。

## 实际结果

- `.\gradlew.bat jar` 最终成功。
- 输出显示 `compileJava`、`reobfJar`、`obfuscateClasses` 均完成。
- 曾遇到一次 `build/libs/hey-1.0.jar` 文件占用，执行 `.\gradlew.bat --stop` 停止 Gradle daemon 后重跑通过。
- 用户再次遇到同类文件占用后，执行 `.\gradlew.bat --stop`，随后 `.\gradlew.bat jar` 成功。
- 新增 `RenderBackendProbe` 后，`.\gradlew.bat jar` 成功，obfuscation 输出显示 class 数变为 438。
- `minecraftLibrary` 依赖树现在包含 `org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.148.1`、`org.jetbrains.skiko:skiko-awt:0.148.1`、`org.jetbrains.kotlin:kotlin-stdlib:2.3.20` 等运行依赖。
- 加入 `minecraftLibrary` 后 `.\gradlew.bat jar` 成功。
- 用户再次启动后仍 fallback，新的日志首个真实异常为 `Cannot find skiko-windows-x64.dll.sha256`。
- `.\gradlew.bat extractSkikoRuntime` 成功。
- `build/skiko-runtime/windows-x64` 已生成 `skiko-windows-x64.dll`、`skiko-windows-x64.dll.sha256`、`icudtl.dat`。
- 修复 native runtime 抽取和 `skiko.library.path` 后，`.\gradlew.bat jar` 成功。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run` 成功，任务图显示 `extractSkikoRuntime` 在 `runClient0` / `runClient` 前。
- 用户截图显示 Skiko 后端已 active，但文字尺寸/位置与图形严重割裂。
- 已将 Skia font size 改为 `fontRenderer.getSize() * 0.5f`，并用 `fontRenderer.getWidth(...)` 推进分段文字。
- 字体尺寸/测宽修复后 `.\gradlew.bat jar` 成功。
- 测宽 flush/reset 保护补充后 `.\gradlew.bat jar` 成功。
- 用户反馈上一轮修复后完整游戏内视觉失败，现象为严重 GL 状态污染。
- 本轮 GL 状态隔离、后端安全测宽和 2D/3D 分流修复后，`.\gradlew.bat jar` 成功。
- 本轮修复后，`.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run` 成功。
- 用户完整运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO` 后确认 `active=SKIKO`，游戏内渲染正常。
- 本轮 GL 状态污染修复后的实际游戏内视觉验收：PASS。

## 用户确认

- 结果：PASS
- 用户输入：PASS，active=skia，正常了
- 确认时间：2026-06-08
