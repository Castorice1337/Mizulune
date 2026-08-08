package shit.zen.modules.impl.movement.scaffold.v2.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class HypixelTowerTest {
    private static final DirectionalInput FORWARD =
            new DirectionalInput(true, false, false, false);

    @Test
    void threeTickCycleAppliesStrafeAndFractionalHeightMotion() {
        HypixelTower tower = new HypixelTower();
        TickMotionDecision first = tower.tick(tick(0, 64.0, new Vec3(1.0, 0.0, 2.0), FORWARD));
        TickMotionDecision third = tower.tick(tick(2, 64.3, Vec3.ZERO, FORWARD));

        assertEquals(0.42, first.velocity().y, 1.0e-9);
        assertEquals(0.0, first.velocity().x, 1.0e-9);
        assertEquals(0.242, first.velocity().z, 1.0e-9);
        assertEquals(0.7, third.velocity().y, 1.0e-9);
    }

    @Test
    void longAirTimePullsDownAndScalesHorizontalMotion() {
        HypixelTower tower = new HypixelTower();
        TickMotionDecision decision = tower.tick(new Tower.TickInput(
                true, 64, true, false, 0, 15, 0.0, 64.0,
                new Vec3(1.0, 0.2, 2.0), FORWARD, 0.0f, 0.0));

        assertEquals(0.6, decision.velocity().x, 1.0e-9);
        assertEquals(0.11, decision.velocity().y, 1.0e-9);
        assertEquals(1.2, decision.velocity().z, 1.0e-9);
    }

    @Test
    void stationaryTargetChoosesClosestNonSolidNeighborBelow() {
        HypixelTower tower = new HypixelTower();
        BlockPos base = new BlockPos(0, 64, 0);
        BlockPos selected = tower.targetedPosition(new Tower.TargetInput(
                base,
                new Vec3(1.4, 64.0, 0.5),
                DirectionalInput.NONE,
                ignored -> false));
        BlockPos fallback = tower.targetedPosition(new Tower.TargetInput(
                base,
                new Vec3(1.4, 64.0, 0.5),
                DirectionalInput.NONE,
                ignored -> true));

        assertEquals(new BlockPos(1, 63, 0), selected);
        assertEquals(new BlockPos(0, 63, 0), fallback);
    }

    private static Tower.TickInput tick(
            int airTicks,
            double playerY,
            Vec3 velocity,
            DirectionalInput input) {
        return new Tower.TickInput(
                true, 64, true, false, 0, airTicks, 0.0, playerY,
                velocity, input, 0.0f, 0.5);
    }
}
