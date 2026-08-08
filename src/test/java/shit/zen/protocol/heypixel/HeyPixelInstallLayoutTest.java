package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HeyPixelInstallLayoutTest {
    @Test
    void infersInstanceDirectoryFromLegacyInstallRoot(@TempDir Path directory) throws Exception {
        Path installRoot = createInstallRoot(directory.resolve(".minecraft"));
        Path instanceDirectory = createInstanceDirectory(installRoot.resolve("heypixel"));

        HeyPixelInstallLayout layout = HeyPixelInstallLayout.fromLegacyPath(installRoot);

        assertEquals(installRoot.toAbsolutePath().normalize(), layout.installRoot());
        assertEquals(instanceDirectory.toAbsolutePath().normalize(), layout.instanceDirectory());
        assertEquals(installRoot.resolve("mods").toAbsolutePath().normalize(), layout.modsDirectory());
        assertEquals(installRoot.resolve("logs").toAbsolutePath().normalize(), layout.logsDirectory());
        assertEquals(installRoot.resolve("libraries").toAbsolutePath().normalize(), layout.librariesDirectory());
    }

    @Test
    void acceptsLegacyInstanceDirectoryAndInfersItsInstallRoot(@TempDir Path directory) throws Exception {
        Path installRoot = createInstallRoot(directory.resolve(".minecraft"));
        Path instanceDirectory = createInstanceDirectory(installRoot.resolve("heypixel"));

        HeyPixelInstallLayout layout = HeyPixelInstallLayout.fromLegacyPath(instanceDirectory);

        assertEquals(installRoot.toAbsolutePath().normalize(), layout.installRoot());
        assertEquals(instanceDirectory.toAbsolutePath().normalize(), layout.instanceDirectory());
    }

    @Test
    void retainsRelatedExplicitInstallAndInstanceRoots(@TempDir Path directory) throws Exception {
        Path installRoot = createInstallRoot(directory.resolve("shared-root"));
        Path instanceDirectory = createInstanceDirectory(installRoot.resolve("instances").resolve("official"));

        HeyPixelInstallLayout layout = HeyPixelInstallLayout.fromPaths(installRoot, instanceDirectory);

        assertEquals(installRoot.toAbsolutePath().normalize(), layout.installRoot());
        assertEquals(instanceDirectory.toAbsolutePath().normalize(), layout.instanceDirectory());
    }

    @Test
    void rejectsUnrelatedExplicitInstallAndInstanceRoots(@TempDir Path directory) throws Exception {
        Path installRoot = createInstallRoot(directory.resolve("shared-root"));
        Path instanceDirectory = createInstanceDirectory(directory.resolve("other-tree").resolve("official"));

        assertThrows(IllegalArgumentException.class,
            () -> HeyPixelInstallLayout.fromPaths(installRoot, instanceDirectory));
    }

    @Test
    void preservesIndependentOfficialForgeAndMinecraftSources(@TempDir Path directory) throws Exception {
        Path forgeRoot = createInstallRoot(directory.resolve("forge-game-root"));
        Path minecraftInstance = createInstanceDirectory(directory.resolve("launcher-instances").resolve("official"));

        HeyPixelInstallLayout layout = HeyPixelInstallLayout.fromOfficialSources(forgeRoot, minecraftInstance);

        assertEquals(forgeRoot.toAbsolutePath().normalize(), layout.installRoot());
        assertEquals(minecraftInstance.toAbsolutePath().normalize(), layout.instanceDirectory());
        assertEquals(forgeRoot.resolve("mods").toAbsolutePath().normalize(), layout.modsDirectory());
    }

    private static Path createInstallRoot(Path installRoot) throws Exception {
        Files.createDirectories(installRoot.resolve("mods"));
        Files.createDirectories(installRoot.resolve("native"));
        Files.createDirectories(installRoot.resolve("libraries"));
        Files.createDirectories(installRoot.resolve("logs"));
        Files.createDirectories(installRoot.resolve("versions"));
        return installRoot;
    }

    private static Path createInstanceDirectory(Path instanceDirectory) throws Exception {
        Files.createDirectories(instanceDirectory.resolve("config"));
        Files.createDirectories(instanceDirectory.resolve("cache"));
        Files.createDirectories(instanceDirectory.resolve("packs"));
        Files.createDirectories(instanceDirectory.resolve("ViaForge"));
        Files.writeString(instanceDirectory.resolve("heypixel.json"), "{}");
        return instanceDirectory;
    }
}
