/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce movement-direction scaffold helpers.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.technique;

import shit.zen.utils.game.DirectionalInput;

final class TechniqueMath {
    private TechniqueMath() {
    }

    static float movementYaw(float facingYaw, DirectionalInput input) {
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

    static float roundToStep(float value, float step) {
        return (float) (Math.rint(value / step) * step);
    }

    static double unitSample(double sample) {
        if (!Double.isFinite(sample)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(Math.nextDown(1.0), sample));
    }

    static double sample(double min, double max, double sample) {
        return min + (max - min) * unitSample(sample);
    }

    static int sampleInclusive(int min, int max, double sample) {
        int count = max - min + 1;
        return min + Math.min(count - 1, (int) (unitSample(sample) * count));
    }
}
