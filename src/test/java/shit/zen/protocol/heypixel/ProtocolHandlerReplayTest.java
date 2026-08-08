package shit.zen.protocol.heypixel;

import com.google.gson.JsonObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

final class ProtocolHandlerReplayTest {
    @Test
    void decodesRecoveredChallengePanelRecordsTokenAndJson() throws Exception {
        UUID uuid = new UUID(1, 2);
        byte[] challengePayload = new HeyPixelMsgpackWriter()
            .packString(uuid.toString()).packLong(3).packInt(3).packString("challenge")
            .toByteArray();
        S2CPacketDecoders.Id101Challenge challenge = S2CPacketDecoders.decodeId101(challengePayload);
        assertEquals(uuid, challenge.packetUuid());
        assertEquals(3, challenge.packetLong());
        assertEquals(Id1PacketBuilder.Id1Subtype.ATTACK, challenge.subtype());
        assertEquals("challenge", challenge.challengeValue());

        S2CPacketDecoders.Id101Challenge sprint = S2CPacketDecoders.decodeId101(
            new HeyPixelMsgpackWriter().packString(uuid.toString()).packLong(4).packInt(0).toByteArray());
        assertEquals(Id1PacketBuilder.Id1Subtype.SPRINT, sprint.subtype());
        assertEquals(null, sprint.challengeValue());

        byte[] panelRecordsPayload = new HeyPixelMsgpackWriter()
            .packArrayHeader(1).packString("key").packInt(1).packInt(2)
            .packString("a").packString("b").packString("c").packString("d")
            .packArrayHeader(1)
            .toByteArray();
        S2CPacketDecoders.S2CHudInfoBatchPacket hudBatch =
            S2CPacketDecoders.decodeHudInfoBatch(panelRecordsPayload);
        assertEquals("key", hudBatch.entries().get(0).hudId());
        assertEquals("d", hudBatch.entries().get(0).hudInfo().key());
        List<S2CPacketDecoders.PanelRecord> panelRecords =
            S2CPacketDecoders.decodeId111(panelRecordsPayload);
        assertEquals("key", panelRecords.get(0).key());

        SyncTokenMetadata syncMetadata = S2CPacketDecoders.decodeId114(
            new HeyPixelMsgpackWriter().packString("sync").toByteArray());
        assertEquals(4, syncMetadata.tokenLength());
        assertEquals("unknown", syncMetadata.format());
        assertFalse(syncMetadata.nativeSinkAvailable());

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
    void updatesProtocolStateWithoutInventingFollowUpPackets() {
        HeyPixelProtocolState state = new HeyPixelProtocolState();
        state.replacePanelRecords(List.of(
            new S2CPacketDecoders.PanelRecord("key", 1, 2, "a", "b", "c", "d")));
        state.applyPanelModelOperation(
            new S2CPacketDecoders.PanelModelOperationPacket(0, List.of("{}")));
        state.setSyncTokenMetadata(SyncTokenMetadata.fromToken("sync"));
        state.putJsonState(119, "{\"type\":\"players\"}");
        assertTrue(state.panelRecords().containsKey("key"));
        assertEquals("d", state.hudInfos().get("key").key());
        assertEquals(S2CPacketDecoders.PanelModelOperation.UPDATE,
            state.panelModelOperation().orElseThrow());
        assertEquals(List.of("{}"), state.panelModelJsonEntries());
        assertEquals(4, state.syncTokenMetadata().orElseThrow().tokenLength());
        assertTrue(state.jsonStates().containsKey(119));
    }

    @Test
    void dispatchesAStubServerSequenceIntoOneCache() throws Exception {
        HeyPixelProtocolState state = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);
        UUID uuid = new UUID(1, 2);
        dispatcher.dispatch(wrap(101, new HeyPixelMsgpackWriter()
            .packString(uuid.toString()).packLong(3).packInt(3).packString("challenge")
            .toByteArray()));
        dispatcher.dispatch(wrap(111, new HeyPixelMsgpackWriter()
            .packArrayHeader(1).packString("key").packInt(1).packInt(2)
            .packString("a").packString("b").packString("c").packString("d")
            .packArrayHeader(1)
            .toByteArray()));
        dispatcher.dispatch(wrap(114, new HeyPixelMsgpackWriter().packString("sync").toByteArray()));

        String json = "{\"type\":\"players\"}";
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(json.getBytes(StandardCharsets.UTF_8));
        }
        dispatcher.dispatch(wrap(119, compressed.toByteArray()));

