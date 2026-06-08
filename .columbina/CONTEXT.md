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
| 0004 | skiko-2d-effects-texture-interop | partial | 增加 Skiko 2D effects/texture 辅助层，迁移 text glow、shadow/元素自身 blur、纯 Skia 背景毛玻璃 blur、普通 ResourceLocation 纹理和玩家头像 Skia 尝试路径，并修复 stencil/FontStore 兼容边界 | `SkikoBackend.java`, `SkikoEffects.java`, `SkikoTextures.java`, `DrawContext.java`, `GlHelper.java`, `TextGlow.java`, `RenderUtil.java`, `StencilHelper.java`, `CustomFont.java` | PASS |
| 0005 | skiko-font-stencil-migration | partial | 迁移 `FontStore`/`CustomFont` 到 Skiko 字体路径，使用 Skia clip 替代 stencil，并完成品牌名称向 Mizulune Client 的全面迁移 | `CustomFont.java`, `SkikoFonts.java`, `RenderBackend.java`, `ZenClient.java`, `webui/index.html` | PASS |
| 0006 | dll-skiko-runtime-packaging | waiting-test | 补齐 DLL 注入路径的 Skiko/Kotlin runtime jar 嵌入、classloader 暴露、Skiko native 文件抽取和 `skiko.library.path` 设置 | `build.gradle`, `GameLoaderBridge.java` | WAITING_USER_PASS |
| 0007 | default-skiko-render-toggle | complete | 将 Skiko 设为默认 2D 渲染后端，在 `Interface` 模块加入游戏内 `Render Backend` 切换设置，并修复 DLL 注入路径因 `mizulune.resources` 属性迁移导致 `mapping.srg` 无法加载的问题 | `BackendType.java`, `Renderer.java`, `Interface.java`, `build.gradle`, `Bootstrap.java`, `GameLoaderBridge.java` | PASS |

## 已有核心模块

| 模块 | 相关文件 | 说明 |
|---|---|---|
| Client bootstrap | `src/main/java/shit/zen/ZenClient.java` | 初始化 event bus、manager、rotation handler、cloud assets、patch，并控制 ready gate |
| Module system | `src/main/java/shit/zen/modules`, `src/main/java/shit/zen/manager/ModuleManager.java` | 功能模块基础设施；新增模块需要手动注册 |
| Event system | `src/main/java/shit/zen/event` | reflection-based event bus，使用 `@EventTarget` |
| Settings/config | `src/main/java/shit/zen/settings`, `src/main/java/shit/zen/config` | 模块设置与持久化配置 |
| GUI/HUD | `src/main/java/shit/zen/gui`, `src/main/java/shit/zen/hud`, `src/main/resources/webui` | Click GUI、HUD、Web UI 静态资源 |
| 2D render backend | `src/main/java/shit/zen/render`, `src/main/java/shit/zen/render/backend` | `Renderer`/`DrawContext` 上层 API 保持，新增 legacy 与 Skiko backend 切换；Skiko effects/texture/font helper 已集中在 backend 包内 |
| Patch system | `src/main/java/asm/patchify`, `src/main/java/shit/zen/patch` | Java agent patch annotation/transformer 和具体 patch 类 |
| Native DLL | `native/dll` | 注入目标 JVM 并加载嵌入 jar；`GameLoaderBridge` 负责把 jar 内资源和 DLL runtime 依赖暴露给游戏进程 |
| Native loader | `native/loader` | Qt Widgets GUI loader，嵌入并注入 DLL |
| Release workflow | `.github/workflows/build-loader.yml` | Windows CI 构建 jar、loader 和 mapping artifact |

## 不要重复造轮子的内容

- 模块注册、启停和 setting 自动注册逻辑已有 `Module` 与 `ModuleManager`。
- 事件分发已有 `EventBus`、`EventTarget`、`Cancellable` 体系。
- 配置持久化已有 `ModulesConfig`、`ValuesConfig` 和 config manager 相关代码。
- 渲染、游戏、数学、rotation、misc helper 已在 `shit.zen.utils.*` 下存在。
- 2D UI/HUD 渲染后端已有 `RenderBackend`、`LegacyGlBackend`、`SkikoBackend`，不要再创建平行渲染入口。
- Skiko 2D effects 和 texture interop 已有 `SkikoEffects`、`SkikoTextures`，不要在 GUI/HUD 层散落 Skia blur、glow、ResourceLocation 解码逻辑。
- Skiko `CustomFont` 字体绘制已有 `SkikoFonts` 和 `RenderBackend.drawCustomFontText(...)`，不要在 GUI/HUD 层新增平行字体绘制入口。
- Skiko 现在是默认 2D 后端；游戏内切换入口是 `Interface -> Render Backend`。
- DLL 注入路径的 Skiko runtime 依赖已集中在 `openzen/dll-libs/` 与 `GameLoaderBridge`，资源目录属性当前为 `mizulune.resources`，并保留 `openzen.resources` 兼容别名；不要另建平行 dependency extraction/classloader 系统。
- GUI setting renderer 已在 `shit.zen.gui.panel.setting` 和 legacy/new click GUI 相关包中存在。
- Patch annotation/agent/transformer 已在 `asm.patchify` 中存在。
- Native 注入、manual map、resource embedding 已在 `native/dll` 和 `native/loader` 中存在。

## 待确认问题

- 当前机器已可完成 `.\gradlew.bat dll`，并重新生成 `build/dist/OpenZenLoader.exe`。
- 修复后 DLL 注入路径、默认 Skiko、游戏内 `Interface -> Render Backend` 切换已由用户确认完成。
- `runClient0` 在当前机器能否成功启动 Minecraft client：未测试。
- Skiko backend 需用 `.\gradlew.bat runClient0 -PopenzenRenderBackend=SKIKO` 做游戏内视觉验收：未测试。
- Web UI `http://127.0.0.1:8089` 的当前运行行为：未测试。
- GitHub CLI 需要重新认证；`gh auth status` 显示 `Castorice1337` 的 token invalid。
