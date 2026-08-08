# Agent Rules

## Columbina v2 and Release Gates

- Columbina v2 is enabled by `.columbina/workflow.toml`; use the project-local SDK at `tools/columbina/columbina_sdk.py`.
- Before handoff, run `python tools/columbina/columbina_sdk.py check --strict` and `python tools/columbina/columbina_sdk.py sync --check`.
- After landed work, record it with `columbina-phase-complete`; after a debug/test loop, record it with `columbina-debug-test-phase`.
- Every completed step is committed and pushed to the private repository first.
- Public publication is allowed only after the corresponding Columbina phase has an explicit human `PASS`; then run the parity checks and publish the same commit to public.

## 发布与 Push 门禁

- 每完成一个可交付步骤，先提交并 push 到 private 仓库，作为可追溯的中间快照。
- 只有 Columbina 测试明确记录为 PASS 后，才允许将对应内容同步到 public 仓库并 push。
- 未获得 Columbina PASS 时，不得 push public；PASS 记录必须与对应提交或阶段证据关联。

本项目使用 Columbina 轻量工作流记录项目上下文和历史改动。

## 默认回复语言

所有 Agent 回复默认使用**中文**。代码标识符、类名、文件路径、命令保持英文原样。

## 必读上下文

在进行较大代码修改、调试或新增功能前，必须优先阅读：

- `.columbina/INIT.md`
- `.columbina/CONTEXT.md`
- 与当前任务相关的 `.columbina/phase/*/CONTEXT.md`
- 如涉及 bug，阅读相关 phase 下的 `debug.md`
- 如涉及测试，阅读相关 phase 下的 `test.md`

## 工作原则

- 不要重复实现已有功能。
- 修改前先搜索现有类、注册器、工具方法、资源路径和已记录的历史改动。
- 优先复用已有抽象，不要创建功能重复的新系统。
- 每次代码实际落地后，应使用 `columbina-phase-complete [ID]` 记录改动。
- 每次调试或测试闭环后，应使用 `columbina-debug-test-phase [ID]` 记录测试和 bug 信息。
- 代码修改完成后，必须运行 `./gradlew build` 验证编译通过。

## 独立子项目边界

- 根 Forge/Patchify/ASM 继续固定 Minecraft 1.20.1 / Java 17 bytecode；`fabricmod/` 独立目标为 Fabric 26.2 / Java 25。不得为了 Fabric 编译直接批量改写共享 1.20.1 Mojang API 名称。
- `fabricmod/gradle/fabric26-source-compat.gradle` 只维护机械名称迁移并生成 build-time source view；render-state、item component、packet 等语义差异必须进入 Fabric adapter/Mixin，不得放入假实现或空壳兼容类。
- Sodium 0.9.1、Iris 1.11.2 和 ViaFabricPlus 4.6.1 是 Fabric 26.2 外部 runtime Mod：使用 immutable Modrinth ID + SHA-512 校验并并列暂存，不嵌入 Mizulune JAR。旧 `viafabricplusmod/` 只保留 phase 0065 的 1.20.1 历史实现，不再参与 26.2 构建。
- Fabric 26.2 现有 Skiko/raw-GL bridge 只支持 OpenGL backend，Vulkan 必须 fail fast；Fabric/Java 25 禁止启用绑定 Java 17 `jvm.dll` 的 official ID114 native sink，Forge/ASM 路径保持原合同。
- `1.21.4protocolmod/` 是 Fabric 1.21.4 / Java 21 独立协议 Mod，必须使用其自身的 `gradlew.bat` 构建；根项目仍是 Forge 1.20.1 / Java 17。
- HeyPixel canonical 协议语义以主项目和 phase 0055 ledger 为权威；`1.21.4protocolmod/` 只做批准的平台适配，不要创建第三套协议 runtime。

## 文档语言

Columbina 文档默认使用中文，代码标识符、类名、文件路径保持原样。

## 历史追溯

旧改动不写入 AGENTS.md，统一到 `.columbina/CONTEXT.md` 和 `.columbina/phase/` 中追溯。
