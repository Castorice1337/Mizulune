/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce TargetFinding, BlockPosOffsets and
 * ScaffoldNormalTechnique:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 scaffold pipeline.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import shit.zen.ClientBase;
import shit.zen.modules.impl.movement.scaffold.v2.technique.Technique;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;

public final class ScaffoldTargetFinder extends ClientBase {
    private static final List<BlockPos> NORMAL_OFFSETS = generateNormalOffsets();
    private static final List<BlockPos> DOWN_OFFSETS = generateOffsets(0, -1, 1, -2, 2);
    private static final List<BlockPos> CARDINAL_OFFSETS = List.of(
            new BlockPos(-1, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(0, 0, -1),
            new BlockPos(0, 0, 1));
    private static final List<BlockPos> EXACT_OFFSETS = List.of(BlockPos.ZERO);
    private static final Technique.TargetOffset DEFAULT_NORMAL_TARGET_OFFSET = new Technique.TargetOffset(
            BlockPos.ZERO,
            Technique.SearchOffsets.NORMAL,
            Technique.TargetPriority.LINE_OR_POSITION,
            Technique.AimMode.STABILIZED,
            false);
    private static final List<Technique.TargetOffset> DEFAULT_NORMAL_TARGET_OFFSETS =
            List.of(DEFAULT_NORMAL_TARGET_OFFSET);

    public FindResult find(
            Vec3 predictedPosition,
            Pose predictedPose,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            ItemStack stack) {
        return this.find(
                predictedPosition,
                predictedPose,
                optimalLine,
                stack,
                resolveTargetedPosition(predictedPosition, 0, false),
                DEFAULT_NORMAL_TARGET_OFFSETS);
    }

    public FindResult find(
            Vec3 predictedPosition,
            Pose predictedPose,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            ItemStack stack,
            BlockPos targetedPosition) {
        return this.find(
                predictedPosition,
                predictedPose,
                optimalLine,
                stack,
                targetedPosition,
                DEFAULT_NORMAL_TARGET_OFFSETS);
    }

    public FindResult find(
            Vec3 predictedPosition,
            Pose predictedPose,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            ItemStack stack,
            List<Technique.TargetOffset> targetOffsets) {
        return this.find(
                predictedPosition,
                predictedPose,
                optimalLine,
                stack,
                resolveTargetedPosition(predictedPosition, 0, false),
                targetOffsets);
    }

    public FindResult find(
            Vec3 predictedPosition,
            Pose predictedPose,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            ItemStack stack,
            BlockPos targetedPosition,
            List<Technique.TargetOffset> targetOffsets) {
        if (mc.player == null || mc.level == null || predictedPosition == null
                || predictedPose == null || stack == null || stack.isEmpty()
                || targetedPosition == null || targetOffsets == null
                || targetOffsets.stream().anyMatch(offset -> offset == null)) {
            return FindResult.none("missing-context");
        }

        if (isDefaultNormalRequest(targetOffsets)
                && isBlockSolid(mc.level.getBlockState(targetedPosition), targetedPosition)) {
            return FindResult.none("base-solid");
        }

        Vec3 planningEye = predictedPosition.add(
                0.0,
                mc.player.getEyeHeight(predictedPose),
                0.0);
        Rotation serverRotation = RotationHandler.getActualServerRotation() != null
                ? RotationHandler.getActualServerRotation()
                : new Rotation(mc.player.getYRot(), mc.player.getXRot());

        int positionsChecked = 0;
        int facesChecked = 0;
        for (Technique.TargetOffset targetOffset : targetOffsets) {
            BlockPos targetRoot = targetedPosition.offset(targetOffset.offset());
            BlockState rootState = mc.level.getBlockState(targetRoot);
            if (isBlockSolid(rootState, targetRoot)) {
                continue;
            }

            ScaffoldFacePointFactory facePointFactory = new ScaffoldFacePointFactory(
                    planningEye,
                    optimalLine,
                    serverRotation,
                    targetOffset.aimMode());
            List<BlockPos> offsets = this.orderedSearchOffsets(
                    targetRoot,
                    targetOffset.searchOffsets(),
                    targetOffset.priority(),
                    predictedPosition,
                    optimalLine);

            for (BlockPos offset : offsets) {
                BlockPos position = targetRoot.offset(offset);
                BlockState state = mc.level.getBlockState(position);
                positionsChecked++;
                if (isBlockSolid(state, position)) {
                    continue;
                }

                TargetingMode targetingMode = state.isAir() || !state.getFluidState().isEmpty()
                        ? TargetingMode.PLACE_AT_NEIGHBOR
                        : TargetingMode.REPLACE_EXISTING_BLOCK;
                if (targetingMode == TargetingMode.REPLACE_EXISTING_BLOCK
                        && !this.canBeReplacedWith(state, position, stack)) {
                    continue;
                }

                TargetPlan targetPlan = this.findBestTargetPlan(
                        position,
                        targetingMode,
                        planningEye,
                        serverRotation,
                        targetOffset.considerFacingAwayFaces());
                facesChecked += Direction.values().length;
                if (targetPlan == null) {
                    continue;
                }

                BlockPos supportPos = targetPlan.blockPosToInteractWith();
                PointOnFace pointOnFace = this.findTargetPointOnFace(
                        mc.level.getBlockState(supportPos),
                        supportPos,
                        targetPlan,
                        facePointFactory);
                if (pointOnFace == null) {
                    continue;
                }

                Vec3 worldPoint = pointOnFace.point().add(Vec3.atLowerCornerOf(supportPos));
                Rotation rotation = new Rotation(planningEye, worldPoint);
                BlockPlacementTarget target = new BlockPlacementTarget(
                        supportPos,
                        position,
                        targetPlan.interactionDirection(),
                        worldPoint,
                        pointOnFace.face().from.y + supportPos.getY(),
                        rotation);
                return new FindResult(
                        target,
                        targetRoot,
                        offset,
                        planningEye,
                        positionsChecked,
                        facesChecked,
                        sourceFor(targetOffset));
            }
        }
        return new FindResult(
                null,
                targetedPosition,
                null,
                planningEye,
                positionsChecked,
                facesChecked,
                "no-target");
    }

    static List<BlockPos> normalOffsets() {
        return NORMAL_OFFSETS;
    }

    static List<BlockPos> downOffsets() {
        return DOWN_OFFSETS;
    }

    static List<BlockPos> searchOffsets(Technique.SearchOffsets searchOffsets) {
        return switch (searchOffsets) {
            case NORMAL -> NORMAL_OFFSETS;
            case DOWN -> DOWN_OFFSETS;
            case CARDINAL -> CARDINAL_OFFSETS;
            case EXACT -> EXACT_OFFSETS;
        };
    }

    public static BlockPos resolveTargetedPosition(
            Vec3 predictedPosition,
            int placementY,
            boolean sameY) {
        if (predictedPosition == null) {
            return null;
        }
        BlockPos base = BlockPos.containing(predictedPosition);
        return sameY
                ? new BlockPos(base.getX(), placementY, base.getZ())
                : base.below();
    }

    static boolean isBlockSolid(BlockState state, BlockPos position) {
        return mc.level != null
                && state.isFaceSturdy(mc.level, position, Direction.UP, SupportType.CENTER);
    }

    private List<BlockPos> orderedSearchOffsets(
            BlockPos targetedPosition,
            Technique.SearchOffsets searchOffsets,
            Technique.TargetPriority priority,
            Vec3 predictedPosition,
            ScaffoldMovementPlanner.MovementLine optimalLine) {
        List<BlockPos> offsets = new ArrayList<>(searchOffsets(searchOffsets));
        offsets.sort((first, second) -> {
            double firstPriority = this.offsetPriority(
                    targetedPosition.offset(first),
                    predictedPosition,
                    optimalLine,
                    priority);
            double secondPriority = this.offsetPriority(
                    targetedPosition.offset(second),
                    predictedPosition,
                    optimalLine,
                    priority);
            return Double.compare(firstPriority, secondPriority);
        });
        return offsets;
    }

    private double offsetPriority(
            BlockPos position,
            Vec3 predictedPosition,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            Technique.TargetPriority priority) {
        BlockState state = mc.level.getBlockState(position);
        VoxelShape shape = state.getShape(mc.level, position);
        AABB localOutlineBox = shape.isEmpty()
                ? new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                : shape.bounds();
        AABB outlineBox = localOutlineBox.move(
                position.getX(),
                position.getY(),
                position.getZ());
        return priorityDistance(outlineBox, predictedPosition, optimalLine, priority);
    }

    static double priorityDistance(
            AABB outlineBox,
            Vec3 predictedPosition,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            Technique.TargetPriority priority) {
        if (priority == Technique.TargetPriority.LINE_OR_POSITION && optimalLine != null) {
            return optimalLine.distanceToSqr(outlineBox);
        }
        return outlineBox.distanceToSqr(predictedPosition);
    }

    private TargetPlan findBestTargetPlan(
            BlockPos position,
            TargetingMode mode,
            Vec3 planningEye,
            Rotation serverRotation,
            boolean considerFacingAwayFaces) {
        TargetPlan bestPlan = null;
        double bestAngle = Double.POSITIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            TargetPlan plan = this.getTargetPlanForPositionAndDirection(position, direction, mode);
            if (plan == null || !acceptsFacing(
                    plan.calculateAngleToPlayerEyeCosine(planningEye),
                    considerFacingAwayFaces)) {
                continue;
            }
            Rotation targetRotation = new Rotation(planningEye, plan.targetPositionOnBlock());
            double angle = serverRotation.distanceTo(targetRotation);
            if (angle < bestAngle) {
                bestAngle = angle;
                bestPlan = plan;
            }
        }
        return bestPlan;
    }

