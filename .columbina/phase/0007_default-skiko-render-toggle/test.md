# Test Record: Phase 0007

## 测试状态

PASS

## 测试目标

验证默认 Skiko、游戏内后端切换设置和 DLL 产物重建不破坏编译、reobf、obfuscation 与 native loader packaging。
补充验证 DLL 注入路径的 `mapping.srg` 资源属性名修复后，最终 jar/loader 可重新打包。

## 测试步骤

- 运行 `.\gradlew.bat jar`。
- 运行 `.\gradlew.bat dll`。
- 检查 `native/zen.jar` 仍包含 `openzen/dll-libs/*.jar`。
- 检查 `native/zen.jar` 包含 `mapping.srg`。
- 实际注入目标客户端后，检查目标游戏 `debug.log` 中 `Runtime mapping = SRG (methods=..., fields=...)` 不再为 `0, 0`，且不再出现核心 `Minecraft#tick()V` 未找到导致 client 不构造的问题。

## 期望结果

- `compileJava` 成功。
- `reobfJar` 成功。
- `obfuscateClasses` 成功。
- `OpenZenLoader.exe` 重新生成。
- `Bootstrap` 能通过 `mizulune.resources` 或兼容 `openzen.resources` 找到 DLL 注入路径解压的 `mapping.srg`。
- 游戏内 `Interface` 模块设置中出现 `Render Backend`，默认 `Skiko`，可切换 `Legacy`。

## 实际结果

- `.\gradlew.bat jar`：首次因测试 Java 进程占用 `build\libs\hey-1.0.jar` 失败；结束疑似测试客户端进程后重跑 PASS。
- `.\gradlew.bat dll`：PASS，已重新生成 `build/dist/OpenZenLoader.exe`。
- `native/zen.jar` 包含 `mapping.srg`。
- `native/zen.jar` 仍包含 `openzen/dll-libs/annotations-23.0.0.jar`、`jbr-api-1.5.0.jar`、`kotlin-stdlib-2.3.20.jar`、`kotlinx-coroutines-core-jvm-1.8.0.jar`、`skiko-awt-0.148.1.jar`、`skiko-awt-runtime-windows-x64-0.148.1.jar`。
- 修复后 DLL 注入实测：用户确认完成。
- 游戏内默认 Skiko 与 `Interface -> Render Backend` 切换：用户确认完成。

## 用户确认

- 结果：PASS
- 用户输入：完成
- 确认时间：2026-06-08
