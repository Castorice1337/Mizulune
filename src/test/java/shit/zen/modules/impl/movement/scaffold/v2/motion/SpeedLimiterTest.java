package shit.zen.modules.impl.movement.scaffold.v2.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class SpeedLimiterTest {
    private static final DirectionalInput FORWARD =
            new DirectionalInput(true, false, false, false);

    @Test
    void preservesLiquidBounceDefaults() {
        assertFalse(SpeedLimiter.DEFAULTS.enabled());
        assertEquals(0.11f, SpeedLimiter.DEFAULTS.speedLimit());
    }

    @Test
    void clearsInputOnlyWhenSpeedIsStrictlyAboveLimit() {
        SpeedLimiter.Settings settings = new SpeedLimiter.Settings(true, 0.11f);
        SpeedLimiter.Decision atLimit = SpeedLimiter.apply(FORWARD, (double) 0.11f, settings);
        SpeedLimiter.Decision aboveLimit = SpeedLimiter.apply(
                FORWARD,
                (double) 0.11f + 1.0e-6,
                settings);

        assertFalse(atLimit.limited());
        assertEquals(FORWARD, atLimit.directionalInput());
        assertTrue(aboveLimit.limited());
        assertEquals(DirectionalInput.NONE, aboveLimit.directionalInput());
    }

    @Test
    void disabledPolicyNeverClearsInput() {
        SpeedLimiter.Decision decision = SpeedLimiter.apply(FORWARD, 10.0, SpeedLimiter.DEFAULTS);

        assertFalse(decision.limited());
        assertEquals(FORWARD, decision.directionalInput());
    }
}