        assertEquals(uuid, state.environmentChallenge().orElseThrow().packetUuid());
        assertTrue(state.hudInfos().containsKey("key"));
        assertEquals("d", state.hudInfos().get("key").key());
        assertEquals(4, state.syncTokenMetadata().orElseThrow().tokenLength());
        assertEquals(json, state.jsonStates().get(119));

        state.reset();
        assertTrue(state.hudInfos().isEmpty());
        assertTrue(state.panelRecords().isEmpty());
    }

    @Test
    void decodesAndCachesBoardInfoWithOfficialBoardIdTabsAndOpenKeys() throws Exception {
        String json = "{\"boardId\":\"board-1\",\"tabs\":[{\"id\":1}],\"open\":true}";
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(json.getBytes(StandardCharsets.UTF_8));
        }

        HeyPixelProtocolState state = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);
        HeyPixelProtocolDispatcher.CanonicalDispatchResult result =
            dispatcher.dispatchCanonical(wrap(121, compressed.toByteArray()));

        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.BOARD_INFO, result.kind());
        S2CPacketDecoders.BoardInfoPacket boardInfo = state.boardInfo().orElseThrow();
        assertTrue(boardInfo.zlibCompressed());
        assertEquals("board-1", boardInfo.boardId().orElseThrow());
        assertEquals(1, boardInfo.tabs().orElseThrow().size());
        assertTrue(boardInfo.open().orElseThrow());
        JsonObject returned = boardInfo.state();
        returned.addProperty("mutated", true);
        assertFalse(state.boardInfo().orElseThrow().state().has("mutated"));
        assertEquals(boardInfo.json(), state.jsonStates().get(121));
    }

    @Test
    void stripsTheBinaryBridgeDiscriminatorBeforeReadingBusinessId() {
        byte[] wire = new byte[]{
            (byte) S2CPacketDecoders.BINARY_BRIDGE_DISCRIMINATOR,
            0, 0, 0, 119,
            1, 2, 3
        };
        S2CPacketDecoders.WrappedPacket wrapped = S2CPacketDecoders.decodeWrapper(wire);
        assertEquals(119, wrapped.packetId());
        assertEquals(3, wrapped.payload().length);
    }

    @Test
    void unwrapsId114AsLengthPrefixedPlaintextAndKeepsPaddingOutOfMsgpack() {
        byte[] messagePack = new HeyPixelMsgpackWriter().packString("HPAC5.test-token").toByteArray();
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(S2CPacketDecoders.BINARY_BRIDGE_DISCRIMINATOR);
        wire.writeBytes(new byte[]{0, 0, 0, 114});
        writeVarInt(wire, messagePack.length);
        wire.writeBytes(messagePack);
        wire.writeBytes(new byte[32]);

        S2CPayloadUnwrapper.UnwrappedPacket unwrapped =
            S2CPayloadUnwrapper.unwrap(wire.toByteArray(), null);

        assertEquals(114, unwrapped.packetId());
        assertFalse(unwrapped.encrypted());
        assertEquals(32, unwrapped.trailingBytes());
        SyncTokenMetadata metadata = S2CPacketDecoders.decodeId114(unwrapped.payload());
        assertEquals("HPAC5.test-token".length(), metadata.tokenLength());
        assertEquals("unknown", metadata.format());
        assertFalse(metadata.toString().contains("HPAC5.test-token"));
    }

    @Test
    void parsesTheRecoveredHpac5EnvelopeWithoutExposingItsCiphertext() {
        String tokenValue = "HPAC5.02020000019eefe0879b0000019eefe0fccb."
                + "fd858d8c05051633822b7db4."
                + "23a72efa."
                + "dff085da906c82463aef4b78a714d87a";
        Hpac5SyncToken token = Hpac5SyncToken.parse(tokenValue).orElseThrow();

        assertEquals("0202", token.versionMarker());
        assertEquals(30_000L, token.validityMillis());
        assertEquals(12, token.nonceBytes());
        assertEquals(4, token.ciphertextBytes());
        assertEquals(16, token.tagBytes());

        SyncTokenMetadata metadata = S2CPacketDecoders.decodeId114(
            new HeyPixelMsgpackWriter().packString(tokenValue).toByteArray());
        assertEquals("HPAC5", metadata.format());
        assertEquals(token, metadata.hpac5().orElseThrow());
        assertFalse(metadata.toString().contains(tokenValue));
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        int remaining = value;
        do {
            int next = remaining & 0x7f;
            remaining >>>= 7;
            if (remaining != 0) next |= 0x80;
            output.write(next);
        } while (remaining != 0);
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
