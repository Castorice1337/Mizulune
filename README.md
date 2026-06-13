# 🌸 Mizulune Client

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](file:///d:/OpenZen-master/LICENSE)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)](https://www.minecraft.net/)
[![Forge Version](https://img.shields.io/badge/Forge-47.4.20-orange.svg)](https://files.minecraftforge.net/)
[![Target JDK](https://img.shields.io/badge/JDK-17-red.svg)](https://adoptium.net/)

Mizulune Client is a hybrid Minecraft Forge client framework featuring a **Java Mod layer**, a **Java Agent bytecode transformer**, and a **Windows Native Qt6 Injector & DLL loader**.

---

## 📌 Features & Core Architecture

Mizulune is divided into two highly coupled subcomponents:

```
                            ┌────────────────────────┐
                            │    OpenZenLoader.exe   │  (Qt6 UI / Process scanner)
                            └───────────┬────────────┘
                                        │ (Manual Maps / Injects)
                                        ▼
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ Target Process: Minecraft.exe (JVM)                                      │
  │                                                                          │
  │   ┌──────────────────────┐              ┌───────────────────────────┐    │
  │   │     OpenZen.dll      ├─────────────►│    GameLoaderBridge       │    │
  │   │  (JNI Bootstrap VM)  │ (Attach JNI) │ (Injected URLClassLoader) │    │
  │   └──────────────────────┘              └─────────────┬─────────────┘    │
  │                                                       │                  │
  │                                                       ▼ (2-Layer Obf)    │
  │                                         ┌───────────────────────────┐    │
  │                                         │      Mizulune Classes     │    │
  │                                         │ (Defined on GameLoader CL)│    │
  │                                         └─────────────┬─────────────┘    │
  │                                                       │                  │
  │                                                       ▼ (Retransforms)   │
  │                                         ┌───────────────────────────┐    │
  │                                         │    Minecraft Game Loop    │    │
  │                                         └───────────────────────────┘    │
  └──────────────────────────────────────────────────────────────────────────┘
```

1. **Java Client Mod (`src/main/java`)**
   - **Target**: Minecraft `1.20.1` (Forge `47.4.20`)
   - **Package Root**: `shit.zen` / `asm.patchify`
   - **Mod ID**: `hey`
   - **Display Name**: `Mizulune Client`
   - **Runtime Modiding**: Retransforms targeted Minecraft classes at runtime using an ASM-based patch transformer registry (21+ hot-patches).

2. **Windows Native Loader (`native/`)**
   - **Technology**: C++17, CMake, MSVC, vcpkg, Qt6 Widgets (WebUI backend)
   - **Injects**: Manual-maps the native DLL and JVM instrumentation agent onto target Minecraft instances.
   - **Profile Directory**: `%USERPROFILE%\.mizulune\`
     - Profile config: `loader-profile.properties`
     - Local update files: `updates\`

---

## 🔒 Security & Double-Layer Obfuscation

Mizulune Client implements a robust, state-of-the-art **double-layer obfuscation system** designed to resist anti-cheat blacklists and runtime/offline analysis:

| Layer | Phase | Mechanism | Benefit |
|---|---|---|---|
| **Layer 1: Static** | **Build Time** | `gradlew.bat obfuscateClasses` renames all owned classes (`shit/zen/` & `asm/patchify/`) to random 16-character strings and flattens them into a single random package. | Prevents offline decompilation and generic static signature matching. |
| **Layer 2: Dynamic** | **Injection Time** | [GameLoaderBridge](file:///d:/OpenZen-master/src/main/java/shit/zen/dll/GameLoaderBridge.java) intercepts loading, generating a **fresh, unique set of randomized class names** for every single game session. | Defeats server-side anti-cheats that attempt to detect class name lists. |

---

## 🛠️ Build & Installation

Prerequisites:
- **Java**: JDK 17 (Target Java 17 bytecode)
- **Native Development**: CMake, MSVC (Visual Studio 2022+), and `vcpkg` configured at `VCPKG_ROOT`, `C:/vcpkg`, `D:/vcpkg` or `%USERPROFILE%/vcpkg`.

### 1. Build Java Mod Jar
Compile the main Java Mod:
```powershell
# In project root
.\gradlew.bat jar
```

### 2. Run Local Test Client (with Agent)
Run the client local testbed with Forge and the agent layer attached:
```powershell
.\gradlew.bat runClient0
```
*(Note: Always use `runClient0` rather than standard Gradle tasks as it correctly feeds `-javaagent` to the environment).*

### 3. Build Native Loader & DLL
Injecting through the DLL injection workflow:
```powershell
.\gradlew.bat dll
```
This script runs CMake, resolves dependencies (vcpkg), compiles the bootstrap DLL, stages the obfuscated Java Mod JAR into the PE resources, and bundles everything into:
- **Output Executable**: `build/dist/OpenZenLoader.exe`

---

## ⚠️ Important Developer Notes

- **Modifying Core Packages**: Do not rename `shit.zen`, `asm.patchify`, `ZenClient`, `DllBootstrap`, `GameLoaderBridge`, or `PatchAgent` without updating the build-time remapper in [build.gradle](file:///d:/OpenZen-master/build.gradle) and native bindings in C++ loaders.
- **Generated headers**: Avoid editing `native/dll/src/generated_names.h` manually. It is automatically generated during Gradle build tasks.
- **Dependency Loading**: Native Skiko runtimes (Windows x64 DLLs) are unpacked to the tmp folder at runtime and initialized via `skiko.library.path`.

---

## 📄 License

This project is licensed under the terms of the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](file:///d:/OpenZen-master/LICENSE) file for the full license text.
