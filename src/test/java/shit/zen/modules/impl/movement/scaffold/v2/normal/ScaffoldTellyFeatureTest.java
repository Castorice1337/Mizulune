package shit.zen.modules.impl.movement.scaffold.v2.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ScaffoldTellyFeatureTest {
    @Test
    void defaultsMatchLiquidSource() {
        ScaffoldTellyFeature.Settings settings = ScaffoldTellyFeature.DEFAULTS;

        assertFalse(settings.enabled());
        assertEquals(ScaffoldTellyFeature.ResetMode.RESET, settings.resetMode());
        assertEquals(0, settings.straightTicks());
        assertEquals(new ScaffoldTellyFeature.JumpTickRange(0, 0), settings.jumpTicks());
        assertTrue(settings.aimOnTower());
    }

    @Test
    void resetModeRequiresStraightRotationAndReachedThreshold() {
        ScaffoldTellyFeature.Settings settings = settings(
                ScaffoldTellyFeature.ResetMode.RESET,
                2,
                true);

        assertFalse(ScaffoldTellyFeature.decide(
                settings,
                frame(false, true, 64, true, true, 0, 1, 1, false)).jump());
        assertTrue(ScaffoldTellyFeature.decide(
                settings,
                frame(false, true, 64, true, false, 0, 1, 1, false)).jump());
        assertFalse(ScaffoldTellyFeature.decide(
                settings,
                frame(false, true, 64, true, false, 0, 0, 1, false)).jump());
    }

    @Test
    void reverseModeJumpsWheneverMovementHandlerIsEligible() {
        ScaffoldTellyFeature.Settings settings = settings(
                ScaffoldTellyFeature.ResetMode.REVERSE,
                3,
                true);

        assertTrue(ScaffoldTellyFeature.decide(
                settings,
                frame(false, true, 1, true, true, 0, 0, 10, false)).jump());
        assertFalse(ScaffoldTellyFeature.decide(
                settings,
                frame(false, false, 1, true, true, 0, 0, 10, false)).jump());
    }

    @Test
    void aimOnTowerAdaptsExistingTimingWindow() {
        ScaffoldTellyFeature.TimingFrame towerFrame =
                frame(false, true, 64, false, true, 0, 2, 2, true);

        assertFalse(ScaffoldTellyFeature.decide(
                settings(ScaffoldTellyFeature.ResetMode.RESET, 0, true),
                towerFrame).doNotAim());
        assertTrue(ScaffoldTellyFeature.decide(
                settings(ScaffoldTellyFeature.ResetMode.RESET, 0, false),
                towerFrame).doNotAim());
    }

    @Test
    void straightBoundaryAndTellyBridgingMatchLiquidSource() {
        ScaffoldTellyFeature.Decision atBoundary = ScaffoldTellyFeature.decide(
                settings(ScaffoldTellyFeature.ResetMode.RESET, 2, true),
                frame(false, true, 64, false, true, 2, 3, 3, false));
        ScaffoldTellyFeature.Decision afterBoundary = ScaffoldTellyFeature.decide(
                settings(ScaffoldTellyFeature.ResetMode.RESET, 2, true),
                frame(false, true, 64, false, true, 3, 3, 3, false));

        assertTrue(atBoundary.doNotAim());
        assertTrue(atBoundary.tellyBridging());
        assertFalse(afterBoundary.doNotAim());
        assertTrue(afterBoundary.tellyBridging());
    }

    @Test
    void liquidSourceBoundsAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldTellyFeature.JumpTickRange(-1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldTellyFeature.JumpTickRange(0, 11));
        assertThrows(IllegalArgumentException.class,
                () -> settings(ScaffoldTellyFeature.ResetMode.RESET, 6, true));
    }

    private static ScaffoldTellyFeature.Settings settings(
            ScaffoldTellyFeature.ResetMode mode,
            int straightTicks,
            boolean aimOnTower) {
        return new ScaffoldTellyFeature.Settings(
                true,
                mode,
                straightTicks,
                new ScaffoldTellyFeature.JumpTickRange(0, 10),
                aimOnTower);
    }

    private static ScaffoldTellyFeature.TimingFrame frame(
            boolean jump,
            boolean moving,
            int blockCount,
            boolean onGround,
            boolean hasRotation,
            int airTicks,
            int ticksUntilJump,
            int sampledJumpTicks,
            boolean towering) {
        return new ScaffoldTellyFeature.TimingFrame(
                jump,
                moving,
                blockCount,
                onGround,
                hasRotation,
                airTicks,
                ticksUntilJump,
                sampledJumpTicks,
                towering);
    }
}
