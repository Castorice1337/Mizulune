package shit.zen.protocol.heypixel;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Executes the official ID114 client work with the native call outside the lifecycle lock. */
final class Id114ClientWork {
    private Id114ClientWork() {
    }

    static void enqueue(
        Consumer<Runnable> clientExecutor,
        Object lifecycleLock,
        BooleanSupplier currentGeneration,
        BooleanSupplier playerAvailable,
        BooleanSupplier nativeAllowed,
        Id114NativeSink nativeSink,
        Id114TokenLease lease,
        Consumer<SyncTokenMetadata> commit,
        Consumer<Id114NativeSink> nativeInvoked,
        Runnable started,
        Consumer<Result> completion,
        Consumer<RuntimeException> executionFailure
    ) {
        Objects.requireNonNull(clientExecutor, "clientExecutor");
        Objects.requireNonNull(lifecycleLock, "lifecycleLock");
        Objects.requireNonNull(currentGeneration, "currentGeneration");
        Objects.requireNonNull(playerAvailable, "playerAvailable");
        Objects.requireNonNull(nativeAllowed, "nativeAllowed");
        Objects.requireNonNull(nativeSink, "nativeSink");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(nativeInvoked, "nativeInvoked");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completion, "completion");
        Objects.requireNonNull(executionFailure, "executionFailure");

        Runnable work = () -> {
            if (!lease.claim()) {
                completion.accept(new Result(Outcome.DUPLICATE_WORK, null));
                return;
            }
            try {
                started.run();
                completion.accept(applyClaimed(
                    lifecycleLock,
                    currentGeneration,
                    playerAvailable,
                    nativeAllowed,
                    nativeSink,
                    lease,
                    commit,
                    nativeInvoked
                ));
            } catch (RuntimeException error) {
                lease.clear();
                executionFailure.accept(error);
            }
        };

