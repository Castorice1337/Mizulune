# OpenZen Agent Guide

This repository is a Minecraft Forge 1.20.1 client project named OpenZen. It has two coupled parts:

- A Java/Forge mod under `src/main/java`, with the active package root `shit.zen`.
- A Windows native loader under `native/`, built with CMake, MSVC, vcpkg, and Qt6 Widgets.

Use this file as the first-stop context before making changes.

## Project Snapshot

- Gradle wrapper: Gradle 8.8.
- Java target: Java 17 toolchain.
- Minecraft: `1.20.1`.
- Forge: `47.4.20`.
- Mapping channel: official Mojang mappings for `1.20.1`.
- Mod id: `hey`.
- Display name/version in properties: `OpenZen` / `1.0`.
- Forge entry point: `shit.zen.ZenClient`, annotated as `@Mod("hey")`.
- Runtime config directory: `%USERPROFILE%\.zen`.
- Web UI module: starts a local HTTP server on `http://127.0.0.1:8089`.

The root currently contains generated/local directories such as `build/`, `.gradle/`, `.idea/`, `run/`, and `native/build/`. Treat them as generated state unless a task explicitly asks about runtime logs or build outputs.

## Important Paths

- `build.gradle`: ForgeGradle setup, Java agent manifest, class-name obfuscation, native build orchestration.
- `gradle.properties`: Minecraft/Forge versions and mod metadata.
- `src/main/java/shit/zen/ZenClient.java`: main client bootstrap, manager setup, patch registration, readiness gate.
- `src/main/java/shit/zen/modules/`: module base classes and all feature modules.
- `src/main/java/shit/zen/manager/`: module, command, config, HUD, lag, and target managers.
- `src/main/java/shit/zen/event/`: reflection-based event bus and event types.
- `src/main/java/shit/zen/settings/`: module setting model and setting implementations.
- `src/main/java/shit/zen/config/`: persisted module/value config files.
- `src/main/java/shit/zen/patch/`: patch classes registered by `ZenClient.registerPatches()`.
- `src/main/java/asm/patchify/`: custom patch annotations and Java agent transformer.
- `src/main/resources/`: Forge metadata, mappings, fonts, cloud assets, and Web UI static files.
- `native/dll/`: injected DLL that attaches to the target JVM and loads the embedded jar.
- `native/loader/`: Qt GUI loader that embeds/injects `OpenZen.dll`.
- `.github/workflows/build-loader.yml`: Windows CI build and optional release workflow.

## Architecture Notes

`ZenClient` initializes once through the Forge mod constructor. It creates the event bus, managers, rotation handler, extracts bundled cloud assets, registers patches, and attempts to install/retransform Java agent patches.

Module initialization is delayed until the first ready tick. `ZenClient.isReady()` requires the client instance, event bus, Minecraft instance, player, non-empty username, and `mc.player.tickCount > 5`. Do not assume modules are registered immediately in the constructor.

Modules extend `shit.zen.modules.Module`. New modules must:

- Call `super(name, Category.X)` or `super(name, Category.X, keyCode)`.
- Declare `Setting<?>` fields as instance fields when they should be auto-registered.
- Be added manually in `ModuleManager.initModules()`.
- Use `@EventTarget` methods for event handling; `setEnabled(true)` registers the module on the event bus.

The event bus is reflection-based. Listener methods must take exactly one event parameter and be annotated with `@EventTarget`. Cancellable events stop dispatch after cancellation.

Patches use the custom `asm.patchify` system. Patch classes must be registered in `ZenClient.registerPatches()`. The Java agent instrumentation is only available when the JVM starts with the built jar as `-javaagent`.

## Build And Run

Use PowerShell on Windows.

```powershell
.\gradlew.bat runClient0
```

Use `runClient0` for local Minecraft client testing. It builds the jar first and then runs the Forge client with the jar configured as `-javaagent`. Plain `runClient` can launch without the agent and leave patches inactive.

```powershell
.\gradlew.bat jar
```

Builds the mod jar. The jar task finalizes through Forge reobfuscation and the custom class-name obfuscation path.

```powershell
.\gradlew.bat dll
```

Builds the native distribution path. This stages the obfuscated jar into `native/zen.jar`, configures/builds the CMake project, embeds `OpenZen.dll` into the Qt loader, and copies `OpenZenLoader.exe` to `build/dist/`.

Native prerequisites:

- `JAVA_HOME` must point to a JDK 17 install.
- CMake must be on PATH, bundled with Visual Studio, or installed standalone.
- MSVC build tools / Visual Studio 2019+ are required.
- vcpkg must exist at `VCPKG_ROOT`, `C:/vcpkg`, `D:/vcpkg`, or `%USERPROFILE%/vcpkg`.
- `native/vcpkg.json` pins Qt via builtin baseline `56bb2411609227288b70117ead2c47585ba07713`.

