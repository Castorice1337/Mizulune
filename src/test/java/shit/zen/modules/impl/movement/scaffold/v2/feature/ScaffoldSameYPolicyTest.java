package shit.zen.modules.impl.movement.scaffold.v2.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ScaffoldSameYPolicyTest {
    @Test
    void defaultsAndInitialStateMatchLiquidSource() {
        ScaffoldSameYPolicy policy = new ScaffoldSameYPolicy();

        assertEquals(ScaffoldSameYPolicy.Mode.OFF, ScaffoldSameYPolicy.DEFAULT_MODE);
        assertThrows(IllegalStateException.class,
                () -> policy.resolve(70, ScaffoldSameYPolicy.Mode.OFF, 0.0));

        policy.reset(70);
        assertEquals(new ScaffoldSameYPolicy.State(69, 70, 2), policy.state());
    }

    @Test
    void offUsesBlockBelowWhileOnUsesCapturedPlacementY() {
        ScaffoldSameYPolicy policy = new ScaffoldSameYPolicy();
        policy.reset(70);

        assertEquals(79, policy.resolve(80, ScaffoldSameYPolicy.Mode.OFF, 0.0).targetY());
        assertEquals(69, policy.resolve(80, ScaffoldSameYPolicy.Mode.ON, 0.0).targetY());
        assertEquals(
                new BlockPos(3, 69, -4),
                policy.resolvePosition(new BlockPos(3, 80, -4), ScaffoldSameYPolicy.Mode.ON, 0.0));
    }

    @Test
    void groundAndJumpKeyUpdatesFollowLiquidSourceOrder() {
        ScaffoldSameYPolicy policy = new ScaffoldSameYPolicy();
        policy.reset(70);
        policy.onTick(72, true, true);

        assertEquals(new ScaffoldSameYPolicy.State(71, 72, 2), policy.state());
    }

    @Test
    void fallingModeUsesStrictLessThanPointTwoBoundary() {
        ScaffoldSameYPolicy policy = new ScaffoldSameYPolicy();
        policy.reset(70);

        ScaffoldSameYPolicy.Decision belowBoundary =
                policy.resolve(80, ScaffoldSameYPolicy.Mode.FALLING, Math.nextDown(0.2));
        ScaffoldSameYPolicy.Decision atBoundary =
                policy.resolve(80, ScaffoldSameYPolicy.Mode.FALLING, 0.2);

        assertEquals(69, belowBoundary.targetY());
        assertTrue(belowBoundary.sameYApplied());
        assertEquals(79, atBoundary.targetY());
        assertFalse(atBoundary.sameYApplied());
    }

    @Test
    void hypixelRequiresExactVelocityAndTwoJumpsThenResetsCounter() {
        ScaffoldSameYPolicy policy = new ScaffoldSameYPolicy();
        policy.reset(70);

        ScaffoldSameYPolicy.Decision nearMiss = policy.resolve(
                80,
                ScaffoldSameYPolicy.Mode.HYPIXEL,
                Math.nextUp(ScaffoldSameYPolicy.HYPIXEL_TRIGGER_VELOCITY));
        ScaffoldSameYPolicy.Decision triggered = policy.resolve(
                80,
                ScaffoldSameYPolicy.Mode.HYPIXEL,
                ScaffoldSameYPolicy.HYPIXEL_TRIGGER_VELOCITY);
        ScaffoldSameYPolicy.Decision afterReset = policy.resolve(
                80,
                ScaffoldSameYPolicy.Mode.HYPIXEL,
                ScaffoldSameYPolicy.HYPIXEL_TRIGGER_VELOCITY);

        assertEquals(69, nearMiss.targetY());
        assertFalse(nearMiss.hypixelTriggered());
        assertEquals(70, triggered.targetY());
        assertTrue(triggered.hypixelTriggered());
        assertEquals(0, policy.state().jumps());
        assertEquals(69, afterReset.targetY());
        assertFalse(afterReset.hypixelTriggered());
    }
}
