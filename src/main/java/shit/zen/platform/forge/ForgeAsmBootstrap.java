package shit.zen.platform.forge;

import asm.patchify.loader.PatchAgent;
import asm.patchify.loader.PatchRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.asm.Bootstrap;
import shit.zen.patch.BlockPatch;
import shit.zen.patch.CameraPatch;
import shit.zen.patch.ChatScreenPatch;
import shit.zen.patch.ClientLevelPatch;
import shit.zen.patch.ConnectionPatch;
import shit.zen.patch.ContainerScreenRenderPatch;
import shit.zen.patch.EntityPatch;
import shit.zen.patch.EntityRendererPatch;
import shit.zen.patch.FogRendererPatch;
import shit.zen.patch.FriendlyByteBufPatch;
import shit.zen.patch.GameRendererPatch;
import shit.zen.patch.GuiPatch;
import shit.zen.patch.HumanoidModelPatch;
import shit.zen.patch.ItemInHandLayerPatch;
import shit.zen.patch.ItemInHandRendererPatch;
import shit.zen.patch.ItemPatch;
import shit.zen.patch.KeyboardHandlerPatch;
import shit.zen.patch.KeyboardInputPatch;
import shit.zen.patch.LevelRendererPatch;
import shit.zen.patch.LightTexturePatch;
import shit.zen.patch.LivingEntityPatch;
import shit.zen.patch.LivingEntityRendererPatch;
import shit.zen.patch.LocalPlayerPatch;
import shit.zen.patch.MinecraftPatch;
import shit.zen.patch.MouseHandlerPatch;
import shit.zen.patch.MultiPlayerGameModePatch;
import shit.zen.patch.NetworkFiltersPatch;
import shit.zen.patch.PacketUtilsPatch;
import shit.zen.patch.PlayerPatch;
import shit.zen.patch.PlayerTabOverlayPatch;
import shit.zen.patch.ScreenEffectRendererPatch;

/** Owns the legacy Patchify registration and javaagent installation path. */
public final class ForgeAsmBootstrap {
    private static final Logger LOGGER = LogManager.getLogger(ForgeAsmBootstrap.class);
    private static boolean patchesRegistered;

    private ForgeAsmBootstrap() {
    }

    public static synchronized void install() {
        Bootstrap.init();
        registerPatches();
        if (PatchAgent.getInstrumentation() != null) {
            PatchAgent.installPatchesAndRetransform();
        } else {
            LOGGER.warn("agent not attached. Launch with `./gradlew runClient0` so the agent jvmArg is set.");
        }
    }

    public static synchronized void registerPatches() {
        if (patchesRegistered) return;
        PatchRegistry.register(MinecraftPatch.class);
        PatchRegistry.register(LocalPlayerPatch.class);
        PatchRegistry.register(LivingEntityPatch.class);
        PatchRegistry.register(EntityPatch.class);
        PatchRegistry.register(PlayerPatch.class);
        PatchRegistry.register(ClientLevelPatch.class);
        PatchRegistry.register(ConnectionPatch.class);
        PatchRegistry.register(NetworkFiltersPatch.class);
        PatchRegistry.register(ContainerScreenRenderPatch.class);
        PatchRegistry.register(PacketUtilsPatch.class);
        PatchRegistry.register(KeyboardHandlerPatch.class);
        PatchRegistry.register(KeyboardInputPatch.class);
        PatchRegistry.register(ChatScreenPatch.class);
        PatchRegistry.register(EntityRendererPatch.class);
        PatchRegistry.register(LevelRendererPatch.class);
        PatchRegistry.register(BlockPatch.class);
        PatchRegistry.register(CameraPatch.class);
        PatchRegistry.register(FogRendererPatch.class);
        PatchRegistry.register(GameRendererPatch.class);
        PatchRegistry.register(GuiPatch.class);
        PatchRegistry.register(LightTexturePatch.class);
        PatchRegistry.register(MouseHandlerPatch.class);
        PatchRegistry.register(MultiPlayerGameModePatch.class);
        PatchRegistry.register(ItemInHandRendererPatch.class);
        PatchRegistry.register(ItemInHandLayerPatch.class);
        PatchRegistry.register(HumanoidModelPatch.class);
        PatchRegistry.register(LivingEntityRendererPatch.class);
        PatchRegistry.register(ItemPatch.class);
        PatchRegistry.register(PlayerTabOverlayPatch.class);
        PatchRegistry.register(ScreenEffectRendererPatch.class);
        PatchRegistry.register(FriendlyByteBufPatch.class);
        patchesRegistered = true;
    }
}
