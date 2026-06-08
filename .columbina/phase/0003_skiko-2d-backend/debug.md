# Debug Record: Phase 0003

## Debug 类型

phase

## 关联问题

Skiko 0.148.1 Java API 与初版伪代码不一致；一次 `reobfJar` 被本机文件锁阻塞；首次 `runClient0 -PopenzenRenderBackend=SKIKO` 游戏内 fallback 到 legacy。

## Skiko 0.148.1 Java API 签名修正

### 症状

首次 `compileJava` 报告多个 Skiko API 不匹配，例如：

```text
DirectContext.makeGL()
BackendRenderTarget.makeGL(...)
Surface.makeFromBackendRenderTarget(...)
Shader.makeLinearGradient(...)
MaskFilter.makeBlur(...)
Path.moveTo/lineTo/quadTo/cubicTo/closePath/addRect/addRRect
Data.makeFromBytes(...)
Typeface.makeDefault()
```

同时缺少 `kotlin.jvm.internal.markers.KMappedMarker`，说明 Kotlin stdlib 没有进入 Java compile classpath。

### 修复

- 在 `build.gradle` 显式增加 `org.jetbrains.kotlin:kotlin-stdlib:2.3.20`。
- 用 `javap` 查看本机 Gradle cache 的 Skiko class 签名。
- 将 Java 调用改为 Kotlin `Companion` factory，例如 `DirectContext.Companion.makeGL()`、`Surface.Companion.makeFromBackendRenderTarget(...)`。
- 用 `PathBuilder` 构造 Skia path，再 `detach()` 出 `org.jetbrains.skia.Path`。
- 用 `FontMgr.Companion.getDefault().makeFromData(...)` 载入字体数据。

### 证据

| 证据 | 说明 |
|---|---|
| `.\gradlew.bat jar` 首次 compile output | 暴露 Skiko API 缺失和 Kotlin stdlib 缺失 |
| `javap` 输出 | 确认 `DirectContext`、`Surface`、`BackendRenderTarget`、`PathBuilder`、`Shader`、`MaskFilter`、`Data`、`Image`、`Typeface` 的真实 Java 签名 |
| `.\gradlew.bat jar` 最终输出 | `compileJava`、`reobfJar`、`obfuscateClasses` 成功 |

## reobfJar 文件占用

### 症状

