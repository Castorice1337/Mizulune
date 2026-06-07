# Phase 0001: project-init

## 状态

complete

## 本次初始化事实

- 按 `columbina-init` 初始化项目本地 Columbina 轻量工作流。
- 初始化前仓库根目录未发现 `.columbina/`。
- 读取并保留现有 `AGENTS.md` 的 OpenZen 项目约束。
- 识别项目为 Minecraft Forge 1.20.1 client，包含 Java/Forge mod 与 Windows native loader。
- Java 主包根为 `shit.zen`，patch/agent 包为 `asm.patchify`。
- Native 侧包含 `native/dll` 与 `native/loader`，由 CMake、MSVC、vcpkg、Qt6 Widgets 构建。
- 生成 `.columbina/INIT.md`、`.columbina/CONTEXT.md`、`.columbina/phase/0001_project-init/CONTEXT.md`、`test.md`、`debug.md`。
- 在 `AGENTS.md` 追加 Columbina 必读上下文、工作原则、文档语言和历史追溯规则。

## 关键文件

- `AGENTS.md`
- `.columbina/INIT.md`
- `.columbina/CONTEXT.md`
- `.columbina/phase/0001_project-init/CONTEXT.md`
- `.columbina/phase/0001_project-init/test.md`
- `.columbina/phase/0001_project-init/debug.md`

## 已知约束

- Columbina 文档使用中文；代码标识符、类名、路径、命令和错误文本保持原样。
- `AGENTS.md` 只保留长期规则；旧改动和详细历史放入 `.columbina/CONTEXT.md` 与 `.columbina/phase/`。
- 本次初始化不改 Java、C++、Gradle、CMake 或资源行为。
