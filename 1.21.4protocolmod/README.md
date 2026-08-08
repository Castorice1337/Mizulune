# Mizulune HeyPixel Protocol 1.21.4

独立的 Fabric 客户端 Mod，用于在 Mizulune/Fantnel 已开启代理后，通过当前 Minecraft 连接收发 HeyPixel 协议。Mod 不创建旁路 socket，也不复制 Fantnel 的登录、认证、进服或代理逻辑。

## 环境要求

- Minecraft 1.21.4
- Java 21
- Fabric Loader 0.19.3 或更高兼容版本
- Fabric API 0.119.4+1.21.4
- Mizulune/Fantnel 生成的有效 v2 签名 session snapshot

## 构建与安装

```powershell
.\gradlew.bat clean build --no-parallel
```

安装 `build/libs/` 下不带 `-sources` 的 remapped JAR，并同时安装对应版本的 Fabric API。

## 使用顺序

1. 通过 Mizulune/Fantnel 登录并开启 HeyPixel 代理。
2. 确认代理侧已生成 `%USERPROFILE%\.mizulune\protocol-session.json` 和配套 HMAC key。
3. 启动 Fabric 1.21.4 客户端，并连接 Mizulune/Fantnel 给出的 loopback 地址。
4. Mod 只有在连接为 loopback、snapshot HMAC/有效期/source 全部有效，且 snapshot 中的真实目标服命中白名单时才会发送协议。

仅连接 `127.0.0.1` 不会被当成可信代理；所有 C2S packet 始终经当前 Minecraft `Connection` 发往 Fantnel transport。

## 配置

首次启动会创建：

```text
%USERPROFILE%\.mizulune\heypixel-protocol-1.21.4.json
```

默认配置开启协议与 live send，目标服白名单为 `pc.bjdmc.net,*.bjdmc.net`。主要字段：

- `enabled`：总开关。
- `allowLiveSend`：允许通过签名门禁的当前连接发包。
- `traceEnabled`：写入脱敏协议 trace。
- `enabledHosts`：真实目标服白名单，逗号分隔。
- `installRoot`：官方安装根；默认只根据当前传入的游戏目录推导，不探测任何开发机固定路径。
- `instanceDirectory`：HeyPixel 实例目录；外部官方安装建议通过配置、system property 或环境变量明确填写。
- `officialUserDirectory`：ID1 使用的官方 `user.dir` 语义，默认等于官方安装根。
- `officialJavaHome`：ID1 使用的官方 Java runtime；会优先从启动器相邻 `ext` 目录检测 JDK 17，无法可靠检测时必须手动填写。
- `syntheticHwid` / `syntheticHwidProfile`：复用既有 ID1 HWID provider；默认关闭。

可用以下环境变量覆盖路径：

- `MIZULUNE_PROTOCOL_DIRECTORY`
- `MIZULUNE_HEYPIXEL_INSTALL_ROOT`
- `MIZULUNE_HEYPIXEL_INSTANCE_DIR`
- `MIZULUNE_HEYPIXEL_USER_DIR`
- `MIZULUNE_HEYPIXEL_JAVA_HOME`

安装根与实例目录是两个独立语义。ID1 mod evidence 从外部官方安装目录、当前 JAR 和最新完整 Forge loading log 恢复，不使用当前 Fabric JVM 的 mod 列表，也不把 Fabric Java 21 的 `user.dir` / `java.home` 冒充官方画像。ID1 `UserId` 来自已通过 HMAC 校验的 Fantnel v2 session，并按官方 signed-long 形状 fail closed。

## 已接通协议

- C2S ID1 initial SPRINT 与 S2C ID101 challenge response。
- C2S ID2 固定 5000 ms 心跳。
- C2S ID3 CPS change telemetry。
- C2S ID5 use-block telemetry。
- 独立 ready opcode 12。
- S2C canonical decoder/state，包括 ID100、101、103–121 中当前 registry 已注册项。
- S2C ID114 plaintext official-prefix decode、client logical work 与 `SyncTokenMetadata` 生命周期。

UI、时装、声音、资源与 manager 类 packet 仅 decode/cache，不执行官方副作用。当前 JAR 没有已证明的 C2S ID114 ACK；未知 native side effect 不在本 Mod 中模拟，也不阻塞已闭合 wire 通讯。

## 隐私与日志

协议 trace 位于 `%USERPROFILE%\.mizulune\protocol-trace-1.21.4\`。日志不保存 token、payload、账号、UUID、HWID、玩家名或 endpoint 原文。ID114 只保留长度、不可逆摘要与结构化有效窗 metadata。

## 验证状态

自动化 golden、MessagePack、ID1、session/HMAC、decoder、ID114 logical work 与隐私测试由 `gradlew test` 执行。完整构建通过不等于实服人工 PASS；首次实服连接仍应检查 initial ID1、ID101 response、连续 ID2 和最终写入 trace。

## License

GPL-3.0-only。
