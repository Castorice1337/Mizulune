package shit.zen.modules.impl.movement.scaffold.v2.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class StrafeOnJumpTest {
    @Test
    void preservesLiquidBounceDefaults() {
        assertFalse(StrafeOnJump.DEFAULTS.enabled());
        assertEquals(0.48f, StrafeOnJump.DEFAULTS.straightSpeed().minimum());
        assertEquals(0.49f, StrafeOnJump.DEFAULTS.straightSpeed().maximum());
        assertEquals(0.48f, StrafeOnJump.DEFAULTS.diagonalSpeed().minimum());
        assertEquals(0.49f, StrafeOnJump.DEFAULTS.diagonalSpeed().maximum());
    }

    @Test
    void choosesStraightRangeForCardinalMovement() {
        StrafeOnJump.Settings settings = fixedSettings(0.48f, 0.49f);
        StrafeOnJump.Decision decision = StrafeOnJump.apply(
                input(new DirectionalInput(true, false, false, false)),
                settings,
                new Random(1L));

        assertTrue(decision.writesVelocity());
        assertTrue(decision.movingStraight());
        assertEquals(180.0f, decision.movingYaw());
        assertEquals(0.48f, decision.sampledSpeed());
        assertEquals(0.0, decision.velocity().x, 1.0e-12);
        assertEquals((double) 0.48f, decision.velocity().z, 1.0e-12);
    }

    @Test
    void choosesDiagonalRangeForDiagonalMovement() {
        StrafeOnJump.Settings settings = fixedSettings(0.48f, 0.49f);
        StrafeOnJump.Decision decision = StrafeOnJump.apply(
                input(new DirectionalInput(true, false, true, false)),
                settings,
                new Random(1L));

        double component = (double) 0.49f / Math.sqrt(2.0);
        assertFalse(decision.movingStraight());
        assertEquals(135.0f, decision.movingYaw());
        assertEquals(0.49f, decision.sampledSpeed());
        assertEquals(component, decision.velocity().x, 1.0e-8);
        assertEquals(component, decision.velocity().z, 1.0e-8);
    }

    @Test
    void disabledPolicyKeepsOriginalVelocity() {
        Vec3 velocity = new Vec3(1.0, 0.42, 2.0);
        StrafeOnJump.Decision decision = StrafeOnJump.apply(
                new StrafeOnJump.AfterJumpInput(
                        velocity,
                        DirectionalInput.NONE,
                        0.0f),
                StrafeOnJump.DEFAULTS,
                new Random(1L));

        assertFalse(decision.writesVelocity());
        assertSame(velocity, decision.velocity());
    }

    private static StrafeOnJump.Settings fixedSettings(float straight, float diagonal) {
        return new StrafeOnJump.Settings(
                true,
                new StrafeOnJump.SpeedRange(straight, straight),
                new StrafeOnJump.SpeedRange(diagonal, diagonal));
    }

    private static StrafeOnJump.AfterJumpInput input(DirectionalInput input) {
        return new StrafeOnJump.AfterJumpInput(new Vec3(1.0, 0.42, 2.0), input, 0.0f);
    }
}
