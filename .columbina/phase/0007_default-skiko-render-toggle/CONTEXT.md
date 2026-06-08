# Phase 0007: 默认 Skiko 与游戏内渲染后端开关

## 状态

waiting-test

## 本阶段目标

让 Skiko 在正常客户端和 DLL 注入路径中成为默认 2D 渲染后端，并提供游戏内可切换回 legacy OpenGL 的设置，方便实测和回归。

## 实际完成内容

- `BackendType.fromProperty(...)` 默认值从 `OPENGL_LEGACY` 改为 `SKIKO`。
- `BackendType.fromProperty(...)` 兼容 `Skiko`、`Skia`、`Legacy`、`OpenGL`、`OPENGL_LEGACY` 等输入。
- `Renderer` 读取当前 Mizulune 命名下的 `mizulune.render.backend` system property。
- `Renderer.setBackend(...)` 的空值默认改为 `SKIKO`，并避免同一后端重复重建。
- `Interface` 模块新增 `Render Backend` 模式设置，选项为 `Skiko` / `Legacy`，默认 `Skiko`。
- `Render Backend` 设置通过 `Setting.onChanged(...)` 立即调用 `Renderer.setBackend(...)`，配置加载和 ClickGUI 点击后都会立即生效。
- Gradle 开发运行继续接受旧 `-PopenzenRenderBackend=...`，也支持新 `-PmizuluneRenderBackend=...`，最终传入 `-Dmizulune.render.backend=...`。

## 改动文件

| 文件 | 改动说明 |
|---|---|
| `src/main/java/shit/zen/render/backend/BackendType.java` | 默认 Skiko，兼容 legacy/skia 输入别名 |
| `src/main/java/shit/zen/render/Renderer.java` | 读取 `mizulune.render.backend`，`setBackend(...)` 默认 Skiko并避免重复重建 |
| `src/main/java/shit/zen/modules/impl/render/Interface.java` | 新增游戏内 `Render Backend` 设置 |
| `build.gradle` | 兼容新旧 Gradle 后端参数名，传入 `mizulune.render.backend` |

## 新增/修改的核心类

| 类/模块 | 作用 |
|---|---|
| `Interface` | 承载游戏内渲染后端设置 |
| `BackendType` | 解析后端配置字符串并提供默认后端 |
| `Renderer` | 实际切换 active/configured 2D 渲染后端 |

## 关键实现决策

- Skiko 成为默认视觉路径；legacy OpenGL 仍作为游戏内回退选项保留。
- 游戏内开关不依赖 `Interface` 模块启用状态，只依赖设置值变化。
- 运行参数使用当前项目命名 `mizulune.render.backend`，同时保留旧 `openzenRenderBackend` / `openzen.render.backend` 输入兼容，减少旧测试命令失效。

## 复用的已有结构

- 复用 `ModeSetting`、`ValuesConfig` 和 ClickGUI setting renderer。
- 复用已有 `Renderer.setBackend(...)` 后端切换入口。
- 复用 `Interface` render 模块作为界面/视觉设置位置。

## 对后续 AI 的提醒

- 不要重新引入 probe 面板作为唯一测试入口；游戏内切换应通过 `Interface -> Render Backend`。
- 如果用户说“默认视觉还是原版”，先检查 `BackendType.fromProperty(...)` 默认值、`Renderer` 读取的 system property 名和 `Interface` 设置是否被配置文件覆盖为 `Legacy`。
- 如果 DLL 注入后默认仍不是 Skiko，确认 loader 里嵌入的 `native/zen.jar` 是否由最新 `.\gradlew.bat dll` 生成。

## 未完成内容

- 未做实际游戏内切换测试。
- 未做 DLL 注入后默认 Skiko 验证。

## 测试状态

WAITING_USER_PASS
