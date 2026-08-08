package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class Id1PacketBuilderTest {
    @Test
    void completeSixteenPlusThirteenSprintHasStableGoldenAndUsesCachedJarDigests() throws Exception {
        Id1PacketBuilder builder = builderThatRejectsLiveJarReads();
        UUID uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        Id1PacketBuilder.Challenge challenge = new Id1PacketBuilder.Challenge(
            uuid,
            -9_223_372_036_854_775_000L,
            Id1PacketBuilder.Id1Subtype.SPRINT,
            null
        );
        Id1PacketBuilder.Context context = new Id1PacketBuilder.Context(uuid, 1_782_140_928_197L);
        Id1PacketBuilder.SprintEnvironment environment = completeEnvironment();

        byte[] preCrypto = builder.buildPreCrypto(challenge, context, environment);

        assertEquals("cbfac8a81b4d2e9cc44aee00c21b57b9fe7ffd350533616ccbb75beb03abf7a0",
            sha256(preCrypto));
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(preCrypto);
        assertEquals(context.writerTime(), reader.readLong());
        assertEquals(uuid, reader.readUuid());
        assertEquals(Id1PacketBuilder.Id1Subtype.SPRINT.wireId(), reader.readByte());
        assertEquals(uuid, reader.readUuid());
        assertEquals(challenge.packetLong(), reader.readLong());
        assertEquals(Id1PacketBuilder.OFFICIAL_LOADED_MOD_COUNT, reader.readArrayHeader());
        for (int index = 0; index < Id1PacketBuilder.OFFICIAL_LOADED_MOD_COUNT; index++) {
            assertEquals(String.format("module%02d", index), reader.readString());
            assertEquals(String.format("loaded/%02d.jar", index), reader.readString());
            assertEquals(String.format("loaded-digest-%02d", index), reader.readString());
        }
        assertEquals(".\\raw-user-dir\\.", reader.readString());
        assertEquals("java-home", reader.readString());
        for (int index = 0; index < 6; index++) reader.skipValue();
        assertEquals(Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT, reader.readArrayHeader());
        for (int index = 0; index < Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT; index++) {
            String jar = String.format("mods/top-%02d.jar", index);
            assertEquals("signed:" + jar, reader.readString());
            assertEquals("signed:" + String.format("jar-digest-%02d", index), reader.readString());
        }
        assertFalse(reader.hasRemaining());
    }

    @Test
    void rejectsPartialOrUncachedOfficialSprintSnapshots() {
        Id1PacketBuilder builder = builderThatRejectsLiveJarReads();
        UUID uuid = new UUID(0L, 0L);
        Id1PacketBuilder.Challenge challenge = new Id1PacketBuilder.Challenge(
            uuid, 1L, Id1PacketBuilder.Id1Subtype.SPRINT, null);
        Id1PacketBuilder.Context context = new Id1PacketBuilder.Context(uuid, 2L);
        Id1PacketBuilder.SprintEnvironment complete = completeEnvironment();

        assertThrows(IllegalStateException.class, () -> builder.buildPreCrypto(
            challenge,
            context,
            copyEnvironment(complete, complete.loadedMods().subList(0, 15), complete.discoveredJars(),
                complete.discoveredJarDigests())
        ));
        assertThrows(IllegalStateException.class, () -> builder.buildPreCrypto(
            challenge,
            context,
            copyEnvironment(complete, complete.loadedMods(), complete.discoveredJars().subList(0, 12),
                complete.discoveredJarDigests())
        ));
        LinkedHashMap<String, String> missingDigest = new LinkedHashMap<>(complete.discoveredJarDigests());
        missingDigest.remove(complete.discoveredJars().get(0));
        assertThrows(IllegalStateException.class, () -> builder.buildPreCrypto(
            challenge,
            context,
            copyEnvironment(complete, complete.loadedMods(), complete.discoveredJars(), missingDigest)
        ));
    }

    private static Id1PacketBuilder builderThatRejectsLiveJarReads() {
        return new Id1PacketBuilder(
            new Id1PacketBuilder.Id1SignatureProvider() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public String digestPathLike(String path) {
                    throw new AssertionError("SPRINT must not read a JAR after startup snapshot capture");
                }

                @Override
                public String signString(String value) {
                    return "signed:" + value;
                }
            },
            new Id1PacketBuilder.Id1CryptoTransform() {
                @Override
                public boolean available() {
                    return false;
                }

                @Override
                public byte[] transform(byte[] preCrypto) {
                    throw new AssertionError("buildPreCrypto does not invoke crypto");
                }
            },
            Id1PacketBuilder.EvidenceSampler.preserveOrder(),
            value -> value
        );
    }

    private static Id1PacketBuilder.SprintEnvironment completeEnvironment() {
        List<Id1PacketBuilder.ModEvidence> loadedMods = new ArrayList<>();
        for (int index = 0; index < Id1PacketBuilder.OFFICIAL_LOADED_MOD_COUNT; index++) {
            loadedMods.add(new Id1PacketBuilder.ModEvidence(
                String.format("module%02d", index),
                String.format("loaded/%02d.jar", index),
                String.format("loaded-digest-%02d", index)
            ));
        }
        List<String> jars = new ArrayList<>();
        LinkedHashMap<String, String> jarDigests = new LinkedHashMap<>();
        for (int index = 0; index < Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT; index++) {
            String jar = String.format("mods/top-%02d.jar", index);
            jars.add(jar);
            jarDigests.put(jar, String.format("jar-digest-%02d", index));
        }
        LinkedHashMap<String, Object> launcherProperties = new LinkedHashMap<>();
        launcherProperties.put("UserId", -42L);
        return new Id1PacketBuilder.SprintEnvironment(
            loadedMods,
            ".\\raw-user-dir\\.",
            "java-home",
            List.of("cpu-id", "cpu-name", "cpu-identifier"),
            List.of("board-maker", "board-model", "board-serial", "board-version", "hardware-uuid"),
            List.of(List.of("network", "display", "mac", "[127.0.0.1]", "[]")),
            List.of(List.of("disk-serial", "disk-name", "disk-model")),
            List.of("encoded-sibling"),
            launcherProperties,
            jars,
            "golden",
            "synthetic-test",
            "profile",
            true,
            "synthetic-id",
            1,
            jarDigests
        );
    }

    private static Id1PacketBuilder.SprintEnvironment copyEnvironment(
        Id1PacketBuilder.SprintEnvironment source,
        List<Id1PacketBuilder.ModEvidence> loadedMods,
        List<String> jars,
        Map<String, String> jarDigests
    ) {
        return new Id1PacketBuilder.SprintEnvironment(
            loadedMods,
            source.userDirectory(),
            source.javaHome(),
            source.cpuInfo(),
            source.computerSystemInfo(),
            source.networkInterfaces(),
            source.diskStores(),
            source.accountTraces(),
            source.userProperties(),
            jars,
            source.source(),
            source.hwidSource(),
            source.hwidProfile(),
            source.syntheticHwid(),
            source.syntheticHwidId(),
            source.syntheticHwidHistoryCount(),
            jarDigests
        );
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
