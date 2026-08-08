/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTowerNone.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

public final class NoneTower implements Tower {
    @Override
    public String name() {
        return "None";
    }

    @Override
    public TickMotionDecision tick(TickInput input) {
        return TickMotionDecision.idle(input.velocity(), false);
    }
}
