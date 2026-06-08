# Mizulune Client

Mizulune Client is a Minecraft Forge `1.20.1` client project with a Java mod, a Java-agent patch layer, and a Windows native injector.

Current version: `1.0`

## Overview

This repository contains two coupled parts:

- `src/main/java`: the Forge client mod, using `shit.zen` as the main package root.
- `native/`: the Windows DLL and Qt Widgets loader used to inject the built client into a target Minecraft JVM.

The Forge mod id is `hey`, and the display name is `Mizulune Client`.

## Runtime Targets

- Minecraft: `1.20.1`
- Forge: `47.4.20`
- Java target: `17`
- Gradle wrapper: `8.8`
- Native loader: Windows, C++17, CMake, MSVC, vcpkg, Qt6 Widgets

## Build

Use PowerShell on Windows.

Build the mod jar:

```powershell
.\gradlew.bat jar
```

Run the local Forge client with the Java agent enabled:

```powershell
.\gradlew.bat runClient0
```

Build the native DLL and loader:

```powershell
.\gradlew.bat dll
```

The native build produces:

- `build/dist/OpenZenLoader.exe`
- an embedded `OpenZen.dll`
- an embedded obfuscated client jar

Native prerequisites:

- JDK 17
- CMake
- MSVC / Visual Studio 2019+
- vcpkg at `VCPKG_ROOT`, `C:/vcpkg`, `D:/vcpkg`, or `%USERPROFILE%/vcpkg`

## Loader Features

The Windows loader scans local Java processes, filters likely Minecraft windows, and injects the embedded DLL into the selected process.

Backend services are available for the redesigned launcher UI:

- Profile storage: `%USERPROFILE%\.mizulune\loader-profile.properties`
- Internal display name: `displayName`
- Update downloads: `%USERPROFILE%\.mizulune\updates\`
- Latest release lookup: GitHub latest Release for this repository

The Java client reads `displayName` from the loader profile during startup. This name is only used as a Mizulune internal display name; it does not authenticate or spoof a Minecraft account.

## Announcements And Updates

Loader announcements are designed to come from the body text of the latest GitHub Release.

When publishing a new public build:

1. Create a GitHub Release.
2. Put the user-facing announcement in the Release notes/body.
3. Attach a loader asset named like:

```text
OpenZenLoader-<sha>.exe
```

The loader update backend compares that asset revision with the current `OPENZEN_BUILD_REVISION` value and can download the newer loader into `.mizulune\updates\`.

The loader does not overwrite the currently running executable.

## Release Notes

GitHub Actions can build these artifacts:

- `OpenZenLoader-<sha>.exe`
- `OpenZen-<sha>.jar`
- `OpenZen-<sha>-mapping.txt`

The class-name obfuscation step generates fresh class names on every build. If you use a prebuilt release, everyone downloads the same obfuscated class names for that release. For unique class names, build from source yourself.

`OpenZen-<sha>-mapping.txt` is required to decode stack traces from a specific build.

## Important Project Notes

- Use `runClient0`, not plain `runClient`, when testing patch/runtime behavior. `runClient0` starts the jar as `-javaagent`.
- Do not hand-edit generated outputs such as `build/`, `native/build/`, `native/zen.jar`, or `native/dll/src/generated_names.h`.
- Do not rename `shit.zen`, `asm.patchify`, `ZenClient`, `DllBootstrap`, `GameLoaderBridge`, or `PatchAgent` without updating the obfuscation and native bridge logic.

