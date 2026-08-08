package shit.zen.protocol.heypixel;

import java.util.List;

public final class HeyPixelProtocolDispatcher {
    private final HeyPixelProtocolState state;

    public HeyPixelProtocolDispatcher(HeyPixelProtocolState state) {
        this.state = state;
    }

    /**
     * Compatibility dispatcher preserving the pre-semantic-refresh Kind and value contracts.
     * New code should use {@link #dispatchCanonical(byte[])}.
     */
    public DispatchResult dispatch(byte[] wire) {
        S2CPacketDecoders.WrappedPacket wrapped = S2CPacketDecoders.decodeWrapper(wire);
        return dispatch(wrapped.packetId(), wrapped.payload());
    }

    /**
     * Compatibility dispatcher preserving the pre-semantic-refresh Kind and value contracts.
     * New code should use {@link #dispatchCanonical(int, byte[])}.
     */
    public DispatchResult dispatch(int packetId, byte[] payload) {
        if (packetId == 100) {
            S2CPacketDecoders.Id100Packet packet = S2CPacketDecoders.decodeId100(payload);
            state.setId100Packet(packet);
            return toLegacy(canonical(packetId, CanonicalKind.ID100_STATE, payload, packet));
        }
        if (packetId == 112) {
            S2CPacketDecoders.PanelModelOperationPacket packet =
                S2CPacketDecoders.decodeId112(payload);
            return toLegacy(applyCanonical(canonical(
                packetId, CanonicalKind.PANEL_MODEL_OPERATION, payload, packet)));
        }
        if (packetId == 114) {
            SyncTokenMetadata metadata = S2CPacketDecoders.decodeId114(payload);
            state.setSyncTokenMetadata(metadata);
            return toLegacy(canonical(packetId, CanonicalKind.SYNC_TOKEN, payload, metadata));
        }
        return toLegacy(dispatchCanonical(packetId, payload));
    }

    public CanonicalDispatchResult dispatchCanonical(byte[] wire) {
        S2CPacketDecoders.WrappedPacket wrapped = S2CPacketDecoders.decodeWrapper(wire);
        return dispatchCanonical(wrapped.packetId(), wrapped.payload());
    }

    public CanonicalDispatchResult dispatchCanonical(int packetId, byte[] payload) {
        return applyCanonical(decodeCanonical(packetId, payload));
    }

    /** Decodes without mutating protocol state; runtime client work applies the result later. */
    public CanonicalDispatchResult decodeCanonical(byte[] wire) {
        S2CPacketDecoders.WrappedPacket wrapped = S2CPacketDecoders.decodeWrapper(wire);
        return decodeCanonical(wrapped.packetId(), wrapped.payload());
    }

