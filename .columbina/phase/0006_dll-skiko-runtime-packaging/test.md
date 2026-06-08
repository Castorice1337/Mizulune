# Test Record: Phase 0006

## 测试状态

WAITING_USER_PASS

## 测试目标

验证 DLL 注入路径所需的 Skiko runtime 依赖已进入构建产物，并且 Java 编译、Forge reobf、自定义 obfuscation 和 native jar staging 链路不被破坏。

## 测试步骤

- 运行 `.\gradlew.bat jar`。
- 检查 `build/libs/hey-1.0.jar` 中是否包含 `openzen/dll-libs/*.jar`。
- 运行 `git diff --check -- build.gradle src\main\java\shit\zen\dll\GameLoaderBridge.java`。
- 运行 `.\gradlew.bat stageNativeJar`。
- 检查 `native/zen.jar` 中是否包含 `openzen/dll-libs/*.jar`。
- 运行 `.\gradlew.bat dll`。

## 期望结果

- `compileJava`、`reobfJar`、`obfuscateClasses` 成功。
- `build/libs/hey-1.0.jar` 和 `native/zen.jar` 都包含 Skiko/Kotlin runtime dependency jar。
- `GameLoaderBridge` 编译通过。
- `OpenZen.dll` 和 `OpenZenLoader.exe` 构建成功，最终 loader 进入 `build/dist/`。
- 后续完整 DLL 注入时，日志应出现 `DLL runtime dependencies prepared`，并显示 `skiaVisible=true`、`kotlinVisible=true`，`skiko.library.path` 指向抽取目录。

## 实际结果

- `.\gradlew.bat jar`：PASS。
- `build/libs/hey-1.0.jar` 已包含 `annotations-23.0.0.jar`、`jbr-api-1.5.0.jar`、`kotlin-stdlib-2.3.20.jar`、`kotlinx-coroutines-core-jvm-1.8.0.jar`、`skiko-awt-0.148.1.jar`、`skiko-awt-runtime-windows-x64-0.148.1.jar`。
- `git diff --check -- build.gradle src\main\java\shit\zen\dll\GameLoaderBridge.java`：PASS，仅有 CRLF 工作区提示。
- `.\gradlew.bat stageNativeJar`：PASS。
- `native/zen.jar` 已包含上述 `openzen/dll-libs/*.jar`。
- `.\gradlew.bat dll`：PASS，已生成 `build/dist/OpenZenLoader.exe`。
- 未运行实际 DLL 注入测试。

## 用户确认

- 结果：WAITING
- 用户输入：
- 确认时间：
