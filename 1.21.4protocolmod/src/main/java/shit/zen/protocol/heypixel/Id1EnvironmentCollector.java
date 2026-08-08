package shit.zen.protocol.heypixel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import oshi.SystemInfo;
import oshi.hardware.Baseboard;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HWDiskStore;
import oshi.hardware.NetworkIF;

/** Collects the Java-visible environment fields emitted by the original SPRINT ID1 writer. */
public final class Id1EnvironmentCollector {
    private static final int CURRENT_OFFICIAL_SNEAK_STATE_CODE = -1;
    private static final String OFFICIAL_USER_ID_ARGUMENT_PREFIX = "-DuserId=";
    private static final String OFFICIAL_USER_ID_MAP_KEY = "UserId";
    private static final Pattern FOUND_VALID_MOD = Pattern.compile("Found valid mod file (.*?) with \\{([^}]*)} mods");
    private static final Pattern LOADING_MOD_FILE = Pattern.compile("Loading mod file (.*?) with languages");
    private static final Pattern GENERATED_MOD_PACK = Pattern.compile(
        "Generating PackInfo named mod:([^\\s]+) for mod file(?:\\s+(.*))?$"
    );
    private static final Pattern LOG_TIMESTAMP = Pattern.compile("^\\[([^]]+)]");
    private final Id1RuntimeSignatureProvider signatures;
    private final Id1HwidProvider hwidProvider;
    private final Supplier<Id1HwidProvider.Settings> hwidSettings;
    private final long minimumStartupLogModifiedTime;
    private final boolean useSignedSessionUserId;
    private final StartupSnapshot startupSnapshot;
    private final Id1HwidProvider.HardwareEvidence hardware;
    private final String lastSource;

    public Id1EnvironmentCollector(Id1RuntimeSignatureProvider signatures) {
        this(signatures, () -> null);
    }

    public Id1EnvironmentCollector(Id1RuntimeSignatureProvider signatures, Supplier<Path> preferredGameDirectory) {
        this(signatures, preferredGameDirectory, new Id1HwidProvider(Path.of(System.getProperty("user.dir", "."))),
            Id1HwidProvider.Settings::real);
    }