    /** Decodes without mutating protocol state; runtime client work applies the result later. */
    public CanonicalDispatchResult decodeCanonical(int packetId, byte[] payload) {
        return switch (packetId) {
            case 100 -> {
                S2CPacketDecoders.Id100Packet packet =
                    S2CPacketDecoders.decodeId100OfficialPrefix(payload);
                yield canonical(packetId, CanonicalKind.ID100_STATE, payload, packet);
            }
            case 101 -> {
                S2CPacketDecoders.Id101Challenge challenge = S2CPacketDecoders.decodeId101(payload);
                yield canonical(packetId, CanonicalKind.ENVIRONMENT_CHALLENGE, payload, challenge);
            }
            case 103 -> {
                S2CPacketDecoders.Id103CpsTelemetry telemetry =
                    S2CPacketDecoders.decodeId103(payload);
                yield canonical(packetId, CanonicalKind.CPS_TELEMETRY_STATE, payload, telemetry);
            }
            case 104 -> {
                S2CPacketDecoders.ActivationEffectPacket effect =
                    S2CPacketDecoders.decodeActivationEffect(payload);
                yield canonical(packetId, CanonicalKind.PLAY_ACTIVATION_EFFECT, payload, effect);
            }
            case 105 -> {
                S2CPacketDecoders.FlightLeanDirectionPacket packet =
                    S2CPacketDecoders.decodeId105(payload);
                yield canonical(packetId, CanonicalKind.FLIGHT_LEAN_DIRECTION, payload, packet);
            }
            case 107 -> {
                S2CPacketDecoders.ShowGameStorePopupRequest request =
                    S2CPacketDecoders.decodeId107(payload);
                yield canonical(packetId, CanonicalKind.SHOW_GAME_STORE_POPUP, payload, request);
            }
            case 108 -> {
                S2CPacketDecoders.ResourceBlobFragment fragment =
                    S2CPacketDecoders.decodeResourceBlobFragment(payload);
                yield canonical(packetId, CanonicalKind.RESOURCE_BLOB_FRAGMENT, payload, fragment);
            }
            case 109 -> {
                S2CPacketDecoders.ResourceIndexFragment fragment =
                    S2CPacketDecoders.decodeResourceIndexFragment(payload);
                yield canonical(packetId, CanonicalKind.RESOURCE_INDEX_FRAGMENT, payload, fragment);
            }
            case 110 -> {
                S2CPacketDecoders.OpenPanelPacket packet = S2CPacketDecoders.decodeId110(payload);
                yield canonical(packetId, CanonicalKind.OPEN_PANEL, payload, packet);
            }
            case 111 -> {
                S2CPacketDecoders.S2CHudInfoBatchPacket packet =
                    S2CPacketDecoders.decodeHudInfoBatch(payload);
                yield canonical(packetId, CanonicalKind.HUD_INFO_BATCH, payload, packet);
            }
            case 112 -> {
                S2CPacketDecoders.PanelModelOperationPacket packet =
                    S2CPacketDecoders.decodeId112OfficialPrefix(payload);
                yield canonical(packetId, CanonicalKind.PANEL_MODEL_OPERATION, payload, packet);
            }
            case 113 -> {
                S2CPacketDecoders.ShopMessagePacket packet =
                    S2CPacketDecoders.decodeShopMessage(payload);
                yield canonical(packetId, CanonicalKind.SHOP_MESSAGE, payload, packet);
            }
            case 114 -> {
                SyncTokenMetadata metadata =
                    S2CPacketDecoders.decodeSyncTokenOfficialPrefix(payload);
                yield canonical(packetId, CanonicalKind.SYNC_TOKEN, payload, metadata);
            }
            case 115 -> {
                S2CPacketDecoders.SelectionDefinitionPacket packet =
                    S2CPacketDecoders.decodeSelectionDefinition(payload);
                yield canonical(packetId, CanonicalKind.SELECTION_DEFINITION, payload, packet);
            }
            case 116 -> {
                S2CPacketDecoders.UnlockExchangeStatePacket packet =
                    S2CPacketDecoders.decodeUnlockExchangeState(payload);
                yield canonical(packetId, CanonicalKind.UNLOCK_EXCHANGE_STATE, payload, packet);
            }
            case 117 -> {
                S2CPacketDecoders.OpenFashionGuiPacket packet =
                    S2CPacketDecoders.decodeOpenFashionGui(payload);
                yield canonical(packetId, CanonicalKind.OPEN_FASHION_GUI, payload, packet);
            }
            case 118 -> {
                S2CPacketDecoders.FashionConfigPacket packet =
                    S2CPacketDecoders.decodeFashionConfig(payload);
                yield canonical(packetId, CanonicalKind.FASHION_CONFIG, payload, packet);
            }
            case 119 -> {
                S2CPacketDecoders.PlayerFashionStatePacket packet =
                    S2CPacketDecoders.decodePlayerFashionState(payload);
                yield canonical(packetId, CanonicalKind.PLAYER_FASHION_STATE, payload, packet);
            }
            case 120 -> {
                S2CPacketDecoders.FashionActionResultPacket packet =
                    S2CPacketDecoders.decodeFashionActionResult(payload);
                yield canonical(packetId, CanonicalKind.FASHION_ACTION_RESULT, payload, packet);
            }
            case 121 -> {
                S2CPacketDecoders.BoardInfoPacket packet =
                    S2CPacketDecoders.decodeBoardInfo(payload);
                yield canonical(packetId, CanonicalKind.BOARD_INFO, payload, packet);
            }
            default -> canonical(packetId, CanonicalKind.UNIMPLEMENTED, payload, payload);
        };
    }

