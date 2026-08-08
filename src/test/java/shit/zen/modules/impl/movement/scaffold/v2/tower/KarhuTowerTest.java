package shit.zen.modules.impl.movement.scaffold.v2.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class KarhuTowerTest {
    @Test
    void firstAirTickRequestsTimerThenPulldownSubtractsOne() {
        KarhuTower tower = new KarhuTower();
        tower.onJump(new Tower.JumpInput(0.42f, false, 64.0));

        TickMotionDecision grounded = tower.tick(tick(true, true, new Vec3(0.0, 0.42, 0.0)));
        TickMotionDecision airborne = tower.tick(tick(false, true, new Vec3(0.0, 0.2, 0.0)));
        TickMotionDecision pulled = tower.tick(tick(false, true, new Vec3(0.0, 0.05, 0.0)));

        assertEquals(5.0, tower.settings().timerSpeed(), 1.0e-9);
        assertEquals(0.06, tower.settings().triggerMotion(), 1.0e-9);
        assertTrue(tower.settings().pulldown());
        assertFalse(grounded.hasTimerRequest());
        assertTrue(airborne.hasTimerRequest());
        assertEquals(5.0f, airborne.timerSpeed(), 1.0e-6f);
        assertEquals(new Vec3(0.0, -0.95, 0.0), pulled.velocity());
        assertEquals(KarhuTower.Phase.IDLE, tower.phase());
    }

    private static Tower.TickInput tick(boolean onGround, boolean blockBelow, Vec3 velocity) {
        return new Tower.TickInput(
                true, 64, blockBelow, onGround, 0, 0, 0.0, 64.0,
                velocity, DirectionalInput.NONE, 0.0f, 0.0);
    }
}
