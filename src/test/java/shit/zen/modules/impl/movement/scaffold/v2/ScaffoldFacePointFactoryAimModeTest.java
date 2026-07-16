package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.modules.impl.movement.scaffold.v2.technique.Technique;
import shit.zen.utils.rotation.Rotation;

final class ScaffoldFacePointFactoryAimModeTest {
    private static final double EPSILON = 1.0E-6;
    private static final ScaffoldGeometry.AlignedFace FACE = new ScaffoldGeometry.AlignedFace(
            new Vec3(0.0, 0.0, 0.0),
            new Vec3(1.0, 1.0, 0.0));
    private static final ScaffoldGeometry.AlignedFace SIDE_FACE = new ScaffoldGeometry.AlignedFace(
            new Vec3(0.0, 0.0, 0.0),
            new Vec3(0.0, 1.0, 1.0));

    @Test
    void centerUsesTheUntrimmedFaceCenter() {
        Vec3 eye = new Vec3(0.5, 0.5, -1.0);
        Vec3 target = factory(
                Technique.AimMode.CENTER,
                eye,
                new Vec3(0.5, 0.5, -1.0),
                0.0f,
                false,
                () -> 0.5).producePositionOnFace(FACE, BlockPos.ZERO);

        assertVector(new Vec3(0.5, 0.5, 0.0), target);
    }

    @Test
    void randomSamplesInsideTheTrimmedFace() {
        double[] samples = {0.25, 0.75};
        AtomicInteger index = new AtomicInteger();
        DoubleSupplier random = () -> samples[index.getAndIncrement()];
        Vec3 eye = new Vec3(0.5, 0.5, -1.0);

        Vec3 target = factory(
                Technique.AimMode.RANDOM,
                eye,
                eye,
                0.0f,
                false,
                random).producePositionOnFace(FACE, BlockPos.ZERO);

        assertVector(new Vec3(0.325, 0.675, 0.0), target);
    }

    @Test
    void stabilizedDefaultsToNearestRotationWhenThereIsNoMovementLine() {
        Vec3 eye = new Vec3(2.0, 0.5, 1.0);
        Rotation serverRotation = new Rotation(eye, new Vec3(2.0, 0.5, 0.0));
        ScaffoldFacePointFactory stabilized = new ScaffoldFacePointFactory(
                eye,
                null,
                serverRotation,
                Technique.AimMode.STABILIZED,
                Vec3.ZERO,
                0.0f,
                false,
                () -> 0.5);
        ScaffoldFacePointFactory nearest = new ScaffoldFacePointFactory(
                eye,
                null,
                serverRotation,
                Technique.AimMode.NEAREST_ROTATION,
                Vec3.ZERO,
                0.0f,
                false,
                () -> 0.5);

        Vec3 stabilizedPoint = stabilized.producePositionOnFace(FACE, BlockPos.ZERO);
        Vec3 nearestPoint = nearest.producePositionOnFace(FACE, BlockPos.ZERO);

        assertVector(new Vec3(0.85, 0.5, 0.0), stabilizedPoint);
        assertVector(stabilizedPoint, nearestPoint);
    }

    @Test
    void yawModesUseReverseDiagonalAndAnglePlanes() {
        Vec3 eye = new Vec3(0.09, 0.5, 0.5);
        Vec3 playerPosition = new Vec3(0.09, 0.0, 0.5);
        Vec3 reverse = factory(
                Technique.AimMode.REVERSE_YAW,
                eye,
                playerPosition,
                -90.0f,
                true,
                () -> 0.5,
                SIDE_FACE).producePositionOnFace(SIDE_FACE, BlockPos.ZERO);
        Vec3 diagonal = factory(
                Technique.AimMode.DIAGONAL_YAW,
                eye,
                playerPosition,
                15.0f,
                true,
                () -> 0.5,
                SIDE_FACE).producePositionOnFace(SIDE_FACE, BlockPos.ZERO);
        Vec3 angle = factory(
                Technique.AimMode.ANGLE_YAW,
                eye,
                playerPosition,
                45.0f,
                true,
                () -> 0.5,
                SIDE_FACE).producePositionOnFace(SIDE_FACE, BlockPos.ZERO);

        assertYawPoint(reverse);
        assertYawPoint(diagonal);
        assertYawPoint(angle);
    }

    @Test
    void edgePointUsesTheCornerFurthestFromThePlayerWhileMoving() {
        Vec3 eye = new Vec3(0.5, 0.5, -1.0);
        Vec3 target = factory(
                Technique.AimMode.EDGE_POINT,
                eye,
                new Vec3(0.0, 0.0, -1.0),
                0.0f,
                true,
                () -> 0.5).producePositionOnFace(FACE, BlockPos.ZERO);

        assertVector(new Vec3(0.85, 0.85, 0.0), target);
    }

    @Test
    void equivalentMultiTurnYawProducesTheSamePointForEveryAimMode() {
        Vec3 eye = new Vec3(0.25, 1.62, -1.0);
        Vec3 playerPosition = new Vec3(0.25, 0.0, -1.0);

        for (Technique.AimMode aimMode : Technique.AimMode.values()) {
            ScaffoldFacePointFactory bounded = new ScaffoldFacePointFactory(
                    eye,
                    null,
                    new Rotation(-7.0312f, 72.0f),
                    aimMode,
                    playerPosition,
                    -7.0312f,
                    true,
                    () -> 0.375);
            ScaffoldFacePointFactory continuous = new ScaffoldFacePointFactory(
                    eye,
                    null,
                    new Rotation(-1087.0312f, 72.0f),
                    aimMode,
                    playerPosition,
                    -1087.0312f,
                    true,
                    () -> 0.375);

            assertVector(
                    bounded.producePositionOnFace(FACE, BlockPos.ZERO),
                    continuous.producePositionOnFace(FACE, BlockPos.ZERO));
        }
    }

    private static ScaffoldFacePointFactory factory(
            Technique.AimMode aimMode,
            Vec3 eye,
            Vec3 playerPosition,
            float playerYaw,
            boolean moving,
            DoubleSupplier randomSource) {
        return factory(aimMode, eye, playerPosition, playerYaw, moving, randomSource, FACE);
    }

    private static ScaffoldFacePointFactory factory(
            Technique.AimMode aimMode,
            Vec3 eye,
            Vec3 playerPosition,
            float playerYaw,
            boolean moving,
            DoubleSupplier randomSource,
            ScaffoldGeometry.AlignedFace face) {
        return new ScaffoldFacePointFactory(
                eye,
                null,
                new Rotation(eye, face.center()),
                aimMode,
                playerPosition,
                playerYaw,
                moving,
                randomSource);
    }

    private static void assertYawPoint(Vec3 point) {
        assertNotNull(point);
        assertEquals(0.0, point.x, EPSILON);
        assertEquals(0.5, point.z, EPSILON);
        assertTrue(Math.abs(point.y - 0.5) > EPSILON);
        assertTrue(point.y >= 0.15 - EPSILON && point.y <= 0.85 + EPSILON);
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertNotNull(actual);
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