    /** Applies one previously decoded result. Runtime calls this only inside client logical work. */
    CanonicalDispatchResult applyCanonical(CanonicalDispatchResult decoded) {
        int packetId = decoded.packetId();
        Object value = decoded.value();
        return switch (packetId) {
            case 100 -> {
                state.setId100Packet((S2CPacketDecoders.Id100Packet) value);
                yield decoded;
            }
            case 101 -> {
                state.setEnvironmentChallenge((S2CPacketDecoders.Id101Challenge) value);
                yield decoded;
            }
            case 103 -> {
                state.setCpsTelemetry((S2CPacketDecoders.Id103CpsTelemetry) value);
                yield decoded;
            }
            case 104 -> {
                state.setLastActivationEffect((S2CPacketDecoders.ActivationEffectPacket) value);
                yield decoded;
            }
            case 105 -> {
                state.setFlightLeanDirection((S2CPacketDecoders.FlightLeanDirectionPacket) value);
                yield decoded;
            }
            case 107 -> {
                state.setLastGameStorePopupRequest(
                    (S2CPacketDecoders.ShowGameStorePopupRequest) value);
                yield decoded;
            }
            case 108 -> {
                S2CPacketDecoders.ResourceBlobFragment fragment =
                    (S2CPacketDecoders.ResourceBlobFragment) value;
                var completed = state.acceptResourceBlobFragment(fragment);
                yield new CanonicalDispatchResult(
                    packetId,
                    completed.isPresent()
                        ? CanonicalKind.RESOURCE_BLOB_COMPLETE
                        : CanonicalKind.RESOURCE_BLOB_FRAGMENT,
                    decoded.payloadLength(),
                    completed.<Object>map(result -> result).orElse(fragment)
                );
            }
            case 109 -> {
                S2CPacketDecoders.ResourceIndexFragment fragment =
                    (S2CPacketDecoders.ResourceIndexFragment) value;
                var completed = state.acceptResourceIndexFragment(fragment);
                yield new CanonicalDispatchResult(
                    packetId,
                    completed.isPresent()
                        ? CanonicalKind.RESOURCE_INDEX_COMPLETE
                        : CanonicalKind.RESOURCE_INDEX_FRAGMENT,
                    decoded.payloadLength(),
                    completed.<Object>map(result -> result).orElse(fragment)
                );
            }
            case 110 -> {
                state.setOpenPanel((S2CPacketDecoders.OpenPanelPacket) value);
                yield decoded;
            }
            case 111 -> {
                state.replaceHudInfos((S2CPacketDecoders.S2CHudInfoBatchPacket) value);
                yield decoded;
            }
            case 112 -> {
                state.applyPanelModelOperation((S2CPacketDecoders.PanelModelOperationPacket) value);
                yield decoded;
            }
            case 113 -> {
                S2CPacketDecoders.ShopMessagePacket packet =
                    (S2CPacketDecoders.ShopMessagePacket) value;
                state.setShopMessage(packet);
                state.putJsonState(packetId, packet.json());
                yield decoded;
            }
            case 114 -> {
                state.setSyncTokenMetadata((SyncTokenMetadata) value);
                yield decoded;
            }
            case 115 -> {
                S2CPacketDecoders.SelectionDefinitionPacket packet =
                    (S2CPacketDecoders.SelectionDefinitionPacket) value;
                state.setSelectionDefinition(packet);
                state.putJsonState(packetId, packet.json());
                yield decoded;
            }
            case 116 -> {
                S2CPacketDecoders.UnlockExchangeStatePacket packet =
                    (S2CPacketDecoders.UnlockExchangeStatePacket) value;
                state.setUnlockExchangeState(packet);
                state.putJsonState(packetId, packet.json());
                yield decoded;
            }
            case 117 -> {
                S2CPacketDecoders.OpenFashionGuiPacket packet =
                    (S2CPacketDecoders.OpenFashionGuiPacket) value;
                state.setOpenFashionGui(packet);
                state.putJsonState(packetId, packet.json());
                yield decoded;
            }
            case 118 -> {
                S2CPacketDecoders.FashionConfigPacket packet =
                    (S2CPacketDecoders.FashionConfigPacket) value;
                state.setFashionConfig(packet);
                state.putJsonState(packetId, packet.json());
                yield decoded;
            }
            case 119 -> {
                S2CPacketDecoders.PlayerFashionStatePacket packet =
                    (S2CPacketDecoders.PlayerFashionStatePacket) value;
                state.setPlayerFashionState(packet);
                state.putJsonState(packetId, packet.json());
                yield decoded;
            }
            case 120 -> {
                S2CPacketDecoders.FashionActionResultPacket packet =
                    (S2CPacketDecoders.FashionActionResultPacket) value;
                state.setFashionActionResult(packet);
                state.putJsonState(packetId, packet.json());
                yield decoded;
            }
            case 121 -> {
                S2CPacketDecoders.BoardInfoPacket packet =
                    (S2CPacketDecoders.BoardInfoPacket) value;
                state.setBoardInfo(packet);
                state.putJsonState(packetId, packet.json());
                yield decoded;
            }
            default -> decoded;
        };
    }

