package shit.zen.dll;

import asm.patchify.loader.PatchAgent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * One-shot bootstrap that is loaded by a throwaway {@code URLClassLoader}
 * (parent = Forge GameClassLoader) and re-defines every class in zen.jar onto
 * the GameClassLoader itself.
 *
 * <p>This is necessary because retransformed Minecraft classes contain
 * {@code INVOKESTATIC} references to our patch handlers (e.g.
 * {@code INVOKESTATIC shit/zen/patch/ConnectionPatch.transformReceive}).
 * When the JVM resolves those references it uses the Minecraft class's
 * defining loader (cpw.mods.cl.ModuleClassLoader). If our classes only live
 * in a sibling URLClassLoader, resolution fails with
 * {@code NoClassDefFoundError} / {@code VerifyError}.</p>
 *
 * <p>Steps:</p>
 * <ol>
 *   <li>Use the live {@link Instrumentation} (already populated by
 *       {@code instrument.dll}'s {@code Agent_OnAttach}) to {@code redefineModule}
 *       java.base/java.lang open to this module, so reflective access to
 *       {@code ClassLoader.defineClass} works without JVM args.</li>
 *   <li>Walk the jar. {@code .class} entries get re-defined on the game
 *       loader; everything else is extracted to a temp directory and exposed
 *       via the {@code mizulune.resources} system property, with legacy
 *       {@code openzen.resources} kept as a compatibility alias, so
 *       {@code Bootstrap} and {@code ZenClient} can still find their
 *       resources (mapping.srg, cloud assets, webui static files).</li>
 *   <li>Hand off to {@link DllBootstrap#start(String)} loaded through the
 *       game loader.</li>
 * </ol>
 */
public final class GameLoaderBridge {
    private static final Logger LOGGER = LogManager.getLogger(GameLoaderBridge.class);
    public static final String RESOURCES_PROP = "mizulune.resources";
    private static final String LEGACY_RESOURCES_PROP = "openzen.resources";
    private static final String DLL_LIBS_DIR = "openzen/dll-libs";
    private static final String SKIKO_LIBRARY_PATH_PROP = "skiko.library.path";
    private static final Set<String> SKIKO_NATIVE_FILES = Set.of(
            "skiko-windows-x64.dll",
            "skiko-windows-x64.dll.sha256",
            "icudtl.dat");
    private static final List<JarFile> APPENDED_JARS = new ArrayList<>();

    private GameLoaderBridge() {
    }

    public static void load(String jarPath, ClassLoader gameLoader) throws Throwable {
        LOGGER.info("bridge.load jar={} gameLoader={}", jarPath, gameLoader);
        long t0 = System.nanoTime();

        Instrumentation inst = PatchAgent.getInstrumentation();
        if (inst == null) {
            throw new IllegalStateException("agent instrumentation returned null - "
                    + "did instrument.dll Agent_OnAttach run?");
        }

        openJavaBaseToSelf(inst);

        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);

        Path resourceDir = Paths.get(System.getProperty("java.io.tmpdir"),
                "openzen-resources-" + ProcessHandle.current().pid());
        Files.createDirectories(resourceDir);

        // Pass 1: read every entry. Defer .class defines until pass 2 so we can
        // retry in dependency order (a patch class extending a zen base class
        // can only be defined after its super has been defined).
        LinkedHashMap<String, byte[]> pendingClasses = new LinkedHashMap<>();
        int resourceCount = 0;

        try (ZipFile zip = new ZipFile(jarPath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.startsWith("META-INF/")) continue;

                byte[] bytes;
                try (InputStream is = zip.getInputStream(entry)) {
                    bytes = is.readAllBytes();
                }

                if (name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    // GameLoaderBridge itself is already live in this URLClassLoader.
                    if (className.equals(GameLoaderBridge.class.getName())) continue;
                    pendingClasses.put(className, bytes);
                } else {
                    Path target = resourceDir.resolve(name);
                    Path parent = target.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    Files.write(target, bytes);
                    resourceCount++;
                }
            }
        }

        int classCount = definePassUntilFixedPoint(defineClass, gameLoader, pendingClasses);

        if (!pendingClasses.isEmpty()) {
            LOGGER.warn("{} class(es) could not be defined on gameLoader after fixed-point retry:",
                    pendingClasses.size());
            for (Map.Entry<String, byte[]> e : pendingClasses.entrySet()) {
                try {
                    defineClass.invoke(gameLoader, e.getKey(), e.getValue(), 0, e.getValue().length);
                } catch (Throwable t) {
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    LOGGER.warn("  {} -> {}", e.getKey(), cause.toString());
                }
            }
        }

        String resourcePath = resourceDir.toAbsolutePath().toString();
        System.setProperty(RESOURCES_PROP, resourcePath);
        System.setProperty(LEGACY_RESOURCES_PROP, resourcePath);
        configureDllRuntimeDependencies(inst, gameLoader, defineClass, resourceDir);

        long ms = (System.nanoTime() - t0) / 1_000_000L;
        LOGGER.info("Defined {} classes, extracted {} resources to {} ({} ms)",
                classCount, resourceCount, resourceDir, ms);

        Class<?> bootstrapCls = Class.forName("shit.zen.dll.DllBootstrap", true, gameLoader);
        LOGGER.info("bootstrap loader (should be gameLoader): {}", bootstrapCls.getClassLoader());
        Method start = bootstrapCls.getMethod("start", String.class);
        start.invoke(null, jarPath);
    }

    private static void configureDllRuntimeDependencies(Instrumentation inst,
                                                        ClassLoader gameLoader,
                                                        Method defineClass,
                                                        Path resourceDir) {
        Path libsDir = resourceDir.resolve(DLL_LIBS_DIR);
        if (!Files.isDirectory(libsDir)) {
            LOGGER.warn("DLL runtime libs directory not found: {}", libsDir);
            return;
        }

        List<Path> jars;
        try (Stream<Path> stream = Files.list(libsDir)) {
            jars = stream
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            LOGGER.warn("Failed to list DLL runtime libs in {}", libsDir, e);
            return;
        }

        if (jars.isEmpty()) {
            LOGGER.warn("No DLL runtime dependency jars found in {}", libsDir);
            return;
        }

        int appended = appendRuntimeJarsToSystemLoader(inst, jars);
        int nativeFiles = extractSkikoNativeRuntime(jars,
                resourceDir.resolve("skiko-runtime").resolve("windows-x64"));

        if (!isClassVisible("org.jetbrains.skia.DirectContext", gameLoader)
                || !isClassVisible("kotlin.jvm.internal.Intrinsics", gameLoader)) {
            int defined = defineRuntimeDependencyClasses(defineClass, gameLoader, jars);
            LOGGER.info("Defined {} DLL runtime dependency classes on gameLoader", defined);
        }

        boolean skiaVisible = isClassVisible("org.jetbrains.skia.DirectContext", gameLoader);
        boolean kotlinVisible = isClassVisible("kotlin.jvm.internal.Intrinsics", gameLoader);
        LOGGER.info("DLL runtime dependencies prepared: jars={}, appended={}, nativeFiles={}, "
                        + "skiaVisible={}, kotlinVisible={}, {}={}",
                jars.size(), appended, nativeFiles, skiaVisible, kotlinVisible,
                SKIKO_LIBRARY_PATH_PROP, System.getProperty(SKIKO_LIBRARY_PATH_PROP));
    }

    private static int appendRuntimeJarsToSystemLoader(Instrumentation inst, List<Path> jars) {
        int appended = 0;
        for (Path jar : jars) {
            try {
                JarFile jarFile = new JarFile(jar.toFile());
                inst.appendToSystemClassLoaderSearch(jarFile);
                APPENDED_JARS.add(jarFile);
                appended++;
            } catch (Throwable t) {
                LOGGER.warn("Failed to append DLL runtime jar to system loader: {}",
                        jar, t);
            }
        }
        return appended;
    }

    private static int extractSkikoNativeRuntime(List<Path> jars, Path targetDir) {
        int copied = 0;
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create Skiko native runtime directory {}", targetDir, e);
            return 0;
        }

        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String fileName = Paths.get(entry.getName()).getFileName().toString();
                    if (!SKIKO_NATIVE_FILES.contains(fileName)) continue;
                    Path target = targetDir.resolve(fileName);
                    try (InputStream is = zip.getInputStream(entry)) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    copied++;
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to extract Skiko native runtime from {}", jar, e);
            }
        }

        if (copied > 0) {
            System.setProperty(SKIKO_LIBRARY_PATH_PROP, targetDir.toAbsolutePath().toString());
        }
        return copied;
    }

    private static int defineRuntimeDependencyClasses(Method defineClass,
                                                      ClassLoader gameLoader,
                                                      List<Path> jars) {
        LinkedHashMap<String, byte[]> pending = new LinkedHashMap<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    if (!name.endsWith(".class")
                            || name.equals("module-info.class")
                            || name.startsWith("META-INF/versions/")) {
                        continue;
                    }
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    if (isClassVisible(className, gameLoader) || pending.containsKey(className)) {
                        continue;
                    }
                    try (InputStream is = zip.getInputStream(entry)) {
                        pending.put(className, is.readAllBytes());
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read DLL runtime dependency jar {}", jar, e);
            }
        }

        int defined = definePassUntilFixedPoint(defineClass, gameLoader, pending);
        if (!pending.isEmpty()) {
            LOGGER.warn("{} DLL runtime dependency class(es) could not be defined:", pending.size());
            for (String className : pending.keySet()) {
                LOGGER.warn("  {}", className);
            }
        }
        return defined;
    }

    private static boolean isClassVisible(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Defines classes onto {@code gameLoader} in repeated passes. Each pass
     * retries everything that previously failed; this terminates either when
     * all entries are defined or when a full pass made no progress (genuinely
     * missing dependency). The remaining failures stay in {@code pending} for
     * the caller to log.
     */
    private static int definePassUntilFixedPoint(Method defineClass,
                                                  ClassLoader gameLoader,
                                                  LinkedHashMap<String, byte[]> pending) {
        int total = 0;
        boolean progressed = true;
        while (progressed && !pending.isEmpty()) {
            progressed = false;
            Iterator<Map.Entry<String, byte[]>> it = pending.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, byte[]> entry = it.next();
                try {
                    defineClass.invoke(gameLoader, entry.getKey(),
                            entry.getValue(), 0, entry.getValue().length);
                    it.remove();
                    progressed = true;
                    total++;
                } catch (Throwable t) {
                    // Leave it in the pending map; another pass might succeed
                    // once dependencies are defined. The non-recoverable
                    // failures are logged by the caller.
                }
            }
        }
        return total;
    }

    private static void openJavaBaseToSelf(Instrumentation inst) {
        Module javaBase = ClassLoader.class.getModule();
        Module here = GameLoaderBridge.class.getModule();
        try {
            inst.redefineModule(
                    javaBase,
                    Set.of(),
                    Map.of(),
                    Map.of("java.lang", Set.of(here)),
                    Set.of(),
                    Map.of());
        } catch (Throwable t) {
            LOGGER.warn("redefineModule for java.base/java.lang failed (may be already open): {}",
                    t.toString());
        }
    }
}