    static boolean acceptsFacing(double angleToEyeCosine, boolean considerFacingAwayFaces) {
        return considerFacingAwayFaces || angleToEyeCosine >= 0.0;
    }

    private TargetPlan getTargetPlanForPositionAndDirection(
            BlockPos position,
            Direction direction,
            TargetingMode mode) {
        if (mode == TargetingMode.PLACE_AT_NEIGHBOR) {
            BlockPos support = position.relative(direction.getOpposite());
            BlockState supportState = mc.level.getBlockState(support);
            if (supportState.canBeReplaced()) {
                return null;
            }
            return new TargetPlan(support, direction);
        }
        return new TargetPlan(position, direction);
    }

    private PointOnFace findTargetPointOnFace(
            BlockState supportState,
            BlockPos supportPos,
            TargetPlan targetPlan,
            ScaffoldFacePointFactory facePointFactory) {
        PointOnFace best = null;
        double bestDirectionScore = Double.NEGATIVE_INFINITY;
        double bestY = Double.NEGATIVE_INFINITY;
        for (AABB shapeBox : supportState
                .getShape(mc.level, supportPos, CollisionContext.of(mc.player))
                .toAabbs()) {
            ScaffoldGeometry.AlignedFace face = getFace(shapeBox, targetPlan.interactionDirection());
            ScaffoldGeometry.AlignedFace searchFace = prepareSearchFace(face);
            Vec3 targetPosition = facePointFactory.producePositionOnFace(searchFace, supportPos);
            if (targetPosition == null) {
                continue;
            }

            Vec3 centered = targetPosition.subtract(0.5, 0.5, 0.5);
            Vec3i normal = targetPlan.interactionDirection().getNormal();
            double directionScore = new Vec3(
                    centered.x * normal.getX(),
                    centered.y * normal.getY(),
                    centered.z * normal.getZ()).lengthSqr();
            if (directionScore > bestDirectionScore
                    || (Double.compare(directionScore, bestDirectionScore) == 0
                    && targetPosition.y > bestY)) {
                bestDirectionScore = directionScore;
                bestY = targetPosition.y;
                best = new PointOnFace(face, targetPosition);
            }
        }
        return best;
    }

