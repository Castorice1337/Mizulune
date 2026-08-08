package shit.zen.protocol.heypixel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** Provides the hardware evidence fields used by the ID1 SPRINT environment writer. */
public final class Id1HwidProvider {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Path storePath;

    public Id1HwidProvider(Path configDirectory) {
        this.storePath = Objects.requireNonNull(configDirectory, "configDirectory")
            .resolve("protocol-hwid-profiles.json");
    }

    public ResolvedHardware resolve(HardwareEvidence real, Settings settings) {
        Settings safeSettings = settings == null ? Settings.real() : settings;
        if (!safeSettings.synthetic()) {
            return new ResolvedHardware(real, "real", "", false, "", historyCount());
        }
        String profile = normalizeProfile(safeSettings.profile());
        HwidProfile stored = loadOrCreate(profile, real);
        return new ResolvedHardware(
            stored.hardware(),
            "synthetic",
            profile,
            true,
            stored.id(),
            historyCount()
        );
    }

    private synchronized HwidProfile loadOrCreate(String profile, HardwareEvidence real) {
        LinkedHashMap<String, HwidProfile> profiles = readProfiles();
        HwidProfile existing = profiles.get(profile);
        if (existing != null) return existing;

        byte[] seed = new byte[32];
        SECURE_RANDOM.nextBytes(seed);
        String seedHex = HexFormat.of().formatHex(seed);
        HwidProfile created = new HwidProfile(
            profile,
            "synthetic-" + sha256((profile + ":" + seedHex).getBytes(StandardCharsets.UTF_8)).substring(0, 16),
            Instant.now().toString(),
            seedHex,
            syntheticHardware(real, profile, seedHex)
        );
        profiles.put(profile, created);
        writeProfiles(profiles);
        return created;
    }

    private int historyCount() {
        return readProfiles().size();
    }

