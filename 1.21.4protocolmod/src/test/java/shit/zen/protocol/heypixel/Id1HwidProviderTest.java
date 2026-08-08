package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Id1HwidProviderTest {
    @Test
    void syntheticProfilesPersistAndKeepHardwareShape(@TempDir Path directory) {
        Id1HwidProvider.HardwareEvidence real = new Id1HwidProvider.HardwareEvidence(
            List.of("real-cpu-id", "real-cpu-name", "real-cpu-identifier"),
            List.of("real-board-maker", "real-board-model", "real-serial", "real-version", "real-uuid"),
            List.of(
                List.of("eth0", "Real NIC", "00:11:22:33:44:55", "[192.168.1.8]", "[fe80::1]"),
                List.of("wlan0", "Real WiFi", "00:11:22:33:44:66", "[]", "[]")
            ),
            List.of(List.of("real-disk-serial", "\\\\.\\PHYSICALDRIVE0", "Real Disk"))
        );
        Id1HwidProvider provider = new Id1HwidProvider(directory);

        Id1HwidProvider.ResolvedHardware first =
            provider.resolve(real, new Id1HwidProvider.Settings(true, "profile-a"));
        Id1HwidProvider.ResolvedHardware again =
            provider.resolve(real, new Id1HwidProvider.Settings(true, "profile-a"));
        Id1HwidProvider.ResolvedHardware switched =
            provider.resolve(real, new Id1HwidProvider.Settings(true, "profile-b"));

        assertTrue(first.synthetic());
        assertEquals("synthetic", first.source());
        assertEquals(first.syntheticId(), again.syntheticId());
        assertEquals(first.hardware(), again.hardware());
        assertNotEquals(first.syntheticId(), switched.syntheticId());
        assertEquals(2, first.hardware().networkInterfaces().size());
        assertEquals(1, first.hardware().diskStores().size());
        assertNotEquals(real.cpuInfo(), first.hardware().cpuInfo());
        assertTrue(Files.isRegularFile(directory.resolve("protocol-hwid-profiles.json")));
    }
}
