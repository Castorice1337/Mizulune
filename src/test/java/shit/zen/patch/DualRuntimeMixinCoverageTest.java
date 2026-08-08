package shit.zen.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import asm.patchify.annotation.Slice;
import asm.patchify.annotation.WrapInvoke;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class DualRuntimeMixinCoverageTest {
    private static final Map<String, String> APPLICABLE_PATCHES = applicablePatches();
    private static final Set<String> FORGE_ONLY_OR_EMPTY = Set.of(
            "NetworkFiltersPatch",
            "ItemInHandLayerPatch");
    private static final Set<String> FABRIC_ONLY = Set.of(
            "TitleScreenMixin",
            "LivingEntityDropMixin");
    private static final Set<String> FABRIC_COMPAT_ONLY = Set.of("LocalPlayerSprintCompatMixin");
    private static final Map<String, String> FABRIC_26_DIRECT_BOUNDARIES = Map.of(
            "FogRendererMixin", "NoRender",
            "GuiRendererMixin", "FabricBlurCompositor",
            "LightmapRenderStateExtractorMixin", "GameRendererHookCallbacks.onFullBrightScale",
            "PacketUtilsMixin", "ReceivePacketEvent");
    private static final Map<String, String> FABRIC_26_API_REPLACEMENTS = Map.of(
            "GameRendererMixin", "GameRendererHookCallbacks.onRender",
            "LevelRendererMixin", "RenderHookCallbacks.onRenderLevel");
    private static final Set<String> FABRIC_26_DEFERRED_RENDER_HOOKS = Set.of(
            "ContainerScreenMixin",
            "EntityRendererMixin",
            "FriendlyByteBufMixin",
            "GuiMixin",
            "LightTextureMixin",
            "LivingEntityRendererMixin",
            "PlayerTabOverlayMixin");

    @Test
    void everyApplicableForgePatchHasExactlyOneRegisteredFabricAdapter() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        String bootstrap = Files.readString(
                root.resolve("src/main/java/shit/zen/platform/forge/ForgeAsmBootstrap.java"),
                StandardCharsets.UTF_8);
        for (String patch : APPLICABLE_PATCHES.keySet()) {
            assertTrue(bootstrap.contains("PatchRegistry.register(" + patch + ".class)"), patch);
        }
        for (String patch : FORGE_ONLY_OR_EMPTY) {
            assertTrue(bootstrap.contains("PatchRegistry.register(" + patch + ".class)"), patch);
        }

        String config = Files.readString(
                root.resolve("fabricmod/src/main/resources/mizulune.fabric.mixins.json"),
                StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\\\"([A-Za-z0-9]+Mixin)\\\"").matcher(config);
        Set<String> registered = new LinkedHashSet<>();
        while (matcher.find()) {
            registered.add(matcher.group(1));
        }

        Set<String> expected = new LinkedHashSet<>(APPLICABLE_PATCHES.values());
        expected.removeAll(FABRIC_26_API_REPLACEMENTS.keySet());
        expected.removeAll(FABRIC_26_DEFERRED_RENDER_HOOKS);
        expected.addAll(FABRIC_COMPAT_ONLY);
        expected.addAll(FABRIC_ONLY);
        expected.addAll(FABRIC_26_DIRECT_BOUNDARIES.keySet());
        assertEquals(expected, registered);
        for (String mixin : registered) {
            Path source = resolveMixinSource(root, mixin);
            assertTrue(Files.isRegularFile(source), source.toString());
            String contents = Files.readString(source, StandardCharsets.UTF_8);
            if ("TitleScreenMixin".equals(mixin)) {
                assertTrue(contents.contains("FantnelScreen"),
                        mixin + " must remain the Fabric-only FantNEL entry point");
            } else if (FABRIC_26_DIRECT_BOUNDARIES.containsKey(mixin)) {
                assertTrue(contents.contains(FABRIC_26_DIRECT_BOUNDARIES.get(mixin)),
                        mixin + " must remain an explicit thin 26.2 API-boundary adapter");
            } else {
                assertTrue(contents.contains("shit.zen.hook"),
                        mixin + " must remain a thin adapter over shared hook semantics");
            }
        }

        String fabricEntrypoint = Files.readString(
                root.resolve("fabricmod/src/main/java/shit/zen/fabric/MizuluneFabricClient.java"),
                StandardCharsets.UTF_8);
        for (Map.Entry<String, String> replacement : FABRIC_26_API_REPLACEMENTS.entrySet()) {
            assertTrue(fabricEntrypoint.contains(replacement.getValue()),
                    replacement.getKey() + " must have a Fabric 26 API replacement");
        }

        String compatibilityNotes = Files.readString(
                root.resolve("fabricmod/README.md"), StandardCharsets.UTF_8);
        for (String deferred : FABRIC_26_DEFERRED_RENDER_HOOKS) {
            assertTrue(compatibilityNotes.contains(deferred),
                    deferred + " must remain an explicit Fabric 26 compatibility boundary");
        }
    }

    @Test
    void correctedOneTwentyOnePatchTargetsStayOnTheirDeclaringOwners() throws Exception {
        assertEquals(
                "net/minecraft/world/entity/player/Player/getYRot",
                wrapTarget(PlayerPatch.class, "onDieGetYRot"));
        assertEquals(
                "net/minecraft/world/entity/player/Player/getXRot",
                wrapTarget(ItemPatch.class, "onGetPOVHitXRot"));
        assertEquals(
                "net/minecraft/client/player/LocalPlayer/getMainHandItem",
                wrapTarget(ItemInHandRendererPatch.class, "onGetMainHandItem"));
        assertEquals(
                "net/minecraft/world/entity/LivingEntity/getYRot",
                wrapTarget(LivingEntityPatch.class, "onJumpGetYRot"));

        Method delayedTick = ClientLevelPatch.class.getDeclaredMethod(
                "onTickEntity",
                net.minecraft.client.multiplayer.ClientLevel.class,
                net.minecraft.world.entity.Entity.class,
                shit.zen.asm.Invocation.class);
        Slice slice = delayedTick.getAnnotation(WrapInvoke.class).slice();
        assertEquals(1, slice.startIndex());
        assertEquals(1, slice.endIndex());

        assertFalse(
                Files.readString(
                        Path.of("src/main/java/shit/zen/patch/EntityPatch.java"),
                        StandardCharsets.UTF_8).contains("isStayingOnGroundSurface"));
        assertTrue(
                Files.readString(
                        Path.of("src/main/java/shit/zen/patch/PlayerPatch.java"),
                        StandardCharsets.UTF_8).contains("isStayingOnGroundSurface"));
    }

    @Test
    void fabricTwentySixLivingRenderStateKeepsHeadYawAndPitchParity() throws Exception {
        String mixin = Files.readString(
                Path.of("fabricmod/src/fabric26/java/shit/zen/fabric/mixin/HumanoidModelMixin.java"),
                StandardCharsets.UTF_8);

        assertTrue(mixin.contains("ModifyExpressionValue"),
                "26.2 must replace the extracted head-yaw expression before body-relative yRot is derived");
        assertTrue(mixin.contains("Lnet/minecraft/util/Mth;rotLerp(FFF)F"),
                "26.2 must hook the same head-yaw interpolation as the 1.20.1 renderer patch");
        assertTrue(mixin.contains("LivingEntityRenderHookCallbacks.headYaw"),
                "Scaffold/KillAura render yaw must keep the shared RotationAnimationEvent semantics");
        assertTrue(mixin.contains("LivingEntityRenderHookCallbacks.pitch"),
                "26.2 model pitch must keep the shared CameraPitchEvent semantics");
    }

    @Test
    void fabricTwentySixRestoresOldHittingAtTheHandSubmitBoundary() throws Exception {
        String mixin = Files.readString(
                Path.of("fabricmod/src/fabric26/java/shit/zen/fabric/mixin/ItemInHandRendererMixin.java"),
                StandardCharsets.UTF_8);

        assertTrue(mixin.contains("submitArmWithItem"),
                "OldHitting must run inside the 26.2 hand submission pose scope");
        assertTrue(mixin.contains("ItemInHandRendererHookCallbacks.onSubmitArmWithItem"),
                "26.2 must reuse the shared OldHitting policy through its platform submission adapter");
    }

    private static String wrapTarget(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method.getAnnotation(WrapInvoke.class).target();
            }
        }
        throw new AssertionError("Missing method " + type.getName() + "#" + methodName);
    }

    private static Path resolveMixinSource(Path root, String mixin) {
        Path fabric26 = root.resolve(
                "fabricmod/src/fabric26/java/shit/zen/fabric/mixin/" + mixin + ".java");
        if (Files.isRegularFile(fabric26)) {
            return fabric26;
        }
        return root.resolve(
                "fabricmod/src/main/java/shit/zen/fabric/mixin/" + mixin + ".java");
    }

    private static Map<String, String> applicablePatches() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("BlockPatch", "BlockMixin");
        result.put("CameraPatch", "CameraMixin");
        result.put("ChatScreenPatch", "ChatScreenMixin");
        result.put("ClientLevelPatch", "ClientLevelMixin");
        result.put("ConnectionPatch", "ConnectionMixin");
        result.put("ContainerScreenRenderPatch", "ContainerScreenMixin");
        result.put("EntityPatch", "EntityMixin");
        result.put("EntityRendererPatch", "EntityRendererMixin");
        result.put("FogRendererPatch", "FogRendererMixin");
        result.put("FriendlyByteBufPatch", "FriendlyByteBufMixin");
        result.put("GameRendererPatch", "GameRendererMixin");
        result.put("GuiPatch", "GuiMixin");
        result.put("HumanoidModelPatch", "HumanoidModelMixin");
        result.put("ItemPatch", "ItemMixin");
        result.put("ItemInHandRendererPatch", "ItemInHandRendererMixin");
        result.put("LevelRendererPatch", "LevelRendererMixin");
        result.put("LightTexturePatch", "LightTextureMixin");
        result.put("KeyboardHandlerPatch", "KeyboardHandlerMixin");
        result.put("KeyboardInputPatch", "KeyboardInputMixin");
        result.put("LivingEntityPatch", "LivingEntityMixin");
        result.put("LivingEntityRendererPatch", "LivingEntityRendererMixin");
        result.put("LocalPlayerPatch", "LocalPlayerMixin");
        result.put("MinecraftPatch", "MinecraftMixin");
        result.put("MouseHandlerPatch", "MouseHandlerMixin");
        result.put("MultiPlayerGameModePatch", "MultiPlayerGameModeMixin");
        result.put("PacketUtilsPatch", "PacketUtilsMixin");
        result.put("PlayerPatch", "PlayerMixin");
        result.put("PlayerTabOverlayPatch", "PlayerTabOverlayMixin");
        result.put("ScreenEffectRendererPatch", "ScreenEffectRendererMixin");
        return result;
    }
}
