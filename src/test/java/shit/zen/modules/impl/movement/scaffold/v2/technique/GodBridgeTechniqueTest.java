package shit.zen.modules.impl.movement.scaffold.v2.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.modules.impl.movement.scaffold.v2.LedgeAction;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;

final class GodBridgeTechniqueTest {
    private static final DirectionalInput FORWARD =
            new DirectionalInput(true, false, false, false);

    @Test
    void defaultsMatchLiquidBounceAndStraightRotationTracksSide() {
        GodBridgeTechnique technique = new GodBridgeTechnique();
        Rotation rotation = technique.rotation(rotationInput(FORWARD, true, 0.2, 0.2, false, false));

        assertEquals(3, technique.settings().forceSneakBelowCount());
        assertEquals(1, technique.settings().sneakTicksMin());
        assertEquals(1, technique.settings().sneakTicksMax());
        assertEquals(java.util.Set.of(GodBridgeTechnique.LedgeMode.JUMP), technique.settings().modes());
        assertTrue(technique.isOnRightSide());
        assertEquals(225.0f, rotation.yaw, 1.0e-6f);
        assertEquals(75.7f, rotation.pitch, 1.0e-6f);
    }

    @Test
    void diagonalAndNoInputRotationsUseLiquidBounceAngles() {
        GodBridgeTechnique technique = new GodBridgeTechnique();
        Rotation diagonal = technique.rotation(rotationInput(
                new DirectionalInput(true, false, false, true),
                false,
                0.0,
                0.0,
                false,
                false));
        assertEquals(225.0f, diagonal.yaw, 1.0e-6f);
        assertEquals(75.6f, diagonal.pitch, 1.0e-6f);

        Rotation noInput = technique.rotation(rotationInput(
                DirectionalInput.NONE,
                false,
                0.0,
                0.0,
                false,
                false));
        assertEquals(-45.0f, noInput.yaw, 1.0e-6f);
        assertEquals(75.0f, noInput.pitch, 1.0e-6f);
    }

    @Test
    void ledgeForcesSneakBelowThreeBlocksThenUsesDefaultJumpMode() {
        GodBridgeTechnique technique = new GodBridgeTechnique();
        LedgeAction forcedSneak = technique.ledgeAction(ledgeInput(2, false, false));
        LedgeAction jump = technique.ledgeAction(ledgeInput(3, false, false));
        LedgeAction safe = technique.ledgeAction(ledgeInput(3, true, true));

        assertEquals(new LedgeAction(false, 1, false, false), forcedSneak);
        assertEquals(new LedgeAction(true, 0, false, false), jump);
        assertEquals(LedgeAction.NO_LEDGE, safe);
        assertFalse(safe.jump());
    }

    private static Technique.RotationInput rotationInput(
            DirectionalInput input,
            boolean onGround,
            double x,
            double z,
            boolean leaning,
            boolean nextAir) {
        return new Technique.RotationInput(
                target(new Rotation(-10.0f, 70.0f)),
                Vec3.ZERO,
                0.0f,
                0.0f,
                input,
                onGround,
                x,
                z,
                leaning,
                nextAir,
                false,
                Technique.AimResetMode.RESET,
                true);
    }

    private static Technique.LedgeInput ledgeInput(int blockCount, boolean matches, boolean valid) {
        return new Technique.LedgeInput(
                true,
                true,
                target(new Rotation(0.0f, 0.0f)),
                matches,
                valid,
                blockCount,
                0.0,
                0.0);
    }

    private static BlockPlacementTarget target(Rotation rotation) {
        return new BlockPlacementTarget(
                BlockPos.ZERO,
                BlockPos.ZERO.above(),
                Direction.UP,
                Vec3.ZERO,
                0.0,
                rotation);
    }
}
