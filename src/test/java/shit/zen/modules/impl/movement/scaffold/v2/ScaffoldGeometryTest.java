package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.rotation.Rotation;

final class ScaffoldGeometryTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void likelyZeroUsesMinecraftEqualTolerance() {
        assertTrue(ScaffoldGeometry.isLikelyZero(new Vec3(0.001, 0.0, 0.0)));
        assertFalse(ScaffoldGeometry.isLikelyZero(new Vec3(0.01, 0.0, 0.0)));
    }

    @Test
    void lineFindsNearestPointOnAxisAlignedBox() {
        ScaffoldGeometry.Line line = new ScaffoldGeometry.Line(
                new Vec3(-2.0, 2.0, 0.5),
                new Vec3(1.0, 0.0, 0.0));
        AABB box = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

        assertVector(new Vec3(0.0, 2.0, 0.5), line.nearestPointTo(box));
        assertEquals(1.0, line.distanceToSqr(box), EPSILON);
    }

    @Test
    void trimFaceRemovesFifteenPercentOnEachVariableAxis() {
        ScaffoldGeometry.AlignedFace face = new ScaffoldGeometry.AlignedFace(
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(1.0, 1.0, 0.0));

        ScaffoldGeometry.AlignedFace trimmed = ScaffoldFacePointFactory.trimFace(face);

        assertVector(new Vec3(0.15, 0.15, 0.0), trimmed.from);
        assertVector(new Vec3(0.85, 0.85, 0.0), trimmed.to);
    }

    @Test
    void nearestPointOnFaceUsesRotationLineAndClampsToTrimmedBorder() {
        Vec3 planningEye = new Vec3(2.0, 0.5, 1.0);
        Rotation rotation = new Rotation(planningEye, new Vec3(2.0, 0.5, 0.0));
        ScaffoldFacePointFactory factory = new ScaffoldFacePointFactory(
                planningEye,
                null,
                rotation);
        ScaffoldGeometry.AlignedFace face = new ScaffoldGeometry.AlignedFace(
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(1.0, 1.0, 0.0));

        Vec3 target = factory.producePositionOnFace(face, BlockPos.ZERO);

        assertVector(new Vec3(0.85, 0.5, 0.0), target);
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
