package com.columbina.heypixel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shit.zen.protocol.heypixel.C2SPacketEncoders;
import shit.zen.protocol.heypixel.ClientLogicalWork;
import shit.zen.protocol.heypixel.HeyPixelPacketSemantics;
import shit.zen.protocol.heypixel.HeyPixelProtocolDispatcher;
import shit.zen.protocol.heypixel.HeyPixelProtocolState;
import shit.zen.protocol.heypixel.HeyPixelOuterBridge;
import shit.zen.protocol.heypixel.Id114ClientWork;
import shit.zen.protocol.heypixel.Id1BuildInput;
import shit.zen.protocol.heypixel.Id1EnvironmentCollector;
import shit.zen.protocol.heypixel.Id1HwidProvider;
import shit.zen.protocol.heypixel.Id1PacketBuilder;
import shit.zen.protocol.heypixel.Id1RuntimeSignatureProvider;
import shit.zen.protocol.heypixel.Id2CachedClock;
import shit.zen.protocol.heypixel.PbeMd5DesId1Crypto;
import shit.zen.protocol.heypixel.ProtocolSessionProvider;
import shit.zen.protocol.heypixel.ProtocolSessionSnapshot;
import shit.zen.protocol.heypixel.ProtocolTraceLogger;
import shit.zen.protocol.heypixel.ProxyTargetResolver;
import shit.zen.protocol.heypixel.S2CPacketDecoders;
import shit.zen.protocol.heypixel.S2CPayloadUnwrapper;
import shit.zen.protocol.heypixel.SyncTokenMetadata;

/**
 * Fabric 1.21.4 transport adapter around the canonical HeyPixel codec and lifecycle.
 * It sends only through the current Minecraft connection and only for a signed Fantnel proxy session.
 */
