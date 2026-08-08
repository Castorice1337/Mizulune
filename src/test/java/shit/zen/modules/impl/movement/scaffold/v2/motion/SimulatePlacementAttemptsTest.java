package shit.zen.modules.impl.movement.scaffold.v2.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

final class SimulatePlacementAttemptsTest {
    @Test
    void preservesLiquidBounceDefaults() {
        assertFalse(SimulatePlacementAttempts.DEFAULTS.enabled());
        assertEquals(5, SimulatePlacementAttempts.DEFAULTS.cps().minimum());
        assertEquals(8, SimulatePlacementAttempts.DEFAULTS.cps().maximum());
        assertTrue(SimulatePlacementAttempts.DEFAULTS.failedAttemptsOnly());
    }

    @Test
    void failedAttemptsOnlyIgnoresSameYAndAcceptsOnlyUnplaceableFaces() {
        SimulatePlacementAttempts.Settings settings = settings(true);

        assertTrue(SimulatePlacementAttempts.shouldSimulate(
                placement(false, true, 64, 64, true, 65),
                settings));
        assertFalse(SimulatePlacementAttempts.shouldSimulate(
                placement(true, true, 64, 64, false, 65),
                settings));
    }

    @Test
    void sameYPolicyMatchesClickedHeightAndRejectsPlaceableTopFace() {
        SimulatePlacementAttempts.Settings settings = settings(false);

        assertTrue(SimulatePlacementAttempts.shouldSimulate(
                placement(true, true, 64, 64, false, 65),
                settings));
        assertTrue(SimulatePlacementAttempts.shouldSimulate(
                placement(false, true, 64, 64, true, 65),
                settings));
        assertFalse(SimulatePlacementAttempts.shouldSimulate(
                placement(true, true, 64, 64, true, 65),
                settings));
        assertFalse(SimulatePlacementAttempts.shouldSimulate(
                placement(false, true, 63, 64, false, 65),
                settings));
    }

    @Test
    void normalPolicyAcceptsTargetsUnderPlayerExceptToweringTopFace() {
        SimulatePlacementAttempts.Settings settings = settings(false);

        assertFalse(SimulatePlacementAttempts.shouldSimulate(
                placement(true, false, 64, 0, true, 65),
                settings));
        assertTrue(SimulatePlacementAttempts.shouldSimulate(
                placement(true, false, 63, 0, true, 65),
                settings));
        assertFalse(SimulatePlacementAttempts.shouldSimulate(
                placement(true, false, 65, 0, false, 65),
                settings));
    }

    @Test
    void attemptRequiresMovementAndCurrentCpsClickTick() {
        SimulatePlacementAttempts.Settings settings = settings(true);
        SimulatePlacementAttempts.PlacementInput placement =
                placement(false, false, 64, 0, false, 65);

        assertFalse(SimulatePlacementAttempts.shouldAttempt(
                new SimulatePlacementAttempts.AttemptInput(placement, false, true),
                settings));
        assertFalse(SimulatePlacementAttempts.shouldAttempt(
                new SimulatePlacementAttempts.AttemptInput(placement, true, false),
                settings));
        assertTrue(SimulatePlacementAttempts.shouldAttempt(
                new SimulatePlacementAttempts.AttemptInput(placement, true, true),
                settings));
    }

    @Test
    void preconditionsAndFixedCpsRangeArePureDecisions() {
        SimulatePlacementAttempts.Settings settings = settings(true);
        SimulatePlacementAttempts.PlacementInput noHand = new SimulatePlacementAttempts.PlacementInput(
                false, true, true, false, false, 64, 0, false, 65);
        SimulatePlacementAttempts.CpsRange fixed = new SimulatePlacementAttempts.CpsRange(7, 7);

        assertFalse(SimulatePlacementAttempts.shouldSimulate(noHand, settings));
        assertEquals(7, fixed.sample(new Random(1L)));
    }

    private static SimulatePlacementAttempts.Settings settings(boolean failedOnly) {
        return new SimulatePlacementAttempts.Settings(
                true,
                SimulatePlacementAttempts.DEFAULT_CPS,
                failedOnly);
    }

    private static SimulatePlacementAttempts.PlacementInput placement(
            boolean canPlaceOnFace,
            boolean sameY,
            int clickedY,
            int placementY,
            boolean clickedFaceUp,
            int playerBlockY) {
        return new SimulatePlacementAttempts.PlacementInput(
                true,
                true,
                true,
                canPlaceOnFace,
                sameY,
                clickedY,
                placementY,
                clickedFaceUp,
                playerBlockY);
    }
}
