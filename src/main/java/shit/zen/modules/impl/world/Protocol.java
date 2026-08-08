package shit.zen.modules.impl.world;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.MouseButtonEvent;
import shit.zen.event.impl.UseBlockEvent;
import shit.zen.manager.ConfigManager;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.protocol.heypixel.HeyPixelProtocolRuntime;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

public final class Protocol extends Module {
    static final boolean DEFAULT_OBSERVE_ONLY = false;
    static final boolean DEFAULT_ALLOW_LIVE_SEND = true;

    private final HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(
        mc, Path.of(ZenClient.configDir));
    private Value<String> enabledHosts;
    private Value<Boolean> traceLogger;
    private Value<Boolean> observeOnly;
    private Value<Boolean> allowLiveSend;
    private Value<Boolean> strictProviderGate;
    private Value<Boolean> id114OfficialNative;
    private Value<String> id1InstallRoot;
    private Value<String> id1InstanceDirectory;
    private Value<String> id1OfficialUserDirectory;
    private Value<String> id1OfficialJavaHome;
    private Value<Boolean> syntheticHwid;
    private Value<String> syntheticHwidProfile;
    private volatile List<String> savedHwidProfiles = List.of();
    private volatile String ephemeralHwidSelector = "";
    private boolean bootstrapConfigurationComplete;

    public Protocol() {
        super("Protocol", Category.WORLD);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup connection = root.group("connection", "Connection");
        enabledHosts = connection.text("enabled_hosts", "Enabled Hosts", "pc.bjdmc.net,*.bjdmc.net");
        traceLogger = connection.bool("trace_logger", "Trace Logger", true);
        observeOnly = connection.bool(
            "observe_only", "Observe Only", DEFAULT_OBSERVE_ONLY);
        allowLiveSend = connection.bool(
            "allow_live_send", "Allow Live Send", DEFAULT_ALLOW_LIVE_SEND);
        strictProviderGate = connection.bool("strict_provider_gate", "Strict Provider Gate", true);
        id114OfficialNative = connection.bool(
            "id114_official_native", "ID114 Official Native", true);
        id1InstallRoot = connection.text("id1_game_directory", "ID1 Install Root", "");
        id1InstanceDirectory = connection.text(
            "id1_instance_directory", "ID1 Instance Directory", "");
        id1OfficialUserDirectory = connection.text(
            "id1_official_user_directory",
            "ID1 Official User Directory",
            ""
        );
        id1OfficialJavaHome = connection.text(
            "id1_official_java_home",
            "ID1 Official Java Home",
            ""
        );
        syntheticHwid = connection.bool("synthetic_hwid", "Synthetic HWID", false);
        refreshSavedHwidProfilesSafely();
        String initialHwidProfile = savedHwidProfiles.isEmpty() ? "" : savedHwidProfiles.get(0);
        connection.action("synthetic_hwid_random", "Random HWID", "Random", this::useRandomHwid);
        syntheticHwidProfile = connection.text(
                "synthetic_hwid_profile", "Synthetic HWID Profile", initialHwidProfile)
            .metadata("optionsSupplier", (java.util.function.Supplier<List<String>>)() -> savedHwidProfiles)
            .metadata("dropdown", true)
            .metadata("emptyOptionLabel", "No saved profiles")
            .visibleWhen(() -> syntheticHwid != null && Boolean.TRUE.equals(syntheticHwid.getValue()))
            .listener((value, previous, current) -> {
                if (canonicalSavedHwidProfile(current) != null) {
                    ephemeralHwidSelector = "";
                    updateRuntimeSettings();
                }
            });
    }

    @Override
    protected void onEnable() {
        if (!bootstrapConfigurationComplete) return;
        updateRuntimeSettings();
        runtime.start();
    }

    @Override
    protected void onDisable() {
        runtime.stop();
    }

    @Override
    public String getSuffix() {
        return runtime.isActiveForCurrentServer() ? "HeyPixel" : "Idle";
    }

    public void bootstrapTick() {
        if (!isEnabled()) return;
        updateRuntimeSettings();
        runtime.tick();
        runtime.tickId3Phase();
    }