一次 `.\gradlew.bat jar` 在 `reobfJar` 阶段失败；用户随后运行 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO` 也在同一阶段失败：

```text
java.nio.file.FileSystemException: D:\OpenZen-master\build\libs\hey-1.0.jar: 另一个程序正在使用此文件，进程无法访问。
```

### 修复

- 运行 `.\gradlew.bat --stop` 停止 Gradle daemon。
- 重新运行 `.\gradlew.bat jar` 后成功。
- 用户复现同类文件锁后，再次运行 `.\gradlew.bat --stop` 并用 `.\gradlew.bat jar` 验证通过。

## Skiko runtime 游戏内不可见

### 症状

用户启用 probe 后，游戏内显示：

```text
configured=SKIKO active=OPENGL_LEGACY
skikoActive=false fallback=true
LegacyGlBackend OPENGL_LEGACY
```

`run/logs/latest.log` 记录：

```text
Render backend SKIKO failed, falling back to legacy OpenGL
java.lang.NoClassDefFoundError: org/jetbrains/skia/DirectContext
Caused by: java.lang.ClassNotFoundException: org.jetbrains.skia.DirectContext
```

### 根因

Skiko runtime 只在 `implementation` / `runtimeClasspath` 中，Java 编译和 Gradle 解析都能通过，但 ForgeGradle 的游戏运行 `ModuleClassLoader` 没有从该配置暴露 Skiko jar。`minecraftLibrary` 为空时，Minecraft/ModLauncher 运行期找不到 `org.jetbrains.skia.DirectContext`。

### 修复

- 在 `build.gradle` 中增加：

```groovy
minecraftLibrary 'org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.148.1'
minecraftLibrary 'org.jetbrains.kotlin:kotlin-stdlib:2.3.20'
```

### 证据

| 证据 | 说明 |
|---|---|
| `.\gradlew.bat dependencies --configuration runtimeClasspath` | Skiko 在 Gradle runtimeClasspath 中存在 |
| `.\gradlew.bat dependencies --configuration minecraftLibrary` 修复前 | `minecraftLibrary` 为 `No dependencies` |
| `.\gradlew.bat dependencies --configuration minecraftLibrary` 修复后 | Skiko runtime、Skiko AWT、Kotlin stdlib、coroutines、jbr-api 已进入 Minecraft library 配置 |
| `.\gradlew.bat jar` | 修复后构建成功 |

## Skiko native runtime resource 不可见

### 症状

用户再次启用 probe 后仍显示：

```text
configured=SKIKO active=OPENGL_LEGACY
skikoActive=false fallback=true
```

`run/logs/latest.log` 记录新的首个真实异常：

```text
Render backend SKIKO failed, falling back to legacy OpenGL
java.lang.ExceptionInInitializerError
Caused by: org.jetbrains.skiko.LibraryLoadException: Cannot find skiko-windows-x64.dll.sha256, proper native dependency missing.
```

### 根因

`minecraftLibrary` 已让 `org.jetbrains.skia.DirectContext` 等 Java class 进入 Forge/ModLauncher 运行期，但 Skiko 的 native loader 仍通过 `org.jetbrains.skiko.Library.class.getResourceAsStream(...)` 在 `skiko-awt` 所在模块内查找 `skiko-windows-x64.dll.sha256`。Windows runtime artifact 是独立的 resource jar，ModLauncher 的 module/classloader 边界下该根资源没有被 Skiko loader 正常看到。

### 修复

- 新增 `skikoNativeRuntime` 配置，单独解析 `org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.148.1`。
- 新增 `extractSkikoRuntime` 任务，将 `skiko-windows-x64.dll`、`skiko-windows-x64.dll.sha256`、`icudtl.dat` 解到 `build/skiko-runtime/windows-x64`。
- 在 client run config 中增加 `-Dskiko.library.path=${buildDir}/skiko-runtime/windows-x64`，让 Skiko native loader 直接 `System.load(...)` 该目录下的 DLL。
- `runClient0` 与 `runClient` 都依赖 `extractSkikoRuntime`，避免启动前 native 文件不存在。

### 证据

| 证据 | 说明 |
|---|---|
| `jar tf skiko-awt-runtime-windows-x64-0.148.1.jar` | 确认 runtime jar 内包含 `skiko-windows-x64.dll`、`.sha256`、`icudtl.dat` |
| `.\gradlew.bat extractSkikoRuntime` | 任务执行成功 |
| `build/skiko-runtime/windows-x64` | 已生成 `skiko-windows-x64.dll`、`skiko-windows-x64.dll.sha256`、`icudtl.dat` |
| `.\gradlew.bat jar` | 修复后 `compileJava`、`reobfJar`、`obfuscateClasses` 成功 |
| `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run` | 任务图包含 `extractSkikoRuntime` 且不启动 Minecraft |

## Skiko 文字与图形尺寸割裂

### 症状

用户用 `-PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true` 启动后确认 probe 显示：

```text
configured=SKIKO active=SKIKO
skikoActive=true fallback=false
```

但截图显示 Skiko 后端下文字明显大于旧 OpenGL legacy，左侧 module list、DynamicIsland 文本和 Hotkeys 文本与背景图形错位，整体观感和原版不一致。

### 根因

旧字体路径不是直接使用 `FontRenderer.size` 渲染。`Fonts.getCustomFont(...)` 会先把传入 size 除以 2，再由 `CustomFont` 按 GUI scale 做 atlas oversampling，并通过 `poseStack.scale(1.0 / scale, 1.0 / scale, 1.0)` 回到 GUI 坐标尺寸。Skiko 首版直接使用 `fontRenderer.getSize()` 创建 `org.jetbrains.skia.Font`，而 Canvas 本身又按 GUI scale 映射到 framebuffer，导致视觉字号约为 legacy 的两倍。分段文字还用 Skia 自己的 `measureTextWidth(...)` 推进光标，与旧 `FontRenderer.getWidth(...)` 布局宽度存在分叉。

### 修复

- `SkikoBackend.getSkFont(...)` 使用 `fontRenderer.getSize() * 0.5f` 创建 Skia font，对齐旧 atlas 的实际 GUI 字号。
- `SkikoBackend.drawFormattedString(...)` 保留旧 `FontRenderer.getMetrics()` 的 baseline/lineHeight 语义。
- `SkikoBackend.drawTextSegment(...)` 改用 `fontRenderer.getWidth(value)` 推进 `§` 分段文字光标，避免 Skia measure 与旧布局系统分叉。
- `FontRenderer.getWidth(...)` 可能首次触发旧 glyph atlas 上传，因此测宽前先 flush Skia，测宽后调用 `DirectContext.resetGLAll()`，避免外部 GL 操作污染 Skia 上下文状态。

注意：上述“分段文字继续调用 `fontRenderer.getWidth(...)` 并 flush/reset”的方案后续被“Skiko 字体/图标偏移与圆角背景间歇消失”修复取代；不要再把它作为当前推荐路径。

### 证据

| 证据 | 说明 |
|---|---|
| 用户截图 `2026-06-08_01.40.32.png` | Skiko active 后文字明显过大、与背景块错位 |
| 用户截图 `2026-06-08_01.13.52.png` | legacy OpenGL 下文字和背景尺寸正常 |
| `Fonts.getCustomFont(...)` | legacy 字体实际用 `size / 2.0f` 创建 `CustomFont` |
| `CustomFont.drawStringRGBFull(...)` | legacy atlas 按 GUI scale oversampling 后用 pose scale 还原 |
| `.\gradlew.bat jar` | 字体尺寸/测宽修复后构建成功 |
| `.\gradlew.bat jar` | 测宽 flush/reset 保护补充后构建成功 |

## 回归测试

- `.\gradlew.bat jar`：构建成功。
- `RenderBackendProbe` 编译与 obfuscation：PASS，`obfuscateJar: renamed 438 classes`。
- `minecraftLibrary` 依赖树包含 Skiko runtime：PASS。
- `.\gradlew.bat extractSkikoRuntime`：PASS，Skiko native 文件已解到 `build/skiko-runtime/windows-x64`。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS，任务图包含 `extractSkikoRuntime`。
- Skiko 文字尺寸/测宽修复后的 `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0`：未测试，等待用户启动客户端做视觉验收。

## 后续提醒

- 若再次遇到 `build/libs/hey-1.0.jar` 文件占用，先尝试 `.\gradlew.bat --stop`。
- 不要把本阶段的 `jar` 构建成功等同于 Skiko 游戏内视觉验收通过。
- 验证 Skiko 是否实际启用时，使用 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true`，并查看左上角 probe 面板或 `run/logs/latest.log` 中的 `OpenZen render backend probe`。
- 如果后续还有 `NoClassDefFoundError: org/jetbrains/skia/...`，优先检查 `minecraftLibrary` 是否被移除或依赖解析是否失败。
- 如果后续还有 `Cannot find skiko-windows-x64.dll.sha256`，优先检查 `-Dskiko.library.path` 和 `build/skiko-runtime/windows-x64`。
- 如果后续文字仍有轻微垂直偏移，优先微调 `SkikoBackend.drawString(...)` 的 baseline，而不是改 HUD/GUI 层布局公式。

