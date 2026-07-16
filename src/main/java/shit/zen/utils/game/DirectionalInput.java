/*
 * This file includes behavior adapted from LiquidBounce:
 * net.ccbluex.liquidbounce.utils.movement.DirectionalInput
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified in 2026 for Mizulune/OpenZen's Java/Forge 1.20.1 input events.
 */
package shit.zen.utils.game;

public record DirectionalInput(
        boolean forwards,
        boolean backwards,
        boolean left,
        boolean right) {
    public static final DirectionalInput NONE = new DirectionalInput(false, false, false, false);

    public static DirectionalInput fromImpulses(float forward, float strafe) {
        return new DirectionalInput(
                forward > 0.0f,
                forward < 0.0f,
                strafe > 0.0f,
                strafe < 0.0f);
    }

    public float forwardImpulse() {
        if (this.forwards == this.backwards) {
            return 0.0f;
        }
        return this.forwards ? 1.0f : -1.0f;
    }

    public float strafeImpulse() {
        if (this.left == this.right) {
            return 0.0f;
        }
        return this.left ? 1.0f : -1.0f;
    }

    public boolean isMoving() {
        return this.forwardImpulse() != 0.0f || this.strafeImpulse() != 0.0f;
    }
}
