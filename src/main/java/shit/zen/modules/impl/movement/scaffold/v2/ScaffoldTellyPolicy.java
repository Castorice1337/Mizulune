/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTellyFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 input events.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import java.util.concurrent.ThreadLocalRandom;
import shit.zen.value.NumericRange;

public final class ScaffoldTellyPolicy {
    private int ticksUntilJump;
    private int jumpTicks;

    public void reset(NumericRange jumpRange) {
        this.ticksUntilJump = 0;
        this.jumpTicks = randomJumpTicks(jumpRange);
    }

    public void onGameTick(boolean onGround) {
        if (onGround) {
            this.ticksUntilJump++;
        }
    }

    public void onAfterJump(NumericRange jumpRange) {
        this.ticksUntilJump = 0;
        this.jumpTicks = randomJumpTicks(jumpRange);
    }

    public int ticksUntilJump() {
        return this.ticksUntilJump;
    }

    public int jumpTicks() {
        return this.jumpTicks;
    }

    static int randomJumpTicks(NumericRange jumpRange) {
        if (jumpRange == null) {
            return 0;
        }
        int lower = (int) Math.round(jumpRange.lower());
        int upper = (int) Math.round(jumpRange.upper());
        if (upper <= lower) {
            return lower;
        }
        return ThreadLocalRandom.current().nextInt(lower, upper + 1);
    }
}
