package shit.zen.modules.impl.movement.scaffold.v2.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;

final class BreezilyTechniqueTest {
    private static final DirectionalInput FORWARD =
            new DirectionalInput(true, false, false, false);

    @Test
    void defaultEdgeRangeAndRecentAirWindowDriveSidewaysInput() {
        BreezilyTechnique technique = new BreezilyTechnique();
        DirectionalInput first = technique.adjustInput(new Technique.MovementInput(
                FORWARD,
                false,
                true,
                1_000L,
                0.9,
                0.5,
                0.0f,
                1.0));
        DirectionalInput continued = technique.adjustInput(new Technique.MovementInput(
                FORWARD,
                false,
                false,
                1_400L,
                0.5,
                0.5,
                0.0f,
                0.0));
        DirectionalInput expired = technique.adjustInput(new Technique.MovementInput(
                FORWARD,
                false,
                false,
                1_501L,
                0.5,
                0.5,
                0.0f,
                0.0));

        assertEquals(0.45, technique.settings().edgeDistanceMin(), 1.0e-9);
        assertEquals(0.5, technique.settings().edgeDistanceMax(), 1.0e-9);
        assertTrue(first.right());
        assertTrue(continued.right());
        assertFalse(expired.right());
        assertEquals(0.5, technique.currentEdgeDistance(), 1.0e-9);
    }

    @Test
    void rotationsUseStraightDiagonalAndNoInputPitches() {
        BreezilyTechnique technique = new BreezilyTechnique();
        Rotation straight = technique.rotation(rotationInput(FORWARD, new Rotation(100.0f, 0.0f)));
        Rotation diagonal = technique.rotation(rotationInput(
                new DirectionalInput(true, false, false, true),
                new Rotation(100.0f, 0.0f)));
        Rotation noInput = technique.rotation(rotationInput(
                DirectionalInput.NONE,
                new Rotation(100.0f, 0.0f)));

        assertEquals(180.0f, straight.yaw, 1.0e-6f);
        assertEquals(80.0f, straight.pitch, 1.0e-6f);
        assertEquals(225.0f, diagonal.yaw, 1.0e-6f);
        assertEquals(75.6f, diagonal.pitch, 1.0e-6f);
        assertEquals(135.0f, noInput.yaw, 1.0e-6f);
        assertEquals(75.0f, noInput.pitch, 1.0e-6f);
    }

    private static Technique.RotationInput rotationInput(
            DirectionalInput input,
            Rotation targetRotation) {
        BlockPlacementTarget target = new BlockPlacementTarget(
                BlockPos.ZERO,
                BlockPos.ZERO.above(),
                Direction.UP,
                Vec3.ZERO,
                0.0,
                targetRotation);
        return new Technique.RotationInput(
                target,
                Vec3.ZERO,
                0.0f,
                0.0f,
                input,
                false,
                0.0,
                0.0,
                false,
                false,
                false,
                Technique.AimResetMode.RESET,
                true);
    }
}
