/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce FaceTargetPositionFactory:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 scaffold pipeline.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.modules.impl.movement.scaffold.v2.technique.Technique;
import shit.zen.utils.rotation.Rotation;

public final class ScaffoldFacePointFactory extends ClientBase {
    private static final float YAW_TOLERANCE = 5.0f;

    private final Vec3 planningEye;
    private final ScaffoldMovementPlanner.MovementLine optimalLine;
    private final Rotation serverRotation;
    private final Technique.AimMode aimMode;
    private final Vec3 playerPosition;
    private final float playerYaw;
    private final boolean movingHorizontally;
    private final DoubleSupplier randomSource;

    public ScaffoldFacePointFactory(
            Vec3 planningEye,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            Rotation serverRotation) {
        this(planningEye, optimalLine, serverRotation, Technique.AimMode.STABILIZED);
    }

    public ScaffoldFacePointFactory(
            Vec3 planningEye,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            Rotation serverRotation,
            Technique.AimMode aimMode) {
        this(
                planningEye,
                optimalLine,
                serverRotation,
                aimMode,
                currentPlayerPosition(),
                currentPlayerYaw(),
                isPlayerMovingHorizontally(),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    ScaffoldFacePointFactory(
            Vec3 planningEye,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            Rotation serverRotation,
            Technique.AimMode aimMode,
            Vec3 playerPosition,
            float playerYaw,
            boolean movingHorizontally,
            DoubleSupplier randomSource) {
        this.planningEye = planningEye;
        this.optimalLine = optimalLine;
        this.serverRotation = serverRotation;
        this.aimMode = Objects.requireNonNull(aimMode, "aimMode");
        this.playerPosition = playerPosition;
        this.playerYaw = playerYaw;
        this.movingHorizontally = movingHorizontally;
        this.randomSource = Objects.requireNonNull(randomSource, "randomSource");
    }

    public Vec3 producePositionOnFace(
            ScaffoldGeometry.AlignedFace face,
            BlockPos targetPos) {
        if (face == null || targetPos == null || this.planningEye == null) {
            return null;
        }
        return switch (this.aimMode) {
            case CENTER -> face.center();
            case RANDOM -> this.randomPointOnFace(trimFace(face));
            case STABILIZED -> this.produceStabilizedPosition(face, targetPos);
            case NEAREST_ROTATION -> this.aimAtNearestPointToRotationLine(targetPos, trimFace(face));
            case REVERSE_YAW -> this.produceYawPosition(face, targetPos, 180.0f);
            case DIAGONAL_YAW -> this.produceYawPosition(face, targetPos, 75.0f);
            case ANGLE_YAW -> this.produceYawPosition(face, targetPos, 45.0f);
            case EDGE_POINT -> this.produceEdgePosition(face, targetPos);
        };
    }

    private Vec3 produceStabilizedPosition(
            ScaffoldGeometry.AlignedFace face,
            BlockPos targetPos) {
        ScaffoldGeometry.AlignedFace trimmedFace = trimFace(face)
                .offset(Vec3.atLowerCornerOf(targetPos));
        ScaffoldGeometry.AlignedFace targetFace = this.getStabilizedTargetFace(trimmedFace);
        if (targetFace == null) {
            targetFace = trimmedFace;
        }
        Vec3 targetOrigin = Vec3.atLowerCornerOf(targetPos);
        ScaffoldGeometry.AlignedFace localFace = targetFace.offset(targetOrigin.scale(-1.0));
        return this.aimAtNearestPointToRotationLine(targetPos, localFace);
    }

    static ScaffoldGeometry.AlignedFace trimFace(ScaffoldGeometry.AlignedFace face) {
        Vec3 offsets = face.dimensions().scale(0.15);
        double[] xRange = range(face.from.x + offsets.x, face.to.x - offsets.x, face.center().x);
        double[] yRange = range(face.from.y + offsets.y, face.to.y - offsets.y, face.center().y);
        double[] zRange = range(face.from.z + offsets.z, face.to.z - offsets.z, face.center().z);
        return new ScaffoldGeometry.AlignedFace(
                new Vec3(
                        Mth.clamp(face.from.x, xRange[0], xRange[1]),
                        Mth.clamp(face.from.y, yRange[0], yRange[1]),
                        Mth.clamp(face.from.z, zRange[0], zRange[1])),
                new Vec3(
                        Mth.clamp(face.to.x, xRange[0], xRange[1]),
                        Mth.clamp(face.to.y, yRange[0], yRange[1]),
                        Mth.clamp(face.to.z, zRange[0], zRange[1])));
    }

    private Vec3 aimAtNearestPointToRotationLine(
            BlockPos targetPos,
            ScaffoldGeometry.AlignedFace face) {
        if (this.serverRotation == null) {
            return null;
        }
        if (Mth.equal(face.area(), 0.0)) {
            return face.from;
        }
        Vec3 targetOrigin = Vec3.atLowerCornerOf(targetPos);
        Vec3 direction = Vec3.directionFromRotation(
                this.serverRotation.getPitch(),
                this.serverRotation.getYaw());
        ScaffoldGeometry.Line rotationLine = new ScaffoldGeometry.Line(
                this.planningEye.subtract(targetOrigin),
                direction);
        return face.nearestPointTo(rotationLine);
    }

    private ScaffoldGeometry.AlignedFace getStabilizedTargetFace(
            ScaffoldGeometry.AlignedFace trimmedFace) {
        if (this.optimalLine == null || this.playerPosition == null) {
            return null;
        }
        Vec3 nearestPointToOptimalLine = this.optimalLine.nearestPointTo(this.playerPosition);
        Vec3 directionToOptimalLine = this.playerPosition
                .subtract(nearestPointToOptimalLine)
                .normalize();
        ScaffoldGeometry.Line optimalLineFromPlayer = new ScaffoldGeometry.Line(
                this.planningEye,
                this.optimalLine.direction());
        Vec3 collisionWithFacePlane = trimmedFace.toPlane().intersection(optimalLineFromPlayer);
        if (collisionWithFacePlane == null) {
            return null;
        }

        Vec3 cropEnd = this.playerPosition.add(directionToOptimalLine.scale(2.0));
        AABB cropBox = new AABB(
                collisionWithFacePlane.x,
                this.playerPosition.y - 2.0,
                collisionWithFacePlane.z,
                cropEnd.x,
                this.playerPosition.y + 1.0,
                cropEnd.z);
        ScaffoldGeometry.AlignedFace clampedFace = trimmedFace.clamp(cropBox);
        return clampedFace.area() < 0.0001 ? null : clampedFace;
    }

    private Vec3 produceYawPosition(
            ScaffoldGeometry.AlignedFace face,
            BlockPos targetPos,
            float angle) {
        ScaffoldGeometry.AlignedFace trimmedFace = trimFace(face);
        if (!this.movingHorizontally) {
            return this.aimAtNearestPointToRotationLine(targetPos, trimmedFace);
        }
        Vec3 yawPoint = this.aimAtNearestPointToYaw(targetPos, trimmedFace, angle);
        return yawPoint != null
                ? yawPoint
                : this.aimAtNearestPointToRotationLine(targetPos, trimmedFace);
    }

    private Vec3 aimAtNearestPointToYaw(
            BlockPos targetPos,
            ScaffoldGeometry.AlignedFace face,
            float angle) {
        if (Mth.equal(face.area(), 0.0)) {
            return face.from;
        }

        float yaw = Mth.wrapDegrees(this.playerYaw);
        float highTargetYaw = Mth.wrapDegrees(yaw + angle);
        float lowTargetYaw = Mth.wrapDegrees(yaw - angle);
        Segment highSegment = intersectFaceWithYawPlane(face, targetPos, highTargetYaw, this.planningEye);
        Segment lowSegment = intersectFaceWithYawPlane(face, targetPos, lowTargetYaw, this.planningEye);
        if (highSegment == null && lowSegment == null) {
            return null;
        }

        Vec3 highClosestPoint = highSegment == null
                ? null
                : this.findClosestPointToYaw(highSegment, targetPos, highTargetYaw);
        Vec3 lowClosestPoint = lowSegment == null
                ? null
                : this.findClosestPointToYaw(lowSegment, targetPos, lowTargetYaw);
        float highTolerance = highClosestPoint == null
                ? Float.MAX_VALUE
                : this.calculateYawDifference(highClosestPoint, targetPos, highTargetYaw);
        float lowTolerance = lowClosestPoint == null
                ? Float.MAX_VALUE
                : this.calculateYawDifference(lowClosestPoint, targetPos, lowTargetYaw);

        if (highTolerance <= YAW_TOLERANCE && lowTolerance <= YAW_TOLERANCE) {
            return highTolerance < lowTolerance ? highClosestPoint : lowClosestPoint;
        }
        if (highTolerance <= YAW_TOLERANCE) {
            return highClosestPoint;
        }
        return lowTolerance <= YAW_TOLERANCE ? lowClosestPoint : null;
    }

    private Vec3 findClosestPointToYaw(
            Segment segment,
            BlockPos targetPos,
            float targetYaw) {
        Vec3 start = segment.first();
        Vec3 end = segment.second();
        Vec3 segmentDelta = end.subtract(start);
        float startYaw = this.calculateYaw(start, targetPos);
        float endYaw = this.calculateYaw(end, targetPos);
        float yawDifference = Mth.wrapDegrees(endYaw - startYaw);
        float targetYawDifference = Mth.wrapDegrees(targetYaw - startYaw);
        float factor = yawDifference != 0.0f ? targetYawDifference / yawDifference : 0.0f;
        return start.add(segmentDelta.scale(Mth.clamp((double) factor, 0.0, 1.0)));
    }

    private float calculateYaw(Vec3 localPoint, BlockPos targetPos) {
        Vec3 eyeRelativeToTarget = this.planningEye.subtract(Vec3.atLowerCornerOf(targetPos));
        double deltaX = localPoint.x - eyeRelativeToTarget.x;
        double deltaZ = localPoint.z - eyeRelativeToTarget.z;
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f);
    }

    private float calculateYawDifference(
            Vec3 localPoint,
            BlockPos targetPos,
            float targetYaw) {
        return Math.abs(Mth.wrapDegrees(this.calculateYaw(localPoint, targetPos) - targetYaw));
    }

    private Vec3 produceEdgePosition(
            ScaffoldGeometry.AlignedFace face,
            BlockPos targetPos) {
        ScaffoldGeometry.AlignedFace trimmedFace = trimFace(face);
        if (!this.movingHorizontally || this.playerPosition == null) {
            return this.aimAtNearestPointToRotationLine(targetPos, trimmedFace);
        }

        Vec3 playerRelativeToTarget = this.playerPosition.subtract(Vec3.atLowerCornerOf(targetPos));
        Vec3 result = null;
        double furthestDistance = Double.NEGATIVE_INFINITY;
        for (Vec3 edgePoint : edgePoints(trimmedFace)) {
            double distance = edgePoint.distanceToSqr(playerRelativeToTarget);
            if (distance > furthestDistance) {
                furthestDistance = distance;
                result = edgePoint;
            }
        }
        return result != null
                ? result
                : this.aimAtNearestPointToRotationLine(targetPos, trimmedFace);
    }

    private Vec3 randomPointOnFace(ScaffoldGeometry.AlignedFace face) {
        return new Vec3(
                this.randomCoordinate(face.from.x, face.to.x),
                this.randomCoordinate(face.from.y, face.to.y),
                this.randomCoordinate(face.from.z, face.to.z));
    }

    private double randomCoordinate(double from, double to) {
        if (Double.compare(from, to) == 0) {
            return from;
        }
        double sample = this.randomSource.getAsDouble();
        if (!Double.isFinite(sample)) {
            sample = 0.5;
        }
        sample = Mth.clamp(sample, 0.0, Math.nextDown(1.0));
        return from + (to - from) * sample;
    }

    private static Segment intersectFaceWithYawPlane(
            ScaffoldGeometry.AlignedFace face,
            BlockPos targetPos,
            float targetYaw,
            Vec3 planningEye) {
        // Intersect LB's vertical yaw plane with the axis-aligned block face, then clip it to the face bounds.
        Vec3 dimensions = face.dimensions();
        Plane facePlane = Plane.fromDirections(
                face.from,
                new Vec3(dimensions.x, dimensions.y, 0.0),
                new Vec3(0.0, dimensions.y, dimensions.z));
        Vec3 yawDirection = new Vec3(0.0, 0.0, 1.0).yRot(targetYaw * Mth.DEG_TO_RAD);
        Plane yawPlane = Plane.fromDirections(
                planningEye.subtract(Vec3.atLowerCornerOf(targetPos)),
                yawDirection,
                new Vec3(0.0, 1.0, 0.0));
        if (facePlane == null || yawPlane == null) {
            return null;
        }
        LineData intersection = facePlane.intersection(yawPlane);
        return intersection == null ? null : clipToFace(intersection, face);
    }

    private static Segment clipToFace(
            LineData line,
            ScaffoldGeometry.AlignedFace face) {
        double[] interval = {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
        if (!clipAxis(line.position().x, line.direction().x, face.from.x, face.to.x, interval)
                || !clipAxis(line.position().y, line.direction().y, face.from.y, face.to.y, interval)
                || !clipAxis(line.position().z, line.direction().z, face.from.z, face.to.z, interval)
                || !Double.isFinite(interval[0])
                || !Double.isFinite(interval[1])) {
            return null;
        }
        Vec3 first = line.position().add(line.direction().scale(interval[0]));
        Vec3 second = line.position().add(line.direction().scale(interval[1]));
        return ScaffoldGeometry.isLikelyZero(second.subtract(first))
                ? null
                : new Segment(first, second);
    }

    private static boolean clipAxis(
            double position,
            double direction,
            double minimum,
            double maximum,
            double[] interval) {
        if (Mth.equal(direction, 0.0)) {
            return position >= minimum - 1.0E-7 && position <= maximum + 1.0E-7;
        }
        double first = (minimum - position) / direction;
        double second = (maximum - position) / direction;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1];
    }

    private static Vec3[] edgePoints(ScaffoldGeometry.AlignedFace face) {
        return new Vec3[]{
                new Vec3(face.from.x, face.from.y, face.from.z),
                new Vec3(face.from.x, face.from.y, face.to.z),
                new Vec3(face.from.x, face.to.y, face.from.z),
                new Vec3(face.from.x, face.to.y, face.to.z),
                new Vec3(face.to.x, face.from.y, face.from.z),
                new Vec3(face.to.x, face.from.y, face.to.z),
                new Vec3(face.to.x, face.to.y, face.from.z),
                new Vec3(face.to.x, face.to.y, face.to.z)
        };
    }

    private static Vec3 currentPlayerPosition() {
        return mc == null || mc.player == null ? null : mc.player.position();
    }

    private static float currentPlayerYaw() {
        return mc == null || mc.player == null ? 0.0f : mc.player.getYRot();
    }

    private static boolean isPlayerMovingHorizontally() {
        return mc != null
                && mc.player != null
                && mc.player.input != null
                && (mc.player.input.up
                || mc.player.input.down
                || mc.player.input.left
                || mc.player.input.right);
    }

    private static double[] range(double start, double end, double center) {
        return start <= end
                ? new double[]{start, end}
                : new double[]{center, center};
    }

    private record Plane(Vec3 normal, double distance) {
        private static Plane fromDirections(Vec3 base, Vec3 first, Vec3 second) {
            Vec3 normal = first.cross(second);
            if (ScaffoldGeometry.isLikelyZero(normal)) {
                return null;
            }
            Vec3 normalized = ScaffoldGeometry.normalizeIfNeeded(normal);
            return new Plane(normalized, base.dot(normalized));
        }

        private LineData intersection(Plane other) {
            Vec3 direction = this.normal.cross(other.normal);
            double directionLengthSqr = direction.lengthSqr();
            if (Mth.equal(directionLengthSqr, 0.0)) {
                return null;
            }
            Vec3 weightedNormals = other.normal.scale(this.distance)
                    .subtract(this.normal.scale(other.distance));
            Vec3 position = weightedNormals.cross(direction).scale(1.0 / directionLengthSqr);
            return new LineData(position, direction);
        }
    }

    private record LineData(Vec3 position, Vec3 direction) {
    }

    private record Segment(Vec3 first, Vec3 second) {
    }
}