## Skiko 字体/图标偏移与圆角背景间歇消失

### 症状

用户继续截图对比 Skiko 与 legacy 后发现：

- DynamicIsland / Watermark 中 `Z` icon、`beta`、`b1`、server/ping 文本与圆角背景相对位置偏移，字号和 legacy 不一致。
- KeyBinds 中 `Hotkeys` 文本、material icon、行文字和右侧数值/开关与圆角块位置不一致。
- Watermark 圆角背景有时会持续消失一段时间，文字仍然显示，之后背景又恢复。

### 根因

Skiko 后端的上一轮文字修复仍在 `SkikoBackend.drawTextSegment(...)` 中调用 legacy `FontRenderer.getWidth(...)`，并在每个文字 segment 前后执行 `flush()` / `DirectContext.resetGLAll()`。这会在同一 Skia frame 中间触发旧 `CustomFont` glyph atlas 路径和 GL state 修改，容易污染 Skia 当前 framebuffer/surface 状态；同时 per-segment `scaleX` 为了强行贴合 legacy advance 拉伸/压缩 Skia glyph，导致 material icon 和 Zen icon 的视觉中心继续偏移。

此外，`GlHelper.getStringWidth(...)` 和部分 HUD 直接调用 `FontRenderer.getWidth(...)` 时，命名字体测宽仍可能触发旧 glyph atlas 上传；这类布局测宽发生在 Skiko 绘制期间时，也属于状态污染入口。

