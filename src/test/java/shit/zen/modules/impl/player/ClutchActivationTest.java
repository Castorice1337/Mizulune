package shit.zen.modules.impl.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ClutchActivationTest {
    @Test
    void manualRescueDoesNotClaimGroundOrAscendingTellyFrames() {
        assertFalse(Clutch.isManualRescueMotion(true, -0.08));
        assertFalse(Clutch.isManualRescueMotion(false, 0.165));
        assertFalse(Clutch.isManualRescueMotion(false, 0.003));
        assertFalse(Clutch.isManualRescueMotion(false, 0.0));
        assertTrue(Clutch.isManualRescueMotion(false, -0.08));
    }

    @Test
    void autoClutchAlsoWaitsForActualDownwardMotion() {
        assertFalse(Clutch.isRescueActivation(false, true, false, 0.165));
        assertFalse(Clutch.isRescueActivation(false, true, false, 0.0));
        assertTrue(Clutch.isRescueActivation(false, true, false, -0.001));
        assertFalse(Clutch.isRescueActivation(false, true, true, -0.08));
    }

    @Test
    void resetOnlySnapbacksOutsideTheAscendingApexWindow() {
        assertFalse(Clutch.shouldAllowSnapback(false, 0.003));
        assertFalse(Clutch.shouldAllowSnapback(false, 0.0));
        assertTrue(Clutch.shouldAllowSnapback(false, -0.001));
        assertTrue(Clutch.shouldAllowSnapback(true, 0.0));
    }

    @Test
    void resetRotationOwnerIsNotReportedAsAnActiveRescue() {
        assertTrue(Clutch.isActiveRescueState(
                true, false, -0.08, true, true, true, false, false));
        assertFalse(Clutch.isActiveRescueState(
                true, false, -0.08, true, false, false, true, false));
        assertTrue(Clutch.isActiveRescueState(
                true, false, -0.08, false, false, false, true, true));
        assertFalse(Clutch.isActiveRescueState(
                false, false, -0.08, true, true, true, false, true));
    }

    @Test
    void staleOwnerAndAirStuckCannotSurviveIntoAnAscendingHandoffTick() {
        assertFalse(Clutch.isActiveRescueState(
                true, false, 0.165, true, true, true, false, false));
        assertFalse(Clutch.isActiveRescueState(
                true, false, 0.003, false, false, false, false, true));
        assertFalse(Clutch.isActiveRescueState(
                true, false, 0.0, true, true, true, false, false));
        assertFalse(Clutch.isActiveRescueState(
                true, true, -0.08, true, true, true, false, true));
    }

    @Test
    void resetRotationYieldsToScaffoldWhileActiveRescueStillWins() {
        int scaffoldPriority = 50;

        assertTrue(Clutch.rotationPriority(false) > scaffoldPriority);
        assertTrue(Clutch.rotationPriority(true) < scaffoldPriority);
    }
}
