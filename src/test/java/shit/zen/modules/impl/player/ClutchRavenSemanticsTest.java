package shit.zen.modules.impl.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.rotation.Rotation;
import shit.zen.value.NumericRange;

final class ClutchRavenSemanticsTest {
    @Test
    void searchBoundsMatchRavenRelease161() {
        Clutch.SearchBounds bounds = Clutch.ravenSearchBounds(new Vec3(12.75, 64.2, -3.1));

        assertEquals(7, bounds.minX());
        assertEquals(16, bounds.maxX());
        assertEquals(60, bounds.minY());
        assertEquals(63, bounds.maxY());
        assertEquals(-9, bounds.minZ());
        assertEquals(0, bounds.maxZ());
    }

    @Test
    void futurePositionUsesRavenCurrentAndFutureWeights() {
        assertEquals(7.1, Clutch.ravenCandidateScore(5.0, 8.0, true), 1.0E-9);
        assertEquals(5.0, Clutch.ravenCandidateScore(5.0, 8.0, false), 1.0E-9);
    }

    @Test
    void futurePredictionStopsOnlyAfterTwoBlocksOrCollision() {
        assertFalse(Clutch.shouldStopRavenFuturePrediction(64.0, 62.0, false));
        assertTrue(Clutch.shouldStopRavenFuturePrediction(64.0, 61.999, false));
        assertTrue(Clutch.shouldStopRavenFuturePrediction(64.0, 64.0, true));
    }

    @Test
    void placementUsesConfiguredRavenRotationWindow() {
        Rotation candidate = new Rotation(179.0f, 70.0f);
        Rotation server = new Rotation(-179.0f, 68.0f);

        assertTrue(Clutch.isWithinRavenRotationWindow(candidate, server, 5.0));
        assertFalse(Clutch.isWithinRavenRotationWindow(candidate, server, 3.0));
    }

    @Test
    void airStuckWindowCanCoverAFullDefaultSpeedTurn() {
        assertEquals(20, ClutchAirStuckController.WINDOW_TICKS);
    }

    @Test
    void strictRiskSeparatesSafeLandingsHighFallsAndVoid() {
        Clutch.FallRiskAssessment safe = Clutch.classifyFallRisk(
                true, false, 9.99, 10.0, 6);
        Clutch.FallRiskAssessment highFall = Clutch.classifyFallRisk(
                true, false, 10.0, 10.0, 12);
        Clutch.FallRiskAssessment noLanding = Clutch.classifyFallRisk(
                false, false, 2.0, 10.0, -1);
        Clutch.FallRiskAssessment belowWorld = Clutch.classifyFallRisk(
                false, true, 30.0, 10.0, -1);

        assertEquals(Clutch.FallRisk.SAFE, safe.risk());
        assertFalse(safe.requiresRescue());
        assertEquals(Clutch.FallRisk.HIGH_FALL, highFall.risk());
        assertTrue(highFall.requiresRescue());
        assertEquals(Clutch.FallRisk.VOID, noLanding.risk());
        assertTrue(noLanding.requiresRescue());
        assertEquals(Clutch.FallRisk.VOID, belowWorld.risk());
    }

    @Test
    void placementTargetCannotIntersectThePlayerBox() {
        AABB player = new AABB(0.2, 64.0, 0.2, 0.8, 65.8, 0.8);

        assertTrue(Clutch.intersectsPlacementBox(player, new BlockPos(0, 64, 0)));
        assertFalse(Clutch.intersectsPlacementBox(player, new BlockPos(0, 63, 0)));
    }

    @Test
    void turnSpeedUsesTheSharedZeroToOneEightyRangeShape() {
        assertEquals(8.0, Clutch.sampleTurnSpeed(
                new NumericRange(8.0, 8.0, 0.0, 180.0, 0.1, false)));

        NumericRange range = new NumericRange(20.0, 40.0, 0.0, 180.0, 0.1, false);
        for (int sample = 0; sample < 50; sample++) {
            double speed = Clutch.sampleTurnSpeed(range);
            assertTrue(speed >= 20.0 && speed < 40.0);
        }
    }
}
