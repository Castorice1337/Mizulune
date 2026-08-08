package com.columbina.heypixel;

/** Keeps login one-shots independent from target/session generation invalidation. */
public final class ProtocolLifecyclePolicy {
    private ProtocolLifecyclePolicy() {
    }

    public static boolean resetsCompletedOneShots(
        Object previousConnection,
        Object observedConnection,
        boolean logout
    ) {
        return logout || previousConnection != observedConnection;
    }
}
