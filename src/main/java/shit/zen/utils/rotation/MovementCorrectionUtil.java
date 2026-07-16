/*
 * This file includes behavior adapted from LiquidBounce:
 * net.ccbluex.liquidbounce.injection.mixins.minecraft.client.MixinKeyboardInput
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified in 2026 for Mizulune/OpenZen's Java/Forge 1.20.1 input events.
 */
package shit.zen.utils.rotation;

import net.minecraft.util.Mth;
import shit.zen.utils.game.DirectionalInput;

public final class MovementCorrectionUtil {
    private MovementCorrectionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static DirectionalInput correctSilentInput(
            DirectionalInput input,
            float playerYaw,
            float rotationYaw) {
        if (input == null || !input.isMoving()) {
            return input == null ? DirectionalInput.NONE : input;
        }

        float z = input.forwardImpulse();
        float x = input.strafeImpulse();
        float deltaYaw = playerYaw - rotationYaw;
        float radians = deltaYaw * Mth.DEG_TO_RAD;
        float newX = x * Mth.cos(radians) - z * Mth.sin(radians);
        float newZ = z * Mth.cos(radians) + x * Mth.sin(radians);
        return DirectionalInput.fromImpulses(Math.round(newZ), Math.round(newX));
    }
}
