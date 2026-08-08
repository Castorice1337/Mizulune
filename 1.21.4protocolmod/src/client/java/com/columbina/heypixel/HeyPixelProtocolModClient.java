package com.columbina.heypixel;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HeyPixelProtocolModClient implements ClientModInitializer {
    public static final String MOD_ID = "mizulune-heypixel-protocol";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static volatile HeyPixelFabricRuntime runtime;

    @Override
    public void onInitializeClient() {
        Path protocolDirectory = protocolDirectory();
        ProtocolModConfig config;
        try {
            config = ProtocolModConfig.load(
                protocolDirectory,
                FabricLoader.getInstance().getGameDir()
            );
        } catch (Exception error) {
            LOGGER.error("Protocol configuration is invalid; bridge remains disabled: {}",
                error.getClass().getSimpleName());
            return;
        }

        PayloadTypeRegistry.playC2S().register(HeyPixelPayload.TYPE, HeyPixelPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HeyPixelPayload.TYPE, HeyPixelPayload.CODEC);

        HeyPixelFabricRuntime created = new HeyPixelFabricRuntime(
            Minecraft.getInstance(),
            protocolDirectory,
            config
        );
        runtime = created;
        boolean receiverRegistered = ClientPlayNetworking.registerGlobalReceiver(
            HeyPixelPayload.TYPE,
            (payload, context) -> created.handle(payload, context.player().connection)
        );
        if (!receiverRegistered) {
            runtime = null;
            created.shutdown();
            LOGGER.error("HeyPixel payload receiver is already registered; bridge remains disabled");
            return;
        }
        ClientPlayConnectionEvents.JOIN.register(
            (handler, sender, client) -> created.onJoin(handler)
        );
        ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> created.onDisconnect(handler)
        );
        ClientTickEvents.START_CLIENT_TICK.register(created::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(created::onEndTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (runtime == created) runtime = null;
            created.shutdown();
        });
        LOGGER.info("Mizulune HeyPixel protocol bridge loaded for Minecraft 1.21.4; liveSend={}",
            config.enabled() && config.allowLiveSend());
    }

    public static HeyPixelFabricRuntime runtime() {
        return runtime;
    }

    private static Path protocolDirectory() {
        String override = System.getenv("MIZULUNE_PROTOCOL_DIRECTORY");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".mizulune")
            .toAbsolutePath().normalize();
    }
}
