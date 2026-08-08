package shit.zen.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.atomic.AtomicBoolean;
import shit.zen.fantnel.FantnelProxySession;
import shit.zen.fabric.render.FabricRenderBridge;
import shit.zen.fabric.network.LegacyCustomPayloads;
import shit.zen.hook.GameRendererHookCallbacks;
import shit.zen.hook.RenderHookCallbacks;
import shit.zen.platform.ClientPlatforms;

/** Fabric/Knot entrypoint. MaxHook remains gated and loaded later by the shared native sink. */
public final class MizuluneFabricClient implements ClientModInitializer {
    private final AtomicBoolean backendValidated = new AtomicBoolean();

    @Override
    public void onInitializeClient() {
        ClientPlatforms.install(FabricClientPlatform.INSTANCE);
        LegacyCustomPayloads.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            FantnelProxySession.onPlayDisconnected());
        ClientTickEvents.START_CLIENT_TICK.register(client -> validateGraphicsBackend());
        HudElementRegistry.addLast(
            ResourceLocation.fromNamespaceAndPath("mizulune", "legacy_hud"),
            (extractor, deltaTracker) -> FabricRenderBridge.withGui(extractor, () ->
                GameRendererHookCallbacks.onRender(
                    extractor == null ? null : net.minecraft.client.Minecraft.getInstance().gameRenderer,
                    deltaTracker.getGameTimeDeltaPartialTick(false)))
        );
        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
            FabricRenderBridge.withWorld(context.submitNodeCollector(), () ->
                RenderHookCallbacks.onRenderLevel(
                    context.poseStack(),
                    net.minecraft.client.Minecraft.getInstance().getDeltaTracker()
                        .getGameTimeDeltaPartialTick(false)))
        );
        // Fabric invokes this while Minecraft is still constructing and before
        // Options exists. MinecraftMixin bootstraps the shared client on tick.
    }

    private void validateGraphicsBackend() {
        if (!backendValidated.compareAndSet(false, true)) return;
        if (!FabricClientPlatform.INSTANCE.supportsLegacyOpenGlRendering()) {
            throw new IllegalStateException(
                "Mizulune Fabric 26.2 currently requires the OpenGL graphics backend; Vulkan is not supported"
            );
        }
    }
}
