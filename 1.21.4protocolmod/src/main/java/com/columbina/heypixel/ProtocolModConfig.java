package com.columbina.heypixel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import shit.zen.protocol.heypixel.HeyPixelInstallLayout;

/** Local, non-secret settings for the standalone protocol bridge. */
public record ProtocolModConfig(
    boolean enabled,
    boolean allowLiveSend,
    boolean traceEnabled,
    String enabledHosts,
    Path installRoot,
    Path instanceDirectory,
    Path officialUserDirectory,
    Path officialJavaHome,
    boolean syntheticHwid,
    String syntheticHwidProfile
) {
    public static final String FILE_NAME = "heypixel-protocol-1.21.4.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ProtocolModConfig {
        enabledHosts = Objects.requireNonNullElse(enabledHosts, "pc.bjdmc.net,*.bjdmc.net").trim();
        installRoot = normalize(Objects.requireNonNull(installRoot, "installRoot"));
        instanceDirectory = normalize(Objects.requireNonNull(instanceDirectory, "instanceDirectory"));
        officialUserDirectory = normalize(Objects.requireNonNull(
            officialUserDirectory, "officialUserDirectory"));
        officialJavaHome = normalize(Objects.requireNonNull(officialJavaHome, "officialJavaHome"));
        syntheticHwidProfile = Objects.requireNonNullElse(syntheticHwidProfile, "default").trim();
        HeyPixelInstallLayout.fromPaths(installRoot, instanceDirectory);
        if (!Files.isDirectory(officialUserDirectory)) {
            throw new IllegalArgumentException("officialUserDirectory is not a directory");
        }
        if (!isJavaHome(officialJavaHome)) {
            throw new IllegalArgumentException("officialJavaHome does not contain bin/java");
        }
    }

    public static ProtocolModConfig load(Path protocolDirectory, Path fabricGameDirectory) throws IOException {
        Objects.requireNonNull(protocolDirectory, "protocolDirectory");
        Objects.requireNonNull(fabricGameDirectory, "fabricGameDirectory");
        Files.createDirectories(protocolDirectory);
        Path path = protocolDirectory.resolve(FILE_NAME);
        Defaults defaults = detectDefaults(fabricGameDirectory);
        if (!Files.isRegularFile(path)) {
            writeDefault(path, defaults);
        }

        JsonObject value = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        boolean enabled = booleanValue(value, "enabled", true);
        boolean allowLiveSend = booleanValue(value, "allowLiveSend", true);
        boolean traceEnabled = booleanValue(value, "traceEnabled", true);
        String enabledHosts = stringValue(value, "enabledHosts", "pc.bjdmc.net,*.bjdmc.net");
        String install = firstNonBlank(
            System.getProperty("mizulune.heypixel.installRoot"),
            System.getenv("MIZULUNE_HEYPIXEL_INSTALL_ROOT"),
            stringValue(value, "installRoot", defaults.installRoot().toString())
        );
        String instance = firstNonBlank(
            System.getProperty("mizulune.heypixel.instanceDir"),
            System.getenv("MIZULUNE_HEYPIXEL_INSTANCE_DIR"),
            stringValue(value, "instanceDirectory", defaults.instanceDirectory().toString())
        );
        Path resolvedInstallRoot = normalize(Path.of(install));
        Path detectedOfficialJavaHome = detectOfficialJavaHome(resolvedInstallRoot);
        String officialUserDirectory = firstNonBlank(
            System.getProperty("mizulune.heypixel.userDirectory"),
            System.getenv("MIZULUNE_HEYPIXEL_USER_DIR"),
            stringValue(value, "officialUserDirectory", ""),
            resolvedInstallRoot.toString()
        );
        String officialJavaHome = firstNonBlankOrNull(
            System.getProperty("mizulune.heypixel.javaHome"),
            System.getenv("MIZULUNE_HEYPIXEL_JAVA_HOME"),
            stringValue(value, "officialJavaHome", ""),
            detectedOfficialJavaHome == null ? "" : detectedOfficialJavaHome.toString()
        );
        if (officialJavaHome == null) {
            throw new IllegalArgumentException(
                "officialJavaHome is required; set it in the config or MIZULUNE_HEYPIXEL_JAVA_HOME");
        }
        boolean syntheticHwid = booleanValue(value, "syntheticHwid", false);
        String syntheticHwidProfile = stringValue(value, "syntheticHwidProfile", "default");
        return new ProtocolModConfig(
            enabled,
            allowLiveSend,
            traceEnabled,
            enabledHosts,
            Path.of(install),
            Path.of(instance),
            Path.of(officialUserDirectory),
            Path.of(officialJavaHome),
            syntheticHwid,
            syntheticHwidProfile
        );
    }

    public HeyPixelInstallLayout installLayout() {
        return HeyPixelInstallLayout.fromPaths(installRoot, instanceDirectory);
    }

    private static void writeDefault(Path path, Defaults defaults) throws IOException {
        JsonObject value = new JsonObject();
        value.addProperty("enabled", true);
        value.addProperty("allowLiveSend", true);
        value.addProperty("traceEnabled", true);
        value.addProperty("enabledHosts", "pc.bjdmc.net,*.bjdmc.net");
        value.addProperty("installRoot", defaults.installRoot().toString());
        value.addProperty("instanceDirectory", defaults.instanceDirectory().toString());
        value.addProperty("officialUserDirectory", defaults.installRoot().toString());
        value.addProperty("officialJavaHome",
            defaults.officialJavaHome() == null ? "" : defaults.officialJavaHome().toString());
        value.addProperty("syntheticHwid", false);
        value.addProperty("syntheticHwidProfile", "default");
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(value) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static Defaults detectDefaults(Path gameDirectory) {
        Path normalizedGame = normalize(gameDirectory);
        Path root;
        if (normalizedGame.getFileName() != null
            && "heypixel".equalsIgnoreCase(normalizedGame.getFileName().toString())
            && normalizedGame.getParent() != null) {
            root = normalize(normalizedGame.getParent());
        } else {
            root = normalizedGame;
        }
        Path conventional = root.resolve("heypixel");
        Path instance = Files.isDirectory(conventional)
            ? normalize(conventional)
            : normalizedGame.startsWith(root) ? normalizedGame : root;
        return new Defaults(root, instance, detectOfficialJavaHome(root));
    }

    private static boolean booleanValue(JsonObject value, String key, boolean fallback) {
        return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsBoolean() : fallback;
    }

    private static String stringValue(JsonObject value, String key, String fallback) {
        return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        throw new IllegalArgumentException("No usable path value");
    }

    private static String firstNonBlankOrNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    static Path detectOfficialJavaHome(Path installRoot) {
        LinkedHashSet<Path> foundCandidates = new LinkedHashSet<>();
        Path current = installRoot;
        for (int depth = 0; current != null && depth < 4; depth++, current = current.getParent()) {
            for (String child : new String[]{"jdk17", "jre", "runtime", "java"}) {
                Path direct = current.resolve(child);
                if (isJavaHome(direct)) foundCandidates.add(normalize(direct));
            }
            Path ext = current.resolve("ext");
            if (!Files.isDirectory(ext)) continue;
            try (var candidates = Files.find(ext, 3,
                (path, attributes) -> attributes.isDirectory() && isJavaHome(path))) {
                candidates.map(ProtocolModConfig::normalize).forEach(foundCandidates::add);
            } catch (IOException ignored) {
            }
        }
        return foundCandidates.stream()
            .min((left, right) -> {
                int score = Integer.compare(javaHomeScore(left), javaHomeScore(right));
                return score != 0 ? score : left.toString().compareToIgnoreCase(right.toString());
            })
            .orElse(null);
    }

    private static int javaHomeScore(Path path) {
        try {
            String release = Files.readString(path.resolve("release"), StandardCharsets.UTF_8);
            if (release.lines().anyMatch(line -> line.startsWith("JAVA_VERSION=\"17")
                || line.startsWith("JAVA_VERSION=17"))) {
                return 0;
            }
        } catch (IOException ignored) {
        }
        String name = path.getFileName() == null
            ? ""
            : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("jdk17") || name.contains("17") ? 1 : 2;
    }

    private static boolean isJavaHome(Path path) {
        return path != null && (Files.isRegularFile(path.resolve("bin").resolve("javaw.exe"))
            || Files.isRegularFile(path.resolve("bin").resolve("java.exe")));
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    record Defaults(Path installRoot, Path instanceDirectory, Path officialJavaHome) {
    }
}
