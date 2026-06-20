# 🌸 Mizulune Client

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](./LICENSE)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)](https://www.minecraft.net/)
[![Forge Version](https://img.shields.io/badge/Forge-47.4.20-orange.svg)](https://files.minecraftforge.net/)
[![Target JDK](https://img.shields.io/badge/JDK-17-red.svg)](https://adoptium.net/)

Mizulune Client is a visual-first Minecraft PvP client framework for Minecraft **1.20.1** / Forge **47.4.20**.

The project is currently built around three cooperating layers:

- a Forge-side Java client module;
- a Java Agent / ASM patch layer for runtime client-side rendering hooks;
- a Windows native Qt6 loader and JNI bootstrap path for local development and packaged runtime experiments.

The intended direction of this fork is HUD rendering, UI infrastructure, input visualization, client-side quality-of-life features, and rendering backend work. It is **not** intended to provide unfair gameplay automation, server-side rule bypasses, or hostile behavior against other players.

---

## 📌 Current Architecture

```text
                            ┌────────────────────────┐
                            │   MizuluneLoader.exe   │  (Qt6 UI / process selector)
                            └───────────┬────────────┘
                                        │
                                        ▼
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ Target Process: Minecraft.exe (JVM)                                      │
  │                                                                          │
  │   ┌──────────────────────┐              ┌───────────────────────────┐    │
  │   │    Mizulune DLL      ├─────────────►│    GameLoaderBridge       │    │
  │   │  (JNI / JVMTI path)  │              │ (Injected class bridge)   │    │
  │   └──────────────────────┘              └─────────────┬─────────────┘    │
  │                                                       │                  │
  │                                                       ▼                  │
  │                                         ┌───────────────────────────┐    │
  │                                         │      Mizulune Classes     │    │
  │                                         │ (defined on GameClassLoader)│   │
  │                                         └─────────────┬─────────────┘    │
  │                                                       │                  │
  │                                                       ▼                  │
  │                                         ┌───────────────────────────┐    │
  │                                         │    Minecraft Game Loop    │    │
  │                                         └───────────────────────────┘    │
  └──────────────────────────────────────────────────────────────────────────┘
```

### Java Client Module

- Source root: [`src/main/java`](./src/main/java)
- Target: Minecraft `1.20.1`, Forge `47.4.20`, Java `17`
- Current legacy package roots: `shit.zen` and `asm.patchify`
- Current mod id: `hey`
- Display name: `Mizulune Client`
- Runtime patching: ASM-based patch transformer registry driven by the Java Agent layer

### Java Agent / Patch Layer

The Forge development run uses the built mod jar as a Java agent so `PatchAgent` can install hooks before Minecraft classes are fully loaded.

Use the dedicated Gradle task:

```powershell
.\gradlew.bat runClient0
```

`runClient0` builds the jar first, extracts the Skiko native runtime for the dev environment, and then launches the Forge client with the required `-javaagent` argument.

### Windows Native Loader

- Source root: [`native/`](./native)
- Toolchain: C++17, CMake, MSVC, vcpkg, Qt6 Widgets / WebUI assets
- Runtime profile directory: `%USERPROFILE%\.mizulune\`
- Profile config: `loader-profile.properties`
- Local update/cache directory: `updates\`

The native path stages the Java jar, builds the DLL/loader projects with CMake, and packages the GUI loader into `build/dist`.

---

## 🧩 Runtime Isolation & Class Relocation

Mizulune currently uses a two-stage class relocation pipeline:

| Layer | Phase | Mechanism | Purpose |
|---|---|---|---|
| Static relocation | Build time | `gradlew.bat obfuscateClasses` rewrites owned classes from `shit/zen/` and `asm/patchify/` into generated names. | Keeps legacy implementation names out of packaged artifacts and reduces accidental namespace/signature collisions. |
| Runtime relocation | Load time | [`GameLoaderBridge`](./src/main/java/shit/zen/dll/GameLoaderBridge.java) defines Mizulune classes on the live game class loader and can remap class names for the current session. | Keeps the injected runtime isolated from normal Forge mod loading and avoids stale class identity conflicts across loader paths. |

This is an engineering isolation mechanism, not a security guarantee. Because relocation makes release artifacts harder to inspect, the source tree should remain the primary audit target. Review the code before running native or injected builds.

---

## 🛠️ Build & Installation

### Prerequisites

- **Java**: JDK 17
- **Minecraft / Forge**: Minecraft `1.20.1`, Forge `47.4.20`
- **Native toolchain**: CMake, MSVC from Visual Studio 2022 or newer, and `vcpkg`
- **vcpkg discovery**: `VCPKG_ROOT`, `C:/vcpkg`, `D:/vcpkg`, or `%USERPROFILE%/vcpkg`

### 1. Build the Java Mod Jar

```powershell
.\gradlew.bat jar
```

### 2. Run the Local Forge Test Client

```powershell
.\gradlew.bat runClient0
```

Always use `runClient0` for the agent-backed development client. The stock Forge run task does not provide the same startup path.

### 3. Build the Native Loader

```powershell
.\gradlew.bat dll
```

This task stages the obfuscated Java jar, runs CMake, resolves native dependencies through vcpkg, compiles the DLL/loader projects, and packages the loader into:

```text
build/dist/MizuluneLoader.exe
```

---

## ⚠️ Developer Notes

- Some internal names are still inherited from OpenZen. In particular, do not rename `shit.zen`, `asm.patchify`, `ZenClient`, `DllBootstrap`, `GameLoaderBridge`, or `PatchAgent` unless the build-time remapper, Java Agent manifest, and native bindings are updated together.
- `native/dll/src/generated_names.h` is generated during Gradle/native build tasks. Do not edit it by hand.
- Skiko Windows x64 runtime files are extracted for development runs and passed through `skiko.library.path`.
- The native path loads code into a live JVM and redefines client classes. Use it only in local environments you control and respect the rules of any server you join.

---

## 🧭 Project Boundary

Mizulune is maintained as a client-side rendering and UI framework. Contributions should stay within local visual features, HUD components, input displays, renderer backends, developer tooling, and transparent quality-of-life behavior.

Please avoid adding features that automate combat, conceal unfair behavior, collect private user data, or intentionally bypass server rules.

---

## 📄 License

This repository includes the **GNU General Public License v3.0**. See [`LICENSE`](./LICENSE) for the full license text.
