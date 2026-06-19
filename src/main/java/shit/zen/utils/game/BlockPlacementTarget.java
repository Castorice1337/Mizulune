package shit.zen.utils.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.rotation.Rotation;

public record BlockPlacementTarget(
        BlockPos interactedBlockPos,
        BlockPos placedBlockPos,
        Direction facing,
        Vec3 targetPoint,
        double minPlacementY,
        Rotation rotation) {

    public BlockHitResult toHitResult() {
        return new BlockHitResult(Vec3.atCenterOf(this.interactedBlockPos), this.facing, this.interactedBlockPos, false);
    }
}
