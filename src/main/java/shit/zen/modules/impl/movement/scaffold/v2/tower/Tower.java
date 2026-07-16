/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTower and tower modes:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Java strategy boundary.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.DirectionalInput;

public interface Tower {
    String name();

    default BlockPos targetedPosition(TargetInput input) {
        return input.blockPos().below();
    }

    default void onJump(JumpInput input) {
    }

    TickMotionDecision tick(TickInput input);

    default void reset() {
    }

    record JumpInput(float motion, boolean cancelled, double playerY) {
        public boolean isValid() {
            return this.motion != 0.0f && !this.cancelled;
        }
    }

    record TickInput(
            boolean jumpPressed,
            int blockCount,
            boolean blockBelow,
            boolean onGround,
            int tickCount,
            int airTicks,
            double playerX,
            double playerY,
            Vec3 velocity,
            DirectionalInput directionalInput,
            float movementYaw,
            double randomSample) {
        public TickInput {
            velocity = velocity == null ? Vec3.ZERO : velocity;
            directionalInput = directionalInput == null ? DirectionalInput.NONE : directionalInput;
        }

        public boolean isTowerActive() {
            return this.jumpPressed && this.blockCount > 0 && this.blockBelow;
        }

        public boolean moving() {
            return this.directionalInput.isMoving();
        }
    }

    record TargetInput(
            BlockPos blockPos,
            Vec3 playerPosition,
            DirectionalInput directionalInput,
            Predicate<BlockPos> redstoneConductor) {
        public TargetInput {
            Objects.requireNonNull(blockPos, "blockPos");
            Objects.requireNonNull(playerPosition, "playerPosition");
            directionalInput = directionalInput == null ? DirectionalInput.NONE : directionalInput;
            redstoneConductor = redstoneConductor == null ? ignored -> false : redstoneConductor;
        }

        public boolean moving() {
            return this.directionalInput.isMoving();
        }
    }
}
