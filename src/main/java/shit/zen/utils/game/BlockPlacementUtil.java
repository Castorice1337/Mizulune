package shit.zen.utils.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;

public final class BlockPlacementUtil extends ClientBase {
    private static final double HIT_EPSILON = 1.0E-4;

    public static BlockPlacementTarget findBestPlacementTarget(
            BlockPos placedBlock,
            ItemStack stack,
            BlockPlacementOptions options) {
        return findBestPlacementTarget(placedBlock, stack, options, null);
    }

    public static BlockPlacementTarget findBestPlacementTarget(
            BlockPos placedBlock,
            ItemStack stack,
            BlockPlacementOptions options,
            BlockHitResult requiredHit) {
        if (mc.player == null || mc.level == null || placedBlock == null) {
            return null;
        }

        BlockPlacementTarget bestTarget = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Direction facing : Direction.values()) {
            BlockPlacementTarget target = createPlacementTarget(placedBlock, facing, stack, options);
            if (target == null) {
                continue;
            }
            if (requiredHit != null && !doesHitMatchTarget(requiredHit, target, options)) {
                continue;
            }

            double score = scoreTarget(target, requiredHit);
            if (score < bestScore) {
                bestScore = score;
                bestTarget = target;
            }
        }
        return bestTarget;
    }

    public static BlockPlacementTarget createPlacementTarget(
            BlockPos placedBlock,
            Direction facing,
            ItemStack stack,
            BlockPlacementOptions options) {
        if (mc.player == null || mc.level == null || placedBlock == null || facing == null) {
            return null;
        }
        if (mc.level.isOutsideBuildHeight(placedBlock) || !canReplace(placedBlock, stack)) {
            return null;
        }

        BlockPos interactedBlock = placedBlock.relative(facing.getOpposite());
        if (mc.level.isOutsideBuildHeight(interactedBlock) || !isValidSupport(interactedBlock)) {
            return null;
        }

        BlockState state = mc.level.getBlockState(interactedBlock);
        FaceTarget faceTarget = getFaceTarget(interactedBlock, state, facing);
        if (faceTarget == null) {
            return null;
        }
        if (!options.considerFacingAwayFaces() && !isFaceVisibleToPlayer(faceTarget.point(), facing)) {
            return null;
        }

        Rotation rotation = getRotationToPoint(faceTarget.point());
        BlockPlacementTarget target = new BlockPlacementTarget(interactedBlock, placedBlock, facing,
                faceTarget.point(), faceTarget.minPlacementY(), rotation);
        return isValidPlacementTarget(target, stack, options) ? target : null;
    }

    public static boolean isValidPlacementTarget(
            BlockPlacementTarget target,
            ItemStack stack,
            BlockPlacementOptions options) {
        if (target == null || mc.player == null || mc.level == null) {
            return false;
        }
        if (mc.level.isOutsideBuildHeight(target.placedBlockPos())
                || !canReplace(target.placedBlockPos(), stack)
                || !isValidSupport(target.interactedBlockPos())) {
            return false;
        }
        return mc.player.getEyePosition().distanceToSqr(target.targetPoint()) <= options.maxRange() * options.maxRange();
    }

    public static boolean canReplace(BlockPos pos, ItemStack stack) {
        if (mc.player == null || mc.level == null || pos == null || mc.level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return true;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        BlockPlaceContext context = new BlockPlaceContext(
                mc.player,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return state.canBeReplaced(context);
    }

    public static boolean isValidSupport(BlockPos pos) {
        if (mc.level == null || pos == null || mc.level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir()
                && !state.canBeReplaced()
                && BlockUtil.isSolid(state)
                && !state.getCollisionShape(mc.level, pos).isEmpty();
    }

    public static BlockHitResult rayTraceTarget(
            Rotation rotation,
            BlockPlacementTarget target,
            BlockPlacementOptions options) {
        if (rotation == null || target == null || mc.player == null || mc.level == null) {
            return null;
        }

        HitResult result = rayTrace(rotation, options.maxRange());
        if (result instanceof BlockHitResult hit && doesHitMatchTarget(hit, target, options)) {
            return hit;
        }
        return options.constructFailResult() ? target.toHitResult() : null;
    }

    public static HitResult rayTrace(Rotation rotation, double range) {
        if (rotation == null || mc.player == null || mc.level == null) {
            return null;
        }
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookDir = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 endPos = eyePos.add(lookDir.scale(range));
        return mc.level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    public static boolean doesHitMatchTarget(
            BlockHitResult hit,
            BlockPlacementTarget target,
            BlockPlacementOptions options) {
        if (hit == null || target == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!hit.getBlockPos().equals(target.interactedBlockPos())) {
            return false;
        }
        if (options.requireDirectionMatch() && hit.getDirection() != target.facing()) {
            return false;
        }
        return hit.getLocation().y + HIT_EPSILON >= target.minPlacementY();
    }

    public static boolean place(
            BlockPlacementTarget target,
            InteractionHand hand,
            Rotation rotation,
            ItemStack stack,
            BlockPlacementOptions options) {
        if (target == null || hand == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            return false;
        }
        if (!isValidPlacementTarget(target, stack, options)) {
            return false;
        }
        BlockHitResult hit = rayTraceTarget(rotation, target, options);
        if (hit == null) {
            return false;
        }

        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
        if (result.consumesAction()) {
            mc.player.swing(hand);
            return true;
        }
        return result == InteractionResult.SUCCESS;
    }

    public static Rotation getRotationToPoint(Vec3 point) {
        if (mc.player == null || point == null) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition(1.0f);
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0f, 90.0f));
    }

    private static double scoreTarget(BlockPlacementTarget target, BlockHitResult requiredHit) {
        if (requiredHit != null) {
            return requiredHit.getLocation().distanceToSqr(target.targetPoint());
        }

        Rotation current = RotationHandler.targetRotation;
        if (current == null && mc.player != null) {
            current = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        }
        double distance = mc.player == null ? 0.0 : mc.player.getEyePosition().distanceToSqr(target.targetPoint());
        if (current == null || target.rotation() == null) {
            return distance;
        }
        return distance + Math.abs(Mth.wrapDegrees(current.getYaw() - target.rotation().getYaw())) * 0.05
                + Math.abs(current.getPitch() - target.rotation().getPitch()) * 0.05;
    }

    private static FaceTarget getFaceTarget(BlockPos interactedBlock, BlockState state, Direction facing) {
        AABB bestBox = null;
        double bestPlane = 0.0;
        for (AABB box : state.getShape(mc.level, interactedBlock).toAabbs()) {
            double plane = getFacePlane(box, facing);
            if (bestBox == null || isBetterFaceBox(box, plane, bestBox, bestPlane, facing)) {
                bestBox = box;
                bestPlane = plane;
            }
        }
        if (bestBox == null) {
            return null;
        }

        double x = interactedBlock.getX() + (bestBox.minX + bestBox.maxX) * 0.5;
        double y = interactedBlock.getY() + (bestBox.minY + bestBox.maxY) * 0.5;
        double z = interactedBlock.getZ() + (bestBox.minZ + bestBox.maxZ) * 0.5;
        double minPlacementY = interactedBlock.getY() + bestBox.minY;
        switch (facing) {
            case EAST -> x = interactedBlock.getX() + bestBox.maxX;
            case WEST -> x = interactedBlock.getX() + bestBox.minX;
            case SOUTH -> z = interactedBlock.getZ() + bestBox.maxZ;
            case NORTH -> z = interactedBlock.getZ() + bestBox.minZ;
            case UP -> {
                y = interactedBlock.getY() + bestBox.maxY;
                minPlacementY = y;
            }
            case DOWN -> {
                y = interactedBlock.getY() + bestBox.minY;
                minPlacementY = y;
            }
        }
        return new FaceTarget(new Vec3(x, y, z), minPlacementY);
    }

    private static double getFacePlane(AABB box, Direction facing) {
        return switch (facing) {
            case EAST -> box.maxX;
            case WEST -> box.minX;
            case SOUTH -> box.maxZ;
            case NORTH -> box.minZ;
            case UP -> box.maxY;
            case DOWN -> box.minY;
        };
    }

    private static boolean isBetterFaceBox(AABB box, double plane, AABB bestBox, double bestPlane, Direction facing) {
        double epsilon = 1.0E-5;
        boolean betterPlane = switch (facing) {
            case EAST, SOUTH, UP -> plane > bestPlane + epsilon;
            case WEST, NORTH, DOWN -> plane < bestPlane - epsilon;
        };
        if (betterPlane) {
            return true;
        }
        if (Math.abs(plane - bestPlane) <= epsilon) {
            return box.maxY > bestBox.maxY;
        }
        return false;
    }

    private static boolean isFaceVisibleToPlayer(Vec3 targetPoint, Direction facing) {
        if (mc.player == null || targetPoint == null || facing == null) {
            return false;
        }
        Vec3 delta = mc.player.getEyePosition(1.0f).subtract(targetPoint);
        return delta.length() > 1.0E-5 && delta.dot(Vec3.atLowerCornerOf(facing.getNormal())) >= 0.0;
    }

    private record FaceTarget(Vec3 point, double minPlacementY) {
    }

    private BlockPlacementUtil() {
    }
}
