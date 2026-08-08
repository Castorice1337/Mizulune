package shit.zen.protocol.heypixel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Evidence-ranked names plus explicit codec/runtime policy for the verified registry. */
public final class HeyPixelPacketSemantics {
    private static final String CLOSED_NON_LIFECYCLE_BOUNDARY =
        "not heartbeat/online/login/challenge lifecycle; transport-safe closed; "
            + "nested UNKNOWN remains visible and non-blocking";

    private static final List<PacketSemantic> PACKETS = List.of(
        packet(Direction.C2S, 0, "C2SId0Packet", Confidence.NUMERIC_PLACEHOLDER,
            "empty marker shape; trigger unknown"),
        packet(Direction.C2S, 1, "C2SEnvironmentAttestationPacket", Confidence.HIGH,
            "environment, mod, runtime and hardware evidence; current official SNEAK/SWIM "
                + "runtime evidence state is constructor-default-only (-1/empty and 0/empty)"),
        packet(Direction.C2S, 2, "C2SHeartbeatPacket", Confidence.HIGH,
            "fixed-rate 5000 ms heartbeat with wall-clock writer time and the official "
                + "daemon-refreshed 1 ms cached clock sample"),
        packet(Direction.C2S, 3, "CpsTelemetryStatePacket", Confidence.HIGH,
            "phase-agnostic START/END client-tick CPS change telemetry; click/window/cooldown "
                + "use the official 1 ms cached clock while the wire timestamp uses wall clock"),
        packet(Direction.C2S, 4, "C2SVshSourceReportPacket", Confidence.HIGH,
            "VSH source/file-content report"),
        packet(Direction.C2S, 5, "C2SUseBlockTelemetryPacket", Confidence.HIGH,
            "use-block prediction telemetry"),
        packet(Direction.C2S, 6, "C2SAttackEntityTelemetryPacket", Confidence.HIGH,
            "registered attack-entity hit and pose telemetry; current official mixin producer is dormant"),
        packet(Direction.C2S, 7, "C2SPanelActionPacket", Confidence.HIGH,
            "official panel-id action envelope; observed actions are banner and close"),
        packet(Direction.C2S, 8, "C2SShopRequestPacket", Confidence.HIGH,
            "official Shop buy/item and infos/keys request map"),
        packet(Direction.C2S, 9, "C2SHexSelectionClickPacket", Confidence.HIGH,
            "three-slot selection action with select/reroll operations"),
        packet(Direction.C2S, 10, "C2SFashionInfoPacket", Confidence.HIGH,
            "official fashion infos/apply/exchange/obtain/equip/unequip action map"),
        packet(Direction.C2S, 11, "C2SBoardActionPacket", Confidence.HIGH,
            "versioned Board Action envelope; request_board is the only hard-coded action, while "
                + "dynamic actions come from server DTOs; " + CLOSED_NON_LIFECYCLE_BOUNDARY
                + "; caller-supplied encoding only, with no automatic send trigger"),

        packet(Direction.S2C, 100, "S2CId100Packet", Confidence.NUMERIC_PLACEHOLDER,
            "official UUID + long prefix ignores trailing values; local decodeId100 is fail-closed; "
                + CLOSED_NON_LIFECYCLE_BOUNDARY + "; no business consumer"),
        packet(Direction.S2C, 101, "S2CEnvironmentChallengePacket", Confidence.HIGH,
            "environment attestation challenge"),
        packet(Direction.S2C, 103, "CpsTelemetryStatePacket", Confidence.HIGH,
            "timestamped server left/right CPS and click-state update"),
        packet(Direction.S2C, 104, "S2CPlayActivationEffectPacket", Confidence.HIGH,
            "plays 30 totem particles, a golden-apple activation animation and a configured sound"),
        packet(Direction.S2C, 105, "S2CFlightLeanDirectionPacket", Confidence.HIGH,
            "player flight-lean direction update"),
        packet(Direction.S2C, 107, "S2CShowGameStorePopupPacket", Confidence.HIGH,
            "empty server instruction that requests the official NetEase game-store popup"),
        packet(Direction.BIDIRECTIONAL, 108, "BidirectionalResourceBlobPacket", Confidence.HIGH,
            "chunked resourceName/hash image blob bus with official SHA-1 cache validation"),
        packet(Direction.BIDIRECTIONAL, 109, "BidirectionalResourceIndexPacket", Confidence.HIGH,
            "official ResourceIndex chunk bus with indexName/cacheHash and two-level JSON persistence; mode name and key transform remain unknown"),
        packet(Direction.S2C, 110, "S2COpenPanelPacket", Confidence.HIGH,
            "open official Panel UI with design size and optional scale controls"),
        packet(Direction.S2C, 111, "S2CHudInfoBatchPacket", Confidence.HIGH,
            "HUD registry batch keyed by hudId with width, height, hudPath, offsets and key"),
        packet(Direction.S2C, 112, "S2CPanelModelOperationPacket", Confidence.HIGH,
            "expected ordinal domain 0/1/2 maps to UPDATE/REMOVE/REPLACE with raw model JSON; "
                + "official helper behavior outside that domain is control-dependent; local decoder "
                + "rejects it fail-closed; only UPDATE clear is proven"),
        packet(Direction.S2C, 113, "S2CShopMessagePacket", Confidence.HIGH,
            "zlib-only Java-replacement JSON object for shop infos/buy/open; "
                + CLOSED_NON_LIFECYCLE_BOUNDARY + "; decode/cache only; no UI/manager sink"),
        packet(Direction.S2C, 114, "S2CSyncTokenPacket", Confidence.HIGH,
            "length-prefixed MessagePack sync-token delivery; NetworkEvent.Context.enqueueWork "
                + "runs the sole SyncToken.accept caller only when LocalPlayer is non-null, with "
                + "no delayed retry after a null-player drop; the sole SyncToken.logout caller is "
                + "a non-null-connection LoggingOut subscriber, so graceful disconnect and JVM exit "
                + "share the same clearLevel lifecycle; bounded MaxHook evidence closes two accept "
                + "stages with six balanced allocator generations and one logout callback with "
                + "seventeen balanced generations; v5 proves control-path and predicate equivalence "
                + "across synthetic argument variants but not data-sink equivalence, with an accept "
                + "slot+0xB5 residual and no observed external write; it does not prove a "
                + "natural server downlink condition; the local runtime mirrors client logical work "
                + "(inline on that thread, otherwise queued) with a generation/player-null contract, "
                + "never invokes SyncToken, and remains metadata-only"),
        packet(Direction.S2C, 115, "S2CSelectionDefinitionPacket", Confidence.HIGH,
            "zlib-only selection JSON with required timestamp and official definitions/non-definitions "
                + "branches; " + CLOSED_NON_LIFECYCLE_BOUNDARY
                + "; decode/cache only; no UI/manager sink"),
        packet(Direction.S2C, 116, "S2CUnlockExchangeStatePacket", Confidence.CONSERVATIVE,
            "unlock and exchange JSON state"),
        packet(Direction.S2C, 117, "S2COpenFashionGuiPacket", Confidence.HIGH,
            "open the official fashion GUI from its JSON state"),
        packet(Direction.S2C, 118, "S2CFashionConfigPacket", Confidence.HIGH,
            "fashion config, enablement and rarity state"),
        packet(Direction.S2C, 119, "S2CPlayerFashionStatePacket", Confidence.HIGH,
            "player/fashion state update"),
        packet(Direction.S2C, 120, "S2CFashionActionResultPacket", Confidence.HIGH,
            "fashion action, success and message result"),
        packet(Direction.S2C, 121, "S2CBoardInfoPacket", Confidence.HIGH,
            "inflate-or-plain Board Info JSON with official boardId/tabs/open coercion and defaults; "
                + CLOSED_NON_LIFECYCLE_BOUNDARY
                + "; decode/cache only; no UI/manager sink or automatic ID11")
    );
    private static final Map<Key, PacketSemantic> BY_KEY = index(PACKETS);
    private static final List<ImplementationContract> IMPLEMENTATIONS = List.of(
        contract(Direction.C2S, 0, WireSupport.ENCODER, RuntimePolicy.CALLER_SUPPLIED_ONLY),
        contract(Direction.C2S, 1, WireSupport.ENCODER, RuntimePolicy.ACTIVE_LIFECYCLE),
        contract(Direction.C2S, 2, WireSupport.ENCODER, RuntimePolicy.ACTIVE_LIFECYCLE),
        contract(Direction.C2S, 3, WireSupport.ENCODER, RuntimePolicy.ACTIVE_LIFECYCLE),
        contract(Direction.C2S, 4, WireSupport.ENCODER, RuntimePolicy.CALLER_SUPPLIED_ONLY),
        contract(Direction.C2S, 5, WireSupport.ENCODER, RuntimePolicy.ACTIVE_LIFECYCLE),
        contract(Direction.C2S, 6, WireSupport.ENCODER, RuntimePolicy.REGISTERED_DORMANT),
        contract(Direction.C2S, 7, WireSupport.ENCODER, RuntimePolicy.CALLER_SUPPLIED_ONLY),
        contract(Direction.C2S, 8, WireSupport.ENCODER, RuntimePolicy.CALLER_SUPPLIED_ONLY),
        contract(Direction.C2S, 9, WireSupport.ENCODER, RuntimePolicy.CALLER_SUPPLIED_ONLY),
        contract(Direction.C2S, 10, WireSupport.ENCODER, RuntimePolicy.CALLER_SUPPLIED_ONLY),
        contract(Direction.C2S, 11, WireSupport.ENCODER, RuntimePolicy.CALLER_SUPPLIED_ONLY),
        contract(Direction.BIDIRECTIONAL, 108, WireSupport.BIDIRECTIONAL_CODEC,
            RuntimePolicy.BIDIRECTIONAL_REASSEMBLY),
        contract(Direction.BIDIRECTIONAL, 109, WireSupport.BIDIRECTIONAL_CODEC,
            RuntimePolicy.BIDIRECTIONAL_REASSEMBLY),
        contract(Direction.S2C, 100, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 101, WireSupport.DECODER, RuntimePolicy.CLIENT_LIFECYCLE_RESPONSE),
        contract(Direction.S2C, 103, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 104, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 105, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 107, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 110, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 111, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 112, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 113, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 114, WireSupport.DECODER, RuntimePolicy.CLIENT_LOGICAL_METADATA),
        contract(Direction.S2C, 115, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 116, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 117, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 118, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 119, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 120, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE),
        contract(Direction.S2C, 121, WireSupport.DECODER, RuntimePolicy.PASSIVE_DECODE_CACHE)
    );
    private static final Map<Key, ImplementationContract> IMPLEMENTATION_BY_KEY =
        indexImplementations(IMPLEMENTATIONS);

