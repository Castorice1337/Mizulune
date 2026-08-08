package shit.zen.protocol.heypixel;

import com.heypixel.heypixelmod.SyncToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Premain-, hash- and runtime-gated delegate to the official MaxHook implementation.
 * Loading the DLL and matching the Java ABI makes invocation possible; it does not by itself
 * prove that MaxHook patched the two HotSpot Method entries.
 */
final class OfficialId114NativeSink implements Id114NativeSink {
    static final String MAXHOOK_SHA256 =
        "982D8223CF8DA9584D67B1A7A24E5B2515DA22BA72EEBBAF813A353DA14F956A";
    static final String OFFICIAL_TEMURIN_17_0_2_JVM_SHA256 =
        "46EB16C248CEC10CDB639E6E97F31F5817ED128E86230AB85715D063DDFCBB47";
    static final String MAXHOOK_PATH_PROPERTY = "mizulune.heypixel.maxHookPath";
    static final String MAXHOOK_PATH_ENV = "MIZULUNE_HEYPIXEL_MAXHOOK_PATH";
    private static final String STAGED_MAXHOOK_RELATIVE_PATH =
        ".mizulune/runtime/maxhook/MaxHook.dll";
    private static final Set<String> PRODUCTION_JVM_ALLOWLIST =
        Set.of(OFFICIAL_TEMURIN_17_0_2_JVM_SHA256);
    private static final NativeLoadRegistry PRODUCTION_LOAD_REGISTRY = new NativeLoadRegistry();

    private final HeyPixelInstallLayout layout;
    private final Path javaHome;
    private final String expectedMaxHookSha256;
    private final Set<String> allowedJvmSha256;
    private final NativeLoader nativeLoader;
    private final NativeLoadRegistry loadRegistry;
    private final BooleanSupplier platformSupported;
    private final PackagedMaxHookLocator packagedMaxHookLocator;
    private final Supplier<ProtocolStartupMode> startupMode;
    private volatile Availability cachedAvailability;

    OfficialId114NativeSink(HeyPixelInstallLayout layout) {
        this(
            layout,
            systemJavaHome(),
            MAXHOOK_SHA256,
            PRODUCTION_JVM_ALLOWLIST,
            System::load,
            PRODUCTION_LOAD_REGISTRY,
            OfficialId114NativeSink::isWindowsX64,
            OfficialId114NativeSink::locatePackagedMaxHook,
            ProtocolStartupMode::fromSystemProperty
        );
    }

    OfficialId114NativeSink(
        HeyPixelInstallLayout layout,
        Path javaHome,
        String expectedMaxHookSha256,
        Set<String> allowedJvmSha256,
        NativeLoader nativeLoader,
        NativeLoadRegistry loadRegistry,
        BooleanSupplier platformSupported
    ) {
        this(
            layout,
            javaHome,
            expectedMaxHookSha256,
            allowedJvmSha256,
            nativeLoader,
            loadRegistry,
            platformSupported,
            () -> null,
            () -> ProtocolStartupMode.PREMAIN
        );
    }

    OfficialId114NativeSink(
        HeyPixelInstallLayout layout,
        Path javaHome,
        String expectedMaxHookSha256,
        Set<String> allowedJvmSha256,
        NativeLoader nativeLoader,
        NativeLoadRegistry loadRegistry,
        BooleanSupplier platformSupported,
        PackagedMaxHookLocator packagedMaxHookLocator
    ) {
        this(
            layout,
            javaHome,
            expectedMaxHookSha256,
            allowedJvmSha256,
            nativeLoader,
            loadRegistry,
            platformSupported,
            packagedMaxHookLocator,
            () -> ProtocolStartupMode.PREMAIN
        );
    }

    OfficialId114NativeSink(
        HeyPixelInstallLayout layout,
        Path javaHome,
        String expectedMaxHookSha256,
        Set<String> allowedJvmSha256,
        NativeLoader nativeLoader,
        NativeLoadRegistry loadRegistry,
        BooleanSupplier platformSupported,
        PackagedMaxHookLocator packagedMaxHookLocator,
        Supplier<ProtocolStartupMode> startupMode
    ) {
        this.layout = layout;
        this.javaHome = javaHome;
        this.expectedMaxHookSha256 = normalizeHash(expectedMaxHookSha256);
        this.allowedJvmSha256 = Objects.requireNonNull(allowedJvmSha256, "allowedJvmSha256")
            .stream()
            .map(OfficialId114NativeSink::normalizeHash)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.nativeLoader = Objects.requireNonNull(nativeLoader, "nativeLoader");
        this.loadRegistry = Objects.requireNonNull(loadRegistry, "loadRegistry");
        this.platformSupported = Objects.requireNonNull(platformSupported, "platformSupported");
        this.packagedMaxHookLocator = Objects.requireNonNull(
            packagedMaxHookLocator, "packagedMaxHookLocator");
        this.startupMode = Objects.requireNonNull(startupMode, "startupMode");
    }

