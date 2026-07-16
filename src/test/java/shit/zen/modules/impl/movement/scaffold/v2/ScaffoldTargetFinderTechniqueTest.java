package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.modules.impl.movement.scaffold.v2.technique.Technique;

final class ScaffoldTargetFinderTechniqueTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void searchModesExposeNormalDownCardinalAndExactDomains() {
        assertEquals(ScaffoldTargetFinder.normalOffsets(),
                ScaffoldTargetFinder.searchOffsets(Technique.SearchOffsets.NORMAL));
        assertEquals(ScaffoldTargetFinder.downOffsets(),
                ScaffoldTargetFinder.searchOffsets(Technique.SearchOffsets.DOWN));
        assertEquals(50, ScaffoldTargetFinder.downOffsets().size());
        assertTrue(ScaffoldTargetFinder.downOffsets().contains(new BlockPos(2, 0, -2)));
        assertTrue(ScaffoldTargetFinder.downOffsets().contains(new BlockPos(-2, -1, 2)));
        List<BlockPos> cardinal = ScaffoldTargetFinder.searchOffsets(
                Technique.SearchOffsets.CARDINAL);
        assertEquals(4, cardinal.size());
        assertTrue(cardinal.stream().allMatch(offset -> offset.getY() == 0
                && Math.abs(offset.getX()) + Math.abs(offset.getZ()) == 1));
        assertEquals(List.of(BlockPos.ZERO),
                ScaffoldTargetFinder.searchOffsets(Technique.SearchOffsets.EXACT));
    }

    @Test
    void lineOrPositionUsesTheLineWhilePositionIgnoresIt() {
        ScaffoldMovementPlanner.MovementLine line = new ScaffoldMovementPlanner.MovementLine(
                new ScaffoldGeometry.Line(Vec3.ZERO, new Vec3(0.0, 0.0, 1.0)),
                BlockPos.ZERO);
        Vec3 predictedPosition = new Vec3(10.0, 0.5, 5.5);
        AABB nearLine = new AABB(0.0, 0.0, 5.0, 1.0, 1.0, 6.0);
        AABB nearPosition = new AABB(9.0, 0.0, 5.0, 10.0, 1.0, 6.0);

        double linePriorityNearLine = ScaffoldTargetFinder.priorityDistance(
                nearLine,
                predictedPosition,
                line,
                Technique.TargetPriority.LINE_OR_POSITION);
        double linePriorityNearPosition = ScaffoldTargetFinder.priorityDistance(
                nearPosition,
                predictedPosition,
                line,
                Technique.TargetPriority.LINE_OR_POSITION);
        double positionPriorityNearLine = ScaffoldTargetFinder.priorityDistance(
                nearLine,
                predictedPosition,
                line,
                Technique.TargetPriority.POSITION);
        double positionPriorityNearPosition = ScaffoldTargetFinder.priorityDistance(
                nearPosition,
                predictedPosition,
                line,
                Technique.TargetPriority.POSITION);

        assertTrue(linePriorityNearLine < linePriorityNearPosition);
        assertTrue(positionPriorityNearPosition < positionPriorityNearLine);
        assertEquals(
                positionPriorityNearLine,
                ScaffoldTargetFinder.priorityDistance(
                        nearLine,
                        predictedPosition,
                        null,
                        Technique.TargetPriority.LINE_OR_POSITION),
                EPSILON);
    }

    @Test
    void facingAwayFacesAreOnlyAcceptedWhenTheTechniqueRequestsThem() {
        assertTrue(ScaffoldTargetFinder.acceptsFacing(0.25, false));
        assertFalse(ScaffoldTargetFinder.acceptsFacing(-0.25, false));
        assertTrue(ScaffoldTargetFinder.acceptsFacing(-0.25, true));
    }
}