    static {
        if (!BY_KEY.keySet().equals(IMPLEMENTATION_BY_KEY.keySet())) {
            throw new IllegalStateException("packet semantics and implementation contracts differ");
        }
    }

    private HeyPixelPacketSemantics() {
    }

    public static List<PacketSemantic> all() {
        return PACKETS;
    }

    public static Optional<PacketSemantic> find(Direction direction, int wireId) {
        return Optional.ofNullable(BY_KEY.get(new Key(direction, wireId)));
    }

    public static List<ImplementationContract> implementations() {
        return IMPLEMENTATIONS;
    }

    public static Optional<ImplementationContract> implementation(Direction direction, int wireId) {
        return Optional.ofNullable(IMPLEMENTATION_BY_KEY.get(new Key(direction, wireId)));
    }

    public static String canonicalName(Direction direction, int wireId) {
        Optional<PacketSemantic> semantic = find(direction, wireId);
        if (semantic.isEmpty() && direction != Direction.BIDIRECTIONAL) {
            semantic = find(Direction.BIDIRECTIONAL, wireId);
        }
        return semantic.map(PacketSemantic::canonicalName)
            .orElse(direction.name() + "Id" + wireId + "Packet");
    }

    private static PacketSemantic packet(Direction direction, int wireId, String canonicalName,
                                         Confidence confidence, String meaning) {
        return new PacketSemantic(direction, wireId, canonicalName, confidence, meaning);
    }

