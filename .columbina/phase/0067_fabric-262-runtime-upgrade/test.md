# Test Record: Phase 0067

## 测试状态

PASS

## 测试目标

验证独立 Fabric Mod 已在 Minecraft 26.2 / Java 25 下完成构建和客户端启动，Sodium/Iris/ViaFabricPlus 能共同加载，原 1.20.1 功能与 Skiko/SKSL 渲染算法通过 adapter 移植后可用，并确认根 Forge/ASM 1.20.1 没有回归。

## 自动化结果

| 检查 | 结果 |
|---|---|
| `fabricmod\gradlew.bat verifyFabricRuntimeMods stageFabricRuntimeMods --no-parallel` | PASS；Sodium/Iris/VFP 固定工件 SHA-512 匹配并完成并列暂存 |
| `fabricmod\gradlew.bat compileJava processResources --no-parallel --console=plain` | PASS |
| `fabricmod\gradlew.bat build --no-parallel --console=plain` | PASS；7 tasks，约 23 秒 |
| Fabric 成品 | PASS；`fabricmod/build/libs/mizulune-fabric-1.2+mc26.2.jar`，56,989,140 bytes |
| `fabricmod\gradlew.bat runClient --no-parallel --console=plain` | PASS；加载 96 个 Mod，进入单人世界并正常退出 |
| runtime 组合 | PASS；Fabric Loader 0.19.3、Sodium 0.9.1+mc26.2、Iris 1.11.2+mc26.2、ViaFabricPlus 4.6.1 同时加载 |
| `DualRuntimeMixinCoverageTest` | PASS；26.2 直接 render/packet boundaries 已显式登记 |
| `gradlew.bat build --no-parallel --console=plain` | PASS；根 Forge/ASM 1.20.1 全量构建、499 tests、635 source portability files、SyncToken ABI、1026 个混淆类均通过 |
| `git diff --check` | PASS；仅有工作区 CRLF 转换提示，无 whitespace error |

## 客户端与人工回归

| 场景 | 结果 |
|---|---|
| FantNEL 页面返回主界面 | PASS；screen lifecycle 恢复 |
| ClickGUI 打开、关闭和交互 | PASS；不再卡死、空白或污染世界颜色状态 |
| 文字、自定义字体和普通纹理 | PASS |
| 圆角矩形、UI 背景、ModuleList 连续背景 | PASS |
| scissor/裁剪 | PASS；ClickGUI 列内容不再穿透容器 |
| blur、liquid glass、glow | PASS；复用原 1.20.1 Skiko/SKSL 算法，不再以灰雾或浅色矩形代替 |
| Notification icon / HUD icon / block item icon | PASS |
| Scaffold 进度条与 HUD 方块 | PASS |
| Scaffold 3D 放置预览 | PASS；延迟提交状态冻结后恢复半透明目标色，不再成为白块 |
| 世界渲染状态 | PASS；未再出现整屏红色、过曝、神秘背景或 framebuffer/state 泄漏 |

## 日志判定

- 无 Mixin apply error。
- 无 `Failed to replay deferred Skiko`。
- 无本阶段代码导致的客户端崩溃。
- 日志中的离线账号/Profile/Realms HTTP 401 属于本地未登录环境，不影响 Mod、世界或渲染验收。

## 上一轮用户确认：UI/GPU 渲染

- 结果：PASS
- 确认人：项目用户
- 确认时间：2026-08-08
- 原文：`ok，渲染模块问题基本修复，PASS`

## Post-PASS 回归：本地玩家头身旋转

### 用户报告

- 结果：FAIL
- 时间：2026-08-08
- 原文：`现在还有一个bug，转头太猎奇了，转身不转头，scaffold和killaura都是这样`

### 自动化结果

| 检查 | 结果 |
|---|---|
| `fabricmod\gradlew.bat compileJava --no-parallel --console=plain` | PASS |
| `fabricmod\gradlew.bat build --no-parallel --console=plain` | PASS；7 tasks |
| `gradlew.bat test --tests shit.zen.patch.DualRuntimeMixinCoverageTest --rerun-tasks --no-parallel --console=plain` | PASS；确认 26.2 同时保留原 head-yaw 与 pitch hook |
| `fabricmod\gradlew.bat runClient --no-parallel --console=plain` | PASS；96 个 Mod 加载、Mixin apply 成功、进入单人世界并正常退出 |
| 运行日志 | PASS；无 Mixin apply error 或客户端崩溃；离线账号 HTTP 401 不影响本项 |
| `git diff --check` | PASS；仅 CRLF 转换提示 |

### 人工复测步骤

1. 进入单人世界并切换第三人称，开启 Scaffold；移动和搭建时观察本地玩家头部是否与 silent yaw/pitch 平滑同步，不能出现身体转向而头仍朝相机方向。
2. 保持第三人称，关闭 Scaffold、开启 KillAura 并放置目标；攻击和切换目标时观察头身旋转，确认没有反向扭头、头部冻结或突跳。
3. 两项完成后关闭模块，确认头部恢复 vanilla 相机方向，身体回正过程没有残留旋转。

### 预期结果

- Scaffold 与 KillAura 都使用原 `RotationAnimationEvent` 插值驱动 26.2 render-state 的 head yaw。
- body yaw、head-relative yaw 和 pitch 连续一致；模块关闭后正常 reset。

### 本轮用户确认

- 结果：PASS
- 确认人：项目用户
- 确认时间：2026-08-08
- 原文：`可以了，还有bug，oldhitting和fullbright无法使用`
- 备注：`可以了` 确认上一项头身旋转恢复；后半句开启下一轮独立回归。

## Post-PASS 回归：OldHitting 与 FullBright

### 用户报告

- 结果：FAIL
- 时间：2026-08-08
- 原文：`还有bug，oldhitting和fullbright无法使用`

### 自动化结果

| 检查 | 结果 |
|---|---|
| `fabricmod\gradlew.bat compileJava --no-parallel --console=plain` | PASS |
| `gradlew.bat test --tests shit.zen.patch.DualRuntimeMixinCoverageTest --rerun-tasks --no-parallel --console=plain` | PASS；锁定 26.2 held-item submit 和 lightmap direct boundaries |
| `fabricmod\gradlew.bat runClient --no-parallel --console=plain` | PASS；96 个 Mod 加载、进入世界、无 Mixin apply error、正常 `Stopping!` |
| `fabricmod\gradlew.bat build --no-parallel --console=plain` | PASS；7 tasks |
| `gradlew.bat build --no-parallel --console=plain` | PASS；根 1.20.1 tests、635 source portability files、SyncToken ABI、1026 个混淆类通过 |

### 人工复测

| 场景 | 预期 | 结果 |
|---|---|---|
| OldHitting，主手剑 + 使用键 | 原 Vanilla/Leaked/Slide 变换接管第一人称物品 | PASS |
| OldHitting，KillAura fake autoblock | 攻击期间使用同一原动画和 swing/equip progress | PASS |
| FullBright 0% -> 100% | 26.2 lightmap 按百分比更新 night-vision intensity | PASS |
| FullBright 关闭 | 下一次 lightmap update 恢复 vanilla 光照，无假 effect 残留 | PASS |

### 用户确认

- 结果：PASS
- 确认人：项目用户
- 确认时间：2026-08-08
- 原文：`可以了`

## 非阻塞发布加固

- 后续可扩展多个 Iris shader pack、窗口 resize/全屏切换和 VFP 旧版本服务器矩阵；这些不属于用户为本次升级定义的构建、`runClient` 与渲染回归验收条件。