### 修复

- `SkikoBackend.drawString(...)` 按旧 `DrawContext -> CustomFont` 路径的语义计算 baseline：`DrawContext` 先加 `GlyphMetrics.ascent()`，`CustomFont` 内部 `--y`，再按 atlas ascent 回到真实 baseline；Skiko 现在用 `toLegacyTextBaseline(...)` 复刻该过程，并保留 0.1 像素取整。
- 移除 `SkikoBackend.drawTextSegment(...)` 内的 legacy `FontRenderer.getWidth(...)`、中途 `flush()` / `resetGLAll()` 和 per-segment `scaleX`。
- 回撤上一轮高风险的全局 `FontRenderer.getWidth(...)` / `getBounds(...)` CPU AWT 测宽改动和 `GlHelper` GUI scale 缓存 key 改动，避免改变 legacy 布局基线。
- 新增 `RenderBackend.measureTextWidth(...)`；`GlHelper.getStringWidth(...)` 在 Skiko active 的 `DrawContext` 内走 Skiko font 测宽，避免 `GlHelper.drawTextFormatted(...)` 每段文字后触发旧 `CustomFont.getStringWidth(...)` 和 glyph atlas 上传。
- `SkikoBackend.flush()` 改为 `directContext.flushAndSubmit(surface, true)`，统一提交当前 Skia surface。

### 证据

| 证据 | 说明 |
|---|---|
| 用户截图 | Skiko 与 legacy 的 Watermark / KeyBinds 文字、图标、背景对齐差异明显 |
| `SkikoBackend.drawTextSegment(...)` 修复前源码 | 每个 segment 调用 `measureLegacyTextWidth(...)`，内部执行 `flush()` 和 `afterExternalGlDraw()` |
| `FontRenderer.getWidth(...)` 修复前源码 | 命名字体测宽会进入 `CustomFont.getStringWidth(...)`，可能触发 glyph atlas 构建和 texture 上传 |
| `.\gradlew.bat jar` | 修复后完整 jar 构建成功 |
| `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run` | Skiko 开关下任务链配置成功，未启动游戏 |

### 回归状态

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- Skiko 实机视觉验收：PASS，用户确认 `active=SKIKO` 且渲染正常。

## Skiko GL 状态污染导致 MC 原生渲染异常

### 症状

用户反馈上一轮修复后问题加重：

- OpenZen 字和图形大量不显示或错位。
- 世界画面出现大块半透明彩色三角/扇形覆盖。
- 打开 MC 原版物品栏后原版 GUI 也被遮罩和错误几何污染。

