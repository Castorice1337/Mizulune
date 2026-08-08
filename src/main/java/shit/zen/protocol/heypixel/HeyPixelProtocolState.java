package shit.zen.protocol.heypixel;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Defensive in-memory state reconstructed from handled HeyPixel S2C packets. */
public final class HeyPixelProtocolState {
    private volatile S2CPacketDecoders.Id100Packet id100Packet;
    private volatile S2CPacketDecoders.Id101Challenge environmentChallenge;
    private volatile S2CPacketDecoders.Id103CpsTelemetry cpsTelemetry;
    private volatile S2CPacketDecoders.ActivationEffectPacket lastActivationEffect;
    private volatile S2CPacketDecoders.FlightLeanDirectionPacket flightLeanDirection;
    private volatile S2CPacketDecoders.ShowGameStorePopupRequest lastGameStorePopupRequest;
    private volatile S2CPacketDecoders.OpenPanelPacket openPanel;
    private volatile SyncTokenMetadata syncTokenMetadata;
    private volatile S2CPacketDecoders.PanelModelOperation panelModelOperation;
    private volatile S2CPacketDecoders.ShopMessagePacket shopMessage;
    private volatile S2CPacketDecoders.SelectionDefinitionPacket selectionDefinition;
    private volatile S2CPacketDecoders.UnlockExchangeStatePacket unlockExchangeState;
    private volatile S2CPacketDecoders.OpenFashionGuiPacket openFashionGui;
    private volatile S2CPacketDecoders.FashionConfigPacket fashionConfig;
    private volatile S2CPacketDecoders.PlayerFashionStatePacket playerFashionState;
    private volatile S2CPacketDecoders.FashionActionResultPacket fashionActionResult;
    private volatile S2CPacketDecoders.BoardInfoPacket boardInfo;
    private final Map<String, S2CPacketDecoders.HudInfo> hudInfos = new LinkedHashMap<>();
    private final Map<Integer, String> jsonStates = new LinkedHashMap<>();
    private final HeyPixelChunkAssembler chunkAssembler = new HeyPixelChunkAssembler();
    private List<String> panelModelJsonEntries = List.of();

    public synchronized void reset() {
        id100Packet = null;
        environmentChallenge = null;
        cpsTelemetry = null;
        lastActivationEffect = null;
        flightLeanDirection = null;
        lastGameStorePopupRequest = null;
        openPanel = null;
        syncTokenMetadata = null;
        panelModelOperation = null;
        shopMessage = null;
        selectionDefinition = null;
        unlockExchangeState = null;
        openFashionGui = null;
        fashionConfig = null;
        playerFashionState = null;
        fashionActionResult = null;
        boardInfo = null;
        hudInfos.clear();
        jsonStates.clear();
        chunkAssembler.reset();
        panelModelJsonEntries = List.of();
    }

    public void setId100Packet(S2CPacketDecoders.Id100Packet packet) {
        id100Packet = packet;
    }

    public Optional<S2CPacketDecoders.Id100Packet> id100Packet() {
        return Optional.ofNullable(id100Packet);
    }

    public void setEnvironmentChallenge(S2CPacketDecoders.Id101Challenge challenge) {
        environmentChallenge = challenge;
    }

    public Optional<S2CPacketDecoders.Id101Challenge> environmentChallenge() {
        return Optional.ofNullable(environmentChallenge);
    }

    public void setCpsTelemetry(S2CPacketDecoders.Id103CpsTelemetry telemetry) {
        cpsTelemetry = telemetry;
    }

    public Optional<S2CPacketDecoders.Id103CpsTelemetry> cpsTelemetry() {
        return Optional.ofNullable(cpsTelemetry);
    }

    public void setLastActivationEffect(S2CPacketDecoders.ActivationEffectPacket effect) {
        lastActivationEffect = Objects.requireNonNull(effect, "effect");
    }

    public Optional<S2CPacketDecoders.ActivationEffectPacket> lastActivationEffect() {
        return Optional.ofNullable(lastActivationEffect);
    }

    /** @deprecated Use {@link #setLastActivationEffect(S2CPacketDecoders.ActivationEffectPacket)}. */
    @Deprecated
    public void setLastSoundEffect(S2CPacketDecoders.Id104SoundEffect effect) {
        setLastActivationEffect(Objects.requireNonNull(effect, "effect").toActivationEffect());
    }

