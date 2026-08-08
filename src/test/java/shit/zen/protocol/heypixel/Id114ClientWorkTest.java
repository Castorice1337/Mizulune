package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class Id114ClientWorkTest {
    private static final String SYNTHETIC_TOKEN = "synthetic-id114-test-value";

    @Test
    void queuedWorkCallsNativeOutsideLockAndCommitsAvailableMetadataInsideLock() {
        Object lifecycleLock = new Object();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicReference<SyncTokenMetadata> committed = new AtomicReference<>();
        AtomicInteger acceptedCallbacks = new AtomicInteger();
        AtomicReference<Id114ClientWork.Result> result = new AtomicReference<>();
        FakeSink sink = FakeSink.ready();
        sink.acceptAction = () -> assertFalse(Thread.holdsLock(lifecycleLock));

        Id114ClientWork.enqueue(
            queued::set,
            lifecycleLock,
            () -> true,
            () -> true,
            () -> true,
            sink,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            metadata -> {
                assertTrue(Thread.holdsLock(lifecycleLock));
                committed.set(metadata);
            },
            ignored -> acceptedCallbacks.incrementAndGet(),
            () -> { },
            result::set,
            error -> fail(error)
        );

        assertNotNull(queued.get());
        assertNull(committed.get());
        queued.get().run();
        assertEquals(1, sink.accepts.get());
        assertEquals(1, acceptedCallbacks.get());
        assertTrue(committed.get().nativeSinkAvailable());
        assertEquals(Id114ClientWork.Outcome.ACCEPTED, result.get().outcome());
    }

    @Test
    void unverifiedInvocationNeverReportsReadyButStillRegistersLogout() {
        Object lifecycleLock = new Object();
        AtomicReference<SyncTokenMetadata> committed = new AtomicReference<>();
        AtomicReference<Id114NativeSink> registeredLogout = new AtomicReference<>();
        FakeSink sink = FakeSink.callbackReadinessUnverified();

        Id114ClientWork.Result result = Id114ClientWork.apply(
            lifecycleLock,
            () -> true,
            () -> true,
            () -> true,
            sink,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            committed::set,
            registeredLogout::set
        );

        assertEquals(
            Id114ClientWork.Outcome.NATIVE_ACCEPT_INVOKED_UNVERIFIED,
            result.outcome()
        );
        assertEquals(
            Id114NativeSink.Reason.CALLBACK_READINESS_UNVERIFIED,
            result.nativeReason()
        );
        assertFalse(committed.get().nativeSinkAvailable());
        assertEquals(sink, registeredLogout.get());
        registeredLogout.get().logout();
        assertEquals(1, sink.logouts.get());
    }

    @Test
    void duplicateRunnableCannotReuseTheLeaseOrRepeatNativeAccept() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        List<Id114ClientWork.Result> results = new ArrayList<>();
        AtomicInteger commits = new AtomicInteger();
        FakeSink sink = FakeSink.ready();

        Id114ClientWork.enqueue(
            queued::set,
            new Object(),
            () -> true,
            () -> true,
            () -> true,
            sink,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            ignored -> commits.incrementAndGet(),
            ignored -> { },
            () -> { },
            results::add,
            error -> fail(error)
        );

        queued.get().run();
        queued.get().run();
        assertEquals(1, sink.accepts.get());
        assertEquals(1, commits.get());
        assertEquals(List.of(
            Id114ClientWork.Outcome.ACCEPTED,
            Id114ClientWork.Outcome.DUPLICATE_WORK
        ), results.stream().map(Id114ClientWork.Result::outcome).toList());
    }

    @Test
    void playerNullAndStaleGenerationNeverTouchTheSinkOrCommit() {
        for (boolean stale : List.of(false, true)) {
            AtomicBoolean playerChecked = new AtomicBoolean();
            AtomicInteger commits = new AtomicInteger();
            FakeSink sink = FakeSink.ready();
            Id114ClientWork.Result result = Id114ClientWork.apply(
                new Object(),
                () -> !stale,
                () -> {
                    playerChecked.set(true);
                    return stale;
                },
                () -> true,
                sink,
                Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
                ignored -> commits.incrementAndGet(),
                ignored -> { }
            );
            assertEquals(stale
                ? Id114ClientWork.Outcome.STALE_GENERATION
                : Id114ClientWork.Outcome.PLAYER_UNAVAILABLE, result.outcome());
            assertEquals(stale, !playerChecked.get());
            assertEquals(0, sink.accepts.get());
            assertEquals(0, commits.get());
        }
    }

    @Test
    void disabledOrUnavailableNativeCommitsOnlyUnavailableMetadata() {
        AtomicReference<SyncTokenMetadata> disabledMetadata = new AtomicReference<>();
        FakeSink ready = FakeSink.ready();
        Id114ClientWork.Result disabled = Id114ClientWork.apply(
            new Object(), () -> true, () -> true, () -> false, ready,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            disabledMetadata::set, ignored -> fail("native must not be accepted"));
        assertEquals(Id114ClientWork.Outcome.METADATA_ONLY_NATIVE_DISABLED, disabled.outcome());
        assertFalse(disabledMetadata.get().nativeSinkAvailable());
        assertEquals(0, ready.accepts.get());

        AtomicReference<SyncTokenMetadata> unavailableMetadata = new AtomicReference<>();
        FakeSink unavailable = FakeSink.unavailable(Id114NativeSink.Reason.JVM_HASH_UNSUPPORTED);
        Id114ClientWork.Result unavailableResult = Id114ClientWork.apply(
            new Object(), () -> true, () -> true, () -> true, unavailable,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            unavailableMetadata::set, ignored -> fail("native must not be accepted"));
        assertEquals(Id114ClientWork.Outcome.METADATA_ONLY_NATIVE_UNAVAILABLE,
            unavailableResult.outcome());
        assertEquals(Id114NativeSink.Reason.JVM_HASH_UNSUPPORTED,
            unavailableResult.nativeReason());
        assertFalse(unavailableMetadata.get().nativeSinkAvailable());
        assertEquals(0, unavailable.accepts.get());
    }

    @Test
    void nativeAcceptFailureDoesNotCommitMetadata() {
        FakeSink sink = FakeSink.ready();
        sink.acceptFailure = Id114NativeSink.Reason.ACCEPT_FAILED;
        AtomicInteger commits = new AtomicInteger();
        Id114ClientWork.Result result = Id114ClientWork.apply(
            new Object(), () -> true, () -> true, () -> true, sink,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            ignored -> commits.incrementAndGet(), ignored -> fail("must not register logout"));
        assertEquals(Id114ClientWork.Outcome.NATIVE_ACCEPT_FAILED, result.outcome());
        assertEquals(0, commits.get());
        assertEquals(1, sink.accepts.get());
    }

    @Test
    void generationChangeDuringNativeAcceptUsesOneCompensatingLogout() {
        AtomicBoolean generation = new AtomicBoolean(true);
        AtomicInteger commits = new AtomicInteger();
        FakeSink sink = FakeSink.ready();
        sink.acceptAction = () -> generation.set(false);

        Id114ClientWork.Result result = Id114ClientWork.apply(
            new Object(), generation::get, () -> true, () -> true, sink,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            ignored -> commits.incrementAndGet(), ignored -> fail("must not register logout"));
        assertEquals(Id114ClientWork.Outcome.STALE_AFTER_NATIVE_ACCEPT, result.outcome());
        assertEquals(1, sink.accepts.get());
        assertEquals(1, sink.logouts.get());
        assertEquals(0, commits.get());
    }

    @Test
    void generationIsRevalidatedAfterPotentiallySlowAvailabilityCheck() {
        AtomicBoolean generation = new AtomicBoolean(true);
        AtomicInteger commits = new AtomicInteger();
        FakeSink sink = FakeSink.ready();
        sink.availabilityAction = () -> generation.set(false);

        Id114ClientWork.Result result = Id114ClientWork.apply(
            new Object(), generation::get, () -> true, () -> true, sink,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            ignored -> commits.incrementAndGet(), ignored -> fail("must not register logout"));
        assertEquals(Id114ClientWork.Outcome.STALE_GENERATION, result.outcome());
        assertEquals(0, sink.accepts.get());
        assertEquals(0, commits.get());
    }

    @Test
    void executorRejectionClearsLeaseAndNeverStartsWork() {
        Id114TokenLease lease = Id114TokenLease.fromToken(SYNTHETIC_TOKEN);
        AtomicBoolean started = new AtomicBoolean();
        FakeSink sink = FakeSink.ready();

        assertThrows(RejectedExecutionException.class, () -> Id114ClientWork.enqueue(
            work -> { throw new RejectedExecutionException("synthetic-rejection"); },
            new Object(), () -> true, () -> true, () -> true, sink, lease,
            ignored -> fail("commit must not run"), ignored -> fail("accept must not run"),
            () -> started.set(true), ignored -> fail("completion must not run"),
            error -> fail(error)
        ));

        assertFalse(started.get());
        assertEquals(Id114ClientWork.Outcome.DUPLICATE_WORK,
            Id114ClientWork.apply(
                new Object(), () -> true, () -> true, () -> true, sink, lease,
                ignored -> fail("commit must not run"), ignored -> fail("accept must not run")
            ).outcome());
        assertEquals(0, sink.accepts.get());
    }

    @Test
    void startedFailureIsReportedWithoutLeakingIntoCommit() {
        RuntimeException expected = new IllegalStateException("synthetic-work-failure");
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicInteger commits = new AtomicInteger();
        FakeSink sink = FakeSink.ready();

        assertDoesNotThrow(() -> Id114ClientWork.enqueue(
            Runnable::run,
            new Object(), () -> true, () -> true, () -> true, sink,
            Id114TokenLease.fromToken(SYNTHETIC_TOKEN),
            ignored -> commits.incrementAndGet(), ignored -> fail("must not register logout"),
            () -> { throw expected; }, ignored -> fail("completion must not run"), failure::set
        ));

        assertEquals(expected, failure.get());
        assertEquals(0, commits.get());
        assertEquals(0, sink.accepts.get());
    }

    @Test
    void normalizedLeaseResultAndMetadataNeverRenderTheSyntheticRawValue() {
        Id114TokenLease lease = Id114TokenLease.fromToken(SYNTHETIC_TOKEN);
        AtomicReference<SyncTokenMetadata> committed = new AtomicReference<>();
        Id114ClientWork.Result result = Id114ClientWork.apply(
            new Object(), () -> true, () -> true, () -> false, FakeSink.ready(), lease,
            committed::set, ignored -> fail("native must not be accepted"));

        assertFalse(lease.toString().contains(SYNTHETIC_TOKEN));
        assertFalse(result.toString().contains(SYNTHETIC_TOKEN));
        assertFalse(committed.get().toString().contains(SYNTHETIC_TOKEN));
        assertFalse(committed.get().traceDetails().toString().contains(SYNTHETIC_TOKEN));
    }

    private static final class FakeSink implements Id114NativeSink {
        private final Availability availability;
        private final AtomicInteger accepts = new AtomicInteger();
        private final AtomicInteger logouts = new AtomicInteger();
        private Runnable availabilityAction = () -> { };
        private Runnable acceptAction = () -> { };
        private Reason acceptFailure;
        private AcceptResult acceptResult = AcceptResult.CONFIRMED;

        private FakeSink(Availability availability) {
            this.availability = availability;
        }

        static FakeSink ready() {
            return new FakeSink(Availability.ready());
        }

        static FakeSink unavailable(Reason reason) {
            return new FakeSink(Availability.unavailable(reason));
        }

        static FakeSink callbackReadinessUnverified() {
            FakeSink sink = new FakeSink(Availability.callbackReadinessUnverified());
            sink.acceptResult = AcceptResult.INVOKED_UNVERIFIED;
            return sink;
        }

        @Override
        public Availability availability() {
            availabilityAction.run();
            return availability;
        }

        @Override
        public AcceptResult accept(String transientToken) {
            accepts.incrementAndGet();
            acceptAction.run();
            if (acceptFailure != null) throw new InvocationException(acceptFailure);
            return acceptResult;
        }

        @Override
        public void logout() {
            logouts.incrementAndGet();
        }
    }
}
