# Mizulune Fabric 1.20.1

这是与根 Forge/Patchify 构建隔离的 Fabric Loom 构建。它直接复用根
`src/main/java` 与资源，只排除 Forge、Patchify、DLL 和 ASM 专属包；Fabric
入口与 Mixin 位于本目录。Fabric 使用自己的 Gradle 8.11.1 wrapper 与
Loom 1.9.2，根 Forge 构建继续固定在 Gradle 8.8。

构建入口：

- 根目录 `./gradlew buildForgeAsm`：Forge 1.20.1 + Patchify ASM JAR
- 根目录 `./gradlew buildForgeAsmDll`：原有 `dllWithFantnel` 分发链
- 根目录 `./gradlew buildFabricMod`：Fabric 1.20.1 + Mixin JAR
- 根目录 `./gradlew runFabricClient`：以固定 Sodium 开发运行时启动 Fabric 客户端
- 根目录 `./gradlew buildAll`：同时构建两个 Mod JAR

Fabric 产物会暂存到根 `build/mod-dist/fabric/`。Fabric API 是必需依赖，
Sodium 0.5+ 是推荐依赖；当前首批 Mixin 仅触及 Minecraft 生命周期和网络
边界，刻意避开 Sodium 的渲染实现类。开发 `runClient` 固定加载 Sodium
`mc1.20.1-0.5.13-fabric`，但发布 JAR 不会嵌入或再分发 Sodium。

根任务默认通过 Gradle Toolchain 选择 Java 17。MaxHook 完整验证/发行时可用
`-PfabricJavaHome=<固定 Temurin 17.0.2>` 或环境变量 `FABRIC_JAVA_HOME` 覆盖；
共享 native sink 仍会独立校验 `jvm.dll` SHA-256，错误运行时不会被放行。

MaxHook 不在 Fabric 中复制或改写。共享 `OfficialId114NativeSink` 仍在游戏
JVM 内校验固定 `MaxHook.dll`、固定 `jvm.dll` 和 exact `SyncToken` ABI 后加载；
Fabric/Knot 的 callback map 在完整运行验证前仍视为未知。
