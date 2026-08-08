package shit.zen.patch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Keeps Forge/Patchify and Fabric/Mixin on the same protocol lifecycle callbacks. */
final class DualRuntimeProtocolLifecycleParityTest {
    @Test
    void bothMinecraftAdaptersUseTheSharedTickAndLogoutCallbacks() throws Exception {
        String forge = source("src/main/java/shit/zen/patch/MinecraftPatch.java");
        String fabric = source("fabricmod/src/main/java/shit/zen/fabric/mixin/MinecraftMixin.java");
        for (String callback : new String[]{"onTickHead", "onTickTail", "onClearLevel", "onClose"}) {
            assertTrue(forge.contains("MinecraftHookCallbacks." + callback), callback + " Forge");
            assertTrue(fabric.contains("MinecraftHookCallbacks." + callback), callback + " Fabric");
        }
    }

    @Test
    void bothConnectionAdaptersReportTheSameFinalWriteBoundary() throws Exception {
        String forge = source("src/main/java/shit/zen/patch/ConnectionPatch.java");
        String fabric = source("fabricmod/src/main/java/shit/zen/fabric/mixin/ConnectionMixin.java");
        assertTrue(forge.contains("ConnectionHookCallbacks.onFinalPacketWrite"));
        assertTrue(fabric.contains("ConnectionHookCallbacks.onFinalPacketWrite"));
    }

    @Test
    void sharedCallbacksOwnProtocolBootstrapReadyAndLogout() throws Exception {
        String minecraft = source("src/main/java/shit/zen/hook/MinecraftHookCallbacks.java");
        String connection = source("src/main/java/shit/zen/hook/ConnectionHookCallbacks.java");
        assertTrue(minecraft.contains("tickProtocolBootstrap()"));
        assertTrue(minecraft.contains("tickProtocolBootstrapEnd()"));
        assertTrue(minecraft.contains("getProtocolModule().onLoggingOut()"));
        assertTrue(connection.contains("protocol.onFinalPacketWrite(packet)"));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath),
            StandardCharsets.UTF_8);
    }
}
