package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HeyPixelProtocolRuntimeExecutorTest {
    @Test
    void finalWriteLedgerUsesPacketIdentityAndClearsStaleReservations() {
        HeyPixelProtocolRuntime.FinalWriteLedger<String> ledger =
            new HeyPixelProtocolRuntime.FinalWriteLedger<>();
        String packet = new String("same-value");
        String equalButDistinctPacket = new String("same-value");

        ledger.reserve(packet, "initial");
        assertEquals(1, ledger.size());
        assertNull(ledger.get(equalButDistinctPacket));
        assertNull(ledger.complete(equalButDistinctPacket));
        assertEquals("initial", ledger.complete(packet));
        assertEquals(0, ledger.size());

        ledger.reserve(packet, "first");
        assertThrows(IllegalStateException.class, () -> ledger.reserve(packet, "duplicate"));
        ledger.reserve(equalButDistinctPacket, "second");
        assertEquals(2, ledger.clear().size());
        assertEquals(0, ledger.size());
    }

    @Test
    void heartbeatUsesTheOfficialFixedRateSchedule() {
        CapturingScheduler scheduler = new CapturingScheduler();
        Runnable heartbeat = () -> { };
        try {
            ScheduledFuture<?> future =
                HeyPixelProtocolRuntime.scheduleId2Heartbeat(scheduler, heartbeat);
            assertSame(scheduler.future, future);
            assertSame(heartbeat, scheduler.command);
            assertEquals(5_000L, scheduler.initialDelay);
            assertEquals(5_000L, scheduler.period);
            assertEquals(TimeUnit.MILLISECONDS, scheduler.unit);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void heartbeatExecutorUsesAnIsolatedDaemonThread() throws Exception {
        ScheduledExecutorService executor = HeyPixelProtocolRuntime.createId2HeartbeatExecutor();
        try {
            Future<String> worker = executor.submit(() ->
                Thread.currentThread().getName() + ":" + Thread.currentThread().isDaemon());
            String descriptor = worker.get(2, TimeUnit.SECONDS);
            assertTrue(descriptor.startsWith("Mizulune-ID2-"));
            assertTrue(descriptor.endsWith(":true"));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void heartbeatReservationRejectsDisabledAndStaleSchedulerGenerations() {
        assertTrue(HeyPixelProtocolRuntime.heartbeatGenerationIsCurrent(true, 7L, 7L));
        assertFalse(HeyPixelProtocolRuntime.heartbeatGenerationIsCurrent(false, 7L, 7L));
        assertFalse(HeyPixelProtocolRuntime.heartbeatGenerationIsCurrent(true, 7L, 8L));
    }

    @Test
    void readySyncIsTheRawSingleByteOpcodeWithoutOuterBridge() {
        assertArrayEquals(new byte[]{0x0c}, HeyPixelProtocolRuntime.encodeReadySyncPayload());
    }

    @Test
    void lifecycleOneShotPendingWritesNeverExpireOnWallClock() {
        assertFalse(HeyPixelProtocolRuntime.pendingWriteExpired(
            HeyPixelProtocolRuntime.PendingWriteKind.INITIAL_ID1,
            1_000L, Long.MAX_VALUE, 15_000L));
        assertFalse(HeyPixelProtocolRuntime.pendingWriteExpired(
            HeyPixelProtocolRuntime.PendingWriteKind.READY_SYNC,
            1_000L, Long.MAX_VALUE, 15_000L));

        assertFalse(HeyPixelProtocolRuntime.pendingWriteExpired(
            HeyPixelProtocolRuntime.PendingWriteKind.BUSINESS,
            1_000L, 15_999L, 15_000L));
        assertTrue(HeyPixelProtocolRuntime.pendingWriteExpired(
            HeyPixelProtocolRuntime.PendingWriteKind.BUSINESS,
            1_000L, 16_000L, 15_000L));
        assertTrue(HeyPixelProtocolRuntime.pendingWriteExpired(
            HeyPixelProtocolRuntime.PendingWriteKind.ID1_RESPONSE,
            1_000L, 16_000L, 15_000L));
        assertThrows(IllegalArgumentException.class,
            () -> HeyPixelProtocolRuntime.pendingWriteExpired(
                HeyPixelProtocolRuntime.PendingWriteKind.BUSINESS, 0L, 0L, -1L));
    }

    @Test
    void onlyId1ChallengeWritesAreClassifiedAsPacketResponses() {
        assertEquals(Integer.valueOf(101),
            HeyPixelProtocolRuntime.PendingWriteKind.ID1_RESPONSE.responseRequestPacketId());
        assertNull(HeyPixelProtocolRuntime.PendingWriteKind.BUSINESS.responseRequestPacketId());
        assertNull(HeyPixelProtocolRuntime.PendingWriteKind.INITIAL_ID1.responseRequestPacketId());
        assertNull(HeyPixelProtocolRuntime.PendingWriteKind.READY_SYNC.responseRequestPacketId());
    }

    @Test
    void completedLoginOneShotsSurviveModuleStopStartAndResetOnLogout(
        @TempDir Path directory
    ) throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory);
        try {
            runtime.start();
            setBoolean(runtime, "initialId1Submitted", true);
            setBoolean(runtime, "readySyncSent", true);

            runtime.stop();
            assertTrue(getBoolean(runtime, "initialId1Submitted"));
            assertTrue(getBoolean(runtime, "readySyncSent"));

            runtime.start();
            assertTrue(getBoolean(runtime, "initialId1Submitted"));
            assertTrue(getBoolean(runtime, "readySyncSent"));

            runtime.stop();
            runtime.state().setSyncTokenMetadata(SyncTokenMetadata.fromToken("sync"));
            assertTrue(runtime.state().syncTokenMetadata().isPresent());
            runtime.onLoggingOut();
            assertFalse(getBoolean(runtime, "initialId1Submitted"));
            assertFalse(getBoolean(runtime, "readySyncSent"));
            assertTrue(runtime.state().syncTokenMetadata().isEmpty());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void id1ChallengeTasksCanOverlapLikeTheOfficialCachedPool() throws Exception {
        ExecutorService executor = HeyPixelProtocolRuntime.createId1Executor();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> awaitRelease(started, release));
        Future<?> second = executor.submit(() -> awaitRelease(started, release));

        try {
            assertTrue(started.await(2, TimeUnit.SECONDS),
                "independent ID101 responses must not be serialized on one worker");
        } finally {
            release.countDown();
        }

        first.get(2, TimeUnit.SECONDS);
        second.get(2, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void id1ExecutorUsesTheOfficialDefaultNonDaemonThreadFactory() throws Exception {
        ExecutorService executor = HeyPixelProtocolRuntime.createId1Executor();
        try {
            assertFalse(executor.submit(() -> Thread.currentThread().isDaemon())
                .get(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void id1TaskSnapshotRejectsEveryStaleLifecycleDimension() {
        UUID uuid = new UUID(1L, 2L);
        Object connection = new Object();
        Id1PacketBuilder builder = builder(uuid);
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot,
            HeyPixelProtocolRuntime.Id1BuildInput> input = (challenge, session) -> null;
        HeyPixelProtocolRuntime.Id1TargetIdentity target = target("pc.bjdmc.net", 25565, "session-a");
        HeyPixelProtocolRuntime.Id1TaskSnapshot snapshot = new HeyPixelProtocolRuntime.Id1TaskSnapshot(
            7L, connection, uuid, builder, input, target);

        assertTrue(snapshot.matches(true, 7L, connection, uuid, builder, input, target));
        assertFalse(snapshot.matches(false, 7L, connection, uuid, builder, input, target));
        assertFalse(snapshot.matches(true, 8L, connection, uuid, builder, input, target));
        assertFalse(snapshot.matches(true, 7L, new Object(), uuid, builder, input, target));
        assertFalse(snapshot.matches(true, 7L, connection, new UUID(3L, 4L), builder, input, target));
        assertFalse(snapshot.matches(true, 7L, connection, uuid, builder(uuid), input, target));
        assertFalse(snapshot.matches(true, 7L, connection, uuid, builder,
            (challenge, session) -> null, target));
        assertFalse(snapshot.matches(true, 7L, connection, uuid, builder, input,
            target("pc.bjdmc.net", 25566, "session-a")));
        assertFalse(snapshot.matches(true, 7L, connection, uuid, builder, input,
            target("pc.bjdmc.net", 25565, "session-b")));
    }

    @Test
    void id114GenerationTokenRejectsEveryStaleIncomingIdentityDimension() {
        Object connection = new Object();
        Object transport = new Object();
        HeyPixelProtocolRuntime.Id1TargetIdentity target =
            target("pc.bjdmc.net", 25565, "session-a");
        HeyPixelProtocolRuntime.Id114IncomingGeneration generation =
            new HeyPixelProtocolRuntime.Id114IncomingGeneration(
                7L, connection, transport, target);

        assertTrue(generation.matches(
            true, 7L, connection, transport, connection, target));
        assertFalse(generation.matches(
            false, 7L, connection, transport, connection, target));
        assertFalse(generation.matches(
            true, 8L, connection, transport, connection, target));
        assertFalse(generation.matches(
            true, 7L, new Object(), transport, connection, target));
        assertFalse(generation.matches(
            true, 7L, connection, new Object(), connection, target));
        assertFalse(generation.matches(
            true, 7L, connection, transport, new Object(), target));
        assertFalse(generation.matches(
            true, 7L, connection, transport, connection,
            target("pc.bjdmc.net", 25566, "session-a")));
        assertFalse(generation.matches(
            true, 7L, connection, transport, connection,
            target("pc.bjdmc.net", 25565, "session-b")));
    }

    @Test
    void queuedId114WorkCannotCommitAcrossActualLogout(@TempDir Path directory) throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory);
        AtomicReference<Runnable> queued = new AtomicReference<>();
            AtomicReference<Id114ClientWork.Result> outcome = new AtomicReference<>();
        AtomicInteger queuedCount = new AtomicInteger();
        AtomicBoolean playerChecked = new AtomicBoolean();
        try {
            runtime.start();
            AtomicLong generation = getAtomicLong(runtime, "id1LifecycleGeneration");
            long capturedGeneration = generation.get();
            Object connection = new Object();
            Object transport = new Object();
            HeyPixelProtocolRuntime.Id1TargetIdentity target =
                target("pc.bjdmc.net", 25565, "session-a");
            HeyPixelProtocolRuntime.Id114IncomingGeneration incoming =
                new HeyPixelProtocolRuntime.Id114IncomingGeneration(
                    capturedGeneration, connection, transport, target);
            Id114ClientWork.enqueue(
                work -> {
                    queuedCount.incrementAndGet();
                    queued.set(work);
                },
                runtime,
                () -> incoming.matches(
                    runtime.isRunning(),
                    generation.get(),
                    connection,
                    transport,
                    connection,
                    target
                ),
                () -> {
                    playerChecked.set(true);
                    return true;
                },
                () -> false,
                Id114NativeSink.unavailable(Id114NativeSink.Reason.NATIVE_DISABLED),
                Id114TokenLease.fromToken("synthetic-id114-test-value"),
                runtime.state()::setSyncTokenMetadata,
                ignored -> { },
                () -> { },
                outcome::set,
                error -> {
                    throw error;
                }
            );

            runtime.onLoggingOut();
            queued.get().run();

            assertEquals(1, queuedCount.get());
            assertEquals(Id114ClientWork.Outcome.STALE_GENERATION, outcome.get().outcome());
            assertFalse(playerChecked.get());
            assertTrue(runtime.state().syncTokenMetadata().isEmpty());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void nativeLogoutLeaseIsConsumedExactlyOnceAcrossRepeatedLogoutAndStop(
        @TempDir Path directory
    ) throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory);
        AtomicInteger logouts = new AtomicInteger();
        Id114NativeSink sink = new Id114NativeSink() {
            @Override
            public Availability availability() {
                return Availability.ready();
            }

            @Override
            public AcceptResult accept(String transientToken) {
                return AcceptResult.CONFIRMED;
            }

            @Override
            public void logout() {
                logouts.incrementAndGet();
            }
        };
        setField(runtime, "pendingId114LogoutSink", sink);

        runtime.onLoggingOut();
        runtime.onLoggingOut();
        runtime.stop();

        assertEquals(1, logouts.get());
        assertNull(getField(runtime, "pendingId114LogoutSink"));
    }

    @Test
    void realConnectionTransitionResetsLoginOneShotsButModuleRestartDoesNot(
        @TempDir Path directory
    ) throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory);
        AtomicInteger logouts = new AtomicInteger();
        Id114NativeSink sink = new Id114NativeSink() {
            @Override
            public Availability availability() {
                return Availability.ready();
            }

            @Override
            public AcceptResult accept(String transientToken) {
                return AcceptResult.CONFIRMED;
            }

            @Override
            public void logout() {
                logouts.incrementAndGet();
            }
        };
        Object firstConnection = new Object();
        Object secondConnection = new Object();

        try {
            runtime.start();
            runtime.observeConnectionLifecycle(firstConnection);
            setBoolean(runtime, "initialId1Submitted", true);
            setBoolean(runtime, "readySyncSent", true);
            setField(runtime, "pendingId114LogoutSink", sink);

            runtime.stop();
            runtime.start();
            runtime.observeConnectionLifecycle(firstConnection);
            assertTrue(getBoolean(runtime, "initialId1Submitted"));
            assertTrue(getBoolean(runtime, "readySyncSent"));
            assertEquals(0, logouts.get());

            runtime.state().setSyncTokenMetadata(SyncTokenMetadata.fromToken("sync"));
            long generationBeforeTransition =
                getAtomicLong(runtime, "id1LifecycleGeneration").get();
            runtime.observeConnectionLifecycle(secondConnection);

            assertFalse(getBoolean(runtime, "initialId1Submitted"));
            assertFalse(getBoolean(runtime, "readySyncSent"));
            assertTrue(runtime.state().syncTokenMetadata().isEmpty());
            assertTrue(getAtomicLong(runtime, "id1LifecycleGeneration").get()
                > generationBeforeTransition);
            assertEquals(1, logouts.get());
            assertNull(getField(runtime, "pendingId114LogoutSink"));

            runtime.observeConnectionLifecycle(secondConnection);
            assertEquals(1, logouts.get());
        } finally {
            runtime.onLoggingOut();
            runtime.stop();
        }
    }

    @Test
    void id1BuildFailureClassificationIsStableAndDoesNotExposeIdentityValues() {
        assertEquals(
            "LAUNCHER_SESSION_IDENTITY_CONFLICT",
            HeyPixelProtocolRuntime.classifyId1BuildFailure(new IllegalStateException(
                "official launcher and signed session UserId identities differ"))
        );
        assertEquals(
            "SIGNED_SESSION_USER_ID_INVALID",
            HeyPixelProtocolRuntime.classifyId1BuildFailure(new IllegalStateException(
                "signed Fantnel userId is not a signed long"))
        );
        assertEquals(
            "STALE_LIFECYCLE",
            HeyPixelProtocolRuntime.classifyId1BuildFailure(new IllegalStateException(
                "ID1 task belongs to a stale lifecycle"))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void logoutImmediatelyClearsEveryQueuedRawId114Lease(@TempDir Path directory)
        throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory);
        Id114TokenLease lease = Id114TokenLease.fromToken("synthetic-id114-test-value");
        Set<Id114TokenLease> pending =
            (Set<Id114TokenLease>) getField(runtime, "pendingId114TokenLeases");
        pending.add(lease);

        runtime.onLoggingOut();

        assertTrue(pending.isEmpty());
        assertNull(lease.take());
    }

    @Test
    void initialId1AttemptTokenBindsGenerationConnectionAndTargetIdentity() {
        Object connection = new Object();
        HeyPixelProtocolRuntime.Id1TargetIdentity target = target("pc.bjdmc.net", 25565, "session-a");
        HeyPixelProtocolRuntime.Id1InitialAttempt attempt =
            new HeyPixelProtocolRuntime.Id1InitialAttempt(7L, connection, target);

        assertTrue(attempt.matches(new HeyPixelProtocolRuntime.Id1InitialAttempt(7L, connection, target)));
        assertFalse(attempt.matches(new HeyPixelProtocolRuntime.Id1InitialAttempt(8L, connection, target)));
        assertFalse(attempt.matches(new HeyPixelProtocolRuntime.Id1InitialAttempt(7L, new Object(), target)));
        assertFalse(attempt.matches(new HeyPixelProtocolRuntime.Id1InitialAttempt(
            7L, connection, target("pc.bjdmc.net", 25566, "session-a"))));
        assertFalse(attempt.matches(null));
    }

    @Test
    void targetCacheIncludingEmptyResultsExpiresAndForcedRefreshBypassesIt() {
        assertTrue(HeyPixelProtocolRuntime.canReuseTargetCache(
            false, "127.0.0.1:25565", "127.0.0.1:25565", 1_000L, 2_000L));
        assertFalse(HeyPixelProtocolRuntime.canReuseTargetCache(
            false, "127.0.0.1:25565", "127.0.0.1:25565", 2_000L, 2_000L));
        assertFalse(HeyPixelProtocolRuntime.canReuseTargetCache(
            true, "127.0.0.1:25565", "127.0.0.1:25565", 1_000L, 2_000L));
        assertFalse(HeyPixelProtocolRuntime.canReuseTargetCache(
            false, "127.0.0.1:25566", "127.0.0.1:25565", 1_000L, 2_000L));
    }

    @Test
    void id1ProviderConfigurationRejectsHalfConfiguredPairs() {
        UUID uuid = new UUID(3L, 4L);
        Id1PacketBuilder builder = builder(uuid);
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot,
            HeyPixelProtocolRuntime.Id1BuildInput> input = (challenge, session) -> null;

        HeyPixelProtocolRuntime.requireCompleteId1Provider(null, null);
        HeyPixelProtocolRuntime.requireCompleteId1Provider(builder, input);
        assertThrows(IllegalArgumentException.class,
            () -> HeyPixelProtocolRuntime.requireCompleteId1Provider(builder, null));
        assertThrows(IllegalArgumentException.class,
            () -> HeyPixelProtocolRuntime.requireCompleteId1Provider(null, input));
    }

    @Test
    void explicitLayoutConfigurationNeverFallsBackFromInvalidInput(@TempDir Path directory) throws Exception {
        Path installRoot = Files.createDirectories(directory.resolve(".minecraft"));
        Path instance = Files.createDirectories(installRoot.resolve("heypixel"));

        HeyPixelProtocolRuntime.LayoutConfiguration valid = HeyPixelProtocolRuntime.explicitLayout(
            installRoot.toString(), instance.toString());
        assertTrue(valid.valid());
        assertEquals(installRoot.toAbsolutePath().normalize(), valid.layout().installRoot());
        assertEquals(instance.toAbsolutePath().normalize(), valid.layout().instanceDirectory());

        HeyPixelProtocolRuntime.LayoutConfiguration automatic = HeyPixelProtocolRuntime.explicitLayout("", "");
        assertTrue(automatic.valid());
        assertNull(automatic.layout());

        HeyPixelProtocolRuntime.LayoutConfiguration partial = HeyPixelProtocolRuntime.explicitLayout(
            installRoot.toString(), "");
        assertFalse(partial.valid());
        assertNull(partial.layout());

        HeyPixelProtocolRuntime.LayoutConfiguration malformed = HeyPixelProtocolRuntime.explicitLayout(
            "\u0000", instance.toString());
        assertFalse(malformed.valid());
        assertNull(malformed.layout());

        HeyPixelProtocolRuntime.LayoutConfiguration missing = HeyPixelProtocolRuntime.explicitLayout(
            installRoot.resolve("missing-root").toString(), instance.toString());
        assertFalse(missing.valid());
        assertNull(missing.layout());

        Path unrelated = Files.createDirectories(directory.resolve("other-instance"));
        HeyPixelProtocolRuntime.LayoutConfiguration unrelatedPair = HeyPixelProtocolRuntime.explicitLayout(
            installRoot.toString(), unrelated.toString());
        assertFalse(unrelatedPair.valid());
        assertNull(unrelatedPair.layout());
    }

    @Test
    void startPreflightsAndFreezesTheAutomaticNativeSink(@TempDir Path directory)
        throws Exception {
        Path installRoot = Files.createDirectories(directory.resolve("official-root"));
        Files.createDirectories(installRoot.resolve("native"));
        Path instance = Files.createDirectories(installRoot.resolve("heypixel"));
        AtomicReference<HeyPixelInstallLayout> capturedLayout = new AtomicReference<>();
        AtomicInteger sinkCreations = new AtomicInteger();
        String installKey = "mizulune.heypixel.installRoot";
        String instanceKey = "mizulune.heypixel.instanceDir";
        String previousInstall = System.getProperty(installKey);
        String previousInstance = System.getProperty(instanceKey);
        try {
            System.setProperty(installKey, installRoot.toString());
            System.setProperty(instanceKey, instance.toString());
            HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(
                null,
                directory.resolve("config"),
                layout -> {
                    sinkCreations.incrementAndGet();
                    capturedLayout.set(layout);
                    return Id114NativeSink.unavailable(Id114NativeSink.Reason.NATIVE_DISABLED);
                }
            );

            runtime.configure(
                "pc.bjdmc.net,*.bjdmc.net",
                false,
                true,
                false,
                true,
                "",
                "",
                "",
                "",
                false,
                "",
                true
            );

            assertNull(capturedLayout.get(), "configure must not late-load the native sink");
            runtime.start();
            assertEquals(installRoot.toAbsolutePath().normalize(),
                capturedLayout.get().installRoot());
            assertEquals(instance.toAbsolutePath().normalize(),
                capturedLayout.get().instanceDirectory());
            assertEquals(1, sinkCreations.get());

            runtime.stop();
            Path otherRoot = Files.createDirectories(directory.resolve("other-root"));
            Files.createDirectories(otherRoot.resolve("native"));
            Path otherInstance = Files.createDirectories(otherRoot.resolve("heypixel"));
            runtime.configure(
                "pc.bjdmc.net,*.bjdmc.net",
                false,
                true,
                false,
                true,
                otherRoot.toString(),
                otherInstance.toString(),
                "",
                "",
                false,
                "",
                true
            );
            runtime.start();
            assertEquals(1, sinkCreations.get(),
                "a process must not swap native identity after startup preflight");
            assertEquals(installRoot.toAbsolutePath().normalize(),
                capturedLayout.get().installRoot());
            runtime.stop();
        } finally {
            restoreProperty(installKey, previousInstall);
            restoreProperty(instanceKey, previousInstance);
        }
    }

    @Test
    void automaticLayoutSourcesAlsoDriveTheId1EnvironmentMode(@TempDir Path directory)
        throws Exception {
        Path installRoot = Files.createDirectories(directory.resolve("official-root"));
        Path officialInstance = Files.createDirectories(installRoot.resolve("heypixel"));
        Path currentInstance = Files.createDirectories(directory.resolve("alternative-instance"));
        String installKey = "mizulune.heypixel.installRoot";
        String instanceKey = "mizulune.heypixel.instanceDir";
        String previousInstall = System.getProperty(installKey);
        String previousInstance = System.getProperty(instanceKey);
        try {
            System.setProperty(installKey, installRoot.toString());
            System.setProperty(instanceKey, officialInstance.toString());

            HeyPixelInstallLayout resolved = HeyPixelProtocolRuntime.resolveId1Layout(
                HeyPixelProtocolRuntime.LayoutConfiguration.automatic());

            assertEquals(installRoot.toAbsolutePath().normalize(), resolved.installRoot());
            assertEquals(officialInstance.toAbsolutePath().normalize(),
                resolved.instanceDirectory());
            assertEquals(HeyPixelProtocolRuntime.Id1EnvironmentMode.EXTERNAL_OFFICIAL_INSTALL,
                HeyPixelProtocolRuntime.id1EnvironmentMode(resolved, currentInstance));
        } finally {
            restoreProperty(installKey, previousInstall);
            restoreProperty(instanceKey, previousInstance);
        }
    }

    @Test
    void selectsExternalSnapshotsOnlyWhenTheConfiguredOfficialInstanceDiffers(
        @TempDir Path directory
    ) throws Exception {
        Path installRoot = Files.createDirectories(directory.resolve("official-root"));
        Path officialInstance = Files.createDirectories(installRoot.resolve("heypixel"));
        Path hmclInstance = Files.createDirectories(directory.resolve("hmcl-instance"));
        HeyPixelInstallLayout layout = HeyPixelInstallLayout.fromPaths(
            installRoot, officialInstance);

        assertEquals(HeyPixelProtocolRuntime.Id1EnvironmentMode.SAME_JVM,
            HeyPixelProtocolRuntime.id1EnvironmentMode(layout, officialInstance));
        assertEquals(HeyPixelProtocolRuntime.Id1EnvironmentMode.EXTERNAL_OFFICIAL_INSTALL,
            HeyPixelProtocolRuntime.id1EnvironmentMode(layout, hmclInstance));
        assertEquals(HeyPixelProtocolRuntime.Id1EnvironmentMode.SAME_JVM,
            HeyPixelProtocolRuntime.id1EnvironmentMode(null, hmclInstance));
    }

    @Test
    void validatesExternalOfficialRuntimePathsWithoutEchoingThem(@TempDir Path directory)
        throws Exception {
        Path userDirectory = Files.createDirectories(directory.resolve("official-user-dir"));
        Path javaHome = Files.createDirectories(directory.resolve("official-java"));
        Files.createDirectories(javaHome.resolve("bin"));
        Files.write(javaHome.resolve("bin").resolve("java.exe"), new byte[]{0});

        HeyPixelProtocolRuntime.OfficialRuntimeConfiguration configured =
            HeyPixelProtocolRuntime.officialRuntime(
                userDirectory.toString(), javaHome.toString());
        assertTrue(configured.valid());
        assertTrue(configured.configured());
        assertEquals("", configured.externalBlockingError());

        HeyPixelProtocolRuntime.OfficialRuntimeConfiguration partial =
            HeyPixelProtocolRuntime.officialRuntime(userDirectory.toString(), "");
        assertFalse(partial.valid());
        assertFalse(partial.error().contains(userDirectory.toString()));

        HeyPixelProtocolRuntime.OfficialRuntimeConfiguration missingJava =
            HeyPixelProtocolRuntime.officialRuntime(
                userDirectory.toString(), directory.resolve("private-java-path").toString());
        assertFalse(missingJava.valid());
        assertFalse(missingJava.error().contains(directory.toString()));
    }

    @Test
    void blankGuiPathsCanUseLauncherSuppliedOfficialRuntimePaths(@TempDir Path directory)
        throws Exception {
        Path userDirectory = Files.createDirectories(directory.resolve("official-user-dir"));
        Path javaHome = Files.createDirectories(directory.resolve("official-java"));
        Files.createDirectories(javaHome.resolve("bin"));
        Files.write(javaHome.resolve("bin").resolve("java.exe"), new byte[]{0});
        String userKey = HeyPixelProtocolRuntime.ID1_OFFICIAL_USER_DIRECTORY_PROPERTY;
        String javaKey = HeyPixelProtocolRuntime.ID1_OFFICIAL_JAVA_HOME_PROPERTY;
        String previousUser = System.getProperty(userKey);
        String previousJava = System.getProperty(javaKey);
        try {
            System.setProperty(userKey, userDirectory.toString());
            System.setProperty(javaKey, javaHome.toString());

            HeyPixelProtocolRuntime.OfficialRuntimeConfiguration automatic =
                HeyPixelProtocolRuntime.officialRuntime("", "");

            assertTrue(automatic.valid());
            assertTrue(automatic.configured());
            assertEquals(userDirectory.toAbsolutePath().normalize(), automatic.userDirectory());
            assertEquals(javaHome.toAbsolutePath().normalize(), automatic.javaHome());
        } finally {
            restoreProperty(userKey, previousUser);
            restoreProperty(javaKey, previousJava);
        }
    }

    @Test
    void environmentConfigurationChangesRemainDeferredForAnExistingStartupSnapshot(
        @TempDir Path directory
    ) throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory.resolve("config"));
        UUID uuid = new UUID(3L, 4L);
        Id1PacketBuilder builder = builder(uuid);
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot,
            HeyPixelProtocolRuntime.Id1BuildInput> input = (challenge, session) -> null;
        Path installRoot = Files.createDirectories(directory.resolve("official-root"));
        Path instance = Files.createDirectories(installRoot.resolve("heypixel"));
        Path userDirectory = Files.createDirectories(directory.resolve("official-user-dir"));
        Path javaHome = Files.createDirectories(directory.resolve("official-java"));
        Files.createDirectories(javaHome.resolve("bin"));
        Files.write(javaHome.resolve("bin").resolve("java.exe"), new byte[]{0});

        try {
            runtime.start();
            runtime.configureId1(builder, input);
            setBoolean(runtime, "initialId1Submitted", true);
            long epoch = getAtomicLong(runtime, "id1ContextEpoch").get();

            runtime.configure(
                "pc.bjdmc.net,*.bjdmc.net",
                false,
                true,
                false,
                true,
                installRoot.toString(),
                instance.toString(),
                userDirectory.toString(),
                javaHome.toString(),
                false,
                ""
            );

            assertEquals(epoch, getAtomicLong(runtime, "id1ContextEpoch").get());
            assertSame(builder, getField(runtime, "id1Builder"));
            assertTrue(getBoolean(runtime, "initialId1Submitted"));
        } finally {
            runtime.stop();
        }
    }

    @Test
    void hwidConfigurationChangesRebuildAnUnusedStartupSnapshot(@TempDir Path directory)
        throws Exception {
        HeyPixelProtocolRuntime runtime =
            new HeyPixelProtocolRuntime(null, directory.resolve("config"));
        UUID uuid = new UUID(5L, 6L);
        Id1PacketBuilder builder = builder(uuid);
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot,
            HeyPixelProtocolRuntime.Id1BuildInput> input = (challenge, session) -> null;

        try {
            runtime.start();
            runtime.configureId1(builder, input);
            long epoch = getAtomicLong(runtime, "id1ContextEpoch").get();

            runtime.configure(
                "pc.bjdmc.net,*.bjdmc.net",
                false,
                true,
                false,
                true,
                "",
                true,
                "default"
            );

            assertTrue(getAtomicLong(runtime, "id1ContextEpoch").get() > epoch);
            assertNull(getField(runtime, "id1Builder"));
            assertNull(getField(runtime, "id1Input"));
            assertFalse(getBoolean(runtime, "id1ContextUsed"));
            assertFalse(getBoolean(runtime, "initialId1Submitted"));
            assertEquals(
                new Id1HwidProvider.Settings(true, "default"),
                getField(runtime, "hwidSettings")
            );
        } finally {
            runtime.stop();
        }
    }

    @Test
    void hwidConfigurationChangesRemainDeferredAfterFirstContextUse(@TempDir Path directory)
        throws Exception {
        HeyPixelProtocolRuntime runtime =
            new HeyPixelProtocolRuntime(null, directory.resolve("config"));
        UUID uuid = new UUID(7L, 8L);
        Id1PacketBuilder builder = builder(uuid);
        BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot,
            HeyPixelProtocolRuntime.Id1BuildInput> input = (challenge, session) -> null;

        try {
            runtime.start();
            runtime.configureId1(builder, input);
            setBoolean(runtime, "id1ContextUsed", true);
            long epoch = getAtomicLong(runtime, "id1ContextEpoch").get();

            runtime.configure(
                "pc.bjdmc.net,*.bjdmc.net",
                false,
                true,
                false,
                true,
                "",
                true,
                "default"
            );

            assertEquals(epoch, getAtomicLong(runtime, "id1ContextEpoch").get());
            assertSame(builder, getField(runtime, "id1Builder"));
            assertSame(input, getField(runtime, "id1Input"));
            assertTrue(getBoolean(runtime, "id1ContextUsed"));
            assertEquals(
                new Id1HwidProvider.Settings(true, "default"),
                getField(runtime, "hwidSettings")
            );
        } finally {
            runtime.stop();
        }
    }

    private static Id1PacketBuilder builder(UUID uuid) {
        PbeMd5DesId1Crypto crypto = new PbeMd5DesId1Crypto(uuid);
        return new Id1PacketBuilder(
            new Id1RuntimeSignatureProvider(crypto),
            crypto,
            Id1PacketBuilder.EvidenceSampler.preserveOrder(),
            value -> value
        );
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static HeyPixelProtocolRuntime.Id1TargetIdentity target(
        String host,
        int port,
        String sessionSha256
    ) {
        return new HeyPixelProtocolRuntime.Id1TargetIdentity(
            "127.0.0.1:25565", host, port, true, sessionSha256);
    }

    private static void awaitRelease(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("ID1 worker interrupted", interrupted);
        }
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static AtomicLong getAtomicLong(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (AtomicLong) field.get(target);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CapturingScheduler extends ScheduledThreadPoolExecutor {
        private final ScheduledFuture<Object> future = new NoOpScheduledFuture();
        private Runnable command;
        private long initialDelay;
        private long period;
        private TimeUnit unit;

        private CapturingScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable command,
            long initialDelay,
            long period,
            TimeUnit unit
        ) {
            this.command = command;
            this.initialDelay = initialDelay;
            this.period = period;
            this.unit = unit;
            return future;
        }
    }

    private static final class NoOpScheduledFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0L;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
