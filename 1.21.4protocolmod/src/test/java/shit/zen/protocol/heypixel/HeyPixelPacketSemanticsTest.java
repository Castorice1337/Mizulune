package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class HeyPixelPacketSemanticsTest {
    @Test
    void coversTheVerifiedThirtyTwoEntryRegistry() {
        assertEquals(32, HeyPixelPacketSemantics.all().size());
        assertEquals(
            Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
            ids(HeyPixelPacketSemantics.Direction.C2S)
        );
        assertEquals(Set.of(108, 109), ids(HeyPixelPacketSemantics.Direction.BIDIRECTIONAL));
        assertEquals(
            Set.of(100, 101, 103, 104, 105, 107, 110, 111, 112, 113, 114, 115, 116,
                117, 118, 119, 120, 121),
            ids(HeyPixelPacketSemantics.Direction.S2C)
        );
    }

    @Test
    void givesEveryRegistryEntryAnExplicitCodecAndRuntimePolicy() {
        assertEquals(32, HeyPixelPacketSemantics.implementations().size());
        assertEquals(
            HeyPixelPacketSemantics.all().stream()
                .map(packet -> packet.direction() + ":" + packet.wireId())
                .collect(Collectors.toSet()),
            HeyPixelPacketSemantics.implementations().stream()
                .map(packet -> packet.direction() + ":" + packet.wireId())
                .collect(Collectors.toSet())
        );

        assertTrue(HeyPixelPacketSemantics.implementations().stream()
            .filter(contract -> contract.direction() == HeyPixelPacketSemantics.Direction.C2S)
            .allMatch(contract -> contract.wireSupport()
                == HeyPixelPacketSemantics.WireSupport.ENCODER));
        assertTrue(HeyPixelPacketSemantics.implementations().stream()
            .filter(contract -> contract.direction() == HeyPixelPacketSemantics.Direction.S2C)
            .allMatch(contract -> contract.wireSupport()
                == HeyPixelPacketSemantics.WireSupport.DECODER));
        assertTrue(HeyPixelPacketSemantics.implementations().stream()
            .filter(contract -> contract.direction() != HeyPixelPacketSemantics.Direction.C2S)
            .allMatch(contract -> contract.runtimePolicy().appliesInboundOnClientLogicalWork()));
        assertTrue(HeyPixelPacketSemantics.implementations().stream()
            .filter(contract -> contract.direction()
                == HeyPixelPacketSemantics.Direction.BIDIRECTIONAL)
            .allMatch(contract -> contract.wireSupport()
                == HeyPixelPacketSemantics.WireSupport.BIDIRECTIONAL_CODEC));

        assertEquals(HeyPixelPacketSemantics.RuntimePolicy.CLIENT_LIFECYCLE_RESPONSE,
            policy(HeyPixelPacketSemantics.Direction.S2C, 101));
        assertEquals(HeyPixelPacketSemantics.RuntimePolicy.CLIENT_LOGICAL_METADATA,
            policy(HeyPixelPacketSemantics.Direction.S2C, 114));
        assertEquals(HeyPixelPacketSemantics.RuntimePolicy.REGISTERED_DORMANT,
            policy(HeyPixelPacketSemantics.Direction.C2S, 6));
        assertFalse(HeyPixelPacketSemantics.implementation(
            HeyPixelPacketSemantics.Direction.S2C, 102).isPresent());
        assertFalse(HeyPixelPacketSemantics.implementation(
            HeyPixelPacketSemantics.Direction.S2C, 106).isPresent());
        assertFalse(HeyPixelPacketSemantics.implementation(
            HeyPixelPacketSemantics.Direction.C2S, 12).isPresent());
    }

    @Test
    void keepsOnlyUnprovenBusinessDomainsAsNumericPlaceholders() {
        Set<String> placeholders = HeyPixelPacketSemantics.all().stream()
            .filter(packet -> packet.confidence() == HeyPixelPacketSemantics.Confidence.NUMERIC_PLACEHOLDER)
            .map(HeyPixelPacketSemantics.PacketSemantic::canonicalName)
            .collect(Collectors.toSet());
        assertEquals(Set.of(
            "C2SId0Packet",
            "S2CId100Packet"
        ), placeholders);
    }

    @Test
    void usesRecoveredActivationAndSyncTokenSemantics() {
        assertEquals("S2CPlayActivationEffectPacket",
            HeyPixelPacketSemantics.canonicalName(HeyPixelPacketSemantics.Direction.S2C, 104));
        assertTrue(HeyPixelPacketSemantics.find(HeyPixelPacketSemantics.Direction.S2C, 104)
            .orElseThrow().meaning().contains("30 totem particles"));
        assertEquals("S2CSyncTokenPacket",
            HeyPixelPacketSemantics.canonicalName(HeyPixelPacketSemantics.Direction.S2C, 114));
        var syncToken = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.S2C, 114).orElseThrow();
        assertTrue(syncToken.meaning().contains("enqueueWork"));
        assertTrue(syncToken.meaning().contains("LocalPlayer is non-null"));
        assertTrue(syncToken.meaning().contains("no delayed retry"));
        assertTrue(syncToken.meaning().contains("LoggingOut"));
        assertTrue(syncToken.meaning().contains("graceful disconnect and JVM exit"));
        assertTrue(syncToken.meaning().contains("two accept stages"));
        assertTrue(syncToken.meaning().contains("six balanced allocator generations"));
        assertTrue(syncToken.meaning().contains("seventeen balanced generations"));
        assertTrue(syncToken.meaning().contains("client logical work"));
        assertTrue(syncToken.meaning().contains("inline on that thread, otherwise queued"));
        assertTrue(syncToken.meaning().contains("generation/player-null"));
        assertTrue(syncToken.meaning().contains("never invokes SyncToken"));
        assertTrue(syncToken.meaning().contains("metadata-only"));
    }

    @Test
    void namesBoardInfoFromRecoveredOfficialKeysAndLogs() {
        var packet = HeyPixelPacketSemantics.find(HeyPixelPacketSemantics.Direction.S2C, 121)
            .orElseThrow();
        assertEquals("S2CBoardInfoPacket", packet.canonicalName());
        assertTrue(packet.meaning().contains("boardId"));
    }

    @Test
    void recordsTheDirectlyProvenAlignmentBoundariesWithoutInventingSinks() {
        var boardAction = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.C2S, 11).orElseThrow();
        assertEquals("C2SBoardActionPacket", boardAction.canonicalName());
        assertTrue(boardAction.meaning().contains("request_board"));

        var id100 = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.S2C, 100).orElseThrow();
        assertEquals("S2CId100Packet", id100.canonicalName());
        assertEquals(HeyPixelPacketSemantics.Confidence.NUMERIC_PLACEHOLDER,
            id100.confidence());
        assertTrue(id100.meaning().contains("ignores trailing values"));
        assertTrue(id100.meaning().contains("local decodeId100 is fail-closed"));

        var panel = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.S2C, 112).orElseThrow();
        assertEquals("S2CPanelModelOperationPacket", panel.canonicalName());
        assertTrue(panel.meaning().contains("expected ordinal domain 0/1/2"));
        assertTrue(panel.meaning().contains("control-dependent"));
        assertTrue(panel.meaning().contains("local decoder rejects it fail-closed"));
        assertTrue(panel.meaning().contains("only UPDATE clear is proven"));

        var shop = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.S2C, 113).orElseThrow();
        assertEquals("S2CShopMessagePacket", shop.canonicalName());
        assertTrue(shop.meaning().contains("zlib-only"));
        assertTrue(shop.meaning().contains("decode/cache only"));

        var selection = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.S2C, 115).orElseThrow();
        assertEquals("S2CSelectionDefinitionPacket", selection.canonicalName());
        assertTrue(selection.meaning().contains("required timestamp"));
        assertTrue(selection.meaning().contains("decode/cache only"));

        var boardInfo = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.S2C, 121).orElseThrow();
        assertEquals("S2CBoardInfoPacket", boardInfo.canonicalName());
        assertTrue(boardInfo.meaning().contains("coercion and defaults"));

        assertClosedNonLifecycle(HeyPixelPacketSemantics.Direction.C2S, 11);
        for (int wireId : new int[]{100, 113, 115, 121}) {
            assertClosedNonLifecycle(HeyPixelPacketSemantics.Direction.S2C, wireId);
        }
    }

    @Test
    void namesClosedUiAndFlightConsumersFromOfficialEvidence() {
        assertEquals("S2CFlightLeanDirectionPacket", canonicalS2C(105));
        assertEquals("S2CShowGameStorePopupPacket", canonicalS2C(107));
        assertEquals("S2COpenPanelPacket", canonicalS2C(110));
        assertEquals("S2CHudInfoBatchPacket", canonicalS2C(111));
        assertEquals("S2CPanelModelOperationPacket", canonicalS2C(112));
        assertEquals("S2COpenFashionGuiPacket", canonicalS2C(117));
        assertEquals("S2CFashionActionResultPacket", canonicalS2C(120));
        assertEquals("C2SPanelActionPacket", canonicalC2S(7));
        assertEquals("C2SShopRequestPacket", canonicalC2S(8));
        assertEquals("C2SHexSelectionClickPacket", canonicalC2S(9));
        assertEquals("C2SFashionInfoPacket", canonicalC2S(10));
        assertEquals("C2SBoardActionPacket", canonicalC2S(11));
        assertEquals("BidirectionalResourceIndexPacket",
            HeyPixelPacketSemantics.canonicalName(
                HeyPixelPacketSemantics.Direction.BIDIRECTIONAL, 109));
    }

    @Test
    void recordsTheCurrentDormantAttackTelemetryProducer() {
        var packet = HeyPixelPacketSemantics.find(HeyPixelPacketSemantics.Direction.C2S, 6)
            .orElseThrow();
        assertEquals("C2SAttackEntityTelemetryPacket", packet.canonicalName());
        assertTrue(packet.meaning().contains("dormant"));
    }

    @Test
    void recordsTheCurrentDefaultOnlyShortId1EvidenceProducer() {
        String meaning = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.C2S, 1).orElseThrow().meaning();
        assertTrue(meaning.contains("constructor-default-only"));
        assertTrue(meaning.contains("-1/empty and 0/empty"));
    }

    @Test
    void recordsTheOfficialId3ClockAndTwoPhaseProducer() {
        String meaning = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.C2S, 3).orElseThrow().meaning();
        assertTrue(meaning.contains("START/END"));
        assertTrue(meaning.contains("1 ms cached clock"));
        assertTrue(meaning.contains("wire timestamp uses wall clock"));
    }

    @Test
    void keepsId114V5ControlAndDataSinkClaimsSeparate() {
        String meaning = HeyPixelPacketSemantics.find(
            HeyPixelPacketSemantics.Direction.S2C, 114).orElseThrow().meaning();
        assertTrue(meaning.contains("control-path and predicate equivalence"));
        assertTrue(meaning.contains("not data-sink equivalence"));
        assertTrue(meaning.contains("slot+0xB5 residual"));
        assertTrue(meaning.contains("no observed external write"));
    }

    private static String canonicalS2C(int wireId) {
        return HeyPixelPacketSemantics.find(HeyPixelPacketSemantics.Direction.S2C, wireId)
            .orElseThrow()
            .canonicalName();
    }

    private static String canonicalC2S(int wireId) {
        return HeyPixelPacketSemantics.find(HeyPixelPacketSemantics.Direction.C2S, wireId)
            .orElseThrow()
            .canonicalName();
    }

    private static void assertClosedNonLifecycle(HeyPixelPacketSemantics.Direction direction,
                                                 int wireId) {
        String meaning = HeyPixelPacketSemantics.find(direction, wireId).orElseThrow().meaning();
        assertTrue(meaning.contains("not heartbeat/online/login/challenge lifecycle"));
        assertTrue(meaning.contains("transport-safe closed"));
        assertTrue(meaning.contains("nested UNKNOWN remains visible and non-blocking"));
    }

    private static Set<Integer> ids(HeyPixelPacketSemantics.Direction direction) {
        return HeyPixelPacketSemantics.all().stream()
            .filter(packet -> packet.direction() == direction)
            .map(HeyPixelPacketSemantics.PacketSemantic::wireId)
            .collect(Collectors.toSet());
    }

    private static HeyPixelPacketSemantics.RuntimePolicy policy(
        HeyPixelPacketSemantics.Direction direction,
        int wireId
    ) {
        return HeyPixelPacketSemantics.implementation(direction, wireId)
            .orElseThrow()
            .runtimePolicy();
    }
}
