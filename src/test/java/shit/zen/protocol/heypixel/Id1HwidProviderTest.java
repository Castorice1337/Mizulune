package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Id1HwidProviderTest {
    @Test
    void ephemeralProfilesNeverTouchPersistentStorage(@TempDir Path directory) throws Exception {
        Path store = directory.resolve("protocol-hwid-profiles.json");
        Id1HwidProvider provider = new Id1HwidProvider(directory);

        String firstSelector = provider.createEphemeral();
        Id1HwidProvider.ResolvedHardware first = provider.resolve(
            realHardware(), new Id1HwidProvider.Settings(true, firstSelector));
        String secondSelector = provider.createEphemeral();
        Id1HwidProvider.ResolvedHardware second = provider.resolve(
            realHardware(), new Id1HwidProvider.Settings(true, secondSelector));

        assertFalse(Files.exists(store));
        assertEquals("random", first.profile());
        assertEquals("synthetic", first.source());
        assertNotEquals(firstSelector, secondSelector);
        assertNotEquals(first.syntheticId(), second.syntheticId());
        assertNotEquals(first.hardware(), second.hardware());

        provider.createSaved("saved");
        byte[] persisted = Files.readAllBytes(store);
        String thirdSelector = provider.createEphemeral();
        provider.resolve(realHardware(), new Id1HwidProvider.Settings(true, thirdSelector));
        assertArrayEquals(persisted, Files.readAllBytes(store));
        assertFalse(Files.readString(store, StandardCharsets.UTF_8).contains(thirdSelector));
    }

    @Test
    void savedProfilesRequireExplicitCreationAndSurviveRestart(@TempDir Path directory) throws Exception {
        Id1HwidProvider provider = new Id1HwidProvider(directory);
        provider.createSaved("Profile-A");
        provider.createSaved("profile-b");

        Id1HwidProvider.ResolvedHardware created = provider.resolve(
            realHardware(), new Id1HwidProvider.Settings(true, "Profile-A"));
        Id1HwidProvider reloadedProvider = new Id1HwidProvider(directory);
        String canonical = reloadedProvider.loadSaved("profile-a");
        Id1HwidProvider.ResolvedHardware reloaded = reloadedProvider.resolve(
            realHardware(), new Id1HwidProvider.Settings(true, "PROFILE-A"));

        assertEquals("Profile-A", canonical);
        assertEquals(List.of("Profile-A", "profile-b"), reloadedProvider.listSavedProfiles());
        assertEquals(created.hardware(), reloaded.hardware());
        assertEquals(created.syntheticId(), reloaded.syntheticId());
        assertEquals(2, reloaded.historyCount());

        Path store = directory.resolve("protocol-hwid-profiles.json");
        JsonObject root = JsonParser.parseString(Files.readString(store)).getAsJsonObject();
        assertEquals(Id1HwidProvider.STORE_VERSION, root.get("version").getAsInt());
        assertEquals(Id1HwidProvider.GENERATOR_VERSION, root.get("generatorVersion").getAsInt());
        assertFalse(Files.exists(directory.resolve("protocol-hwid-profiles.json.tmp")));
    }

    @Test
    void missingProfilesAreNotSilentlyCreated(@TempDir Path directory) {
        Id1HwidProvider provider = new Id1HwidProvider(directory);

        assertThrows(IllegalStateException.class, () -> provider.resolve(
            realHardware(), new Id1HwidProvider.Settings(true, "missing")));
        assertThrows(IllegalArgumentException.class, () -> provider.loadSaved("missing"));
        assertFalse(Files.exists(directory.resolve("protocol-hwid-profiles.json")));
    }

    @Test
    void invalidAndDuplicateNamesAreRejected(@TempDir Path directory) {
        Id1HwidProvider provider = new Id1HwidProvider(directory);
        provider.createSaved("Alpha");

        assertThrows(IllegalArgumentException.class, () -> provider.createSaved("alpha"));
        assertThrows(IllegalArgumentException.class, () -> provider.createSaved("../escape"));
        assertThrows(IllegalArgumentException.class, () -> provider.createSaved("with space"));
        assertThrows(IllegalArgumentException.class, () -> provider.createSaved(""));
        assertEquals(List.of("Alpha"), provider.listSavedProfiles());
    }

    @Test
    void malformedStoreIsNeverOverwritten(@TempDir Path directory) throws Exception {
        Path store = directory.resolve("protocol-hwid-profiles.json");
        Files.writeString(store, "{broken", StandardCharsets.UTF_8);
        Id1HwidProvider provider = new Id1HwidProvider(directory);

        assertThrows(IllegalStateException.class, () -> provider.createSaved("safe"));
        assertEquals("{broken", Files.readString(store, StandardCharsets.UTF_8));
        assertFalse(Files.exists(directory.resolve("protocol-hwid-profiles.json.tmp")));
    }

    @Test
    void legacyProfilesRegenerateIntoCurrentRealModelCatalog(@TempDir Path directory) throws Exception {
        Path store = directory.resolve("protocol-hwid-profiles.json");
        Files.writeString(store, """
            {
              "version": 1,
              "profiles": [{
                "name": "legacy",
                "id": "synthetic-legacy",
                "createdAt": "2026-01-01T00:00:00Z",
                "seed": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "cpu": ["0123456789ABCDEF", "legacy cpu", "legacy identifier"],
                "computerSystem": ["legacy maker", "legacy board", "legacy serial", "1.0", "00000000-0000-4000-8000-000000000000"],
                "networkInterfaces": [["eth0", "legacy nic", "02:00:00:00:00:01", "[192.168.1.2]", "[]"]],
                "diskStores": [["legacy disk", "disk0", "legacy model"]]
              }]
            }
            """, StandardCharsets.UTF_8);

        Id1HwidProvider provider = new Id1HwidProvider(directory);
        Id1HwidProvider.ResolvedHardware resolved = provider.resolve(
            realHardware(), new Id1HwidProvider.Settings(true, "legacy"));

        assertCatalogHardware(resolved.hardware());
        provider.createSaved("current");
        JsonObject migrated = JsonParser.parseString(Files.readString(store)).getAsJsonObject();
        assertEquals(2, migrated.get("version").getAsInt());
        assertEquals(2, migrated.getAsJsonArray("profiles").get(0).getAsJsonObject()
            .get("generatorVersion").getAsInt());
    }

    @Test
    void catalogProducesCoherentRealModelsAndLargeIdentifierSpace() throws Exception {
        Set<String> fingerprints = new HashSet<>();
        for (int i = 1; i <= 128; i++) {
            String seed = String.format("%064x", i);
            Id1HwidProvider.HardwareEvidence hardware = Id1SyntheticHardwareCatalog.generate(seed);
            assertCatalogHardware(hardware);
            fingerprints.add(String.join("|", hardware.cpuInfo())
                + hardware.computerSystemInfo()
                + hardware.networkInterfaces()
                + hardware.diskStores());
        }
        assertTrue(fingerprints.size() >= 126);
    }

    @Test
    void concurrentProfileCreationKeepsEveryEntry(@TempDir Path directory) throws Exception {
        Id1HwidProvider provider = new Id1HwidProvider(directory);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                int index = i;
                tasks.add(() -> provider.createSaved("profile-" + index));
            }
            List<Future<String>> futures = executor.invokeAll(tasks);
            for (Future<String> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(12, provider.listSavedProfiles().size());
        assertFalse(Files.exists(directory.resolve("protocol-hwid-profiles.json.tmp")));
    }

    private static void assertCatalogHardware(Id1HwidProvider.HardwareEvidence hardware) throws Exception {
        assertEquals(3, hardware.cpuInfo().size());
        assertEquals(5, hardware.computerSystemInfo().size());
        assertTrue(Id1SyntheticHardwareCatalog.isKnownCpuName(hardware.cpuInfo().get(1)));
        assertTrue(Id1SyntheticHardwareCatalog.isKnownBoard(
            hardware.computerSystemInfo().get(0), hardware.computerSystemInfo().get(1)));
        assertTrue(Id1SyntheticHardwareCatalog.isCoherentPlatform(
            hardware.cpuInfo().get(1),
            hardware.computerSystemInfo().get(0),
            hardware.computerSystemInfo().get(1)
        ));
        UUID.fromString(hardware.computerSystemInfo().get(4));

        assertFalse(hardware.networkInterfaces().isEmpty());
        for (List<String> network : hardware.networkInterfaces()) {
            assertEquals(5, network.size());
            assertTrue(Id1SyntheticHardwareCatalog.isKnownNetworkName(network.get(1)));
            assertTrue(network.get(2).matches("(?i)[0-9a-f]{2}(?::[0-9a-f]{2}){5}"));
            InetAddress.getByName(unbracket(network.get(3)));
            if (!"[]".equals(network.get(4))) InetAddress.getByName(unbracket(network.get(4)));
        }

        assertFalse(hardware.diskStores().isEmpty());
        for (int i = 0; i < hardware.diskStores().size(); i++) {
            List<String> disk = hardware.diskStores().get(i);
            assertEquals(3, disk.size());
            assertEquals("\\\\.\\PHYSICALDRIVE" + i, disk.get(1));
            assertTrue(Id1SyntheticHardwareCatalog.isKnownDiskName(disk.get(2)));
        }
    }

    private static String unbracket(String value) {
        return value.substring(1, value.length() - 1);
    }

    private static Id1HwidProvider.HardwareEvidence realHardware() {
        return new Id1HwidProvider.HardwareEvidence(
            List.of("real-cpu-id", "real-cpu-name", "real-cpu-identifier"),
            List.of("real-board-maker", "real-board-model", "real-serial", "real-version", "real-uuid"),
            List.of(List.of("eth0", "Real NIC", "00:11:22:33:44:55", "[192.168.1.8]", "[fe80::1]")),
            List.of(List.of("real-disk-serial", "\\\\.\\PHYSICALDRIVE0", "Real Disk"))
        );
    }
}
