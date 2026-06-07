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
