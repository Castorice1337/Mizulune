package shit.zen.modules.impl.movement.scaffold.v2;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;

/**
 * Immutable Scaffold planning snapshot. Target selection, ray tracing and
 * placement must consume the same frame instead of mixing different motion
 * phases of one client tick.
 */
public record ScaffoldTickFrame(
        long frameId,
        int playerTick,
        Vec3 playerPosition,
        Vec3 eyePosition,
        Pose pose,
        DirectionalInput rawInput,
        ScaffoldMovementPlanner.MovementLine movementLine,
        ScaffoldMovementPrediction.Prediction prediction,
        ScaffoldTargetFinder.FindResult findResult,
        BlockPlacementTarget target,
        Rotation requestedRotation,
        InteractionHand hand,
        int hotbarSlot,
        ItemStack stack,
        int placementY) {

    public ScaffoldTickFrame {
        stack = stack == null ? null : stack.copy();
        requestedRotation = requestedRotation == null ? null : requestedRotation.clone();
        target = copyTarget(target);
        if (findResult != null) {
            findResult = new ScaffoldTargetFinder.FindResult(
                    target,
                    findResult.targetedPosition(),
                    findResult.selectedOffset(),
                    findResult.planningEye(),
                    findResult.positionsChecked(),
                    findResult.facesChecked(),
                    findResult.source());
        }
    }

    public boolean isCurrent(int tick) {
        return this.playerTick == tick;
    }

    static BlockPlacementTarget copyTarget(BlockPlacementTarget target) {
        if (target == null) {
            return null;
        }
        return new BlockPlacementTarget(
                target.interactedBlockPos(),
                target.placedBlockPos(),
                target.facing(),
                target.targetPoint(),
                target.minPlacementY(),
                target.rotation() == null ? null : target.rotation().clone());
    }
}
