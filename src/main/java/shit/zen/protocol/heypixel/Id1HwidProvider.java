package shit.zen.protocol.heypixel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Provides the hardware evidence fields used by the ID1 SPRINT environment writer. */
public final class Id1HwidProvider {
    static final int STORE_VERSION = 2;
    static final int GENERATOR_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern SAFE_PROFILE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final String EPHEMERAL_PREFIX = "@ephemeral:";

    private final Path storePath;
    private HwidProfile ephemeralProfile;
    private String ephemeralSelector = "";

    public Id1HwidProvider(Path configDirectory) {
        this.storePath = Objects.requireNonNull(configDirectory, "configDirectory")
            .resolve("protocol-hwid-profiles.json");
    }

    /** Creates a new process-memory-only profile and returns its non-persistent selector. */
    public synchronized String createEphemeral() {
        HwidProfile profile = createProfile("random");
        this.ephemeralProfile = profile;
        this.ephemeralSelector = EPHEMERAL_PREFIX + randomHex(16);
        return this.ephemeralSelector;
    }

    /** Creates a named profile exactly once. Existing names are never overwritten. */
    public synchronized String createSaved(String rawName) {
        String name = normalizeProfileName(rawName);
        LinkedHashMap<String, HwidProfile> profiles = readProfiles();
        if (findProfile(profiles, name) != null) {
            throw new IllegalArgumentException("HWID profile already exists: " + name);
        }
        profiles.put(name, createProfile(name));
        writeProfiles(profiles);
        return name;
    }

    /** Resolves a saved name without changing or regenerating its stored snapshot. */
    public synchronized String loadSaved(String rawName) {
        String name = normalizeProfileName(rawName);
        HwidProfile profile = findProfile(readProfiles(), name);
        if (profile == null) {
            throw new IllegalArgumentException("HWID profile not found: " + name);
        }
        return profile.name();
    }

    public synchronized boolean hasSaved(String rawName) {
        try {
            String name = normalizeProfileName(rawName);
            return findProfile(readProfiles(), name) != null;
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return false;
        }
    }

