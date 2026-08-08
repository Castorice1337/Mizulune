package com.columbina.heypixel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProtocolLifecyclePolicyTest {
    @Test
    void preservesCompletedOneShotsAcrossSameConnectionTargetRefresh() {
        Object connection = new Object();

        assertFalse(ProtocolLifecyclePolicy.resetsCompletedOneShots(
            connection, connection, false));
    }

    @Test
    void resetsCompletedOneShotsForNewConnection() {
        assertTrue(ProtocolLifecyclePolicy.resetsCompletedOneShots(
            new Object(), new Object(), false));
    }

    @Test
    void resetsCompletedOneShotsForLogoutEvenWhenHandlerIsStillObservable() {
        Object connection = new Object();

        assertTrue(ProtocolLifecyclePolicy.resetsCompletedOneShots(
            connection, connection, true));
    }

    @Test
    void treatsMissingDisconnectEventAsConnectionEnd() {
        assertTrue(ProtocolLifecyclePolicy.resetsCompletedOneShots(
            new Object(), null, false));
    }
}
