# 🌸 Mizulune Client

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](./LICENSE)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)](https://www.minecraft.net/)
[![Forge Version](https://img.shields.io/badge/Forge-47.4.20-orange.svg)](https://files.minecraftforge.net/)
[![Target JDK](https://img.shields.io/badge/JDK-17-red.svg)](https://adoptium.net/)

Mizulune Client is a visual-first Minecraft PvP client framework for Minecraft **1.20.1** / Forge **47.4.20**.

The project is currently built around three cooperating layers:

- a Forge-side Java client module;
- a Java Agent / ASM patch layer for runtime client-side rendering hooks;
- a Windows native loader with a local **HTML / CSS / JavaScript WebUI** rendered through Microsoft Edge WebView2.

The native loader still uses a small Qt Widgets shell for the frameless host window and native event loop, but the launcher interface itself lives in `native/loader/webui` and communicates with C++ through WebView2 web messages.

The intended direction of this fork is HUD rendering, UI infrastructure, input visualization, client-side quality-of-life features, and rendering backend work. It is **not** intended to provide unfair gameplay automation, server-side rule bypasses, or hostile behavior against other players.

---

## 📌 Current Architecture

```text
                            ┌────────────────────────┐
                            │   MizuluneLoader.exe   │  (native WebUI host / process selector)
                            └───────────┬────────────┘
                                        │
                                        ▼
                    ┌───────────────────────────────────────┐
                    │ HTML / CSS / JS WebUI                  │
                    │ native/loader/webui via WebView2       │
                    └───────────────────┬───────────────────┘
                                        │ WebView2 messages
                                        ▼
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ Native Loader Backend                                                    │
  │                                                                          │
  │   ┌──────────────────────┐              ┌───────────────────────────┐    │
  │   │ Minecraft Process    │◄─────────────┤ Process scanner / updater │    │
  │   │ selector             │              │ profile store / injector  │    │
  │   └──────────────────────┘              └─────────────┬─────────────┘    │
  │                                                       │                  │
  │                                                       ▼                  │
  │                                         ┌───────────────────────────┐    │
  │                                         │      Mizulune DLL         │    │
  │                                         │   JNI / JVMTI bootstrap   │    │
  │                                         └─────────────┬─────────────┘    │
  └───────────────────────────────────────────────────────┼──────────────────┘
                                                          │
                                                          ▼
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ Target Process: Minecraft.exe (JVM)                                      │
  │                                                                          │
  │                                         ┌───────────────────────────┐    │
  │                                         │    GameLoaderBridge       │    │
  │                                         │ (Injected class bridge)   │    │
  │                                         └─────────────┬─────────────┘    │
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
- WebUI root: [`native/loader/webui`](./native/loader/webui)
- Native shell: Qt Widgets frameless host window + Microsoft Edge WebView2 controller
- UI stack: local `index.html`, `styles.css`, `app.js`, and packaged WebUI resources
- Toolchain: C++17, CMake, MSVC, vcpkg, Qt6 Widgets, WebView2 SDK
- Runtime requirement: Microsoft Edge WebView2 Runtime
- Runtime profile directory: `%USERPROFILE%\.mizulune\`
- Profile config: `loader-profile.properties`
- Local update/cache directory: `updates\`

The loader opens the WebUI, exchanges events through WebView2 JSON messages, and provides three launch paths: Fantnel direct launch, Fantnel loopback proxy, and the existing in-memory JVM injection path. Fantnel runs in an isolated .NET named-pipe sidecar and the loader asks for confirmation before injecting a directly launched Java process.

The native build stages the Java jar, embeds the DLL into the loader executable, packages WebUI assets, and writes the distributable loader into `build/dist`.

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

### Dual-runtime build entry points

The root ForgeGradle project remains the Forge/Patchify owner. Fabric is built
through two isolated Java 17/Loom subprojects so Forge/ASM and DLL builds do not
acquire Fabric or ViaVersion dependencies.

```powershell
# Forge/Patchify Java build
.\gradlew.bat buildForgeAsm

# Forge/ASM DLL + FantNEL distribution
.\gradlew.bat buildForgeAsmDll

# Mizulune Fabric 1.20.1 + ViaFabricPlus OpenZen 766 + FantNEL Host
.\gradlew.bat fabricmodbuild

# All Java/mod distributions
.\gradlew.bat buildAll
```

`fabricmodbuild` stages the two required Fabric jars and the frozen FantNEL
sidecar under `build/mod-dist/fabric/`. `viafabricplus-2.8.7+openzen.766.jar`
is the complete ViaFabricPlus 1.20.1 source build: it retains the original
multiplayer version selector, settings, legacy/Bedrock support and gameplay
Mixins, while extending the version table and ViaVersion stack with Minecraft
1.20.5/1.20.6 protocol `766`.

For a development launch, use:

```powershell
.\gradlew.bat runFabricClient
```

The title screen exposes a vanilla-component `FantNEL` flow for account login,
captcha, server/role selection, loopback proxy startup, protocol selection and
connection. The protocol override is scoped to that proxy endpoint and is
cleared when the proxy or Minecraft connection closes.

### Prerequisites

- **Java**: JDK 17
- **Minecraft / Forge**: Minecraft `1.20.1`, Forge `47.4.20`
- **Native toolchain**: CMake, MSVC from Visual Studio 2022 or newer, and `vcpkg`
- **Native dependencies**: Qt6 Widgets and Microsoft Edge WebView2 SDK through vcpkg
- **Fantnel backend build**: .NET SDK 10 (the packaged sidecar is self-contained)
- **Runtime dependency**: Microsoft Edge WebView2 Runtime installed on Windows
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

### 3. Build the Native WebUI Loader

```powershell
.\gradlew.bat dll
```

This task stages the obfuscated Java jar, runs CMake, resolves native dependencies through vcpkg, compiles the DLL/loader projects, embeds the DLL, packages the WebUI assets, and writes the base loader into:

```text
build/dist/MizuluneLoader.exe
```

### 4. Build the Loader with Fantnel

```powershell
.\gradlew.bat dllWithFantnel
```

The Fantnel distribution is written to `build/dist`. `MizuluneLoader.exe` stages
`fantnel/Mizulune.FantnelHost.exe` and its locked resources into
`%USERPROFILE%\.mizulune\backends\fantnel`, then communicates over a
current-user-only named pipe. Use `-PfantnelDistDir=<path>` only for an intentional
secondary Fantnel copy, or `-PnativeDistDir=<path>` when a running Loader locks the
default `build/dist` and a complete fresh package must be staged elsewhere.

For a portable ID114 release, provide the verified, redistribution-authorized
MaxHook build input and run the distribution verifier:

```powershell
$env:MAXHOOK_DLL = '<path-to-MaxHook.dll>'
.\gradlew.bat dllWithFantnel verifyDistributability
```

Only SHA-256 `982D8223CF8DA9584D67B1A7A24E5B2515DA22BA72EEBBAF813A353DA14F956A`
is accepted. The file is packaged as `build/dist/maxhook/MaxHook.dll`; on launch it
is atomically staged to `%USERPROFILE%\.mizulune\runtime\maxhook\MaxHook.dll`.
The protocol verifies the hash again before loading it. When an official install
contains `<installRoot>/native/MaxHook.dll`, that canonical path is always preferred
so the already-hooked official module is reused; the staged copy is only the
no-official-install fallback. No machine-specific source path is embedded in code
or in the package.

The pinned Eclipse Temurin 17.0.2+8 JDK is restored inside the primary package at
`build/dist/runtime/jdk17`. Supply the release input when building:

```powershell
.\gradlew.bat dllWithFantnel verifyDistributability `
    -PmaxHookJavaHome='<Temurin-17.0.2+8-JDK>'
```

Its manifest pins every file, `bin/server/jvm.dll`, and the required MaxHook hash.
The actual Minecraft game process must be launched by
`build/dist/runtime/jdk17/bin/java.exe`; the launcher brand is irrelevant, and
injecting the Loader after startup cannot replace an already-running JVM. The source may also be
supplied through `MAXHOOK_JAVA_HOME` or an exact `JAVA_HOME`; there is no
developer-machine path fallback. `packageMaxHookRuntimeZip` remains available only
as an optional standalone mirror of the same bundled runtime.

The legacy OpenSDK fallback remains available as `.\gradlew.bat dllWithOpenSDK` until both Fantnel direct-launch and proxy paths pass live validation.

The BJD proxy preset requires a Minecraft 1.20.1 client with ViaForge configured to advertise the Minecraft 1.20.5/1.20.6 wire protocol (`766`). The proxy binds only to loopback and displays the selected local port in the launcher.

---

## ⚠️ Developer Notes

- The launcher UI is WebUI-first. Edit `native/loader/webui/index.html`, `styles.css`, and `app.js` for interface work; edit the C++ loader only for native windowing, WebView2 bridge, process scanning, update, profile, or injection behavior.
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

## Repository workflow

日常小更新进入 `Mizulune-Private`，大版本从 private 的指定 `master` 提交快进发布到 public。协议实现和测试属于公开共享源码。完整流程见 [`docs/repository-workflow.md`](./docs/repository-workflow.md)。
