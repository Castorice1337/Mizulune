package shit.zen.protocol.heypixel;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Executes the client-logical part of the official ID114 handler exactly once.
 * The lifecycle lock keeps the generation check and metadata commit atomic.
 */
public final class Id114ClientWork {
    private Id114ClientWork() {
    }

    public static void enqueue(
        Consumer<Runnable> clientExecutor,
        Object lifecycleLock,
        BooleanSupplier currentGeneration,
        BooleanSupplier playerAvailable,
        Consumer<SyncTokenMetadata> commit,
        SyncTokenMetadata metadata,
        Runnable started,
        Consumer<Outcome> completion,
        Consumer<RuntimeException> executionFailure
    ) {
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(completion, "completion");
        ClientLogicalWork.enqueue(
            clientExecutor,
            lifecycleLock,
            currentGeneration,
            playerAvailable,
            () -> {
                commit.accept(metadata);
                return metadata;
            },
            started,
            result -> completion.accept(Outcome.valueOf(result.outcome().name())),
            executionFailure
        );
    }

    public static Outcome apply(
        Object lifecycleLock,
        BooleanSupplier currentGeneration,
        BooleanSupplier playerAvailable,
        Consumer<SyncTokenMetadata> commit,
        SyncTokenMetadata metadata
    ) {
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(metadata, "metadata");
        ClientLogicalWork.Result<SyncTokenMetadata> result = ClientLogicalWork.apply(
            lifecycleLock,
            currentGeneration,
            playerAvailable,
            () -> {
                commit.accept(metadata);
                return metadata;
            }
        );
        return Outcome.valueOf(result.outcome().name());
    }

    public enum Outcome {
        ACCEPTED,
        STALE_GENERATION,
        PLAYER_UNAVAILABLE
    }
}
