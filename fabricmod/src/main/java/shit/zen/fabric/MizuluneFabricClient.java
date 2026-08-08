package shit.zen.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import shit.zen.fantnel.FantnelProxySession;
import shit.zen.platform.ClientPlatforms;

/** Fabric/Knot entrypoint. MaxHook remains gated and loaded later by the shared native sink. */
public final class MizuluneFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlatforms.install(FabricClientPlatform.INSTANCE);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            FantnelProxySession.onPlayDisconnected());
        // Fabric invokes this while Minecraft is still constructing and before
        // Options exists. MinecraftMixin bootstraps the shared client on tick.
    }
}
