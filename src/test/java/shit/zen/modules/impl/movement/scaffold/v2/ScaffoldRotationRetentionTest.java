package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import shit.zen.utils.rotation.Rotation;

final class ScaffoldRotationRetentionTest {
    @Test
    void targetRefreshesRetentionWindow() {
        ScaffoldRotationRetention retention = new ScaffoldRotationRetention();

        retention.onTarget(new Rotation(90.0f, 70.0f), 3);
        retention.onMissingTarget(new Rotation(85.0f, 68.0f));
        retention.onTarget(new Rotation(135.0f, 72.0f), 3);

        assertEquals(135.0f, retention.rotation().getYaw());
        assertEquals(3, retention.ticksRemaining());
    }

    @Test
    void firstMissingTickFreezesLastAppliedServerRotation() {
        ScaffoldRotationRetention retention = new ScaffoldRotationRetention();
        retention.onTarget(new Rotation(135.0f, 75.0f), 2);

        retention.onMissingTarget(new Rotation(110.0f, 70.0f));

        assertEquals(110.0f, retention.rotation().getYaw());
        assertEquals(1, retention.ticksRemaining());
    }

    @Test
    void remainsActiveForConfiguredMissingTargetTicksThenExpires() {
        ScaffoldRotationRetention retention = new ScaffoldRotationRetention();
        retention.onTarget(new Rotation(90.0f, 70.0f), 2);

        retention.onMissingTarget(new Rotation(90.0f, 70.0f));
        assertTrue(retention.active());
        retention.onMissingTarget(null);
        assertTrue(retention.active());
        retention.onMissingTarget(null);

        assertFalse(retention.active());
        assertNull(retention.rotation());
    }

    @Test
    void clearImmediatelyDropsAllRetainedState() {
        ScaffoldRotationRetention retention = new ScaffoldRotationRetention();
        retention.onTarget(new Rotation(90.0f, 70.0f), 5);

        retention.clear();

        assertFalse(retention.active());
        assertEquals(0, retention.ticksRemaining());
    }
}