    private LinkedHashMap<String, HwidProfile> readProfiles() {
        LinkedHashMap<String, HwidProfile> profiles = new LinkedHashMap<>();
        if (!Files.isRegularFile(storePath)) return profiles;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(storePath, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return profiles;
            JsonArray array = parsed.getAsJsonObject().getAsJsonArray("profiles");
            if (array == null) return profiles;
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                HwidProfile profile = readProfile(element.getAsJsonObject());
                if (profile != null) profiles.put(profile.name(), profile);
            }
        } catch (RuntimeException | IOException ignored) {
            return new LinkedHashMap<>();
        }
        return profiles;
    }

    private void writeProfiles(LinkedHashMap<String, HwidProfile> profiles) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray array = new JsonArray();
        for (HwidProfile profile : profiles.values()) array.add(writeProfile(profile));
        root.add("profiles", array);
        try {
            Files.createDirectories(storePath.getParent());
            Files.writeString(storePath, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static HwidProfile readProfile(JsonObject object) {
        String name = string(object, "name");
        if (name.isBlank()) return null;
        HardwareEvidence hardware = new HardwareEvidence(
            stringList(object.get("cpu")),
            stringList(object.get("computerSystem")),
            nestedStringList(object.get("networkInterfaces")),
            nestedStringList(object.get("diskStores"))
        );
        return new HwidProfile(
            name,
            string(object, "id"),
            string(object, "createdAt"),
            string(object, "seed"),
            hardware
        );
    }

    private static JsonObject writeProfile(HwidProfile profile) {
        JsonObject object = new JsonObject();
        object.addProperty("name", profile.name());
        object.addProperty("id", profile.id());
        object.addProperty("createdAt", profile.createdAt());
        object.addProperty("seed", profile.seed());
        object.add("cpu", stringArray(profile.hardware().cpuInfo()));
        object.add("computerSystem", stringArray(profile.hardware().computerSystemInfo()));
        object.add("networkInterfaces", nestedStringArray(profile.hardware().networkInterfaces()));
        object.add("diskStores", nestedStringArray(profile.hardware().diskStores()));
        return object;
    }

    private static HardwareEvidence syntheticHardware(HardwareEvidence real, String profile, String seedHex) {
        Random random = new Random(seedLong(profile + ":" + seedHex));
        List<String> cpu = List.of(
            hex(random, 16).toUpperCase(Locale.ROOT),
            cpuName(random),
            "Intel64 Family 6 Model " + (140 + random.nextInt(45)) + " Stepping " + (1 + random.nextInt(9))
        );
        UUID hardwareUuid = new UUID(random.nextLong(), random.nextLong());
        List<String> computer = List.of(
            boardManufacturer(random),
            "MS-" + (7000 + random.nextInt(999)),
            "SYN" + hex(random, 12).toUpperCase(Locale.ROOT),
            "1." + random.nextInt(9),
            hardwareUuid.toString()
        );
        int networkCount = Math.max(1, real == null ? 2 : real.networkInterfaces().size());
        List<List<String>> networks = new ArrayList<>();
        for (int i = 0; i < networkCount; i++) {
            networks.add(List.of(
                i == 0 ? "eth" + random.nextInt(4) : "wlan" + random.nextInt(4),
                networkName(random, i),
                mac(random),
                "[" + (10 + random.nextInt(172)) + "." + random.nextInt(256) + "."
                    + random.nextInt(256) + "." + (2 + random.nextInt(200)) + "]",
                "[fe80::" + hex(random, 4) + ":" + hex(random, 4) + ":" + hex(random, 4) + "]"
            ));
        }
        int diskCount = Math.max(1, real == null ? 2 : real.diskStores().size());
        List<List<String>> disks = new ArrayList<>();
        for (int i = 0; i < diskCount; i++) {
            disks.add(List.of(
                "S" + hex(random, 14).toUpperCase(Locale.ROOT),
                "\\\\.\\PHYSICALDRIVE" + i,
                diskModel(random)
            ));
        }
        return new HardwareEvidence(cpu, computer, List.copyOf(networks), List.copyOf(disks));
    }

    private static String normalizeProfile(String profile) {
        String value = profile == null ? "" : profile.trim();
        if (value.isBlank()) return "default";
        String normalized = value.replaceAll("[^A-Za-z0-9_.-]+", "_");
        return normalized.isBlank() ? "default" : normalized;
    }

    private static String cpuName(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> "Intel(R) Core(TM) i7-12700H";
            case 1 -> "Intel(R) Core(TM) i5-12400F";
            case 2 -> "AMD Ryzen 7 5800H with Radeon Graphics";
            default -> "AMD Ryzen 5 5600X 6-Core Processor";
        };
    }

    private static String boardManufacturer(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> "Micro-Star International Co., Ltd.";
            case 1 -> "ASUSTeK COMPUTER INC.";
            case 2 -> "Gigabyte Technology Co., Ltd.";
            default -> "LENOVO";
        };
    }

    private static String networkName(Random random, int index) {
        if (index > 0) return "Intel(R) Wi-Fi 6 AX" + (200 + random.nextInt(11));
        return switch (random.nextInt(3)) {
            case 0 -> "Realtek Gaming 2.5GbE Family Controller";
            case 1 -> "Intel(R) Ethernet Controller I225-V";
            default -> "Killer E2600 Gigabit Ethernet Controller";
        };
    }

    private static String diskModel(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> "Samsung SSD 980 PRO 1TB";
            case 1 -> "WDC PC SN730 SDBPNTY-512G";
            case 2 -> "KINGSTON SNV2S1000G";
            default -> "ST1000LM049-2GH172";
        };
    }

    private static String mac(Random random) {
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        bytes[0] = (byte) ((bytes[0] & 0xfe) | 0x02);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) builder.append(':');
            builder.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
        }
        return builder.toString();
    }

    private static String hex(Random random, int length) {
        StringBuilder builder = new StringBuilder(length);
        while (builder.length() < length) {
            builder.append(Long.toHexString(random.nextLong()));
        }
        return builder.substring(0, length);
    }

    private static long seedLong(String value) {
        byte[] digest = sha256Bytes(value.getBytes(StandardCharsets.UTF_8));
        long seed = 0L;
        for (int i = 0; i < Long.BYTES; i++) seed = (seed << 8) | (digest[i] & 0xffL);
        return seed;
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
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
        HardwareEvidence hardware
    ) {
    }
}
