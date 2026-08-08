# Mizulune Fabric 26.2

Fabric 侧已与根目录的 Forge/Patchify 构建隔离：

- 根项目继续使用 Minecraft 1.20.1、Java 17 bytecode 与 ASM/Patchify。
- `fabricmod/` 使用 Minecraft 26.2、Java 25、Fabric Loader 0.19.3、Fabric API 0.156.0+26.2、Loom 1.17.19 和 Gradle 9.5.1。
- Sodium `mc26.2-0.9.1-fabric`、Iris `1.11.2+26.2-fabric`、ViaFabricPlus `4.6.1` 作为独立 runtime mod 加载，不嵌入 Mizulune JAR。
- 三个 runtime mod 的 Modrinth version ID 与 SHA-512 固定在 `gradle.properties`，由 `verifyFabricRuntimeMods` 校验。

## 维护边界

`../src/main/java` 仍是 Forge/ASM 1.20.1 的 canonical 共享源码。Fabric 26.2 的机械名称迁移集中在 `gradle/fabric26-source-compat.gradle`；无法用机械替换表达的行为差异放在 `src/fabric26/java`。

Minecraft 26.2 改为 GUI/world render-state 提取与提交。Fabric 侧使用 `FabricRenderBridge`、`FabricSubmissionBackend` 和 `GuiGraphics` facade，把旧绘制调用提交到 26.2 render state，禁止在 GUI extraction 阶段直接改写 framebuffer。该边界用于兼容 Sodium/Iris，也避免 ClickGUI 污染整帧颜色状态。

HUD 与 world render 已分别迁到 Fabric API 的 `HudElementRegistry` 和 `LevelRenderEvents.COLLECT_SUBMITS`。输入事件、网络 `PacketProcessor`、camera、fog、实体移动、客户端生命周期等使用 26.2 专用薄 Mixin，继续调用共享 hook semantics。

Fabric 26.2 当前只支持 OpenGL graphics backend；Vulkan backend 会在入口处 fail fast。Fabric 26.2 / Java 25 也不会加载绑定 Java 17 `jvm.dll` 的 official ID114 native sink，Forge/ASM 1.20.1 native 路径不变。

## 明确的剩余兼容边界

以下 1.20.1 render/codec Mixin 没有在 26.2 注册，避免把已经删除的方法签名强行注入新 render-state 管线。名称保留在这里，并由根项目测试保证不会悄悄扩散：

- `ContainerScreenMixin`：容器页抑制需要迁到 26.2 screen extraction。
- `EntityRendererMixin`：vanilla name-tag 抑制需要迁到 entity render-state。
- `FriendlyByteBufMixin`：Component JSON 读取已改为 codec 管线；NameProtect decode hook 需要新的 codec 边界。
- `GuiMixin`：scoreboard、portal、texture overlay 抑制需要逐项迁到 HUD render-state。
- `LightTextureMixin`：darkness light scale 需要迁到 26.2 lightmap/fog state。
- `LivingEntityRendererMixin`：entity render pre/post 与完整 body/head 插值需要迁到 extracted entity state；local-player head pitch 已由 26.2 adapter 保留。
- `PlayerTabOverlayMixin`：tab list header/footer/name 与布局 hook 需要迁到 tab render-state。

这些边界不影响本阶段验收目标：Fabric 26.2 构建、Sodium/Iris/ViaFabricPlus 同时加载、客户端进入主菜单与基础页面运行。后续若要求这些具体模块达到 1.20.1 功能全等，应单独建兼容阶段逐项验收。

## 构建与运行

在 `fabricmod/`：

```powershell
.\gradlew.bat build verifyFabricRuntimeMods stageFabricRuntimeMods stageFabricMod --no-daemon --console=plain
.\gradlew.bat runClient --no-daemon --console=plain
```

在根目录：

```powershell
.\gradlew.bat build --no-parallel --no-daemon --console=plain
```

可用 `-PfabricJavaHome=<JDK 25>` 或环境变量 `FABRIC_JAVA_HOME` 覆盖 Fabric Java。根 Forge/ASM 构建仍保持自己的 Java 17 bytecode/toolchain 合同。
