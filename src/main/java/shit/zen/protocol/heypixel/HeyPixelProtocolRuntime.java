package shit.zen.protocol.heypixel;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.misc.PacketUtil;

public final class HeyPixelProtocolRuntime {
    private static final long INITIAL_ID1_RETRY_INTERVAL_MILLIS = 2_000L;
    private static final long ID1_CONTEXT_RETRY_INTERVAL_MILLIS = 2_000L;
    private static final long TARGET_CACHE_REFRESH_INTERVAL_MILLIS = 1_000L;
    private static final long ID2_HEARTBEAT_INTERVAL_MILLIS = 5_000L;
    private static final long PENDING_WRITE_TIMEOUT_MILLIS = 15_000L;
    private static final long ANY_ID2_HEARTBEAT_GENERATION = Long.MIN_VALUE;
    private static final long ID3_CPS_WINDOW_MILLIS = 1_000L;
    private static final long ID3_SEND_COOLDOWN_MILLIS = 250L;
    private static final int READY_SYNC_OPCODE = 12;
    static final String ID1_OFFICIAL_USER_DIRECTORY_PROPERTY =
        "mizulune.heypixel.officialUserDir";
    static final String ID1_OFFICIAL_JAVA_HOME_PROPERTY =
        "mizulune.heypixel.officialJavaHome";
    private static final String ID1_OFFICIAL_USER_DIRECTORY_ENV =
        "MIZULUNE_HEYPIXEL_OFFICIAL_USER_DIR";
    private static final String ID1_OFFICIAL_JAVA_HOME_ENV =
        "MIZULUNE_HEYPIXEL_OFFICIAL_JAVA_HOME";
    private static final AtomicInteger ID2_WORKER_SEQUENCE = new AtomicInteger();
    public static final ResourceLocation MAIN_CHANNEL = channel("heypixel:s2cevent");
    public static final ResourceLocation SKIN_CHANNEL = channel("heypixel:sync_skins");
    public static final ResourceLocation FORM_CHANNEL = channel("floodgate:form");
    public static final ResourceLocation NETEASE_CHANNEL = channel("floodgate:netease");

