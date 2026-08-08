package shit.zen.modules.impl.movement.scaffold.v2.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class PulldownTowerTest {
    @Test
    void validJumpWaitsForTriggerThenSetsVerticalMotionToMinusOne() {
        PulldownTower tower = new PulldownTower();
        tower.onJump(new Tower.JumpInput(0.42f, false, 64.0));

        TickMotionDecision waiting = tower.tick(tick(false, true, new Vec3(0.1, 0.1, 0.2)));
        TickMotionDecision pulled = tower.tick(tick(false, true, new Vec3(0.1, 0.09, 0.2)));

        assertEquals(0.1, tower.settings().triggerMotion(), 1.0e-9);
        assertFalse(waiting.velocityChanged());
        assertTrue(pulled.velocityChanged());
        assertEquals(new Vec3(0.1, -1.0, 0.2), pulled.velocity());
        assertFalse(tower.isArmed());
    }

    @Test
    void cancelledJumpDoesNotArmStrategy() {
        PulldownTower tower = new PulldownTower();
        tower.onJump(new Tower.JumpInput(0.42f, true, 64.0));
        assertFalse(tower.isArmed());
    }

    private static Tower.TickInput tick(boolean onGround, boolean blockBelow, Vec3 velocity) {
        return new Tower.TickInput(
                true, 64, blockBelow, onGround, 0, 0, 0.0, 64.0,
                velocity, DirectionalInput.NONE, 0.0f, 0.0);
    }
}