        try {
            clientExecutor.accept(work);
        } catch (RuntimeException error) {
            lease.clear();
            throw error;
        }
    }

    static Result apply(
        Object lifecycleLock,
        BooleanSupplier currentGeneration,
        BooleanSupplier playerAvailable,
        BooleanSupplier nativeAllowed,
        Id114NativeSink nativeSink,
        Id114TokenLease lease,
        Consumer<SyncTokenMetadata> commit,
        Consumer<Id114NativeSink> nativeInvoked
    ) {
        Objects.requireNonNull(lease, "lease");
        if (!lease.claim()) return new Result(Outcome.DUPLICATE_WORK, null);
        return applyClaimed(
            lifecycleLock,
            currentGeneration,
            playerAvailable,
            nativeAllowed,
            nativeSink,
            lease,
            commit,
            nativeInvoked
        );
    }

    private static Result applyClaimed(
        Object lifecycleLock,
        BooleanSupplier currentGeneration,
        BooleanSupplier playerAvailable,
        BooleanSupplier nativeAllowed,
        Id114NativeSink nativeSink,
        Id114TokenLease lease,
        Consumer<SyncTokenMetadata> commit,
        Consumer<Id114NativeSink> nativeInvoked
    ) {
        Objects.requireNonNull(lifecycleLock, "lifecycleLock");
        Objects.requireNonNull(currentGeneration, "currentGeneration");
        Objects.requireNonNull(playerAvailable, "playerAvailable");
        Objects.requireNonNull(nativeAllowed, "nativeAllowed");
        Objects.requireNonNull(nativeSink, "nativeSink");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(nativeInvoked, "nativeInvoked");

        SyncTokenMetadata metadata = lease.metadata();
        synchronized (lifecycleLock) {
            if (!currentGeneration.getAsBoolean()) {
                lease.clear();
                return new Result(Outcome.STALE_GENERATION, null);
            }
            if (!playerAvailable.getAsBoolean()) {
                lease.clear();
                return new Result(Outcome.PLAYER_UNAVAILABLE, null);
            }
            if (!nativeAllowed.getAsBoolean()) {
                lease.clear();
                commit.accept(metadata.withNativeSinkAvailable(false));
                return new Result(
                    Outcome.METADATA_ONLY_NATIVE_DISABLED,
                    Id114NativeSink.Reason.NATIVE_DISABLED
                );
            }
        }

        Id114NativeSink.Availability availability = nativeSink.availability();
        if (!availability.available()) {
            lease.clear();
            synchronized (lifecycleLock) {
                if (!currentGeneration.getAsBoolean()) {
                    return new Result(Outcome.STALE_GENERATION, availability.reason());
                }
                if (!playerAvailable.getAsBoolean()) {
                    return new Result(Outcome.PLAYER_UNAVAILABLE, availability.reason());
                }
                if (!nativeAllowed.getAsBoolean()) {
                    commit.accept(metadata.withNativeSinkAvailable(false));
                    return new Result(
                        Outcome.METADATA_ONLY_NATIVE_DISABLED,
                        Id114NativeSink.Reason.NATIVE_DISABLED
                    );
                }
                commit.accept(metadata.withNativeSinkAvailable(false));
            }
            return new Result(Outcome.METADATA_ONLY_NATIVE_UNAVAILABLE, availability.reason());
        }

        synchronized (lifecycleLock) {
            if (!currentGeneration.getAsBoolean()) {
                lease.clear();
                return new Result(Outcome.STALE_GENERATION, availability.reason());
            }
            if (!playerAvailable.getAsBoolean()) {
                lease.clear();
                return new Result(Outcome.PLAYER_UNAVAILABLE, availability.reason());
            }
            if (!nativeAllowed.getAsBoolean()) {
                lease.clear();
                commit.accept(metadata.withNativeSinkAvailable(false));
                return new Result(
                    Outcome.METADATA_ONLY_NATIVE_DISABLED,
                    Id114NativeSink.Reason.NATIVE_DISABLED
                );
            }
        }

        String transientToken = lease.take();
        if (transientToken == null) return new Result(Outcome.DUPLICATE_WORK, null);
        Id114NativeSink.AcceptResult acceptResult;
        try {
            acceptResult = Objects.requireNonNull(
                nativeSink.accept(transientToken),
                "native accept result"
            );
        } catch (Id114NativeSink.InvocationException error) {
            return new Result(Outcome.NATIVE_ACCEPT_FAILED, error.reason());
        } finally {
            transientToken = null;
            lease.clear();
        }

        Id114NativeSink.Reason invocationReason = acceptResult
            == Id114NativeSink.AcceptResult.CONFIRMED
            ? Id114NativeSink.Reason.READY
            : Id114NativeSink.Reason.CALLBACK_READINESS_UNVERIFIED;
        Outcome postAcceptOutcome;
        synchronized (lifecycleLock) {
            if (!currentGeneration.getAsBoolean()) {
                postAcceptOutcome = Outcome.STALE_AFTER_NATIVE_ACCEPT;
            } else if (!playerAvailable.getAsBoolean()) {
                postAcceptOutcome = Outcome.PLAYER_UNAVAILABLE_AFTER_NATIVE_ACCEPT;
            } else if (!nativeAllowed.getAsBoolean()) {
                postAcceptOutcome = Outcome.NATIVE_DISABLED_AFTER_ACCEPT;
            } else {
                nativeInvoked.accept(nativeSink);
                boolean confirmed = acceptResult == Id114NativeSink.AcceptResult.CONFIRMED;
                commit.accept(metadata.withNativeSinkAvailable(confirmed));
                return new Result(
                    confirmed ? Outcome.ACCEPTED : Outcome.NATIVE_ACCEPT_INVOKED_UNVERIFIED,
                    invocationReason
                );
            }
        }

        try {
            nativeSink.logout();
            return new Result(postAcceptOutcome, invocationReason);
        } catch (Id114NativeSink.InvocationException error) {
            return new Result(Outcome.COMPENSATING_LOGOUT_FAILED, error.reason());
        }
    }

    record Result(Outcome outcome, Id114NativeSink.Reason nativeReason) {
        Result {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    enum Outcome {
        ACCEPTED,
        NATIVE_ACCEPT_INVOKED_UNVERIFIED,
        METADATA_ONLY_NATIVE_DISABLED,
        METADATA_ONLY_NATIVE_UNAVAILABLE,
        STALE_GENERATION,
        PLAYER_UNAVAILABLE,
        DUPLICATE_WORK,
        NATIVE_ACCEPT_FAILED,
        STALE_AFTER_NATIVE_ACCEPT,
        PLAYER_UNAVAILABLE_AFTER_NATIVE_ACCEPT,
        NATIVE_DISABLED_AFTER_ACCEPT,
        COMPENSATING_LOGOUT_FAILED
    }
}
