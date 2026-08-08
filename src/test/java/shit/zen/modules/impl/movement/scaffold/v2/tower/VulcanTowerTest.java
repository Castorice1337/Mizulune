package shit.zen.modules.impl.movement.scaffold.v2.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class VulcanTowerTest {
    @Test
    void evenStationaryTickUsesPointSevenAndReturnsPacketOffsetAsData() {
        VulcanTower tower = new VulcanTower();
        TickMotionDecision decision = tower.tick(tick(2, DirectionalInput.NONE));

        assertEquals(0.7, decision.velocity().y, 1.0e-9);
        assertEquals(new Vec3(0.1, 0.0, 0.1), decision.outgoingMoveOffset());
        assertTrue(decision.hasOutgoingMoveOffset());
        assertFalse(decision.awardJumpStat());
    }

    @Test
    void oddMovingTickUsesVanillaJumpMotionAndAwardsStat() {
        VulcanTower tower = new VulcanTower();
        TickMotionDecision decision = tower.tick(tick(
                3,
                new DirectionalInput(true, false, false, false)));

        assertEquals(0.42, decision.velocity().y, 1.0e-9);
        assertEquals(Vec3.ZERO, decision.outgoingMoveOffset());
        assertTrue(decision.awardJumpStat());
    }

    private static Tower.TickInput tick(int tickCount, DirectionalInput input) {
        return new Tower.TickInput(
                true, 64, true, false, tickCount, 0, 0.0, 64.0,
                new Vec3(0.2, 0.0, 0.3), input, 0.0f, 0.0);
    }
}
