/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldMovementPrediction and MovementUtils:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 scaffold pipeline.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.utils.game.DirectionalInput;

public final class ScaffoldMovementPrediction extends ClientBase {
    private static final int MAX_PLACEMENT_OFFSETS = 4;
    private static final double OPTIMAL_EDGE_DISTANCE = 0.0;

    private final Deque<Vec3> lastPlacementOffsets = new ArrayDeque<>();

    public Prediction predict(
            ScaffoldMovementPlanner.MovementLine optimalLine,
            DirectionalInput input) {
        if (mc.player == null || mc.level == null || optimalLine == null) {
            return Prediction.none();
        }
        if (this.isCloseToEdge(input, OPTIMAL_EDGE_DISTANCE)) {
            return Prediction.none();
        }

        Vec3 fallOffPoint = this.getFallOffPositionOnLine(optimalLine);
        if (fallOffPoint == null) {
            return Prediction.none();
        }
        Vec3 offset;
        Vec3 averagePlacement = this.getAveragePlacementPosition();
        if (averagePlacement == null) {
            Vec3 fallOffPointToPlayer = fallOffPoint.subtract(mc.player.position());
            Vec3 direction = ScaffoldGeometry.isLikelyZero(fallOffPointToPlayer)
                    ? Vec3.ZERO
                    : fallOffPointToPlayer.normalize();
            offset = fallOffPoint.subtract(direction.scale(OPTIMAL_EDGE_DISTANCE));
        } else {
            float lineDirectionAngle = (float) Math.atan2(
                    optimalLine.direction().z,
                    optimalLine.direction().x);
            offset = fallOffPoint.add(averagePlacement.yRot(-lineDirectionAngle));
        }
        return new Prediction(offset, fallOffPoint, averagePlacement);
    }

    public void onPlace(
            ScaffoldMovementPlanner.MovementLine optimalLine,
            Vec3 lastFallOffPosition) {
        if (mc.player == null || optimalLine == null || lastFallOffPosition == null) {
            return;
        }
        float lineDirectionAngle = (float) Math.atan2(
                optimalLine.direction().z,
                optimalLine.direction().x);
        Vec3 unrotatedOffset = mc.player.position()
                .subtract(lastFallOffPosition)
                .yRot(lineDirectionAngle);
        this.lastPlacementOffsets.addLast(unrotatedOffset);
        while (this.lastPlacementOffsets.size() > MAX_PLACEMENT_OFFSETS) {
            this.lastPlacementOffsets.removeFirst();
        }
    }

    public Vec3 getFallOffPositionOnLine(ScaffoldMovementPlanner.MovementLine optimalLine) {
        if (mc.player == null || mc.level == null || optimalLine == null) {
            return null;
        }
        Vec3 nearestPosition = optimalLine.nearestPointTo(mc.player.position());
        Vec3 fromLine = nearestPosition.add(0.0, -0.1, 0.0);
        Vec3 toLine = fromLine.add(optimalLine.direction().scale(3.0));
        Vec3 edgeCollision = findEdgeCollision(
                fromLine,
                toLine,
                this.collectCollisionBoundingBoxes(fromLine, toLine, 0.5f));
        return edgeCollision == null
                ? null
                : new Vec3(edgeCollision.x, mc.player.getY(), edgeCollision.z);
    }

    public Vec3 getAveragePlacementPosition() {
        if (this.lastPlacementOffsets.isEmpty()) {
            return null;
        }
        Vec3 sum = Vec3.ZERO;
        for (Vec3 offset : this.lastPlacementOffsets) {
            sum = sum.add(offset);
        }
        return sum.scale(1.0 / this.lastPlacementOffsets.size());
    }

    public void reset() {
        this.lastPlacementOffsets.clear();
    }

