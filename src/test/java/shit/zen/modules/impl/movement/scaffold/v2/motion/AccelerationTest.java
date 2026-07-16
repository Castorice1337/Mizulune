package shit.zen.modules.impl.movement.scaffold.v2.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class AccelerationTest {
    @Test
    void preservesLiquidBounceDefaults() {
        assertFalse(Acceleration.DEFAULTS.enabled());
        assertEquals(0.6f, Acceleration.DEFAULTS.speedMultiplier());
        assertFalse(Acceleration.DEFAULTS.onlyOnGround());
    }

    @Test
    void scalesOnlyHorizontalVelocity() {
        Vec3 result = Acceleration.apply(
                new Vec3(2.0, 0.42, -3.0),
                false,
                new Acceleration.Settings(true, 0.6f, false));

        assertEquals(2.0 * (double) 0.6f, result.x, 1.0e-12);
        assertEquals(0.42, result.y, 1.0e-12);
        assertEquals(-3.0 * (double) 0.6f, result.z, 1.0e-12);
    }

    @Test
    void disabledAndAirOnlyOnGroundPathsKeepOriginalVelocity() {
        Vec3 velocity = new Vec3(1.0, 2.0, 3.0);

        assertSame(velocity, Acceleration.apply(velocity, true, Acceleration.DEFAULTS));
        assertSame(velocity, Acceleration.apply(
                velocity,
                false,
                new Acceleration.Settings(true, 0.6f, true)));
    }
}
