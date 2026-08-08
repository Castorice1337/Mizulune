package shit.zen.modules.impl.movement.scaffold.v2.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;

final class NormalTechniqueTest {
    @Test
    void defaultsUseStabilizedNormalSearch() {
        NormalTechnique technique = new NormalTechnique();
        Technique.TargetOffset offset = technique.targetOffsets(new Technique.TargetInput(0.0f)).get(0);

        assertEquals(Technique.AimMode.STABILIZED, technique.settings().aimMode());
        assertEquals(false, technique.settings().requiresSight());
        assertEquals(BlockPos.ZERO, offset.offset());
        assertEquals(Technique.SearchOffsets.NORMAL, offset.searchOffsets());
        assertEquals(Technique.TargetPriority.LINE_OR_POSITION, offset.priority());
    }

    @Test
    void reverseResetRoundsYawAndSightRequirementCanRejectTarget() {
        NormalTechnique technique = new NormalTechnique();
        Rotation reverse = technique.rotation(input(
                target(new Rotation(10.0f, 70.0f)),
                67.0f,
                40.0f,
                true,
                Technique.AimResetMode.REVERSE,
                false));

        assertEquals(45.0f, reverse.yaw, 1.0e-6f);
        assertEquals(45.0f, reverse.pitch, 1.0e-6f);

        NormalTechnique sight = new NormalTechnique(new NormalTechnique.Settings(
                Technique.AimMode.STABILIZED,
                true));
        assertNull(sight.rotation(input(
                target(new Rotation(10.0f, 70.0f)),
                0.0f,
                0.0f,
                false,
                Technique.AimResetMode.RESET,
                false)));
    }

    private static Technique.RotationInput input(
            BlockPlacementTarget target,
            float yaw,
            float pitch,
            boolean doNotAim,
            Technique.AimResetMode resetMode,
            boolean visible) {
        return new Technique.RotationInput(
                target,
                Vec3.ZERO,
                yaw,
                pitch,
                DirectionalInput.NONE,
                true,
                0.0,
                0.0,
                false,
                false,
                doNotAim,
                resetMode,
                visible);
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
