package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class ScaffoldMovementPlannerTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void movementInputMatchesLiquidBounceYawRules() {
        assertEquals(30.0f, ScaffoldMovementPlanner.getMovementDirectionOfInput(
                30.0f,
                DirectionalInput.NONE));
        assertEquals(0.0f, ScaffoldMovementPlanner.getMovementDirectionOfInput(
                0.0f,
                new DirectionalInput(true, false, false, false)));
        assertEquals(180.0f, ScaffoldMovementPlanner.getMovementDirectionOfInput(
                0.0f,
                new DirectionalInput(false, true, false, false)));
        assertEquals(-90.0f, ScaffoldMovementPlanner.getMovementDirectionOfInput(
                0.0f,
                new DirectionalInput(false, false, true, false)));
        assertEquals(90.0f, ScaffoldMovementPlanner.getMovementDirectionOfInput(
                0.0f,
                new DirectionalInput(false, false, false, true)));
        assertEquals(-45.0f, ScaffoldMovementPlanner.getMovementDirectionOfInput(
                0.0f,
                new DirectionalInput(true, false, true, false)));
    }

    @Test
    void movementDirectionIsQuantizedToEightLiquidBounceDirections() {
        assertVector(new Vec3(0.0, 0.0, 1.0), ScaffoldMovementPlanner.chooseDirection(0.0f));
        assertVector(new Vec3(-Math.sqrt(0.5), 0.0, Math.sqrt(0.5)),
                ScaffoldMovementPlanner.chooseDirection(45.0f));
        assertVector(new Vec3(-1.0, 0.0, 0.0), ScaffoldMovementPlanner.chooseDirection(90.0f));
        assertVector(new Vec3(1.0, 0.0, 0.0), ScaffoldMovementPlanner.chooseDirection(-90.0f));
        assertVector(new Vec3(0.0, 0.0, -1.0), ScaffoldMovementPlanner.chooseDirection(180.0f));
    }

    @Test
    void halfDirectionBoundaryUsesKotlinRoundTiesToEven() {
        assertVector(new Vec3(0.0, 0.0, 1.0), ScaffoldMovementPlanner.chooseDirection(22.5f));
        assertVector(new Vec3(-1.0, 0.0, 0.0), ScaffoldMovementPlanner.chooseDirection(67.5f));
    }

    @Test
    void historyLineUsesOnlyTheLastTwoPlacedBlocks() {
        ScaffoldMovementPlanner planner = new ScaffoldMovementPlanner();
        planner.trackPlacedBlock(new BlockPos(0, 2, 0));
        planner.trackPlacedBlock(new BlockPos(1, 2, 0));
        planner.trackPlacedBlock(new BlockPos(1, 2, 1));
        planner.trackPlacedBlock(new BlockPos(1, 2, 2));

        ScaffoldGeometry.Line line = planner.fitLineThroughLastPlacedBlocks();

        assertVector(new Vec3(1.0, 2.0, 1.5), line.position);
        assertVector(new Vec3(0.0, 0.0, 1.0), line.direction);
    }

    @Test
    void historyLineIsRejectedBelowDotThreshold() {
        ScaffoldGeometry.Line line = new ScaffoldGeometry.Line(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0));

        assertTrue(ScaffoldMovementPlanner.divergesTooMuchFromDirection(
                line,
                new Vec3(0.49, 0.0, Math.sqrt(1.0 - 0.49 * 0.49))));
        assertFalse(ScaffoldMovementPlanner.divergesTooMuchFromDirection(
                line,
                new Vec3(0.5, 0.0, Math.sqrt(0.75))));
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
