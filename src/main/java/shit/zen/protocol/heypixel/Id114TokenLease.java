package shit.zen.protocol.heypixel;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One-shot in-memory lease for the raw S2C ID114 value. */
final class Id114TokenLease {
    private final AtomicReference<String> transientToken;
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final SyncTokenMetadata metadata;

    private Id114TokenLease(String transientToken) {
        String value = Objects.requireNonNull(transientToken, "transientToken");
        this.transientToken = new AtomicReference<>(value);
        this.metadata = SyncTokenMetadata.fromToken(value);
    }

    static Id114TokenLease fromToken(String transientToken) {
        return new Id114TokenLease(transientToken);
    }

    SyncTokenMetadata metadata() {
        return metadata;
    }

    boolean claim() {
        return claimed.compareAndSet(false, true);
    }

    String take() {
        return transientToken.getAndSet(null);
    }

    void clear() {
        transientToken.set(null);
    }
}