    static ScaffoldGeometry.AlignedFace prepareSearchFace(ScaffoldGeometry.AlignedFace face) {
        if (face == null || face.to.y < 0.9) {
            return face;
        }
        double minY = Math.max(face.from.y, 0.6);
        if (face.to.y <= minY) {
            return face;
        }
        return new ScaffoldGeometry.AlignedFace(
                new Vec3(face.from.x, minY, face.from.z),
                face.to);
    }

    private boolean canBeReplacedWith(
            BlockState state,
            BlockPos position,
            ItemStack stack) {
        BlockPlaceContext context = new BlockPlaceContext(
                mc.player,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(
                        Vec3.atLowerCornerOf(position),
                        Direction.UP,
                        position,
                        false));
        return state.canBeReplaced(context);
    }

    static ScaffoldGeometry.AlignedFace getFace(AABB box, Direction direction) {
        return switch (direction) {
            case DOWN -> new ScaffoldGeometry.AlignedFace(
                    new Vec3(box.minX, box.minY, box.minZ),
                    new Vec3(box.maxX, box.minY, box.maxZ));
            case UP -> new ScaffoldGeometry.AlignedFace(
                    new Vec3(box.minX, box.maxY, box.minZ),
                    new Vec3(box.maxX, box.maxY, box.maxZ));
            case SOUTH -> new ScaffoldGeometry.AlignedFace(
                    new Vec3(box.minX, box.minY, box.maxZ),
                    new Vec3(box.maxX, box.maxY, box.maxZ));
            case NORTH -> new ScaffoldGeometry.AlignedFace(
                    new Vec3(box.minX, box.minY, box.minZ),
                    new Vec3(box.maxX, box.maxY, box.minZ));
            case EAST -> new ScaffoldGeometry.AlignedFace(
                    new Vec3(box.maxX, box.minY, box.minZ),
                    new Vec3(box.maxX, box.maxY, box.maxZ));
            case WEST -> new ScaffoldGeometry.AlignedFace(
                    new Vec3(box.minX, box.minY, box.minZ),
                    new Vec3(box.minX, box.maxY, box.maxZ));
        };
    }

