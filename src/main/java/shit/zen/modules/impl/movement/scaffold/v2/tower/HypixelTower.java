/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTowerHypixel.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class HypixelTower implements Tower {
    @Override
    public String name() {
        return "Hypixel";
    }

    @Override
    public BlockPos targetedPosition(TargetInput input) {
        if (!input.moving()) {
            List<BlockPos> candidates = List.of(
                    input.blockPos().offset(0, 0, 1),
                    input.blockPos().offset(0, 0, -1),
                    input.blockPos().offset(1, 0, 0),
                    input.blockPos().offset(-1, 0, 0));
            BlockPos blockOffset = candidates.stream()
                    .min(Comparator.comparingDouble(candidate -> Vec3.atCenterOf(candidate)
                            .distanceToSqr(input.playerPosition())))
                    .map(BlockPos::below)
                    .orElse(input.blockPos());
            if (!input.redstoneConductor().test(blockOffset)) {
                return blockOffset;
            }
        }
        return Tower.super.targetedPosition(input);
    }

    @Override
    public TickMotionDecision tick(TickInput input) {
        if (!input.isTowerActive()) {
            return TickMotionDecision.idle(input.velocity(), false);
        }

        Vec3 velocity = input.velocity();
        boolean changed = false;
        if (input.playerX() % 1.0 != 0.0 && !input.moving()) {
            double centeredX = Math.min(Math.rint(input.playerX()) - input.playerX(), 0.281);
            velocity = new Vec3(centeredX, velocity.y, velocity.z);
            changed = true;
        }

        if (input.airTicks() > 14) {
            velocity = new Vec3(
                    velocity.x * 0.6,
                    velocity.y - 0.09,
                    velocity.z * 0.6);
            return new TickMotionDecision(
                    true,
                    velocity,
                    true,
                    null,
                    null,
                    false,
                    Vec3.ZERO);
        }

        switch (input.airTicks() % 3) {
            case 0 -> {
                velocity = new Vec3(velocity.x, 0.42, velocity.z);
                double speed = 0.247 - TowerMath.unitSample(input.randomSample()) / 100.0;
                velocity = TowerMath.withStrafe(
                        velocity,
                        speed,
                        input.directionalInput(),
                        input.movementYaw());
                changed = true;
            }
            case 2 -> {
                velocity = new Vec3(
                        velocity.x,
                        1.0 - input.playerY() % 1.0,
                        velocity.z);
                changed = true;
            }
            default -> {
            }
        }

        return new TickMotionDecision(
                true,
                velocity,
                changed,
                null,
                null,
                false,
                Vec3.ZERO);
    }
}