    /** @deprecated Use {@link #lastActivationEffect()}. */
    @Deprecated
    public Optional<S2CPacketDecoders.Id104SoundEffect> lastSoundEffect() {
        return lastActivationEffect().map(S2CPacketDecoders.Id104SoundEffect::fromActivationEffect);
    }

    public void setFlightLeanDirection(S2CPacketDecoders.FlightLeanDirectionPacket packet) {
        flightLeanDirection = packet;
    }

    public Optional<S2CPacketDecoders.FlightLeanDirectionPacket> flightLeanDirection() {
        return Optional.ofNullable(flightLeanDirection);
    }

    public void setLastGameStorePopupRequest(S2CPacketDecoders.ShowGameStorePopupRequest request) {
        lastGameStorePopupRequest = request;
    }

    public Optional<S2CPacketDecoders.ShowGameStorePopupRequest> lastGameStorePopupRequest() {
        return Optional.ofNullable(lastGameStorePopupRequest);
    }

    public void setOpenPanel(S2CPacketDecoders.OpenPanelPacket packet) {
        openPanel = packet;
    }

    public Optional<S2CPacketDecoders.OpenPanelPacket> openPanel() {
        return Optional.ofNullable(openPanel);
    }

    public synchronized void replaceHudInfos(
        S2CPacketDecoders.S2CHudInfoBatchPacket packet
    ) {
        Objects.requireNonNull(packet, "packet");
        hudInfos.clear();
        hudInfos.putAll(packet.hudInfosById());
    }

