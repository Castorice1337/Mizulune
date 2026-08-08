package shit.zen.dll;

import asm.patchify.loader.PatchAgent;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.ZenClient;
import shit.zen.platform.forge.ForgeAsmBootstrap;
import shit.zen.platform.forge.ForgeClientPlatform;

/**
 * Final hand-off step on the DLL injection path. By the time {@link #start} is
 * invoked, {@code GameLoaderBridge} has already re-defined every class in the
 * jar onto the Forge GameClassLoader, so {@link ZenClient} and all Patchify
 * handler classes share Minecraft's class loader.
 *
 * <p>This entry point intentionally does <b>not</b> construct {@link ZenClient}.
 * Once {@link PatchAgent#installPatchesAndRetransform()} returns, the
 * retransformed {@code Minecraft.tick()} contains the injected
 * {@code MinecraftPatch.onTick} prologue that lazily constructs {@link
 * ZenClient} on the next tick. Letting the existing tick-driven lazy-init run
 * keeps the DLL path and the mod path identical from {@code ZenClient.init}
 * onwards.</p>
 */
public final class DllBootstrap {
    private static final Logger LOGGER = LogManager.getLogger(DllBootstrap.class);
    private static volatile boolean started = false;

    private DllBootstrap() {
    }

    public static synchronized void start(String extractedJarPath) {
        if (started) {
            LOGGER.info("bootstrap.start ignored (already started)");
            return;
        }
        started = true;
        try {
            LOGGER.info("bootstrap.start jar={}", extractedJarPath);
            LOGGER.info("bootstrap loader  = {}", DllBootstrap.class.getClassLoader());
            LOGGER.info("client loader     = {}", ZenClient.class.getClassLoader());
            LOGGER.info("Minecraft loader     = {}", Minecraft.class.getClassLoader());
            LOGGER.info("agent inst      = {}", PatchAgent.getInstrumentation());

            // Load mojmap → SRG mappings before we attempt any retransform.
            ForgeClientPlatform.install();

            // Register the Patchify classes and trigger retransform of the
            // already-loaded Minecraft targets. After this returns,
            // Minecraft.tick / LocalPlayer.tick / etc. carry the injected
            // prologues; the next tick will invoke MinecraftPatch.onTick which
            // performs the real ZenClient construction via its existing
            // lazy-init path.
            ForgeAsmBootstrap.install();

            LOGGER.info("bootstrap done. client will be constructed on the next tick.");
        } catch (Throwable t) {
            LOGGER.error("bootstrap.start failed", t);
        }
    }
}
