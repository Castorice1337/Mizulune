package shit.zen.protocol.heypixel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Separates the shared HeyPixel installation files from one instance's files. */
public record HeyPixelInstallLayout(Path installRoot, Path instanceDirectory) {
    private static final String DEFAULT_INSTANCE_DIRECTORY = "heypixel";

    public HeyPixelInstallLayout {
        installRoot = normalize(Objects.requireNonNull(installRoot, "installRoot"));
        instanceDirectory = normalize(Objects.requireNonNull(instanceDirectory, "instanceDirectory"));
    }

    /**
     * Resolves an explicitly supplied root and/or instance. Explicit pairs must describe the
     * same installation tree; unrelated paths are rejected instead of silently combining two
     * different launches.
     */
    public static HeyPixelInstallLayout fromPaths(Path installRoot, Path instanceDirectory) {
        if (installRoot == null && instanceDirectory == null) {
            throw new IllegalArgumentException("An install root or instance directory is required");
        }
        if (installRoot == null) {
            Path instance = normalize(instanceDirectory);
            Path inferredRoot = instance.getParent();
            return new HeyPixelInstallLayout(inferredRoot == null ? instance : inferredRoot, instance);
        }
        Path root = normalize(installRoot);
        Path instance = instanceDirectory == null
            ? inferInstanceDirectory(root)
            : normalize(instanceDirectory);
        requireRelatedExplicitPaths(root, instance);
        return new HeyPixelInstallLayout(root, instance);
    }

    /**
     * Keeps the two official runtime sources independent: Forge supplies the game/install root,
     * while {@code Minecraft.gameDirectory} supplies the active instance directory. The launcher
     * is allowed to place those sources in separate trees, so this factory does not apply the
     * explicit-configuration relationship check.
     */
    public static HeyPixelInstallLayout fromOfficialSources(Path forgeGameRoot, Path minecraftInstanceDirectory) {
        if (forgeGameRoot == null && minecraftInstanceDirectory == null) {
            throw new IllegalArgumentException("A Forge game root or Minecraft instance directory is required");
        }
        if (forgeGameRoot == null) {
            Path instance = normalize(minecraftInstanceDirectory);
            Path inferredRoot = instance.getParent();
            return new HeyPixelInstallLayout(inferredRoot == null ? instance : inferredRoot, instance);
        }
        Path root = normalize(forgeGameRoot);
        Path instance = minecraftInstanceDirectory == null
            ? inferInstanceDirectory(root)
            : normalize(minecraftInstanceDirectory);
        return new HeyPixelInstallLayout(root, instance);
    }

    /**
     * Interprets the legacy single game-directory setting as either an install root or an
     * instance directory using filesystem evidence. Ambiguous paths retain the legacy behavior
     * by serving as both roots.
     */
    public static HeyPixelInstallLayout fromLegacyPath(Path legacyPath) {
        Path candidate = normalize(Objects.requireNonNull(legacyPath, "legacyPath"));
        if (hasInstallRootEvidence(candidate)) {
            return new HeyPixelInstallLayout(candidate, inferInstanceDirectory(candidate));
        }

        Path parent = candidate.getParent();
        if (hasInstanceEvidence(candidate) || parent != null && hasInstallRootEvidence(parent)) {
            return new HeyPixelInstallLayout(parent == null ? candidate : parent, candidate);
        }
        return new HeyPixelInstallLayout(candidate, candidate);
    }

    public Path modsDirectory() {
        return installRoot.resolve("mods");
    }

    public Path nativeDirectory() {
        return installRoot.resolve("native");
    }

    public Path librariesDirectory() {
        return installRoot.resolve("libraries");
    }

    public Path logsDirectory() {
        return installRoot.resolve("logs");
    }

    public Path versionsDirectory() {
        return installRoot.resolve("versions");
    }

    private static Path inferInstanceDirectory(Path installRoot) {
        Path conventional = installRoot.resolve(DEFAULT_INSTANCE_DIRECTORY);
        return Files.isDirectory(conventional) || hasInstanceEvidence(conventional)
            ? normalize(conventional)
            : installRoot;
    }

    private static void requireRelatedExplicitPaths(Path installRoot, Path instanceDirectory) {
        if (!instanceDirectory.startsWith(installRoot)) {
            throw new IllegalArgumentException(
                "Explicit HeyPixel install root and instance directory are unrelated");
        }
    }

    private static boolean hasInstallRootEvidence(Path directory) {
        return isDirectory(directory.resolve("mods"))
            || isDirectory(directory.resolve("native"))
            || isDirectory(directory.resolve("libraries"))
            || isDirectory(directory.resolve("logs"))
            || isDirectory(directory.resolve("versions"));
    }

    private static boolean hasInstanceEvidence(Path directory) {
        return Files.isRegularFile(directory.resolve("heypixel.json"))
            || isDirectory(directory.resolve("cache"))
            || isDirectory(directory.resolve("packs"))
            || isDirectory(directory.resolve("ViaForge"));
    }

    private static boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
