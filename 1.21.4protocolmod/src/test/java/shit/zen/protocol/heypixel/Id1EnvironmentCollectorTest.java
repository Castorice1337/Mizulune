package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Id1EnvironmentCollectorTest {
    @Test
    void usesTheCurrentOfficialDefaultOnlySneakAndSwimEvidenceState() {
        Id1PacketBuilder.SneakEvidence sneak =
            Id1EnvironmentCollector.currentOfficialSneakEvidence();
        Id1PacketBuilder.SwimEvidence swim =
            Id1EnvironmentCollector.currentOfficialSwimEvidence();

        assertEquals(-1, sneak.stateCode());
        assertTrue(sneak.values().isEmpty());
        assertEquals(0, swim.evidenceKeyCount());
        assertTrue(swim.valuesByKey().isEmpty());
    }

    @Test
    void encodesSiblingsFromTwoParentsAboveTheMinecraftInstance(@TempDir Path directory) throws Exception {
        Path gameRoot = Files.createDirectories(directory.resolve("Game"));
        Path installRoot = Files.createDirectories(gameRoot.resolve(".minecraft"));
        Path instanceDirectory = Files.createDirectories(installRoot.resolve("official-instance"));
        Files.createDirectories(gameRoot.resolve("account-sample"));
        Files.createDirectories(gameRoot.resolve("123456"));
        Files.writeString(gameRoot.resolve("not-a-directory.txt"), "ignored");

        List<String> values = Id1EnvironmentCollector.collectEncodedSiblingDirectories(instanceDirectory);

        assertEquals(2, values.size());
        assertTrue(values.contains(encoded("Game" + File.separator + "account-sample")));
        assertTrue(values.contains(encoded("Game" + File.separator + "123456")));
        assertFalse(values.contains(encoded("Game" + File.separator + ".minecraft")));
    }

    @Test
    void preservesTheRawUserDirectoryWireValue() {
        String raw = ".\\launch-root\\..\\launch-root";

        assertEquals(raw, Id1EnvironmentCollector.wireUserDirectory(raw));
        assertEquals("", Id1EnvironmentCollector.wireUserDirectory(null));
    }

    @Test
    void derivesLauncherMapFromSignedRuntimeInputArguments() {
        Map<String, Object> properties = Id1EnvironmentCollector.collectOfficialLauncherProperties(List.of(
            "-Xmx4G",
            "-DuserId=12",
            "-Dunrelated=999",
            "-DuserId=-42"
        ));

        assertEquals(Map.of("UserId", -42L), properties);
        assertEquals(Map.of(), Id1EnvironmentCollector.collectOfficialLauncherProperties(List.of("-Xms1G")));
    }

    @Test
    void failsConservativelyForMalformedOrUnsignedLauncherUserIds() {
        IllegalStateException malformed = assertThrows(IllegalStateException.class,
            () -> Id1EnvironmentCollector.collectOfficialLauncherProperties(List.of("-DuserId=not-a-long")));
        IllegalStateException unsignedOverflow = assertThrows(IllegalStateException.class,
            () -> Id1EnvironmentCollector.collectOfficialLauncherProperties(
                List.of("-DuserId=18446744073709551615")));

        assertEquals("official launcher userId property is not a signed long", malformed.getMessage());
        assertEquals("official launcher userId property is not a signed long", unsignedOverflow.getMessage());
        assertFalse(malformed.getMessage().contains("not-a-long"));
        assertEquals(null, malformed.getCause());
    }

    @Test
    void mapsTheValidatedFantnelSessionUserIdToTheOfficialSignedLongShape() {
        assertEquals(
            Map.of("UserId", -42L),
            Id1EnvironmentCollector.signedSessionUserIdProperties(session("-42"))
        );
    }

    @Test
    void rejectsNonNumericFantnelUserIdsWithoutEchoingThem() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> Id1EnvironmentCollector.signedSessionUserIdProperties(
                session("private-user-value")));

        assertFalse(error.getMessage().contains("private-user-value"));
        assertEquals(null, error.getCause());
    }

    @Test
    void rejectsInvalidNonBlankAutomaticPathSourcesWithoutFallback() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> Id1EnvironmentCollector.configuredPathFrom("\u0000", "test.property"));

        assertEquals("test.property is not a valid path", error.getMessage());
        assertEquals(null, error.getCause());
    }

    @Test
    void readsOnlyPrimaryModsTomlBlocks(@TempDir Path directory) throws Exception {
        Path jar = directory.resolve("example.jar");
        writeJar(jar, """
            modLoader="javafml"
            [[mods]]
            modId="first"
            [[mods]]
            modId="second"
            [[dependencies.first]]
            modId="forge"
            [[dependencies.second]]
            modId="minecraft"
            """);

        assertEquals(List.of("first", "second"), Id1EnvironmentCollector.readPrimaryModIds(jar));
    }

    @Test
    void failsClosedWhenPostSortEvidenceIsMissing(@TempDir Path directory) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Files.writeString(logs.resolve("debug.log"), """
            Found valid mod file client.jar with {minecraft} mods - versions {1.20.1}
            Loading mod file client.jar with languages []
            Found valid mod file alpha.jar with {alpha} mods - versions {1.0}
            Loading mod file alpha.jar with languages []
            """, Charset.defaultCharset());

        assertEquals(List.of(), Id1EnvironmentCollector.readForgeLoadingOrder(directory));
        assertThrows(IllegalStateException.class,
            () -> Id1EnvironmentCollector.requireOfficialModEvidence(List.of()));
    }

    @Test
    void failsClosedForPartialPostSortPrefixOrSuffix(@TempDir Path directory) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path debug = logs.resolve("debug.log");
        Files.writeString(debug, """
            Found valid mod file client.jar with {minecraft} mods - versions {1.20.1}
            Loading mod file client.jar with languages []
            Found valid mod file alpha.jar with {alpha} mods - versions {1.0}
            Loading mod file alpha.jar with languages []
            Found valid mod file beta.jar with {beta} mods - versions {1.0}
            Loading mod file beta.jar with languages []
            Generating PackInfo named mod:beta for mod file beta.jar
            """, Charset.defaultCharset());

        assertEquals(List.of(), Id1EnvironmentCollector.readForgeLoadingOrder(directory));

        Files.writeString(debug, """
            Found valid mod file alpha.jar with {alpha} mods - versions {1.0}
            Loading mod file alpha.jar with languages []
            Found valid mod file beta.jar with {beta} mods - versions {1.0}
            Loading mod file beta.jar with languages []
            Generating PackInfo named mod:alpha for mod file alpha.jar
            Generating PackInfo named mod:beta for mod file beta.jar
            """, Charset.defaultCharset());

        assertEquals(List.of(), Id1EnvironmentCollector.readForgeLoadingOrder(directory));
    }

    @Test
    void selectsTheLatestCompleteLogInsteadOfTheLargerOldLaunch(@TempDir Path directory) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path debug = logs.resolve("debug.log");
        Path latest = logs.resolve("latest.log");
        Files.writeString(debug, completeLog(
            List.of("old-alpha", "old-beta", "old-gamma"),
            Map.of()
        ), Charset.defaultCharset());
        Files.writeString(latest, completeLog(List.of("current-alpha"), Map.of()), Charset.defaultCharset());
        Files.setLastModifiedTime(debug, FileTime.fromMillis(1_000L));
        Files.setLastModifiedTime(latest, FileTime.fromMillis(2_000L));

        assertEquals(
            List.of("minecraft", "current-alpha"),
            Id1EnvironmentCollector.readForgeLoadingOrder(directory)
        );
    }

    @Test
    void doesNotFallBackToAnOldCompleteLaunchWhenTheNewestEvidenceIsPartial(
        @TempDir Path directory
    ) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path debug = logs.resolve("debug.log");
        Path latest = logs.resolve("latest.log");
        Files.writeString(debug, completeLog(List.of("old-alpha", "old-beta"), Map.of()),
            Charset.defaultCharset());
        Files.writeString(latest, """
            Found valid mod file client.jar with {minecraft} mods - versions {1.20.1}
            Loading mod file client.jar with languages []
            Found valid mod file current-alpha.jar with {current-alpha} mods - versions {1.0}
            Loading mod file current-alpha.jar with languages []
            """, Charset.defaultCharset());
        Files.setLastModifiedTime(debug, FileTime.fromMillis(1_000L));
        Files.setLastModifiedTime(latest, FileTime.fromMillis(2_000L));

        assertEquals(List.of(), Id1EnvironmentCollector.readForgeLoadingOrder(directory));
    }

    @Test
    void doesNotFallBackWhenTheNewestLogHasNoStartupEvidence(@TempDir Path directory) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path debug = logs.resolve("debug.log");
        Path latest = logs.resolve("latest.log");
        Files.writeString(debug, completeLog(List.of("old-alpha"), Map.of()), Charset.defaultCharset());
        Files.writeString(latest, "current launch without debug-level mod discovery\n", Charset.defaultCharset());
        Files.setLastModifiedTime(debug, FileTime.fromMillis(1_000L));
        Files.setLastModifiedTime(latest, FileTime.fromMillis(2_000L));

        assertEquals(
            List.of(),
            Id1EnvironmentCollector.readForgeLoadedModFiles(directory, 1_500L)
        );
    }

    @Test
    void usesCurrentDebugEvidenceWhenLatestHasNoDebugMarkers(@TempDir Path directory) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path debug = logs.resolve("debug.log");
        Path latest = logs.resolve("latest.log");
        Files.writeString(debug, completeLog(List.of("current-alpha"), Map.of()), Charset.defaultCharset());
        Files.writeString(latest, "current launch without debug-level mod discovery\n", Charset.defaultCharset());
        Files.setLastModifiedTime(debug, FileTime.fromMillis(1_000L));
        Files.setLastModifiedTime(latest, FileTime.fromMillis(2_000L));

        assertEquals(
            List.of("minecraft", "current-alpha"),
            Id1EnvironmentCollector.readForgeLoadedModFiles(directory, 500L).stream()
                .map(Id1EnvironmentCollector.OfficialModFileReference::moduleName)
                .toList()
        );
    }

    @Test
    void rejectsAnOldCompleteSegmentWhenOnlyOrdinaryCurrentLinesWereAppended(
        @TempDir Path directory
    ) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path debug = logs.resolve("debug.log");
        LocalDateTime oldLaunch = LocalDateTime.now().minusHours(2);
        Files.writeString(debug,
            completeLogAt(oldLaunch, List.of("old-alpha"), Map.of())
                + logPrefix(LocalDateTime.now()) + "ordinary current-launch message\n",
            Charset.defaultCharset());

        assertEquals(
            List.of(),
            Id1EnvironmentCollector.readForgeLoadedModFiles(
                directory,
                System.currentTimeMillis() - 60_000L
            )
        );
    }

    @Test
    void rejectsCrossEpochDiscoveryCombinedWithCurrentGeneratedEvidence(
        @TempDir Path directory
    ) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path debug = logs.resolve("debug.log");
        LocalDateTime oldLaunch = LocalDateTime.now().minusHours(2);
        LocalDateTime currentLaunch = LocalDateTime.now();
        StringBuilder mixed = new StringBuilder();
        appendDiscovery(mixed, "client.jar", "minecraft", "client.jar", oldLaunch);
        appendDiscovery(mixed, "alpha.jar", "alpha", "alpha.jar", oldLaunch);
        mixed.append(logPrefix(currentLaunch))
            .append("Generating PackInfo named mod:alpha for mod file alpha.jar\n");
        Files.writeString(debug, mixed, Charset.defaultCharset());
        Files.setLastModifiedTime(debug, FileTime.fromMillis(System.currentTimeMillis()));

        assertEquals(
            List.of(),
            Id1EnvironmentCollector.readForgeLoadedModFiles(
                directory,
                currentLaunch.minusMinutes(1)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            )
        );
    }

    @Test
    void parsesForgeChineseAndEnglishLogTimestamps() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 7, 13, 19, 42, 13, 446_000_000);
        long expected = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(expected, Id1EnvironmentCollector.parseLogTimestampMillis(
            '[' + timestamp.format(DateTimeFormatter.ofPattern("ddMMMyyyy HH:mm:ss.SSS", Locale.CHINA))
                + "] [main/DEBUG] test"));
        assertEquals(expected, Id1EnvironmentCollector.parseLogTimestampMillis(
            '[' + timestamp.format(DateTimeFormatter.ofPattern("ddMMMyyyy HH:mm:ss.SSS", Locale.ENGLISH))
                + "] [main/DEBUG] test"));
    }

    @Test
    void rejectsCompleteStartupEvidenceOlderThanTheCurrentJvmEpoch(@TempDir Path directory) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Path debug = logs.resolve("debug.log");
        Files.writeString(debug, completeLog(List.of("old-alpha"), Map.of()), Charset.defaultCharset());
        Files.setLastModifiedTime(debug, FileTime.fromMillis(1_000L));

        assertEquals(
            List.of(),
            Id1EnvironmentCollector.readForgeLoadedModFiles(directory, 2_000L)
        );
    }

    @Test
    void selectsTheLatestCompleteSegmentWithinOneLog(@TempDir Path directory) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Files.writeString(logs.resolve("debug.log"),
            completeLog(List.of("old-alpha", "old-beta"), Map.of())
                + completeLog(List.of("current-alpha"), Map.of()),
            Charset.defaultCharset());

        assertEquals(
            List.of("minecraft", "current-alpha"),
            Id1EnvironmentCollector.readForgeLoadingOrder(directory)
        );
    }

    @Test
    void duplicateModuleUsesTheCurrentSegmentsFinalFileIdentity(@TempDir Path directory) throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        Files.writeString(logs.resolve("debug.log"), """
            Found valid mod file client.jar with {minecraft} mods - versions {1.20.1}
            Loading mod file client.jar with languages []
            Found valid mod file old-alpha.jar with {alpha} mods - versions {1.0}
            Loading mod file old-alpha.jar with languages []
            Found valid mod file current-alpha.jar with {alpha} mods - versions {2.0}
            Loading mod file current-alpha.jar with languages []
            Generating PackInfo named mod:alpha for mod file
            """, Charset.defaultCharset());

        List<Id1EnvironmentCollector.OfficialModFileReference> files =
            Id1EnvironmentCollector.readForgeLoadedModFiles(directory);

        assertEquals(2, files.size());
        assertEquals("current-alpha.jar", files.get(1).fileName());
        assertEquals("current-alpha.jar", files.get(1).path());
    }

    @Test
    void capturesCompleteSixteenPlusThirteenStartupSnapshotAndIgnoresLaterDiskChanges(
        @TempDir Path directory
    ) throws Exception {
        OfficialFixture fixture = createOfficialFixture(directory);
        String rawUserDirectory = ".\\raw-launch-root\\.";
        Id1EnvironmentCollector collector = new Id1EnvironmentCollector(
            signatures(),
            fixture.layout(),
            new Id1HwidProvider(directory.resolve("hwid")),
            Id1HwidProvider.Settings::real,
            List.of("-DuserId=-7", "-Dtoken=redacted"),
            rawUserDirectory,
            "java-home"
        );
        Id1EnvironmentCollector.StartupSnapshot snapshot = collector.startupSnapshot();
        List<String> jarPaths = fixture.topLevelJars().stream()
            .map(path -> path.toAbsolutePath().normalize().toString())
            .toList();
        Map<String, String> originalDigests = new LinkedHashMap<>(snapshot.discoveredJarDigests());

        assertEquals(Id1PacketBuilder.OFFICIAL_LOADED_MOD_COUNT, snapshot.loadedMods().size());
        assertEquals(fixture.loadedOrder(), snapshot.loadedMods().stream()
            .map(Id1PacketBuilder.ModEvidence::moduleName)
            .toList());
        assertEquals(Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT, snapshot.discoveredJars().size());
        assertEquals(jarPaths, snapshot.discoveredJars());
        assertEquals(Map.of("UserId", -7L), snapshot.launcherProperties());
        assertEquals(rawUserDirectory, snapshot.userDirectory());
        assertTrue(snapshot.encodedSiblingDirectories().contains(
            encoded("Game" + File.separator + "account-one")));

        Files.writeString(fixture.topLevelJars().get(0), "changed-after-collector");
        Files.delete(fixture.topLevelJars().get(1));
        writeJar(fixture.layout().modsDirectory().resolve("late.jar"), modsToml("late"));
        Files.writeString(fixture.layout().logsDirectory().resolve("debug.log"), "truncated-new-launch");

        assertSame(snapshot, collector.startupSnapshot());
        assertEquals(jarPaths, collector.startupSnapshot().discoveredJars());
        assertEquals(originalDigests, collector.startupSnapshot().discoveredJarDigests());
        assertEquals(fixture.loadedOrder(), collector.startupSnapshot().loadedMods().stream()
            .map(Id1PacketBuilder.ModEvidence::moduleName)
            .toList());
    }

    @Test
    void capturesHardwareExactlyOnceDuringContextConstruction(@TempDir Path directory)
        throws Exception {
        OfficialFixture fixture = createOfficialFixture(directory);
        AtomicInteger captures = new AtomicInteger();
        Id1HwidProvider.HardwareEvidence expected = new Id1HwidProvider.HardwareEvidence(
            List.of("cpu-id", "cpu-name", "cpu-identifier"),
            List.of("board-maker", "board-model", "board-serial", "board-version", "hardware-uuid"),
            List.of(List.of("nic", "display", "mac", "[v4]", "[v6]")),
            List.of(List.of("disk-serial", "disk-name", "disk-model"))
        );
        Id1EnvironmentCollector collector = new Id1EnvironmentCollector(
            signatures(),
            fixture.layout(),
            new Id1HwidProvider(directory.resolve("hwid")),
            Id1HwidProvider.Settings::real,
            List.of("-DuserId=-7"),
            "raw-user-dir",
            "java-home",
            () -> {
                captures.incrementAndGet();
                return expected;
            }
        );
        assertEquals(1, captures.get());

        UUID uuid = new UUID(7L, 9L);
        S2CPacketDecoders.Id101Challenge challenge = new S2CPacketDecoders.Id101Challenge(
            uuid, 11L, Id1PacketBuilder.Id1Subtype.SPRINT, null);
        Id1PacketBuilder.SprintEnvironment first = (Id1PacketBuilder.SprintEnvironment)
            collector.collect(challenge, null, uuid).subtypePayload();
        Id1PacketBuilder.SprintEnvironment second = (Id1PacketBuilder.SprintEnvironment)
            collector.collect(challenge, null, uuid).subtypePayload();

        assertEquals(expected.cpuInfo(), first.cpuInfo());
        assertEquals(expected.computerSystemInfo(), first.computerSystemInfo());
        assertEquals(expected.networkInterfaces(), first.networkInterfaces());
        assertEquals(expected.diskStores(), first.diskStores());
        assertEquals(first.cpuInfo(), second.cpuInfo());
        assertEquals(1, captures.get());
    }

    @Test
    void externalOfficialInstallAcceptsACompleteSnapshotFromBeforeThisJvm(
        @TempDir Path directory
    ) throws Exception {
        OfficialFixture fixture = createOfficialFixture(directory);
        Files.setLastModifiedTime(
            fixture.layout().logsDirectory().resolve("debug.log"),
            FileTime.fromMillis(1_000L)
        );

        Id1EnvironmentCollector collector = new Id1EnvironmentCollector(
            signatures(),
            fixture.layout(),
            new Id1HwidProvider(directory.resolve("hwid")),
            Id1HwidProvider.Settings::real,
            List.of("-DuserId=-7"),
            "raw-user-dir",
            "java-home",
            () -> new Id1HwidProvider.HardwareEvidence(
                List.of(), List.of(), List.of(), List.of()),
            Long.MIN_VALUE
        );

        assertEquals(Id1PacketBuilder.OFFICIAL_LOADED_MOD_COUNT,
            collector.startupSnapshot().loadedMods().size());
        assertEquals(Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT,
            collector.startupSnapshot().discoveredJars().size());
    }

    @Test
    void externalFactoryUsesOfficialRuntimePathsAndSignedSessionUserId(
        @TempDir Path directory
    ) throws Exception {
        OfficialFixture fixture = createOfficialFixture(directory);
        String officialUserDirectory = fixture.layout().installRoot().toString();
        String officialJavaHome = directory.resolve("official-jdk17").toString();
        Id1EnvironmentCollector collector = Id1EnvironmentCollector.fromExternalOfficialInstall(
            signatures(),
            () -> fixture.layout().installRoot(),
            () -> fixture.layout().instanceDirectory(),
            new Id1HwidProvider(directory.resolve("hwid")),
            Id1HwidProvider.Settings::real,
            () -> officialUserDirectory,
            () -> officialJavaHome
        );
        UUID uuid = new UUID(7L, 9L);
        S2CPacketDecoders.Id101Challenge challenge = new S2CPacketDecoders.Id101Challenge(
            uuid, 11L, Id1PacketBuilder.Id1Subtype.SPRINT, null);

        Id1PacketBuilder.SprintEnvironment sprint = (Id1PacketBuilder.SprintEnvironment)
            collector.collect(challenge, session("-42"), uuid).subtypePayload();

        assertEquals(officialUserDirectory, sprint.userDirectory());
        assertEquals(officialJavaHome, sprint.javaHome());
        assertEquals(Map.of("UserId", -42L), sprint.userProperties());
    }

    private static String completeLog(List<String> modules, Map<String, String> generatedPaths) {
        return completeLogAt(LocalDateTime.now(), modules, generatedPaths);
    }

    private static String completeLogAt(
        LocalDateTime timestamp,
        List<String> modules,
        Map<String, String> generatedPaths
    ) {
        StringBuilder log = new StringBuilder();
        appendDiscovery(log, "client.jar", "minecraft", "client.jar", timestamp);
        for (String module : modules) {
            appendDiscovery(log, module + ".jar", module, module + ".jar", timestamp);
        }
        for (String module : modules) {
            log.append(logPrefix(timestamp))
                .append("Generating PackInfo named mod:").append(module).append(" for mod file");
            String path = generatedPaths.get(module);
            if (path != null && !path.isBlank()) log.append(' ').append(path);
            log.append('\n');
        }
        return log.toString();
    }

    private static OfficialFixture createOfficialFixture(Path directory) throws Exception {
        Path gameRoot = Files.createDirectories(directory.resolve("Game"));
        Path installRoot = Files.createDirectories(gameRoot.resolve(".minecraft"));
        Path instanceDirectory = Files.createDirectories(installRoot.resolve("official-instance"));
        Path mods = Files.createDirectories(installRoot.resolve("mods"));
        Path logs = Files.createDirectories(installRoot.resolve("logs"));
        Path libraries = Files.createDirectories(installRoot.resolve("libraries"));
        Files.createDirectories(installRoot.resolve("native"));
        Files.createDirectories(installRoot.resolve("versions"));
        Files.createDirectories(gameRoot.resolve("account-one"));

        byte[] nested = jarBytes(modsToml("mixinextras"));
        for (int index = 1; index <= Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT; index++) {
            String module = moduleName(index);
            Path jar = mods.resolve(String.format("%02d-%s.jar", index, module));
            if (index == 1) {
                writeJarWithNested(jar, modsToml(module), "META-INF/jarjar/mixinextras.jar", nested);
            } else {
                writeJar(jar, modsToml(module));
            }
        }
        Path nestedDirectory = Files.createDirectories(mods.resolve("nested"));
        writeJar(nestedDirectory.resolve("must-not-enter-top-level.jar"), modsToml("nested_ignored"));

        Path minecraft = libraries.resolve("minecraft-client.jar");
        Path forge = libraries.resolve("forge-universal.jar");
        writeJar(minecraft, modsToml("minecraft"));
        writeJar(forge, modsToml("forge"));

        List<Path> topLevelJars = Id1EnvironmentCollector.discoverModJars(mods).stream()
            .map(Path::of)
            .toList();
        Map<String, Path> jarByModule = new LinkedHashMap<>();
        for (Path jar : topLevelJars) jarByModule.put(moduleFromJar(jar), jar);
        List<String> generatedOrder = new ArrayList<>();
        for (int index = 1; index <= Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT; index++) {
            generatedOrder.add(moduleName(index));
        }
        generatedOrder.add("forge");
        generatedOrder.add("mixinextras");

        StringBuilder log = new StringBuilder();
        appendDiscovery(log, minecraft.getFileName().toString(), "minecraft", minecraft.toString());
        for (Path jar : topLevelJars) {
            appendDiscovery(log, jar.getFileName().toString(), moduleFromJar(jar), jar.toString());
        }
        appendDiscovery(log, forge.getFileName().toString(), "forge", forge.toString());
        appendDiscovery(log, "mixinextras.jar", "mixinextras", "");
        for (String module : generatedOrder) {
            log.append(logPrefix(LocalDateTime.now()))
                .append("Generating PackInfo named mod:").append(module).append(" for mod file");
            if (module.startsWith("module")) {
                log.append(' ').append(jarByModule.get(module));
            } else if ("forge".equals(module)) {
                log.append(' ').append(forge);
            }
            log.append('\n');
        }
        Files.writeString(logs.resolve("debug.log"), log, Charset.defaultCharset());

        List<String> loadedOrder = new ArrayList<>();
        loadedOrder.add("minecraft");
        loadedOrder.addAll(generatedOrder);
        return new OfficialFixture(
            HeyPixelInstallLayout.fromPaths(installRoot, instanceDirectory),
            topLevelJars,
            List.copyOf(loadedOrder)
        );
    }

    private static void appendDiscovery(
        StringBuilder log,
        String fileName,
        String moduleName,
        String path
    ) {
        appendDiscovery(log, fileName, moduleName, path, LocalDateTime.now());
    }

    private static void appendDiscovery(
        StringBuilder log,
        String fileName,
        String moduleName,
        String path,
        LocalDateTime timestamp
    ) {
        log.append(logPrefix(timestamp)).append("Found valid mod file ").append(fileName)
            .append(" with {").append(moduleName).append("} mods - versions {1.0}\n")
            .append(logPrefix(timestamp)).append("Loading mod file ").append(path)
            .append(" with languages []\n");
    }

    private static String logPrefix(LocalDateTime timestamp) {
        return '[' + timestamp.format(DateTimeFormatter.ofPattern(
            "ddMMMyyyy HH:mm:ss.SSS",
            Locale.getDefault()
        )) + "] [main/DEBUG] [test/LOADING]: ";
    }

    private static String moduleName(int index) {
        return String.format("module%02d", index);
    }

    private static String moduleFromJar(Path jar) {
        String name = jar.getFileName().toString();
        return name.substring(name.indexOf('-') + 1, name.length() - ".jar".length());
    }

    private static String modsToml(String modId) {
        return "modLoader=\"javafml\"\n[[mods]]\nmodId=\"" + modId + "\"\n";
    }

    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(Charset.defaultCharset()));
    }

    private static void writeJar(Path jar, String modsToml) throws Exception {
        Files.write(jar, jarBytes(modsToml));
    }

    private static byte[] jarBytes(String modsToml) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream output = new JarOutputStream(bytes)) {
            output.putNextEntry(new JarEntry("META-INF/mods.toml"));
            output.write(modsToml.getBytes(Charset.defaultCharset()));
            output.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void writeJarWithNested(
        Path jar,
        String modsToml,
        String nestedName,
        byte[] nestedBytes
    ) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("META-INF/mods.toml"));
            output.write(modsToml.getBytes(Charset.defaultCharset()));
            output.closeEntry();
            output.putNextEntry(new JarEntry(nestedName));
            output.write(nestedBytes);
            output.closeEntry();
        }
    }

    private static Id1RuntimeSignatureProvider signatures() {
        return new Id1RuntimeSignatureProvider(new PbeMd5DesId1Crypto(new UUID(0L, 0L)));
    }

    private static ProtocolSessionSnapshot session(String userId) {
        return new ProtocolSessionSnapshot(
            2,
            "fantnel",
            "role",
            "pc.bjdmc.net",
            25565,
            userId,
            "token-digest",
            "entity",
            "",
            "",
            "",
            "game",
            "launcher",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(3600),
            "signature"
        );
    }

    @Test
    void readsOfficialGbkLogsWhenTheFabricJvmDefaultsToUtf8(@TempDir Path directory)
        throws Exception {
        Path logs = Files.createDirectories(directory.resolve("logs"));
        String content = "启动器中文日志\n" + completeLog(List.of("current-alpha"), Map.of());
        Files.write(logs.resolve("debug.log"), content.getBytes(Charset.forName("GB18030")));

        assertEquals(
            List.of("minecraft", "current-alpha"),
            Id1EnvironmentCollector.readForgeLoadingOrder(directory)
        );
    }

    private record OfficialFixture(
        HeyPixelInstallLayout layout,
        List<Path> topLevelJars,
        List<String> loadedOrder
    ) {
    }
}