    private static ImplementationContract contract(
        Direction direction,
        int wireId,
        WireSupport wireSupport,
        RuntimePolicy runtimePolicy
    ) {
        return new ImplementationContract(direction, wireId, wireSupport, runtimePolicy);
    }

    private static Map<Key, PacketSemantic> index(List<PacketSemantic> packets) {
        LinkedHashMap<Key, PacketSemantic> result = new LinkedHashMap<>();
        for (PacketSemantic packet : packets) {
            Key key = new Key(packet.direction(), packet.wireId());
            if (result.put(key, packet) != null) {
                throw new IllegalStateException("duplicate packet semantic: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Key, ImplementationContract> indexImplementations(
        List<ImplementationContract> implementations
    ) {
        LinkedHashMap<Key, ImplementationContract> result = new LinkedHashMap<>();
        for (ImplementationContract implementation : implementations) {
            Key key = new Key(implementation.direction(), implementation.wireId());
            if (result.put(key, implementation) != null) {
                throw new IllegalStateException("duplicate packet implementation: " + key);
            }
        }
        return Map.copyOf(result);
    }

    public enum Direction {
        C2S,
        S2C,
        BIDIRECTIONAL
    }

    public enum Confidence {
        HIGH,
        CONSERVATIVE,
        NUMERIC_PLACEHOLDER
    }

    public enum WireSupport {
        ENCODER,
        DECODER,
        BIDIRECTIONAL_CODEC
    }

    public enum RuntimePolicy {
        ACTIVE_LIFECYCLE,
        CALLER_SUPPLIED_ONLY,
        REGISTERED_DORMANT,
        BIDIRECTIONAL_REASSEMBLY,
        PASSIVE_DECODE_CACHE,
        CLIENT_LIFECYCLE_RESPONSE,
        CLIENT_LOGICAL_METADATA;

        public boolean appliesInboundOnClientLogicalWork() {
            return switch (this) {
                case BIDIRECTIONAL_REASSEMBLY, PASSIVE_DECODE_CACHE,
                    CLIENT_LIFECYCLE_RESPONSE, CLIENT_LOGICAL_METADATA -> true;
                default -> false;
            };
        }
    }

    public record PacketSemantic(Direction direction, int wireId, String canonicalName,
                                 Confidence confidence, String meaning) {
    }

    public record ImplementationContract(
        Direction direction,
        int wireId,
        WireSupport wireSupport,
        RuntimePolicy runtimePolicy
    ) {
        public ImplementationContract {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(wireSupport, "wireSupport");
            Objects.requireNonNull(runtimePolicy, "runtimePolicy");
            if (wireId < 0) throw new IllegalArgumentException("wireId must not be negative");
        }
    }

    private record Key(Direction direction, int wireId) {
    }
}
