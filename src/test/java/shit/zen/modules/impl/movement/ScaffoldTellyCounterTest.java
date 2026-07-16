package shit.zen.modules.impl.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import shit.zen.modules.impl.movement.scaffold.v2.normal.ScaffoldDownFeature;
import shit.zen.utils.rotation.MovementCorrection;
import shit.zen.utils.rotation.RotationApplyMode;
import shit.zen.utils.rotation.SmoothMode;
import shit.zen.value.NumericRange;

final class ScaffoldTellyCounterTest {
    @Test
    void removedLegacyRotationAndSneakValuesStayOutOfTheUi() {
        Scaffold scaffold = new Scaffold();
        scaffold.registerSettings();

        assertNull(scaffold.findValue("Snap"));
        assertNull(scaffold.findValue("Sneak"));
        assertNotNull(scaffold.findValue("RotationTick"));
        assertNull(scaffold.findValue("Max Yaw Speed"));
        assertNull(scaffold.findValue("Max Pitch Speed"));
        assertNull(scaffold.findValue("Min Rotation Step"));
        assertNull(scaffold.findValue("Rotation Epsilon"));
        assertNull(scaffold.findValue("Humanize Rotation"));
        assertNotNull(scaffold.findValue("Eagle"));
        assertNotNull(scaffold.findValue("Angle Smooth"));
        assertNotNull(scaffold.findValue("Horizontal Turn Speed"));
        assertNotNull(scaffold.findValue("Vertical Turn Speed"));
        assertNotNull(scaffold.findValue("Rotation Timing"));
        assertNotNull(scaffold.findValue("Keep Rotation"));
        assertNotNull(scaffold.findValue("Same Y"));
        assertNotNull(scaffold.findValue("Swing"));
        assertEquals(SmoothMode.LINEAR, scaffold.getSmoothMode());
        assertEquals("Normal", scaffold.rotationTiming.getValue());
        assertEquals("Off", scaffold.sameY.getValue());
        assertEquals("Show", scaffold.swing.getValue());
        assertEquals(5, scaffold.resetTicks.getValue().intValue());
        assertFalse(scaffold.keepRotation.getValue());
        assertEquals(2.0, scaffold.resetThreshold.getValue().doubleValue());
        assertTrue(scaffold.prediction.getValue());
        assertTrue(scaffold.autoBlock.getValue());
        assertTrue(scaffold.ledge.getValue());
        assertEquals("Safe", scaffold.safeWalk.getValue());
        assertTrue(scaffold.stabilizeMovement.getValue());
        assertEquals("Normal", scaffold.technique.getValue());
        assertTrue(scaffold.newTellyAlwaysUpdateRotation.getValue());
        assertEquals(1, scaffold.newTellyPlaceTick.getValue().intValue());
        assertEquals(3, scaffold.newTellyRotationTick.getValue().intValue());
        assertFalse(scaffold.newTellyNoUpTelly.getValue());
        assertTrue(scaffold.newTellyHeypixelUpTelly.getValue());
        assertFalse(scaffold.newTellySafeMode.getValue());
        assertFalse(scaffold.newTellyTestOnGround.getValue());
        assertFalse(scaffold.newTellyFixRotation.getValue());
        assertFalse(scaffold.newTellySlowUpTelly.getValue());
        assertTrue(scaffold.newTellyDuplicateRotPlace.getValue());
        assertTrue(scaffold.newTellyInteractItemBeforePlace.getValue());
        assertEquals("Farthest", scaffold.newTellyBlockSlotMode.getValue());
        assertEquals("Normal", scaffold.newTellyJumpMode.getValue());

        NumericRange horizontal = scaffold.horizontalTurnSpeed.getValue();
        NumericRange vertical = scaffold.verticalTurnSpeed.getValue();
        assertEquals(180.0, horizontal.lower());
        assertEquals(180.0, horizontal.upper());
        assertEquals(180.0, vertical.lower());
        assertEquals(180.0, vertical.upper());
        assertTrue(scaffold.getMaxYawSpeed() >= horizontal.lower());
        assertTrue(scaffold.getMaxYawSpeed() <= horizontal.upper());
        assertTrue(scaffold.getMaxPitchSpeed() >= vertical.lower());
        assertTrue(scaffold.getMaxPitchSpeed() <= vertical.upper());
    }

