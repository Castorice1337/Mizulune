package shit.zen.protocol.heypixel;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs a decoded S2C packet on the client logical executor and applies it at most once.
 * The lifecycle lock makes the generation/player checks and state mutation one operation.
 */
public final class ClientLogicalWork {
    private ClientLogicalWork() {
    }

    public static <T> void enqueue(
        Consumer<Runnable> clientExecutor,
        Object lifecycleLock,
        BooleanSupplier currentGeneration,
        BooleanSupplier playerAvailable,
        Supplier<T> apply,
        Runnable started,
        Consumer<Result<T>> completion,
        Consumer<RuntimeException> executionFailure
    ) {
        Objects.requireNonNull(clientExecutor, "clientExecutor");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completion, "completion");
        Objects.requireNonNull(executionFailure, "executionFailure");
        clientExecutor.accept(() -> {
            try {
                started.run();
                completion.accept(apply(
                    lifecycleLock,
                    currentGeneration,
                    playerAvailable,
                    apply
                ));
            } catch (RuntimeException error) {
                executionFailure.accept(error);
            }
        });
    }

    public static <T> Result<T> apply(
        Object lifecycleLock,
        BooleanSupplier currentGeneration,
        BooleanSupplier playerAvailable,
        Supplier<T> apply
    ) {
        Objects.requireNonNull(lifecycleLock, "lifecycleLock");
        Objects.requireNonNull(currentGeneration, "currentGeneration");
        Objects.requireNonNull(playerAvailable, "playerAvailable");
        Objects.requireNonNull(apply, "apply");
        synchronized (lifecycleLock) {
            if (!currentGeneration.getAsBoolean()) {
                return new Result<>(Outcome.STALE_GENERATION, null);
            }
            if (!playerAvailable.getAsBoolean()) {
                return new Result<>(Outcome.PLAYER_UNAVAILABLE, null);
            }
            return new Result<>(Outcome.ACCEPTED, Objects.requireNonNull(apply.get(), "applied value"));
        }
    }

    public enum Outcome {
        ACCEPTED,
        STALE_GENERATION,
        PLAYER_UNAVAILABLE
    }

    public record Result<T>(Outcome outcome, T value) {
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            if ((outcome == Outcome.ACCEPTED) != (value != null)) {
                throw new IllegalArgumentException("only accepted work may carry an applied value");
            }
        }
    }
}