public final class HeyPixelFabricRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mizulune.Protocol");
    private static final long INITIAL_RETRY_MILLIS = 2_000L;
    private static final long ENVIRONMENT_RETRY_MILLIS = 30_000L;
    private static final long HEARTBEAT_MILLIS = 5_000L;
    private static final long CPS_WINDOW_MILLIS = 1_000L;
    private static final long CPS_COOLDOWN_MILLIS = 250L;
    private static final int READY_SYNC_OPCODE = 12;

    private final Minecraft minecraft;
    private final ProtocolModConfig config;
    private final ProtocolSessionProvider sessions;
    private final ProxyTargetResolver targets;
    private final ProtocolTraceLogger trace;
    private final HeyPixelProtocolState state = new HeyPixelProtocolState();
    private final HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(state);
    private final Object lifecycleLock = new Object();
    private final Object clickLock = new Object();
    private final UUID runtimeUuid = UUID.randomUUID();
    private final PbeMd5DesId1Crypto crypto = new PbeMd5DesId1Crypto(runtimeUuid);
    private final Id1RuntimeSignatureProvider signatures = new Id1RuntimeSignatureProvider(crypto);
    private final Id1PacketBuilder id1Builder = new Id1PacketBuilder(
        signatures,
        crypto,
        Id1PacketBuilder.EvidenceSampler.preserveOrder(),
        value -> value
    );
    private final Id1HwidProvider hwidProvider;
    private final ExecutorService id1Executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService heartbeatExecutor =
        Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Mizulune-Protocol-ID2");
            thread.setDaemon(true);
            return thread;
        });
    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();

    private ClientPacketListener observedHandler;
    private ActiveConnection active;
    private long generation;
    private ScheduledFuture<?> heartbeatTask;
    private boolean initialScheduled;
    private boolean initialSent;
    private boolean readyScheduled;
    private boolean readySent;
    private long nextInitialAttemptAt;
    private volatile Id1EnvironmentCollector environment;
    private volatile boolean environmentBuilding;
    private volatile long nextEnvironmentAttemptAt;
    private long nextCpsSendAt;
    private int lastLeftCps;
    private int lastRightCps;

    public HeyPixelFabricRuntime(
        Minecraft minecraft,
        Path protocolDirectory,
        ProtocolModConfig config
    ) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.config = Objects.requireNonNull(config, "config");
        this.sessions = new ProtocolSessionProvider(protocolDirectory);
        this.targets = new ProxyTargetResolver(sessions);
        this.trace = new ProtocolTraceLogger(protocolDirectory.resolve("protocol-trace-1.21.4"));
        this.trace.setEnabled(config.traceEnabled());
        this.hwidProvider = new Id1HwidProvider(protocolDirectory);
        if (config.enabled() && config.allowLiveSend()) buildEnvironmentSynchronously();
    }

    public void onJoin(ClientPacketListener handler) {
        refreshConnection(handler);
    }

    public void onDisconnect(ClientPacketListener handler) {
        synchronized (lifecycleLock) {
            if (handler != observedHandler) return;
        }
        transition(handler, null, true);
    }

    public void onStartTick(Minecraft client) {
        if (client != minecraft) return;
        refreshConnection(client.getConnection());
        tickCps();
    }

    public void onEndTick(Minecraft client) {
        if (client != minecraft) return;
        refreshConnection(client.getConnection());
        startEnvironmentBuild();
        tickInitialId1();
        tickReadySync();
        tickCps();
    }

    public void handle(HeyPixelPayload payload, ClientPacketListener sourceHandler) {
        if (payload == null || sourceHandler == null || minecraft.getConnection() != sourceHandler) return;
        refreshConnection(sourceHandler);
        LifecycleSnapshot incoming = snapshot();
        if (incoming == null || incoming.connection().handler() != sourceHandler) return;
        byte[] wire = payload.copyData();
        try {
            if (wire.length > 0
                && (wire[0] & 0xff) == S2CPacketDecoders.JSON_EVENT_DISCRIMINATOR) {
                trace.log("s2c-json-event", HeyPixelPayload.CHANNEL.toString(), null,
                    Map.of("length", wire.length - 1));
                return;
            }
            S2CPayloadUnwrapper.UnwrappedPacket packet =
                S2CPayloadUnwrapper.unwrap(wire, crypto);
            if (!isCurrent(incoming)) return;
            if (packet.packetId() == 114) {
                handleSyncToken(packet, incoming);
                return;
            }
            HeyPixelProtocolDispatcher.CanonicalDispatchResult decoded =
                dispatcher.decodeCanonical(packet.packetId(), packet.payload());
            if (decoded.kind() == HeyPixelProtocolDispatcher.CanonicalKind.UNIMPLEMENTED) {
                traceDispatch(decoded, packet.encrypted(), packet.trailingBytes());
                return;
            }
            enqueueDispatch(decoded, packet.encrypted(), packet.trailingBytes(), incoming);
        } catch (RuntimeException error) {
            trace.log("s2c-error", HeyPixelPayload.CHANNEL.toString(), null, Map.of(
                "wireLength", wire.length,
                "errorType", error.getClass().getSimpleName()
            ));
        }
    }

    public void recordMouseButton(int button, int action) {
        if (action != 1 || (button != 0 && button != 1) || snapshot() == null) return;
        synchronized (clickLock) {
            (button == 0 ? leftClicks : rightClicks).addLast(Id2CachedClock.currentTimeMillis());
        }
    }

    public void sendUseBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (player == null || hand == null || hit == null || player != minecraft.player) return;
        LifecycleSnapshot current = snapshot();
        if (current == null) return;
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
        sendBusiness(current, C2SPacketEncoders.encodeUseBlockTelemetry(packet), 5,
            "USE_BLOCK", SendKind.BUSINESS);
    }

    public HeyPixelProtocolState state() {
        return state;
    }

    public boolean isActive() {
        return snapshot() != null;
    }

    public void shutdown() {
        transition(minecraft.getConnection(), null, true);
        id1Executor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    private void refreshConnection(ClientPacketListener handler) {
        ActiveConnection resolved = resolveActive(handler).orElse(null);
        synchronized (lifecycleLock) {
            if (handler == observedHandler
                && Objects.equals(identityOf(active), identityOf(resolved))) {
                return;
            }
        }
        transition(handler, resolved, false);
    }

    private void transition(
        ClientPacketListener handler,
        ActiveConnection next,
        boolean logout
    ) {
        ScheduledFuture<?> staleHeartbeat;
        long nextGeneration;
        synchronized (lifecycleLock) {
            ClientPacketListener nextHandler = next == null ? handler : next.handler();
            boolean resetCompletedOneShots = ProtocolLifecyclePolicy.resetsCompletedOneShots(
                observedHandler,
                nextHandler,
                logout
            );
            staleHeartbeat = heartbeatTask;
            heartbeatTask = null;
            generation++;
            nextGeneration = generation;
            observedHandler = logout ? null : nextHandler;
            active = next;
            initialScheduled = false;
            readyScheduled = false;
            if (resetCompletedOneShots) {
                initialSent = false;
                readySent = false;
            }
            nextInitialAttemptAt = 0L;
            if (next != null) {
                heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(
                    () -> runHeartbeat(nextGeneration),
                    HEARTBEAT_MILLIS,
                    HEARTBEAT_MILLIS,
                    TimeUnit.MILLISECONDS
                );
            }
        }
        if (staleHeartbeat != null) staleHeartbeat.cancel(false);
        state.reset();
        resetClicks();
        trace.log("connection-state", HeyPixelPayload.CHANNEL.toString(), null, Map.of(
            "active", next != null,
            "proxied", next != null,
            "signedSession", next != null
        ));
    }

    private Optional<ActiveConnection> resolveActive(ClientPacketListener handler) {
        if (!config.enabled() || !config.allowLiveSend() || handler == null) return Optional.empty();
        Connection transport = handler.getConnection();
        if (transport == null || !transport.isConnected()) return Optional.empty();
        String endpoint = currentEndpoint(handler);
        return targets.resolve(endpoint, config.enabledHosts())
            .filter(ProxyTargetResolver.ResolvedTarget::proxied)
            .filter(target -> target.session().isPresent())
            .filter(target -> target.session().orElseThrow().version() >= 2)
            .filter(target -> "fantnel".equalsIgnoreCase(target.session().orElseThrow().source()))
            .map(target -> new ActiveConnection(
                handler,
                transport,
                target.session().orElseThrow(),
                TargetIdentity.from(target)
            ));
    }

    private String currentEndpoint(ClientPacketListener handler) {
        ServerData server = minecraft.getCurrentServer();
        if (server == null) server = handler.getServerData();
        if (server != null && server.ip != null && !server.ip.isBlank()) return server.ip.trim();
        return handler.getConnection().getRemoteAddress() == null
            ? ""
            : handler.getConnection().getRemoteAddress().toString();
    }

    private LifecycleSnapshot snapshot() {
        synchronized (lifecycleLock) {
            if (active == null || observedHandler != active.handler()) return null;
            return new LifecycleSnapshot(generation, active);
        }
    }

    private LifecycleSnapshot snapshot(long requiredGeneration) {
        LifecycleSnapshot current = snapshot();
        return current != null && current.generation() == requiredGeneration ? current : null;
    }

    private boolean isCurrent(LifecycleSnapshot snapshot) {
        synchronized (lifecycleLock) {
            return isCurrentLocked(snapshot);
        }
    }

    private boolean isCurrentLocked(LifecycleSnapshot snapshot) {
        return snapshot != null
            && generation == snapshot.generation()
            && active != null
            && active.handler() == snapshot.connection().handler()
            && active.transport() == snapshot.connection().transport()
            && active.identity().equals(snapshot.connection().identity())
            && minecraft.getConnection() == active.handler();
    }

    private void startEnvironmentBuild() {
        if (!config.enabled() || !config.allowLiveSend()) return;
        long now = System.currentTimeMillis();
        if (!reserveEnvironmentBuild(now)) return;
        try {
            id1Executor.execute(this::finishEnvironmentBuild);
        } catch (RuntimeException error) {
            environmentBuilding = false;
            nextEnvironmentAttemptAt = now + ENVIRONMENT_RETRY_MILLIS;
        }
    }

    private void buildEnvironmentSynchronously() {
        if (reserveEnvironmentBuild(System.currentTimeMillis())) finishEnvironmentBuild();
    }

    private boolean reserveEnvironmentBuild(long now) {
        if (environment != null || environmentBuilding || now < nextEnvironmentAttemptAt) return false;
        synchronized (lifecycleLock) {
            if (environment != null || environmentBuilding || now < nextEnvironmentAttemptAt) return false;
            environmentBuilding = true;
            return true;
        }
    }

    private void finishEnvironmentBuild() {
        try {
            Id1EnvironmentCollector built = Id1EnvironmentCollector.fromExternalOfficialInstall(
                signatures,
                () -> config.installRoot(),
                () -> config.instanceDirectory(),
                hwidProvider,
                () -> new Id1HwidProvider.Settings(
                    config.syntheticHwid(), config.syntheticHwidProfile()),
                () -> config.officialUserDirectory().toString(),
                () -> config.officialJavaHome().toString()
            );
            environment = built;
            nextEnvironmentAttemptAt = 0L;
            trace.log("id1-context-ready", HeyPixelPayload.CHANNEL.toString(), 1,
                Map.of("officialInstallSnapshot", true, "externalInstall", true));
        } catch (RuntimeException error) {
            nextEnvironmentAttemptAt = System.currentTimeMillis() + ENVIRONMENT_RETRY_MILLIS;
            trace.log("id1-context-blocked", HeyPixelPayload.CHANNEL.toString(), 1,
                Map.of("errorType", error.getClass().getSimpleName(), "retryable", true));
        } finally {
            environmentBuilding = false;
        }
    }

    private void tickInitialId1() {
        LifecycleSnapshot current = snapshot();
        Id1EnvironmentCollector currentEnvironment = environment;
        long now = System.currentTimeMillis();
        if (current == null || currentEnvironment == null || minecraft.player == null
            || minecraft.level == null) {
            return;
        }
        synchronized (lifecycleLock) {
            if (!isCurrent(current) || initialSent || initialScheduled || now < nextInitialAttemptAt) return;
            initialScheduled = true;
        }
        try {
            id1Executor.execute(() -> buildInitialId1(current, currentEnvironment));
        } catch (RuntimeException error) {
            failInitial(current);
        }
    }

    private void buildInitialId1(
        LifecycleSnapshot current,
        Id1EnvironmentCollector currentEnvironment
    ) {
        boolean queued = false;
        try {
            requireCurrent(current);
            long packetTime = System.currentTimeMillis();
            S2CPacketDecoders.Id101Challenge request = new S2CPacketDecoders.Id101Challenge(
                runtimeUuid,
                packetTime,
                Id1PacketBuilder.Id1Subtype.SPRINT,
                null
            );
            Id1BuildInput input = currentEnvironment.collect(
                request, current.connection().session(), runtimeUuid);
            Id1PacketBuilder.Challenge challenge = new Id1PacketBuilder.Challenge(
                runtimeUuid,
                packetTime,
                Id1PacketBuilder.Id1Subtype.SPRINT,
                null
            );
            Id1PacketBuilder.SprintEnvironment sprint =
                (Id1PacketBuilder.SprintEnvironment) input.subtypePayload();
            Id1PacketBuilder.BuiltPacket built =
                id1Builder.buildInitialSprint(challenge, input.context(), sprint);
            trace.log("id1-initial-built", HeyPixelPayload.CHANNEL.toString(), 1, Map.of(
                "businessLength", built.wire().length,
                "wireSha256", sha256(built.wire()),
                "loadedMods", sprint.loadedMods().size(),
                "discoveredJars", sprint.discoveredJars().size(),
                "environmentSource", sprint.source()
            ));
            requireCurrent(current);
            queued = sendBusiness(current, built.wire(), 1,
                "JOIN_INITIAL_SPRINT", SendKind.INITIAL_ID1);
        } catch (RuntimeException error) {
            trace.log("id1-initial-error", HeyPixelPayload.CHANNEL.toString(), 1,
                Map.of("errorType", error.getClass().getSimpleName()));
        } finally {
            if (!queued) failInitial(current);
        }
    }

    private void failInitial(LifecycleSnapshot current) {
        synchronized (lifecycleLock) {
            if (!isCurrent(current)) return;
            initialScheduled = false;
            nextInitialAttemptAt = System.currentTimeMillis() + INITIAL_RETRY_MILLIS;
        }
    }

    private void tickReadySync() {
        LifecycleSnapshot current = snapshot();
        if (current == null || minecraft.player == null || minecraft.level == null
            || minecraft.gameMode == null || minecraft.screen instanceof ReceivingLevelScreen) {
            return;
        }
        synchronized (lifecycleLock) {
            if (!isCurrent(current) || readySent || readyScheduled) return;
            readyScheduled = true;
        }
        if (!sendPayload(current, encodeReadySyncPayload(), -1,
            "READY_SYNC_OPCODE_12", SendKind.READY_SYNC)) {
            synchronized (lifecycleLock) {
                if (isCurrent(current)) readyScheduled = false;
            }
        }
    }

    static byte[] encodeReadySyncPayload() {
        return new byte[]{(byte) READY_SYNC_OPCODE};
    }

    private void runHeartbeat(long requiredGeneration) {
        LifecycleSnapshot current = snapshot(requiredGeneration);
        if (current == null || minecraft.player == null || minecraft.level == null) return;
        sendBusiness(
            current,
            C2SPacketEncoders.encodeHeartbeat(
                System.currentTimeMillis(), Id2CachedClock.currentTimeMillis()),
            2,
            "FIXED_RATE_5000MS",
            SendKind.BUSINESS
        );
    }

    private void tickCps() {
        LifecycleSnapshot current = snapshot();
        if (current == null || minecraft.screen != null) return;
        long now = Id2CachedClock.currentTimeMillis();
        if (now < nextCpsSendAt) return;
        int left;
        int right;
        synchronized (clickLock) {
            discardClicksBefore(leftClicks, now - CPS_WINDOW_MILLIS);
            discardClicksBefore(rightClicks, now - CPS_WINDOW_MILLIS);
            left = leftClicks.size();
            right = rightClicks.size();
        }
        if (left == lastLeftCps && right == lastRightCps) return;
        lastLeftCps = left;
        lastRightCps = right;
        nextCpsSendAt = now + CPS_COOLDOWN_MILLIS;
        if (minecraft.player == null || minecraft.level == null) return;
        sendBusiness(current, C2SPacketEncoders.encodeCpsTelemetry(
            System.currentTimeMillis(), left, right), 3, "CPS_CHANGE", SendKind.BUSINESS);
    }

    private void resetClicks() {
        synchronized (clickLock) {
            leftClicks.clear();
            rightClicks.clear();
        }
        nextCpsSendAt = 0L;
        lastLeftCps = 0;
        lastRightCps = 0;
    }

    private static void discardClicksBefore(Deque<Long> clicks, long cutoff) {
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.removeFirst();
    }

    private void enqueueDispatch(
        HeyPixelProtocolDispatcher.CanonicalDispatchResult decoded,
        boolean encrypted,
        int trailingBytes,
        LifecycleSnapshot incoming
    ) {
        try {
            ClientLogicalWork.enqueue(
                minecraft::execute,
                lifecycleLock,
                () -> isCurrent(incoming),
                () -> minecraft.player != null,
                () -> dispatcher.applyCanonical(decoded),
                () -> {
                },
                result -> {
                    if (result.outcome() != ClientLogicalWork.Outcome.ACCEPTED) {
                        trace.log("s2c-dropped", HeyPixelPayload.CHANNEL.toString(), decoded.packetId(),
                            Map.of("reason", result.outcome().name(), "retry", false));
                        return;
                    }
                    HeyPixelProtocolDispatcher.CanonicalDispatchResult applied = result.value();
                    traceDispatch(applied, encrypted, trailingBytes);
                    if (applied.kind()
                        == HeyPixelProtocolDispatcher.CanonicalKind.ENVIRONMENT_CHALLENGE) {
                        handleChallenge((S2CPacketDecoders.Id101Challenge) applied.value(), incoming);
                    }
                },
                error -> trace.log("s2c-work-error", HeyPixelPayload.CHANNEL.toString(),
                    decoded.packetId(), Map.of("errorType", error.getClass().getSimpleName()))
            );
        } catch (RuntimeException error) {
            trace.log("s2c-submit-error", HeyPixelPayload.CHANNEL.toString(), decoded.packetId(),
                Map.of("errorType", error.getClass().getSimpleName()));
        }
    }

    private void handleChallenge(
        S2CPacketDecoders.Id101Challenge challenge,
        LifecycleSnapshot incoming
    ) {
        Id1EnvironmentCollector currentEnvironment = environment;
        if (currentEnvironment == null || !isCurrent(incoming)) {
            trace.log("id1-provider-blocked", HeyPixelPayload.CHANNEL.toString(), 1,
                Map.of("environmentReady", currentEnvironment != null));
            return;
        }
        try {
            id1Executor.execute(() -> {
                try {
                    requireCurrent(incoming);
                    Id1BuildInput input = currentEnvironment.collect(
                        challenge, incoming.connection().session(), runtimeUuid);
                    if (input.subtype() != challenge.subtype()) {
                        throw new IllegalArgumentException("ID1 subtype mismatch");
                    }
                    Id1PacketBuilder.Challenge request = new Id1PacketBuilder.Challenge(
                        challenge.packetUuid(),
                        challenge.packetLong(),
                        challenge.subtype(),
                        challenge.challengeValue()
                    );
                    byte[] wire = id1Builder.buildPacket(
                        request, input.context(), input.subtypePayload());
                    requireCurrent(incoming);
                    sendBusiness(incoming, wire, 1, "S2C_ID101", SendKind.ID1_RESPONSE);
                } catch (RuntimeException error) {
                    trace.log("id1-build-error", HeyPixelPayload.CHANNEL.toString(), 1,
                        Map.of("errorType", error.getClass().getSimpleName()));
                }
            });
        } catch (RuntimeException error) {
            trace.log("id1-submit-error", HeyPixelPayload.CHANNEL.toString(), 1,
                Map.of("errorType", error.getClass().getSimpleName()));
        }
    }

    private void handleSyncToken(
        S2CPayloadUnwrapper.UnwrappedPacket packet,
        LifecycleSnapshot incoming
    ) {
        SyncTokenMetadata metadata =
            S2CPacketDecoders.decodeSyncTokenOfficialPrefix(packet.payload());
        try {
            Id114ClientWork.enqueue(
                minecraft::execute,
                lifecycleLock,
                () -> isCurrent(incoming),
                () -> minecraft.player != null,
                state::setSyncTokenMetadata,
                metadata,
                () -> trace.log("sync-token-work-start", HeyPixelPayload.CHANNEL.toString(), 114,
                    Map.of("execution", "client-logical-work")),
                outcome -> trace.log(
                    outcome == Id114ClientWork.Outcome.ACCEPTED
                        ? "sync-token-cached" : "sync-token-dropped",
                    HeyPixelPayload.CHANNEL.toString(),
                    114,
                    outcome == Id114ClientWork.Outcome.ACCEPTED
                        ? metadata.traceDetails()
                        : Map.of("reason", outcome.name(), "retry", false)
                ),
                error -> trace.log("sync-token-work-error", HeyPixelPayload.CHANNEL.toString(), 114,
                    Map.of("errorType", error.getClass().getSimpleName()))
            );
        } catch (RuntimeException error) {
            trace.log("sync-token-submit-error", HeyPixelPayload.CHANNEL.toString(), 114,
                Map.of("errorType", error.getClass().getSimpleName()));
        }
    }

    private void traceDispatch(
        HeyPixelProtocolDispatcher.CanonicalDispatchResult result,
        boolean encrypted,
        int trailingBytes
    ) {
        trace.log("s2c-dispatch", HeyPixelPayload.CHANNEL.toString(), result.packetId(), Map.of(
            "length", result.payloadLength(),
            "kind", result.kind().name(),
            "semantic", HeyPixelPacketSemantics.canonicalName(
                HeyPixelPacketSemantics.Direction.S2C, result.packetId()),
            "encrypted", encrypted,
            "trailingBytes", trailingBytes
        ));
    }

    private boolean sendBusiness(
        LifecycleSnapshot current,
        byte[] businessWire,
        int packetId,
        String trigger,
        SendKind kind
    ) {
        if (businessWire == null) return false;
        return sendPayload(current, HeyPixelOuterBridge.wrapBinary(businessWire),
            packetId, trigger, kind);
    }

    private boolean sendPayload(
        LifecycleSnapshot current,
        byte[] channelWire,
        int packetId,
        String trigger,
        SendKind kind
    ) {
        if (channelWire == null) return false;
        ServerboundCustomPayloadPacket packet =
            new ServerboundCustomPayloadPacket(new HeyPixelPayload(channelWire));
        PendingSend pending = new PendingSend(
            current,
            packet,
            kind,
            packetId,
            Objects.requireNonNullElse(trigger, "")
        );
        try {
            synchronized (lifecycleLock) {
                if (!isCurrentLocked(current)) return false;
                current.connection().transport().send(packet, new PacketSendListener() {
                    @Override
                    public void onSuccess() {
                        completeSend(pending);
                    }

                    @Override
                    public Packet<?> onFailure() {
                        failSend(pending);
                        return null;
                    }
                });
            }
            trace.log("c2s-send", HeyPixelPayload.CHANNEL.toString(),
                packetId < 0 ? null : packetId, Map.of(
                    "length", channelWire.length,
                    "trigger", trigger,
                    "kind", kind.name()
                ));
            return true;
        } catch (RuntimeException error) {
            failSend(pending);
            trace.log("c2s-send-error", HeyPixelPayload.CHANNEL.toString(),
                packetId < 0 ? null : packetId,
                Map.of("trigger", trigger, "errorType", error.getClass().getSimpleName()));
            return false;
        }
    }

    private void completeSend(PendingSend pending) {
        synchronized (lifecycleLock) {
            if (!isCurrentLocked(pending.lifecycle())) return;
            switch (pending.kind()) {
                case INITIAL_ID1 -> {
                    initialScheduled = false;
                    initialSent = true;
                }
                case READY_SYNC -> {
                    readyScheduled = false;
                    readySent = true;
                }
                case BUSINESS, ID1_RESPONSE -> {
                }
            }
        }
        trace.log("c2s-final-write", HeyPixelPayload.CHANNEL.toString(),
            pending.packetId() < 0 ? null : pending.packetId(), Map.of(
                "trigger", pending.trigger(),
                "kind", pending.kind().name()
            ));
        if (pending.kind() == SendKind.ID1_RESPONSE) {
            trace.logPacketResponse(
                HeyPixelPayload.CHANNEL.toString(),
                101,
                1,
                pending.trigger(),
                "final-write"
            );
        }
    }

    private void failSend(PendingSend pending) {
        synchronized (lifecycleLock) {
            if (!isCurrentLocked(pending.lifecycle())) return;
            if (pending.kind() == SendKind.INITIAL_ID1) {
                initialScheduled = false;
                nextInitialAttemptAt = System.currentTimeMillis() + INITIAL_RETRY_MILLIS;
            } else if (pending.kind() == SendKind.READY_SYNC) {
                readyScheduled = false;
            }
        }
    }

    private void requireCurrent(LifecycleSnapshot current) {
        if (!isCurrent(current)) throw new IllegalStateException("stale protocol lifecycle");
    }

    private static TargetIdentity identityOf(ActiveConnection connection) {
        return connection == null ? null : connection.identity();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private enum SendKind {
        BUSINESS,
        ID1_RESPONSE,
        INITIAL_ID1,
        READY_SYNC
    }

    private record ActiveConnection(
        ClientPacketListener handler,
        Connection transport,
        ProtocolSessionSnapshot session,
        TargetIdentity identity
    ) {
        private ActiveConnection {
            Objects.requireNonNull(handler, "handler");
            Objects.requireNonNull(transport, "transport");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(identity, "identity");
        }
    }

    private record LifecycleSnapshot(long generation, ActiveConnection connection) {
    }

    private record TargetIdentity(String targetHost, int targetPort, String sessionSha256) {
        private static TargetIdentity from(ProxyTargetResolver.ResolvedTarget target) {
            ProtocolSessionSnapshot session = target.session().orElseThrow();
            return new TargetIdentity(
                ProtocolSessionProvider.normalizeHost(target.targetHost()),
                target.targetPort(),
                sha256(ProtocolSessionProvider.canonical(session).getBytes(StandardCharsets.UTF_8))
            );
        }
    }

    private record PendingSend(
        LifecycleSnapshot lifecycle,
        Packet<?> packet,
        SendKind kind,
        int packetId,
        String trigger
    ) {
    }
}
