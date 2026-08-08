package shit.zen.protocol.heypixel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic synthetic ID1 hardware generator backed only by real product names.
 *
 * <p>A platform is selected first, then a CPU and baseboard from the same socket/family.
 * Identifiers are independently randomized so a small, auditable product catalog still
 * produces a very large profile space without inventing product model names.</p>
 */
final class Id1SyntheticHardwareCatalog {
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final List<PlatformTemplate> PLATFORMS = List.of(
        new PlatformTemplate(
            false,
            List.of(
                new CpuModel("Intel(R) Core(TM) i5-10400F CPU @ 2.90GHz",
                    "Intel64 Family 6 Model 165 Stepping 5"),
                new CpuModel("Intel(R) Core(TM) i7-10700 CPU @ 2.90GHz",
                    "Intel64 Family 6 Model 165 Stepping 5"),
                new CpuModel("Intel(R) Core(TM) i9-10900K CPU @ 3.70GHz",
                    "Intel64 Family 6 Model 165 Stepping 5")
            ),
            List.of(
                new BoardModel("ASUSTeK COMPUTER INC.", "PRIME B460M-A", "Rev 1.xx"),
                new BoardModel("Micro-Star International Co., Ltd.", "MAG B460M MORTAR (MS-7C82)", "1.0"),
                new BoardModel("Gigabyte Technology Co., Ltd.", "Z490 AORUS ELITE", "x.x"),
                new BoardModel("ASRock", "B460M Pro4", "")
            )
        ),
        new PlatformTemplate(
            false,
            List.of(
                new CpuModel("12th Gen Intel(R) Core(TM) i5-12400F",
                    "Intel64 Family 6 Model 151 Stepping 5"),
                new CpuModel("12th Gen Intel(R) Core(TM) i7-12700K",
                    "Intel64 Family 6 Model 151 Stepping 2"),
                new CpuModel("13th Gen Intel(R) Core(TM) i5-13400F",
                    "Intel64 Family 6 Model 191 Stepping 2"),
                new CpuModel("13th Gen Intel(R) Core(TM) i7-13700K",
                    "Intel64 Family 6 Model 183 Stepping 1")
            ),
            List.of(
                new BoardModel("ASUSTeK COMPUTER INC.", "PRIME B660M-A D4", "Rev 1.xx"),
                new BoardModel("Micro-Star International Co., Ltd.", "PRO B660M-A DDR4 (MS-7D43)", "1.0"),
                new BoardModel("Gigabyte Technology Co., Ltd.", "B660M DS3H DDR4", "x.x"),
                new BoardModel("ASRock", "B760M Pro RS/D4", "")
            )
        ),
        new PlatformTemplate(
            false,
            List.of(
                new CpuModel("AMD Ryzen 5 5600X 6-Core Processor",
                    "AMD64 Family 25 Model 33 Stepping 2"),
                new CpuModel("AMD Ryzen 7 5700X 8-Core Processor",
                    "AMD64 Family 25 Model 33 Stepping 2"),
                new CpuModel("AMD Ryzen 7 5800X3D 8-Core Processor",
                    "AMD64 Family 25 Model 33 Stepping 2")
            ),
            List.of(
                new BoardModel("ASUSTeK COMPUTER INC.", "TUF GAMING B550M-PLUS", "Rev X.0x"),
                new BoardModel("Micro-Star International Co., Ltd.", "MAG B550 TOMAHAWK (MS-7C91)", "2.0"),
                new BoardModel("Gigabyte Technology Co., Ltd.", "B550 AORUS ELITE V2", "x.x"),
                new BoardModel("ASRock", "B550M Pro4", "")
            )
        ),
        new PlatformTemplate(
            false,
            List.of(
                new CpuModel("AMD Ryzen 5 7600X 6-Core Processor",
                    "AMD64 Family 25 Model 97 Stepping 2"),
                new CpuModel("AMD Ryzen 7 7700X 8-Core Processor",
                    "AMD64 Family 25 Model 97 Stepping 2"),
                new CpuModel("AMD Ryzen 7 7800X3D 8-Core Processor",
                    "AMD64 Family 25 Model 97 Stepping 2")
            ),
            List.of(
                new BoardModel("ASUSTeK COMPUTER INC.", "TUF GAMING B650-PLUS WIFI", "Rev 1.xx"),
                new BoardModel("Micro-Star International Co., Ltd.", "MAG B650 TOMAHAWK WIFI (MS-7D75)", "1.0"),
                new BoardModel("Gigabyte Technology Co., Ltd.", "B650 AORUS ELITE AX", "x.x"),
                new BoardModel("ASRock", "B650M Pro RS WiFi", "")
            )
        ),
        new PlatformTemplate(
            true,
            List.of(
                new CpuModel("12th Gen Intel(R) Core(TM) i5-12500H",
                    "Intel64 Family 6 Model 154 Stepping 3"),
                new CpuModel("12th Gen Intel(R) Core(TM) i7-12700H",
                    "Intel64 Family 6 Model 154 Stepping 3"),
                new CpuModel("12th Gen Intel(R) Core(TM) i9-12900H",
                    "Intel64 Family 6 Model 154 Stepping 3")
            ),
            List.of(
                new BoardModel("LENOVO", "LNVNB161216", "SDK0T76461 WIN"),
                new BoardModel("ASUSTeK COMPUTER INC.", "G733ZW", "1.0"),
                new BoardModel("Dell Inc.", "0M2MWX", "A00"),
                new BoardModel("HP", "8A50", "37.50")
            )
        ),
        new PlatformTemplate(
            true,
            List.of(
                new CpuModel("AMD Ryzen 5 5600H with Radeon Graphics",
                    "AMD64 Family 25 Model 80 Stepping 0"),
                new CpuModel("AMD Ryzen 7 5800H with Radeon Graphics",
                    "AMD64 Family 25 Model 80 Stepping 0"),
                new CpuModel("AMD Ryzen 7 6800H with Radeon Graphics",
                    "AMD64 Family 25 Model 68 Stepping 1")
            ),
            List.of(
                new BoardModel("LENOVO", "LNVNB161216", "SDK0T76461 WIN"),
                new BoardModel("ASUSTeK COMPUTER INC.", "GA402RJ", "1.0"),
                new BoardModel("Micro-Star International Co., Ltd.", "MS-1582", "REV:1.0"),
                new BoardModel("HP", "88F7", "KBC Version 69.16")
            )
        )
    );

