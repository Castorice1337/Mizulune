package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ClientLogicalWorkTest {
    @Test
    void queuedWorkAppliesOnlyWhenTheClientExecutorRunsIt() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicInteger applies = new AtomicInteger();
        AtomicReference<ClientLogicalWork.Result<String>> result = new AtomicReference<>();

        ClientLogicalWork.enqueue(
            queued::set,
            new Object(),
            () -> true,
            () -> true,
            () -> {
                applies.incrementAndGet();
                return "committed";
            },
            () -> {
            },
            result::set,
            error -> {
                throw error;
            }
        );

        assertEquals(0, applies.get());
        assertNull(result.get());
        queued.get().run();
        assertEquals(1, applies.get());
        assertEquals(ClientLogicalWork.Outcome.ACCEPTED, result.get().outcome());
        assertEquals("committed", result.get().value());
    }

    @Test
    void staleOrPlayerlessWorkDropsWithoutApplyingAndNeverRetries() {
        AtomicInteger applies = new AtomicInteger();

        ClientLogicalWork.Result<String> stale = ClientLogicalWork.apply(
            new Object(), () -> false, () -> true,
            () -> {
                applies.incrementAndGet();
                return "unexpected";
            });
        ClientLogicalWork.Result<String> playerless = ClientLogicalWork.apply(
            new Object(), () -> true, () -> false,
            () -> {
                applies.incrementAndGet();
                return "unexpected";
            });

        assertEquals(ClientLogicalWork.Outcome.STALE_GENERATION, stale.outcome());
        assertEquals(ClientLogicalWork.Outcome.PLAYER_UNAVAILABLE, playerless.outcome());
        assertNull(stale.value());
        assertNull(playerless.value());
        assertEquals(0, applies.get());
    }

    @Test
    void executionFailureIsSeparatedFromSubmissionFailure() {
        AtomicReference<RuntimeException> executionFailure = new AtomicReference<>();
        ClientLogicalWork.enqueue(
            Runnable::run,
            new Object(),
            () -> true,
            () -> true,
            () -> {
                throw new IllegalStateException("apply");
            },
            () -> {
            },
            ignored -> {
            },
            executionFailure::set
        );
        assertEquals(IllegalStateException.class, executionFailure.get().getClass());

        assertThrows(RejectedExecutionException.class, () -> ClientLogicalWork.enqueue(
            ignored -> {
                throw new RejectedExecutionException("submit");
            },
            new Object(),
            () -> true,
            () -> true,
            () -> "unused",
            () -> {
            },
            ignored -> {
            },
            ignored -> {
            }
        ));
    }
}
