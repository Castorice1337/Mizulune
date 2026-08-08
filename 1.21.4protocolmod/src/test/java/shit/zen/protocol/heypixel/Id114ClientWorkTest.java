package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class Id114ClientWorkTest {
    private static final SyncTokenMetadata METADATA = SyncTokenMetadata.fromToken("sync");

    @Test
    void queuedExecutorDefersTheMetadataCommitUntilClientWorkRuns() {
        Object lifecycleLock = new Object();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicReference<SyncTokenMetadata> committed = new AtomicReference<>();
        AtomicReference<Id114ClientWork.Outcome> outcome = new AtomicReference<>();
        AtomicBoolean started = new AtomicBoolean();

        Id114ClientWork.enqueue(
            queued::set,
            lifecycleLock,
            () -> true,
            () -> true,
            metadata -> {
                assertTrue(Thread.holdsLock(lifecycleLock));
                committed.set(metadata);
            },
            METADATA,
            () -> started.set(true),
            outcome::set,
            error -> fail(error)
        );

        assertNotNull(queued.get());
        assertFalse(started.get());
        assertNull(committed.get());
        assertNull(outcome.get());
        queued.get().run();
        assertTrue(started.get());
        assertSame(METADATA, committed.get());
        assertEquals(Id114ClientWork.Outcome.ACCEPTED, outcome.get());
    }

    @Test
    void inlineExecutorPreservesTheSameAtomicClientWorkSemantics() {
        Object lifecycleLock = new Object();
        AtomicReference<SyncTokenMetadata> committed = new AtomicReference<>();
        AtomicReference<Id114ClientWork.Outcome> outcome = new AtomicReference<>();
        AtomicBoolean started = new AtomicBoolean();

        Id114ClientWork.enqueue(
            Runnable::run,
            lifecycleLock,
            () -> true,
            () -> true,
            committed::set,
            METADATA,
            () -> started.set(true),
            outcome::set,
            error -> fail(error)
        );

        assertTrue(started.get());
        assertSame(METADATA, committed.get());
        assertEquals(Id114ClientWork.Outcome.ACCEPTED, outcome.get());
    }

    @Test
    void playerNullDropsExactlyOnceWithoutCommitOrRetry() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicBoolean playerAvailable = new AtomicBoolean(true);
        AtomicInteger queuedCount = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<Id114ClientWork.Outcome> outcome = new AtomicReference<>();

        Id114ClientWork.enqueue(
            work -> {
                queuedCount.incrementAndGet();
                queued.set(work);
            },
            new Object(),
            () -> true,
            playerAvailable::get,
            metadata -> commits.incrementAndGet(),
            METADATA,
            () -> { },
            outcome::set,
            error -> fail(error)
        );

        playerAvailable.set(false);
        queued.get().run();
        assertEquals(1, queuedCount.get());
        assertEquals(0, commits.get());
        assertEquals(Id114ClientWork.Outcome.PLAYER_UNAVAILABLE, outcome.get());
    }

    @Test
    void staleGenerationDropsBeforeReadingPlayerOrCommitting() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicBoolean currentGeneration = new AtomicBoolean(true);
        AtomicBoolean playerChecked = new AtomicBoolean();
        AtomicBoolean committed = new AtomicBoolean();
        AtomicReference<Id114ClientWork.Outcome> outcome = new AtomicReference<>();

        Id114ClientWork.enqueue(
            queued::set,
            new Object(),
            currentGeneration::get,
            () -> {
                playerChecked.set(true);
                return true;
            },
            metadata -> committed.set(true),
            METADATA,
            () -> { },
            outcome::set,
            error -> fail(error)
        );

        currentGeneration.set(false);
        queued.get().run();
        assertEquals(Id114ClientWork.Outcome.STALE_GENERATION, outcome.get());
        assertFalse(playerChecked.get());
        assertFalse(committed.get());
    }

    @Test
    void inlineExecutionFailureIsReportedAsWorkFailureInsteadOfSubmitFailure() {
        RuntimeException expected = new IllegalStateException("synthetic-work-failure");
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicReference<Id114ClientWork.Outcome> outcome = new AtomicReference<>();

        assertDoesNotThrow(() -> Id114ClientWork.enqueue(
            Runnable::run,
            new Object(),
            () -> true,
            () -> true,
            metadata -> fail("commit must not run"),
            METADATA,
            () -> {
                throw expected;
            },
            outcome::set,
            failure::set
        ));

        assertSame(expected, failure.get());
        assertNull(outcome.get());
    }

    @Test
    void executorRejectionPropagatesWithoutStartingOrCompletingWork() {
        AtomicBoolean started = new AtomicBoolean();
        AtomicReference<Id114ClientWork.Outcome> outcome = new AtomicReference<>();
        AtomicReference<RuntimeException> executionFailure = new AtomicReference<>();

        assertThrows(RejectedExecutionException.class, () -> Id114ClientWork.enqueue(
            work -> {
                throw new RejectedExecutionException("synthetic-rejection");
            },
            new Object(),
            () -> true,
            () -> true,
            metadata -> fail("commit must not run"),
            METADATA,
            () -> started.set(true),
            outcome::set,
            executionFailure::set
        ));

        assertFalse(started.get());
        assertNull(outcome.get());
        assertNull(executionFailure.get());
    }
}
