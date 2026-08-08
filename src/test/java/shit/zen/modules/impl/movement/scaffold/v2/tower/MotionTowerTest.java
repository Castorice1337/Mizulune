package shit.zen.modules.impl.movement.scaffold.v2.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class MotionTowerTest {
    @Test
    void jumpHeightTriggerSnapsYAndAppliesDefaultMotion() {
        MotionTower tower = new MotionTower();
        tower.onJump(new Tower.JumpInput(0.42f, false, 64.0));

        TickMotionDecision decision = tower.tick(tick(true, 64.79, new Vec3(0.2, 0.1, -0.3)));

        assertEquals(0.42, tower.settings().motion(), 1.0e-9);
        assertEquals(0.78, tower.settings().triggerHeight(), 1.0e-9);
        assertEquals(1.0, tower.settings().slow(), 1.0e-9);
        assertTrue(decision.active());
        assertTrue(decision.hasPositionSnap());
        assertEquals(64.0, decision.snappedY(), 1.0e-9);
        assertEquals(new Vec3(0.2, 0.42, -0.3), decision.velocity());
        assertTrue(decision.awardJumpStat());
        assertEquals(64.0, tower.jumpOffPosition(), 1.0e-9);
    }

    @Test
    void inactiveTickClearsJumpOffPosition() {
        MotionTower tower = new MotionTower();
        tower.onJump(new Tower.JumpInput(0.42f, false, 64.0));
        tower.tick(tick(false, 64.5, Vec3.ZERO));
        assertTrue(Double.isNaN(tower.jumpOffPosition()));
    }

    private static Tower.TickInput tick(boolean active, double y, Vec3 velocity) {
        return new Tower.TickInput(
                active, active ? 64 : 0, active, false, 0, 0, 0.0, y,
                velocity, DirectionalInput.NONE, 0.0f, 0.0);
    }
}
