package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.rotation.Rotation;

final class ScaffoldTargetAndPlacementTest {
    @Test
    void normalOffsetsMatchLiquidBounceOrder() {
        assertEquals(List.of(
                new BlockPos(0, 0, 0),
                new BlockPos(0, -1, 0),
                new BlockPos(-1, 0, 0),
                new BlockPos(0, 0, -1),
                new BlockPos(0, 0, 1),
                new BlockPos(1, 0, 0),
                new BlockPos(-1, -1, 0),
                new BlockPos(0, -1, -1),
                new BlockPos(0, -1, 1),
                new BlockPos(1, -1, 0),
                new BlockPos(-1, 0, -1),
                new BlockPos(-1, 0, 1),
                new BlockPos(1, 0, -1),
                new BlockPos(1, 0, 1),
                new BlockPos(-1, -1, -1),
                new BlockPos(-1, -1, 1),
                new BlockPos(1, -1, -1),
                new BlockPos(1, -1, 1)),
                ScaffoldTargetFinder.normalOffsets());
    }

    @Test
    void sameYUsesTheCapturedPlacementLevel() {
        Vec3 predicted = new Vec3(4.9, 72.8, -8.1);

        assertEquals(
                new BlockPos(4, 63, -9),
                ScaffoldTargetFinder.resolveTargetedPosition(predicted, 63, true));
        assertEquals(
                new BlockPos(4, 71, -9),
                ScaffoldTargetFinder.resolveTargetedPosition(predicted, 63, false));
    }

    @Test
    void faceExtractionUsesActualShapeBounds() {
        AABB halfSlab = new AABB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

        ScaffoldGeometry.AlignedFace top = ScaffoldTargetFinder.getFace(halfSlab, Direction.UP);
        ScaffoldGeometry.AlignedFace east = ScaffoldTargetFinder.getFace(halfSlab, Direction.EAST);

        assertEquals(0.5, top.from.y);
        assertEquals(0.5, top.to.y);
        assertEquals(1.0, east.from.x);
        assertEquals(0.0, east.from.y);
        assertEquals(0.5, east.to.y);
    }

    @Test
    void sideSearchKeepsTheLiquidBounceFaceDomain() {
        AABB fullBlock = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        ScaffoldGeometry.AlignedFace west = ScaffoldTargetFinder.getFace(fullBlock, Direction.WEST);

        ScaffoldGeometry.AlignedFace searchFace = ScaffoldTargetFinder.prepareSearchFace(west);

        assertEquals(0.6, searchFace.from.y);
        assertEquals(1.0, searchFace.to.y);
    }

    @Test
    void placementRequiresExactBlockFaceAndMinimumY() {
        BlockPos support = new BlockPos(4, 10, 8);
        BlockPlacementTarget target = new BlockPlacementTarget(
                support,
                support.relative(Direction.NORTH),
                Direction.NORTH,
                new Vec3(4.5, 10.7, 8.0),
                10.6,
                new Rotation(0.0f, 80.0f));

        assertTrue(ScaffoldPlacementPipeline.matches(
                target,
                new BlockHitResult(new Vec3(4.5, 10.7, 8.0), Direction.NORTH, support, false)));
        assertFalse(ScaffoldPlacementPipeline.matches(
                target,
                new BlockHitResult(new Vec3(4.5, 10.7, 8.0), Direction.SOUTH, support, false)));
        assertFalse(ScaffoldPlacementPipeline.matches(
                target,
                new BlockHitResult(
                        new Vec3(4.5, 10.7, 8.0),
                        Direction.NORTH,
                        support.relative(Direction.EAST),
                        false)));
        assertFalse(ScaffoldPlacementPipeline.matches(
                target,
                new BlockHitResult(new Vec3(4.5, 10.5, 8.0), Direction.NORTH, support, false)));
    }

    @Test
    void onTickRestoreYawUsesTheClosestEquivalentPlayerAngle() {
        Rotation restored = ScaffoldPlacementPipeline.fixedPlayerRotation(
                new Rotation(-179.0f, 12.0f),
                new Rotation(179.0f, 80.0f));

        assertEquals(181.0f, restored.getYaw());
        assertEquals(12.0f, restored.getPitch());
    }

    @Test
    void tickFrameCopiesTheMutablePlannedRotation() {
        Rotation plannedRotation = new Rotation(0.0f, 80.0f);
        BlockPlacementTarget target = new BlockPlacementTarget(
                new BlockPos(4, 63, 8),
                new BlockPos(3, 63, 8),
                Direction.WEST,
                new Vec3(4.0, 63.75, 8.5),
                63.0,
                plannedRotation);

        BlockPlacementTarget snapshot = ScaffoldTickFrame.copyTarget(target);
        plannedRotation.setYawPitch(135.0f, 25.0f);

        assertEquals(0.0f, snapshot.rotation().getYaw());
        assertEquals(80.0f, snapshot.rotation().getPitch());
    }
}
