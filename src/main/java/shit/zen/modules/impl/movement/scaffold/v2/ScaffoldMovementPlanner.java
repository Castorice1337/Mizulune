/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldMovementPlanner:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 event architecture.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.utils.game.DirectionalInput;

public final class ScaffoldMovementPlanner extends ClientBase {
    private static final int MAX_LAST_PLACED_BLOCKS = 4;
    private static final double[] SUPPORT_OFFSETS = {0.301, 0.0, -0.301};

    private final Deque<BlockPos> lastPlacedBlocks = new ArrayDeque<>(MAX_LAST_PLACED_BLOCKS);
    private BlockPos lastPosition;

    public MovementLine getOptimalMovementLine(DirectionalInput input) {
        if (mc.player == null || mc.level == null || input == null) {
            return null;
        }

        Vec3 direction = chooseDirection(getMovementDirectionOfInput(mc.player.getYRot(), input));
        BlockPos blockUnderPlayer = this.findBlockPlayerStandsOn();
        if (blockUnderPlayer == null) {
            return null;
        }

        ScaffoldGeometry.Line lastBlocksLine = this.fitLineThroughLastPlacedBlocks();
        Vec3 lineBaseBlock = lastBlocksLine != null
                && !divergesTooMuchFromDirection(lastBlocksLine, direction)
                ? lastBlocksLine.position
                : Vec3.atLowerCornerOf(blockUnderPlayer);
        Vec3 position = new Vec3(
                lineBaseBlock.x + 0.5,
                mc.player.getY(),
                lineBaseBlock.z + 0.5);
        return new MovementLine(new ScaffoldGeometry.Line(position, direction), blockUnderPlayer);
    }

    public void trackPlacedBlock(BlockPos target) {
        if (target == null || target.equals(this.lastPlacedBlocks.peekLast())) {
            return;
        }
        while (this.lastPlacedBlocks.size() >= MAX_LAST_PLACED_BLOCKS) {
            this.lastPlacedBlocks.removeFirst();
        }
        this.lastPlacedBlocks.addLast(target);
    }

    public void reset() {
        this.lastPosition = null;
        this.lastPlacedBlocks.clear();
    }

    static float getMovementDirectionOfInput(float facingYaw, DirectionalInput input) {
        float actualYaw = facingYaw;
        float forwardMultiplier;
        if (input.backwards() && !input.forwards()) {
            actualYaw += 180.0f;
            forwardMultiplier = -0.5f;
        } else if (input.forwards() && !input.backwards()) {
            forwardMultiplier = 0.5f;
        } else {
            forwardMultiplier = 1.0f;
        }

        if (input.left() && !input.right()) {
            actualYaw -= 90.0f * forwardMultiplier;
        }
        if (input.right() && !input.left()) {
            actualYaw += 90.0f * forwardMultiplier;
        }
        return actualYaw;
    }

    static Vec3 chooseDirection(float currentAngle) {
        float currentDirection = currentAngle / 180.0f * 4.0f + 4.0f;
        double newDirectionNumber = Math.rint(currentDirection);
        float newDirectionAngle = Mth.wrapDegrees(
                (float) ((newDirectionNumber - 4.0) / 4.0 * 180.0 + 90.0));
        float radians = newDirectionAngle * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(radians), 0.0, Mth.sin(radians));
    }

    static boolean divergesTooMuchFromDirection(ScaffoldGeometry.Line line, Vec3 direction) {
        return line.direction.dot(direction) < 0.5;
    }

    ScaffoldGeometry.Line fitLineThroughLastPlacedBlocks() {
        if (this.lastPlacedBlocks.size() < 2) {
            return null;
        }
        Iterator<BlockPos> descending = this.lastPlacedBlocks.descendingIterator();
        BlockPos last = descending.next();
        BlockPos secondToLast = descending.next();

        Vec3 averagePosition = new Vec3(
                secondToLast.getX() + last.getX(),
                secondToLast.getY() + last.getY(),
                secondToLast.getZ() + last.getZ()).scale(0.5);
        Vec3 direction = new Vec3(
                last.getX() - secondToLast.getX(),
                last.getY() - secondToLast.getY(),
                last.getZ() - secondToLast.getZ()).normalize();
        if (ScaffoldGeometry.isLikelyZero(direction)) {
            return null;
        }
        return new ScaffoldGeometry.Line(averagePosition, direction);
    }

    private BlockPos findBlockPlayerStandsOn() {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (double xOffset : SUPPORT_OFFSETS) {
            for (double zOffset : SUPPORT_OFFSETS) {
                BlockPos playerPos = BlockPos.containing(
                        mc.player.getX() + xOffset,
                        mc.player.getY() - 1.0,
                        mc.player.getZ() + zOffset);
                if (!mc.level.getBlockState(playerPos)
                        .getCollisionShape(mc.level, playerPos)
                        .isEmpty()) {
                    candidates.add(playerPos.immutable());
                }
            }
        }

        BlockPos lastPlacedBlock = this.lastPlacedBlocks.peekLast();
        if (lastPlacedBlock != null && candidates.contains(lastPlacedBlock)) {
            return lastPlacedBlock;
        }
        if (this.lastPosition != null && candidates.contains(this.lastPosition)) {
            return this.lastPosition;
        }

        BlockPos selected = candidates.stream().findFirst().orElse(null);
        this.lastPosition = selected;
        return selected;
    }

    public record MovementLine(ScaffoldGeometry.Line geometry, BlockPos support) {
        public Vec3 position() {
            return this.geometry.position;
        }

        public Vec3 direction() {
            return this.geometry.direction;
        }

        public Vec3 nearestPointTo(Vec3 point) {
            return this.geometry.nearestPointTo(point);
        }

        public double distanceToSqr(net.minecraft.world.phys.AABB box) {
            return this.geometry.distanceToSqr(box);
        }
    }
}