    private static CanonicalDispatchResult canonical(
        int packetId,
        CanonicalKind kind,
        byte[] payload,
        Object value
    ) {
        return new CanonicalDispatchResult(packetId, kind, payload.length, value);
    }

    private static DispatchResult toLegacy(CanonicalDispatchResult canonical) {
        Object value = canonical.value();
        Kind kind;
        switch (canonical.kind()) {
            case PLAY_ACTIVATION_EFFECT -> {
                kind = Kind.PLAY_SOUND_EFFECT;
                value = S2CPacketDecoders.Id104SoundEffect.fromActivationEffect(
                    (S2CPacketDecoders.ActivationEffectPacket) value);
            }
            case RESOURCE_INDEX_FRAGMENT -> {
                kind = Kind.CHUNKED_DATA_FRAGMENT;
                value = S2CPacketDecoders.ChunkedDataFragment.fromResourceIndex(
                    (S2CPacketDecoders.ResourceIndexFragment) value);
            }
            case RESOURCE_INDEX_COMPLETE -> {
                kind = Kind.CHUNKED_DATA_COMPLETE;
                value = HeyPixelChunkAssembler.CompletedChunkedData.fromResourceIndex(
                    (HeyPixelChunkAssembler.CompletedResourceIndex) value);
            }
            case HUD_INFO_BATCH -> {
                kind = Kind.PANEL_RECORD_BATCH;
                S2CPacketDecoders.S2CHudInfoBatchPacket packet =
                    (S2CPacketDecoders.S2CHudInfoBatchPacket) value;
                value = packet.entries().stream()
                    .map(S2CPacketDecoders.PanelRecord::fromHudInfoEntry)
                    .toList();
            }
            case SHOP_MESSAGE -> {
                kind = Kind.SHOP_MESSAGE;
                S2CPacketDecoders.ShopMessagePacket packet =
                    (S2CPacketDecoders.ShopMessagePacket) value;
                value = new S2CPacketDecoders.JsonPayload(
                    canonical.packetId(), packet.json(), packet.zlibCompressed());
            }
            case SELECTION_DEFINITION -> {
                kind = Kind.SELECTION_DEFINITION;
                S2CPacketDecoders.SelectionDefinitionPacket packet =
                    (S2CPacketDecoders.SelectionDefinitionPacket) value;
                value = new S2CPacketDecoders.JsonPayload(
                    canonical.packetId(), packet.json(), packet.zlibCompressed());
            }
            case FASHION_ACTION_RESULT -> {
                kind = Kind.ACTION_RESULT;
                value = S2CPacketDecoders.ActionResultPacket.fromFashionActionResult(
                    (S2CPacketDecoders.FashionActionResultPacket) value);
            }
            case BOARD_INFO -> {
                kind = Kind.NOTICE_CENTER;
                value = S2CPacketDecoders.NoticeCenterSync.fromBoardInfo(
                    (S2CPacketDecoders.BoardInfoPacket) value);
            }
            default -> kind = Kind.valueOf(canonical.kind().name());
        }
        return new DispatchResult(
            canonical.packetId(), kind, canonical.payloadLength(), value);
    }