    private static final List<String> ETHERNET_MODELS = List.of(
        "Realtek PCIe GbE Family Controller",
        "Realtek Gaming 2.5GbE Family Controller",
        "Intel(R) Ethernet Controller (3) I225-V",
        "Intel(R) Ethernet Controller I226-V",
        "Killer E2600 Gigabit Ethernet Controller"
    );
    private static final List<String> WIFI_MODELS = List.of(
        "Intel(R) Wi-Fi 6 AX200 160MHz",
        "Intel(R) Wi-Fi 6E AX210 160MHz",
        "MediaTek Wi-Fi 6 MT7921 Wireless LAN Card",
        "Realtek 8822CE Wireless LAN 802.11ac PCI-E NIC",
        "Killer(R) Wi-Fi 6 AX1650i 160MHz Wireless Network Adapter (201NGW)"
    );
    private static final List<String> SSD_MODELS = List.of(
        "Samsung SSD 980 PRO 1TB",
        "Samsung SSD 970 EVO Plus 1TB",
        "WD_BLACK SN770 1TB",
        "WDC PC SN730 SDBPNTY-512G-1001",
        "KINGSTON SNV2S1000G",
        "CT1000P3PSSD8",
        "KIOXIA-EXCERIA G2 SSD"
    );
    private static final List<String> HDD_MODELS = List.of(
        "ST1000DM010-2EP102",
        "ST2000DM008-2FR102",
        "WDC WD10EZEX-08WN4A0",
        "ST1000LM049-2GH172",
        "HGST HTS721010A9E630"
    );

    private static final Set<String> CPU_NAMES = collectCpuNames();
    private static final Set<String> BOARD_KEYS = collectBoardKeys();
    private static final Set<String> NETWORK_NAMES = union(ETHERNET_MODELS, WIFI_MODELS);
    private static final Set<String> DISK_NAMES = union(SSD_MODELS, HDD_MODELS);

    private Id1SyntheticHardwareCatalog() {
    }

    static Id1HwidProvider.HardwareEvidence generate(String seedHex) {
        Random random = new Random(seedLong(seedHex));
        PlatformTemplate platform = pick(PLATFORMS, random);
        CpuModel cpu = pick(platform.cpus(), random);
        BoardModel board = pick(platform.boards(), random);

        List<String> cpuInfo = List.of(
            randomHex(random, 16),
            cpu.name(),
            cpu.identifier()
        );
        List<String> computerSystem = List.of(
            board.manufacturer(),
            board.model(),
            boardSerial(random),
            board.version(),
            randomUuid(random).toString()
        );

        List<List<String>> networks = new ArrayList<>();
        boolean ethernet = !platform.portable() || random.nextBoolean();
        boolean wifi = platform.portable() || random.nextBoolean();
        if (!ethernet && !wifi) ethernet = true;
        if (ethernet) networks.add(network(random, "eth0", pick(ETHERNET_MODELS, random)));
        if (wifi) networks.add(network(random, "wlan0", pick(WIFI_MODELS, random)));

        int diskCount = 1 + random.nextInt(3);
        List<List<String>> disks = new ArrayList<>();
        for (int i = 0; i < diskCount; i++) {
            boolean solidState = i == 0 || random.nextBoolean();
            String model = pick(solidState ? SSD_MODELS : HDD_MODELS, random);
            disks.add(List.of(
                diskSerial(random, model),
                "\\\\.\\PHYSICALDRIVE" + i,
                model
            ));
        }

        return new Id1HwidProvider.HardwareEvidence(
            cpuInfo,
            computerSystem,
            List.copyOf(networks),
            List.copyOf(disks)
        );
    }

