/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTowerVulcan.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

import net.minecraft.world.phys.Vec3;

public final class VulcanTower implements Tower {
    private static final Vec3 STATIONARY_EVEN_PACKET_OFFSET = new Vec3(0.1, 0.0, 0.1);

    @Override
    public String name() {
        return "Vulcan";
    }

    @Override
    public TickMotionDecision tick(TickInput input) {
        boolean evenTick = input.tickCount() % 2 == 0;
        Vec3 outgoingMoveOffset = evenTick && !input.moving()
                ? STATIONARY_EVEN_PACKET_OFFSET
                : Vec3.ZERO;
        if (!input.isTowerActive()) {
            return new TickMotionDecision(
                    false,
                    input.velocity(),
                    false,
                    null,
                    null,
                    false,
                    outgoingMoveOffset);
        }

        double motionY = evenTick ? 0.7 : input.moving() ? 0.42 : 0.6;
        Vec3 velocity = new Vec3(input.velocity().x, motionY, input.velocity().z);
        return new TickMotionDecision(
                true,
                velocity,
                true,
                null,
                null,
                !evenTick,
                outgoingMoveOffset);
    }
}
