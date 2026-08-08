package shit.zen.modules.impl.movement.scaffold.v2.motion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AutoSpeedTest {
    @Test
    void preservesDisabledLiquidBounceDefault() {
        assertFalse(AutoSpeed.DEFAULT_ENABLED);
        assertFalse(AutoSpeed.DEFAULTS.enabled());
    }

    @Test
    void scaffoldRequestsSpeedOnlyWhenAutoSpeedIsEnabled() {
        assertFalse(AutoSpeed.requestsSpeed(true, AutoSpeed.DEFAULTS));
        assertFalse(AutoSpeed.requestsSpeed(false, new AutoSpeed.Settings(true)));
        assertTrue(AutoSpeed.requestsSpeed(true, new AutoSpeed.Settings(true)));
    }

    @Test
    void existingSpeedActivationAndDownstreamRequirementsRemainAuthoritative() {
        assertTrue(AutoSpeed.resolveActivation(true, false, AutoSpeed.DEFAULTS));
        assertTrue(AutoSpeed.resolveActivation(false, true, new AutoSpeed.Settings(true)));
        assertFalse(AutoSpeed.shouldRun(true, false, AutoSpeed.DEFAULTS, false));
    }
}
