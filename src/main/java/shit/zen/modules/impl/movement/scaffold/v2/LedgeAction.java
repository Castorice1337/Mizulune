/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldLedgeFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import shit.zen.utils.game.DirectionalInput;

public record LedgeAction(
        boolean jump,
        int sneakTicks,
        boolean stopInput,
        boolean stepBack) {
    public static final LedgeAction NO_LEDGE = new LedgeAction(false, 0, false, false);

    public DirectionalInput applyInput(DirectionalInput input) {
        DirectionalInput result = input == null ? DirectionalInput.NONE : input;
        if (this.stopInput) {
            result = DirectionalInput.NONE;
        }
        if (this.stepBack) {
            result = new DirectionalInput(false, true, result.left(), result.right());
        }
        return result;
    }

    public boolean applyJump(boolean currentJump) {
        return currentJump || this.jump;
    }
}
