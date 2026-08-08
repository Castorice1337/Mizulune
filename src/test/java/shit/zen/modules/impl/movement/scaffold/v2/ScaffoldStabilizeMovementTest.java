package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class ScaffoldStabilizeMovementTest {
    private final ScaffoldStabilizeMovement stabilizer = new ScaffoldStabilizeMovement();
    private final ScaffoldMovementPlanner.MovementLine line =
            new ScaffoldMovementPlanner.MovementLine(
                    new ScaffoldGeometry.Line(Vec3.ZERO, new Vec3(0.0, 0.0, 1.0)),
                    BlockPos.ZERO);

    @Test
    void addsOnlyTheUnoccupiedInputAxis() {
        DirectionalInput result = this.stabilizer.stabilize(
                new DirectionalInput(true, false, false, false),
                false,
                true,
                this.line,
                new Vec3(0.3, 0.0, 0.0),
                Vec3.ZERO,
                0.0f);

        assertEquals(new DirectionalInput(true, false, false, true), result);
    }

    @Test
    void doesNotStabilizeImmediatelyBeforeGroundJump() {
        DirectionalInput input = new DirectionalInput(true, false, false, false);

        assertEquals(input, this.stabilizer.stabilize(
                input,
                true,
                true,
                this.line,
                new Vec3(0.3, 0.0, 0.0),
                Vec3.ZERO,
                0.0f));
    }

    @Test
    void usesTighterThresholdWhileMovingTowardsLine() {
        DirectionalInput input = DirectionalInput.NONE;

        assertEquals(input, this.stabilizer.stabilize(
                input,
                false,
                false,
                this.line,
                new Vec3(0.1, 0.0, 0.0),
                new Vec3(0.1, 0.0, 0.0),
                0.0f));
        assertEquals(new DirectionalInput(true, false, false, true), this.stabilizer.stabilize(
                input,
                false,
                false,
                this.line,
                new Vec3(0.1, 0.0, 0.0),
                new Vec3(-0.1, 0.0, 0.0),
                0.0f));
    }
}