    public synchronized Map<String, S2CPacketDecoders.HudInfo> hudInfos() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(hudInfos));
    }

    /** @deprecated Use {@link #replaceHudInfos(S2CPacketDecoders.S2CHudInfoBatchPacket)}. */
    @Deprecated
    public synchronized void replacePanelRecords(List<S2CPacketDecoders.PanelRecord> records) {
        Objects.requireNonNull(records, "records");
        replaceHudInfos(new S2CPacketDecoders.S2CHudInfoBatchPacket(
            records.stream().map(S2CPacketDecoders.PanelRecord::toHudInfoEntry).toList()));
    }

    /** @deprecated Use {@link #hudInfos()}. */
    @Deprecated
    public synchronized Map<String, S2CPacketDecoders.PanelRecord> panelRecords() {
        LinkedHashMap<String, S2CPacketDecoders.PanelRecord> result = new LinkedHashMap<>();
        for (Map.Entry<String, S2CPacketDecoders.HudInfo> entry : hudInfos.entrySet()) {
            S2CPacketDecoders.HudInfoEntry hudEntry =
                new S2CPacketDecoders.HudInfoEntry(entry.getKey(), entry.getValue());
            result.put(entry.getKey(), S2CPacketDecoders.PanelRecord.fromHudInfoEntry(hudEntry));
        }
        return Collections.unmodifiableMap(result);
    }

    public synchronized void applyPanelModelOperation(
        S2CPacketDecoders.PanelModelOperationPacket packet
    ) {
        panelModelOperation = packet.operation();
        panelModelJsonEntries = List.copyOf(packet.jsonEntries());
    }

    public Optional<S2CPacketDecoders.PanelModelOperation> panelModelOperation() {
        return Optional.ofNullable(panelModelOperation);
    }

    public synchronized List<String> panelModelJsonEntries() {
        return panelModelJsonEntries;
    }

    public void setSyncTokenMetadata(SyncTokenMetadata metadata) {
        syncTokenMetadata = Objects.requireNonNull(metadata, "metadata");
    }

    public Optional<SyncTokenMetadata> syncTokenMetadata() {
        return Optional.ofNullable(syncTokenMetadata);
    }

    public synchronized void putJsonState(int packetId, String json) {
        jsonStates.put(packetId, json);
    }

    public synchronized Map<Integer, String> jsonStates() {
        return Map.copyOf(jsonStates);
    }

    public void setShopMessage(S2CPacketDecoders.ShopMessagePacket packet) {
        shopMessage = Objects.requireNonNull(packet, "packet");
    }

    public Optional<S2CPacketDecoders.ShopMessagePacket> shopMessage() {
        return Optional.ofNullable(shopMessage);
    }

    public void setSelectionDefinition(S2CPacketDecoders.SelectionDefinitionPacket packet) {
        selectionDefinition = Objects.requireNonNull(packet, "packet");
    }

    public Optional<S2CPacketDecoders.SelectionDefinitionPacket> selectionDefinition() {
        return Optional.ofNullable(selectionDefinition);
    }

    public void setUnlockExchangeState(S2CPacketDecoders.UnlockExchangeStatePacket packet) {
        unlockExchangeState = Objects.requireNonNull(packet, "packet");
    }

    public Optional<S2CPacketDecoders.UnlockExchangeStatePacket> unlockExchangeState() {
        return Optional.ofNullable(unlockExchangeState);
    }

    public void setOpenFashionGui(S2CPacketDecoders.OpenFashionGuiPacket packet) {
        openFashionGui = Objects.requireNonNull(packet, "packet");
    }

    public Optional<S2CPacketDecoders.OpenFashionGuiPacket> openFashionGui() {
        return Optional.ofNullable(openFashionGui);
    }

    public void setFashionConfig(S2CPacketDecoders.FashionConfigPacket packet) {
        fashionConfig = Objects.requireNonNull(packet, "packet");
    }

    public Optional<S2CPacketDecoders.FashionConfigPacket> fashionConfig() {
        return Optional.ofNullable(fashionConfig);
    }

    public void setPlayerFashionState(S2CPacketDecoders.PlayerFashionStatePacket packet) {
        playerFashionState = Objects.requireNonNull(packet, "packet");
    }

    public Optional<S2CPacketDecoders.PlayerFashionStatePacket> playerFashionState() {
        return Optional.ofNullable(playerFashionState);
    }

    public void setFashionActionResult(S2CPacketDecoders.FashionActionResultPacket packet) {
        fashionActionResult = Objects.requireNonNull(packet, "packet");
    }

    public Optional<S2CPacketDecoders.FashionActionResultPacket> fashionActionResult() {
        return Optional.ofNullable(fashionActionResult);
    }

    public void setBoardInfo(S2CPacketDecoders.BoardInfoPacket packet) {
        boardInfo = Objects.requireNonNull(packet, "packet");
    }

    public Optional<S2CPacketDecoders.BoardInfoPacket> boardInfo() {
        return Optional.ofNullable(boardInfo);
    }

    /** @deprecated Use {@link #setBoardInfo(S2CPacketDecoders.BoardInfoPacket)}. */
    @Deprecated
    public void setNoticeCenterState(S2CPacketDecoders.NoticeCenterSync sync) {
        Objects.requireNonNull(sync, "sync");
        setBoardInfo(new S2CPacketDecoders.BoardInfoPacket(
            sync.json(), sync.state(), sync.zlibCompressed()));
    }

    /** @deprecated Use {@link #boardInfo()}. */
    @Deprecated
    public Optional<JsonObject> noticeCenterState() {
        return boardInfo().map(S2CPacketDecoders.BoardInfoPacket::state);
    }

    /** @deprecated Use {@code boardInfo().map(BoardInfoPacket::zlibCompressed)}. */
    @Deprecated
    public boolean noticeCenterCompressed() {
        return boardInfo().map(S2CPacketDecoders.BoardInfoPacket::zlibCompressed).orElse(false);
    }

    public Optional<HeyPixelChunkAssembler.CompletedResourceBlob> acceptResourceBlobFragment(
        S2CPacketDecoders.ResourceBlobFragment fragment
    ) {
        return chunkAssembler.accept(fragment);
    }

    public Optional<HeyPixelChunkAssembler.CompletedResourceIndex> acceptResourceIndexFragment(
        S2CPacketDecoders.ResourceIndexFragment fragment
    ) {
        return chunkAssembler.accept(fragment);
    }

    /** @deprecated Use {@link #acceptResourceIndexFragment}. */
    @Deprecated
    public Optional<HeyPixelChunkAssembler.CompletedChunkedData> acceptChunkedDataFragment(
        S2CPacketDecoders.ChunkedDataFragment fragment
    ) {
        return chunkAssembler.accept(fragment);
    }
}