    @Override
    public Availability availability() {
        Availability cached = cachedAvailability;
        if (cached != null) return cached;
        synchronized (this) {
            cached = cachedAvailability;
            if (cached == null) {
                cached = evaluateAvailability();
                cachedAvailability = cached;
            }
            return cached;
        }
    }

    @Override
    public AcceptResult accept(String transientToken) {
        Objects.requireNonNull(transientToken, "transientToken");
        Availability availability = availability();
        if (!availability.available()) throw new InvocationException(availability.reason());
        try {
            SyncToken.accept(transientToken);
            return availability.reason() == Reason.READY
                ? AcceptResult.CONFIRMED
                : AcceptResult.INVOKED_UNVERIFIED;
        } catch (RuntimeException | LinkageError error) {
            throw new InvocationException(Reason.ACCEPT_FAILED);
        }
    }

    @Override
    public void logout() {
        Availability availability = availability();
        if (!availability.available()) throw new InvocationException(availability.reason());
        try {
            SyncToken.logout();
        } catch (RuntimeException | LinkageError error) {
            throw new InvocationException(Reason.LOGOUT_FAILED);
        }
    }

    private Availability evaluateAvailability() {
        ProtocolStartupMode mode;
        try {
            mode = startupMode.get();
        } catch (RuntimeException error) {
            mode = ProtocolStartupMode.NONE;
        }
        if (mode == ProtocolStartupMode.AGENTMAIN) {
            return Availability.unavailable(Reason.LATE_ATTACH_UNSUPPORTED);
        }
        if (mode != ProtocolStartupMode.PREMAIN) {
            return Availability.unavailable(Reason.PREMAIN_REQUIRED);
        }
        if (!platformSupported.getAsBoolean()) {
            return Availability.unavailable(Reason.PLATFORM_UNSUPPORTED);
        }
        if (javaHome == null) {
            return Availability.unavailable(Reason.JVM_LIBRARY_UNAVAILABLE);
        }
        try {
            Path maxHook = null;
            boolean officialNativeDirectoryAvailable = false;
            if (layout != null) {
                Path configuredNativeDirectory = layout.nativeDirectory();
                officialNativeDirectoryAvailable = Files.isDirectory(
                    configuredNativeDirectory, LinkOption.NOFOLLOW_LINKS);
                if (officialNativeDirectoryAvailable) {
                    Path nativeDirectory = configuredNativeDirectory.toRealPath();
                    Path configuredMaxHook = nativeDirectory.resolve("MaxHook.dll");
                    if (Files.isRegularFile(configuredMaxHook, LinkOption.NOFOLLOW_LINKS)) {
                        maxHook = configuredMaxHook.toRealPath();
                        if (!nativeDirectory.equals(maxHook.getParent())
                            || !maxHook.startsWith(nativeDirectory)) {
                            return Availability.unavailable(
                                Reason.MAXHOOK_OUTSIDE_NATIVE_DIRECTORY);
                        }
                    }
                }
            }

            // The official client has already loaded and patched SyncToken from
            // this canonical path. Loading the same bytes again from the staged
            // distribution path creates a second module identity and an isolated
            // callback map. Use the packaged copy only when no official native
            // MaxHook is available on this machine.
            if (maxHook == null) {
                Path packagedMaxHook = packagedMaxHookLocator.locate();
                if (packagedMaxHook != null) {
                    if (!Files.isRegularFile(packagedMaxHook, LinkOption.NOFOLLOW_LINKS)) {
                        return Availability.unavailable(Reason.MAXHOOK_UNAVAILABLE);
                    }
                    maxHook = packagedMaxHook.toRealPath();
                }
            }
            if (maxHook == null) {
                if (layout == null) {
                    return Availability.unavailable(Reason.LAYOUT_UNAVAILABLE);
                }
                return Availability.unavailable(officialNativeDirectoryAvailable
                    ? Reason.MAXHOOK_UNAVAILABLE
                    : Reason.NATIVE_DIRECTORY_UNAVAILABLE);
            }
            String maxHookSha256 = sha256(maxHook);
            if (!expectedMaxHookSha256.equals(maxHookSha256)) {
                return Availability.unavailable(Reason.MAXHOOK_HASH_MISMATCH);
            }

            Path jvm = javaHome.resolve("bin").resolve("server").resolve("jvm.dll");
            if (!Files.isRegularFile(jvm, LinkOption.NOFOLLOW_LINKS)) {
                return Availability.unavailable(Reason.JVM_LIBRARY_UNAVAILABLE);
            }
            String jvmSha256 = sha256(jvm.toRealPath());
            if (!allowedJvmSha256.contains(jvmSha256)) {
                return Availability.unavailable(Reason.JVM_HASH_UNSUPPORTED);
            }

            NativeIdentity identity = new NativeIdentity(maxHook, maxHookSha256, jvmSha256);
            Availability loaded = loadRegistry.ensureLoaded(identity, nativeLoader);
            if (!loaded.available()) return loaded;
            return exactSyncTokenAbi()
                ? Availability.callbackReadinessUnverified()
                : Availability.unavailable(Reason.SYNC_TOKEN_ABI_MISMATCH);
        } catch (IOException | RuntimeException error) {
            return Availability.unavailable(Reason.NATIVE_LOAD_FAILED);
        }
    }

