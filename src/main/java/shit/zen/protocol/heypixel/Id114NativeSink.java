package shit.zen.protocol.heypixel;

import java.util.Objects;

/**
 * Narrow bridge to the official ID114 native lifecycle.
 *
 * <p>The token argument is transient. Implementations must not retain it or
 * include it in diagnostics.</p>
 */
public interface Id114NativeSink {
    Availability availability();

    AcceptResult accept(String transientToken);

    void logout();

    static Id114NativeSink unavailable(Reason reason) {
        Availability availability = Availability.unavailable(reason);
        return new Id114NativeSink() {
            @Override
            public Availability availability() {
                return availability;
            }

            @Override
            public AcceptResult accept(String transientToken) {
                throw new InvocationException(availability.reason());
            }

            @Override
            public void logout() {
                throw new InvocationException(availability.reason());
            }
        };
    }

    record Availability(boolean available, Reason reason) {
        public Availability {
            reason = Objects.requireNonNull(reason, "reason");
            boolean invokable = reason == Reason.READY
                || reason == Reason.CALLBACK_READINESS_UNVERIFIED;
            if (available != invokable) {
                throw new IllegalArgumentException("availability and reason disagree");
            }
        }

        public static Availability ready() {
            return new Availability(true, Reason.READY);
        }

        public static Availability callbackReadinessUnverified() {
            return new Availability(true, Reason.CALLBACK_READINESS_UNVERIFIED);
        }

        public static Availability unavailable(Reason reason) {
            if (reason == Reason.READY || reason == Reason.CALLBACK_READINESS_UNVERIFIED) {
                throw new IllegalArgumentException(reason + " is not an unavailable reason");
            }
            return new Availability(false, reason);
        }
    }

    enum AcceptResult {
        CONFIRMED,
        INVOKED_UNVERIFIED
    }

    enum Reason {
        READY,
        CALLBACK_READINESS_UNVERIFIED,
        NATIVE_DISABLED,
        PREMAIN_REQUIRED,
        LATE_ATTACH_UNSUPPORTED,
        STARTUP_PREFLIGHT_TOO_LATE,
        LAYOUT_UNAVAILABLE,
        PLATFORM_UNSUPPORTED,
        NATIVE_DIRECTORY_UNAVAILABLE,
        MAXHOOK_UNAVAILABLE,
        MAXHOOK_OUTSIDE_NATIVE_DIRECTORY,
        MAXHOOK_HASH_MISMATCH,
        JVM_LIBRARY_UNAVAILABLE,
        JVM_HASH_UNSUPPORTED,
        SYNC_TOKEN_ABI_MISMATCH,
        NATIVE_IDENTITY_CHANGED,
        NATIVE_LOAD_FAILED,
        ACCEPT_FAILED,
        LOGOUT_FAILED
    }

    final class InvocationException extends RuntimeException {
        private final Reason reason;

        public InvocationException(Reason reason) {
            super(Objects.requireNonNull(reason, "reason").name());
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    @FunctionalInterface
    interface Factory {
        Id114NativeSink create(HeyPixelInstallLayout layout);
    }
}
