package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ProtocolWireGoldenTest {
    @Test
    void wrapsBusinessPacketsWithTheBinaryBridgeDiscriminator() {
        assertArrayEquals(
            new byte[]{(byte) S2CPacketDecoders.BINARY_BRIDGE_DISCRIMINATOR, 1, 2, 3},
            HeyPixelOuterBridge.wrapBinary(new byte[]{1, 2, 3})
        );
    }

    @Test
    void rebuildsCapturedId2Id3AndId5Wire() {
        assertHex("0213cf0000019eefe085a0cf0000019eefe085a0",
            C2SPacketEncoders.encodeHeartbeat(1782140929440L, 1782140929440L));
        assertHex("030ccf0000019eefe0abfe0100",
            C2SPacketEncoders.encodeCpsTelemetry(1782140939262L, 1, 0));
        assertHex(
            "0569cf0000019eefe2a702cb40707a8638e7ccc2cb404b1fe55cb8e5a8cbc053b05ba826efa80101"
                + "cb40707fbccf0fe8a1cb404b000000000000cbc052b814a1278be4cb4070700000000000cb404a800000000000"
                + "cbc052c00000000000c2cac099a69aca41cd3341c3",
            C2SPacketEncoders.encodeUseBlockTelemetry(new C2SPacketEncoders.Id5UseBlock(
                1782141069058L,
                263.6577691130334,
                54.24918707874468,
                -78.75559428980216,
                1,
                1,
                263.98359590734805,
                54.0,
                -74.87625912534673,
                263.0,
                53.0,
                -75.0,
                false,
                -4.801587104797363f,
                25.650026321411133f,
                true
            ))
        );
    }

    @Test
    void encodesId0AsTheRecoveredEmptyMarkerWithoutAddingATrigger() {
        assertHex("0001", C2SPacketEncoders.encodeId0());
    }

    @Test
    @SuppressWarnings("deprecation")
    void encodesTheRecoveredId7PanelActionsAndKeepsLegacyAdapters() {
        C2SPacketEncoders.C2SPanelActionPacket banner =
            C2SPacketEncoders.C2SPanelActionPacket.banner(2L);
        C2SPacketEncoders.C2SPanelActionPacket close =
            C2SPacketEncoders.C2SPanelActionPacket.close(2L);

        assertEquals("banner", banner.action());
        assertEquals("close", close.action());
        assertHex("070b0102d90662616e6e6572",
            C2SPacketEncoders.encodePanelAction(1L, banner));
        byte[] closeWire = C2SPacketEncoders.encodePanelAction(1L, close);
        assertHex("070a0102d905636c6f7365", closeWire);
        assertArrayEquals(closeWire, C2SPacketEncoders.encodeId7(1L, 2L, "close"));
        assertArrayEquals(closeWire, C2SPacketEncoders.encodePanelState(1L, 2L, "close"));
        assertThrows(NullPointerException.class,
            () -> C2SPacketEncoders.encodePanelAction(1L, 2L, null));
    }

    @Test
    void rawFramingAccountsForTheFullPacketIdVarIntWidth() {
        assertHex("7f01", C2SPacketEncoders.frameRawPayload(127, new byte[0]));
        assertHex("800102", C2SPacketEncoders.frameRawPayload(128, new byte[0]));
        assertHex("ac0202", C2SPacketEncoders.frameRawPayload(300, new byte[0]));
        assertThrows(IllegalArgumentException.class,
            () -> C2SPacketEncoders.frameRawPayload(-1, new byte[0]));
        assertThrows(NullPointerException.class,
            () -> C2SPacketEncoders.frameRawPayload(1, null));
    }

    @Test
    void exposesOfficialResourceAndResourceIndexNamesWithoutChangingLegacyWire() {
        assertArrayEquals(
            C2SPacketEncoders.encodeId108(7L, "hud/icon.png", "sha1"),
            C2SPacketEncoders.encodeResourceBlob(7L, "hud/icon.png", "sha1")
        );
        assertArrayEquals(
            C2SPacketEncoders.encodeId109(8L, "fashion", "cache-hash", 1),
            C2SPacketEncoders.encodeResourceIndex(8L, "fashion", "cache-hash", 1)
        );
        assertArrayEquals(
            C2SPacketEncoders.encodeChunkedData(8L, "fashion", "cache-hash", 1),
            C2SPacketEncoders.encodeResourceIndex(8L, "fashion", "cache-hash", 1)
        );
    }

    @Test
    void retainsTheGenericId8MapBoundaryForForwardCompatibleShopRequests() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation-key", "infos-literal");
        payload.put("argument-key", "shop-key");
        assertRawJson(
            8,
            "{\"operation-key\":\"infos-literal\",\"argument-key\":\"shop-key\"}",
            C2SPacketEncoders.encodeShopRequest(payload)
        );
    }

    @Test
    void encodesTheRecoveredId8BuyAndInfosFactories() {
        assertRawJson(8, "{\"type\":\"buy\",\"item\":\"item-1\"}",
            C2SPacketEncoders.encodeShopRequest(
                C2SPacketEncoders.C2SShopRequestPacket.buy("item-1")));
        assertRawJson(8, "{\"type\":\"infos\",\"keys\":\"keys-2\"}",
            C2SPacketEncoders.encodeShopRequest(
                C2SPacketEncoders.C2SShopRequestPacket.infos("keys-2")));
        assertThrows(NullPointerException.class,
            () -> C2SPacketEncoders.C2SShopRequestPacket.buy(null));
        assertThrows(NullPointerException.class,
            () -> C2SPacketEncoders.C2SShopRequestPacket.infos(null));
    }

    @Test
    void encodesTheRecoveredId9HexSelectionActions() {
        assertRawJson(
            9,
            "{\"sessionId\":42,\"actionType\":\"select\",\"slotIndex\":1,\"key\":\"a1\"}",
            C2SPacketEncoders.encodeHexSelectionClick(
                C2SPacketEncoders.C2SHexSelectionClickPacket.select(42L, 1, "a1"))
        );
        assertRawJson(
            9,
            "{\"sessionId\":43,\"actionType\":\"reroll\",\"slotIndex\":2,\"key\":\"b2\"}",
            C2SPacketEncoders.encodeHexSelectionClick(
                C2SPacketEncoders.C2SHexSelectionClickPacket.reroll(43L, 2, "b2"))
        );

        C2SPacketEncoders.C2SHexSelectionClickPacket raw =
            new C2SPacketEncoders.C2SHexSelectionClickPacket(44L, "future", 7, "c3");
        assertRawJson(
            9,
            "{\"sessionId\":44,\"actionType\":\"future\",\"slotIndex\":7,\"key\":\"c3\"}",
            C2SPacketEncoders.encodeHexSelectionClick(raw)
        );
    }

    @Test
    void encodesEveryRecoveredId10FashionInfoFactoryWithOfficialKeys() {
        assertRawJson(10, "{\"type\":\"infos\"}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.infos()));

        LinkedHashMap<String, Object> equipped = new LinkedHashMap<>();
        equipped.put("hat", "fashion-a");
        equipped.put("cape", "fashion-a");
        equipped.put("pet", 3);
        assertRawJson(
            10,
            "{\"type\":\"apply\",\"equipped\":{\"hat\":\"fashion-a\","
                + "\"cape\":\"fashion-a\",\"pet\":3},\"fashions\":[\"fashion-a\"]}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.apply(equipped))
        );
        assertRawJson(10, "{\"type\":\"apply\",\"fashions\":[\"a\",\"b\"]}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.apply(List.of("a", "b"))));
        assertRawJson(
            10,
            "{\"type\":\"exchange\",\"fashion\":\"f\",\"group\":\"g\","
                + "\"exchange\":\"x\",\"day\":0}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.exchange("f", "g", "x"))
        );
        assertRawJson(
            10,
            "{\"type\":\"exchange\",\"fashion\":\"f\",\"group\":\"g\","
                + "\"exchange\":\"x\",\"day\":4}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.exchange("f", "g", "x", 4))
        );
        assertRawJson(
            10,
            "{\"type\":\"obtain\",\"fashion\":\"f\",\"group\":\"g\","
                + "\"obtainCommandKey\":\"command\"}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.obtain("f", "g", "command"))
        );
        assertRawJson(10, "{\"type\":\"equip\",\"fashion\":\"f\",\"group\":\"g\"}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.equip("f", "g")));
        assertRawJson(
            10,
            "{\"type\":\"unequip_category\",\"category\":\"hat\"}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.unequipCategory("hat"))
        );
        assertRawJson(10, "{\"type\":\"unequip_all\"}",
            C2SPacketEncoders.encodeFashionInfo(
                C2SPacketEncoders.C2SFashionInfoPacket.unequipAll()));
    }

    @Test
    void encodesId11BoardActionAndRequestBoardWithDeterministicMetadata() {
        assertThrows(NullPointerException.class, () -> C2SPacketEncoders.encodeId11(null));
        C2SPacketEncoders.C2SBoardActionPacket action =
            C2SPacketEncoders.C2SBoardActionPacket.createBoardAction(
                "request-1",
                "board-1",
                7,
                "tab-2",
                "select",
                "action-3",
                123456789L,
                Map.of("slot", 2)
            );
        assertRawJson(
            11,
            "{\"protocol\":1,\"type\":\"board_action\",\"requestId\":\"request-1\","
                + "\"boardId\":\"board-1\",\"revision\":7,\"tabId\":\"tab-2\","
                + "\"action\":\"select\",\"actionId\":\"action-3\","
                + "\"clientTime\":123456789,\"payload\":{\"slot\":2}}",
            C2SPacketEncoders.encodeBoardAction(action)
        );

        assertRawJson(
            11,
            "{\"protocol\":1,\"type\":\"board_action\",\"requestId\":\"request-2\","
                + "\"boardId\":\"board-2\",\"revision\":0,\"tabId\":\"\","
                + "\"action\":\"request_board\",\"actionId\":\"request_board\","
                + "\"clientTime\":987654321,\"payload\":{}}",
            C2SPacketEncoders.encodeBoardAction(
                C2SPacketEncoders.C2SBoardActionPacket.requestBoard(
                    "request-2", "board-2", 987654321L))
        );

        C2SPacketEncoders.C2SBoardActionPacket nullPayload =
            C2SPacketEncoders.C2SBoardActionPacket.createBoardAction(
                "request-3", "board-3", 0, "", "noop", "noop", 5L, null);
        assertEquals(Map.of(), nullPayload.map().get("payload"));
    }

    @Test
    void snapshotsNestedJsonValuesBeforeEncoding() {
        LinkedHashMap<String, Object> equipped = new LinkedHashMap<>();
        equipped.put("hat", "fashion-a");
        C2SPacketEncoders.C2SFashionInfoPacket packet =
            C2SPacketEncoders.C2SFashionInfoPacket.apply(equipped);
        equipped.put("hat", "mutated");
        assertEquals("fashion-a", packet.map().get("equipped") instanceof Map<?, ?> nested
            ? nested.get("hat") : null);

        JsonObject mutableJson = new JsonObject();
        mutableJson.addProperty("value", "original");
        C2SPacketEncoders.C2SFashionInfoPacket jsonPacket =
            new C2SPacketEncoders.C2SFashionInfoPacket(Map.of("state", mutableJson));
        mutableJson.addProperty("value", "input-mutated");
        JsonObject returned = (JsonObject) jsonPacket.map().get("state");
        returned.addProperty("value", "accessor-mutated");
        assertEquals("original",
            ((JsonObject) jsonPacket.map().get("state")).get("value").getAsString());

        AtomicInteger mutableNumber = new AtomicInteger(3);
        C2SPacketEncoders.C2SFashionInfoPacket numberPacket =
            new C2SPacketEncoders.C2SFashionInfoPacket(Map.of("counter", mutableNumber));
        mutableNumber.set(9);
        assertRawJson(10, "{\"counter\":3}",
            C2SPacketEncoders.encodeFashionInfo(numberPacket));

        C2SPacketEncoders.C2SFashionInfoPacket nullableFashion =
            C2SPacketEncoders.C2SFashionInfoPacket.exchange(null, null, null, 0);
        assertTrue(nullableFashion.map().containsKey("fashion"));
        assertNull(nullableFashion.map().get("fashion"));
        assertRawJson(10, "{\"type\":\"exchange\",\"day\":0}",
            C2SPacketEncoders.encodeFashionInfo(nullableFashion));

        C2SPacketEncoders.C2SBoardActionPacket nullableBoard =
            C2SPacketEncoders.C2SBoardActionPacket.createBoardAction(
                null, null, 0, null, null, null, 0L, null);
        assertTrue(nullableBoard.map().containsKey("boardId"));
        assertNull(nullableBoard.map().get("boardId"));
        assertRawJson(11,
            "{\"protocol\":1,\"type\":\"board_action\",\"revision\":0,"
                + "\"clientTime\":0,\"payload\":{}}",
            C2SPacketEncoders.encodeBoardAction(nullableBoard));
    }

    @Test
    void preservesObservedId1LayoutSixFraming() {
        UUID uuid = new UUID(5475196311097722046L, -4634851894786249576L);
        UuidSelectedPayloadFramer framer = new UuidSelectedPayloadFramer();
        assertHex("0104101828", framer.framePacket(1, new byte[]{0x01, (byte)0x81, (byte)0x82}, uuid));
    }

    @Test
    void layoutFiveReversesOnlyCompleteDwordsAndLeavesTheRemainderLast() {
        UUID uuid = new UUID(0L, 5L);
        UuidSelectedPayloadFramer framer = new UuidSelectedPayloadFramer();
        assertEquals(5, framer.selectLayout(uuid));
        assertHex(
            "010b04050607000102030809",
            framer.framePacket(1, new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, uuid)
        );
        assertArrayEquals(new byte[]{1, 2, 3},
            framer.applyLayout(new byte[]{1, 2, 3}, 5));
    }

    @Test
    void initialSprintId1FramesPreCryptoWithoutCallingTheChallengeTransform() {
        UUID uuid = UUID.fromString("4bfbce09-3e82-44be-bfad-b2c218b82c98");
        AtomicBoolean transformed = new AtomicBoolean();
        Id1PacketBuilder builder = new Id1PacketBuilder(
            new Id1PacketBuilder.Id1SignatureProvider() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public String digestPathLike(String path) {
                    return "digest:" + path;
                }

                @Override
                public String signString(String value) {
                    return "signed:" + value;
                }
            },
            new Id1PacketBuilder.Id1CryptoTransform() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public byte[] transform(byte[] preCrypto) {
                    transformed.set(true);
                    return new byte[]{0x55};
                }
            },
            Id1PacketBuilder.EvidenceSampler.preserveOrder(),
            value -> value
        );
        Id1PacketBuilder.Challenge challenge = new Id1PacketBuilder.Challenge(
            uuid, 1782140928153L, Id1PacketBuilder.Id1Subtype.SPRINT, null);
        Id1PacketBuilder.Context context = new Id1PacketBuilder.Context(uuid, 1782140928197L);
        Id1PacketBuilder.SprintEnvironment environment = completeSprintEnvironment(
            java.util.Map.of("UserId", 2952423644L));

        byte[] preCrypto = builder.buildPreCrypto(challenge, context, environment);
        byte[] expected = new UuidSelectedPayloadFramer().framePacket(1, preCrypto, uuid);
        Id1PacketBuilder.BuiltPacket built = builder.buildInitialSprint(challenge, context, environment);
        assertArrayEquals(preCrypto, built.preCrypto());
        assertArrayEquals(expected, built.wire());
        org.junit.jupiter.api.Assertions.assertEquals(
            new UuidSelectedPayloadFramer().selectLayout(uuid), built.layout());
        assertArrayEquals(expected, builder.buildInitialSprintPacket(challenge, context, environment));
        org.junit.jupiter.api.Assertions.assertFalse(transformed.get());
    }

    @Test
    void initialSprintId1RejectsChallengeShapes() {
        UUID uuid = UUID.fromString("4bfbce09-3e82-44be-bfad-b2c218b82c98");
        Id1PacketBuilder builder = new Id1PacketBuilder(
            unavailableSignatures(), unavailableCrypto(), Id1PacketBuilder.EvidenceSampler.preserveOrder(), value -> value);
        Id1PacketBuilder.Context context = new Id1PacketBuilder.Context(uuid, 1L);
        Id1PacketBuilder.SprintEnvironment environment = new Id1PacketBuilder.SprintEnvironment(
            List.of(), "", "", List.of(), List.of(), List.of(), List.of(), List.of(), java.util.Map.of(),
            List.of(), "test", "real", "", false, "", 0);

        assertThrows(IllegalArgumentException.class, () -> builder.buildInitialSprintPacket(
            new Id1PacketBuilder.Challenge(uuid, 1L, Id1PacketBuilder.Id1Subtype.ATTACK, null), context, environment));
        assertThrows(IllegalArgumentException.class, () -> builder.buildInitialSprintPacket(
            new Id1PacketBuilder.Challenge(UUID.randomUUID(), 1L, Id1PacketBuilder.Id1Subtype.SPRINT, null),
            context, environment));
    }

    private static Id1PacketBuilder.Id1SignatureProvider unavailableSignatures() {
        return new Id1PacketBuilder.Id1SignatureProvider() {
            public boolean available() { return false; }
            public String digestPathLike(String path) { return ""; }
            public String signString(String value) { return ""; }
        };
    }

    private static Id1PacketBuilder.Id1CryptoTransform unavailableCrypto() {
        return new Id1PacketBuilder.Id1CryptoTransform() {
            public boolean available() { return false; }
            public byte[] transform(byte[] preCrypto) { return preCrypto; }
        };
    }

    private static Id1PacketBuilder.SprintEnvironment completeSprintEnvironment(Object userProperties) {
        List<Id1PacketBuilder.ModEvidence> loadedMods = new java.util.ArrayList<>();
        for (int index = 0; index < Id1PacketBuilder.OFFICIAL_LOADED_MOD_COUNT; index++) {
            loadedMods.add(new Id1PacketBuilder.ModEvidence(
                "module-" + index, "loaded-" + index + ".jar", "loaded-digest-" + index));
        }
        List<String> jars = new java.util.ArrayList<>();
        LinkedHashMap<String, String> jarDigests = new LinkedHashMap<>();
        for (int index = 0; index < Id1PacketBuilder.OFFICIAL_TOP_LEVEL_JAR_COUNT; index++) {
            String jar = "top-level-" + index + ".jar";
            jars.add(jar);
            jarDigests.put(jar, "digest:" + jar);
        }
        return new Id1PacketBuilder.SprintEnvironment(
            loadedMods, "game", "java", List.of(), List.of(), List.of(), List.of(),
            List.of(), userProperties, jars, "test", "real", "", false, "", 0, jarDigests);
    }

    private static void assertHex(String expected, byte[] actual) {
        assertArrayEquals(unhex(expected), actual);
    }

    private static void assertRawJson(int packetId, String expectedJson, byte[] actual) {
        byte[] payload = expectedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        writeVarIntIndependent(expected, packetId);
        writeVarIntIndependent(expected, payload.length + independentVarIntSize(packetId));
        expected.writeBytes(payload);
        assertArrayEquals(expected.toByteArray(), actual);
    }

    private static int independentVarIntSize(int value) {
        int bytes = 1;
        while (value >= 0x80) {
            value >>>= 7;
            bytes++;
        }
        return bytes;
    }

    private static void writeVarIntIndependent(ByteArrayOutputStream output, int value) {
        do {
            int next = value & 0x7f;
            value >>>= 7;
            if (value != 0) next |= 0x80;
            output.write(next);
        } while (value != 0);
    }

    private static byte[] unhex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
