package shit.zen.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigManagerNameValidationTest {
    @TempDir
    private Path tempDir;

    @Test
    void profileNameAcceptsSafeNamesAndStripsJsonExtension() {
        assertEquals("legit", ConfigManager.normalizeProfileName("legit"));
        assertEquals("legit", ConfigManager.normalizeProfileName("legit.json"));
        assertEquals("pvp-1.20_ghost", ConfigManager.normalizeProfileName("pvp-1.20_ghost"));
    }

    @Test
    void profileNameRejectsTraversalAndUnsafeNames() {
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.normalizeProfileName(""));
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.normalizeProfileName(".."));
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.normalizeProfileName("a..b"));
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.normalizeProfileName("a/b"));
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.normalizeProfileName("a\\b"));
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.normalizeProfileName("bad name"));
    }

    @Test
    void profileListScansDirectoryEveryTime() throws Exception {
        Files.writeString(this.tempDir.resolve("legit.json"), "{}");
        Files.writeString(this.tempDir.resolve("ignore.txt"), "{}");

        assertEquals(List.of("legit"), ConfigManager.listProfileNames(this.tempDir));

        Files.writeString(this.tempDir.resolve("ghost.json"), "{}");
        assertEquals(List.of("ghost", "legit"), ConfigManager.listProfileNames(this.tempDir));

        Files.delete(this.tempDir.resolve("legit.json"));
        assertEquals(List.of("ghost"), ConfigManager.listProfileNames(this.tempDir));
    }
}
