package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

final class S2CExtendedPacketDecodersTest {
    @Test
    void decodesId100AndBothId103WireShapesWithOfficialLeftRightCpsOrder() {
        UUID local = new UUID(1, 2);
        UUID remote = new UUID(3, 4);

        S2CPacketDecoders.Id100Packet id100 = S2CPacketDecoders.decodeId100(
            new HeyPixelMsgpackWriter().packValue(local.toString()).packLong(7).toByteArray());
        assertEquals(local, id100.field00());
        assertEquals(7, id100.field01());
        byte[] id100WithTrailingValue = new HeyPixelMsgpackWriter()
            .packValue(local.toString()).packLong(7).packInt(8).toByteArray();
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeId100(id100WithTrailingValue));
        S2CPacketDecoders.Id100Packet officialPrefix =
            S2CPacketDecoders.decodeId100OfficialPrefix(id100WithTrailingValue);
        assertEquals(local, officialPrefix.field00());
        assertEquals(7, officialPrefix.field01());
        HeyPixelProtocolState id100State = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher id100Dispatcher = new HeyPixelProtocolDispatcher(id100State);
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.ID100_STATE,
            id100Dispatcher.dispatchCanonical(100, id100WithTrailingValue).kind());
        assertEquals(officialPrefix, id100State.id100Packet().orElseThrow());
        assertThrows(IllegalArgumentException.class,
            () -> id100Dispatcher.dispatch(100, id100WithTrailingValue));

        S2CPacketDecoders.Id103CpsTelemetry legacy = S2CPacketDecoders.decodeId103(
            writeCpsEntry(new HeyPixelMsgpackWriter(), local, 11, 2, 3).toByteArray());
        assertFalse(legacy.batchEncoding());
        assertEquals(1, legacy.entries().size());
        assertEquals(2, legacy.entries().get(0).leftCps());
        assertEquals(3, legacy.entries().get(0).rightCps());
        assertEquals(11, legacy.entries().get(0).timestampMillis());
        assertEquals(11, legacy.entries().get(0).field01());
        assertEquals(2, legacy.entries().get(0).field02());
        assertEquals(3, legacy.entries().get(0).field03());
        assertEquals(1, legacy.remoteEntries(local).size());

        HeyPixelMsgpackWriter batchWriter = new HeyPixelMsgpackWriter().packArrayHeader(2);
        batchWriter.packArrayHeader(4);
        writeCpsEntry(batchWriter, local, 12, 4, 5);
        batchWriter.packArrayHeader(4);
        writeCpsEntry(batchWriter, remote, 13, 6, 7);
        S2CPacketDecoders.Id103CpsTelemetry batch =
            S2CPacketDecoders.decodeId103(batchWriter.toByteArray());
        assertTrue(batch.batchEncoding());
        assertEquals(2, batch.entries().size());
        assertEquals(remote, batch.remoteEntries(local).get(0).playerUuid());

        HeyPixelMsgpackWriter extendedBatch = new HeyPixelMsgpackWriter().packArrayHeader(1);
        extendedBatch.packArrayHeader(5);
        writeCpsEntry(extendedBatch, remote, 14, 8, 9);
        extendedBatch.packArrayHeader(2).packString("future").packInt(1);
        S2CPacketDecoders.Id103CpsTelemetry extended =
            S2CPacketDecoders.decodeId103(extendedBatch.toByteArray());
        assertEquals(1, extended.entries().size());
        assertEquals(0, extended.ignoredTrailingBytes());

        byte[] withTopLevelTail = new HeyPixelMsgpackWriter()
            .packValue(remote.toString()).packLong(15).packInt(10).packInt(11)
            .packString("ignored")
            .toByteArray();
        assertTrue(S2CPacketDecoders.decodeId103(withTopLevelTail).ignoredTrailingBytes() > 0);

        byte[] malformed = new HeyPixelMsgpackWriter()
            .packArrayHeader(1).packArrayHeader(3)
            .packValue(local.toString()).packLong(1).packInt(2)
            .toByteArray();
        assertThrows(IllegalArgumentException.class, () -> S2CPacketDecoders.decodeId103(malformed));
    }

    @Test
    void decodesSoundFlightLeanAndTheGameStorePopupInstruction() {
        UUID player = new UUID(5, 6);
        S2CPacketDecoders.ActivationEffectPacket effect = S2CPacketDecoders.decodeActivationEffect(
            new HeyPixelMsgpackWriter()
                .packByte((byte) 2)
                .packString("unknown-effect")
                .packString("minecraft:item.totem.use")
                .packString("PLAYERS")
                .toByteArray());
        assertEquals(S2CPacketDecoders.ActivationResourceType.TEXTURE, effect.resourceType());
        assertEquals(2, effect.resourceTypeOrdinal());
        assertEquals("unknown-effect", effect.field01());
        assertEquals("minecraft:item.totem.use", effect.soundId());
        assertEquals("PLAYERS", effect.soundSource());

        S2CPacketDecoders.FlightLeanDirectionPacket id105 = S2CPacketDecoders.decodeId105(
            new HeyPixelMsgpackWriter()
                .packValue(player.toString()).packInt(0).packInt(3)
                .toByteArray());
        assertEquals(player, id105.playerUuid());
        assertTrue(id105.isFlightLean());
        assertEquals(S2CPacketDecoders.FlightLeanDirection.UP, id105.leanDirection());

        S2CPacketDecoders.FlightLeanDirectionPacket unknownDirection =
            new S2CPacketDecoders.FlightLeanDirectionPacket(player, 1, 9);
        assertFalse(unknownDirection.isFlightLean());
        assertEquals(S2CPacketDecoders.FlightLeanDirection.UNKNOWN,
            unknownDirection.leanDirection());

        assertEquals(0, S2CPacketDecoders.decodeId107(new byte[0]).ignoredPayloadBytes());
        assertEquals(1, S2CPacketDecoders.decodeId107(new byte[]{1}).ignoredPayloadBytes());

        HeyPixelProtocolState state = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher.DispatchResult nonEmpty =
            new HeyPixelProtocolDispatcher(state).dispatch(107, new byte[]{1, 2, 3});
        assertEquals(3,
            ((S2CPacketDecoders.ShowGameStorePopupRequest) nonEmpty.value()).ignoredPayloadBytes());
        assertEquals(3, state.lastGameStorePopupRequest().orElseThrow().ignoredPayloadBytes());
    }

    @Test
    @SuppressWarnings("deprecation")
    void decodesEveryOfficialActivationResourceWireIdAndKeepsTheLegacyAdapter() {
        S2CPacketDecoders.ActivationResourceType[] expected = {
            S2CPacketDecoders.ActivationResourceType.ITEM,
            S2CPacketDecoders.ActivationResourceType.BLOCK,
            S2CPacketDecoders.ActivationResourceType.TEXTURE,
            S2CPacketDecoders.ActivationResourceType.TEXT,
            S2CPacketDecoders.ActivationResourceType.GeoModel
        };

        for (int wireId = 0; wireId < expected.length; wireId++) {
            byte[] payload = activationPayload(wireId);
            S2CPacketDecoders.ActivationEffectPacket packet =
                S2CPacketDecoders.decodeActivationEffect(payload);
            assertEquals(expected[wireId], packet.resourceType());
            assertEquals(wireId, packet.resourceTypeOrdinal());
            assertEquals(wireId, packet.resourceType().wireId());

            S2CPacketDecoders.Id104SoundEffect legacy =
                S2CPacketDecoders.decodeId104(payload);
            assertEquals((byte) wireId, legacy.field00());
            assertEquals(packet, legacy.toActivationEffect());
        }

        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeActivationEffect(activationPayload(-1)));
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeActivationEffect(activationPayload(5)));

        byte[] base = activationPayload(0);
        byte[] trailing = Arrays.copyOf(base, base.length + 1);
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeActivationEffect(trailing));
    }

    @Test
    void activationAndSyncTokenDispatchExposeTypedValuesWithoutPollutingStateOnFailure() {
        HeyPixelProtocolState state = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);

        HeyPixelProtocolDispatcher.CanonicalDispatchResult activation =
            dispatcher.dispatchCanonical(104, activationPayload(4));
        assertEquals(
            HeyPixelProtocolDispatcher.CanonicalKind.PLAY_ACTIVATION_EFFECT,
            activation.kind()
        );
        assertTrue(activation.value() instanceof S2CPacketDecoders.ActivationEffectPacket);
        S2CPacketDecoders.ActivationEffectPacket effect =
            (S2CPacketDecoders.ActivationEffectPacket) activation.value();
        assertEquals(S2CPacketDecoders.ActivationResourceType.GeoModel, effect.resourceType());
        assertEquals(effect, state.lastActivationEffect().orElseThrow());

        assertThrows(IllegalArgumentException.class,
            () -> dispatcher.dispatchCanonical(104, activationPayload(5)));
        assertEquals(effect, state.lastActivationEffect().orElseThrow());

        byte[] syncPayload = new HeyPixelMsgpackWriter().packString("synthetic-sync").toByteArray();
        assertEquals(
            S2CPacketDecoders.decodeId114(syncPayload),
            S2CPacketDecoders.decodeSyncToken(syncPayload)
        );
        HeyPixelProtocolDispatcher.CanonicalDispatchResult sync =
            dispatcher.dispatchCanonical(114, syncPayload);
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.SYNC_TOKEN, sync.kind());
        assertTrue(sync.value() instanceof SyncTokenMetadata);
        assertEquals(sync.value(), state.syncTokenMetadata().orElseThrow());

        byte[] syncWithTrailing = new HeyPixelMsgpackWriter()
            .packString("synthetic-sync")
            .packInt(7)
            .toByteArray();
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeId114(syncWithTrailing));
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeSyncToken(syncWithTrailing));
        assertEquals(sync.value(),
            S2CPacketDecoders.decodeSyncTokenOfficialPrefix(syncWithTrailing));
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.SYNC_TOKEN,
            dispatcher.dispatchCanonical(114, syncWithTrailing).kind());
        assertThrows(IllegalArgumentException.class,
            () -> dispatcher.dispatch(114, syncWithTrailing));

        state.reset();
        assertTrue(state.lastActivationEffect().isEmpty());
        assertTrue(state.syncTokenMetadata().isEmpty());
    }

    @Test
    void canonicalDecodeDoesNotMutateStateUntilClientWorkAppliesIt() {
        HeyPixelProtocolState state = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);

        HeyPixelProtocolDispatcher.CanonicalDispatchResult decoded =
            dispatcher.decodeCanonical(104, activationPayload(0));
        assertTrue(state.lastActivationEffect().isEmpty());

        HeyPixelProtocolDispatcher.CanonicalDispatchResult applied =
            dispatcher.applyCanonical(decoded);
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.PLAY_ACTIVATION_EFFECT,
            applied.kind());
        assertEquals(applied.value(), state.lastActivationEffect().orElseThrow());
    }

    @Test
    void appliesRecoveredId110DefaultsAndConditionalFields() {
        S2CPacketDecoders.OpenPanelPacket minimal = S2CPacketDecoders.decodeId110(
            id110Prefix(1).toByteArray());
        assertEquals("", minimal.content());
        assertFalse(minimal.scaleToFit());
        assertEquals(1.0f, minimal.scaleMultiplier());

        S2CPacketDecoders.OpenPanelPacket optionalString = S2CPacketDecoders.decodeId110(
            id110Prefix(1).packString("state").toByteArray());
        assertEquals("state", optionalString.content());
        assertFalse(optionalString.scaleToFit());

        S2CPacketDecoders.OpenPanelPacket full = S2CPacketDecoders.decodeId110(
            id110Prefix(0)
                .packString("state")
                .packBoolean(true)
                .packFloat(0.5f)
                .toByteArray());
        assertEquals(0, full.panelMode());
        assertEquals(2, full.panelId());
        assertEquals(3, full.designWidth());
        assertEquals(4, full.designHeight());
        assertEquals("state", full.content());
        assertTrue(full.scaleToFit());
        assertEquals(0.5f, full.scaleMultiplier());

        S2CPacketDecoders.OpenPanelPacket disabled = S2CPacketDecoders.decodeId110(
            id110Prefix(0).packString("").packBoolean(false).toByteArray());
        assertFalse(disabled.scaleToFit());
        assertEquals(1.0f, disabled.scaleMultiplier());

        S2CPacketDecoders.OpenPanelPacket modeZeroWithoutOptionals =
            S2CPacketDecoders.decodeId110(id110Prefix(0).toByteArray());
        assertEquals("", modeZeroWithoutOptionals.content());
        assertFalse(modeZeroWithoutOptionals.scaleToFit());
        assertEquals(1.0f, modeZeroWithoutOptionals.scaleMultiplier());

        S2CPacketDecoders.OpenPanelPacket scaleWithoutMultiplier = S2CPacketDecoders.decodeId110(
            id110Prefix(0).packString("state").packBoolean(true).toByteArray());
        assertTrue(scaleWithoutMultiplier.scaleToFit());
        assertEquals(1.0f, scaleWithoutMultiplier.scaleMultiplier());

        byte[] invalidConditional = id110Prefix(1)
            .packString("state").packBoolean(true).toByteArray();
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeId110(invalidConditional));

        byte[] invalidDisabledTail = id110Prefix(0)
            .packString("state").packBoolean(false).packFloat(0.5f).toByteArray();
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeId110(invalidDisabledTail));
    }

    @Test
    void preservesTheDifferentId117AndId120JsonFramingRules() throws Exception {
        String json = "{\"category\":\"hat\"}";
        S2CPacketDecoders.OpenFashionGuiPacket fashionGui =
            S2CPacketDecoders.decodeOpenFashionGui(
            json.getBytes(StandardCharsets.UTF_8));
        assertFalse(fashionGui.empty());
        assertEquals("hat", fashionGui.category().orElseThrow());

        S2CPacketDecoders.OpenFashionGuiPacket emptyFashionGui =
            S2CPacketDecoders.decodeOpenFashionGui(new byte[0]);
        assertTrue(emptyFashionGui.empty());
        assertNull(emptyFashionGui.state());

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(json.getBytes(StandardCharsets.UTF_8));
        }
        assertThrows(RuntimeException.class,
            () -> S2CPacketDecoders.decodeOpenFashionGui(compressed.toByteArray()));
        String actionJson = "{\"action\":\"equip\",\"success\":true,\"message\":\"ok\"}";
        compressed.reset();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(actionJson.getBytes(StandardCharsets.UTF_8));
        }
        S2CPacketDecoders.FashionActionResultPacket compressedActionResult =
            S2CPacketDecoders.decodeFashionActionResult(compressed.toByteArray());
        assertTrue(compressedActionResult.zlibCompressed());
        assertEquals("equip", compressedActionResult.action().orElseThrow());
        assertTrue(compressedActionResult.success().orElseThrow());
        assertEquals("ok", compressedActionResult.message().orElseThrow());

        S2CPacketDecoders.FashionActionResultPacket plainActionResult =
            S2CPacketDecoders.decodeFashionActionResult(actionJson.getBytes(StandardCharsets.UTF_8));
        assertFalse(plainActionResult.zlibCompressed());
        assertEquals(actionJson, plainActionResult.json());
        JsonObject returned = plainActionResult.state();
        returned.addProperty("mutated", true);
        assertFalse(plainActionResult.state().has("mutated"));
    }

    @Test
    void followsInterleavedPanelBatchGuardsAndKeepsId112StrictnessLocal() {
        HeyPixelMsgpackWriter records = new HeyPixelMsgpackWriter();
        records.packArrayHeader(2);
        writePanelRecord(records, "first", 1);
        records.packArrayHeader(2);
        writePanelRecord(records, "second", 2);
        records.packArrayHeader(2);

        S2CPacketDecoders.S2CHudInfoBatchPacket hudBatch =
            S2CPacketDecoders.decodeHudInfoBatch(records.toByteArray());
        assertEquals(2, hudBatch.entries().size());
        assertEquals("first", hudBatch.entries().get(0).hudId());
        S2CPacketDecoders.HudInfo first = hudBatch.entries().get(0).hudInfo();
        assertEquals(1, first.width());
        assertEquals(11, first.height());
        assertEquals("a1", first.hudPath());
        assertEquals("b1", first.offsetX());
        assertEquals("c1", first.offsetY());
        assertEquals("d1", first.key());
        assertEquals("d2", hudBatch.hudInfosById().get("second").key());

        var decodedRecords = S2CPacketDecoders.decodeId111(records.toByteArray());
        assertEquals("first", decodedRecords.get(0).key());
        assertEquals("d1", decodedRecords.get(0).field05());

        byte[] conventionalSingleHeader = new HeyPixelMsgpackWriter()
            .packArrayHeader(1)
            .packString("key").packInt(1).packInt(2)
            .packString("a").packString("b").packString("c").packString("d")
            .toByteArray();
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeId111(conventionalSingleHeader));

        S2CPacketDecoders.PanelModelOperationPacket models = S2CPacketDecoders.decodeId112(
            new HeyPixelMsgpackWriter()
                .packInt(0)
                .packArrayHeader(2).packString("{\"key\":\"first\"}")
                .packArrayHeader(2).packString("{\"key\":\"second\"}")
                .packArrayHeader(2)
                .toByteArray());
        assertEquals(S2CPacketDecoders.PanelModelOperation.UPDATE, models.operation());
        assertEquals(2, models.jsonEntries().size());

        S2CPacketDecoders.PanelModelOperationPacket emptyRemove = S2CPacketDecoders.decodeId112(
            new HeyPixelMsgpackWriter().packInt(1).packArrayHeader(0).toByteArray());
        assertEquals(S2CPacketDecoders.PanelModelOperation.REMOVE, emptyRemove.operation());
        assertTrue(emptyRemove.jsonEntries().isEmpty());

        String rawModelJson = " { \"key\" : 1 } ";
        S2CPacketDecoders.PanelModelOperationPacket replace = S2CPacketDecoders.decodeId112(
            new HeyPixelMsgpackWriter()
                .packInt(2)
                .packArrayHeader(1).packString(rawModelJson)
                .packArrayHeader(1)
                .toByteArray());
        assertEquals(S2CPacketDecoders.PanelModelOperation.REPLACE, replace.operation());
        assertEquals(rawModelJson, replace.jsonEntries().get(0));
        assertThrows(UnsupportedOperationException.class, () -> replace.jsonEntries().clear());

        byte[] outOfDomainOperation = new HeyPixelMsgpackWriter()
            .packInt(99).packArrayHeader(0).toByteArray();
        IllegalArgumentException localOrdinalPolicy = assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeId112(outOfDomainOperation));
        assertTrue(localOrdinalPolicy.getMessage().contains("local expected domain 0..2"));

        @SuppressWarnings("deprecation")
        S2CPacketDecoders.PanelModelOperation localUnknown =
            new S2CPacketDecoders.PanelModelOperationPacket(99, java.util.List.of())
                .operationOrUnknown();
        assertEquals(S2CPacketDecoders.PanelModelOperation.UNKNOWN, localUnknown);

        byte[] trailingAfterTerminalGuard = new HeyPixelMsgpackWriter()
            .packInt(0).packArrayHeader(0).packString("local-trailing-policy").toByteArray();
        IllegalArgumentException localTrailingPolicy = assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeId112(trailingAfterTerminalGuard));
        assertTrue(localTrailingPolicy.getMessage().contains("unread bytes"));
        assertEquals(S2CPacketDecoders.PanelModelOperation.UPDATE,
            S2CPacketDecoders.decodeId112OfficialPrefix(trailingAfterTerminalGuard).operation());
        HeyPixelProtocolDispatcher canonicalDispatcher =
            new HeyPixelProtocolDispatcher(new HeyPixelProtocolState());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.PANEL_MODEL_OPERATION,
            canonicalDispatcher.dispatchCanonical(112, trailingAfterTerminalGuard).kind());
        assertThrows(IllegalArgumentException.class,
            () -> canonicalDispatcher.dispatch(112, trailingAfterTerminalGuard));
    }

    @Test
    void decodesAndDispatchesCanonicalResourceAndResourceIndexMetadata() {
        byte[] resourcePayload = new HeyPixelMsgpackWriter()
            .packString("hud/icon.png")
            .packString("sha1")
            .packInt(0)
            .packInt(1)
            .packValue(new byte[]{1, 2})
            .toByteArray();
        S2CPacketDecoders.ResourceBlobFragment resource =
            S2CPacketDecoders.decodeResourceBlobFragment(resourcePayload);
        assertEquals("hud/icon.png", resource.resourceName());
        assertEquals("sha1", resource.hash());
        assertEquals("hud/icon.png", resource.field00());

        byte[] indexPayload = new HeyPixelMsgpackWriter()
            .packString("index-transfer")
            .packString("cache-hash")
            .packInt(0)
            .packInt(0)
            .packInt(1)
            .packValue(new byte[]{(byte) 0xff})
            .toByteArray();
        S2CPacketDecoders.ResourceIndexFragment index =
            S2CPacketDecoders.decodeResourceIndexFragment(indexPayload);
        assertEquals("index-transfer", index.indexName());
        assertEquals("cache-hash", index.cacheHash());
        assertEquals(0, index.mode());
        assertEquals("cache-hash",
            S2CPacketDecoders.decodeChunkedDataFragment(indexPayload).field01());

        HeyPixelProtocolDispatcher dispatcher =
            new HeyPixelProtocolDispatcher(new HeyPixelProtocolState());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.RESOURCE_BLOB_COMPLETE,
            dispatcher.dispatchCanonical(108, resourcePayload).kind());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.RESOURCE_INDEX_COMPLETE,
            dispatcher.dispatchCanonical(109, indexPayload).kind());
    }

    @Test
    void rejectsOversizedId111DeclarationsAndInflatedJsonBeforeAllocationGrowth() throws Exception {
        byte[] oversizedId111 = new HeyPixelMsgpackWriter()
            .packArrayHeader(Integer.MAX_VALUE)
            .toByteArray();
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeId111(oversizedId111));

        byte[] inflated = new byte[S2CPacketDecoders.MAX_JSON_BYTES + 1];
        Arrays.fill(inflated, (byte) 'a');
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(inflated);
        }
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeJsonPacket(113, compressed.toByteArray()));
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeOpenFashionGui(inflated));
    }

    @Test
    void decodesZlibId116Id118AndId119WithOfficialAccessorsAndDefensiveCopies()
        throws Exception {
        String unlockJson = """
            {
              "type":"infos",
              "exchange":[{"id":"exchange-a"}],
              "exchange_raw":["raw-a"],
              "unlocked":{"fashion-a":true}
            }
            """;
        S2CPacketDecoders.UnlockExchangeStatePacket unlock =
            S2CPacketDecoders.decodeUnlockExchangeState(zlib(unlockJson));
        assertTrue(unlock.zlibCompressed());
        assertEquals("infos", unlock.type().orElseThrow());
        assertEquals("exchange-a",
            unlock.exchange().orElseThrow().get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("raw-a", unlock.exchangeRaw().orElseThrow().get(0).getAsString());
        assertTrue(unlock.unlocked().orElseThrow().get("fashion-a").getAsBoolean());

        JsonObject returnedUnlockState = unlock.state();
        returnedUnlockState.addProperty("mutated", true);
        unlock.exchange().orElseThrow().add("mutated");
        unlock.unlocked().orElseThrow().addProperty("mutated", true);
        assertFalse(unlock.state().has("mutated"));
        assertEquals(1, unlock.exchange().orElseThrow().size());
        assertFalse(unlock.unlocked().orElseThrow().has("mutated"));

        String configJson = """
            {
              "type":"config",
              "configs":{"fashion-a":{"display":"Fashion A"}},
              "enabled":{"fashion-a":true},
              "enabled_fashions":["fashion-a"],
              "rarities":{"rare":{"display":"Rare"}}
            }
            """;
        S2CPacketDecoders.FashionConfigPacket config =
            S2CPacketDecoders.decodeFashionConfig(zlib(configJson));
        assertTrue(config.zlibCompressed());
        assertEquals("config", config.type().orElseThrow());
        assertEquals("Fashion A", config.configs().orElseThrow()
            .getAsJsonObject("fashion-a").get("display").getAsString());
        assertTrue(config.enabled().orElseThrow().get("fashion-a").getAsBoolean());
        assertEquals("fashion-a", config.enabledFashions().orElseThrow().get(0).getAsString());
        assertEquals("Rare", config.rarities().orElseThrow()
            .getAsJsonObject("rare").get("display").getAsString());
        assertEquals("enabled", config.enabledSummaryField().orElseThrow().key());
        assertEquals(S2CPacketDecoders.JsonFieldPresence.VALUE,
            config.enabledSummaryField().orElseThrow().presence());
        assertTrue(config.configsField().value().isJsonObject());
        assertTrue(config.raritiesField().value().isJsonObject());

        config.state().addProperty("mutated", true);
        config.configs().orElseThrow().remove("fashion-a");
        config.enabledFashions().orElseThrow().add("mutated");
        assertFalse(config.state().has("mutated"));
        assertTrue(config.configs().orElseThrow().has("fashion-a"));
        assertEquals(1, config.enabledFashions().orElseThrow().size());

        String playersJson = """
            {
              "type":"players",
              "players":{"player-a":{"name":"Player A","equipped":true,"fashion":"fashion-a"}}
            }
            """;
        S2CPacketDecoders.PlayerFashionStatePacket players =
            S2CPacketDecoders.decodePlayerFashionState(zlib(playersJson));
        assertTrue(players.zlibCompressed());
        assertEquals("players", players.type().orElseThrow());
        assertEquals("Player A", players.players().orElseThrow()
            .getAsJsonObject("player-a").get("name").getAsString());
        assertFalse(players.hasDirectPatch());
        assertTrue(players.directPatch().isEmpty());
        assertTrue(players.selfPatch().isEmpty());
        assertEquals("Player A",
            players.playerPatches().get("player-a").name().orElseThrow());

        players.state().addProperty("mutated", true);
        players.players().orElseThrow().remove("player-a");
        assertFalse(players.state().has("mutated"));
        assertTrue(players.players().orElseThrow().has("player-a"));
    }

    @Test
    void preservesId118AliasesAndId119OfficialPatchRoutingWithoutNormalizingNestedShapes()
        throws Exception {
        S2CPacketDecoders.FashionConfigPacket config =
            S2CPacketDecoders.decodeFashionConfig(zlib("""
                {
                  "type":"config",
                  "configs":7,
                  "enabled":null,
                  "enabled_fashions":{"fashion-a":true},
                  "enabledFashions":["compat-a"],
                  "rarities":[]
                }
                """));

        S2CPacketDecoders.JsonField enabled = config.enabledSummaryField().orElseThrow();
        assertEquals("enabled", enabled.key());
        assertEquals(S2CPacketDecoders.JsonFieldPresence.JSON_NULL, enabled.presence());
        assertNull(enabled.value());
        assertEquals(7, config.configsField().value().getAsInt());
        assertTrue(config.raritiesField().value().isJsonArray());
        assertTrue(config.configs().isEmpty());
        assertTrue(config.enabled().isEmpty());
        assertTrue(config.enabledFashions().isEmpty());
        assertTrue(config.rarities().isEmpty());

        S2CPacketDecoders.FashionConfigPacket camelAlias =
            S2CPacketDecoders.decodeFashionConfig(zlib("{\"enabledFashions\":true}"));
        assertEquals("enabledFashions", camelAlias.enabledSummaryField().orElseThrow().key());
        assertTrue(camelAlias.enabledSummaryField().orElseThrow().value().getAsBoolean());
        assertEquals(S2CPacketDecoders.JsonFieldPresence.ABSENT,
            camelAlias.configsField().presence());

        S2CPacketDecoders.FashionConfigPacket aliasPrecedence =
            S2CPacketDecoders.decodeFashionConfig(zlib("""
                {
                  "enabled_fashions":{"snake":true},
                  "enabledFashions":{"camel":true}
                }
                """));
        S2CPacketDecoders.JsonField preferredAlias =
            aliasPrecedence.enabledSummaryField().orElseThrow();
        assertEquals("enabled_fashions", preferredAlias.key());
        assertTrue(preferredAlias.value().getAsJsonObject().get("snake").getAsBoolean());

        S2CPacketDecoders.PlayerFashionStatePacket players =
            S2CPacketDecoders.decodePlayerFashionState(zlib("""
                {
                  "type":"players",
                  "uuid":123,
                  "name":true,
                  "id":[1],
                  "fashion":"direct-fashion",
                  "equipped":{"slot":"hat"},
                  "unlocked":null,
                  "self":{"uuid":"-10","name":"Self","fashion":["self-fashion"]},
                  "players":{
                    "player-a":{"name":"Player A","id":7,"equipped":false,
                      "unlocked":["fashion-a"]},
                    "ignored":"not-an-object"
                  }
                }
                """));

        assertTrue(players.hasDirectPatch());
        S2CPacketDecoders.PlayerFashionPatchView direct = players.directPatch().orElseThrow();
        assertEquals("123", direct.uuidText().orElseThrow());
        assertEquals("true", direct.name().orElseThrow());
        assertTrue(direct.id().value().isJsonArray());
        assertEquals("direct-fashion", direct.fashion().value().getAsString());
        assertEquals("hat", direct.equipped().value().getAsJsonObject().get("slot").getAsString());
        assertEquals(S2CPacketDecoders.JsonFieldPresence.JSON_NULL, direct.unlocked().presence());

        S2CPacketDecoders.PlayerFashionPatchView self = players.selfPatch().orElseThrow();
        assertEquals("-10", self.uuidText().orElseThrow());
        assertTrue(self.fashion().value().isJsonArray());
        assertEquals(S2CPacketDecoders.JsonFieldPresence.ABSENT, self.equipped().presence());

        assertEquals(1, players.playerPatches().size());
        S2CPacketDecoders.PlayerFashionPatchView player =
            players.playerPatches().get("player-a");
        assertEquals("Player A", player.name().orElseThrow());
        assertEquals(7, player.id().value().getAsInt());
        assertFalse(player.equipped().value().getAsBoolean());
        assertTrue(player.unlocked().value().isJsonArray());
        assertThrows(UnsupportedOperationException.class,
            () -> players.playerPatches().clear());

        JsonObject returnedRaw = direct.raw();
        returnedRaw.getAsJsonObject("equipped").addProperty("mutated", true);
        assertFalse(players.directPatch().orElseThrow().raw()
            .getAsJsonObject("equipped").has("mutated"));
        JsonObject returnedField = direct.equipped().value().getAsJsonObject();
        returnedField.addProperty("mutated", true);
        assertFalse(direct.equipped().value().getAsJsonObject().has("mutated"));

        S2CPacketDecoders.PlayerFashionStatePacket fashionOnly =
            S2CPacketDecoders.decodePlayerFashionState(zlib("{\"fashion\":null}"));
        assertTrue(fashionOnly.hasDirectPatch());
        S2CPacketDecoders.PlayerFashionPatchView fashionOnlyPatch =
            fashionOnly.directPatch().orElseThrow();
        assertEquals(S2CPacketDecoders.JsonFieldPresence.JSON_NULL,
            fashionOnlyPatch.fashion().presence());
        assertEquals(S2CPacketDecoders.JsonFieldPresence.ABSENT,
            fashionOnlyPatch.equipped().presence());

        S2CPacketDecoders.PlayerFashionStatePacket equippedOnly =
            S2CPacketDecoders.decodePlayerFashionState(zlib("{\"equipped\":false}"));
        assertTrue(equippedOnly.hasDirectPatch());
        S2CPacketDecoders.PlayerFashionPatchView equippedOnlyPatch =
            equippedOnly.directPatch().orElseThrow();
        assertEquals(S2CPacketDecoders.JsonFieldPresence.ABSENT,
            equippedOnlyPatch.fashion().presence());
        assertFalse(equippedOnlyPatch.equipped().value().getAsBoolean());

        assertFalse(S2CPacketDecoders.decodePlayerFashionState(zlib("{\"unlocked\":[]}"))
            .hasDirectPatch());

        assertThrows(IllegalArgumentException.class,
            () -> new S2CPacketDecoders.JsonField(
                "fashion",
                S2CPacketDecoders.JsonFieldPresence.VALUE,
                JsonNull.INSTANCE));
    }

    @Test
    void dispatchesTypedId116Id118AndId119StateWhileKeepingGenericJsonStates() throws Exception {
        HeyPixelProtocolState state = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);

        HeyPixelProtocolDispatcher.DispatchResult unlockResult = dispatcher.dispatch(
            116,
            zlib("{\"type\":\"infos\",\"exchange\":[],\"exchange_raw\":[],\"unlocked\":{}}")
        );
        assertEquals(HeyPixelProtocolDispatcher.Kind.UNLOCK_EXCHANGE_STATE, unlockResult.kind());
        assertTrue(unlockResult.value() instanceof S2CPacketDecoders.UnlockExchangeStatePacket);
        S2CPacketDecoders.UnlockExchangeStatePacket unlock =
            (S2CPacketDecoders.UnlockExchangeStatePacket) unlockResult.value();
        assertEquals("infos", unlock.type().orElseThrow());
        assertEquals(unlock.json(), state.unlockExchangeState().orElseThrow().json());
        assertEquals(unlock.json(), state.jsonStates().get(116));

        HeyPixelProtocolDispatcher.DispatchResult configResult = dispatcher.dispatch(
            118,
            zlib("{\"type\":\"config\",\"configs\":{},\"enabled\":{},"
                + "\"enabled_fashions\":[],\"rarities\":{}}")
        );
        assertEquals(HeyPixelProtocolDispatcher.Kind.FASHION_CONFIG, configResult.kind());
        assertTrue(configResult.value() instanceof S2CPacketDecoders.FashionConfigPacket);
        S2CPacketDecoders.FashionConfigPacket config =
            (S2CPacketDecoders.FashionConfigPacket) configResult.value();
        assertEquals("config", config.type().orElseThrow());
        assertEquals(config.json(), state.fashionConfig().orElseThrow().json());
        assertEquals(config.json(), state.jsonStates().get(118));

        HeyPixelProtocolDispatcher.DispatchResult playersResult = dispatcher.dispatch(
            119,
            zlib("{\"type\":\"players\",\"players\":{}}")
        );
        assertEquals(HeyPixelProtocolDispatcher.Kind.PLAYER_FASHION_STATE, playersResult.kind());
        assertTrue(playersResult.value() instanceof S2CPacketDecoders.PlayerFashionStatePacket);
        S2CPacketDecoders.PlayerFashionStatePacket players =
            (S2CPacketDecoders.PlayerFashionStatePacket) playersResult.value();
        assertEquals("players", players.type().orElseThrow());
        assertEquals(players.json(), state.playerFashionState().orElseThrow().json());
        assertEquals(players.json(), state.jsonStates().get(119));

        assertEquals(3, state.jsonStates().size());
    }

    @Test
    void decodesAndCachesTypedShopAndSelectionStateWithoutOfficialSideEffects() throws Exception {
        String shopJson = """
            {
              "type":"infos",
              "keys":"shop-key",
              "msg":"ready",
              "page":"main",
              "deposits":{"coins":3},
              "limits":{"daily":5}
            }
            """;
        S2CPacketDecoders.ShopMessagePacket shop =
            S2CPacketDecoders.decodeShopMessage(zlib(shopJson));
        assertTrue(shop.zlibCompressed());
        assertEquals(shopJson, shop.json());
        assertEquals("infos", shop.type().orElseThrow());
        assertEquals("shop-key", shop.keys().orElseThrow());
        assertEquals("ready", shop.msg().orElseThrow());
        assertEquals("main", shop.page().orElseThrow());
        assertEquals(3, shop.deposits().orElseThrow().get("coins").getAsInt());
        assertEquals(5, shop.limits().orElseThrow().get("daily").getAsInt());
        shop.deposits().orElseThrow().addProperty("mutated", true);
        assertFalse(shop.deposits().orElseThrow().has("mutated"));
        JsonObject returnedShopDocument = shop.document().getAsJsonObject();
        returnedShopDocument.addProperty("mutated", true);
        assertFalse(shop.document().getAsJsonObject().has("mutated"));

        String definitionsJson = """
            {
              "type":"definitions",
              "timestamp":123,
              "definitions":{"a":"Alpha"}
            }
            """;
        S2CPacketDecoders.SelectionDefinitionPacket definitions =
            S2CPacketDecoders.decodeSelectionDefinition(zlib(definitionsJson));
        assertTrue(definitions.zlibCompressed());
        assertEquals("definitions", definitions.type().orElseThrow());
        assertEquals(123L, definitions.timestamp().orElseThrow());
        assertEquals("Alpha", definitions.definitions().orElseThrow().get("a").getAsString());
        definitions.definitions().orElseThrow().remove("a");
        assertTrue(definitions.definitions().orElseThrow().has("a"));
        S2CPacketDecoders.SelectionDefinitionView definitionsView = definitions.officialView();
        assertEquals("definitions", definitionsView.type());
        assertEquals(123L, definitionsView.timestamp());
        assertEquals(0L, definitionsView.sessionId());
        assertEquals(java.util.Map.of("a", "Alpha"), definitionsView.definitions());
        assertEquals(java.util.List.of("", "", ""), definitionsView.hexKeys());
        assertEquals(java.util.List.of(false, false, false), definitionsView.refreshUsed());

        String selectionJson = """
            {
              "type":"selection_update",
              "timestamp":124,
              "sessionId":456,
              "hexKeys":["aa","bb","cc"],
              "refreshUsed":[true,false,true]
            }
            """;
        S2CPacketDecoders.SelectionDefinitionPacket selection =
            S2CPacketDecoders.decodeSelectionDefinition(zlib(selectionJson));
        assertEquals(selectionJson, selection.json());
        assertEquals(456L, selection.sessionId().orElseThrow());
        assertEquals(java.util.List.of("aa", "bb", "cc"), selection.hexKeys().orElseThrow());
        assertEquals(java.util.List.of(true, false, true), selection.refreshUsed().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
            () -> selection.hexKeys().orElseThrow().clear());
        S2CPacketDecoders.SelectionDefinitionView selectionView = selection.officialView();
        assertEquals("selection_update", selectionView.type());
        assertEquals(124L, selectionView.timestamp());
        assertEquals(456L, selectionView.sessionId());
        assertTrue(selectionView.definitions().isEmpty());
        assertEquals(java.util.List.of("aa", "bb", "cc"), selectionView.hexKeys());
        assertEquals(java.util.List.of(true, false, true), selectionView.refreshUsed());

        HeyPixelProtocolState state = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);
        assertEquals(HeyPixelProtocolDispatcher.Kind.SHOP_MESSAGE,
            dispatcher.dispatch(113, zlib(shopJson)).kind());
        assertEquals(HeyPixelProtocolDispatcher.Kind.SELECTION_DEFINITION,
            dispatcher.dispatch(115, zlib(selectionJson)).kind());
        assertEquals("shop-key", state.shopMessage().orElseThrow().keys().orElseThrow());
        assertEquals(456L, state.selectionDefinition().orElseThrow().sessionId().orElseThrow());
        assertEquals(shopJson, state.jsonStates().get(113));
        assertEquals(selectionJson, state.jsonStates().get(115));
        state.reset();
        assertTrue(state.shopMessage().isEmpty());
        assertTrue(state.selectionDefinition().isEmpty());
    }

    @Test
    void followsOfficialId115DefaultsRequiredFieldsAndBranchIsolation() throws Exception {
        String missingType = """
            {
              "timestamp":"8",
              "sessionId":"9",
              "hexKeys":[7],
              "refreshUsed":["true",false,true,false]
            }
            """;
        S2CPacketDecoders.SelectionDefinitionView defaultType =
            S2CPacketDecoders.decodeSelectionDefinition(zlib(missingType)).officialView();
        assertEquals("", defaultType.type());
        assertEquals(8L, defaultType.timestamp());
        assertEquals(9L, defaultType.sessionId());
        assertEquals(java.util.List.of("7", "", ""), defaultType.hexKeys());
        assertEquals(java.util.List.of(true, false, true), defaultType.refreshUsed());

        String definitionsIgnoreOtherBranch = """
            {
              "type":"definitions",
              "timestamp":10,
              "definitions":{"numeric":2},
              "sessionId":"not-read",
              "hexKeys":{},
              "refreshUsed":null
            }
            """;
        S2CPacketDecoders.SelectionDefinitionView definitions =
            S2CPacketDecoders.decodeSelectionDefinition(zlib(definitionsIgnoreOtherBranch))
                .officialView();
        assertEquals(java.util.Map.of("numeric", "2"), definitions.definitions());
        assertEquals(0L, definitions.sessionId());
        assertEquals(java.util.List.of("", "", ""), definitions.hexKeys());
        assertEquals(java.util.List.of(false, false, false), definitions.refreshUsed());

        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeSelectionDefinition(zlib(
                "{\"type\":\"definitions\",\"definitions\":{}}")));
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeSelectionDefinition(zlib(
                "{\"type\":\"definitions\",\"timestamp\":1,\"definitions\":[]}")));
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeSelectionDefinition(zlib(
                "{\"type\":\"future\",\"timestamp\":1}")));
    }

    @Test
    void separatesOfficialZlibReplacementFromLocalGenericJsonPolicy() throws Exception {
        String arrayJson = " [1, {\"key\":true}] ";
        S2CPacketDecoders.JsonPayload localShop = S2CPacketDecoders.decodeJsonPacket(
            113, arrayJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(arrayJson, localShop.json());
        assertFalse(localShop.zlibCompressed());
        assertTrue(JsonParser.parseString(localShop.json()).isJsonArray());

        String nullJson = "null";
        S2CPacketDecoders.JsonPayload localSelection = S2CPacketDecoders.decodeJsonPacket(
            115, nullJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(nullJson, localSelection.json());
        assertFalse(localSelection.zlibCompressed());
        assertTrue(JsonParser.parseString(localSelection.json()).isJsonNull());

        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeShopMessage(
                "{\"type\":\"infos\"}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeSelectionDefinition(
                "{\"type\":\"definitions\",\"timestamp\":1,\"definitions\":{}}"
                    .getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeShopMessage(zlib(arrayJson)));

        byte[] replacementJson = jsonWithMalformedUtf8(
            "{\"type\":\"buy\",\"msg\":\"", "\"}");
        S2CPacketDecoders.ShopMessagePacket replacement =
            S2CPacketDecoders.decodeShopMessage(zlib(replacementJson));
        assertEquals("\ufffd(", replacement.msg().orElseThrow());

        byte[] malformedUtf8 = {(byte) 0xc3, 0x28};
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeJsonPacket(113, malformedUtf8));
        assertThrows(IllegalArgumentException.class,
            () -> S2CPacketDecoders.decodeOpenFashionGui(malformedUtf8));
    }

    @Test
    void selectionLongAccessorsRejectFractionsAndOverflowWithoutTruncation() {
        S2CPacketDecoders.SelectionDefinitionPacket fraction = localSelectionPacket(
            "{\"timestamp\":1.5}");
        assertFalse(fraction.timestamp().isPresent());

        S2CPacketDecoders.SelectionDefinitionPacket overflow = localSelectionPacket(
            "{\"sessionId\":9223372036854775808}");
        assertFalse(overflow.sessionId().isPresent());

        S2CPacketDecoders.SelectionDefinitionPacket exactExponent = localSelectionPacket(
            "{\"timestamp\":1e3,\"sessionId\":9223372036854775807}");
        assertEquals(1_000L, exactExponent.timestamp().orElseThrow());
        assertEquals(Long.MAX_VALUE, exactExponent.sessionId().orElseThrow());
    }

    @Test
    void preservesId121RawJsonAndDefensiveTypedBoardInfo() throws Exception {
        String boardJson = """
            {
              "boardId":"board-1",
              "tabs":[{"id":"tab-1"}],
              "open":true
            }
            """;
        S2CPacketDecoders.BoardInfoPacket board =
            S2CPacketDecoders.decodeBoardInfo(zlib(boardJson));
        assertTrue(board.zlibCompressed());
        assertEquals(boardJson, board.json());
        assertEquals("board-1", board.boardId().orElseThrow());
        assertEquals(1, board.tabs().orElseThrow().size());
        assertTrue(board.open().orElseThrow());
        assertEquals("board-1", board.officialBoardId());
        assertEquals(1, board.officialTabs().size());
        assertTrue(board.officialOpen());

        JsonObject returnedState = board.state();
        returnedState.addProperty("mutated", true);
        assertFalse(board.state().has("mutated"));
        var returnedTabs = board.tabs().orElseThrow();
        returnedTabs.add("mutated");
        assertEquals(1, board.tabs().orElseThrow().size());

        String wrongTypes = "{\"boardId\":1,\"tabs\":{},\"open\":\"true\"}";
        S2CPacketDecoders.BoardInfoPacket conservative = S2CPacketDecoders.decodeBoardInfo(
            wrongTypes.getBytes(StandardCharsets.UTF_8));
        assertEquals(wrongTypes, conservative.json());
        assertTrue(conservative.boardId().isEmpty());
        assertTrue(conservative.tabs().isEmpty());
        assertTrue(conservative.open().isEmpty());
        assertEquals("1", conservative.officialBoardId());
        assertTrue(conservative.officialTabs().isEmpty());
        assertTrue(conservative.officialOpen());

        S2CPacketDecoders.BoardInfoPacket defaults = S2CPacketDecoders.decodeBoardInfo(
            "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("", defaults.officialBoardId());
        assertTrue(defaults.officialTabs().isEmpty());
        assertFalse(defaults.officialOpen());

        byte[] replacementJson = jsonWithMalformedUtf8(
            "{\"boardId\":\"", "\",\"tabs\":[],\"open\":false}");
        S2CPacketDecoders.BoardInfoPacket replacement =
            S2CPacketDecoders.decodeBoardInfo(replacementJson);
        assertFalse(replacement.zlibCompressed());
        assertEquals("\ufffd(", replacement.officialBoardId());
    }

    @Test
    @SuppressWarnings("deprecation")
    void keepsLegacyId120AndId121ApisAsRealAdapters() throws Exception {
        String actionJson = "{\"action\":\"equip\",\"success\":true}";
        S2CPacketDecoders.ActionResultPacket legacyAction =
            S2CPacketDecoders.decodeActionResult(actionJson.getBytes(StandardCharsets.UTF_8));
        assertEquals("equip", legacyAction.state().get("action").getAsString());

        String boardJson = "{\"boardId\":\"board\",\"tabs\":[],\"open\":false}";
        S2CPacketDecoders.NoticeCenterSync legacyBoard =
            S2CPacketDecoders.decodeNoticeCenterSync(zlib(boardJson));
        HeyPixelProtocolState state = new HeyPixelProtocolState();
        state.setNoticeCenterState(legacyBoard);
        assertEquals("board", state.noticeCenterState().orElseThrow()
            .get("boardId").getAsString());
        assertTrue(state.noticeCenterCompressed());

        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);
        HeyPixelProtocolDispatcher.DispatchResult activation =
            dispatcher.dispatch(104, activationPayload(0));
        assertEquals(HeyPixelProtocolDispatcher.Kind.PLAY_SOUND_EFFECT, activation.kind());
        assertTrue(activation.value() instanceof S2CPacketDecoders.Id104SoundEffect);

        HeyPixelProtocolDispatcher.DispatchResult action = dispatcher.dispatch(
            120, actionJson.getBytes(StandardCharsets.UTF_8));
        assertEquals(HeyPixelProtocolDispatcher.Kind.ACTION_RESULT,
            action.kind());
        assertTrue(action.value() instanceof S2CPacketDecoders.ActionResultPacket);

        HeyPixelProtocolDispatcher.DispatchResult board =
            dispatcher.dispatch(121, zlib(boardJson));
        assertEquals(HeyPixelProtocolDispatcher.Kind.NOTICE_CENTER,
            board.kind());
        assertTrue(board.value() instanceof S2CPacketDecoders.NoticeCenterSync);

        HeyPixelMsgpackWriter hudPayload = new HeyPixelMsgpackWriter();
        hudPayload.packArrayHeader(1);
        writePanelRecord(hudPayload, "legacy-hud", 4);
        hudPayload.packArrayHeader(1);
        HeyPixelProtocolDispatcher.DispatchResult hud =
            dispatcher.dispatch(111, hudPayload.toByteArray());
        assertEquals(HeyPixelProtocolDispatcher.Kind.PANEL_RECORD_BATCH, hud.kind());
        assertTrue(hud.value() instanceof java.util.List<?>);
        assertTrue(((java.util.List<?>) hud.value()).get(0)
            instanceof S2CPacketDecoders.PanelRecord);

        byte[] indexPayload = new HeyPixelMsgpackWriter()
            .packString("legacy-index")
            .packString("cache")
            .packInt(0)
            .packInt(0)
            .packInt(1)
            .packValue(new byte[]{1})
            .toByteArray();
        HeyPixelProtocolDispatcher.DispatchResult index =
            dispatcher.dispatch(109, indexPayload);
        assertEquals(HeyPixelProtocolDispatcher.Kind.CHUNKED_DATA_COMPLETE, index.kind());
        assertTrue(index.value() instanceof HeyPixelChunkAssembler.CompletedChunkedData);

        HeyPixelProtocolDispatcher.DispatchResult shop = dispatcher.dispatch(
            113, zlib("{\"type\":\"infos\"}"));
        HeyPixelProtocolDispatcher.DispatchResult selection = dispatcher.dispatch(
            115, zlib("{\"type\":\"definitions\",\"timestamp\":0,\"definitions\":{}}"));
        assertTrue(shop.value() instanceof S2CPacketDecoders.JsonPayload);
        assertTrue(selection.value() instanceof S2CPacketDecoders.JsonPayload);

        assertArrayEquals(new HeyPixelProtocolDispatcher.Kind[]{
            HeyPixelProtocolDispatcher.Kind.ID100_STATE,
            HeyPixelProtocolDispatcher.Kind.ENVIRONMENT_CHALLENGE,
            HeyPixelProtocolDispatcher.Kind.CPS_TELEMETRY_STATE,
            HeyPixelProtocolDispatcher.Kind.PLAY_SOUND_EFFECT,
            HeyPixelProtocolDispatcher.Kind.FLIGHT_LEAN_DIRECTION,
            HeyPixelProtocolDispatcher.Kind.SHOW_GAME_STORE_POPUP,
            HeyPixelProtocolDispatcher.Kind.RESOURCE_BLOB_FRAGMENT,
            HeyPixelProtocolDispatcher.Kind.RESOURCE_BLOB_COMPLETE,
            HeyPixelProtocolDispatcher.Kind.CHUNKED_DATA_FRAGMENT,
            HeyPixelProtocolDispatcher.Kind.CHUNKED_DATA_COMPLETE,
            HeyPixelProtocolDispatcher.Kind.OPEN_PANEL,
            HeyPixelProtocolDispatcher.Kind.PANEL_RECORD_BATCH,
            HeyPixelProtocolDispatcher.Kind.PANEL_MODEL_OPERATION,
            HeyPixelProtocolDispatcher.Kind.SYNC_TOKEN,
            HeyPixelProtocolDispatcher.Kind.SHOP_MESSAGE,
            HeyPixelProtocolDispatcher.Kind.SELECTION_DEFINITION,
            HeyPixelProtocolDispatcher.Kind.UNLOCK_EXCHANGE_STATE,
            HeyPixelProtocolDispatcher.Kind.FASHION_CONFIG,
            HeyPixelProtocolDispatcher.Kind.PLAYER_FASHION_STATE,
            HeyPixelProtocolDispatcher.Kind.OPEN_FASHION_GUI,
            HeyPixelProtocolDispatcher.Kind.ACTION_RESULT,
            HeyPixelProtocolDispatcher.Kind.NOTICE_CENTER,
            HeyPixelProtocolDispatcher.Kind.UNIMPLEMENTED
        }, HeyPixelProtocolDispatcher.Kind.values());
    }

    @Test
    void unimplementedDispatchPayloadIsDefensivelyCopiedInBothDirections() {
        HeyPixelProtocolDispatcher dispatcher =
            new HeyPixelProtocolDispatcher(new HeyPixelProtocolState());
        byte[] payload = {1, 2, 3};
        HeyPixelProtocolDispatcher.DispatchResult result = dispatcher.dispatch(999, payload);
        payload[0] = 9;
        byte[] firstRead = (byte[]) result.value();
        assertArrayEquals(new byte[]{1, 2, 3}, firstRead);
        firstRead[1] = 8;
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) result.value());
    }

    @Test
    void dispatchesAndCachesSafeStateWithoutCallingUnknownConsumers() {
        UUID player = new UUID(7, 8);
        HeyPixelProtocolState state = new HeyPixelProtocolState();
        HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);

        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.ID100_STATE,
            dispatcher.dispatchCanonical(100, new HeyPixelMsgpackWriter()
                .packValue(player.toString()).packLong(1).toByteArray()).kind());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.CPS_TELEMETRY_STATE,
            dispatcher.dispatchCanonical(103, writeCpsEntry(
                new HeyPixelMsgpackWriter(), player, 2, 3, 4).toByteArray()).kind());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.PLAY_ACTIVATION_EFFECT,
            dispatcher.dispatchCanonical(104, new HeyPixelMsgpackWriter()
                .packByte((byte) 0).packString("")
                .packString("minecraft:item.totem.use").packString("PLAYERS")
                .toByteArray()).kind());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.FLIGHT_LEAN_DIRECTION,
            dispatcher.dispatchCanonical(105, new HeyPixelMsgpackWriter()
                .packValue(player.toString()).packInt(0).packInt(1).toByteArray()).kind());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.SHOW_GAME_STORE_POPUP,
            dispatcher.dispatchCanonical(107, new byte[0]).kind());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.OPEN_PANEL,
            dispatcher.dispatchCanonical(110, id110Prefix(1).toByteArray()).kind());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.OPEN_FASHION_GUI,
            dispatcher.dispatchCanonical(117, "{\"category\":\"hat\"}"
                .getBytes(StandardCharsets.UTF_8)).kind());
        assertEquals(HeyPixelProtocolDispatcher.CanonicalKind.FASHION_ACTION_RESULT,
            dispatcher.dispatchCanonical(120, ("{\"action\":\"equip\",\"success\":true,"
                + "\"message\":\"ok\"}").getBytes(StandardCharsets.UTF_8)).kind());

        assertTrue(state.id100Packet().isPresent());
        assertTrue(state.cpsTelemetry().isPresent());
        assertTrue(state.lastActivationEffect().isPresent());
        assertTrue(state.flightLeanDirection().isPresent());
        assertTrue(state.lastGameStorePopupRequest().isPresent());
        assertTrue(state.openPanel().isPresent());
        assertEquals("hat", state.openFashionGui().orElseThrow().category().orElseThrow());
        assertEquals("equip",
            state.fashionActionResult().orElseThrow().action().orElseThrow());
        assertTrue(state.jsonStates().containsKey(117));
        assertTrue(state.jsonStates().containsKey(120));

        state.reset();
        assertTrue(state.openFashionGui().isEmpty());
        assertTrue(state.fashionActionResult().isEmpty());
        assertTrue(state.jsonStates().isEmpty());
    }

    private static HeyPixelMsgpackWriter writeCpsEntry(
        HeyPixelMsgpackWriter writer,
        UUID playerUuid,
        long field01,
        int leftCps,
        int rightCps
    ) {
        return writer.packValue(playerUuid.toString())
            .packLong(field01)
            .packInt(leftCps)
            .packInt(rightCps);
    }

    private static void writePanelRecord(HeyPixelMsgpackWriter writer, String hudId, int seed) {
        writer.packString(hudId)
            .packInt(seed)
            .packInt(seed + 10)
            .packString("a" + seed)
            .packString("b" + seed)
            .packString("c" + seed)
            .packString("d" + seed);
    }

    private static HeyPixelMsgpackWriter id110Prefix(int field00) {
        return new HeyPixelMsgpackWriter()
            .packInt(field00)
            .packLong(2)
            .packInt(3)
            .packInt(4);
    }

    private static byte[] activationPayload(int resourceTypeWireId) {
        return new HeyPixelMsgpackWriter()
            .packInt(resourceTypeWireId)
            .packString("resource")
            .packString("minecraft:item.totem.use")
            .packString("PLAYERS")
            .toByteArray();
    }

    private static S2CPacketDecoders.SelectionDefinitionPacket localSelectionPacket(
        String json
    ) {
        return new S2CPacketDecoders.SelectionDefinitionPacket(
            json, JsonParser.parseString(json), false);
    }

    private static byte[] jsonWithMalformedUtf8(String prefix, String suffix) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.writeBytes(prefix.getBytes(StandardCharsets.UTF_8));
        raw.write(0xc3);
        raw.write(0x28);
        raw.writeBytes(suffix.getBytes(StandardCharsets.UTF_8));
        return raw.toByteArray();
    }

    private static byte[] zlib(String json) throws Exception {
        return zlib(json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zlib(byte[] bytes) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(bytes);
        }
        return compressed.toByteArray();
    }
}
