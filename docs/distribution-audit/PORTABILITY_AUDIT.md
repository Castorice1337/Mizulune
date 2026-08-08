# Mizulune 可分发性审计

## 结论

Loader/Fantnel 主包现已采用精确文件合同，可从任意目录运行，不再携带开发机路径、历史 ZIP 或旧 staging 文件。MaxHook 是固定版本公共二进制，不是机器唯一识别数据；发行构建将其与固定 Temurin 17.0.2+8 JDK 一并放入主包。Loader 会原子 staging MaxHook，Java 在调用前再次校验 DLL/JVM SHA-256。

自动化入口：

```powershell
.\gradlew.bat verifyDistributability --no-parallel
```

该任务会执行源码路径扫描、精确主包校验、JAR 依赖校验、ZIP entry 安全检查，并把主包复制到随机 GUID、带空格的多层目录后运行 Fantnel `--health` 与 `--console-health`。

## 分发边界

| 组件 | 主包状态 | 运行时来源 |
|---|---|---|
| `MizuluneLoader.exe` | 已包含 | 主包根目录；静态 Qt，内嵌 `Mizulune.dll` 与协议 JAR |
| Fantnel Host | 已包含 | self-contained `fantnel/Mizulune.FantnelHost.exe` |
| Fantnel 7-Zip/配置/lock | 已包含 | `fantnel/resources/`，Gradle 按 SHA-256 lock 校验 |
| MaxHook | 已包含于 portable release | `maxhook/MaxHook.dll`，固定 SHA-256；Loader staging 到当前用户目录 |
| Java runtime libraries | 已包含 | 协议 JAR 的 `openzen/dll-libs/` 内固定 9 个 JAR |
| MaxHook JVM | 已包含 | `runtime/jdk17/`；完整逐文件 manifest，独立 ZIP 仅作为可选镜像 |
| Microsoft Edge WebView2 Runtime | 不进入主包 | Windows 系统运行时；Loader 的唯一额外系统级运行依赖 |
| 官方 ID1 环境 evidence | 不伪造、不内嵌 | 当前或显式配置的游戏树中 `mods/native/libraries/logs/versions` |

不安装网易启动器本身不再阻断 MaxHook：portable release 包含统一副本。实际官方 Minecraft JVM 已从 `<installRoot>/native/MaxHook.dll` 加载并接管时，协议优先复用该 canonical 模块，避免第二份 callback map；只有官方 native 不存在时才使用显式路径或 Loader staging 副本。ID1 仍需要与实际客户端一致的官方游戏文件和 Forge loading evidence，不能用任意本地 mod 列表替代。

## 固定门禁

- MaxHook SHA-256：`982D8223CF8DA9584D67B1A7A24E5B2515DA22BA72EEBBAF813A353DA14F956A`
- Temurin 17.0.2+8 `jvm.dll` SHA-256：`46EB16C248CEC10CDB639E6E97F31F5817ED128E86230AB85715D063DDFCBB47`
- MaxHook 查找顺序：官方 `<installRoot>/native/MaxHook.dll` → `mizulune.heypixel.maxHookPath` / `MIZULUNE_HEYPIXEL_MAXHOOK_PATH` → Loader staging。
- 任一候选都必须是普通文件并命中固定哈希；路径、发行版名称或“同为 Java 17”不能替代字节身份。

## 已消除的不可迁移项

- `Protocol` 新配置默认路径改为空值，使用现有自动发现、配置、system property 或环境变量合同。
- Fabric 1.21.4 adapter 不再探测固定 `D:` 目录，只从传入 game directory 和显式配置推导。
- Gradle 不再回退开发机 `maxHookJavaHome`，JVM 任务必须接收显式 property/env。
- `packageMaxHookRuntime` 在 `build/runtime-dist/jdk17` 生成 canonical staging 与逐文件 manifest，`packageDist` 再将其复制到主包 `runtime/jdk17`。
- `packageDist` 每次先精确清理 `build/dist`，再恢复固定 JDK，杜绝旧 JVM、历史 ZIP 和手工文件污染。
- Java WebUI 删除 jQuery、Tailwind CDN 与 Google Fonts，改用原生 `fetch` 和系统字体。
- `rootProject.name` 固定为 `Mizulune`，JAR manifest 不再泄露工作区目录名。

## 仍然存在的外部连接

这些是功能网络边界，不是缺失的本地 DLL/JAR：

- Fantnel 登录、认证和代理目标服务：协议运行所必需。
- GitHub release 更新检查：Loader 启动非必需，失败不会影响本地注入与协议包。
- Mizulune music API：音乐功能可选，与 HeyPixel 协议无关。

Java WebUI 本身不再在运行时请求 CDN 资源。

## 依赖安全债务

本轮 `.NET` 构建仍报告 NuGet audit 告警：

- `OpenTl.Netty.Socks 1.0.2` 传递引入 `log4net 2.0.8`，出现 `NU1904/NU1902`。
- Fantnel `Nirvana.Development` 直接使用 `MessagePack 3.1.6`，出现多项 `NU1903/NU1902`。

这些程序集会进入 self-contained Fantnel Host，因此不是“只在开发机存在”的依赖。当前 Host 保持冻结上游语义，本轮没有在可分发性修复中盲升序列化/网络依赖；发布前应另开依赖升级与协议回归阶段处理，不能把 portable PASS 等同于依赖安全 PASS。

## 本轮自动化结果

- 生产/构建文本扫描：598 文件，0 个机器路径或远程 WebUI 依赖。
- 主包：517 个精确文件，MaxHook 与固定 JDK 均已包含，非 canonical JVM/ZIP 污染为 0。
- 协议 JAR：9 个嵌入 runtime JAR，1051 个 ZIP entry 全部通过路径与重复项检查。
- 随机目录重定位：517/517 文件 SHA-256 一致，Fantnel 两个健康入口通过。
- 固定 JDK：494/494 manifest 文件校验通过，runtime tree SHA-256 为 `85421BB02607B3DA0322AFAB31F77D99B4344A346911F99ECFBDACDC4ED68101`。

机器可读结果位于：

- `build/dist/mizulune-distribution-manifest.json`
- `build/reports/distributability/full.json`
- `build/dist/runtime/jdk17/maxhook-java-runtime-manifest.json`
- `build/runtime-dist/jdk17/maxhook-java-runtime-manifest.json`（构建 staging）
