package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import shit.zen.value.NumericRange;

final class ScaffoldTellyPolicyTest {
    @Test
    void groundTicksAdvanceOnlyWhileGrounded() {
        ScaffoldTellyPolicy policy = new ScaffoldTellyPolicy();
        policy.reset(new NumericRange(1, 1, 0, 10, 1, true));
        policy.onGameTick(true);
        policy.onGameTick(false);

        assertEquals(1, policy.ticksUntilJump());
        assertEquals(1, policy.jumpTicks());
    }

    @Test
    void actualJumpResetsGroundCycleAndResamplesFixedRange() {
        ScaffoldTellyPolicy policy = new ScaffoldTellyPolicy();
        NumericRange initial = new NumericRange(1, 1, 0, 10, 1, true);
        NumericRange afterJump = new NumericRange(3, 3, 0, 10, 1, true);
        policy.reset(initial);
        policy.onGameTick(true);
        policy.onAfterJump(afterJump);

        assertEquals(0, policy.ticksUntilJump());
        assertEquals(3, policy.jumpTicks());
    }
}