    private static boolean exactSyncTokenAbi() {
        try {
            Class<?> type = Class.forName(
                "com.heypixel.heypixelmod.SyncToken",
                false,
                OfficialId114NativeSink.class.getClassLoader()
            );
            if (!Modifier.isPublic(type.getModifiers()) || type.getSuperclass() != Object.class
                || type.getDeclaredFields().length != 0) {
                return false;
            }
            Constructor<?> constructor = type.getDeclaredConstructor();
            Method accept = type.getDeclaredMethod("accept", String.class);
            Method logout = type.getDeclaredMethod("logout");
            return Modifier.isPublic(constructor.getModifiers())
                && Modifier.isPublic(accept.getModifiers())
                && Modifier.isStatic(accept.getModifiers())
                && accept.getReturnType() == void.class
                && Modifier.isPublic(logout.getModifiers())
                && Modifier.isStatic(logout.getModifiers())
                && logout.getReturnType() == void.class
                && type.getDeclaredMethods().length == 2;
        } catch (ReflectiveOperationException | LinkageError error) {
            return false;
        }
    }

    private static Path systemJavaHome() {
        try {
            String value = System.getProperty("java.home", "");
            return value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static Path locatePackagedMaxHook() {
        String explicit = firstNonBlank(
            System.getProperty(MAXHOOK_PATH_PROPERTY, ""),
            System.getenv(MAXHOOK_PATH_ENV)
        );
        if (explicit != null) {
            return Path.of(explicit).toAbsolutePath().normalize();
        }

        String userHome = System.getProperty("user.home", "");
        if (userHome.isBlank()) return null;
        try {
            Path staged = Path.of(userHome)
                .resolve(STAGED_MAXHOOK_RELATIVE_PATH)
                .toAbsolutePath()
                .normalize();
            return Files.exists(staged, LinkOption.NOFOLLOW_LINKS) ? staged : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static boolean isWindowsX64() {
        String os = System.getProperty("os.name", "");
        String arch = System.getProperty("os.arch", "");
        return os.regionMatches(true, 0, "Windows", 0, "Windows".length())
            && ("amd64".equalsIgnoreCase(arch) || "x86_64".equalsIgnoreCase(arch));
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static String normalizeHash(String value) {
        return Objects.requireNonNull(value, "value").toUpperCase(java.util.Locale.ROOT);
    }

    @FunctionalInterface
    interface NativeLoader {
        void load(String canonicalPath);
    }

    @FunctionalInterface
    interface PackagedMaxHookLocator {
        Path locate() throws IOException;
    }

    static final class NativeLoadRegistry {
        private NativeIdentity loadedIdentity;
        private NativeIdentity failedIdentity;
        private Availability failedAvailability;

        synchronized Availability ensureLoaded(NativeIdentity identity, NativeLoader loader) {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(loader, "loader");
            if (loadedIdentity != null) {
                return loadedIdentity.equals(identity)
                    ? Availability.ready()
                    : Availability.unavailable(Reason.NATIVE_IDENTITY_CHANGED);
            }
            if (identity.equals(failedIdentity) && failedAvailability != null) {
                return failedAvailability;
            }
            try {
                loader.load(identity.maxHook().toString());
                loadedIdentity = identity;
                failedIdentity = null;
                failedAvailability = null;
                return Availability.ready();
            } catch (RuntimeException | LinkageError error) {
                failedIdentity = identity;
                failedAvailability = Availability.unavailable(Reason.NATIVE_LOAD_FAILED);
                return failedAvailability;
            }
        }
    }

    private record NativeIdentity(Path maxHook, String maxHookSha256, String jvmSha256) {
        private NativeIdentity {
            Objects.requireNonNull(maxHook, "maxHook");
            Objects.requireNonNull(maxHookSha256, "maxHookSha256");
            Objects.requireNonNull(jvmSha256, "jvmSha256");
        }
    }
}