    private final Minecraft minecraft;
    private final ProtocolSessionProvider sessions;
    private final ProxyTargetResolver targets;
    private final ProtocolTraceLogger trace;
    private final Id114NativeSink.Factory id114NativeSinkFactory;
    private final HeyPixelProtocolState state = new HeyPixelProtocolState();
    private final HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);
    private final UUID protocolRuntimeUuid = UUID.randomUUID();
    private final ExecutorService id1Executor = createId1Executor();
    private final ScheduledExecutorService id2HeartbeatExecutor = createId2HeartbeatExecutor();
    private final Object id1ContextLock = new Object();
    private final Object id1ContextBuildLock = new Object();
    private final Object id114NativePreflightLock = new Object();
    private final AtomicLong id1ContextEpoch = new AtomicLong();
    private final AtomicLong id1LifecycleGeneration = new AtomicLong();
    private final FinalWriteLedger<PendingWrite> pendingWrites = new FinalWriteLedger<>();
    private final Set<Id114TokenLease> pendingId114TokenLeases =
        Collections.newSetFromMap(new IdentityHashMap<>());

    static ExecutorService createId1Executor() {
        return Executors.newCachedThreadPool();
    }

    static ScheduledExecutorService createId2HeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(
                task, "Mizulune-ID2-" + ID2_WORKER_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    static ScheduledFuture<?> scheduleId2Heartbeat(
        ScheduledExecutorService executor,
        Runnable heartbeat
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(heartbeat, "heartbeat");
        return executor.scheduleAtFixedRate(
            heartbeat,
            ID2_HEARTBEAT_INTERVAL_MILLIS,
            ID2_HEARTBEAT_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        );
    }

    private volatile boolean enabled;
    private volatile boolean observeOnly = true;
    private volatile boolean allowLiveSend;
    private volatile boolean strictProviderGate = true;
    private volatile boolean id114OfficialNativeEnabled;
    private volatile String enabledHosts = "pc.bjdmc.net,*.bjdmc.net";
    private volatile HeyPixelInstallLayout id1LayoutOverride;
    private volatile String id1LayoutConfigurationError = "";
    private volatile OfficialRuntimeConfiguration id1OfficialRuntime =
        OfficialRuntimeConfiguration.automatic();
    private volatile Id1HwidProvider.Settings hwidSettings = Id1HwidProvider.Settings.real();
    private volatile String lastHost = "";
    private volatile String lastEndpoint = "";
    private volatile Id1TargetIdentity lastTargetIdentity;
    private volatile Object lastConnection;
    /** Survives module stop/start so only a real Minecraft connection change closes a login. */
    private Object activeLifecycleConnection;
    /**
     * Tracks the signed target/session identity for the active Minecraft connection. Unlike
     * lastTargetIdentity this survives a module stop/start, so an account switch that happens
     * while Protocol is stopped is still observed when the module resumes.
     */
    private boolean activeLifecycleTargetObserved;
    private Id1TargetIdentity activeLifecycleTargetIdentity;
    private volatile TargetCacheEntry targetCache = TargetCacheEntry.empty();
    private volatile UUID localUuid;
    private volatile PbeMd5DesId1Crypto payloadCrypto;
    private volatile Id1PacketBuilder id1Builder;
    private volatile BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> id1Input;
    private volatile long nextId1ContextAttemptAt;
    private final Id1HwidProvider hwidProvider;
    private Id1InitialAttempt initialId1ScheduledAttempt;
    private boolean id1ContextUsed;
    private LocalPlayer observedLocalPlayer;
    private boolean localPlayerJoinPending;
    private boolean initialId1Submitted;
    private boolean readySyncSent;
    private boolean readySyncScheduled;
    private volatile long nextInitialId1AttemptAt;
    private ScheduledFuture<?> id2HeartbeatTask;
    private long id2HeartbeatGeneration;
    private final Object clickLock = new Object();
    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();
    private volatile long nextId3SendAt;
    private volatile int lastId3LeftCps;
    private volatile int lastId3RightCps;
    private volatile Id114NativeSink id114NativeSink =
        Id114NativeSink.unavailable(Id114NativeSink.Reason.LAYOUT_UNAVAILABLE);
    private volatile HeyPixelInstallLayout id114NativeLayout;
    private volatile Id114NativeSink.Availability id114NativePreflightAvailability =
        Id114NativeSink.Availability.unavailable(Id114NativeSink.Reason.LAYOUT_UNAVAILABLE);
    private volatile boolean id114NativePreflightFrozen;
    private Id114NativeSink pendingId114LogoutSink;

    public HeyPixelProtocolRuntime(Minecraft minecraft, Path configDirectory) {
        this(minecraft, configDirectory, OfficialId114NativeSink::new);
    }

    HeyPixelProtocolRuntime(
        Minecraft minecraft,
        Path configDirectory,
        Id114NativeSink.Factory id114NativeSinkFactory
    ) {
        this.minecraft = minecraft;
        this.sessions = new ProtocolSessionProvider(configDirectory);
        this.targets = new ProxyTargetResolver(sessions);
        this.trace = new ProtocolTraceLogger(configDirectory.resolve("protocol-trace"));
        this.hwidProvider = new Id1HwidProvider(configDirectory);
        this.id114NativeSinkFactory =
            Objects.requireNonNull(id114NativeSinkFactory, "id114NativeSinkFactory");
    }

    /** Prepares a process-memory-only synthetic profile and returns its transient selector. */
    public String createEphemeralHwidProfile() {
        return hwidProvider.createEphemeral();
    }

    /** Creates a persistent synthetic profile without silently replacing an existing name. */
    public String createSavedHwidProfile(String name) {
        return hwidProvider.createSaved(name);
    }

    /** Resolves the canonical spelling of an existing persistent synthetic profile. */
    public String loadSavedHwidProfile(String name) {
        return hwidProvider.loadSaved(name);
    }

    public List<String> listSavedHwidProfiles() {
        return hwidProvider.listSavedProfiles();
    }

    public void start() {
        synchronized (id1ContextLock) {
            if (enabled) return;
        }
        Id114NativeSink.Availability nativePreflight = ensureId114NativePreflight();
        List<Packet<?>> stalePackets;
        synchronized (id1ContextLock) {
            if (enabled) return;
            if (id2HeartbeatTask != null) id2HeartbeatTask.cancel(false);
            id2HeartbeatTask = null;
            long heartbeatGeneration = ++id2HeartbeatGeneration;
            invalidateId1ContextLocked(true);
            stalePackets = clearPendingWritesLocked();
            lastHost = "";
            lastEndpoint = "";
            lastTargetIdentity = null;
            lastConnection = null;
            observedLocalPlayer = null;
            localPlayerJoinPending = false;
            readySyncScheduled = false;
            targetCache = TargetCacheEntry.empty();
            nextInitialId1AttemptAt = 0L;
            nextId1ContextAttemptAt = 0L;
            id2HeartbeatTask = scheduleId2Heartbeat(
                id2HeartbeatExecutor,
                () -> runId2Heartbeat(heartbeatGeneration)
            );
            enabled = true;
        }
        cancelPreparedPackets(stalePackets);
        resetClickState();
        refreshLocalUuid();
        trace.log("sync-token-native-preflight", MAIN_CHANNEL.toString(), 114, Map.of(
            "invocationAvailable", nativePreflight.available(),
            "callbackReadinessConfirmed",
                nativePreflight.reason() == Id114NativeSink.Reason.READY,
            "reason", nativePreflight.reason().name(),
            "frozen", true
        ));
        trace.log("runtime-start", null, null, Map.of(
            "observeOnly", observeOnly,
            "allowLiveSend", allowLiveSend,
            "strictProviderGate", strictProviderGate,
            "id114OfficialNativeEnabled", id114OfficialNativeEnabled,
            "id114NativePreflightReason", nativePreflight.reason().name(),
            "cryptoReady", payloadCrypto != null,
            "id1ContextReady", id1Builder != null && id1Input != null
        ));
    }

    public void stop() {
        List<Packet<?>> stalePackets;
        ScheduledFuture<?> heartbeatTask;
        synchronized (id1ContextLock) {
            if (!enabled) return;
            enabled = false;
            ++id2HeartbeatGeneration;
            heartbeatTask = id2HeartbeatTask;
            id2HeartbeatTask = null;
            invalidateId1ContextLocked(true);
            stalePackets = clearPendingWritesLocked();
            lastHost = "";
            lastEndpoint = "";
            lastTargetIdentity = null;
            lastConnection = null;
            observedLocalPlayer = null;
            localPlayerJoinPending = false;
            readySyncScheduled = false;
            targetCache = TargetCacheEntry.empty();
            nextInitialId1AttemptAt = 0L;
            nextId1ContextAttemptAt = 0L;
        }
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        cancelPreparedPackets(stalePackets);
        trace.log("runtime-stop", null, null, Map.of());
        resetClickState();
        state.reset();
    }

    /** Resets per-login latches while preserving the immutable startup ID1 evidence context. */
    public void onLoggingOut() {
        LoginLifecycleReset reset;
        synchronized (id1ContextLock) {
            reset = resetLoginLifecycleLocked();
        }
        finishLoginLifecycleReset(reset, "official-logout");
    }

    /**
     * Fallback for a missed Forge/clearLevel callback. The identity is the logical
     * ClientPacketListener object, not an endpoint string or a mutable account field.
     */
    void observeConnectionLifecycle(Object connection) {
        LoginLifecycleReset reset = null;
        synchronized (id1ContextLock) {
            if (!enabled || activeLifecycleConnection == connection) return;
            if (activeLifecycleConnection != null) {
                reset = resetLoginLifecycleLocked();
            }
            activeLifecycleConnection = connection;
        }
        if (reset != null) finishLoginLifecycleReset(reset, "connection-transition-fallback");
    }

    private LoginLifecycleReset resetLoginLifecycleLocked() {
        invalidateId1TasksLocked();
        List<Packet<?>> stalePackets = clearPendingWritesLocked();
        lastHost = "";
        lastEndpoint = "";
        lastTargetIdentity = null;
        lastConnection = null;
        activeLifecycleConnection = null;
        activeLifecycleTargetObserved = false;
        activeLifecycleTargetIdentity = null;
        observedLocalPlayer = null;
        localPlayerJoinPending = false;
        initialId1Submitted = false;
        readySyncSent = false;
        readySyncScheduled = false;
        targetCache = TargetCacheEntry.empty();
        nextInitialId1AttemptAt = 0L;
        Id114NativeSink nativeLogoutSink = pendingId114LogoutSink;
        pendingId114LogoutSink = null;
        return new LoginLifecycleReset(stalePackets, nativeLogoutSink);
    }

    private void finishLoginLifecycleReset(LoginLifecycleReset reset, String reason) {
        cancelPreparedPackets(reset.stalePackets());
        resetClickState();
        state.reset();
        performId114NativeLogout(reset.nativeLogoutSink());
        trace.log("runtime-logout", null, null, Map.of("reason", reason));
    }

    private void performId114NativeLogout(Id114NativeSink sink) {
        if (sink == null) return;
        try {
            sink.logout();
            trace.log("sync-token-native-logout", MAIN_CHANNEL.toString(), 114,
                Map.of("outcome", "completed"));
        } catch (Id114NativeSink.InvocationException error) {
            trace.log("sync-token-native-logout", MAIN_CHANNEL.toString(), 114,
                Map.of("outcome", "failed", "reason", error.reason().name()));
        } catch (RuntimeException error) {
            trace.log("sync-token-native-logout", MAIN_CHANNEL.toString(), 114,
                Map.of("outcome", "failed", "reason", "UNEXPECTED_RUNTIME_FAILURE"));
        }
    }

    public void configure(String hosts, boolean traceEnabled, boolean observeOnly,
                          boolean allowLiveSend, boolean strictProviderGate) {
        configureCommon(hosts, traceEnabled, observeOnly, allowLiveSend, strictProviderGate,
            LayoutConfiguration.automatic(), OfficialRuntimeConfiguration.automatic(), false, "", false);
    }

    public void configure(String hosts, boolean traceEnabled, boolean observeOnly,
                          boolean allowLiveSend, boolean strictProviderGate,
                          String id1GameDirectory) {
        configureCommon(hosts, traceEnabled, observeOnly, allowLiveSend, strictProviderGate,
            legacyLayout(id1GameDirectory), OfficialRuntimeConfiguration.automatic(), false, "", false);
    }

    public void configure(String hosts, boolean traceEnabled, boolean observeOnly,
                          boolean allowLiveSend, boolean strictProviderGate,
                          String id1GameDirectory, boolean syntheticHwid, String syntheticHwidProfile) {
        configureCommon(hosts, traceEnabled, observeOnly, allowLiveSend, strictProviderGate,
            legacyLayout(id1GameDirectory), OfficialRuntimeConfiguration.automatic(),
            syntheticHwid, syntheticHwidProfile, false);
    }

    public void configure(String hosts, boolean traceEnabled, boolean observeOnly,
                          boolean allowLiveSend, boolean strictProviderGate,
                          String id1InstallRoot, String id1InstanceDirectory,
                          boolean syntheticHwid, String syntheticHwidProfile) {
        configureCommon(hosts, traceEnabled, observeOnly, allowLiveSend, strictProviderGate,
            explicitLayout(id1InstallRoot, id1InstanceDirectory),
            OfficialRuntimeConfiguration.automatic(), syntheticHwid, syntheticHwidProfile, false);
    }

    public void configure(String hosts, boolean traceEnabled, boolean observeOnly,
                          boolean allowLiveSend, boolean strictProviderGate,
                          String id1InstallRoot, String id1InstanceDirectory,
                          String id1OfficialUserDirectory, String id1OfficialJavaHome,
                          boolean syntheticHwid, String syntheticHwidProfile) {
        configureCommon(hosts, traceEnabled, observeOnly, allowLiveSend, strictProviderGate,
            explicitLayout(id1InstallRoot, id1InstanceDirectory),
            officialRuntime(id1OfficialUserDirectory, id1OfficialJavaHome),
            syntheticHwid, syntheticHwidProfile, false);
    }

    public void configure(String hosts, boolean traceEnabled, boolean observeOnly,
                          boolean allowLiveSend, boolean strictProviderGate,
                          String id1InstallRoot, String id1InstanceDirectory,
                          String id1OfficialUserDirectory, String id1OfficialJavaHome,
                          boolean syntheticHwid, String syntheticHwidProfile,
                          boolean id114OfficialNativeEnabled) {
        configureCommon(hosts, traceEnabled, observeOnly, allowLiveSend, strictProviderGate,
            explicitLayout(id1InstallRoot, id1InstanceDirectory),
            officialRuntime(id1OfficialUserDirectory, id1OfficialJavaHome),
            syntheticHwid, syntheticHwidProfile, id114OfficialNativeEnabled);
    }

    private void configureCommon(String hosts, boolean traceEnabled, boolean observeOnly,
                                 boolean allowLiveSend, boolean strictProviderGate,
                                 LayoutConfiguration id1Layout,
                                 OfficialRuntimeConfiguration officialRuntime,
                                 boolean syntheticHwid, String syntheticHwidProfile,
                                 boolean id114OfficialNativeEnabled) {
        String configuredHosts = hosts == null ? "" : hosts;
        HeyPixelInstallLayout resolvedNativeLayout = resolveId114NativeLayout(id1Layout);
        boolean nativeLayoutChanged =
            !Objects.equals(this.id114NativeLayout, resolvedNativeLayout);
        if (!Objects.equals(this.id1LayoutOverride, id1Layout.layout())
            || !Objects.equals(this.id1LayoutConfigurationError, id1Layout.error())
            || !Objects.equals(this.id1OfficialRuntime, officialRuntime)) {
            this.id1LayoutOverride = id1Layout.layout();
            this.id1LayoutConfigurationError = id1Layout.error();
            this.id1OfficialRuntime = officialRuntime;
            if (id1Builder == null || id1Input == null) {
                id1ContextEpoch.incrementAndGet();
                this.nextId1ContextAttemptAt = 0L;
            } else if (enabled) {
                // The official environment is a startup snapshot. Do not rescan or resend when a GUI value
                // changes mid-session; the pending configuration is applied after the next stop/start epoch.
                trace.log("id1-environment-deferred", null, null, Map.of(
                    "layoutValid", id1Layout.valid(),
                    "officialRuntimeValid", officialRuntime.valid(),
                    "officialRuntimeConfigured", officialRuntime.configured(),
                    "appliesOnNextStart", true
                ));
            }
        }
        if (nativeLayoutChanged) {
            this.id114NativeLayout = resolvedNativeLayout;
        }
        Id1HwidProvider.Settings nextHwidSettings =
            new Id1HwidProvider.Settings(syntheticHwid, syntheticHwidProfile);
        boolean hwidAppliesBeforeFirstUse = false;
        boolean hwidDeferred = false;
        synchronized (id1ContextLock) {
            if (!nextHwidSettings.equals(this.hwidSettings)) {
                this.hwidSettings = nextHwidSettings;
                if (id1ContextCanRebuildBeforeFirstUseLocked()) {
                    // The startup snapshot may already be cached while the user is still in a menu or
                    // lobby. Until an ID1 task consumes it, replace it so the next connection sees the
                    // selected hardware instead of silently retaining the previous mode.
                    id1Input = null;
                    id1Builder = null;
                    id1ContextUsed = false;
                    initialId1ScheduledAttempt = null;
                    nextInitialId1AttemptAt = 0L;
                    nextId1ContextAttemptAt = 0L;
                    id1ContextEpoch.incrementAndGet();
                    hwidAppliesBeforeFirstUse = enabled;
                } else if (enabled) {
                    hwidDeferred = true;
                }
            }
        }
        if (hwidAppliesBeforeFirstUse) {
            trace.log("id1-hwid-context-rebuild", null, null, Map.of(
                "appliesBeforeFirstUse", true,
                "syntheticHwid", nextHwidSettings.synthetic()
            ));
        } else if (hwidDeferred) {
            // Hardware settings belong to the same startup context after its first use. Applying them
            // then would let one runtime context emit two different SPRINT environments.
            trace.log("id1-hwid-deferred", null, null, Map.of(
                "appliesOnNextStart", true,
                "contextUsed", true,
                "syntheticHwid", nextHwidSettings.synthetic()
            ));
        }
        this.trace.setEnabled(traceEnabled);
        Id1TargetTransition configuredTargetTransition = Id1TargetTransition.none();
        synchronized (id1ContextLock) {
            if (!configuredHosts.equals(this.enabledHosts)) {
                this.enabledHosts = configuredHosts;
                this.targetCache = TargetCacheEntry.empty();
                Optional<Id1TargetIdentity> configuredTarget = resolveCurrentTargetIdentityLocked();
                configuredTargetTransition = recordId1TargetLocked(
                    minecraft.getConnection(), configuredTarget.orElse(null), false);
                if (configuredTargetTransition.loginReset() == null) invalidateId1TasksLocked();
            }
            this.observeOnly = observeOnly;
            this.allowLiveSend = allowLiveSend;
            this.strictProviderGate = strictProviderGate;
            this.id114OfficialNativeEnabled = id114OfficialNativeEnabled;
        }
        finishId1TargetTransition(configuredTargetTransition);
        if (nativeLayoutChanged && id114NativePreflightFrozen) {
            trace.log("sync-token-native-layout-deferred", MAIN_CHANNEL.toString(), 114,
                Map.of(
                    "reason", "STARTUP_PREFLIGHT_FROZEN",
                    "appliesOnNextJvm", true
                ));
        }
    }

    private Id114NativeSink.Availability ensureId114NativePreflight() {
        synchronized (id114NativePreflightLock) {
            if (id114NativePreflightFrozen) return id114NativePreflightAvailability;

            Id114NativeSink sink;
            if (!id114OfficialNativeEnabled) {
                sink = Id114NativeSink.unavailable(Id114NativeSink.Reason.NATIVE_DISABLED);
            } else if (minecraft != null && minecraft.getConnection() != null) {
                sink = Id114NativeSink.unavailable(
                    Id114NativeSink.Reason.STARTUP_PREFLIGHT_TOO_LATE);
            } else {
                sink = createId114NativeSink(id114NativeLayout);
            }

            Id114NativeSink.Availability availability;
            try {
                availability = Objects.requireNonNull(
                    sink.availability(),
                    "ID114 native availability"
                );
            } catch (RuntimeException | LinkageError error) {
                sink = Id114NativeSink.unavailable(Id114NativeSink.Reason.NATIVE_LOAD_FAILED);
                availability = sink.availability();
            }
            id114NativeSink = sink;
            id114NativePreflightAvailability = availability;
            id114NativePreflightFrozen = true;
            return availability;
        }
    }

    private Id114NativeSink createId114NativeSink(HeyPixelInstallLayout layout) {
        try {
            Id114NativeSink sink = id114NativeSinkFactory.create(layout);
            return sink == null
                ? Id114NativeSink.unavailable(Id114NativeSink.Reason.LAYOUT_UNAVAILABLE)
                : sink;
        } catch (RuntimeException ignored) {
            return Id114NativeSink.unavailable(Id114NativeSink.Reason.LAYOUT_UNAVAILABLE);
        }
    }

    private static HeyPixelInstallLayout resolveId114NativeLayout(
        LayoutConfiguration configuration
    ) {
        try {
            return resolveId1Layout(configuration);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static HeyPixelInstallLayout resolveId1Layout(LayoutConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (!configuration.valid()) {
            throw new IllegalStateException(configuration.error());
        }
        return configuration.layout() != null
            ? configuration.layout()
            : Id1EnvironmentCollector.resolveInstallLayout(null);
    }

    public void configureId1(
        Id1PacketBuilder builder,
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> input
    ) {
        requireCompleteId1Provider(builder, input);
        synchronized (id1ContextLock) {
            id1ContextEpoch.incrementAndGet();
            invalidateId1TasksLocked();
            this.id1Input = null;
            this.id1Builder = builder;
            this.id1Input = input;
            this.id1ContextUsed = false;
            this.nextInitialId1AttemptAt = 0L;
            this.nextId1ContextAttemptAt = 0L;
        }
    }

    public void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        String endpoint = currentEndpoint();
        Object connection = minecraft.getConnection();
        observeConnectionLifecycle(connection);
        Optional<ProxyTargetResolver.ResolvedTarget> target;
        Id1TargetTransition transition;
        List<Packet<?>> expiredPackets;
        synchronized (id1ContextLock) {
            if (!enabled) return;
            expiredPackets = expirePendingWritesLocked(now);
            observeLocalPlayerLifecycleLocked();
            target = resolveTarget(endpoint, false);
            transition = recordId1TargetLocked(
                connection,
                target.map(Id1TargetIdentity::from).orElse(null),
                true
            );
        }
        cancelPreparedPackets(expiredPackets);
        finishId1TargetTransition(transition);
        boolean connectionChanged = transition.connectionChanged();
        String host = target.map(ProxyTargetResolver.ResolvedTarget::connectionHost)
            .orElseGet(() -> ProtocolSessionProvider.normalizeHost(endpoint));
        String targetHost = target.map(ProxyTargetResolver.ResolvedTarget::targetHost).orElse("");
        boolean endpointChanged = !host.equals(lastHost) || !endpoint.equals(lastEndpoint);
        if (endpointChanged || connectionChanged) {
            trace.log("server-change", null, null, Map.of(
                "host", host,
                "connectionEndpoint", target.map(ProxyTargetResolver.ResolvedTarget::connectionEndpoint)
                    .orElse(endpoint),
                "connectionPort", target.map(ProxyTargetResolver.ResolvedTarget::connectionPort).orElse(-1),
                "targetHost", targetHost,
                "targetPort", target.map(ProxyTargetResolver.ResolvedTarget::targetPort).orElse(-1),
                "target", target.isPresent(),
                "proxied", target.map(ProxyTargetResolver.ResolvedTarget::proxied).orElse(false)
            ));
            lastHost = host;
            lastEndpoint = endpoint;
        }
        if (target.isEmpty() || minecraft.level == null || minecraft.player == null) return;
        refreshLocalUuid();
        tickInitialId1(connection, target.get());
    }

    /** Mirrors the official client-tick END phase used by the independent ready one-shot. */
    public void tickReadyPhase() {
        if (!enabled) return;
        Object connection = minecraft.getConnection();
        observeConnectionLifecycle(connection);
        Optional<ProxyTargetResolver.ResolvedTarget> target;
        Id1TargetTransition transition;
        synchronized (id1ContextLock) {
            if (!enabled) return;
            target = resolveTarget(currentEndpoint(), false);
            transition = recordId1TargetLocked(
                connection,
                target.map(Id1TargetIdentity::from).orElse(null),
                true
            );
        }
        finishId1TargetTransition(transition);
        if (target.isPresent()) tickReadySync(connection, target.get());
    }

    public boolean handle(ClientboundCustomPayloadPacket packet) {
        return handle(packet, currentTransportConnection());
    }

    public boolean handle(ClientboundCustomPayloadPacket packet, Connection sourceConnection) {
        if (!enabled || packet == null) return false;
        ResourceLocation channel = packet.getIdentifier();
        if (!isSupportedChannel(channel)) return false;
        boolean mainChannel = MAIN_CHANNEL.equals(channel);
        refreshLocalUuid();
        Object lifecycleConnection = minecraft.getConnection();
        observeConnectionLifecycle(lifecycleConnection);
        Runnable deferredAction = null;
        Id1TargetTransition transition = Id1TargetTransition.none();
        boolean handled = mainChannel;
        synchronized (id1ContextLock) {
            if (sourceConnection == null || sourceConnection != currentTransportConnection()) {
                return mainChannel;
            }
            Optional<ProxyTargetResolver.ResolvedTarget> target =
                resolveTarget(currentEndpoint(), true);
            Id1TargetIdentity targetIdentity = target.map(Id1TargetIdentity::from).orElse(null);
            transition = recordId1TargetLocked(lifecycleConnection, targetIdentity, true);
            if (target.isEmpty() || lifecycleConnection != minecraft.getConnection()
                || sourceConnection != currentTransportConnection()) {
                handled = mainChannel;
            } else {
                FriendlyByteBuf data = packet.getData();
                byte[] bytes = new byte[data.readableBytes()];
                data.getBytes(data.readerIndex(), bytes);
                if (!MAIN_CHANNEL.equals(channel)) {
                    trace.log("s2c-channel", channel.toString(), null,
                        Map.of("length", bytes.length));
                    handled = false;
                } else {
                    Id1IncomingContext incoming = new Id1IncomingContext(
                        id1LifecycleGeneration.get(),
                        lifecycleConnection,
                        sourceConnection,
                        targetIdentity,
                        target.get().session()
                    );
                    deferredAction = handleMainChannel(bytes, incoming);
                    handled = true;
                }
            }
        }
        finishId1TargetTransition(transition);
        if (deferredAction != null) deferredAction.run();
        return handled;
    }

    public boolean sendBusinessPacket(byte[] wire, int packetId, String trigger) {
        return sendBusinessPacket(wire, packetId, trigger, null, PendingWriteKind.BUSINESS);
    }

    private boolean sendBusinessPacket(
        byte[] wire,
        int packetId,
        String trigger,
        Id1TaskSnapshot id1Task,
        PendingWriteKind writeKind
    ) {
        return sendBusinessPacket(wire, packetId, trigger, id1Task, writeKind, true);
    }

    private boolean sendBusinessPacket(
        byte[] wire,
        int packetId,
        String trigger,
        Id1TaskSnapshot id1Task,
        PendingWriteKind writeKind,
        boolean requireProviderSession
    ) {
        return sendBusinessPacket(
            wire,
            packetId,
            trigger,
            id1Task,
            writeKind,
            requireProviderSession,
            ANY_ID2_HEARTBEAT_GENERATION
        );
    }

    private boolean sendBusinessPacket(
        byte[] wire,
        int packetId,
        String trigger,
        Id1TaskSnapshot id1Task,
        PendingWriteKind writeKind,
        boolean requireProviderSession,
        long requiredHeartbeatGeneration
    ) {
        if (wire == null) return false;
        String semantic = HeyPixelPacketSemantics.canonicalName(
            HeyPixelPacketSemantics.Direction.C2S, packetId);
        byte[] channelWire = HeyPixelOuterBridge.wrapBinary(wire);
        ServerboundCustomPayloadPacket outbound = null;
        ClientPacketListener connectionToSend = null;
        PendingWrite pending = null;
        Id1TargetTransition transition = Id1TargetTransition.none();
        boolean lifecycleBlocked = false;
        synchronized (id1ContextLock) {
            if (requiredHeartbeatGeneration != ANY_ID2_HEARTBEAT_GENERATION
                && !heartbeatGenerationIsCurrent(
                    enabled, requiredHeartbeatGeneration, id2HeartbeatGeneration)) {
                return false;
            }
            Optional<ProxyTargetResolver.ResolvedTarget> target =
                resolveTarget(currentEndpoint(), true);
            Id1TargetIdentity targetIdentity = target.map(Id1TargetIdentity::from).orElse(null);
            transition = recordId1TargetLocked(
                minecraft.getConnection(), targetIdentity, true);
            if (!enabled || targetIdentity == null || transition.changed()) {
                lifecycleBlocked = true;
            } else {
                if (observeOnly || !allowLiveSend) {
                    trace.log("c2s-blocked", MAIN_CHANNEL.toString(), packetId,
                        Map.of(
                            "reason", observeOnly ? "observe-only" : "live-send-disabled",
                            "trigger", trigger,
                            "semantic", semantic
                        ));
                    return false;
                }
                if (requireProviderSession && strictProviderGate
                    && target.orElseThrow().session().isEmpty()) {
                    trace.log("c2s-blocked", MAIN_CHANNEL.toString(), packetId,
                        Map.of(
                            "reason", "session-provider-unavailable",
                            "trigger", trigger,
                            "semantic", semantic
                        ));
                    return false;
                }
                connectionToSend = minecraft.getConnection();
                if (connectionToSend == null) return false;
                if (id1Task != null && (connectionToSend != id1Task.connection()
                    || !isCurrentId1TaskLocked(id1Task, targetIdentity))) {
                    return false;
                }
                FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(channelWire));
                outbound = new ServerboundCustomPayloadPacket(MAIN_CHANNEL, payload);
                pending = new PendingWrite(
                    writeKind,
                    id1LifecycleGeneration.get(),
                    connectionToSend,
                    targetIdentity,
                    id1Task,
                    System.currentTimeMillis(),
                    packetId,
                    trigger
                );
                pendingWrites.reserve(outbound, pending);
            }
        }

        finishId1TargetTransition(transition);
        if (lifecycleBlocked) return false;
        if (!dispatchReservedPacket(outbound, connectionToSend, pending)) return false;
        trace.log("c2s-send", MAIN_CHANNEL.toString(), packetId,
            Map.of(
                "length", channelWire.length,
                "businessLength", wire.length,
                "trigger", trigger,
                "semantic", semantic
            ));
        return true;
    }

    public boolean isActiveForCurrentServer() {
        return enabled && hasCurrentTarget(false);
    }

    public boolean isRunning() {
        return enabled;
    }

    public HeyPixelProtocolState state() {
        return state;
    }

    public void recordMouseButton(int button, int action) {
        if (!enabled || action != 1 || (button != 0 && button != 1)
            || !hasCurrentTarget(false)) {
            return;
        }
        synchronized (clickLock) {
            (button == 0 ? leftClicks : rightClicks).addLast(
                Id2CachedClock.currentTimeMillis());
        }
    }

    public void sendUseBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (!enabled || player == null || hand == null || hit == null
            || !hasCurrentTarget(false)) {
            return;
        }
        Vec3 playerPosition = player.position();
        Vec3 hitPosition = hit.getLocation();
        C2SPacketEncoders.Id5UseBlock packet = new C2SPacketEncoders.Id5UseBlock(
            System.currentTimeMillis(),
            playerPosition.x, playerPosition.y, playerPosition.z,
            hit.getDirection().ordinal(), hit.getType().ordinal(),
            hitPosition.x, hitPosition.y, hitPosition.z,
            hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ(),
            hit.isInside(), player.getYRot(), player.getXRot(), hand == InteractionHand.MAIN_HAND
        );
        sendBusinessPacket(C2SPacketEncoders.encodeUseBlockTelemetry(packet), 5, "USE_BLOCK");
    }

    private Runnable handleMainChannel(byte[] wire, Id1IncomingContext incoming) {
        try {
            if (!isCurrentIncomingLocked(incoming)) return null;
            if (wire.length > 0 && (wire[0] & 0xff) == S2CPacketDecoders.JSON_EVENT_DISCRIMINATOR) {
                String json = new String(wire, 1, wire.length - 1, StandardCharsets.UTF_8);
                trace.log("s2c-json-event", MAIN_CHANNEL.toString(), null,
                    Map.of("length", json.length()));
                return null;
            }
            S2CPayloadUnwrapper.UnwrappedPacket packet = S2CPayloadUnwrapper.unwrap(wire, payloadCrypto);
            if (!isCurrentIncomingLocked(incoming)) return null;
            if (packet.packetId() == 114) {
                return prepareSyncTokenWork(packet, incoming);
            }
            HeyPixelProtocolDispatcher.CanonicalDispatchResult decoded =
                dispatcher.decodeCanonical(packet.packetId(), packet.payload());
            if (decoded.kind() == HeyPixelProtocolDispatcher.CanonicalKind.UNIMPLEMENTED) {
                traceS2cDispatch(decoded, packet);
                return null;
            }
            enqueueClientPacketWork(
                decoded,
                packet.encrypted(),
                packet.trailingBytes(),
                incoming
            );
            return null;
        } catch (RuntimeException error) {
            S2cFailure failure = inspectS2cFailure(wire, error);
            trace.log("s2c-error", MAIN_CHANNEL.toString(), failure.packetId(), failure.details());
            return null;
        }
    }

    private void enqueueClientPacketWork(
        HeyPixelProtocolDispatcher.CanonicalDispatchResult decoded,
        boolean encrypted,
        int trailingBytes,
        Id1IncomingContext incoming
    ) {
        int packetId = decoded.packetId();
        try {
            ClientLogicalWork.enqueue(
                minecraft::execute,
                id1ContextLock,
                () -> isCurrentIncomingLocked(incoming),
                () -> minecraft.player != null,
                () -> dispatcher.applyCanonical(decoded),
                () -> {
                },
                result -> completeClientPacketWork(
                    result,
                    packetId,
                    encrypted,
                    trailingBytes,
                    incoming
                ),
                error -> trace.log("s2c-work-error", MAIN_CHANNEL.toString(), packetId,
                    Map.of("errorType", error.getClass().getSimpleName()))
            );
        } catch (RuntimeException error) {
            trace.log("s2c-submit-error", MAIN_CHANNEL.toString(), packetId,
                Map.of("errorType", error.getClass().getSimpleName()));
        }
    }

    private void completeClientPacketWork(
        ClientLogicalWork.Result<HeyPixelProtocolDispatcher.CanonicalDispatchResult> result,
        int packetId,
        boolean encrypted,
        int trailingBytes,
        Id1IncomingContext incoming
    ) {
        if (result.outcome() == ClientLogicalWork.Outcome.STALE_GENERATION) {
            trace.log("s2c-dropped", MAIN_CHANNEL.toString(), packetId,
                Map.of("reason", "stale-generation", "retry", false));
            return;
        }
        if (result.outcome() == ClientLogicalWork.Outcome.PLAYER_UNAVAILABLE) {
            trace.log("s2c-dropped", MAIN_CHANNEL.toString(), packetId,
                Map.of("reason", "player-unavailable", "retry", false));
            return;
        }

        HeyPixelProtocolDispatcher.CanonicalDispatchResult applied = result.value();
        traceS2cDispatch(applied, encrypted, trailingBytes);
        if (applied.kind() == HeyPixelProtocolDispatcher.CanonicalKind.ENVIRONMENT_CHALLENGE) {
            handleChallenge((S2CPacketDecoders.Id101Challenge) applied.value(), incoming);
        }
    }

    private void handleChallenge(
        S2CPacketDecoders.Id101Challenge challenge,
        Id1IncomingContext incoming
    ) {
        trace.log("id101-challenge", MAIN_CHANNEL.toString(), 101,
            Map.of("subtype", challenge.subtypeName(), "packetLong", challenge.packetLong()));
        if (observeOnly || !allowLiveSend) return;
        Id1TaskDispatch dispatch = snapshotId1Dispatch(incoming);
        if (dispatch == null) {
            trace.log("id1-provider-blocked", MAIN_CHANNEL.toString(), 1,
                Map.of("session", incoming.session().isPresent(), "builder", id1Builder != null,
                    "input", id1Input != null, "strict", strictProviderGate,
                    "connection", incoming.connection() == minecraft.getConnection()));
            return;
        }
        id1Executor.execute(() -> buildAndSendId1(
            challenge, dispatch.session(), dispatch.task()));
    }

    private void buildAndSendId1(
        S2CPacketDecoders.Id101Challenge challenge,
        ProtocolSessionSnapshot session,
        Id1TaskSnapshot task
    ) {
        try {
            requireCurrentId1Task(task);
            Id1BuildInput input = task.inputProvider().apply(challenge, session);
            if (input.subtype() != challenge.subtype()) {
                throw new IllegalArgumentException("ID1 input subtype does not match ID101 challenge");
            }
            Id1PacketBuilder.Challenge request = new Id1PacketBuilder.Challenge(
                challenge.packetUuid(), challenge.packetLong(), challenge.subtype(), challenge.challengeValue());
            byte[] wire = task.builder().buildPacket(request, input.context(), input.subtypePayload());
            requireCurrentId1Task(task);
            if (!sendBusinessPacket(wire, 1, "S2C_ID101", task, PendingWriteKind.ID1_RESPONSE)) {
                throw new IllegalStateException("ID1 task became stale before send");
            }
        } catch (RuntimeException error) {
            trace.log("id1-build-error", MAIN_CHANNEL.toString(), 1,
                Map.of(
                    "error", error.getClass().getSimpleName(),
                    "reason", classifyId1BuildFailure(error),
                    "message", String.valueOf(error.getMessage())
                ));
        }
    }

    private void tickInitialId1(
        Object connection,
        ProxyTargetResolver.ResolvedTarget target
    ) {
        long now = System.currentTimeMillis();
        Id1TaskSnapshot task;
        ProtocolSessionSnapshot session;
        boolean uuidReady;
        boolean builderReady;
        boolean inputReady;
        synchronized (id1ContextLock) {
            if (initialId1Submitted) {
                localPlayerJoinPending = false;
                return;
            }
            if (!enabled || observeOnly || !allowLiveSend || connection == null
                || !localPlayerJoinPending || observedLocalPlayer == null
                || observedLocalPlayer != minecraft.player
                || now < nextInitialId1AttemptAt) {
                return;
            }
            Id1TargetIdentity targetIdentity = Id1TargetIdentity.from(target);
            if (connection != lastConnection || !targetIdentity.equals(lastTargetIdentity)) return;
            Optional<ProtocolSessionSnapshot> resolvedSession = target.session();
            session = resolvedSession.orElse(null);
            UUID uuid = localUuid;
            Id1PacketBuilder builder = id1Builder;
            BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> inputProvider =
                id1Input;
            uuidReady = uuid != null;
            builderReady = builder != null;
            inputReady = inputProvider != null;
            if (session == null || !uuidReady || !builderReady || !inputReady) {
                nextInitialId1AttemptAt = now + INITIAL_ID1_RETRY_INTERVAL_MILLIS;
                task = null;
            } else {
                long generation = id1LifecycleGeneration.get();
                Id1InitialAttempt attempt =
                    new Id1InitialAttempt(generation, connection, targetIdentity);
                if (attempt.matches(initialId1ScheduledAttempt)) {
                    return;
                }
                task = new Id1TaskSnapshot(
                    generation,
                    connection,
                    uuid,
                    builder,
                    inputProvider,
                    targetIdentity
                );
                id1ContextUsed = true;
                initialId1ScheduledAttempt = attempt;
            }
        }
        if (task == null) {
            trace.log("id1-initial-provider-wait", MAIN_CHANNEL.toString(), 1, Map.of(
                "session", session != null,
                "uuid", uuidReady,
                "builder", builderReady,
                "input", inputReady
            ));
            return;
        }

        trace.log("id1-initial-scheduled", MAIN_CHANNEL.toString(), 1,
            Map.of(
                "subtype", Id1PacketBuilder.Id1Subtype.SPRINT.name(),
                "timestampSource", "id1-worker"
            ));
        try {
            id1Executor.execute(() -> buildAndSendInitialId1(session, task));
        } catch (RuntimeException error) {
            failInitialId1Attempt(task);
            throw error;
        }
    }

    private void buildAndSendInitialId1(
        ProtocolSessionSnapshot session,
        Id1TaskSnapshot task
    ) {
        boolean sent = false;
        try {
            requireCurrentId1Task(task);
            long packetLong = System.currentTimeMillis();
            S2CPacketDecoders.Id101Challenge request = new S2CPacketDecoders.Id101Challenge(
                task.localUuid(), packetLong, Id1PacketBuilder.Id1Subtype.SPRINT, null);
            Id1BuildInput input = task.inputProvider().apply(request, session);
            if (input.subtype() != Id1PacketBuilder.Id1Subtype.SPRINT) {
                throw new IllegalArgumentException("Initial ID1 input must use the SPRINT subtype");
            }
            Id1PacketBuilder.Challenge challenge = new Id1PacketBuilder.Challenge(
                task.localUuid(), request.packetLong(), Id1PacketBuilder.Id1Subtype.SPRINT, null);
            Id1PacketBuilder.SprintEnvironment environment =
                (Id1PacketBuilder.SprintEnvironment) input.subtypePayload();
            Id1PacketBuilder.BuiltPacket packet = task.builder().buildInitialSprint(
                challenge,
                input.context(),
                environment
            );
            byte[] wire = packet.wire();
            trace.log("id1-initial-built", MAIN_CHANNEL.toString(), 1, initialId1Details(
                task.localUuid(), input.context(), environment, packet, session));
            requireCurrentId1Task(task);
            sent = sendBusinessPacket(
                wire, 1, "JOIN_INITIAL_SPRINT", task, PendingWriteKind.INITIAL_ID1);
        } catch (RuntimeException error) {
            trace.log("id1-initial-build-error", MAIN_CHANNEL.toString(), 1,
                Map.of(
                    "error", error.getClass().getSimpleName(),
                    "reason", classifyId1BuildFailure(error),
                    "message", String.valueOf(error.getMessage())
                ));
        } finally {
            if (!sent) failInitialId1Attempt(task);
        }
    }

    private Runnable prepareSyncTokenWork(
        S2CPayloadUnwrapper.UnwrappedPacket packet,
        Id1IncomingContext incoming
    ) {
        Id114TokenLease lease =
            S2CPacketDecoders.decodeSyncTokenLeaseOfficialPrefix(packet.payload());
        SyncTokenMetadata metadata = lease.metadata();
        Id114IncomingGeneration generation = Id114IncomingGeneration.from(incoming);
        Id114NativeSink nativeSink = id114NativeSink;
        HeyPixelProtocolDispatcher.CanonicalDispatchResult result =
            new HeyPixelProtocolDispatcher.CanonicalDispatchResult(
                114,
                HeyPixelProtocolDispatcher.CanonicalKind.SYNC_TOKEN,
                packet.payload().length,
                metadata
            );
        boolean encrypted = packet.encrypted();
        int trailingBytes = packet.trailingBytes();
        pendingId114TokenLeases.add(lease);
        return () -> {
            try {
                Id114ClientWork.enqueue(
                    minecraft::execute,
                    id1ContextLock,
                    () -> isCurrentId114GenerationLocked(generation),
                    () -> minecraft.player != null,
                    this::isId114NativeAllowedLocked,
                    nativeSink,
                    lease,
                    state::setSyncTokenMetadata,
                    invokedSink -> registerPendingId114LogoutLocked(generation, invokedSink),
                    () -> {
                        traceS2cDispatch(result, encrypted, trailingBytes);
                        trace.log("sync-token-work-start", MAIN_CHANNEL.toString(), 114,
                            Map.of("execution", "client-logical-work"));
                    },
                    outcome -> {
                        releaseId114TokenLease(lease);
                        traceSyncTokenOutcome(outcome, metadata);
                    },
                    error -> {
                        releaseId114TokenLease(lease);
                        trace.log("sync-token-work-error", MAIN_CHANNEL.toString(), 114,
                            Map.of("errorType", error.getClass().getSimpleName()));
                    }
                );
            } catch (RuntimeException error) {
                releaseId114TokenLease(lease);
                trace.log("sync-token-submit-error", MAIN_CHANNEL.toString(), 114,
                    Map.of("errorType", error.getClass().getSimpleName()));
            }
        };
    }

    private void releaseId114TokenLease(Id114TokenLease lease) {
        lease.clear();
        synchronized (id1ContextLock) {
            pendingId114TokenLeases.remove(lease);
        }
    }

    private void traceS2cDispatch(
        HeyPixelProtocolDispatcher.CanonicalDispatchResult result,
        S2CPayloadUnwrapper.UnwrappedPacket packet
    ) {
        traceS2cDispatch(result, packet.encrypted(), packet.trailingBytes());
    }

    private void traceS2cDispatch(
        HeyPixelProtocolDispatcher.CanonicalDispatchResult result,
        boolean encrypted,
        int trailingBytes
    ) {
        trace.log("s2c-dispatch", MAIN_CHANNEL.toString(), result.packetId(),
            Map.of(
                "length", result.payloadLength(),
                "kind", result.kind().name(),
                "semantic", HeyPixelPacketSemantics.canonicalName(
                    HeyPixelPacketSemantics.Direction.S2C, result.packetId()),
                "encrypted", encrypted,
                "trailingBytes", trailingBytes
            ));
    }

    private void traceSyncTokenOutcome(Id114ClientWork.Result result, SyncTokenMetadata metadata) {
        Id114ClientWork.Outcome outcome = result.outcome();
        switch (outcome) {
            case ACCEPTED -> trace.log("sync-token-cached", MAIN_CHANNEL.toString(), 114,
                syncTokenTraceDetails(metadata.withNativeSinkAvailable(true), result));
            case NATIVE_ACCEPT_INVOKED_UNVERIFIED -> trace.log(
                "sync-token-native-unverified",
                MAIN_CHANNEL.toString(),
                114,
                syncTokenTraceDetails(metadata.withNativeSinkAvailable(false), result)
            );
            case METADATA_ONLY_NATIVE_DISABLED, METADATA_ONLY_NATIVE_UNAVAILABLE ->
                trace.log("sync-token-cached", MAIN_CHANNEL.toString(), 114,
                    syncTokenTraceDetails(metadata.withNativeSinkAvailable(false), result));
            case STALE_GENERATION -> trace.log(
                "sync-token-dropped", MAIN_CHANNEL.toString(), 114,
                Map.of("reason", "stale-generation"));
            case PLAYER_UNAVAILABLE -> trace.log(
                "sync-token-dropped", MAIN_CHANNEL.toString(), 114,
                Map.of("reason", "player-unavailable", "retry", false));
            case DUPLICATE_WORK -> trace.log(
                "sync-token-dropped", MAIN_CHANNEL.toString(), 114,
                Map.of("reason", "duplicate-client-work", "retry", false));
            case NATIVE_ACCEPT_FAILED, COMPENSATING_LOGOUT_FAILED -> trace.log(
                "sync-token-native-failed", MAIN_CHANNEL.toString(), 114,
                Map.of(
                    "outcome", outcome.name(),
                    "reason", result.nativeReason() == null
                        ? "UNSPECIFIED" : result.nativeReason().name()
                ));
            case STALE_AFTER_NATIVE_ACCEPT, PLAYER_UNAVAILABLE_AFTER_NATIVE_ACCEPT,
                 NATIVE_DISABLED_AFTER_ACCEPT -> trace.log(
                "sync-token-native-cleanup", MAIN_CHANNEL.toString(), 114,
                Map.of("outcome", outcome.name(), "logoutAttempted", true));
        }
    }

    private static Map<String, Object> syncTokenTraceDetails(
        SyncTokenMetadata metadata,
        Id114ClientWork.Result result
    ) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(metadata.traceDetails());
        details.put("nativeOutcome", result.outcome().name());
        if (result.nativeReason() != null) {
            details.put("nativeReason", result.nativeReason().name());
        }
        return Map.copyOf(details);
    }

    private boolean isId114NativeAllowedLocked() {
        return enabled && id114OfficialNativeEnabled && !observeOnly && allowLiveSend;
    }

    private void registerPendingId114LogoutLocked(
        Id114IncomingGeneration generation,
        Id114NativeSink sink
    ) {
        if (!isCurrentId114GenerationLocked(generation)) {
            throw new IllegalStateException("ID114 generation changed before native commit");
        }
        pendingId114LogoutSink = Objects.requireNonNull(sink, "sink");
    }

    private void failInitialId1Attempt(Id1TaskSnapshot task) {
        synchronized (id1ContextLock) {
            Id1InitialAttempt attempt = Id1InitialAttempt.from(task);
            if (!attempt.matches(initialId1ScheduledAttempt)) return;
            boolean current = isCurrentId1TaskLocked(task);
            if (current) {
                nextInitialId1AttemptAt =
                    System.currentTimeMillis() + INITIAL_ID1_RETRY_INTERVAL_MILLIS;
            }
            initialId1ScheduledAttempt = null;
        }
    }

    private boolean commitInitialId1AttemptLocked(
        Id1TaskSnapshot task,
        Id1TargetIdentity targetIdentity
    ) {
        Id1InitialAttempt attempt = Id1InitialAttempt.from(task);
        if (!attempt.matches(initialId1ScheduledAttempt)
            || !isCurrentId1TaskLocked(task, targetIdentity)) {
            return false;
        }
        initialId1Submitted = true;
        initialId1ScheduledAttempt = null;
        localPlayerJoinPending = false;
        return true;
    }

    private boolean dispatchReservedPacket(
        ServerboundCustomPayloadPacket outbound,
        ClientPacketListener connectionToSend,
        PendingWrite pending
    ) {
        PacketUtil.prepareDirectSend(outbound);
        RuntimeException failure = null;
        synchronized (id1ContextLock) {
            if (!isCurrentPendingWriteLocked(outbound, pending)) {
                pendingWrites.complete(outbound);
                PacketUtil.cancelDirectSend(outbound);
                failPendingWriteLocked(pending, System.currentTimeMillis());
                return false;
            }
            try {
                // This invocation is the dispatch linearization point. The direct marker prevents
                // arbitrary module listeners from cancelling protocol infrastructure packets.
                connectionToSend.send(outbound);
            } catch (RuntimeException error) {
                failure = error;
                PacketUtil.cancelDirectSend(outbound);
                PendingWrite removed = pendingWrites.complete(outbound);
                if (removed != null) failPendingWriteLocked(removed, System.currentTimeMillis());
            }
        }
        if (failure != null) {
            trace.log("c2s-send-error", MAIN_CHANNEL.toString(), pending.packetId(),
                Map.of(
                    "trigger", pending.trigger(),
                    "error", failure.getClass().getSimpleName()
                ));
            return false;
        }
        return true;
    }

    /** Commits one-shot lifecycle state only for the exact packet object that reached writeAndFlush. */
    public void onFinalPacketWrite(Packet<?> packet) {
        if (packet == null) return;
        PacketUtil.cancelDirectSend(packet);
        PendingWrite pending;
        boolean committed = false;
        synchronized (id1ContextLock) {
            pending = pendingWrites.complete(packet);
            if (pending == null) return;
            if (!isCurrentPendingWriteLocked(pending)) {
                failPendingWriteLocked(pending, System.currentTimeMillis());
                return;
            }
            switch (pending.kind()) {
                case INITIAL_ID1 -> {
                    committed = pending.id1Task() != null
                        && commitInitialId1AttemptLocked(
                            pending.id1Task(), pending.targetIdentity());
                    if (!committed) failPendingWriteLocked(pending, System.currentTimeMillis());
                }
                case READY_SYNC -> {
                    readySyncScheduled = false;
                    readySyncSent = true;
                    committed = true;
                }
                case ID1_RESPONSE, BUSINESS -> committed = true;
            }
        }
        if (!committed) return;
        if (pending.kind() == PendingWriteKind.INITIAL_ID1) {
            trace.log("id1-initial-complete", MAIN_CHANNEL.toString(), 1,
                Map.of("commitPoint", "final-write"));
        }
        trace.log("c2s-final-write", MAIN_CHANNEL.toString(),
            pending.packetId() < 0 ? null : pending.packetId(),
            Map.of("trigger", pending.trigger(), "kind", pending.kind().name()));
        Integer requestPacketId = pending.kind().responseRequestPacketId();
        if (requestPacketId != null) {
            trace.logPacketResponse(
                MAIN_CHANNEL.toString(),
                requestPacketId,
                pending.packetId(),
                pending.trigger(),
                "final-write"
            );
        }
    }

    private boolean isCurrentPendingWriteLocked(
        Packet<?> packet,
        PendingWrite expected
    ) {
        return pendingWrites.get(packet) == expected && isCurrentPendingWriteLocked(expected);
    }

    private boolean isCurrentPendingWriteLocked(PendingWrite pending) {
        return pending != null
            && enabled
            && pending.generation() == id1LifecycleGeneration.get()
            && pending.connection() == minecraft.getConnection()
            && pending.connection() == lastConnection
            && pending.targetIdentity().equals(lastTargetIdentity);
    }

    private void failPendingWriteLocked(PendingWrite pending, long now) {
        if (pending == null) return;
        if (pending.kind() == PendingWriteKind.READY_SYNC) {
            readySyncScheduled = false;
        } else if (pending.kind() == PendingWriteKind.INITIAL_ID1
            && pending.id1Task() != null) {
            Id1InitialAttempt attempt = Id1InitialAttempt.from(pending.id1Task());
            if (attempt.matches(initialId1ScheduledAttempt)) {
                initialId1ScheduledAttempt = null;
                if (enabled) nextInitialId1AttemptAt = now + INITIAL_ID1_RETRY_INTERVAL_MILLIS;
            }
        }
    }

    private List<Packet<?>> expirePendingWritesLocked(long now) {
        List<Packet<?>> expiredPackets = new ArrayList<>();
        for (FinalWriteLedger.Entry<PendingWrite> entry : pendingWrites.removeIf(
            pending -> pendingWriteExpired(
                pending.kind(), pending.createdAt(), now, PENDING_WRITE_TIMEOUT_MILLIS))) {
            if (entry.key() instanceof Packet<?> packet) expiredPackets.add(packet);
            failPendingWriteLocked(entry.value(), now);
        }
        return expiredPackets;
    }

    private List<Packet<?>> clearPendingWritesLocked() {
        List<Packet<?>> packets = new ArrayList<>();
        for (FinalWriteLedger.Entry<PendingWrite> entry : pendingWrites.clear()) {
            if (entry.key() instanceof Packet<?> packet) packets.add(packet);
            if (entry.value().kind() == PendingWriteKind.READY_SYNC) readySyncScheduled = false;
        }
        return packets;
    }

    private static void cancelPreparedPackets(List<Packet<?>> packets) {
        if (packets == null) return;
        for (Packet<?> packet : packets) PacketUtil.cancelDirectSend(packet);
    }

    static boolean pendingWriteExpired(
        PendingWriteKind kind,
        long createdAt,
        long now,
        long timeout
    ) {
        Objects.requireNonNull(kind, "kind");
        if (timeout < 0L) throw new IllegalArgumentException("timeout must not be negative");
        return !kind.lifecycleOneShot()
            && now >= createdAt
            && now - createdAt >= timeout;
    }

    private void runId2Heartbeat(long heartbeatGeneration) {
        synchronized (id1ContextLock) {
            if (!heartbeatGenerationIsCurrent(
                    enabled, heartbeatGeneration, id2HeartbeatGeneration)
                || observeOnly || !allowLiveSend) {
                return;
            }
        }
        // The official runnable gates only on LocalPlayer and ClientLevel availability.
        if (minecraft.player == null || minecraft.level == null) return;
        long writerTime = System.currentTimeMillis();
        long cachedClockMillis = Id2CachedClock.currentTimeMillis();
        sendBusinessPacket(
            C2SPacketEncoders.encodeHeartbeat(writerTime, cachedClockMillis),
            2,
            "FIXED_RATE_5000MS",
            null,
            PendingWriteKind.BUSINESS,
            false,
            heartbeatGeneration
        );
    }

    static boolean heartbeatGenerationIsCurrent(
        boolean enabled,
        long requiredGeneration,
        long currentGeneration
    ) {
        return enabled && requiredGeneration == currentGeneration;
    }

    private void tickReadySync(
        Object connection,
        ProxyTargetResolver.ResolvedTarget target
    ) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null
            || minecraft.screen instanceof ReceivingLevelScreen) {
            return;
        }

        byte[] wire;
        ServerboundCustomPayloadPacket outbound;
        ClientPacketListener connectionToSend;
        PendingWrite pending;
        synchronized (id1ContextLock) {
            Id1TargetIdentity targetIdentity = Id1TargetIdentity.from(target);
            connectionToSend = minecraft.getConnection();
            if (!enabled || observeOnly || !allowLiveSend
                || !readySyncOneShotReady(
                    initialId1Submitted, readySyncSent, readySyncScheduled)
                || connectionToSend == null || connectionToSend != connection
                || connectionToSend != lastConnection || !targetIdentity.equals(lastTargetIdentity)) {
                return;
            }
            wire = encodeReadySyncPayload();
            outbound = new ServerboundCustomPayloadPacket(
                MAIN_CHANNEL,
                new FriendlyByteBuf(Unpooled.wrappedBuffer(wire))
            );
            pending = new PendingWrite(
                PendingWriteKind.READY_SYNC,
                id1LifecycleGeneration.get(),
                connectionToSend,
                targetIdentity,
                null,
                System.currentTimeMillis(),
                -1,
                "READY_SYNC_OPCODE_12"
            );
            pendingWrites.reserve(outbound, pending);
            readySyncScheduled = true;
        }
        if (dispatchReservedPacket(outbound, connectionToSend, pending)) {
            trace.log("ready-sync-send", MAIN_CHANNEL.toString(), null,
                Map.of("opcode", READY_SYNC_OPCODE, "length", wire.length));
        }
    }

    static byte[] encodeReadySyncPayload() {
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.buffer(1));
        try {
            payload.writeVarInt(READY_SYNC_OPCODE);
            byte[] wire = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), wire);
            return wire;
        } finally {
            payload.release();
        }
    }

    /** Mirrors the official phase-agnostic ClientTickEvent ID3 producer. */
    public void tickId3Phase() {
        if (!enabled || observeOnly || !allowLiveSend || !hasCurrentTarget(false)) return;
        long now = Id2CachedClock.currentTimeMillis();
        if (now < nextId3SendAt || minecraft.screen != null) return;

        int leftCps;
        int rightCps;
        synchronized (clickLock) {
            discardClicksBefore(leftClicks, now - ID3_CPS_WINDOW_MILLIS);
            discardClicksBefore(rightClicks, now - ID3_CPS_WINDOW_MILLIS);
            leftCps = leftClicks.size();
            rightCps = rightClicks.size();
        }
        if (leftCps == lastId3LeftCps && rightCps == lastId3RightCps) return;

        lastId3LeftCps = leftCps;
        lastId3RightCps = rightCps;
        nextId3SendAt = now + ID3_SEND_COOLDOWN_MILLIS;
        // The official packet state advances before its base send applies the player/level gate.
        if (minecraft.player == null || minecraft.level == null) return;
        sendBusinessPacket(C2SPacketEncoders.encodeCpsTelemetry(
            System.currentTimeMillis(), leftCps, rightCps), 3, "CPS_CHANGE");
    }

    private void resetClickState() {
        synchronized (clickLock) {
            leftClicks.clear();
            rightClicks.clear();
        }
        nextId3SendAt = 0L;
        lastId3LeftCps = 0;
        lastId3RightCps = 0;
    }

    private static void discardClicksBefore(Deque<Long> clicks, long cutoff) {
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.removeFirst();
    }

    private boolean isSupportedChannel(ResourceLocation channel) {
        return MAIN_CHANNEL.equals(channel) || SKIN_CHANNEL.equals(channel)
            || FORM_CHANNEL.equals(channel) || NETEASE_CHANNEL.equals(channel);
    }

    private static ResourceLocation channel(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value), "invalid channel: " + value);
    }

    private Optional<ProxyTargetResolver.ResolvedTarget> resolveTarget(String endpoint, boolean refresh) {
        String normalized = ProxyTargetResolver.parseConnectionEndpoint(endpoint).address();
        long now = System.currentTimeMillis();
        TargetCacheEntry cached = targetCache;
        if (canReuseTargetCache(
            refresh, normalized, cached.connectionEndpoint(), now, cached.refreshAt())) {
            return Optional.ofNullable(cached.target());
        }
        Optional<ProxyTargetResolver.ResolvedTarget> result = targets.resolve(endpoint, enabledHosts);
        targetCache = new TargetCacheEntry(
            normalized,
            result.orElse(null),
            now + TARGET_CACHE_REFRESH_INTERVAL_MILLIS
        );
        return result;
    }

    private boolean hasCurrentTarget(boolean refresh) {
        boolean present;
        Id1TargetTransition transition;
        synchronized (id1ContextLock) {
            Optional<ProxyTargetResolver.ResolvedTarget> target =
                resolveTarget(currentEndpoint(), refresh);
            transition = recordId1TargetLocked(
                minecraft.getConnection(), target.map(Id1TargetIdentity::from).orElse(null), true);
            present = target.isPresent();
        }
        finishId1TargetTransition(transition);
        return present && !transition.changed();
    }

    static boolean readySyncOneShotReady(
        boolean initialId1FinalWritten,
        boolean readySyncSent,
        boolean readySyncScheduled
    ) {
        return initialId1FinalWritten && !readySyncSent && !readySyncScheduled;
    }

    static boolean canReuseTargetCache(
        boolean refresh,
        String endpoint,
        String cachedEndpoint,
        long now,
        long refreshAt
    ) {
        return !refresh && Objects.equals(endpoint, cachedEndpoint) && now < refreshAt;
    }

    private String currentEndpoint() {
        ServerData server = minecraft.getCurrentServer();
        return server == null || server.ip == null ? "" : server.ip.trim();
    }

    private Connection currentTransportConnection() {
        var listener = minecraft.getConnection();
        return listener == null ? null : listener.getConnection();
    }

    private void refreshLocalUuid() {
        synchronized (id1ContextBuildLock) {
            refreshLocalUuidOnce();
        }
    }

    private void refreshLocalUuidOnce() {
        UUID resolved = protocolRuntimeUuid;
        if (resolved == null) return;

        PbeMd5DesId1Crypto crypto;
        synchronized (id1ContextLock) {
            if (!enabled) return;
            crypto = payloadCrypto;
            if (!resolved.equals(localUuid) || crypto == null) {
                crypto = new PbeMd5DesId1Crypto(resolved);
                payloadCrypto = crypto;
                localUuid = resolved;
            }
            if (id1Builder != null && id1Input != null) return;
        }

        long now = System.currentTimeMillis();
        if (now < nextId1ContextAttemptAt) return;
        long contextEpoch = id1ContextEpoch.get();
        HeyPixelInstallLayout configuredLayout = id1LayoutOverride;
        OfficialRuntimeConfiguration configuredOfficialRuntime = id1OfficialRuntime;
        String configurationError = id1LayoutConfigurationError;
        HeyPixelInstallLayout resolvedLayout = null;
        if (configurationError.isBlank()) {
            try {
                resolvedLayout = resolveId1Layout(new LayoutConfiguration(
                    configuredLayout,
                    configurationError
                ));
            } catch (RuntimeException | LinkageError ignored) {
                configurationError = "official HeyPixel install layout is unavailable";
            }
        }
        final HeyPixelInstallLayout effectiveLayout = resolvedLayout;
        Path currentGameDirectory = currentMinecraftGameDirectory();
        if (configurationError.isBlank() && currentGameDirectory == null) {
            configurationError = "current Minecraft game directory is unavailable";
        }
        Id1EnvironmentMode environmentMode = id1EnvironmentMode(
            effectiveLayout,
            currentGameDirectory
        );
        if (configurationError.isBlank() && environmentMode.externalOfficialInstall()) {
            configurationError = configuredOfficialRuntime.externalBlockingError();
        }
        Id1HwidProvider.Settings configuredHwidSettings = hwidSettings;
        if (!configurationError.isBlank()) {
            nextId1ContextAttemptAt = now + ID1_CONTEXT_RETRY_INTERVAL_MILLIS;
            trace.log("id1-context-blocked", null, null, Map.of(
                "reason", configurationError,
                "retryable", true
            ));
            return;
        }

        try {
            UUID profileUuid = minecraft.getUser().getGameProfile().getId();
            UUID playerUuid = minecraft.player == null ? null : minecraft.player.getUUID();
            Id1RuntimeSignatureProvider signatures = new Id1RuntimeSignatureProvider(crypto);
            Id1EnvironmentCollector environment = environmentMode.externalOfficialInstall()
                ? Id1EnvironmentCollector.fromExternalOfficialInstall(
                    signatures,
                    () -> effectiveLayout.installRoot(),
                    () -> effectiveLayout.instanceDirectory(),
                    hwidProvider,
                    () -> configuredHwidSettings,
                    () -> configuredOfficialRuntime.userDirectory().toString(),
                    () -> configuredOfficialRuntime.javaHome().toString()
                )
                : new Id1EnvironmentCollector(
                    signatures,
                    () -> effectiveLayout.installRoot(),
                    () -> effectiveLayout.instanceDirectory(),
                    hwidProvider,
                    () -> configuredHwidSettings
                );
            Id1PacketBuilder builder = new Id1PacketBuilder(
                signatures,
                crypto,
                Id1PacketBuilder.EvidenceSampler.preserveOrder(),
                value -> value
            );
            BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> inputProvider =
                (challenge, session) -> environment.collect(challenge, session, resolved);

            synchronized (id1ContextLock) {
                if (!enabled
                    || contextEpoch != id1ContextEpoch.get()
                    || !Objects.equals(configuredLayout, id1LayoutOverride)
                    || !Objects.equals(configurationError, id1LayoutConfigurationError)
                    || !Objects.equals(configuredOfficialRuntime, id1OfficialRuntime)
                    || !Objects.equals(configuredHwidSettings, hwidSettings)) {
                    nextId1ContextAttemptAt = 0L;
                    return;
                }
                invalidateId1TasksLocked();
                id1Input = null;
                id1Builder = builder;
                id1Input = inputProvider;
            }
            nextId1ContextAttemptAt = 0L;
            trace.log("local-uuid-ready", null, null, Map.of(
                "source", "protocol-runtime-random",
                "localUuid", resolved.toString(),
                "profileUuid", uuidText(profileUuid),
                "playerUuid", uuidText(playerUuid),
                "id1BuilderReady", true,
                "signatureProviderReady", signatures.available(),
                "externalOfficialInstall", environmentMode.externalOfficialInstall()
            ));
        } catch (RuntimeException error) {
            boolean currentAttempt;
            synchronized (id1ContextLock) {
                currentAttempt = contextEpoch == id1ContextEpoch.get()
                    && id1Builder == null
                    && id1Input == null;
                if (currentAttempt) {
                    id1Input = null;
                    id1Builder = null;
                    nextId1ContextAttemptAt = now + ID1_CONTEXT_RETRY_INTERVAL_MILLIS;
                }
            }
            if (!currentAttempt) return;
            trace.log("id1-context-blocked", null, null, Map.of(
                "reason", "official-startup-snapshot-unavailable",
                "error", error.getClass().getSimpleName(),
                "message", String.valueOf(error.getMessage()),
                "retryable", true
            ));
        }
    }

    private void invalidateId1Context(boolean clearCrypto) {
        synchronized (id1ContextLock) {
            invalidateId1ContextLocked(clearCrypto);
        }
    }

    private void invalidateId1ContextLocked(boolean clearCrypto) {
        id1ContextEpoch.incrementAndGet();
        invalidateId1TasksLocked();
        id1Input = null;
        id1Builder = null;
        id1ContextUsed = false;
        if (clearCrypto) {
            localUuid = null;
            payloadCrypto = null;
        }
    }

    private void invalidateId1Tasks() {
        synchronized (id1ContextLock) {
            invalidateId1TasksLocked();
        }
    }

    private void invalidateId1TasksLocked() {
        id1LifecycleGeneration.incrementAndGet();
        clearPendingId114TokenLeasesLocked();
        cancelPreparedPackets(clearPendingWritesLocked());
        initialId1ScheduledAttempt = null;
    }

    private void clearPendingId114TokenLeasesLocked() {
        pendingId114TokenLeases.forEach(Id114TokenLease::clear);
        pendingId114TokenLeases.clear();
    }

    private Optional<Id1TargetIdentity> resolveCurrentTargetIdentityLocked() {
        return resolveTarget(currentEndpoint(), true).map(Id1TargetIdentity::from);
    }

    private Id1TargetTransition recordId1TargetLocked(
        Object connection,
        Id1TargetIdentity targetIdentity,
        boolean invalidateTasks
    ) {
        boolean connectionChanged = connection != lastConnection;
        boolean targetChanged = !Objects.equals(targetIdentity, lastTargetIdentity);
        // Some early/configuration paths can resolve the current target before the regular tick
        // fallback records the listener. Seed the lifecycle connection here so a subsequent
        // A -> null/B transition cannot lose its predecessor.
        if (activeLifecycleConnection == null && connection != null && enabled) {
            activeLifecycleConnection = connection;
        }
        LoginLifecycleReset loginReset = observeActiveTargetTransitionLocked(
            connection, targetIdentity);
        if (!connectionChanged && !targetChanged && loginReset == null) {
            return Id1TargetTransition.none();
        }
        if (invalidateTasks && loginReset == null) invalidateId1TasksLocked();
        lastConnection = connection;
        lastTargetIdentity = targetIdentity;
        applyId1TargetTransitionLocked();
        return new Id1TargetTransition(connectionChanged, targetChanged, loginReset);
    }

    /**
     * Re-arms login one-shots only after a non-null target has actually been established for the
     * current Minecraft connection. A -> null and A -> B each close the old target once; the
     * subsequent null -> B observation belongs to the already re-armed lifecycle and must not
     * manufacture a second logout.
     */
    private LoginLifecycleReset observeActiveTargetTransitionLocked(
        Object connection,
        Id1TargetIdentity targetIdentity
    ) {
        if (connection == null || connection != activeLifecycleConnection) return null;
        if (!activeLifecycleTargetObserved) {
            if (targetIdentity != null) {
                activeLifecycleTargetObserved = true;
                activeLifecycleTargetIdentity = targetIdentity;
            }
            return null;
        }

        if (activeLifecycleTargetIdentity == null) {
            if (targetIdentity != null) activeLifecycleTargetIdentity = targetIdentity;
            return null;
        }
        if (activeLifecycleTargetIdentity.equals(targetIdentity)) return null;

        LoginLifecycleReset reset = resetTargetLifecycleLocked();
        activeLifecycleTargetIdentity = targetIdentity;
        return reset;
    }

    /** Keeps the immutable startup ID1 context while closing all per-target state. */
    private LoginLifecycleReset resetTargetLifecycleLocked() {
        invalidateId1TasksLocked();
        initialId1Submitted = false;
        readySyncSent = false;
        readySyncScheduled = false;
        LocalPlayer currentPlayer = minecraft == null ? observedLocalPlayer : minecraft.player;
        observedLocalPlayer = currentPlayer;
        localPlayerJoinPending = currentPlayer != null;
        nextInitialId1AttemptAt = 0L;
        Id114NativeSink nativeLogoutSink = pendingId114LogoutSink;
        pendingId114LogoutSink = null;
        return new LoginLifecycleReset(List.of(), nativeLogoutSink);
    }

    private void finishId1TargetTransition(Id1TargetTransition transition) {
        if (transition == null || !transition.changed()) return;
        if (transition.loginReset() != null) {
            LoginLifecycleReset reset = transition.loginReset();
            cancelPreparedPackets(reset.stalePackets());
            performId114NativeLogout(reset.nativeLogoutSink());
            trace.log("runtime-logout", null, null, Map.of("reason", "target-transition"));
        }
    }

    private void applyId1TargetTransitionLocked() {
        nextInitialId1AttemptAt = 0L;
        resetClickState();
        state.reset();
    }

    private void observeLocalPlayerLifecycleLocked() {
        LocalPlayer currentPlayer = minecraft.player;
        if (currentPlayer == observedLocalPlayer) return;
        invalidateId1TasksLocked();
        observedLocalPlayer = currentPlayer;
        localPlayerJoinPending = currentPlayer != null && !initialId1Submitted;
        nextInitialId1AttemptAt = 0L;
    }

    static void requireCompleteId1Provider(
        Id1PacketBuilder builder,
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> input
    ) {
        if ((builder == null) != (input == null)) {
            throw new IllegalArgumentException("ID1 builder and input provider must both be set or both be null");
        }
    }

    private Id1TaskDispatch snapshotId1Dispatch(Id1IncomingContext incoming) {
        synchronized (id1ContextLock) {
            if (observeOnly || !allowLiveSend || !isCurrentIncomingLocked(incoming)
                || incoming.session().isEmpty()) {
                return null;
            }
            Id1TaskSnapshot task = snapshotId1TaskLocked(
                incoming.connection(), incoming.targetIdentity());
            if (task != null) id1ContextUsed = true;
            return task == null ? null : new Id1TaskDispatch(task, incoming.session().get());
        }
    }

    private boolean id1ContextCanRebuildBeforeFirstUseLocked() {
        return !id1ContextUsed
            && !initialId1Submitted
            && initialId1ScheduledAttempt == null;
    }

    private Id1TaskSnapshot snapshotId1TaskLocked(
        Object connection,
        Id1TargetIdentity targetIdentity
    ) {
        UUID uuid = localUuid;
        Id1PacketBuilder builder = id1Builder;
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> inputProvider =
            id1Input;
        if (!enabled || connection == null || uuid == null || builder == null || inputProvider == null
            || targetIdentity == null) {
            return null;
        }
        return new Id1TaskSnapshot(
            id1LifecycleGeneration.get(),
            connection,
            uuid,
            builder,
            inputProvider,
            targetIdentity
        );
    }

    private boolean isCurrentId1Task(Id1TaskSnapshot task) {
        synchronized (id1ContextLock) {
            return isCurrentId1TaskLocked(task);
        }
    }

    private boolean isCurrentIncomingLocked(Id1IncomingContext incoming) {
        return incoming != null
            && enabled
            && incoming.generation() == id1LifecycleGeneration.get()
            && incoming.connection() == minecraft.getConnection()
            && incoming.transportConnection() == currentTransportConnection()
            && incoming.connection() == lastConnection
            && incoming.targetIdentity().equals(lastTargetIdentity);
    }

    private boolean isCurrentId114GenerationLocked(Id114IncomingGeneration generation) {
        return generation != null && generation.matches(
            enabled,
            id1LifecycleGeneration.get(),
            minecraft.getConnection(),
            currentTransportConnection(),
            lastConnection,
            lastTargetIdentity
        );
    }

    private boolean isCurrentId1TaskLocked(Id1TaskSnapshot task) {
        Id1TargetIdentity targetIdentity = lastTargetIdentity;
        return targetIdentity != null && isCurrentId1TaskLocked(task, targetIdentity);
    }

    private boolean isCurrentId1TaskLocked(
        Id1TaskSnapshot task,
        Id1TargetIdentity targetIdentity
    ) {
        return task != null && task.matches(
            enabled,
            id1LifecycleGeneration.get(),
            minecraft.getConnection(),
            localUuid,
            id1Builder,
            id1Input,
            targetIdentity
        );
    }

    private void requireCurrentId1Task(Id1TaskSnapshot task) {
        if (!isCurrentId1Task(task)) throw new IllegalStateException("ID1 task belongs to a stale lifecycle");
    }

    private static Map<String, Object> initialId1Details(
        UUID localUuid,
        Id1PacketBuilder.Context context,
        Id1PacketBuilder.SprintEnvironment environment,
        Id1PacketBuilder.BuiltPacket packet,
        ProtocolSessionSnapshot session
    ) {
        byte[] preCrypto = packet.preCrypto();
        byte[] wire = packet.wire();
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("localUuid", localUuid.toString());
        details.put("snapshotEntityId", session.entityId());
        details.put("roleName", session.roleName());
        details.put("writerTime", context.writerTime());
        details.put("layout", packet.layout());
        details.put("preCryptoLength", preCrypto.length);
        details.put("businessLength", wire.length);
        details.put("preCryptoSha256", sha256(preCrypto));
        details.put("wireSha256", sha256(wire));
        details.put("preCryptoPrefixHex", hexPrefix(preCrypto, 96));
        details.put("loadedMods", environment.loadedMods().size());
        details.put("discoveredJars", environment.discoveredJars().size());
        details.put("environmentSource", environment.source());
        details.put("hwidSource", environment.hwidSource());
        details.put("syntheticHwid", environment.syntheticHwid());
        details.put("syntheticHwidProfile", environment.hwidProfile());
        details.put("syntheticHwidId", environment.syntheticHwidId());
        details.put("syntheticHwidHistoryCount", environment.syntheticHwidHistoryCount());
        details.put("modSummarySha256", sha256(modSummary(environment).getBytes(StandardCharsets.UTF_8)));
        details.put("jarSummarySha256", sha256(String.join("\n", environment.discoveredJars()).getBytes(StandardCharsets.UTF_8)));
        details.put("networkInterfaces", valueSize(environment.networkInterfaces()));
        details.put("diskStores", valueSize(environment.diskStores()));
        details.put("accountTraces", valueSize(environment.accountTraces()));
        details.put("userProperties", valueSize(environment.userProperties()));
        details.put("userDirectorySha256", sha256(environment.userDirectory().getBytes(StandardCharsets.UTF_8)));
        details.put("javaHomeSha256", sha256(environment.javaHome().getBytes(StandardCharsets.UTF_8)));
        return details;
    }

    private static S2cFailure inspectS2cFailure(byte[] wire, RuntimeException error) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("error", error.getClass().getSimpleName());
        details.put("message", String.valueOf(error.getMessage()));
        Throwable root = rootCause(error);
        details.put("rootError", root.getClass().getSimpleName());
        details.put("rootMessage", String.valueOf(root.getMessage()));
        details.put("wireLength", wire.length);
        details.put("wirePrefixHex", hexPrefix(wire, 128));

        Integer packetId = null;
        try {
            S2CPacketDecoders.WrappedPacket wrapped = S2CPacketDecoders.decodeWrapper(wire);
            packetId = wrapped.packetId();
            byte[] payload = wrapped.payload();
            details.put("wrapperPayloadLength", payload.length);
            details.put("payloadPrefixHex", hexPrefix(payload, 128));
            try {
                S2CPayloadUnwrapper.LengthPrefix prefix = S2CPayloadUnwrapper.readVarInt(payload);
                details.put("declaredPayloadLength", prefix.value());
                details.put("lengthPrefixBytes", prefix.bytesRead());
                details.put("payloadRemaining", prefix.remaining());
            } catch (RuntimeException prefixError) {
                details.put("lengthPrefixError", prefixError.getClass().getSimpleName()
                    + ": " + String.valueOf(prefixError.getMessage()));
            }
        } catch (RuntimeException wrapperError) {
            details.put("wrapperError", wrapperError.getClass().getSimpleName()
                + ": " + String.valueOf(wrapperError.getMessage()));
        }
        return new S2cFailure(packetId, details);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    static String classifyId1BuildFailure(Throwable error) {
        Throwable root = rootCause(Objects.requireNonNull(error, "error"));
        String message = String.valueOf(error.getMessage());
        if (message.contains("signed Fantnel session is required")) {
            return "SIGNED_SESSION_MISSING";
        }
        if (message.contains("signed Fantnel userId is not a signed long")) {
            return "SIGNED_SESSION_USER_ID_INVALID";
        }
        if (message.contains("launcher and signed session UserId identities differ")) {
            return "LAUNCHER_SESSION_IDENTITY_CONFLICT";
        }
        if (message.contains("16-entry startup LoadingModList")) {
            return "MOD_SNAPSHOT_INCOMPLETE";
        }
        if (message.contains("13-entry top-level JAR")) {
            return "JAR_SNAPSHOT_INCOMPLETE";
        }
        if (message.contains("stale lifecycle") || message.contains("stale before send")) {
            return "STALE_LIFECYCLE";
        }
        if (root instanceof javax.crypto.BadPaddingException
            || message.contains("PBE transform failed")) {
            return "CRYPTO_TRANSFORM_FAILED";
        }
        return "UNCLASSIFIED_" + error.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);
    }

    private static int valueSize(Object value) {
        if (value instanceof Collection<?> collection) return collection.size();
        if (value instanceof Map<?, ?> map) return map.size();
        if (value instanceof Object[] array) return array.length;
        return value == null ? 0 : 1;
    }

    private static String uuidText(UUID value) {
        return value == null ? "" : value.toString();
    }

    private Path currentMinecraftGameDirectory() {
        if (minecraft == null || minecraft.gameDirectory == null) return null;
        try {
            return minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static Id1EnvironmentMode id1EnvironmentMode(
        HeyPixelInstallLayout configuredLayout,
        Path currentGameDirectory
    ) {
        if (configuredLayout == null || currentGameDirectory == null) {
            return Id1EnvironmentMode.SAME_JVM;
        }
        return sameDirectory(configuredLayout.instanceDirectory(), currentGameDirectory)
            ? Id1EnvironmentMode.SAME_JVM
            : Id1EnvironmentMode.EXTERNAL_OFFICIAL_INSTALL;
    }

    private static boolean sameDirectory(Path left, Path right) {
        Path normalizedLeft = left.toAbsolutePath().normalize();
        Path normalizedRight = right.toAbsolutePath().normalize();
        try {
            if (Files.exists(normalizedLeft) && Files.exists(normalizedRight)) {
                return Files.isSameFile(normalizedLeft, normalizedRight);
            }
        } catch (Exception ignored) {
        }
        return normalizedLeft.toString().equalsIgnoreCase(normalizedRight.toString());
    }

    private static Path configuredPath(String value) {
        return Path.of(value.trim()).toAbsolutePath().normalize();
    }

    static OfficialRuntimeConfiguration officialRuntime(String userDirectory, String javaHome) {
        boolean userDirectoryBlank = userDirectory == null || userDirectory.isBlank();
        boolean javaHomeBlank = javaHome == null || javaHome.isBlank();
        if (userDirectoryBlank && javaHomeBlank) {
            userDirectory = automaticOfficialRuntimePath(
                ID1_OFFICIAL_USER_DIRECTORY_PROPERTY,
                ID1_OFFICIAL_USER_DIRECTORY_ENV
            );
            javaHome = automaticOfficialRuntimePath(
                ID1_OFFICIAL_JAVA_HOME_PROPERTY,
                ID1_OFFICIAL_JAVA_HOME_ENV
            );
            userDirectoryBlank = userDirectory == null || userDirectory.isBlank();
            javaHomeBlank = javaHome == null || javaHome.isBlank();
            if (userDirectoryBlank && javaHomeBlank) {
                return OfficialRuntimeConfiguration.automatic();
            }
        }
        if (userDirectoryBlank || javaHomeBlank) {
            return OfficialRuntimeConfiguration.invalid(
                "both external ID1 official user directory and Java Home are required");
        }
        try {
            Path configuredUserDirectory = configuredPath(userDirectory);
            Path configuredJavaHome = configuredPath(javaHome);
            if (!Files.isDirectory(configuredUserDirectory)) {
                return OfficialRuntimeConfiguration.invalid(
                    "external ID1 official user directory must exist");
            }
            Path bin = configuredJavaHome.resolve("bin");
            if (!Files.isRegularFile(bin.resolve("java.exe"))
                && !Files.isRegularFile(bin.resolve("javaw.exe"))
                && !Files.isRegularFile(bin.resolve("java"))) {
                return OfficialRuntimeConfiguration.invalid(
                    "external ID1 official Java Home must contain bin/java");
            }
            return OfficialRuntimeConfiguration.configured(
                configuredUserDirectory,
                configuredJavaHome
            );
        } catch (RuntimeException ignored) {
            return OfficialRuntimeConfiguration.invalid(
                "invalid external ID1 official runtime paths");
        }
    }

    private static String automaticOfficialRuntimePath(String property, String environment) {
        try {
            String configured = System.getProperty(property, "");
            if (!configured.isBlank()) return configured;
            String inherited = System.getenv(environment);
            return inherited == null ? "" : inherited;
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    static LayoutConfiguration legacyLayout(String value) {
        if (value == null || value.isBlank()) return LayoutConfiguration.automatic();
        try {
            Path path = configuredPath(value);
            if (!Files.isDirectory(path)) {
                return LayoutConfiguration.invalid("legacy ID1 game directory must exist");
            }
            return LayoutConfiguration.configured(HeyPixelInstallLayout.fromLegacyPath(path));
        } catch (RuntimeException ignored) {
            return LayoutConfiguration.invalid("invalid legacy ID1 game directory");
        }
    }

    static LayoutConfiguration explicitLayout(String installRoot, String instanceDirectory) {
        boolean rootBlank = installRoot == null || installRoot.isBlank();
        boolean instanceBlank = instanceDirectory == null || instanceDirectory.isBlank();
        if (rootBlank && instanceBlank) return LayoutConfiguration.automatic();
        if (rootBlank || instanceBlank) {
            return LayoutConfiguration.invalid("both ID1 install root and instance directory are required");
        }
        try {
            Path root = configuredPath(installRoot);
            Path instance = configuredPath(instanceDirectory);
            if (!Files.isDirectory(root) || !Files.isDirectory(instance)) {
                return LayoutConfiguration.invalid(
                    "ID1 install root and instance directory must both exist");
            }
            return LayoutConfiguration.configured(HeyPixelInstallLayout.fromPaths(root, instance));
        } catch (RuntimeException ignored) {
            return LayoutConfiguration.invalid("invalid or unrelated ID1 install root and instance directory");
        }
    }

    private static String modSummary(Id1PacketBuilder.SprintEnvironment environment) {
        StringBuilder builder = new StringBuilder();
        for (Id1PacketBuilder.ModEvidence mod : environment.loadedMods()) {
            builder.append(mod.moduleName()).append('\n')
                .append(mod.path()).append('\n')
                .append(mod.digest()).append('\n');
        }
        return builder.toString();
    }

    private static String hexPrefix(byte[] value, int limit) {
        return HexFormat.of().formatHex(value, 0, Math.min(value.length, limit));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    static final class FinalWriteLedger<T> {
        private final IdentityHashMap<Object, T> entries = new IdentityHashMap<>();

        void reserve(Object key, T value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (entries.containsKey(key)) {
                throw new IllegalStateException("packet already has a final-write reservation");
            }
            entries.put(key, value);
        }

        T get(Object key) {
            return entries.get(key);
        }

        T complete(Object key) {
            return entries.remove(key);
        }

        List<Entry<T>> removeIf(java.util.function.Predicate<? super T> predicate) {
            Objects.requireNonNull(predicate, "predicate");
            List<Entry<T>> removed = new ArrayList<>();
            Iterator<Map.Entry<Object, T>> iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, T> entry = iterator.next();
                if (!predicate.test(entry.getValue())) continue;
                removed.add(new Entry<>(entry.getKey(), entry.getValue()));
                iterator.remove();
            }
            return removed;
        }

        List<Entry<T>> clear() {
            List<Entry<T>> removed = new ArrayList<>(entries.size());
            entries.forEach((key, value) -> removed.add(new Entry<>(key, value)));
            entries.clear();
            return removed;
        }

        int size() {
            return entries.size();
        }

        record Entry<T>(Object key, T value) {
        }
    }

    enum PendingWriteKind {
        BUSINESS,
        ID1_RESPONSE,
        INITIAL_ID1,
        READY_SYNC;

        boolean lifecycleOneShot() {
            return this == INITIAL_ID1 || this == READY_SYNC;
        }

        Integer responseRequestPacketId() {
            return this == ID1_RESPONSE ? 101 : null;
        }
    }

    private record LoginLifecycleReset(
        List<Packet<?>> stalePackets,
        Id114NativeSink nativeLogoutSink
    ) {
        private LoginLifecycleReset {
            stalePackets = List.copyOf(stalePackets);
        }
    }

    private record PendingWrite(
        PendingWriteKind kind,
        long generation,
        Object connection,
        Id1TargetIdentity targetIdentity,
        Id1TaskSnapshot id1Task,
        long createdAt,
        int packetId,
        String trigger
    ) {
        private PendingWrite {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(targetIdentity, "targetIdentity");
            trigger = Objects.requireNonNullElse(trigger, "");
        }
    }

    private record S2cFailure(Integer packetId, Map<String, Object> details) {
    }

    private record Id1TargetTransition(
        boolean connectionChanged,
        boolean targetChanged,
        LoginLifecycleReset loginReset
    ) {
        private static Id1TargetTransition none() {
            return new Id1TargetTransition(false, false, null);
        }

        private boolean changed() {
            return connectionChanged || targetChanged;
        }
    }

    private record TargetCacheEntry(
        String connectionEndpoint,
        ProxyTargetResolver.ResolvedTarget target,
        long refreshAt
    ) {
        private TargetCacheEntry {
            connectionEndpoint = Objects.requireNonNullElse(connectionEndpoint, "");
        }

        private static TargetCacheEntry empty() {
            return new TargetCacheEntry("", null, 0L);
        }
    }

    record LayoutConfiguration(HeyPixelInstallLayout layout, String error) {
        LayoutConfiguration {
            error = error == null ? "" : error;
            if (layout != null && !error.isBlank()) {
                throw new IllegalArgumentException("A layout configuration cannot be valid and invalid at once");
            }
        }

        static LayoutConfiguration automatic() {
            return new LayoutConfiguration(null, "");
        }

        static LayoutConfiguration configured(HeyPixelInstallLayout layout) {
            return new LayoutConfiguration(Objects.requireNonNull(layout, "layout"), "");
        }

        static LayoutConfiguration invalid(String error) {
            return new LayoutConfiguration(null, Objects.requireNonNull(error, "error"));
        }

        boolean valid() {
            return error.isBlank();
        }
    }

    enum Id1EnvironmentMode {
        SAME_JVM,
        EXTERNAL_OFFICIAL_INSTALL;

        boolean externalOfficialInstall() {
            return this == EXTERNAL_OFFICIAL_INSTALL;
        }
    }

    record OfficialRuntimeConfiguration(Path userDirectory, Path javaHome, String error) {
        OfficialRuntimeConfiguration {
            error = error == null ? "" : error;
            if ((userDirectory == null) != (javaHome == null)) {
                throw new IllegalArgumentException(
                    "official runtime paths must both be configured or both be absent");
            }
            if (userDirectory != null) {
                userDirectory = userDirectory.toAbsolutePath().normalize();
                javaHome = javaHome.toAbsolutePath().normalize();
            }
            if (userDirectory != null && !error.isBlank()) {
                throw new IllegalArgumentException(
                    "an official runtime configuration cannot be valid and invalid at once");
            }
        }

        static OfficialRuntimeConfiguration automatic() {
            return new OfficialRuntimeConfiguration(null, null, "");
        }

        static OfficialRuntimeConfiguration configured(Path userDirectory, Path javaHome) {
            return new OfficialRuntimeConfiguration(
                Objects.requireNonNull(userDirectory, "userDirectory"),
                Objects.requireNonNull(javaHome, "javaHome"),
                ""
            );
        }

        static OfficialRuntimeConfiguration invalid(String error) {
            return new OfficialRuntimeConfiguration(null, null, Objects.requireNonNull(error, "error"));
        }

        boolean configured() {
            return userDirectory != null && javaHome != null;
        }

        boolean valid() {
            return error.isBlank();
        }

        String externalBlockingError() {
            if (!valid()) return error;
            return configured()
                ? ""
                : "external ID1 official user directory and Java Home are required";
        }
    }

    record Id1TaskSnapshot(
        long generation,
        Object connection,
        UUID localUuid,
        Id1PacketBuilder builder,
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> inputProvider,
        Id1TargetIdentity targetIdentity
    ) {
        Id1TaskSnapshot {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(localUuid, "localUuid");
            Objects.requireNonNull(builder, "builder");
            Objects.requireNonNull(inputProvider, "inputProvider");
            Objects.requireNonNull(targetIdentity, "targetIdentity");
        }

        boolean matches(
            boolean enabled,
            long currentGeneration,
            Object currentConnection,
            UUID currentUuid,
            Id1PacketBuilder currentBuilder,
            BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> currentInput,
            Id1TargetIdentity currentTargetIdentity
        ) {
            return enabled
                && generation == currentGeneration
                && connection == currentConnection
                && localUuid.equals(currentUuid)
                && builder == currentBuilder
                && inputProvider == currentInput
                && targetIdentity.equals(currentTargetIdentity);
        }
    }

    private record Id1TaskDispatch(Id1TaskSnapshot task, ProtocolSessionSnapshot session) {
        private Id1TaskDispatch {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(session, "session");
        }
    }

    private record Id1IncomingContext(
        long generation,
        Object connection,
        Connection transportConnection,
        Id1TargetIdentity targetIdentity,
        Optional<ProtocolSessionSnapshot> session
    ) {
        private Id1IncomingContext {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(transportConnection, "transportConnection");
            Objects.requireNonNull(targetIdentity, "targetIdentity");
            session = session == null ? Optional.empty() : session;
        }
    }

    record Id114IncomingGeneration(
        long generation,
        Object connection,
        Object transportConnection,
        Id1TargetIdentity targetIdentity
    ) {
        Id114IncomingGeneration {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(transportConnection, "transportConnection");
            Objects.requireNonNull(targetIdentity, "targetIdentity");
        }

        private static Id114IncomingGeneration from(Id1IncomingContext incoming) {
            Objects.requireNonNull(incoming, "incoming");
            return new Id114IncomingGeneration(
                incoming.generation(),
                incoming.connection(),
                incoming.transportConnection(),
                incoming.targetIdentity()
            );
        }

        boolean matches(
            boolean enabled,
            long currentGeneration,
            Object currentConnection,
            Object currentTransportConnection,
            Object recordedConnection,
            Id1TargetIdentity currentTargetIdentity
        ) {
            return enabled
                && generation == currentGeneration
                && connection == currentConnection
                && transportConnection == currentTransportConnection
                && connection == recordedConnection
                && targetIdentity.equals(currentTargetIdentity);
        }
    }

    record Id1TargetIdentity(
        String connectionEndpoint,
        String targetHost,
        int targetPort,
        boolean proxied,
        String sessionSha256
    ) {
        Id1TargetIdentity {
            connectionEndpoint = Objects.requireNonNullElse(connectionEndpoint, "");
            targetHost = ProtocolSessionProvider.normalizeHost(targetHost);
            sessionSha256 = Objects.requireNonNullElse(sessionSha256, "");
        }

        static Id1TargetIdentity from(ProxyTargetResolver.ResolvedTarget target) {
            Objects.requireNonNull(target, "target");
            String sessionSha256 = target.session()
                .map(ProtocolSessionProvider::canonical)
                .map(value -> sha256(value.getBytes(StandardCharsets.UTF_8)))
                .orElse("");
            return new Id1TargetIdentity(
                target.connectionEndpoint(),
                target.targetHost(),
                target.targetPort(),
                target.proxied(),
                sessionSha256
            );
        }
    }

    record Id1InitialAttempt(
        long generation,
        Object connection,
        Id1TargetIdentity targetIdentity
    ) {
        Id1InitialAttempt {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(targetIdentity, "targetIdentity");
        }

        static Id1InitialAttempt from(Id1TaskSnapshot task) {
            Objects.requireNonNull(task, "task");
            return new Id1InitialAttempt(
                task.generation(), task.connection(), task.targetIdentity());
        }

        boolean matches(Id1InitialAttempt other) {
            return other != null
                && generation == other.generation
                && connection == other.connection
                && targetIdentity.equals(other.targetIdentity);
        }
    }

    public Optional<UUID> localUuid() {
        return Optional.ofNullable(localUuid);
    }

    PbeMd5DesId1Crypto payloadCrypto() {
        return payloadCrypto;
    }

    public record Id1BuildInput(
        Id1PacketBuilder.Id1Subtype subtype,
        Id1PacketBuilder.Context context,
        Object subtypePayload
    ) {
    }
}
