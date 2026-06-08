# Phase 0006: DLL 注入路径 Skiko runtime 打包

## 状态

waiting-test

## 本阶段目标

补齐 phase 0003 计划中后置的 DLL 注入路径 Skiko runtime 依赖处理，让 `OpenZen.dll` 嵌入的 `zen.jar` 在注入后能提供 Skiko/Kotlin Java class 和 Windows x64 Skiko native 文件。

## 实际完成内容

- `jar` 任务会把 `skikoNativeRuntime` 解析到的 runtime jar 嵌入到 `openzen/dll-libs/`。
- `GameLoaderBridge` 在 DLL 注入路径抽取 `zen.jar` 资源后，扫描 `openzen/dll-libs/*.jar`，通过 `Instrumentation.appendToSystemClassLoaderSearch(...)` 追加到 system classloader。
- 如果 `gameLoader` 仍不可见 `org.jetbrains.skia.DirectContext` 或 `kotlin.jvm.internal.Intrinsics`，`GameLoaderBridge` 会 fallback，把依赖 jar 中的 class 按固定点重试定义到 `gameLoader`。
- `GameLoaderBridge` 会从嵌套 runtime jar 中抽取 `skiko-windows-x64.dll`、`skiko-windows-x64.dll.sha256` 和 `icudtl.dat`，并设置 `skiko.library.path`。

## 改动文件

| 文件 | 改动说明 |
|---|---|
| `build.gradle` | 将 Skiko runtime 解析结果嵌入 `openzen/dll-libs/`，供 DLL 注入路径使用 |
| `src/main/java/shit/zen/dll/GameLoaderBridge.java` | 注入时准备 DLL runtime 依赖 jar、Skiko native 文件和 `skiko.library.path` |

## 新增/修改的核心类

| 类/模块 | 作用 |
|---|---|
| `GameLoaderBridge` | DLL 注入路径中将 OpenZen class 定义到 `gameLoader`，并补齐嵌套 runtime 依赖可见性 |

## 关键实现决策

- 不改 native resource 结构，继续只让 `OpenZen.dll` 嵌入 `zen.jar`；Skiko 依赖作为 `zen.jar` 内部资源随 jar 一起进入 DLL。
- 优先使用 `Instrumentation.appendToSystemClassLoaderSearch(...)` 暴露 dependency jar，只有在 `gameLoader` 仍不可见关键 class 时才 fallback 到 `defineClass`。
- Skiko native 文件不依赖 Skiko resource jar 被 classloader 直接查到，而是抽取后显式设置 `skiko.library.path`，复用开发期已验证过的加载边界。
- 默认后端策略已在 phase 0007 改为 Skiko；本阶段只负责 DLL 注入路径具备加载 Skiko runtime 的能力。

## 复用的已有结构

- 复用 `stageNativeJar` 的 `native/zen.jar` staging 链路。
- 复用 `GameLoaderBridge` 已有的资源抽取目录和 `openzen.resources` 机制。
- 复用 phase 0003 的 `skikoNativeRuntime` 配置，不新增平行依赖解析系统。

## 对后续 AI 的提醒

- 不要把 Skiko/Kotlin dependency class 混进 OpenZen 自有 class obfuscation 范围；`openzen/dll-libs/*.jar` 应作为资源保留。
- 如果 DLL 注入后仍报 `NoClassDefFoundError: org/jetbrains/skia/...`，先看 `DLL runtime dependencies prepared` 日志中的 `skiaVisible` / `kotlinVisible`。
- 如果 DLL 注入后仍报 `Cannot find skiko-windows-x64.dll.sha256` 或 `LibraryLoadException`，先看日志中的 `skiko.library.path` 是否指向抽取目录，以及该目录是否包含 3 个 Skiko native 文件。
- 完整 DLL 验证仍需要 `.\gradlew.bat dll` 和实际注入运行，不要只用 `jar` 成功代替注入路径验收。

## 未完成内容

- 未运行完整 `.\gradlew.bat dll` 原生构建。
- 未做实际 `OpenZenLoader.exe` 注入验证。
- 未确认 DLL 注入后默认 Skiko 是否稳定生效；具体默认后端和游戏内切换见 phase 0007。

## 测试状态

WAITING_USER_PASS