    private static List<BlockPos> generateNormalOffsets() {
        return generateOffsets(0, -1, 1);
    }

    private static List<BlockPos> generateOffsets(int... horizontalValues) {
        Set<BlockPos> offsets = new LinkedHashSet<>();
        for (int x : horizontalValues) {
            for (int z : horizontalValues) {
                offsets.add(new BlockPos(x, 0, z));
                offsets.add(new BlockPos(x, -1, z));
            }
        }
        List<BlockPos> result = new ArrayList<>(offsets);
        result.sort(Comparator
                .comparingLong(ScaffoldTargetFinder::lengthSqr)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return List.copyOf(result);
    }

    private static long lengthSqr(BlockPos position) {
        return (long) position.getX() * position.getX()
                + (long) position.getY() * position.getY()
                + (long) position.getZ() * position.getZ();
    }

    private static boolean isDefaultNormalRequest(List<Technique.TargetOffset> targetOffsets) {
        return targetOffsets.size() == 1
                && DEFAULT_NORMAL_TARGET_OFFSET.equals(targetOffsets.get(0));
    }

    private static String sourceFor(Technique.TargetOffset targetOffset) {
        if (DEFAULT_NORMAL_TARGET_OFFSET.equals(targetOffset)) {
            return "normal";
        }
        if (targetOffset.searchOffsets() == Technique.SearchOffsets.DOWN) {
            return "down";
        }
        if (targetOffset.searchOffsets() == Technique.SearchOffsets.CARDINAL) {
            return "cardinal";
        }
        return targetOffset.searchOffsets() == Technique.SearchOffsets.EXACT
                ? "exact"
                : "technique";
    }

    private enum TargetingMode {
        PLACE_AT_NEIGHBOR,
        REPLACE_EXISTING_BLOCK
    }

    private record TargetPlan(
            BlockPos blockPosToInteractWith,
            Direction interactionDirection) {
        private Vec3 targetPositionOnBlock() {
            Vec3 center = Vec3.atCenterOf(this.blockPosToInteractWith);
            Vec3i normal = this.interactionDirection.getNormal();
            return center.add(
                    normal.getX() * 0.5,
                    normal.getY() * 0.5,
                    normal.getZ() * 0.5);
        }

        private double calculateAngleToPlayerEyeCosine(Vec3 eyePosition) {
            Vec3 delta = eyePosition.subtract(this.targetPositionOnBlock());
            if (ScaffoldGeometry.isLikelyZero(delta)) {
                return 1.0;
            }
            Vec3i normal = this.interactionDirection.getNormal();
            return delta.dot(new Vec3(normal.getX(), normal.getY(), normal.getZ()))
                    / delta.length();
        }
    }

    private record PointOnFace(
            ScaffoldGeometry.AlignedFace face,
            Vec3 point) {
    }

    public record FindResult(
            BlockPlacementTarget target,
            BlockPos targetedPosition,
            BlockPos selectedOffset,
            Vec3 planningEye,
            int positionsChecked,
            int facesChecked,
            String source) {
        private static FindResult none(String source) {
            return new FindResult(null, null, null, null, 0, 0, source);
        }
    }
}
