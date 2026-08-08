/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce tower motion helpers.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.DirectionalInput;

final class TowerMath {
    private TowerMath() {
    }

    static double truncate(double value) {
        return value < 0.0 ? Math.ceil(value) : Math.floor(value);
    }

    static double unitSample(double sample) {
        if (!Double.isFinite(sample)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(Math.nextDown(1.0), sample));
    }

    static Vec3 withStrafe(Vec3 velocity, double speed, DirectionalInput input, float movementYaw) {
        if (!input.isMoving()) {
            return new Vec3(0.0, velocity.y, 0.0);
        }
        double angle = Math.toRadians(movementYaw);
        return new Vec3(
                -Math.sin(angle) * speed,
                velocity.y,
                Math.cos(angle) * speed);
    }
}
