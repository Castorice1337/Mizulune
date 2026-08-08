package asm.patchify.loader;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

/**
 * Java agent entry point — loaded by the system class loader when the JVM is started with
 * {@code -javaagent:<this jar>}.
 *
 * <p>In a ForgeGradle dev environment the mod jar and the agent jar are the same file but get
 * loaded by different class loaders (system vs the Forge module layer). That means the static
 * fields on this class are NOT shared between agent-side and mod-side copies of
 * {@code PatchAgent}. To bridge the gap we stash {@link Instrumentation} in
 * {@link System#getProperties()} under {@link #INSTRUMENTATION_KEY} so the mod can retrieve it
 * regardless of which class loader it lives in.</p>
 */
public final class PatchAgent {
    public static final String INSTRUMENTATION_KEY = "oz.instrumentation";
    public static final String STARTUP_MODE_KEY = "oz.agent.startupMode";

    private static volatile boolean transformerInstalled = false;

    private PatchAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        install(inst, StartupMode.PREMAIN);
    }

    public static void agentmain(String args, Instrumentation inst) {
        install(inst, StartupMode.AGENTMAIN);
    }

    public static synchronized void install(Instrumentation inst) {
        install(inst, getStartupMode());
    }

    private static synchronized void install(Instrumentation inst, StartupMode requestedMode) {
        if (inst == null) throw new IllegalArgumentException("instrumentation");
        StartupMode startupMode = preferEarlierStartup(getStartupMode(), requestedMode);
        Object existing = System.getProperties().get(INSTRUMENTATION_KEY);
        if (existing == inst && startupMode == getStartupMode()) {
            return;
        }
        System.getProperties().put(INSTRUMENTATION_KEY, inst);
        System.setProperty(STARTUP_MODE_KEY, startupMode.propertyValue());
    }

    /**
     * Looks up the {@link Instrumentation} stashed by {@link #premain}. Works across class loaders.
     */
    public static Instrumentation getInstrumentation() {
        Object instObj = System.getProperties().get(INSTRUMENTATION_KEY);
        return instObj instanceof Instrumentation ? (Instrumentation) instObj : null;
    }

    /**
     * Returns how Instrumentation first became available in this JVM. The value is stored as a
     * fixed String in system properties so the system-loader and Forge-layer copies agree even
     * when their {@code PatchAgent} classes are different objects.
     */
    public static StartupMode getStartupMode() {
        Object value = System.getProperties().get(STARTUP_MODE_KEY);
        return StartupMode.fromProperty(value == null ? null : String.valueOf(value));
    }

    private static StartupMode preferEarlierStartup(
        StartupMode existing,
        StartupMode requested
    ) {
        if (existing == StartupMode.PREMAIN || requested == StartupMode.PREMAIN) {
            return StartupMode.PREMAIN;
        }
        if (existing == StartupMode.AGENTMAIN || requested == StartupMode.AGENTMAIN) {
            return StartupMode.AGENTMAIN;
        }
        return StartupMode.NONE;
    }

    /**
     * Install a transformer for the currently registered patches and retransform any patch target
     * that is already loaded. Called from mod code once {@link PatchRegistry} is populated.
     */
    public static synchronized void installPatchesAndRetransform() {
        if (transformerInstalled) {
            ModLogger.info("Patches already installed; skipping duplicate retransform request");
            return;
        }
        Instrumentation inst = getInstrumentation();
        if (inst == null) {
            ModLogger.warn("agent not attached; cannot install patches");
            return;
        }
        ModLogger.info(
            "agent available, startup mode = {}, retransform supported = {}",
            getStartupMode(),
            inst.isRetransformClassesSupported()
        );
        PatchClassFileTransformer transformer = new PatchClassFileTransformer();
        inst.addTransformer(transformer, true);
        transformerInstalled = true;
        List<Class<?>> retransform = new ArrayList<>();
        for (Class<?> patch : PatchRegistry.getPatches()) {
            asm.patchify.annotation.Patch ann = patch.getAnnotation(asm.patchify.annotation.Patch.class);
            if (ann == null) continue;
            Class<?> target;
            try {
                target = ann.value();
            } catch (Throwable t) {
                ModLogger.warn("Patch target unresolved for {}: {}", patch.getName(), t.toString());
                continue;
            }
            if (inst.isModifiableClass(target)) {
                retransform.add(target);
            } else {
                ModLogger.warn("Cannot retransform unmodifiable target {}", target.getName());
            }
        }
        if (retransform.isEmpty()) {
            return;
        }
        // Retransform one class at a time so we can pinpoint which patch produces invalid
        // bytecode if the JVM throws VerifyError / LinkageError.
        int success = 0;
        for (Class<?> target : retransform) {
            try {
                inst.retransformClasses(target);
                success++;
            } catch (Throwable t) {
                ModLogger.error("Retransform failed for {}", target.getName(), t);
            }
        }
        ModLogger.info("Retransformed {} / {} patch target(s)", success, retransform.size());
    }

    /**
     * Defers Log4j linkage until Forge invokes the patch installer. The JVM calls {@code premain}
     * before the Minecraft/Forge class path is necessarily usable, so the agent entry point itself
     * must only depend on JDK classes.
     */
    private static final class ModLogger {
        private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(PatchAgent.class);

        private static void info(String message, Object... arguments) {
            LOGGER.info(message, arguments);
        }

        private static void warn(String message, Object... arguments) {
            LOGGER.warn(message, arguments);
        }

        private static void error(String message, Object... arguments) {
            LOGGER.error(message, arguments);
        }
    }

    public enum StartupMode {
        NONE("none"),
        PREMAIN("premain"),
        AGENTMAIN("agentmain");

        private final String propertyValue;

        StartupMode(String propertyValue) {
            this.propertyValue = propertyValue;
        }

        private String propertyValue() {
            return propertyValue;
        }

        private static StartupMode fromProperty(String value) {
            if (value == null) return NONE;
            for (StartupMode mode : values()) {
                if (mode.propertyValue.equalsIgnoreCase(value.trim())) return mode;
            }
            return NONE;
        }
    }
}
