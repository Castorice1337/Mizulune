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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

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

        // --- Runtime class-name obfuscation (second layer) ---
        // The jar already contains build-time obfuscated names (layer 1).
        // Generate fresh random names for this injection session (layer 2),
        // so every DLL injection produces unique, unpredictable class names.
        String selfInternalName = GameLoaderBridge.class.getName().replace('.', '/');
        Map<String, String> runtimeTypeMap = buildRuntimeTypeMap(
                pendingClasses.keySet(), selfInternalName);
        if (!runtimeTypeMap.isEmpty()) {
            long obfT0 = System.nanoTime();
            pendingClasses = applyRuntimeObfuscation(pendingClasses, runtimeTypeMap);
            long obfMs = (System.nanoTime() - obfT0) / 1_000_000L;
            LOGGER.info("Runtime obfuscation: renamed {} classes in {} ms",
                    runtimeTypeMap.size(), obfMs);
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

        // Resolve DllBootstrap using the runtime type map (its name was remapped).
        String dllBootstrapName = resolveDllBootstrapName(runtimeTypeMap);
        Class<?> bootstrapCls = Class.forName(dllBootstrapName, true, gameLoader);
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

    // ===== Runtime class-name obfuscation =====
    //
    // Mirrors the build-time ext.obfuscateJar logic (build.gradle) but runs
    // inside the target JVM at injection time, so every DLL injection session
    // gets a unique set of random class names. This defeats anti-cheat systems
    // that maintain class-name blacklists from known pre-built releases.

    /**
     * Well-known class prefixes that should NOT be renamed (JDK, Minecraft,
     * Forge, Kotlin, Skiko, and other third-party libraries).
     */
    private static final String[] THIRD_PARTY_PREFIXES = {
            "java/", "javax/", "jdk/", "sun/",
            "net/minecraft/", "net/minecraftforge/", "cpw/",
            "com/mojang/", "com/google/", "io/netty/",
            "org/jetbrains/", "kotlin/", "kotlinx/",
            "org/objectweb/asm/",
            "org/apache/", "org/slf4j/",
            "it/unimi/", "com/electronwill/",
            "org/lwjgl/", "org/joml/",
            "me/", "lombok/",
            "module-info",
    };

    private static final String LEAD_ALPHABET;
    private static final String NAME_ALPHABET;
    static {
        StringBuilder lead = new StringBuilder(52);
        StringBuilder full = new StringBuilder(62);
        for (char c = 'a'; c <= 'z'; c++) { lead.append(c); full.append(c); }
        for (char c = 'A'; c <= 'Z'; c++) { lead.append(c); full.append(c); }
        for (char c = '0'; c <= '9'; c++) { full.append(c); }
        LEAD_ALPHABET = lead.toString();
        NAME_ALPHABET = full.toString();
    }

    /**
     * Build a mapping from current (build-time obfuscated) internal class names
     * to fresh random internal names. All owned classes are flattened into a
     * single random package (same strategy as build-time obfuscation) so that
     * package-private access between formerly same-package classes still works.
     *
     * @param classNames           dotted FQCNs of all pending classes
     * @param selfInternalName     internal name of GameLoaderBridge (excluded)
     * @return map of old-internal-name → new-internal-name
     */
    private static Map<String, String> buildRuntimeTypeMap(
            Set<String> classNames, String selfInternalName) {
        SecureRandom rng = new SecureRandom();
        String obfPackage = randomName(rng);
        Set<String> usedNames = new HashSet<>();
        Map<String, String> typeMap = new HashMap<>();

        for (String dotted : classNames) {
            String internal = dotted.replace('.', '/');
            if (internal.equals(selfInternalName)) continue; // skip self
            if (isThirdParty(internal)) continue;

            String newSimple;
            do { newSimple = randomName(rng); } while (!usedNames.add(newSimple));
            typeMap.put(internal, obfPackage + "/" + newSimple);
        }
        return typeMap;
    }

    private static boolean isThirdParty(String internal) {
        for (String prefix : THIRD_PARTY_PREFIXES) {
            if (internal.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String randomName(SecureRandom rng) {
        StringBuilder sb = new StringBuilder(16);
        sb.append(LEAD_ALPHABET.charAt(rng.nextInt(LEAD_ALPHABET.length())));
        for (int i = 0; i < 15; i++) {
            sb.append(NAME_ALPHABET.charAt(rng.nextInt(NAME_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Remap all owned classes using ASM {@link ClassRemapper}. Returns a new
     * map with the remapped class names as keys and rewritten bytecode as values.
     */
    private static LinkedHashMap<String, byte[]> applyRuntimeObfuscation(
            LinkedHashMap<String, byte[]> pending,
            Map<String, String> typeMap) {

        // Build string-constant remap table for Class.forName() / other string refs
        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, String> e : typeMap.entrySet()) {
            // Dotted form: Class.forName("old.pkg.ClassName")
            stringMap.put(e.getKey().replace('/', '.'), e.getValue().replace('/', '.'));
            // Internal form: sometimes used in string constants too
            stringMap.put(e.getKey(), e.getValue());
        }

        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                String mapped = typeMap.get(internalName);
                return mapped != null ? mapped : internalName;
            }

            @Override
            public Object mapValue(Object value) {
                if (value instanceof String s) {
                    String repl = stringMap.get(s);
                    if (repl != null) return repl;
                }
                return super.mapValue(value);
            }
        };

        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : pending.entrySet()) {
            String oldDotted = entry.getKey();
            String oldInternal = oldDotted.replace('.', '/');
            byte[] oldBytes = entry.getValue();

            String newInternal = typeMap.get(oldInternal);
            if (newInternal != null) {
                // Owned class — remap class name + all references
                ClassReader cr = new ClassReader(oldBytes);
                ClassWriter cw = new ClassWriter(0);
                // Strip SourceFile attribute for additional obfuscation
                ClassVisitor stripSource = new ClassVisitor(Opcodes.ASM9, cw) {
                    @Override
                    public void visitSource(String source, String debug) {
                        super.visitSource(null, null);
                    }
                };
                cr.accept(new ClassRemapper(stripSource, remapper), 0);
                result.put(newInternal.replace('/', '.'), cw.toByteArray());
            } else {
                // Third-party / non-owned class — still remap internal references
                // in case it refers to owned classes (unlikely but safe)
                ClassReader cr = new ClassReader(oldBytes);
                ClassWriter cw = new ClassWriter(0);
                cr.accept(new ClassRemapper(cw, remapper), 0);
                byte[] rewritten = cw.toByteArray();
                result.put(oldDotted, rewritten);
            }
        }
        return result;
    }

    /**
     * Resolve the runtime-obfuscated name of {@code DllBootstrap}. The original
     * name was already replaced by build-time obfuscation; find its new mapping
     * entry by looking for the class whose build-time name matches the
     * compile-time reference (which is itself remapped by build-time obfuscation
     * via {@code mapValue}).
     *
     * <p>Because the build-time obfuscator rewrites the string constant
     * {@code "shit.zen.dll.DllBootstrap"} inside GameLoaderBridge's bytecode,
     * at runtime this class sees the build-time-obfuscated name. We look up
     * that name in the runtime type map to get the final session-unique name.</p>
     */
    private static String resolveDllBootstrapName(Map<String, String> runtimeTypeMap) {
        // The build-time obfuscator rewrites the literal "shit.zen.dll.DllBootstrap"
        // inside this class's bytecode to the build-time-obfuscated name.
        // At runtime, BOOTSTRAP_REF holds that build-time name.
        String buildTimeInternal = BOOTSTRAP_REF.replace('.', '/');
        String runtimeInternal = runtimeTypeMap.get(buildTimeInternal);
        if (runtimeInternal != null) {
            return runtimeInternal.replace('/', '.');
        }
        // Fallback: no runtime mapping (shouldn't happen), use build-time name
        return BOOTSTRAP_REF;
    }

    /**
     * Build-time reference to DllBootstrap. The build-time obfuscator's
     * {@code mapValue()} will rewrite this string literal to the build-time
     * obfuscated FQCN, keeping it in sync with the jar contents.
     */
    private static final String BOOTSTRAP_REF = "shit.zen.dll.DllBootstrap";
}
