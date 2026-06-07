# Columbina Init

## 项目名称

OpenZen

## 项目类型

Minecraft Forge 1.20.1 客户端项目，包含 Java/Forge mod 与 Windows native loader。

## 技术栈

- 语言：Java 17、C++17、CMake、Qt6 Widgets
- 构建工具：Gradle 8.8 wrapper、ForgeGradle、CMake、MSVC、vcpkg
- 主要框架：Minecraft Forge `47.4.20`、official Mojang mappings for `1.20.1`
- 运行环境：Windows、Minecraft `1.20.1` client、JDK 17；native 构建需要 CMake、MSVC、vcpkg、Qt6

## 项目目标

OpenZen 是一个开源 Minecraft Forge 1.20.1 client。Java 侧以 `shit.zen` 为主要包根，提供模块系统、事件总线、配置、HUD、GUI、patch 和 Web UI 等功能。项目同时包含 `asm.patchify` Java agent/transformer，用于运行时 patch。Native 侧提供注入 DLL 与 Qt Widgets loader，将 obfuscated jar 打包到 DLL，再将 DLL 嵌入最终 loader。运行配置强调通过 `runClient0` 启动，以确保 jar 作为 `-javaagent` 注入并激活 patch。

## 关键目录

| 路径 | 作用 |
|---|---|
| `src/main/java/shit/zen` | Java/Forge client 主源码 |
| `src/main/java/asm/patchify` | 自定义 patch annotation、loader 和 Java agent transformer |
| `src/main/java/shit/zen/modules` | 模块基类、分类和功能模块 |
| `src/main/java/shit/zen/manager` | 模块、命令、配置、HUD、lag、target 等 manager |
| `src/main/java/shit/zen/event` | reflection-based event bus 和事件基类 |
| `src/main/java/shit/zen/settings` | 模块 setting 模型和实现 |
| `src/main/java/shit/zen/config` | 持久化 module/value config |
| `src/main/java/shit/zen/patch` | 注册到 `ZenClient.registerPatches()` 的 patch 类 |
| `src/main/resources` | Forge metadata、资源、字体、cloud assets、Web UI 静态文件 |
| `native/dll` | 注入 DLL，负责附加目标 JVM 并加载嵌入 jar |
| `native/loader` | Qt GUI loader，负责嵌入并注入 `OpenZen.dll` |
| `.github/workflows/build-loader.yml` | Windows CI 构建和可选 release workflow |

## 关键入口

| 文件/类 | 作用 |
|---|---|
| `src/main/java/shit/zen/ZenClient.java` | Forge entry point，`@Mod("hey")`，初始化 manager、event bus、patch、ready gate |
| `src/main/java/shit/zen/manager/ModuleManager.java` | 手动注册模块的核心位置 |
| `src/main/java/shit/zen/modules/Module.java` | 模块基类；模块通过 `setEnabled(true)` 注册 event bus |
| `src/main/java/shit/zen/event/EventBus.java` | 反射分发 `@EventTarget` listener |
| `src/main/java/asm/patchify/loader/PatchAgent.java` | Java agent 入口和 retransform 相关逻辑 |
| `src/main/java/shit/zen/dll/DllBootstrap.java` | Native DLL 加载 Java 侧入口之一 |
| `src/main/java/shit/zen/dll/GameLoaderBridge.java` | Native bridge 依赖的 Java 侧桥接类之一 |
| `native/dll/src/main.cpp` | 注入 DLL native 入口 |
| `native/loader/src/main.cpp` | Qt loader native 入口 |
| `build.gradle` | ForgeGradle、Java agent manifest、class-name obfuscation、native build orchestration |

## 已知约束

- 默认使用中文回复用户；代码标识符、类名、路径、命令和错误文本保持原样。
- 不要随意重命名 `shit.zen`、`asm.patchify`、`ZenClient`、`DllBootstrap`、`GameLoaderBridge`、`PatchAgent`。
- 不要只改 `mod_id=hey`；如需改动，必须同步 `@Mod("hey")`、`mods.toml`、dependency blocks、run config namespaces 和资源展开假设。
- 模块初始化延迟到 ready tick；不要假设模块在 `ZenClient` constructor 内立即完成注册。
- 新模块必须在 `ModuleManager.initModules()` 手动注册，并遵循现有 `Module`、`Setting<?>`、`@EventTarget` 模式。
- 新 patch 必须注册到 `ZenClient.registerPatches()`，并通过 active `-javaagent` 路径验证。
- `build/`、`.gradle/`、`.idea/`、`run/`、`native/build/`、`native/zen.jar`、`native/dll/src/generated_names.h` 视为生成或本地状态，除非任务明确要求，不要编辑。
- runtime config directory 为 `%USERPROFILE%\.zen`。
- Web UI server 使用 `http://127.0.0.1:8089`。

## 常用验证

| 场景 | 命令 | 说明 |
|---|---|---|
| Java-only compile/package | `.\gradlew.bat jar` | jar task 会经过 Forge reobfuscation 和 class-name obfuscation |
| patch/runtime 行为 | `.\gradlew.bat runClient0` | 构建 jar 后以 `-javaagent` 启动 client |
| native loader/DLL | `.\gradlew.bat dll` | 需要 JDK 17、CMake、MSVC、vcpkg、Qt6 |
| 清理 | `.\gradlew.bat clean` | 同时清理 `native/build/` 和 `native/zen.jar` |

## 历史追溯规则

旧改动不写入 `AGENTS.md`，统一到 `.columbina/CONTEXT.md` 和 `.columbina/phase/` 中追溯。