```powershell
.\gradlew.bat clean
```

Also removes `native/build/` and `native/zen.jar` via `cleanNative`.

## Obfuscation And Release Details

`build.gradle` defines `ext.obfuscateJar`, which renames every owned class under `shit.zen.**` and `asm.patchify.**` to fresh opaque names on every build. Method and field names are intentionally preserved.

The obfuscation step also emits:

- `build/rename-mapping.txt`: old-name to new-name mapping, needed to decode stack traces.
- `native/dll/src/generated_names.h`: generated bridge name for native loading.

Do not hand-edit generated obfuscation outputs. `native/dll/src/generated_names.h` is generated and ignored by git.

CI uploads:

- `OpenZenLoader-<sha>.exe`
- `OpenZen-<sha>.jar`
- `OpenZen-<sha>-mapping.txt`

A commit message containing `[Release]` triggers the optional GitHub Release path in the workflow. `[SKIP CI]` skips push CI.

## Native Loader Notes

`native/CMakeLists.txt` enforces Windows-only C++17 and static MSVC runtime linking. This is important because the DLL is manually mapped into another process; dynamic CRT imports can crash due to process-local DLL base differences.

The native build has two targets:

- `OpenZen`: injected DLL, built from `native/dll`.
- `OpenZenLoader`: Qt Widgets GUI loader, built from `native/loader`.

The DLL embeds `native/zen.jar` as a resource. The loader embeds `OpenZen.dll` as a resource, so the final local distribution ships only `build/dist/OpenZenLoader.exe`.

## Editing Rules For Agents

- Reply to the user in Chinese by default unless the user explicitly requests another language.
- Keep source changes narrow. This codebase has tight coupling between Forge metadata, Java agent manifest entries, obfuscation, and native generated names.
- Do not rename `shit.zen`, `asm.patchify`, `ZenClient`, `DllBootstrap`, `GameLoaderBridge`, or `PatchAgent` unless the build-time obfuscation/native bridge logic is updated and verified end to end.
- Do not change `mod_id=hey` without updating `@Mod("hey")`, `mods.toml`, dependency blocks, run config namespaces, and any generated resource expansion assumptions.
- Do not edit generated directories or files unless the task is specifically about build artifacts: `build/`, `.gradle/`, `run/`, `native/build/`, `native/zen.jar`, `native/dll/src/generated_names.h`.
- Preserve UTF-8 source encoding. Java compilation is configured with `options.encoding = 'UTF-8'`.
- Prefer existing utility classes in `shit.zen.utils.*` and existing manager/event/module patterns before adding new abstractions.
- When adding a module, register it in `ModuleManager.initModules()` and include focused manual validation through `runClient0`.
- When adding a patch, register it in `ZenClient.registerPatches()` and validate with the agent path active.
- When touching native code, verify with `.\gradlew.bat dll` if the host has JDK 17, CMake, MSVC, and vcpkg available.
- Avoid introducing credential collection, persistence outside the existing config directory, or unrelated network behavior.

## Verification Expectations

There is no obvious dedicated test suite in the current tree. Choose verification by blast radius:

- Java-only compile or packaging change: `.\gradlew.bat jar`.
- Patch/runtime behavior change: `.\gradlew.bat runClient0`, then inspect `run/logs/latest.log` or `run/logs/debug.log`.
- Native loader/DLL change: `.\gradlew.bat dll`.
- Release path change: inspect `.github/workflows/build-loader.yml` and make sure artifact names still match the workflow's staging checks.

If a verification command cannot run because local prerequisites are missing, report the exact missing tool or environment variable rather than guessing.

## Columbina Lightweight Workflow

本项目使用 Columbina 轻量工作流记录项目上下文和历史改动。

### 必读上下文

在进行较大代码修改、调试或新增功能前，必须优先阅读：

- `.columbina/INIT.md`
- `.columbina/CONTEXT.md`
- 与当前任务相关的 `.columbina/phase/*/CONTEXT.md`
- 如涉及 bug，阅读相关 phase 下的 `debug.md`
- 如涉及测试，阅读相关 phase 下的 `test.md`

### 工作原则

- 不要重复实现已有功能。
- 修改前先搜索现有类、注册器、工具方法、资源路径和已记录的历史改动。
- 优先复用已有抽象，不要创建功能重复的新系统。
- 每次代码实际落地后，应使用 `columbina-phase-complete [ID]` 记录改动。
- 每次调试或测试闭环后，应使用 `columbina-debug-test-phase [ID]` 记录测试和 bug 信息。

### 文档语言

Columbina 文档默认使用中文，代码标识符、类名、文件路径、命令和错误文本保持原样。

### 历史追溯

旧改动不写入 `AGENTS.md`，统一到 `.columbina/CONTEXT.md` 和 `.columbina/phase/` 中追溯。
