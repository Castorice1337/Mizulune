package shit.zen.modules.impl.movement.scaffold.v2.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class NoneTowerTest {
    @Test
    void noneKeepsMotionAndUsesDefaultBelowTarget() {
        NoneTower tower = new NoneTower();
        Vec3 velocity = new Vec3(0.1, 0.2, 0.3);
        TickMotionDecision decision = tower.tick(tick(velocity));
        BlockPos target = tower.targetedPosition(new Tower.TargetInput(
                new BlockPos(2, 5, 7),
                Vec3.ZERO,
                DirectionalInput.NONE,
                ignored -> false));

        assertEquals("None", tower.name());
        assertEquals(velocity, decision.velocity());
        assertFalse(decision.active());
        assertFalse(decision.velocityChanged());
        assertEquals(new BlockPos(2, 4, 7), target);
    }

    private static Tower.TickInput tick(Vec3 velocity) {
        return new Tower.TickInput(
                true, 64, true, false, 0, 0, 0.0, 64.0,
                velocity, DirectionalInput.NONE, 0.0f, 0.0);
    }
}
