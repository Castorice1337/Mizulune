/*
 * Edge geometry helpers extracted from Mizulune/OpenZen SafeWalk.
 *
 * The original SafeWalk edge-distance implementation in this repository is
 * GPL-3.0-or-later. Modified here as a stateless utility for local client-side
 * edge safety checks only; this class does not control input, packets, or
 * anti-cheat bypass behavior.
 */
package shit.zen.utils.game;

import lombok.Generated;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import shit.zen.ClientBase;

public final class EdgeSafetyUtil extends ClientBase {
    public static final double SUPPORT_PROBE_DOWN = 0.08;
    public static final double SUPPORT_PROBE_UP = 0.02;
    public static final double SUPPORT_PROBE_EPSILON = 1.0E-4;
    public static final double RELEASE_DISTANCE_HYSTERESIS = 0.08;
    public static final double MOVEMENT_TRIGGER_MARGIN = 0.04;
    public static final double MIN_UNSNEAK_STEP_DISTANCE = 0.10;
    public static final double MAX_UNSNEAK_STEP_DISTANCE = 0.28;
    public static final double TRUE_EDGE_FALL_DISTANCE = 0.015;

    public static double getDistanceToFall(AABB box) {
        SupportDistances distances = EdgeSafetyUtil.getSupportDistances(box);
        return Math.max(0.0, Math.min(Math.min(distances.east(), distances.west()), Math.min(distances.south(), distances.north())));
    }

    public static double getDistanceToFall(AABB box, MovementVector movementVector) {
        if (movementVector == null) {
            return EdgeSafetyUtil.getDistanceToFall(box);
        }
        return EdgeSafetyUtil.getDirectionalDistanceToFall(box, movementVector.x(), movementVector.z());
    }

    public static double getDirectionalDistanceToFall(AABB box, double directionX, double directionZ) {
        SupportDistances distances = EdgeSafetyUtil.getSupportDistances(box);
        double distance = Double.POSITIVE_INFINITY;
        double absX = Math.abs(directionX);
        double absZ = Math.abs(directionZ);
        if (directionX > SUPPORT_PROBE_EPSILON) {
            distance = Math.min(distance, distances.east() / absX);
        } else if (directionX < -SUPPORT_PROBE_EPSILON) {
            distance = Math.min(distance, distances.west() / absX);
        }
        if (directionZ > SUPPORT_PROBE_EPSILON) {
            distance = Math.min(distance, distances.south() / absZ);
        } else if (directionZ < -SUPPORT_PROBE_EPSILON) {
            distance = Math.min(distance, distances.north() / absZ);
        }
        if (distance == Double.POSITIVE_INFINITY) {
            return 0.0;
        }
        return Math.max(0.0, distance);
    }

    public static SupportDistances getSupportDistances(AABB box) {
        if (mc.player == null || mc.level == null) {
            return new SupportDistances(0.0, 0.0, 0.0, 0.0);
        }
        AABB supportProbe = new AABB(
                box.minX + SUPPORT_PROBE_EPSILON,
                box.minY - SUPPORT_PROBE_DOWN,
                box.minZ + SUPPORT_PROBE_EPSILON,
                box.maxX - SUPPORT_PROBE_EPSILON,
                box.minY + SUPPORT_PROBE_UP,
                box.maxZ - SUPPORT_PROBE_EPSILON);

        double eastSupport = Double.NEGATIVE_INFINITY;
        double westSupport = Double.POSITIVE_INFINITY;
        double southSupport = Double.NEGATIVE_INFINITY;
        double northSupport = Double.POSITIVE_INFINITY;

        for (VoxelShape shape : mc.level.getBlockCollisions(mc.player, supportProbe)) {
            for (AABB support : shape.toAabbs()) {
                if (!EdgeSafetyUtil.overlaps(support.minZ, support.maxZ, box.minZ, box.maxZ)) {
                    continue;
                }
                eastSupport = Math.max(eastSupport, support.maxX);
                westSupport = Math.min(westSupport, support.minX);
            }
            for (AABB support : shape.toAabbs()) {
                if (!EdgeSafetyUtil.overlaps(support.minX, support.maxX, box.minX, box.maxX)) {
                    continue;
                }
                southSupport = Math.max(southSupport, support.maxZ);
                northSupport = Math.min(northSupport, support.minZ);
            }
        }

        double eastDistance = eastSupport == Double.NEGATIVE_INFINITY ? 0.0 : eastSupport - box.maxX;
        double westDistance = westSupport == Double.POSITIVE_INFINITY ? 0.0 : box.minX - westSupport;
        double southDistance = southSupport == Double.NEGATIVE_INFINITY ? 0.0 : southSupport - box.maxZ;
        double northDistance = northSupport == Double.POSITIVE_INFINITY ? 0.0 : box.minZ - northSupport;
        return new SupportDistances(
                Math.max(0.0, eastDistance),
                Math.max(0.0, westDistance),
                Math.max(0.0, southDistance),
                Math.max(0.0, northDistance));
    }

    public static boolean overlaps(double firstMin, double firstMax, double secondMin, double secondMax) {
        return firstMax > secondMin + SUPPORT_PROBE_EPSILON && firstMin < secondMax - SUPPORT_PROBE_EPSILON;
    }

    public static MovementVector getMovementVectorFromInput(float yaw, int forward, int strafe) {
        if (forward == 0 && strafe == 0) {
            return null;
        }
        double yawRadians = Math.toRadians(yaw);
        double x = (double) forward * -Math.sin(yawRadians) + (double) strafe * Math.cos(yawRadians);
        double z = (double) forward * Math.cos(yawRadians) + (double) strafe * Math.sin(yawRadians);
        double length = Math.hypot(x, z);
        if (length <= SUPPORT_PROBE_EPSILON) {
            return null;
        }
        return new MovementVector(x / length, z / length);
    }

    public static double getPredictedUnsneakStepDistance(Vec3 delta, MovementVector movementVector, boolean sneaking) {
        if (delta == null || movementVector == null) {
            return 0.0;
        }
        double currentSpeed = Math.hypot(delta.x, delta.z);
        double directionalSpeed = Math.max(0.0, delta.x * movementVector.x() + delta.z * movementVector.z());
        double predicted = Math.max(currentSpeed, directionalSpeed);
        if (sneaking) {
            predicted = Math.max(predicted * 2.6, MIN_UNSNEAK_STEP_DISTANCE);
        } else {
            predicted = Math.max(predicted, MIN_UNSNEAK_STEP_DISTANCE);
        }
        return Math.min(MAX_UNSNEAK_STEP_DISTANCE, predicted);
    }

    public static boolean isTrueEdge(AABB box) {
        return EdgeSafetyUtil.getDistanceToFall(box) <= TRUE_EDGE_FALL_DISTANCE;
    }

    public static boolean isNearDirectionalEdge(AABB box, MovementVector movementVector, double threshold) {
        if (movementVector == null) {
            return false;
        }
        return EdgeSafetyUtil.getDistanceToFall(box, movementVector) <= Math.max(0.0, threshold);
    }

    public record MovementVector(double x, double z) {
    }

    public record SupportDistances(double east, double west, double south, double north) {
    }

    @Generated
    private EdgeSafetyUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