    @Test
    void counterAdvancesAtMostOncePerPlayerTick() {
        Scaffold.CounterState airborne = Scaffold.advanceCounters(-1, 0, 0, 42, false);
        Scaffold.CounterState duplicate = Scaffold.advanceCounters(
                airborne.lastTick(),
                airborne.groundTicks(),
                airborne.airTicks(),
                42,
                true);

        assertEquals(42, duplicate.lastTick());
        assertEquals(0, duplicate.groundTicks());
        assertEquals(1, duplicate.airTicks());

        Scaffold.CounterState grounded = Scaffold.advanceCounters(
                duplicate.lastTick(),
                duplicate.groundTicks(),
                duplicate.airTicks(),
                43,
                true);
        assertEquals(1, grounded.groundTicks());
        assertEquals(0, grounded.airTicks());
    }

    @Test
    void keepRotationForcesSilentApplyWithoutChangingDisabledBehavior() {
        Scaffold scaffold = new Scaffold();
        scaffold.movementCorrection.setValue("ChangeLook");

        assertEquals(RotationApplyMode.CHANGE_LOOK, scaffold.getApplyMode());
        assertEquals(MovementCorrection.CHANGE_LOOK, scaffold.getMovementCorrection());

        scaffold.keepRotation.setValue(true);

        assertEquals(RotationApplyMode.SILENT, scaffold.getApplyMode());
        assertEquals(MovementCorrection.SILENT, scaffold.getMovementCorrection());
    }

    @Test
    void newTellyForcesSilentSnapAndOnlyFixesSensitivityWhenRequested() {
        Scaffold scaffold = new Scaffold();
        scaffold.technique.setValue("New Telly");
        scaffold.movementCorrection.setValue("ChangeLook");
        scaffold.angleSmooth.setValue("Sigmoid");
        scaffold.keepRotation.setValue(true);

        assertEquals(RotationApplyMode.SILENT, scaffold.getApplyMode());
        assertEquals(MovementCorrection.SILENT, scaffold.getMovementCorrection());
        assertEquals(SmoothMode.SNAP, scaffold.getSmoothMode());
        assertFalse(scaffold.shouldSnapToSensitivity());
        assertTrue(scaffold.shouldNormalizeYawForServerPackets());

        scaffold.newTellyFixRotation.setValue(true);
        assertTrue(scaffold.shouldSnapToSensitivity());
    }

    @Test
    void legacyTellyModeMigratesToNormalTechniqueAndFeatureToggle() {
        Scaffold scaffold = new Scaffold();
        scaffold.mode.setValue("Telly");
        scaffold.technique.setValue("Expand");
        scaffold.telly.setValue(false);

        assertTrue(scaffold.migrateLegacyMode());
        assertEquals("Normal", scaffold.mode.getValue());
        assertEquals("Normal", scaffold.technique.getValue());
        assertTrue(scaffold.telly.getValue());
        assertTrue(!scaffold.migrateLegacyMode());
    }

    @Test
    void downFeatureOverridesSafeWalkOnlyWhenFallingThrough() {
        assertTrue(Scaffold.resolveSafeWalk(
                true,
                new ScaffoldDownFeature.State(true, false)));
        assertTrue(!Scaffold.resolveSafeWalk(
                true,
                new ScaffoldDownFeature.State(true, true)));
    }

    @Test
    void towerAndAirborneTowerCleanupBothForceNormalFinder() {
        assertTrue(Scaffold.useNormalFinderForTower(true, false));
        assertTrue(Scaffold.useNormalFinderForTower(false, true));
        assertTrue(!Scaffold.useNormalFinderForTower(false, false));
    }

    @Test
    void creativeFlightDisablesTowerAndStationaryJumpTargeting() {
        assertTrue(Scaffold.shouldActivateTower(false, true, true, 64));
        assertTrue(!Scaffold.shouldActivateTower(true, true, true, 64));

        assertTrue(Scaffold.shouldTargetBelowForStationaryJump(
                false, true, false, false));
        assertTrue(Scaffold.shouldTargetBelowForStationaryJump(
                false, true, true, true));
        assertTrue(!Scaffold.shouldTargetBelowForStationaryJump(
                true, true, false, false));
    }

}