    public Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        Supplier<Path> preferredInstallRoot,
        Supplier<Path> preferredInstanceDirectory
    ) {
        this(
            signatures,
            preferredInstallRoot,
            preferredInstanceDirectory,
            new Id1HwidProvider(Path.of(System.getProperty("user.dir", "."))),
            Id1HwidProvider.Settings::real
        );
    }

    public Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        Supplier<Path> preferredGameDirectory,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings
    ) {
        this(
            signatures,
            (LayoutProvider) () -> layoutFromLegacyPath(preferredGameDirectory.get()),
            hwidProvider,
            hwidSettings
        );
        Objects.requireNonNull(preferredGameDirectory, "preferredGameDirectory");
    }

    public Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        Supplier<Path> preferredInstallRoot,
        Supplier<Path> preferredInstanceDirectory,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings
    ) {
        this(
            signatures,
            (LayoutProvider) () -> layoutFromPaths(preferredInstallRoot.get(), preferredInstanceDirectory.get()),
            hwidProvider,
            hwidSettings
        );
        Objects.requireNonNull(preferredInstallRoot, "preferredInstallRoot");
        Objects.requireNonNull(preferredInstanceDirectory, "preferredInstanceDirectory");
    }

    /**
     * Builds an immutable snapshot from a separate official installation. The external Forge
     * launch necessarily predates this Fabric JVM, so freshness is established by the latest
     * complete log snapshot plus the current installed JAR evidence instead of this JVM's epoch.
     */
    public static Id1EnvironmentCollector fromExternalOfficialInstall(
        Id1RuntimeSignatureProvider signatures,
        Supplier<Path> preferredInstallRoot,
        Supplier<Path> preferredInstanceDirectory,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings,
        Supplier<String> officialUserDirectory,
        Supplier<String> officialJavaHome
    ) {
        Objects.requireNonNull(preferredInstallRoot, "preferredInstallRoot");
        Objects.requireNonNull(preferredInstanceDirectory, "preferredInstanceDirectory");
        Objects.requireNonNull(officialUserDirectory, "officialUserDirectory");
        Objects.requireNonNull(officialJavaHome, "officialJavaHome");
        return new Id1EnvironmentCollector(
            signatures,
            (LayoutProvider) () -> layoutFromPaths(
                preferredInstallRoot.get(), preferredInstanceDirectory.get()),
            hwidProvider,
            hwidSettings,
            List::of,
            officialUserDirectory,
            officialJavaHome,
            Id1EnvironmentCollector::collectHardware,
            Long.MIN_VALUE,
            true
        );
    }

    private Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        LayoutProvider preferredLayout,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings
    ) {
        this(
            signatures,
            preferredLayout,
            hwidProvider,
            hwidSettings,
            () -> ManagementFactory.getRuntimeMXBean().getInputArguments(),
            () -> System.getProperty("user.dir", ""),
            () -> System.getProperty("java.home", ""),
            Id1EnvironmentCollector::collectHardware
        );
    }

    private Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        LayoutProvider preferredLayout,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings,
        Supplier<List<String>> inputArguments,
        Supplier<String> userDirectory,
        Supplier<String> javaHome,
        Supplier<Id1HwidProvider.HardwareEvidence> hardwareCapture
    ) {
        this(
            signatures,
            preferredLayout,
            hwidProvider,
            hwidSettings,
            inputArguments,
            userDirectory,
            javaHome,
            hardwareCapture,
            ManagementFactory.getRuntimeMXBean().getStartTime(),
            false
        );
    }

    private Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        LayoutProvider preferredLayout,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings,
        Supplier<List<String>> inputArguments,
        Supplier<String> userDirectory,
        Supplier<String> javaHome,
        Supplier<Id1HwidProvider.HardwareEvidence> hardwareCapture,
        long minimumStartupLogModifiedTime,
        boolean useSignedSessionUserId
    ) {
        this.signatures = Objects.requireNonNull(signatures, "signatures");
        this.hwidProvider = Objects.requireNonNull(hwidProvider, "hwidProvider");
        this.hwidSettings = Objects.requireNonNull(hwidSettings, "hwidSettings");
        this.minimumStartupLogModifiedTime = minimumStartupLogModifiedTime;
        this.useSignedSessionUserId = useSignedSessionUserId;
        Objects.requireNonNull(preferredLayout, "preferredLayout");
        Objects.requireNonNull(inputArguments, "inputArguments");
        Objects.requireNonNull(userDirectory, "userDirectory");
        Objects.requireNonNull(javaHome, "javaHome");
        Objects.requireNonNull(hardwareCapture, "hardwareCapture");
        // Official Id1HardwareCollector snapshots network -> disk -> CPU -> system at context construction.
        this.hardware = Objects.requireNonNull(hardwareCapture.get(), "hardwareCapture result");
        this.startupSnapshot = captureStartupSnapshot(
            resolveInstallLayout(preferredLayout.get()),
            inputArguments.get(),
            userDirectory.get(),
            javaHome.get()
        );
        this.lastSource = "official-startup-snapshot";
    }

    Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        HeyPixelInstallLayout layout,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings,
        List<String> inputArguments,
        String userDirectory,
        String javaHome
    ) {
        this(
            signatures,
            () -> Objects.requireNonNull(layout, "layout"),
            hwidProvider,
            hwidSettings,
            () -> List.copyOf(inputArguments),
            () -> userDirectory,
            () -> javaHome,
            Id1EnvironmentCollector::collectHardware
        );
    }

    Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        HeyPixelInstallLayout layout,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings,
        List<String> inputArguments,
        String userDirectory,
        String javaHome,
        Supplier<Id1HwidProvider.HardwareEvidence> hardwareCapture
    ) {
        this(
            signatures,
            () -> Objects.requireNonNull(layout, "layout"),
            hwidProvider,
            hwidSettings,
            () -> List.copyOf(inputArguments),
            () -> userDirectory,
            () -> javaHome,
            hardwareCapture
        );
    }

    Id1EnvironmentCollector(
        Id1RuntimeSignatureProvider signatures,
        HeyPixelInstallLayout layout,
        Id1HwidProvider hwidProvider,
        Supplier<Id1HwidProvider.Settings> hwidSettings,
        List<String> inputArguments,
        String userDirectory,
        String javaHome,
        Supplier<Id1HwidProvider.HardwareEvidence> hardwareCapture,
        long minimumStartupLogModifiedTime
    ) {
        this(
            signatures,
            () -> Objects.requireNonNull(layout, "layout"),
            hwidProvider,
            hwidSettings,
            () -> List.copyOf(inputArguments),
            () -> userDirectory,
            () -> javaHome,
            hardwareCapture,
            minimumStartupLogModifiedTime,
            false
        );
    }

    public Id1BuildInput collect(
        S2CPacketDecoders.Id101Challenge challenge,
        ProtocolSessionSnapshot session,
        UUID localUuid
    ) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(localUuid, "localUuid");
        Object payload = switch (challenge.subtype()) {
            case SPRINT -> collectSprint(session);
            case SNEAK -> currentOfficialSneakEvidence();
            case SWIM -> currentOfficialSwimEvidence();
            case ATTACK -> null;
        };
        return new Id1BuildInput(
            challenge.subtype(),
            new Id1PacketBuilder.Context(localUuid, System.currentTimeMillis()),
            payload
        );
    }

    /**
     * The current official JAR initializes this state to -1/empty and has no reachable mutator
     * for either value. Keep this versioned producer explicit instead of treating it as fallback.
     */
    static Id1PacketBuilder.SneakEvidence currentOfficialSneakEvidence() {
        return new Id1PacketBuilder.SneakEvidence(
            CURRENT_OFFICIAL_SNEAK_STATE_CODE,
            List.of()
        );
    }

    /** Current official runtime evidence keys/map are constructed empty and never mutated. */
    static Id1PacketBuilder.SwimEvidence currentOfficialSwimEvidence() {
        return new Id1PacketBuilder.SwimEvidence(0, Map.of());
    }

    private Id1PacketBuilder.SprintEnvironment collectSprint(ProtocolSessionSnapshot session) {
        Id1HwidProvider.ResolvedHardware resolvedHardware =
            hwidProvider.resolve(hardware, hwidSettings.get());
        Id1HwidProvider.HardwareEvidence currentHardware = resolvedHardware.hardware();
        return new Id1PacketBuilder.SprintEnvironment(
            startupSnapshot.loadedMods(),
            startupSnapshot.userDirectory(),
            startupSnapshot.javaHome(),
            currentHardware.cpuInfo(),
            currentHardware.computerSystemInfo(),
            currentHardware.networkInterfaces(),
            currentHardware.diskStores(),
            startupSnapshot.encodedSiblingDirectories(),
            useSignedSessionUserId
                ? signedSessionUserIdProperties(session)
                : startupSnapshot.launcherProperties(),
            startupSnapshot.discoveredJars(),
            lastSource,
            resolvedHardware.source(),
            resolvedHardware.profile(),
            resolvedHardware.synthetic(),
            resolvedHardware.syntheticId(),
            resolvedHardware.historyCount(),
            startupSnapshot.discoveredJarDigests()
        );
    }

    static Map<String, Object> signedSessionUserIdProperties(ProtocolSessionSnapshot session) {
        if (session == null) {
            throw new IllegalStateException("signed Fantnel session is required for external ID1");
        }
        try {
            return Map.of(OFFICIAL_USER_ID_MAP_KEY, Long.parseLong(session.userId()));
        } catch (NumberFormatException error) {
            throw new IllegalStateException("signed Fantnel userId is not a signed long");
        }
    }

    StartupSnapshot startupSnapshot() {
        return startupSnapshot;
    }

    private StartupSnapshot captureStartupSnapshot(
        HeyPixelInstallLayout layout,
        List<String> inputArguments,
        String userDirectory,
        String javaHome
    ) {
        List<String> discoveredJars = requireOfficialJarSnapshot(discoverModJars(layout.modsDirectory()));
        List<Id1PacketBuilder.ModEvidence> loadedMods = requireOfficialModEvidence(
            collectOfficialMods(layout.installRoot(), discoveredJars)
        );
        LinkedHashMap<String, String> discoveredJarDigests = new LinkedHashMap<>();
        for (String jar : discoveredJars) {
            String digest = signatures.digestPath(Path.of(jar));
            if (digest.isBlank()) {
                throw new IllegalStateException("official top-level JAR digest snapshot is incomplete");
            }
            discoveredJarDigests.put(jar, digest);
        }
        return new StartupSnapshot(
            layout,
            loadedMods,
            wireUserDirectory(userDirectory),
            javaHome == null ? "" : javaHome,
            collectEncodedSiblingDirectories(layout.instanceDirectory()),
            collectOfficialLauncherProperties(inputArguments),
            discoveredJars,
            discoveredJarDigests
        );
    }

    static String wireUserDirectory(String rawUserDirectory) {
        return rawUserDirectory == null ? "" : rawUserDirectory;
    }

    static List<String> collectEncodedSiblingDirectories(Path instanceDirectory) {
        if (instanceDirectory == null) return List.of();
        Path normalizedInstance = instanceDirectory.toAbsolutePath().normalize();
        Path installParent = normalizedInstance.getParent();
        Path accountRoot = installParent == null ? null : installParent.getParent();
        if (accountRoot == null || accountRoot.getFileName() == null) return List.of();
        File[] entries = accountRoot.toFile().listFiles();
        if (entries == null) return List.of();

        String excludedDirectory = installParent.getFileName() == null ? "" : installParent.getFileName().toString();
        String rootName = accountRoot.getFileName().toString();
        List<String> encoded = new ArrayList<>();
        for (File entry : entries) {
            if (!entry.isDirectory() || entry.getName().equals(excludedDirectory)) continue;
            String value = rootName + File.separator + entry.getName();
            encoded.add(Base64.getEncoder().encodeToString(value.getBytes(Charset.defaultCharset())));
        }
        return List.copyOf(encoded);
    }

    static Map<String, Object> collectOfficialLauncherProperties(List<String> inputArguments) {
        Objects.requireNonNull(inputArguments, "inputArguments");
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        for (String argument : inputArguments) {
            if (argument == null || !argument.startsWith(OFFICIAL_USER_ID_ARGUMENT_PREFIX)) continue;
            try {
                long value = Long.parseLong(argument.substring(OFFICIAL_USER_ID_ARGUMENT_PREFIX.length()));
                properties.put(OFFICIAL_USER_ID_MAP_KEY, value);
            } catch (NumberFormatException ignored) {
                throw new IllegalStateException("official launcher userId property is not a signed long");
            }
        }
        return Collections.unmodifiableMap(properties);
    }

    private static HeyPixelInstallLayout resolveInstallLayout(HeyPixelInstallLayout preferred) {
        if (preferred != null) return preferred;

        Path installRoot = configuredPathFrom(
            System.getProperty("mizulune.heypixel.installRoot"),
            "mizulune.heypixel.installRoot"
        );
        Path instanceDirectory = configuredPathFrom(
            System.getProperty("mizulune.heypixel.instanceDir"),
            "mizulune.heypixel.instanceDir"
        );
        if (installRoot == null) {
            installRoot = configuredPathFrom(
                System.getenv("MIZULUNE_HEYPIXEL_INSTALL_ROOT"),
                "MIZULUNE_HEYPIXEL_INSTALL_ROOT"
            );
        }
        if (instanceDirectory == null) {
            instanceDirectory = configuredPathFrom(
                System.getenv("MIZULUNE_HEYPIXEL_INSTANCE_DIR"),
                "MIZULUNE_HEYPIXEL_INSTANCE_DIR"
            );
        }
        if (installRoot != null || instanceDirectory != null) {
            if (installRoot == null || instanceDirectory == null) {
                throw new IllegalStateException(
                    "both explicit HeyPixel install root and instance directory are required");
            }
            return HeyPixelInstallLayout.fromPaths(installRoot, instanceDirectory);
        }

        Path property = configuredPathFrom(
            System.getProperty("mizulune.heypixel.gameDir"),
            "mizulune.heypixel.gameDir"
        );
        if (property != null) return HeyPixelInstallLayout.fromLegacyPath(property);
        Path env = configuredPathFrom(
            System.getenv("MIZULUNE_HEYPIXEL_GAME_DIR"),
            "MIZULUNE_HEYPIXEL_GAME_DIR"
        );
        if (env != null) return HeyPixelInstallLayout.fromLegacyPath(env);
        Path fabricGameDirectory = null;
        try {
            fabricGameDirectory = FabricLoader.getInstance().getGameDir();
        } catch (RuntimeException | LinkageError ignored) {
            // Fabric Loader is unavailable in isolated codec tests.
        }
        if (fabricGameDirectory != null) {
            return HeyPixelInstallLayout.fromLegacyPath(fabricGameDirectory);
        }
        // raw user.dir is an independent wire field and must never be reinterpreted as either official path.
        throw new IllegalStateException(
            "explicit HeyPixel install root and instance sources are unavailable");
    }

    private static HeyPixelInstallLayout layoutFromLegacyPath(Path legacyPath) {
        return legacyPath == null ? null : HeyPixelInstallLayout.fromLegacyPath(legacyPath);
    }

    private static HeyPixelInstallLayout layoutFromPaths(Path installRoot, Path instanceDirectory) {
        if (installRoot == null && instanceDirectory == null) return null;
        if (installRoot == null || instanceDirectory == null) {
            throw new IllegalArgumentException(
                "both HeyPixel install root and instance directory are required");
        }
        return HeyPixelInstallLayout.fromPaths(installRoot, instanceDirectory);
    }

    static List<Id1PacketBuilder.ModEvidence> requireOfficialModEvidence(
        List<Id1PacketBuilder.ModEvidence> officialMods
    ) {
        Objects.requireNonNull(officialMods, "officialMods");
        if (officialMods.size() != Id1PacketBuilder.OFFICIAL_LOADED_MOD_COUNT) {
            throw new IllegalStateException(
                "official 16-entry loading-time ModFileInfo snapshot could not be reconstructed");
        }
        Set<String> modules = new LinkedHashSet<>();
        for (Id1PacketBuilder.ModEvidence mod : officialMods) {
            if (mod.moduleName().isBlank() || mod.digest().isBlank()
                || !modules.add(normalizeModuleName(mod.moduleName()))) {
                throw new IllegalStateException("official loading-time ModFileInfo snapshot is incomplete");
            }
        }
        return List.copyOf(officialMods);
    }

    static List<String> requireOfficialJarSnapshot(List<String> discoveredJars) {
        Objects.requireNonNull(discoveredJars, "discoveredJars");
        if (discoveredJars.size() != Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT
            || new LinkedHashSet<>(discoveredJars).size() != discoveredJars.size()) {
            throw new IllegalStateException("official 13-entry top-level JAR snapshot could not be reconstructed");
        }
        return List.copyOf(discoveredJars);
    }

    public String source() {
        return lastSource;
    }

    List<Id1PacketBuilder.ModEvidence> collectOfficialMods(Path installRoot, List<String> discoveredJars) {
        List<OfficialModFileReference> loadedFiles = readForgeLoadedModFiles(
            installRoot,
            minimumStartupLogModifiedTime
        );
        if (loadedFiles.isEmpty()) return List.of();

        LinkedHashMap<String, Path> topLevelByFileName = new LinkedHashMap<>();
        LinkedHashMap<String, NestedModEvidence> nestedByIdentity = new LinkedHashMap<>();
        for (String jar : discoveredJars) {
            Path path = pathFrom(jar);
            if (path == null) continue;
            topLevelByFileName.putIfAbsent(fileName(path.toString()).toLowerCase(Locale.ROOT), path);
            for (NestedModEvidence nested : readNestedModEvidence(path)) {
                nestedByIdentity.putIfAbsent(nested.identity(), nested);
            }
        }

        List<Id1PacketBuilder.ModEvidence> result = new ArrayList<>(loadedFiles.size());
        for (OfficialModFileReference loadedFile : loadedFiles) {
            Path resolvedPath = resolveOfficialLoadedPath(installRoot, loadedFile, topLevelByFileName);
            if (resolvedPath != null) {
                result.add(new Id1PacketBuilder.ModEvidence(
                    loadedFile.moduleName(),
                    resolvedPath.toString(),
                    signatures.digestPath(resolvedPath)
                ));
                continue;
            }

            NestedModEvidence nested = nestedByIdentity.get(loadedFile.identity());
            if (nested == null) {
                // A partial official list is worse than an explicit fallback because it silently changes
                // the array cardinality. Only select this source when every ModFileInfo is reproducible.
                return List.of();
            }
            result.add(new Id1PacketBuilder.ModEvidence(
                loadedFile.moduleName(),
                loadedFile.path(),
                nested.digest()
            ));
        }
        return List.copyOf(result);
    }

    private static Path resolveOfficialLoadedPath(
        Path installRoot,
        OfficialModFileReference loadedFile,
        Map<String, Path> topLevelByFileName
    ) {
        if (!loadedFile.path().isBlank()) {
            try {
                Path root = installRoot.toAbsolutePath().normalize();
                Path candidate = Path.of(loadedFile.path());
                if (!candidate.isAbsolute()) candidate = root.resolve(candidate);
                candidate = candidate.toAbsolutePath().normalize();
                if (candidate.startsWith(root) && Files.isRegularFile(candidate)) return candidate;
            } catch (RuntimeException ignored) {
            }
        }
        Path topLevel = topLevelByFileName.get(loadedFile.fileName().toLowerCase(Locale.ROOT));
        return topLevel != null && Files.isRegularFile(topLevel) ? topLevel : null;
    }

    private List<NestedModEvidence> readNestedModEvidence(Path jarPath) {
        List<NestedModEvidence> result = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!(name.startsWith("META-INF/jarjar/") || name.startsWith("META-INF/jars/"))
                    || !name.endsWith(".jar")) {
                    continue;
                }
                byte[] bytes = jar.getInputStream(entry).readAllBytes();
                Path temporary = Files.createTempFile("mizulune-nested-mod-", ".jar");
                try {
                    Files.write(temporary, bytes);
                    List<String> modIds = readPrimaryModIds(temporary);
                    if (!modIds.isEmpty()) {
                        result.add(new NestedModEvidence(modIds.get(0), fileName(name), sha1(bytes)));
                    }
                } finally {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
        return List.copyOf(result);
    }

    static List<OfficialModFileReference> readForgeLoadedModFiles(Path installRoot) {
        return readForgeLoadedModFiles(installRoot, Long.MIN_VALUE);
    }

    static List<OfficialModFileReference> readForgeLoadedModFiles(
        Path installRoot,
        long minimumModifiedTime
    ) {
        Path logs = installRoot.resolve("logs");
        List<Path> candidates = List.of(logs.resolve("debug.log"), logs.resolve("latest.log"));
        LogSnapshot newest = null;
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            Path log = candidates.get(candidateIndex);
            if (!Files.isRegularFile(log)) continue;
            BasicFileAttributes before;
            try {
                before = Files.readAttributes(log, BasicFileAttributes.class);
            } catch (IOException ignored) {
                continue;
            }
            long modifiedTime = before.lastModifiedTime().toMillis();
            if (modifiedTime < minimumModifiedTime) continue;
            ParsedStartupSegment parsed = parseLatestForgeStartupSegment(log);
            BasicFileAttributes after;
            try {
                after = Files.readAttributes(log, BasicFileAttributes.class);
            } catch (IOException ignored) {
                continue;
            }
            if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
                continue;
            }
            if (!parsed.hasEvidence()) continue;
            if (minimumModifiedTime != Long.MIN_VALUE
                && parsed.earliestEvidenceTime() < minimumModifiedTime) {
                continue;
            }
            LogSnapshot candidate = new LogSnapshot(parsed, modifiedTime, candidateIndex);
            if (newest == null || candidate.isNewerThan(newest)) newest = candidate;
        }
        return newest == null || !newest.segment().complete()
            ? List.of()
            : newest.segment().loadedFiles();
    }

    private static ParsedStartupSegment parseLatestForgeStartupSegment(Path log) {
        LinkedHashSet<Charset> charsets = new LinkedHashSet<>();
        charsets.add(Charset.defaultCharset());
        charsets.add(Charset.forName("UTF-8"));
        charsets.add(Charset.forName("GB18030"));
        for (Charset charset : charsets) {
            ParsedStartupSegment parsed = parseLatestForgeStartupSegment(log, charset);
            if (parsed.hasEvidence()) return parsed;
        }
        return ParsedStartupSegment.none();
    }

    private static ParsedStartupSegment parseLatestForgeStartupSegment(
        Path log,
        Charset charset
    ) {
        StartupLogSegment current = null;
        PendingModFile pending = null;
        try (BufferedReader reader = Files.newBufferedReader(log, charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                long lineTimestamp = parseLogTimestampMillis(line);
                Matcher found = FOUND_VALID_MOD.matcher(line);
                if (found.find()) {
                    String moduleName = firstModId(found.group(2));
                    if (moduleName.isBlank()) {
                        pending = null;
                        continue;
                    }
                    boolean minecraftAnchor = "minecraft".equals(normalizeModuleName(moduleName));
                    if (current == null
                        || minecraftAnchor && current.hasEvidence()
                        || current.hasGeneratedPackEvidence()) {
                        current = new StartupLogSegment(minecraftAnchor);
                    } else if (minecraftAnchor) {
                        current.markAnchored();
                    }
                    pending = new PendingModFile(moduleName, fileName(found.group(1).trim()));
                    current.markEvidence(lineTimestamp);
                    continue;
                }

                Matcher loading = LOADING_MOD_FILE.matcher(line);
                if (loading.find()) {
                    if (current != null && pending != null) {
                        current.addDiscovered(new OfficialModFileReference(
                            pending.moduleName(),
                            pending.fileName(),
                            loading.group(1).trim()
                        ), lineTimestamp);
                    }
                    pending = null;
                    continue;
                }

                Matcher generatedPack = GENERATED_MOD_PACK.matcher(line);
                if (generatedPack.find()) {
                    if (current == null) current = new StartupLogSegment(false);
                    current.addGenerated(new GeneratedModReference(
                        generatedPack.group(1),
                        generatedPack.group(2) == null ? "" : generatedPack.group(2).trim()
                    ), lineTimestamp);
                }
            }
        } catch (IOException ignored) {
            return ParsedStartupSegment.none();
        }
        return current == null ? ParsedStartupSegment.none() : current.finish(pending != null);
    }

    static long parseLogTimestampMillis(String line) {
        if (line == null) return Long.MIN_VALUE;
        Matcher matcher = LOG_TIMESTAMP.matcher(line);
        if (!matcher.find()) return Long.MIN_VALUE;
        String value = matcher.group(1);
        for (Locale locale : List.of(Locale.getDefault(), Locale.CHINA, Locale.ENGLISH)) {
            try {
                LocalDateTime timestamp = LocalDateTime.parse(
                    value,
                    DateTimeFormatter.ofPattern("ddMMMyyyy HH:mm:ss.SSS", locale)
                );
                return timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        return Long.MIN_VALUE;
    }

    static List<String> readForgeLoadingOrder(Path installRoot) {
        return readForgeLoadedModFiles(installRoot).stream()
            .map(OfficialModFileReference::moduleName)
            .toList();
    }

    static List<String> readPrimaryModIds(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = findToml(jar);
            if (entry == null) return List.of();
            String text = new String(jar.getInputStream(entry).readAllBytes(), Charset.defaultCharset());
            List<String> result = new ArrayList<>();
            boolean inModsBlock = false;
            for (String line : text.split("\\R")) {
                String trimmed = line.trim();
                if ("[[mods]]".equals(trimmed)) {
                    inModsBlock = true;
                    continue;
                }
                if (trimmed.startsWith("[[") && !trimmed.equals("[[mods]]")) {
                    inModsBlock = false;
                }
                if (inModsBlock && trimmed.startsWith("modId")) {
                    String modId = parseTomlStringValue(trimmed);
                    if (!modId.isBlank()) result.add(modId);
                }
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }

    private static JarEntry findToml(JarFile jar) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if ("META-INF/mods.toml".equals(name) || "META-INF/neoforge.mods.toml".equals(name)) {
                return entry;
            }
        }
        return null;
    }

    private static String parseTomlStringValue(String line) {
        int equals = line.indexOf('=');
        if (equals < 0) return "";
        String value = line.substring(equals + 1).trim();
        int start = value.indexOf('"');
        int end = value.indexOf('"', start + 1);
        return start >= 0 && end > start ? value.substring(start + 1, end) : "";
    }

    private static String firstModId(String ids) {
        int comma = ids.indexOf(',');
        return (comma >= 0 ? ids.substring(0, comma) : ids).trim();
    }

    private static String fileName(String path) {
        int slash = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    static List<String> discoverModJars(Path modsDirectory) {
        if (!Files.isDirectory(modsDirectory)) return List.of();
        try (var paths = Files.walk(modsDirectory, 1)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .map(path -> path.toAbsolutePath().normalize().toString())
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static Id1HwidProvider.HardwareEvidence collectHardware() {
        SystemInfo system = new SystemInfo();
        var systemHardware = system.getHardware();

        List<List<String>> networks = new ArrayList<>();
        for (NetworkIF network : systemHardware.getNetworkIFs()) {
            networks.add(List.of(
                safe(network.getName()),
                safe(network.getDisplayName()),
                safe(network.getMacaddr()),
                Arrays.toString(network.getIPv4addr()),
                Arrays.toString(network.getIPv6addr())
            ));
        }

        List<List<String>> disks = new ArrayList<>();
        for (HWDiskStore disk : systemHardware.getDiskStores()) {
            disks.add(List.of(safe(disk.getSerial()), safe(disk.getName()), safe(disk.getModel())));
        }

        CentralProcessor.ProcessorIdentifier processor =
            systemHardware.getProcessor().getProcessorIdentifier();
        List<String> cpu = List.of(
            safe(processor.getProcessorID()),
            safe(processor.getName()),
            safe(processor.getIdentifier())
        );

        ComputerSystem computer = systemHardware.getComputerSystem();
        Baseboard board = computer.getBaseboard();
        List<String> computerSystem = List.of(
            safe(board.getManufacturer()),
            safe(board.getModel()),
            safe(board.getSerialNumber()),
            safe(board.getVersion()),
            safe(computer.getHardwareUUID())
        );
        return new Id1HwidProvider.HardwareEvidence(cpu, computerSystem, List.copyOf(networks), List.copyOf(disks));
    }

    private static Path pathFrom(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-1 is unavailable", error);
        }
    }

    record OfficialModFileReference(String moduleName, String fileName, String path) {
        private String identity() {
            return Id1EnvironmentCollector.identity(moduleName, fileName);
        }
    }

    static Path configuredPathFrom(String value, String source) {
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            throw new IllegalStateException(source + " is not a valid path");
        }
    }

    record StartupSnapshot(
        HeyPixelInstallLayout layout,
        List<Id1PacketBuilder.ModEvidence> loadedMods,
        String userDirectory,
        String javaHome,
        List<String> encodedSiblingDirectories,
        Map<String, Object> launcherProperties,
        List<String> discoveredJars,
        Map<String, String> discoveredJarDigests
    ) {
        StartupSnapshot {
            layout = Objects.requireNonNull(layout, "layout");
            loadedMods = List.copyOf(loadedMods);
            userDirectory = Objects.requireNonNull(userDirectory, "userDirectory");
            javaHome = Objects.requireNonNull(javaHome, "javaHome");
            encodedSiblingDirectories = List.copyOf(encodedSiblingDirectories);
            launcherProperties = Collections.unmodifiableMap(new LinkedHashMap<>(launcherProperties));
            discoveredJars = List.copyOf(discoveredJars);
            discoveredJarDigests = Collections.unmodifiableMap(new LinkedHashMap<>(discoveredJarDigests));
        }
    }

    private record PendingModFile(String moduleName, String fileName) {
    }

    private record GeneratedModReference(String moduleName, String path) {
    }

    private record ParsedStartupSegment(
        boolean hasEvidence,
        boolean complete,
        List<OfficialModFileReference> loadedFiles,
        long earliestEvidenceTime,
        long latestEvidenceTime
    ) {
        private ParsedStartupSegment {
            loadedFiles = List.copyOf(loadedFiles);
        }

        private static ParsedStartupSegment none() {
            return new ParsedStartupSegment(
                false,
                false,
                List.of(),
                Long.MAX_VALUE,
                Long.MIN_VALUE
            );
        }

        private static ParsedStartupSegment incomplete(
            long earliestEvidenceTime,
            long latestEvidenceTime
        ) {
            return new ParsedStartupSegment(
                true,
                false,
                List.of(),
                earliestEvidenceTime,
                latestEvidenceTime
            );
        }

        private static ParsedStartupSegment complete(
            List<OfficialModFileReference> loadedFiles,
            long earliestEvidenceTime,
            long latestEvidenceTime
        ) {
            return new ParsedStartupSegment(
                true,
                true,
                loadedFiles,
                earliestEvidenceTime,
                latestEvidenceTime
            );
        }
    }

    private record LogSnapshot(ParsedStartupSegment segment, long modifiedTime, int candidatePriority) {
        private boolean isNewerThan(LogSnapshot other) {
            return modifiedTime > other.modifiedTime
                || modifiedTime == other.modifiedTime && candidatePriority > other.candidatePriority;
        }
    }

    private static final class StartupLogSegment {
        private final LinkedHashMap<String, List<OfficialModFileReference>> discovered = new LinkedHashMap<>();
        private final List<GeneratedModReference> generated = new ArrayList<>();
        private boolean anchored;
        private boolean evidence;
        private long earliestEvidenceTime = Long.MAX_VALUE;
        private long latestEvidenceTime = Long.MIN_VALUE;

        private StartupLogSegment(boolean anchored) {
            this.anchored = anchored;
        }

        private void markAnchored() {
            anchored = true;
        }

        private void markEvidence(long timestamp) {
            evidence = true;
            earliestEvidenceTime = Math.min(earliestEvidenceTime, timestamp);
            latestEvidenceTime = Math.max(latestEvidenceTime, timestamp);
        }

        private boolean hasEvidence() {
            return evidence || !discovered.isEmpty() || !generated.isEmpty();
        }

        private boolean hasGeneratedPackEvidence() {
            return !generated.isEmpty();
        }

        private void addDiscovered(OfficialModFileReference reference, long timestamp) {
            markEvidence(timestamp);
            discovered.computeIfAbsent(normalizeModuleName(reference.moduleName()), ignored -> new ArrayList<>())
                .add(reference);
        }

        private void addGenerated(GeneratedModReference reference, long timestamp) {
            markEvidence(timestamp);
            generated.add(reference);
        }

        private ParsedStartupSegment finish(boolean danglingDiscovery) {
            if (!anchored || danglingDiscovery || generated.isEmpty()) {
                return ParsedStartupSegment.incomplete(earliestEvidenceTime, latestEvidenceTime);
            }
            List<OfficialModFileReference> minecraftFiles = discovered.get("minecraft");
            if (minecraftFiles == null || minecraftFiles.isEmpty()) {
                return ParsedStartupSegment.incomplete(earliestEvidenceTime, latestEvidenceTime);
            }

            LinkedHashSet<String> remaining = new LinkedHashSet<>(discovered.keySet());
            remaining.remove("minecraft");
            if (generated.size() != remaining.size()) {
                return ParsedStartupSegment.incomplete(earliestEvidenceTime, latestEvidenceTime);
            }

            List<OfficialModFileReference> ordered = new ArrayList<>(discovered.size());
            ordered.add(minecraftFiles.get(minecraftFiles.size() - 1));
            Set<String> generatedModules = new LinkedHashSet<>();
            for (GeneratedModReference generatedReference : generated) {
                String moduleName = normalizeModuleName(generatedReference.moduleName());
                if (!remaining.remove(moduleName) || !generatedModules.add(moduleName)) {
                    return ParsedStartupSegment.incomplete(earliestEvidenceTime, latestEvidenceTime);
                }
                List<OfficialModFileReference> candidates = discovered.get(moduleName);
                OfficialModFileReference selected = selectGeneratedIdentity(candidates, generatedReference.path());
                if (selected == null) {
                    return ParsedStartupSegment.incomplete(earliestEvidenceTime, latestEvidenceTime);
                }
                ordered.add(selected);
            }
            return remaining.isEmpty()
                ? ParsedStartupSegment.complete(ordered, earliestEvidenceTime, latestEvidenceTime)
                : ParsedStartupSegment.incomplete(earliestEvidenceTime, latestEvidenceTime);
        }
    }

    private record NestedModEvidence(String moduleName, String fileName, String digest) {
        private String identity() {
            return Id1EnvironmentCollector.identity(moduleName, fileName);
        }
    }

    private static String identity(String moduleName, String fileName) {
        return moduleName.toLowerCase(Locale.ROOT) + '\0' + fileName.toLowerCase(Locale.ROOT);
    }

    private static OfficialModFileReference selectGeneratedIdentity(
        List<OfficialModFileReference> candidates,
        String generatedPath
    ) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (generatedPath == null || generatedPath.isBlank()) {
            return candidates.get(candidates.size() - 1);
        }
        String normalizedGeneratedPath = normalizeLogPath(generatedPath);
        for (int index = candidates.size() - 1; index >= 0; index--) {
            OfficialModFileReference candidate = candidates.get(index);
            if (!candidate.path().isBlank()
                && normalizeLogPath(candidate.path()).equals(normalizedGeneratedPath)) {
                return candidate;
            }
        }
        String generatedFileName = fileName(generatedPath);
        for (int index = candidates.size() - 1; index >= 0; index--) {
            OfficialModFileReference candidate = candidates.get(index);
            if (candidate.fileName().equalsIgnoreCase(generatedFileName)) return candidate;
        }
        return null;
    }

    private static String normalizeModuleName(String moduleName) {
        return moduleName.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLogPath(String path) {
        return path.trim().replace('/', '\\').toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface LayoutProvider {
        HeyPixelInstallLayout get();
    }
}
