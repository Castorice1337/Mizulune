# Debug Record: Phase 0007

## Debug 类型

quick

## 关联问题

点击注入后没有任何可见反应。

## 症状

- 操作：在 loader 中点击注入，目标 Minecraft 客户端未出现 Mizulune/OpenZen 视觉或 GUI 行为。
- 现象：native 注入日志显示 DLL 已进入目标 JVM，`GameLoaderBridge.load` 正常返回，但游戏内没有 client 启动效果。
- 首个真实异常：目标游戏 `debug.log` 中 `Runtime mapping = SRG (methods=0, fields=0)`，随后大量 patch handler 仍以 Mojang 方法名匹配 SRG runtime class，例如 `Minecraft#tick()V` 未找到。

## 根因分析

品牌迁移后 `GameLoaderBridge` 将资源目录写入 `mizulune.resources`，但 `Bootstrap.openMappingStream()` 仍只读取旧属性 `openzen.resources`。DLL 注入路径已正确把 `mapping.srg` 解压到临时目录，但 `Bootstrap` 找不到该目录，导致 SRG runtime remap 表为空。patch 系统因此在生产 SRG 环境中继续查找 `tick`、`render` 等 Mojang 方法名，核心 tick patch 没有生效，client 不会在下一 tick 构造。

## 证据

| 证据 | 说明 |
|---|---|
| `%TEMP%\openzen.log` | `Agent_OnAttach returned 0`、`GameLoaderBridge.load returned without exception`，证明 native 注入和 Java bridge 已运行。 |
| `D:\MCLDownload\Game\.minecraft\logs\debug.log` | `mapping.srg not found (classpath nor openzen.resources)`，紧接 `Runtime mapping = SRG (methods=0, fields=0)`。 |
| `D:\MCLDownload\Game\.minecraft\logs\debug.log` | 多个 patch warning：`Minecraft#tick()V`、`GameRenderer#render(FJZ)V` 等目标方法不存在。 |
| `src/main/resources/mapping.srg` | 存在 `MD: net/minecraft/client/Minecraft/m_91398_ ()V net/minecraft/client/Minecraft/tick ()V`，映射文件内容本身正常。 |
| `native/zen.jar` | 构建后仍包含 `mapping.srg` 和 `openzen/dll-libs/*.jar`。 |

## 尝试过的方案

| 方案 | 结果 |
|---|---|
| 检查 native 注入日志 | 有效，确认不是 DLL 未进入目标进程。 |
| 检查目标游戏 `debug.log` | 有效，定位到 runtime mappings 为空。 |
| 检查 `mapping.srg` 内容与 jar entry | 有效，确认资源存在，问题在属性名查找链路。 |

## 最终修复

- `GameLoaderBridge` 同时设置 `mizulune.resources` 和兼容别名 `openzen.resources`。
- `Bootstrap` 优先读取 `mizulune.resources`，再回退读取 `openzen.resources`。
- 修正 `mapping.srg not found` 日志，明确输出当前新旧资源属性名。

## 修改文件

| 文件 | 改动 |
|---|---|
| `src/main/java/shit/zen/dll/GameLoaderBridge.java` | 注入 bridge 设置新旧资源目录属性，保证 DLL 解压资源可被 game loader 中的 `Bootstrap` 找到。 |
| `src/main/java/shit/zen/asm/Bootstrap.java` | runtime mapping 加载支持 `mizulune.resources`，并保留 `openzen.resources` 回退。 |

## 回归测试

- `.\gradlew.bat jar`：PASS。
- `.\gradlew.bat dll`：PASS。
- `native/zen.jar` 包含 `mapping.srg`：PASS。
- `native/zen.jar` 包含 `openzen/dll-libs/*.jar`：PASS。
- 实际 DLL 注入后 `Runtime mapping` 非空、client 可见启动：PASS，用户输入“完成”。

## 后续提醒

- 若再次做品牌/属性名迁移，`GameLoaderBridge`、`Bootstrap`、资源读取 helper 需要同步新旧属性兼容。
- 注入无反应时优先看 `%TEMP%\openzen.log` 判断 native/bridge 是否运行，再看目标游戏 `debug.log` 的 `Runtime mapping` 和 patch warning。
