# Columbina Context

## 当前项目状态

OpenZen 当前是一个已存在的 Minecraft Forge 1.20.1 client 仓库，Java/Forge mod 与 Windows native loader 耦合构建；本次仅初始化 Columbina 轻量上下文记录。

## 最近重要结论

- Forge entry point 是 `shit.zen.ZenClient`，mod id 是 `hey`。
- Java 源码主包根是 `shit.zen`，patch/agent 相关包是 `asm.patchify`。
- 模块需要继承 `shit.zen.modules.Module` 并在 `ModuleManager.initModules()` 手动注册。
- Event bus 使用反射分发；listener 必须有 `@EventTarget` 且只接收一个 event 参数。
- Patch 类需要在 `ZenClient.registerPatches()` 注册；需要 `runClient0` 的 `-javaagent` 路径验证。
- Native 构建链路是 jar -> `native/zen.jar` -> `OpenZen.dll` -> `OpenZenLoader.exe`。
- `build/`、`.gradle/`、`.idea/`、`run/`、`native/build/`、`native/zen.jar`、`native/dll/src/generated_names.h` 是生成或本地状态。

## 历史 Phase 索引

| ID | 名称 | 状态 | 主要改动 | 关键文件 | 测试状态 |
|---|---|---|---|---|---|
| 0001 | project-init | complete | 初始化 Columbina 工作流 | `AGENTS.md`, `.columbina/INIT.md`, `.columbina/CONTEXT.md` | 未测试 |
| 0002 | git-publication-setup | partial | 初始化本地 Git 仓库并提交公开源码；GitHub repo 创建因 `gh` token 失效暂未完成 | `.gitignore`, `.columbina/phase/0002_git-publication-setup/CONTEXT.md` | 未测试 |
| 0003 | skiko-2d-backend | partial | 增加 Skiko 2D UI/HUD 渲染后端骨架、验证面板和开发期 Skiko native runtime 抽取；修复 Skiko GL 状态污染、PoseStack 映射和 RenderUtil 2D/3D 分流边界 | `Renderer.java`, `RenderBackend.java`, `RenderBackendProbe.java`, `DrawContext.java`, `SkikoBackend.java`, `GlHelper.java`, `RenderUtil.java`, `build.gradle` | PASS |

## 已有核心模块

| 模块 | 相关文件 | 说明 |
|---|---|---|
| Client bootstrap | `src/main/java/shit/zen/ZenClient.java` | 初始化 event bus、manager、rotation handler、cloud assets、patch，并控制 ready gate |
| Module system | `src/main/java/shit/zen/modules`, `src/main/java/shit/zen/manager/ModuleManager.java` | 功能模块基础设施；新增模块需要手动注册 |
| Event system | `src/main/java/shit/zen/event` | reflection-based event bus，使用 `@EventTarget` |
| Settings/config | `src/main/java/shit/zen/settings`, `src/main/java/shit/zen/config` | 模块设置与持久化配置 |
| GUI/HUD | `src/main/java/shit/zen/gui`, `src/main/java/shit/zen/hud`, `src/main/resources/webui` | Click GUI、HUD、Web UI 静态资源 |
| 2D render backend | `src/main/java/shit/zen/render`, `src/main/java/shit/zen/render/backend` | `Renderer`/`DrawContext` 上层 API 保持，新增 legacy 与 Skiko backend 切换 |
| Patch system | `src/main/java/asm/patchify`, `src/main/java/shit/zen/patch` | Java agent patch annotation/transformer 和具体 patch 类 |
| Native DLL | `native/dll` | 注入目标 JVM 并加载嵌入 jar |
| Native loader | `native/loader` | Qt Widgets GUI loader，嵌入并注入 DLL |
| Release workflow | `.github/workflows/build-loader.yml` | Windows CI 构建 jar、loader 和 mapping artifact |

## 不要重复造轮子的内容

- 模块注册、启停和 setting 自动注册逻辑已有 `Module` 与 `ModuleManager`。
- 事件分发已有 `EventBus`、`EventTarget`、`Cancellable` 体系。
- 配置持久化已有 `ModulesConfig`、`ValuesConfig` 和 config manager 相关代码。
- 渲染、游戏、数学、rotation、misc helper 已在 `shit.zen.utils.*` 下存在。
- 2D UI/HUD 渲染后端已有 `RenderBackend`、`LegacyGlBackend`、`SkikoBackend`，不要再创建平行渲染入口。
- GUI setting renderer 已在 `shit.zen.gui.panel.setting` 和 legacy/new click GUI 相关包中存在。
- Patch annotation/agent/transformer 已在 `asm.patchify` 中存在。
- Native 注入、manual map、resource embedding 已在 `native/dll` 和 `native/loader` 中存在。

## 待确认问题

- 当前本地 JDK 17、CMake、MSVC、vcpkg 是否都可用于完整 `.\gradlew.bat dll` 验证：未测试。
- `runClient0` 在当前机器能否成功启动 Minecraft client：未测试。
- Skiko backend 需用 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO` 做游戏内视觉验收：未测试。
- Web UI `http://127.0.0.1:8089` 的当前运行行为：未测试。
- GitHub CLI 需要重新认证；`gh auth status` 显示 `Castorice1337` 的 token invalid。