    public synchronized List<String> listSavedProfiles() {
        return readProfiles().values().stream()
            .map(HwidProfile::name)
            .sorted(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)))
            .toList();
    }

    public synchronized ResolvedHardware resolve(HardwareEvidence real, Settings settings) {
        Settings safeSettings = settings == null ? Settings.real() : settings;
        if (!safeSettings.synthetic()) {
            return new ResolvedHardware(real, "real", "", false, "", historyCountSafely());
        }

        HwidProfile selected;
        String publicProfileName;
        int historyCount;
        if (!this.ephemeralSelector.isBlank()
            && this.ephemeralSelector.equals(safeSettings.profile())
            && this.ephemeralProfile != null) {
            selected = this.ephemeralProfile;
            publicProfileName = "random";
            historyCount = historyCountSafely();
        } else {
            String requested = normalizeProfileName(safeSettings.profile());
            LinkedHashMap<String, HwidProfile> profiles = readProfiles();
            selected = findProfile(profiles, requested);
            if (selected == null) {
                throw new IllegalStateException("Synthetic HWID profile is unavailable: " + requested);
            }
            publicProfileName = selected.name();
            historyCount = profiles.size();
        }

        return new ResolvedHardware(
            selected.hardware(),
            "synthetic",
            publicProfileName,
            true,
            selected.id(),
            historyCount
        );
    }

    public static String normalizeProfileName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - ".json".length());
        }
        if (name.isBlank()
            || ".".equals(name)
            || "..".equals(name)
            || name.contains("..")
            || name.contains("/")
            || name.contains("\\")
            || !SAFE_PROFILE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid HWID profile name: " + name);
        }
        return name;
    }

    private HwidProfile createProfile(String name) {
        byte[] seed = new byte[32];
        SECURE_RANDOM.nextBytes(seed);
        String seedHex = HexFormat.of().formatHex(seed);
        return new HwidProfile(
            name,
            "synthetic-" + sha256((name + ":" + seedHex).getBytes(StandardCharsets.UTF_8)).substring(0, 16),
            Instant.now().toString(),
            seedHex,
            GENERATOR_VERSION,
            Id1SyntheticHardwareCatalog.generate(seedHex)
        );
    }

    private int historyCountSafely() {
        try {
            return readProfiles().size();
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private LinkedHashMap<String, HwidProfile> readProfiles() {
        LinkedHashMap<String, HwidProfile> profiles = new LinkedHashMap<>();
        if (!Files.exists(storePath)) return profiles;
        if (!Files.isRegularFile(storePath)) {
            throw new IllegalStateException("HWID profile store is not a regular file");
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(storePath, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("HWID profile store root is not an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            int version = integer(root, "version", 1);
            if (version < 1 || version > STORE_VERSION) {
                throw new IllegalStateException("Unsupported HWID profile store version: " + version);
            }
            JsonArray array = root.getAsJsonArray("profiles");
            if (array == null) return profiles;
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    throw new IllegalStateException("Malformed HWID profile entry");
                }
                HwidProfile profile = readProfile(element.getAsJsonObject());
                if (findProfile(profiles, profile.name()) != null) {
                    throw new IllegalStateException("Duplicate HWID profile name: " + profile.name());
                }
                profiles.put(profile.name(), profile);
            }
            return profiles;
        } catch (IOException | RuntimeException error) {
            if (error instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Failed to read HWID profile store", error);
        }
    }

    private void writeProfiles(LinkedHashMap<String, HwidProfile> profiles) {
        JsonObject root = new JsonObject();
        root.addProperty("version", STORE_VERSION);
        root.addProperty("generatorVersion", GENERATOR_VERSION);
        JsonArray array = new JsonArray();
        for (HwidProfile profile : profiles.values()) array.add(writeProfile(profile));
        root.add("profiles", array);

        Path parent = storePath.getParent();
        Path temp = parent.resolve(storePath.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            moveReplacing(temp, storePath);
        } catch (IOException error) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("Failed to write HWID profile store", error);
        }
    }

    private static HwidProfile readProfile(JsonObject object) {
        String name = normalizeProfileName(string(object, "name"));
        String id = string(object, "id");
        String createdAt = string(object, "createdAt");
        String seed = string(object, "seed");
        int generatorVersion = integer(object, "generatorVersion", 1);
        if (id.isBlank() || seed.isBlank()) {
            throw new IllegalStateException("HWID profile metadata is incomplete: " + name);
        }
        HardwareEvidence storedHardware = new HardwareEvidence(
            stringList(object.get("cpu")),
            stringList(object.get("computerSystem")),
            nestedStringList(object.get("networkInterfaces")),
            nestedStringList(object.get("diskStores"))
        );
        validateHardwareShape(storedHardware);
        HardwareEvidence hardware = generatorVersion < GENERATOR_VERSION
            ? Id1SyntheticHardwareCatalog.generate(seed)
            : storedHardware;
        validateCatalogHardware(hardware);
        return new HwidProfile(name, id, createdAt, seed, GENERATOR_VERSION, hardware);
    }

    private static JsonObject writeProfile(HwidProfile profile) {
        JsonObject object = new JsonObject();
        object.addProperty("name", profile.name());
        object.addProperty("id", profile.id());
        object.addProperty("createdAt", profile.createdAt());
        object.addProperty("seed", profile.seed());
        object.addProperty("generatorVersion", profile.generatorVersion());
        object.add("cpu", stringArray(profile.hardware().cpuInfo()));
        object.add("computerSystem", stringArray(profile.hardware().computerSystemInfo()));
        object.add("networkInterfaces", nestedStringArray(profile.hardware().networkInterfaces()));
        object.add("diskStores", nestedStringArray(profile.hardware().diskStores()));
        return object;
    }

    private static void validateHardwareShape(HardwareEvidence hardware) {
        if (hardware.cpuInfo().size() != 3 || hardware.computerSystemInfo().size() != 5) {
            throw new IllegalStateException("HWID profile has an invalid CPU or computer-system shape");
        }
        if (hardware.networkInterfaces().isEmpty() || hardware.diskStores().isEmpty()) {
            throw new IllegalStateException("HWID profile has no network or disk evidence");
        }
        for (List<String> network : hardware.networkInterfaces()) {
            if (network.size() != 5) {
                throw new IllegalStateException("HWID profile has an invalid network shape");
            }
        }
        for (List<String> disk : hardware.diskStores()) {
            if (disk.size() != 3) {
                throw new IllegalStateException("HWID profile has an invalid disk shape");
            }
        }
    }

    private static void validateCatalogHardware(HardwareEvidence hardware) {
        String cpuName = hardware.cpuInfo().get(1);
        String manufacturer = hardware.computerSystemInfo().get(0);
        String boardModel = hardware.computerSystemInfo().get(1);
        if (!Id1SyntheticHardwareCatalog.isKnownCpuName(cpuName)
            || !Id1SyntheticHardwareCatalog.isKnownBoard(manufacturer, boardModel)
            || !Id1SyntheticHardwareCatalog.isCoherentPlatform(cpuName, manufacturer, boardModel)) {
            throw new IllegalStateException("HWID profile contains an unknown or incoherent platform model");
        }
        for (List<String> network : hardware.networkInterfaces()) {
            if (!Id1SyntheticHardwareCatalog.isKnownNetworkName(network.get(1))) {
                throw new IllegalStateException("HWID profile contains an unknown network model");
            }
        }
        for (List<String> disk : hardware.diskStores()) {
            if (!Id1SyntheticHardwareCatalog.isKnownDiskName(disk.get(2))) {
                throw new IllegalStateException("HWID profile contains an unknown disk model");
            }
        }
    }

    private static HwidProfile findProfile(Map<String, HwidProfile> profiles, String name) {
        for (HwidProfile profile : profiles.values()) {
            if (profile.name().equalsIgnoreCase(name)) return profile;
        }
        return null;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        SECURE_RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value == null ? "" : value);
        return array;
    }

    private static JsonArray nestedStringArray(List<List<String>> values) {
        JsonArray array = new JsonArray();
        for (List<String> nested : values) array.add(stringArray(nested));
        return array;
    }

    private static List<String> stringList(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            values.add(value.isJsonPrimitive() ? value.getAsString() : "");
        }
        return List.copyOf(values);
    }

    private static List<List<String>> nestedStringList(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<List<String>> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) values.add(stringList(value));
        return List.copyOf(values);
    }

    public record Settings(boolean synthetic, String profile) {
        public Settings {
            profile = profile == null ? "" : profile.trim();
        }

        public static Settings real() {
            return new Settings(false, "");
        }
    }

    public record HardwareEvidence(
        List<String> cpuInfo,
        List<String> computerSystemInfo,
        List<List<String>> networkInterfaces,
        List<List<String>> diskStores
    ) {
        public HardwareEvidence {
            cpuInfo = List.copyOf(cpuInfo);
            computerSystemInfo = List.copyOf(computerSystemInfo);
            networkInterfaces = copyNested(networkInterfaces);
            diskStores = copyNested(diskStores);
        }

        private static List<List<String>> copyNested(List<List<String>> values) {
            List<List<String>> result = new ArrayList<>();
            for (List<String> value : values) result.add(List.copyOf(value));
            return List.copyOf(result);
        }
    }

    public record ResolvedHardware(
        HardwareEvidence hardware,
        String source,
        String profile,
        boolean synthetic,
        String syntheticId,
        int historyCount
    ) {
    }

    private record HwidProfile(
        String name,
        String id,
        String createdAt,
        String seed,
        int generatorVersion,
        HardwareEvidence hardware
    ) {
    }
}