    /** Exact pre-refresh dispatcher kind set retained for compiled and exhaustive-switch callers. */
    public enum Kind {
        ID100_STATE,
        ENVIRONMENT_CHALLENGE,
        CPS_TELEMETRY_STATE,
        PLAY_SOUND_EFFECT,
        FLIGHT_LEAN_DIRECTION,
        SHOW_GAME_STORE_POPUP,
        RESOURCE_BLOB_FRAGMENT,
        RESOURCE_BLOB_COMPLETE,
        CHUNKED_DATA_FRAGMENT,
        CHUNKED_DATA_COMPLETE,
        OPEN_PANEL,
        PANEL_RECORD_BATCH,
        PANEL_MODEL_OPERATION,
        SYNC_TOKEN,
        SHOP_MESSAGE,
        SELECTION_DEFINITION,
        UNLOCK_EXCHANGE_STATE,
        FASHION_CONFIG,
        PLAYER_FASHION_STATE,
        OPEN_FASHION_GUI,
        ACTION_RESULT,
        NOTICE_CENTER,
        UNIMPLEMENTED
    }

    public enum CanonicalKind {
        ID100_STATE,
        ENVIRONMENT_CHALLENGE,
        CPS_TELEMETRY_STATE,
        PLAY_ACTIVATION_EFFECT,
        FLIGHT_LEAN_DIRECTION,
        SHOW_GAME_STORE_POPUP,
        RESOURCE_BLOB_FRAGMENT,
        RESOURCE_BLOB_COMPLETE,
        RESOURCE_INDEX_FRAGMENT,
        RESOURCE_INDEX_COMPLETE,
        OPEN_PANEL,
        HUD_INFO_BATCH,
        PANEL_MODEL_OPERATION,
        SYNC_TOKEN,
        SHOP_MESSAGE,
        SELECTION_DEFINITION,
        UNLOCK_EXCHANGE_STATE,
        FASHION_CONFIG,
        PLAYER_FASHION_STATE,
        OPEN_FASHION_GUI,
        FASHION_ACTION_RESULT,
        BOARD_INFO,
        UNIMPLEMENTED
    }

    public record DispatchResult(int packetId, Kind kind, int payloadLength, Object value) {
        public DispatchResult {
            value = defensiveValue(value);
        }

        @Override
        public Object value() {
            return defensiveValue(value);
        }
    }

    public record CanonicalDispatchResult(
        int packetId,
        CanonicalKind kind,
        int payloadLength,
        Object value
    ) {
        public CanonicalDispatchResult {
            value = defensiveValue(value);
        }

        @Override
        public Object value() {
            return defensiveValue(value);
        }
    }

    private static Object defensiveValue(Object value) {
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof List<?> list) return List.copyOf(list);
        return value;
    }
}
