package shit.zen.modules.impl.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertFalse(Clutch.isRescueActivation(false, true, false, false, 0.165));
        assertFalse(Clutch.isRescueActivation(false, true, false, false, 0.0));
        assertTrue(Clutch.isRescueActivation(false, true, false, false, -0.001));
        assertFalse(Clutch.isRescueActivation(false, true, false, true, -0.08));
    }

    @Test
    void activeAirStuckOwnsTheRescueWhilePositionHoldZerosMotion() {
        assertTrue(Clutch.isRescueActivation(false, false, true, false, 0.0));
        assertTrue(Clutch.isRescueActivation(false, false, true, false, 0.003));
        assertFalse(Clutch.isRescueActivation(false, false, true, false, 0.165));
        assertFalse(Clutch.isRescueActivation(false, false, true, true, 0.0));
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
    void airStuckOnlySurvivesItsHoldSizedVerticalDeadband() {
        assertFalse(Clutch.isActiveRescueState(
                true, false, 0.165, true, true, true, false, false));
        assertTrue(Clutch.isActiveRescueState(
                true, false, 0.003, false, false, false, false, true));
        assertFalse(Clutch.isActiveRescueState(
                true, false, 0.0, true, true, true, false, false));
        assertFalse(Clutch.isActiveRescueState(
                true, false, 0.165, false, false, false, false, true));
        assertFalse(Clutch.isActiveRescueState(
                true, true, -0.08, true, true, true, false, true));
    }

    @Test
    void resetRotationYieldsToScaffoldWhileActiveRescueStillWins() {
        int scaffoldPriority = 50;

        assertTrue(Clutch.rotationPriority(false) > scaffoldPriority);
        assertTrue(Clutch.rotationPriority(true) < scaffoldPriority);
    }

    @Test
    void strictDangerCanPreAimBeforeThePlayerStartsDescending() {
        Clutch.FallRiskAssessment danger = new Clutch.FallRiskAssessment(
                Clutch.FallRisk.VOID, 4.0, -1);

        assertTrue(Clutch.strictRescueEligible(false, danger, false));
        assertFalse(Clutch.strictRescueEligible(true, danger, false));
    }

    @Test
    void latchedStrictDangerSurvivesAOneTickSafePrediction() {
        Clutch.FallRiskAssessment safe = new Clutch.FallRiskAssessment(
                Clutch.FallRisk.SAFE, 2.0, 3);

        assertTrue(Clutch.strictRescueEligible(false, safe, true));
        assertFalse(Clutch.strictRescueEligible(false, safe, false));
    }

    @Test
    void velocityWindowWaitsForTakeoffThenStaysArmedUntilLanding() {
        Clutch.VelocityArmState waiting = new Clutch.VelocityArmState(true, false, 3);
        Clutch.VelocityArmState grounded = Clutch.advanceVelocityArm(waiting, true);
        Clutch.VelocityArmState airborne = Clutch.advanceVelocityArm(grounded, false);
        Clutch.VelocityArmState stillAirborne = Clutch.advanceVelocityArm(airborne, false);
        Clutch.VelocityArmState landed = Clutch.advanceVelocityArm(stillAirborne, true);

        assertTrue(grounded.armed());
        assertEquals(2, grounded.remainingTicks());
        assertTrue(airborne.armed());
        assertTrue(airborne.airborneSeen());
        assertEquals(2, stillAirborne.remainingTicks());
        assertFalse(landed.armed());
        assertEquals(0, landed.remainingTicks());
    }

    @Test
    void velocityArmExpiresIfThePacketNeverCausesTakeoff() {
        Clutch.VelocityArmState state = new Clutch.VelocityArmState(true, false, 1);

        Clutch.VelocityArmState expired = Clutch.advanceVelocityArm(state, true);

        assertFalse(expired.armed());
        assertFalse(expired.airborneSeen());
    }
}