`run/logs/latest.log` 中持续出现 OpenGL debug error：

```text
GL_INVALID_OPERATION error generated. Array object is not active.
GL_INVALID_OPERATION error generated. Invalid VAO/VBO/pointer usage.
GL_INVALID_VALUE error generated. Invalid offset and/or size.
```

### 根因

Skiko GPU surface 绘制和旧 Minecraft/OpenGL 绘制共用同一个 GL context。上一轮只做了局部 flush/reset，未在 Skiko begin/end 和 legacy GL 混画边界完整恢复 Minecraft 期望的 FBO、VAO/VBO、shader program、texture unit、viewport、scissor、blend/depth/color mask 等状态，导致后续 `BufferBuilder` / vanilla GUI 读取到被 Skia 修改过的 GL 管线状态。

同时 `RenderUtil` 的 Skiko 分流只检查 `Renderer.isSkikoEnabled()`，没有检查传入 `PoseStack` 是否属于 2D GUI；这会让非 2D 或带外部矩阵的调用误进入 Skia 2D surface。`GlHelper.drawTextFormatted(...)` 仍会在 Skiko 绘制期间调用旧 `FontRenderer.getWidth(...)` 推进分段文字，也可能触发 glyph atlas 上传。

### 修复

- `SkikoBackend` 增加 `GlStateGuard`，在 begin 捕获状态，在 end 和 `beforeExternalGlDraw()` 后恢复状态。
- `GlStateGuard` 覆盖 FBO、VAO、array/element buffer、shader program、active texture、texture binding、viewport、scissor、blend function/equation、depth mask、color mask、blend/depth/cull/scissor enable。
- `RenderBackend` 增加 `pushExternalPose(...)` / `popExternalPose()`；`SkikoBackend` 用 Skia `Matrix33` concat 外部 `PoseStack`，并在 `Renderer.renderWithPose(...)` 嵌套和独立路径中配对 save/restore。
- `Renderer.canUseSkiko2D(PoseStack)` 用 2D affine pose 判定限制 `RenderUtil` 的 Skiko 分流，避免 world/3D pose 误入 Skia。
- `RenderBackend.measureTextWidth(...)` + `SkikoBackend.measureTextWidth(...)` 提供 Skiko 帧内安全测宽；`GlHelper.getStringWidth(...)` 在 Skiko active 时不再触发 legacy glyph atlas 上传。
- 保留 legacy OpenGL 3D/world 渲染路径，不把 `drawSolidBox`、`drawOutlineBox`、`drawColoredBox`、`drawFilledColoredBox`、`drawSpiralEffect`、`drawBoxVerts` 迁到 Skiko。

### 证据

| 证据 | 说明 |
|---|---|
| 用户截图 | 世界和原版物品栏均被大块半透明几何污染 |
| `run/logs/latest.log` | 出现 VAO/VBO/pointer 和 offset/size OpenGL error |
| `SkikoBackend` | 新增 `GlStateGuard`、`pushExternalPose(...)`、`measureTextWidth(...)` |
| `Renderer` | 新增 `canUseSkiko2D(PoseStack)` 和外部 pose 配对应用 |
| `RenderUtil` | Skiko 分流从 `isSkikoEnabled()` 改为 `canUseSkiko2D(poseStack)` |
| `GlHelper` | Skiko active 时 `getStringWidth(...)` 走后端安全测宽 |

### 回归状态

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO -PopenzenRenderProbe=true --dry-run`：PASS。
- 完整 `runClient0` 游戏内视觉验收：PASS，用户确认 `active=SKIKO` 且渲染正常。
- 后续仍需在新增渲染迁移时继续检查：世界/物品栏不出现大块半透明几何，`run/logs/latest.log` 不刷 VAO/VBO/pointer error，Watermark 圆角背景不间歇消失。