    static boolean isKnownCpuName(String value) {
        return CPU_NAMES.contains(value);
    }

    static boolean isKnownBoard(String manufacturer, String model) {
        return BOARD_KEYS.contains(manufacturer + "\u0000" + model);
    }

    static boolean isCoherentPlatform(String cpuName, String manufacturer, String boardModel) {
        for (PlatformTemplate platform : PLATFORMS) {
            boolean cpuMatches = platform.cpus().stream().anyMatch(cpu -> cpu.name().equals(cpuName));
            boolean boardMatches = platform.boards().stream().anyMatch(board ->
                board.manufacturer().equals(manufacturer) && board.model().equals(boardModel));
            if (cpuMatches && boardMatches) return true;
        }
        return false;
    }

    static boolean isKnownNetworkName(String value) {
        return NETWORK_NAMES.contains(value);
    }

    static boolean isKnownDiskName(String value) {
        return DISK_NAMES.contains(value);
    }

    private static List<String> network(Random random, String name, String displayName) {
        return List.of(
            name,
            displayName,
            randomMac(random),
            "[" + privateIpv4(random) + "]",
            random.nextBoolean() ? "[]" : "[" + linkLocalIpv6(random) + "]"
        );
    }

    private static String privateIpv4(Random random) {
        int host = 2 + random.nextInt(252);
        return switch (random.nextInt(3)) {
            case 0 -> "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + host;
            case 1 -> "172." + (16 + random.nextInt(16)) + "." + random.nextInt(256) + "." + host;
            default -> "192.168." + random.nextInt(256) + "." + host;
        };
    }

    private static String linkLocalIpv6(Random random) {
        return String.format(
            Locale.ROOT,
            "fe80::%x:%x:%x:%x",
            random.nextInt(0x10000),
            random.nextInt(0x10000),
            random.nextInt(0x10000),
            random.nextInt(0x10000)
        );
    }

    private static String randomMac(Random random) {
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        bytes[0] = (byte)((bytes[0] & 0xfe) | 0x02);
        StringBuilder builder = new StringBuilder(17);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) builder.append(':');
            builder.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xff));
        }
        return builder.toString();
    }

    private static String boardSerial(Random random) {
        return "MB" + randomCharacters(random, 12 + random.nextInt(5));
    }

    private static String diskSerial(Random random, String model) {
        String prefix = model.startsWith("Samsung") ? "S" : model.startsWith("WDC") || model.startsWith("WD_")
            ? "WD" : model.startsWith("ST") ? "Z" : model.startsWith("KINGSTON") ? "50026B" : "D";
        return prefix + randomCharacters(random, 10 + random.nextInt(7));
    }

    private static String randomCharacters(Random random, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return builder.toString();
    }

    private static String randomHex(Random random, int length) {
        byte[] bytes = new byte[(length + 1) / 2];
        random.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes).substring(0, length);
    }

    private static UUID randomUuid(Random random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        bytes[6] = (byte)((bytes[6] & 0x0f) | 0x40);
        bytes[8] = (byte)((bytes[8] & 0x3f) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static long seedLong(String seedHex) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(seedHex.getBytes(StandardCharsets.US_ASCII));
            return ByteBuffer.wrap(digest).getLong();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static <T> T pick(List<T> values, Random random) {
        return values.get(random.nextInt(values.size()));
    }

    private static Set<String> collectCpuNames() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PlatformTemplate platform : PLATFORMS) {
            for (CpuModel cpu : platform.cpus()) values.add(cpu.name());
        }
        return Set.copyOf(values);
    }

    private static Set<String> collectBoardKeys() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PlatformTemplate platform : PLATFORMS) {
            for (BoardModel board : platform.boards()) {
                values.add(board.manufacturer() + "\u0000" + board.model());
            }
        }
        return Set.copyOf(values);
    }

    private static Set<String> union(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>(first);
        values.addAll(second);
        return Set.copyOf(values);
    }

    private record CpuModel(String name, String identifier) {
    }

    private record BoardModel(String manufacturer, String model, String version) {
    }

    private record PlatformTemplate(
        boolean portable,
        List<CpuModel> cpus,
        List<BoardModel> boards
    ) {
    }
}
