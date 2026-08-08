package shit.zen.modules.impl.movement.scaffold.v2.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;

final class ExpandTechniqueTest {
    @Test
    void defaultLengthProducesExactOffsetsFromZeroThroughFour() {
        ExpandTechnique technique = new ExpandTechnique();
        List<Technique.TargetOffset> offsets = technique.targetOffsets(new Technique.TargetInput(0.0f));

        assertEquals(4, technique.settings().length());
        assertEquals(5, offsets.size());
        assertEquals(new BlockPos(0, 0, 0), offsets.get(0).offset());
        assertEquals(new BlockPos(0, 0, 4), offsets.get(4).offset());
        assertEquals(Technique.SearchOffsets.EXACT, offsets.get(4).searchOffsets());
        assertEquals(Technique.AimMode.CENTER, offsets.get(4).aimMode());
        assertEquals(true, offsets.get(4).considerFacingAwayFaces());
    }

    @Test
    void yawNinetyExpandsTowardNegativeXAndAimsAtPlacedBlockCenter() {
        ExpandTechnique technique = new ExpandTechnique();
        List<Technique.TargetOffset> offsets = technique.targetOffsets(new Technique.TargetInput(90.0f));
        assertEquals(new BlockPos(-4, 0, 0), offsets.get(4).offset());

        BlockPlacementTarget target = new BlockPlacementTarget(
                BlockPos.ZERO,
                new BlockPos(1, 2, 3),
                Direction.UP,
                Vec3.ZERO,
                0.0,
                new Rotation(0.0f, 0.0f));
        Technique.RotationInput input = new Technique.RotationInput(
                target,
                Vec3.ZERO,
                0.0f,
                0.0f,
                DirectionalInput.NONE,
                true,
                0.0,
                0.0,
                false,
                false,
                false,
                Technique.AimResetMode.RESET,
                true);

        Rotation expected = new Rotation(Vec3.ZERO, Vec3.atCenterOf(target.placedBlockPos()));
        Rotation actual = technique.rotation(input);
        assertEquals(expected.yaw, actual.yaw, 1.0e-6f);
        assertEquals(expected.pitch, actual.pitch, 1.0e-6f);
    }
}
