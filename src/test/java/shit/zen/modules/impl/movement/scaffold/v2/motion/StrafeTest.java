package shit.zen.modules.impl.movement.scaffold.v2.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class StrafeTest {
    private static final DirectionalInput FORWARD =
            new DirectionalInput(true, false, false, false);

    @Test
    void preservesLiquidBounceDefaults() {
        assertFalse(Strafe.DEFAULTS.enabled());
        assertEquals(0.247f, Strafe.DEFAULTS.speed());
        assertFalse(Strafe.DEFAULTS.hypixel());
        assertFalse(Strafe.DEFAULTS.onlyOnGround());
    }

    @Test
    void nonHypixelBranchKeepsTheUnassignedWithStrafeResult() {
        Strafe strafe = new Strafe();
        Vec3 original = new Vec3(1.0, 0.2, 2.0);
        Strafe.Decision decision = strafe.tick(
                tick(original, FORWARD, true, true, 1, -1),
                new Strafe.Settings(true, 0.247f, false, false));

        assertFalse(decision.writesVelocity());
        assertSame(original, decision.velocity());
        assertEquals((double) 0.247f, decision.requestedSpeed(), 0.0);
    }

    @Test
    void hypixelUsesLowSpeedForFirstSevenMoveTicksThenBaseSpeed() {
        Strafe strafe = new Strafe();
        Strafe.Settings settings = new Strafe.Settings(true, 0.247f, true, false);
        Strafe.Decision decision = null;

        for (int tick = 1; tick <= 8; tick++) {
            decision = strafe.tick(tick(Vec3.ZERO, FORWARD, true, true, tick, -1), settings);
            if (tick <= 7) {
                assertEquals(Strafe.HYPIXEL_LOW_SPEED, decision.requestedSpeed(), 0.0);
            }
        }

        assertEquals(Strafe.HYPIXEL_BASE_SPEED, decision.requestedSpeed(), 0.0);
        assertEquals(8, decision.moveTicks());
        assertEquals(Strafe.HYPIXEL_BASE_SPEED, decision.velocity().z, 1.0e-12);
    }

    @Test
    void hypixelPotionAndPeriodicSlowdownMatchSourceOrder() {
        Strafe strafe = new Strafe();
        Strafe.Settings settings = new Strafe.Settings(true, 0.247f, true, false);
        Strafe.Decision decision = null;

        for (int tick = 1; tick <= 8; tick++) {
            decision = strafe.tick(tick(Vec3.ZERO, FORWARD, true, true, tick, 0), settings);
        }
        assertEquals(Strafe.HYPIXEL_SPEED_EFFECT_SPEED, decision.requestedSpeed(), 0.0);

        decision = strafe.tick(tick(Vec3.ZERO, FORWARD, true, true, 20, 0), settings);
        assertEquals(Strafe.HYPIXEL_LOW_SPEED, decision.requestedSpeed(), 0.0);
    }

    @Test
    void movementCounterUpdatesBeforeGroundGateAndHypixelDisableHalvesHorizontalMotion() {
        Strafe strafe = new Strafe();
        Strafe.Settings settings = new Strafe.Settings(true, 0.247f, true, true);
        Vec3 original = new Vec3(2.0, 0.3, -4.0);

        Strafe.Decision airDecision = strafe.tick(
                tick(original, FORWARD, true, false, 1, -1),
                settings);
        Strafe.Decision disabled = strafe.onDisabled(original, settings);

        assertFalse(airDecision.writesVelocity());
        assertEquals(1, airDecision.moveTicks());
        assertTrue(disabled.writesVelocity());
        assertEquals(1.0, disabled.velocity().x, 1.0e-12);
        assertEquals(0.3, disabled.velocity().y, 1.0e-12);
        assertEquals(-2.0, disabled.velocity().z, 1.0e-12);
    }

    private static Strafe.TickInput tick(
            Vec3 velocity,
            DirectionalInput input,
            boolean moving,
            boolean onGround,
            int playerTick,
            int speedEffectAmplifier) {
        return new Strafe.TickInput(
                velocity,
                input,
                0.0f,
                moving,
                onGround,
                playerTick,
                speedEffectAmplifier);
    }
}