    public void bootstrapTickEnd() {
        if (!isEnabled()) return;
        runtime.tickId3Phase();
        runtime.tickReadyPhase();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isIncoming()) return;
        if (event.getPacket() instanceof ClientboundCustomPayloadPacket payload) {
            if (runtime.handle(payload, event.getSourceConnection())) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onMouseButton(MouseButtonEvent event) {
        runtime.recordMouseButton(event.button(), event.action());
    }

    @EventTarget
    public void onUseBlock(UseBlockEvent event) {
        runtime.sendUseBlock(event.player(), event.hand(), event.hit());
    }

    public HeyPixelProtocolRuntime getRuntime() {
        return runtime;
    }

    /** Selects an in-memory-only profile. Its selector and generated hardware are never saved. */
    public synchronized void useRandomHwid() {
        ephemeralHwidSelector = runtime.createEphemeralHwidProfile();
        if (syntheticHwid != null) syntheticHwid.setValue(true);
        updateRuntimeSettings();
    }

    /** Creates, selects and persists a named profile. Existing names are not overwritten. */
    public synchronized String createHwidProfile(String name) {
        String created = runtime.createSavedHwidProfile(name);
        refreshSavedHwidProfiles();
        ephemeralHwidSelector = "";
        if (syntheticHwid != null) syntheticHwid.setValue(true);
        if (syntheticHwidProfile != null) syntheticHwidProfile.setValue(created);
        updateRuntimeSettings();
        ConfigManager.requestSaveIfReady();
        return created;
    }

    /** Selects an existing named profile without modifying its saved hardware snapshot. */
    public synchronized String loadHwidProfile(String name) {
        String loaded = runtime.loadSavedHwidProfile(name);
        refreshSavedHwidProfiles();
        ephemeralHwidSelector = "";
        if (syntheticHwid != null) syntheticHwid.setValue(true);
        if (syntheticHwidProfile != null) syntheticHwidProfile.setValue(loaded);
        updateRuntimeSettings();
        ConfigManager.requestSaveIfReady();
        return loaded;
    }

    public synchronized List<String> listHwidProfiles() {
        refreshSavedHwidProfiles();
        return savedHwidProfiles;
    }

    @Override
    protected void onConfigLoaded() {
        completeBootstrapConfiguration();
    }

    /** Completes the early config pass after values have been applied. */
    public void completeBootstrapConfiguration() {
        bootstrapConfigurationComplete = true;
        normalizeHwidSelection();
        updateRuntimeSettings();
        if (isEnabled() && !runtime.isRunning()) runtime.start();
    }

    /** Exact MAIN-channel ingress used before the cancellable general EventBus. */
    public boolean consumeEarlyPacket(Connection sourceConnection, Packet<?> packet) {
        if (!(packet instanceof ClientboundCustomPayloadPacket payload)
            || !HeyPixelProtocolRuntime.MAIN_CHANNEL.equals(payload.getIdentifier())) {
            return false;
        }
        return runtime.handle(payload, sourceConnection);
    }

    public void onFinalPacketWrite(Packet<?> packet) {
        runtime.onFinalPacketWrite(packet);
    }

    public void onLoggingOut() {
        runtime.onLoggingOut();
    }

    public void shutdownRuntime() {
        runtime.stop();
    }

    private void updateRuntimeSettings() {
        if (enabledHosts == null) return;
        boolean useSyntheticHwid = syntheticHwid != null
            && Boolean.TRUE.equals(syntheticHwid.getValue());
        String selectedHwidProfile = ephemeralHwidSelector.isBlank()
            ? syntheticHwidProfile == null ? "" : syntheticHwidProfile.getValue()
            : ephemeralHwidSelector;
        if (useSyntheticHwid
            && ephemeralHwidSelector.isBlank()
            && canonicalSavedHwidProfile(selectedHwidProfile) == null) {
            useSyntheticHwid = false;
        }
        runtime.configure(
            enabledHosts.getValue(),
            Boolean.TRUE.equals(traceLogger.getValue()),
            Boolean.TRUE.equals(observeOnly.getValue()),
            Boolean.TRUE.equals(allowLiveSend.getValue()),
            Boolean.TRUE.equals(strictProviderGate.getValue()),
            id1InstallRoot == null ? "" : id1InstallRoot.getValue(),
            id1InstanceDirectory == null ? "" : id1InstanceDirectory.getValue(),
            id1OfficialUserDirectory == null ? "" : id1OfficialUserDirectory.getValue(),
            id1OfficialJavaHome == null ? "" : id1OfficialJavaHome.getValue(),
            useSyntheticHwid,
            selectedHwidProfile,
            id114OfficialNative != null && Boolean.TRUE.equals(id114OfficialNative.getValue())
        );
    }

    private void normalizeHwidSelection() {
        refreshSavedHwidProfilesSafely();
        if (syntheticHwidProfile == null || syntheticHwid == null) return;
        String canonical = canonicalSavedHwidProfile(syntheticHwidProfile.getValue());
        if (canonical != null) {
            syntheticHwidProfile.setValue(canonical);
            return;
        }
        if (!savedHwidProfiles.isEmpty()) {
            syntheticHwidProfile.setValue(savedHwidProfiles.get(0));
            return;
        }
        if (ephemeralHwidSelector.isBlank()) syntheticHwid.setValue(false);
    }

    private void refreshSavedHwidProfiles() {
        savedHwidProfiles = List.copyOf(runtime.listSavedHwidProfiles());
    }

    private void refreshSavedHwidProfilesSafely() {
        try {
            refreshSavedHwidProfiles();
        } catch (RuntimeException ignored) {
            savedHwidProfiles = List.of();
        }
    }

    private String canonicalSavedHwidProfile(String name) {
        if (name == null) return null;
        for (String profile : savedHwidProfiles) {
            if (profile.equalsIgnoreCase(name.trim())) return profile;
        }
        return null;
    }
}