    static Vec3 findEdgeCollision(Vec3 from, Vec3 to, List<AABB> boundingBoxes) {
        List<AABB> remainingBoxes = new ArrayList<>(boundingBoxes);
        Vec3 currentFrom = from;
        Vec3 lineVector = to.subtract(from);
        Vec3 extendedFrom = from.subtract(lineVector.scale(1000.0));
        Vec3 extendedTo = to.add(lineVector.scale(1000.0));
        Set<AABB> cache = new HashSet<>();

        while (true) {
            for (AABB box : remainingBoxes) {
                if (box.contains(currentFrom)) {
                    cache.add(box);
                }
            }
            if (cache.isEmpty()) {
                return currentFrom;
            }
            for (AABB box : cache) {
                if (box.contains(to)) {
                    return null;
                }
            }

            Vec3 next = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (AABB box : cache) {
                Optional<Vec3> clipped = box.clip(extendedTo, extendedFrom);
                if (clipped.isEmpty()) {
                    throw new IllegalArgumentException("Scaffold edge raycast unexpectedly failed");
                }
                double distance = clipped.get().distanceToSqr(to);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    next = clipped.get();
                }
            }
            currentFrom = next;
            remainingBoxes.removeAll(cache);
            cache.clear();
        }
    }

    private boolean isCloseToEdge(
            DirectionalInput input,
            double distance) {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        float movementYaw = ScaffoldMovementPlanner.getMovementDirectionOfInput(
                mc.player.getYRot(),
                input == null
                        ? DirectionalInput.NONE
                        : input);
        float alpha = (movementYaw + 90.0f) * ((float) Math.PI / 180.0f);
        Vec3 horizontalMotion = mc.player.getDeltaMovement().multiply(1.0, 0.0, 1.0);
        Vec3 direction = horizontalMotion.lengthSqr() > 0.003 * 0.003
                ? horizontalMotion.normalize()
                : new Vec3(Math.cos(alpha), 0.0, Math.sin(alpha));
        Vec3 position = mc.player.position();
        Vec3 from = position.add(0.0, -0.1, 0.0);
        Vec3 to = from.add(direction.scale(distance));
        if (findEdgeCollision(
                from,
                to,
                this.collectCollisionBoundingBoxes(from, to, 0.5f)) != null) {
            return true;
        }

        Vec3 playerPositionInTwoTicks = position.add(horizontalMotion);
        return this.wouldBeCloseToFallOff(position)
                || this.wouldBeCloseToFallOff(playerPositionInTwoTicks);
    }

    private boolean wouldBeCloseToFallOff(Vec3 position) {
        AABB hitbox = mc.player.getDimensions(Pose.STANDING)
                .makeBoundingBox(position)
                .inflate(-0.05, 0.0, -0.05)
                .move(0.0, mc.player.fallDistance - mc.player.maxUpStep(), 0.0);
        return mc.level.noCollision(mc.player, hitbox);
    }

    private List<AABB> collectCollisionBoundingBoxes(
            Vec3 from,
            Vec3 to,
            float allowedDropDown) {
        AABB fromBox = mc.player.getDimensions(Pose.STANDING).makeBoundingBox(from);
        AABB toBox = mc.player.getDimensions(Pose.STANDING).makeBoundingBox(to);
        AABB unionBox = fromBox.minmax(toBox);

        BlockPos fromBlockPos = BlockPos.containing(
                unionBox.minX - 0.3 - 1.0E-7,
                unionBox.minY - allowedDropDown - 1.0E-7,
                unionBox.minZ - 0.3 - 1.0E-7);
        BlockPos toBlockPos = BlockPos.containing(
                unionBox.maxX + 0.3 + 1.0E-7,
                unionBox.minY + 1.0E-7,
                unionBox.maxZ + 0.3 + 1.0E-7);

        Vec3 lineVector = to.subtract(from);
        Vec3 extendedFrom = from.subtract(lineVector.scale(1000.0));
        Vec3 extendedTo = to.add(lineVector.scale(1000.0));
        List<AABB> foundBoxes = new ArrayList<>();
        for (BlockPos blockPos : BlockPos.betweenClosed(fromBlockPos, toBlockPos)) {
            for (AABB boundingBox : mc.level.getBlockState(blockPos)
                    .getCollisionShape(mc.level, blockPos)
                    .toAabbs()) {
                AABB adjustedBox = new AABB(
                        boundingBox.minX - 0.3,
                        boundingBox.minY - 1.0,
                        boundingBox.minZ - 0.3,
                        boundingBox.maxX + 0.3,
                        boundingBox.maxY + allowedDropDown + 0.05,
                        boundingBox.maxZ + 0.3)
                        .move(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                if (adjustedBox.clip(extendedFrom, extendedTo).isPresent()) {
                    foundBoxes.add(adjustedBox);
                }
            }
        }
        return foundBoxes;
    }

    public record Prediction(
            Vec3 position,
            Vec3 fallOffPosition,
            Vec3 averagePlacementOffset) {
        private static Prediction none() {
            return new Prediction(null, null, null);
        }

        public boolean predicted() {
            return this.position != null;
        }
    }
}
