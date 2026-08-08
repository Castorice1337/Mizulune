package asm.patchify.bridge;

import java.lang.instrument.Instrumentation;

/**
 * JDK-only startup bridge that keeps the full client jar out of the premain
 * class path. It deliberately does not load Forge, Log4j, ASM, Minecraft, or
 * any client resource. The normal client-side
 * {@code PatchAgent} consumes the two system properties after its own class
 * loader is ready.
 */
public final class PremainBridge {
    public static final String INSTRUMENTATION_KEY = "oz.instrumentation";
    public static final String STARTUP_MODE_KEY = "oz.agent.startupMode";

    private PremainBridge() {
    }

    public static void premain(String args, Instrumentation instrumentation) {
        if (instrumentation == null) {
            throw new IllegalArgumentException("instrumentation");
        }
        System.getProperties().put(INSTRUMENTATION_KEY, instrumentation);
        System.setProperty(STARTUP_MODE_KEY, "premain");
    }
}
