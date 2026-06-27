package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

final class ProtocolHandlerReplayTest {
    @Test
    void decodesRecoveredChallengeRulesTokenAndJson() throws Exception {
        UUID uuid = new UUID(1, 2);
        byte[] challengePayload = new HeyPixelMsgpackWriter()
            .packString(uuid.toString()).packLong(3).packString("SPRINT").packString("challenge")
            .toByteArray();
        S2CPacketDecoders.Id101Challenge challenge = S2CPacketDecoders.decodeId101(challengePayload);
        assertEquals(uuid, challenge.packetUuid());
        assertEquals(3, challenge.packetLong());

        byte[] rulesPayload = new HeyPixelMsgpackWriter()
            .packArrayHeader(1).packString("key").packInt(1).packInt(2)
            .packString("a").packString("b").packString("c").packString("d")
            .toByteArray();
        List<S2CPacketDecoders.Id111Record> rules = S2CPacketDecoders.decodeId111(rulesPayload);
        assertEquals("key", rules.get(0).key());

        assertEquals("sync", S2CPacketDecoders.decodeId114(
            new HeyPixelMsgpackWriter().packString("sync").toByteArray()));

        String json = "{\"type\":\"players\"}";
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(json.getBytes(StandardCharsets.UTF_8));
        }
        S2CPacketDecoders.JsonPayload decoded = S2CPacketDecoders.decodeJsonPacket(119, compressed.toByteArray());
        assertTrue(decoded.zlibCompressed());
        assertEquals(json, decoded.json());
    }

    @Test
    void updatesRuleCacheWithoutInventingFollowUpPackets() {
        ProtocolRuleCache cache = new ProtocolRuleCache();
        cache.replaceRules(List.of(new S2CPacketDecoders.Id111Record("key", 1, 2, "a", "b", "c", "d")));
        cache.applyUpdate(new S2CPacketDecoders.Id112Update(0, List.of("{}")));
        cache.setSyncToken("sync");
        cache.putJsonState(119, "{\"type\":\"players\"}");
        assertTrue(cache.rules().isEmpty());
        assertEquals(List.of("{}"), cache.updateEntries());
        assertEquals("sync", cache.syncToken().orElseThrow());
        assertTrue(cache.jsonState().containsKey(119));
    }

    @Test
    void dispatchesAStubServerSequenceIntoOneCache() throws Exception {
        ProtocolRuleCache cache = new ProtocolRuleCache();
        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(cache);
        UUID uuid = new UUID(1, 2);
        dispatcher.dispatch(wrap(101, new HeyPixelMsgpackWriter()
            .packString(uuid.toString()).packLong(3).packString("SPRINT").packString("challenge")
            .toByteArray()));
        dispatcher.dispatch(wrap(111, new HeyPixelMsgpackWriter()
            .packArrayHeader(1).packString("key").packInt(1).packInt(2)
            .packString("a").packString("b").packString("c").packString("d")
            .toByteArray()));
        dispatcher.dispatch(wrap(114, new HeyPixelMsgpackWriter().packString("sync").toByteArray()));

        String json = "{\"type\":\"players\"}";
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(json.getBytes(StandardCharsets.UTF_8));
        }
        dispatcher.dispatch(wrap(119, compressed.toByteArray()));

        assertEquals(uuid, cache.challenge().orElseThrow().packetUuid());
        assertTrue(cache.rules().containsKey("key"));
        assertEquals("sync", cache.syncToken().orElseThrow());
        assertEquals(json, cache.jsonState().get(119));
    }

    private static byte[] wrap(int packetId, byte[] payload) {
        byte[] wire = new byte[payload.length + 4];
        wire[0] = (byte)(packetId >>> 24);
        wire[1] = (byte)(packetId >>> 16);
        wire[2] = (byte)(packetId >>> 8);
        wire[3] = (byte)packetId;
        System.arraycopy(payload, 0, wire, 4, payload.length);
        return wire;
    }
}
