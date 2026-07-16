package shit.zen.modules.impl.movement.scaffold.v2.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class ScaffoldPlacementGateTest {
    @Test
    void defaultsMatchLiquidSource() {
        assertEquals(0, ScaffoldPlacementGate.DEFAULTS.delayMinTicks());
        assertEquals(0, ScaffoldPlacementGate.DEFAULTS.delayMaxTicks());
        assertEquals(0.0, ScaffoldPlacementGate.DEFAULTS.minDistance());
    }

    @Test
    void zeroDelayAllowsTheNextTickAndMaximumDelaySkipsFortyTicks() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        gate.onPlacementSucceeded(100, 0);

        assertFalse(gate.canAttempt(100));
        assertTrue(gate.canAttempt(101));

        gate.onPlacementSucceeded(200, 40);
        assertFalse(gate.canAttempt(240));
        assertTrue(gate.canAttempt(241));
    }

    @Test
    void northSouthUseZAndEastWestUseXWithInclusiveBoundary() {
        Vec3 eye = Vec3.ZERO;

        assertFalse(ScaffoldPlacementGate.passesMinDistance(
                Direction.NORTH,
                eye,
                new Vec3(10.0, 0.0, 0.249999),
                0.25));
        assertTrue(ScaffoldPlacementGate.passesMinDistance(
                Direction.SOUTH,
                eye,
                new Vec3(0.0, 0.0, -0.25),
                0.25));
        assertFalse(ScaffoldPlacementGate.passesMinDistance(
                Direction.EAST,
                eye,
                new Vec3(0.249999, 0.0, 10.0),
                0.25));
        assertTrue(ScaffoldPlacementGate.passesMinDistance(
                Direction.WEST,
                eye,
                new Vec3(-0.25, 0.0, 0.0),
                0.25));
    }

    @Test
    void verticalFacesIgnoreMinDistanceAndZeroAllowsAnyHorizontalHit() {
        assertTrue(ScaffoldPlacementGate.passesMinDistance(
                Direction.UP,
                Vec3.ZERO,
                Vec3.ZERO,
                0.25));
        assertTrue(ScaffoldPlacementGate.passesMinDistance(
                Direction.NORTH,
                Vec3.ZERO,
                Vec3.ZERO,
                0.0));
    }

    @Test
    void settingBoundsAreInclusiveAndRejectInvalidRanges() {
        new ScaffoldPlacementGate.Settings(0, 40, 0.25);

        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldPlacementGate.Settings(-1, 0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldPlacementGate.Settings(0, 41, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldPlacementGate.Settings(2, 1, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldPlacementGate.Settings(0, 0, -0.001));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldPlacementGate.Settings(0, 0, 0.250001));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldPlacementGate.Settings(0, 0, Double.NaN));
    }
}
