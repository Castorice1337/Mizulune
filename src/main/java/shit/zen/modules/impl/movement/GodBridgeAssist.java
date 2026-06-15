/*
 * This file includes logic adapted from LiquidBounce Nextgen:
 * net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.ScaffoldGodBridgeTechnique
 * net.ccbluex.liquidbounce.features.module.modules.world.scaffold.features.ScaffoldLedgeFeature
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen as a standalone ghost/legit GodBridge assist module
 * instead of a full Scaffold technique.
 */
package shit.zen.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.EdgeSafetyUtil;
import shit.zen.utils.game.EdgeSafetyUtil.MovementVector;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationApplyMode;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.utils.rotation.RotationProvider;
import shit.zen.utils.rotation.SmoothMode;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class GodBridgeAssist extends Module implements RotationProvider {
    public static GodBridgeAssist INSTANCE;

    private static final double TARGET_PROBE_DISTANCE = 0.55;
    private static final double LEDGE_PREDICT_DISTANCE = 0.24;
    private static final double EDGE_INFLATE = 0.08;
    private static final double MAX_PLACE_DISTANCE_SQ = 20.25;
    private static final BlockPos[] PLACEMENT_OFFSETS = {
            new BlockPos(0, 0, 0),
            new BlockPos(0, -1, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 0, -1),
            new BlockPos(0, 0, 1),
            new BlockPos(1, 0, 0),
            new BlockPos(-1, -1, 0),
            new BlockPos(0, -1, -1),
            new BlockPos(0, -1, 1),
            new BlockPos(1, -1, 0),
            new BlockPos(-1, 0, -1),
            new BlockPos(-1, 0, 1),
            new BlockPos(1, 0, -1),
            new BlockPos(1, 0, 1),
            new BlockPos(-1, -1, -1),
            new BlockPos(-1, -1, 1),
            new BlockPos(1, -1, -1),
            new BlockPos(1, -1, 1)
    };

    private final GodBridgeAngleSolver angleSolver = new GodBridgeAngleSolver();

    public final ModeValue rotationMode = new ModeValue("Rotation Mode", "ChangeLook", "Silent", "Off").withDefault("ChangeLook");
    public final ModeValue smoothMode = new ModeValue("Smooth Mode", "SIGMOID", "LINEAR", "SNAP").withDefault("SIGMOID");
    public final ModeValue timing = new ModeValue("Timing", "Normal").withDefault("Normal");
    public final NumberValue smoothDuration = new NumberValue("Smooth Duration", 6, 1, 20, 1, () -> !this.smoothMode.is("SNAP"));
    public final NumberValue smoothSteepness = new NumberValue("Smooth Steepness", 8.0, 1.0, 16.0, 0.1, () -> this.smoothMode.is("SIGMOID"));
    public final NumberValue maxYawSpeed = new NumberValue("Max Yaw Speed", 45.0, 1.0, 180.0, 1.0, () -> !this.smoothMode.is("SNAP"));
    public final NumberValue maxPitchSpeed = new NumberValue("Max Pitch Speed", 35.0, 1.0, 90.0, 1.0, () -> !this.smoothMode.is("SNAP"));
    public final NumberValue minStep = new NumberValue("Min Step", 0.15, 0.01, 5.0, 0.01, () -> !this.smoothMode.is("SNAP"));
    public final NumberValue epsilon = new NumberValue("Epsilon", 0.25, 0.01, 3.0, 0.01, () -> !this.smoothMode.is("SNAP"));
    public final BooleanValue humanizeRotation = new BooleanValue("Humanize Rotation", false, () -> !this.smoothMode.is("SNAP"));
    public final BooleanValue movementFix = new BooleanValue("Movement Fix", false, () -> !this.rotationMode.is("Off"));
    public final ModeValue placeMode = new ModeValue("Place Mode", "ManualPlaceOnly", "AutoPlace").withDefault("ManualPlaceOnly");
    public final BooleanValue autoBlock = new BooleanValue("Auto Block", false);
    public final BooleanValue counterOnIsland = new BooleanValue("Counter On Island", false);
    public final BooleanValue ledgeAssist = new BooleanValue("Ledge Assist", true);
    public final ModeValue ledgeDetection = new ModeValue("Ledge Detection", "SafeWalkDistance", "PredictProbe")
            .withDefault("SafeWalkDistance")
            .withVisibility(() -> this.ledgeAssist.getValue());
    public final ModeValue ledgeAction = new ModeValue("Ledge Action", "Sneak", "Jump", "StopInput", "Backwards").withDefault("Sneak");
    public final NumberValue bridgeEdgeDistanceMin = new NumberValue("Bridge Edge Distance Min", 0.08, 0.01, 0.5, 0.01, this::isSafeWalkDistanceLedge);
    public final NumberValue bridgeEdgeDistanceMax = new NumberValue("Bridge Edge Distance Max", 0.18, 0.01, 0.5, 0.01, this::isSafeWalkDistanceLedge);
    public final NumberValue bridgeLedgeTicksMin = new NumberValue("Bridge Ledge Ticks Min", 1, 1, 10, 1, this::isSafeWalkDistanceLedge);
    public final NumberValue bridgeLedgeTicksMax = new NumberValue("Bridge Ledge Ticks Max", 3, 1, 10, 1, this::isSafeWalkDistanceLedge);
    public final NumberValue bridgeReleaseDelay = new NumberValue("Bridge Release Delay", 1, 0, 5, 1, this::isSafeWalkDistanceLedge);
    public final BooleanValue forceTrueEdgeSneak = new BooleanValue("Force True Edge Sneak", true, this::isSafeWalkDistanceLedge);

    private Rotation currentTargetRotation;
    private PlacementTarget currentTarget;
    private boolean moduleSneakDown;
    private boolean moduleJumpDown;
    private boolean moduleUseDown;
    private boolean stopInputThisTick;
    private boolean backwardsThisTick;
    private int lastPlaceTick = -1;
    private int savedAutoBlockSlot = -1;
    private int lockedBlockSlot = -1;
    private boolean edgeDistanceArmed;
    private double currentEdgeDistance;
    private int remainingLedgeTicks;
    private int releaseDelayTicks;

    public GodBridgeAssist() {
        super("BridgeAssist", Category.MISC);
        INSTANCE = this;
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup rotation = root.group("rotation", "Rotation");
        rotation.add(this.rotationMode);
        rotation.add(this.smoothMode);
        rotation.add(this.timing);
        rotation.add(this.smoothDuration);
        rotation.add(this.smoothSteepness);
        rotation.add(this.maxYawSpeed);
        rotation.add(this.maxPitchSpeed);
        rotation.add(this.minStep);
        rotation.add(this.epsilon);
        rotation.add(this.humanizeRotation);
        rotation.add(this.movementFix);

        ValueGroup placement = root.group("placement", "Placement");
        placement.add(this.placeMode);
        placement.add(this.autoBlock);
        placement.add(this.counterOnIsland);

        ValueGroup ledge = root.group("ledge", "Ledge");
        ledge.add(this.ledgeAssist);
        ledge.add(this.ledgeDetection);
        ledge.add(this.ledgeAction);
        ledge.add(this.bridgeEdgeDistanceMin);
        ledge.add(this.bridgeEdgeDistanceMax);
        ledge.add(this.bridgeLedgeTicksMin);
        ledge.add(this.bridgeLedgeTicksMax);
        ledge.add(this.bridgeReleaseDelay);
        ledge.add(this.forceTrueEdgeSneak);
    }

    @Override
    protected void onEnable() {
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            Scaffold.INSTANCE.setEnabled(false);
        }
        this.currentTargetRotation = null;
        this.currentTarget = null;
        this.stopInputThisTick = false;
        this.backwardsThisTick = false;
        this.lastPlaceTick = -1;
        this.savedAutoBlockSlot = -1;
        this.lockedBlockSlot = -1;
        this.resetBridgeEdgeState();
        RotationHandler.registerProvider(this);
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        RotationHandler.unregisterProvider(this);
        this.currentTargetRotation = null;
        this.currentTarget = null;
        this.stopInputThisTick = false;
        this.backwardsThisTick = false;
        this.resetBridgeEdgeState();
        this.restoreAutoBlockSlot();
        this.restoreControlledKeys();
        super.onDisable();
    }

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        this.restoreControlledKeys();
        this.stopInputThisTick = false;
        this.backwardsThisTick = false;
        if (mc.player == null || mc.level == null || mc.options == null) {
            this.currentTargetRotation = null;
            this.currentTarget = null;
            this.resetBridgeEdgeState();
            return;
        }
        if (!this.ledgeAssist.getValue()) {
            this.resetBridgeEdgeState();
        }

        this.updateAutoBlockSlot();
        this.currentTarget = this.findPlacementTarget();
        this.currentTargetRotation = this.angleSolver.solve(
                this.toSolverTarget(this.currentTarget),
                this.getPhysicalForward(),
                this.getPhysicalStrafe());

        if (this.ledgeAssist.getValue() && this.shouldApplyLedgeAssist()) {
            this.applyLedgeAssist();
        }
    }

    @EventTarget(value = 4)
    public void onTickLate(TickEvent event) {
        this.updateAutoBlockSlot();
        if (this.placeMode.is("AutoPlace")) {
            this.tryAutoPlace();
        }
    }

    @EventTarget(value = 3)
    public void onStrafe(StrafeEvent event) {
        if (this.stopInputThisTick) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
        } else if (this.backwardsThisTick) {
            event.setForward(-1.0f);
            event.setStrafe(0.0f);
        }
    }

    @Override
    public String getName() {
        return "BridgeAssist";
    }

    @Override
    public Rotation getRotation() {
        return this.currentTargetRotation;
    }

    @Override
    public boolean isRotationActive() {
        return this.isEnabled() && this.getApplyMode() != RotationApplyMode.OFF && this.currentTargetRotation != null;
    }

    @Override
    public RotationApplyMode getApplyMode() {
        if (this.rotationMode.is("ChangeLook")) {
            return RotationApplyMode.CHANGE_LOOK;
        }
        if (this.rotationMode.is("Off")) {
            return RotationApplyMode.OFF;
        }
        return RotationApplyMode.SILENT;
    }

    @Override
    public SmoothMode getSmoothMode() {
        if (this.smoothMode.is("SNAP")) {
            return SmoothMode.SNAP;
        }
        if (this.smoothMode.is("LINEAR")) {
            return SmoothMode.LINEAR;
        }
        return SmoothMode.SIGMOID;
    }

    @Override
    public int getSmoothDurationTicks() {
        return Math.max(1, this.smoothDuration.getValue().intValue());
    }

    @Override
    public double getSmoothSteepness() {
        return this.smoothSteepness.getValue().doubleValue();
    }

    @Override
    public double getMaxYawSpeed() {
        return this.maxYawSpeed.getValue().doubleValue();
    }

    @Override
    public double getMaxPitchSpeed() {
        return this.maxPitchSpeed.getValue().doubleValue();
    }

    @Override
    public double getMinStep() {
        return this.minStep.getValue().doubleValue();
    }

    @Override
    public double getRotationEpsilon() {
        return this.epsilon.getValue().doubleValue();
    }

    @Override
    public boolean shouldHumanizeRotation() {
        return this.humanizeRotation.getValue();
    }

    @Override
    public boolean shouldFixMovement() {
        return this.getApplyMode() == RotationApplyMode.SILENT || this.movementFix.getValue();
    }

    @Override
    public int getRotationPriority() {
        return 40;
    }

    private GodBridgeAngleSolver.Target toSolverTarget(PlacementTarget target) {
        return target == null ? null : new GodBridgeAngleSolver.Target(this.getHitVec(target));
    }

    private boolean isSafeWalkDistanceLedge() {
        return this.ledgeAssist.getValue() && this.ledgeDetection.is("SafeWalkDistance");
    }

    private boolean shouldApplyLedgeAssist() {
        if (!this.isInputActive()) {
            this.resetBridgeEdgeState();
            return false;
        }
        if (!this.shouldProtectBridgeEdge()) {
            return false;
        }
        if (this.getPlaceableBlockCount() <= 0) {
            return true;
        }
        if (!this.isValidPlacementTarget(this.currentTarget) || this.currentTargetRotation == null) {
            return true;
        }
        Rotation smoothedRotation = RotationHandler.getSmoothedRotation(this);
        return smoothedRotation == null || !this.canPlaceWithRotation(smoothedRotation);
    }

    private void applyLedgeAssist() {
        String action = this.getPlaceableBlockCount() <= 0 ? "Sneak" : this.ledgeAction.getValue();
        if ("Jump".equalsIgnoreCase(action)) {
            this.setKeyDown(mc.options.keyJump, true);
            this.moduleJumpDown = true;
        } else if ("StopInput".equalsIgnoreCase(action)) {
            this.stopInputThisTick = true;
        } else if ("Backwards".equalsIgnoreCase(action)) {
            this.backwardsThisTick = true;
        } else {
            this.setKeyDown(mc.options.keyShift, true);
            this.moduleSneakDown = true;
            if (this.forceTrueEdgeSneak.getValue() && mc.player != null && EdgeSafetyUtil.isTrueEdge(mc.player.getBoundingBox())) {
                mc.player.setShiftKeyDown(true);
            }
        }
    }

    private boolean shouldProtectBridgeEdge() {
        if (this.ledgeDetection.is("PredictProbe")) {
            this.resetBridgeEdgeState();
            return this.willWalkOffEdge();
        }
        return this.shouldProtectBridgeSafeWalkDistanceEdge();
    }

    private boolean shouldProtectBridgeSafeWalkDistanceEdge() {
        if (mc.player == null || mc.level == null || mc.options == null || mc.getWindow() == null || !mc.player.onGround()) {
            this.resetBridgeEdgeState();
            return false;
        }
        int forward = this.getPhysicalForward();
        int strafe = this.getPhysicalStrafe();
        MovementVector movementVector = EdgeSafetyUtil.getMovementVectorFromInput(mc.player.getYRot(), forward, strafe);
        if (movementVector == null) {
            this.resetBridgeEdgeState();
            return false;
        }
        if (!this.edgeDistanceArmed) {
            this.currentEdgeDistance = this.randomBridgeEdgeDistance();
            this.edgeDistanceArmed = true;
        }

        AABB box = mc.player.getBoundingBox();
        double distanceToFall = EdgeSafetyUtil.getDistanceToFall(box, movementVector);
        double predictedStep = EdgeSafetyUtil.getPredictedUnsneakStepDistance(mc.player.getDeltaMovement(), movementVector, mc.options.keyShift.isDown());
        double effectiveEdgeDistance = Math.max(this.currentEdgeDistance, predictedStep + EdgeSafetyUtil.MOVEMENT_TRIGGER_MARGIN);
        boolean nearDirectionalEdge = distanceToFall <= effectiveEdgeDistance;
        boolean nearGlobalEdge = EdgeSafetyUtil.getDistanceToFall(box) <= this.currentEdgeDistance;
        if (nearDirectionalEdge || nearGlobalEdge) {
            if (this.remainingLedgeTicks <= 0) {
                this.remainingLedgeTicks = this.randomBridgeLedgeTicks();
            }
            this.releaseDelayTicks = this.getBridgeReleaseDelayTicks();
            return true;
        }

        if (this.isHoldingBridgeEdge()) {
            if (distanceToFall <= effectiveEdgeDistance + EdgeSafetyUtil.RELEASE_DISTANCE_HYSTERESIS) {
                return true;
            }
            if (this.remainingLedgeTicks > 0) {
                this.remainingLedgeTicks--;
                return true;
            }
            if (this.releaseDelayTicks > 0) {
                this.releaseDelayTicks--;
                return true;
            }
        }
        this.resetBridgeEdgeState();
        return false;
    }

    private boolean isHoldingBridgeEdge() {
        return this.remainingLedgeTicks > 0 || this.releaseDelayTicks > 0;
    }

    private double randomBridgeEdgeDistance() {
        double min = this.bridgeEdgeDistanceMin.getValue().doubleValue();
        double max = this.bridgeEdgeDistanceMax.getValue().doubleValue();
        double lower = Math.max(0.0, Math.min(min, max));
        double upper = Math.max(lower, Math.max(min, max));
        if (upper <= lower) {
            return lower;
        }
        return ThreadLocalRandom.current().nextDouble(lower, upper);
    }

    private int randomBridgeLedgeTicks() {
        int min = this.bridgeLedgeTicksMin.getValue().intValue();
        int max = this.bridgeLedgeTicksMax.getValue().intValue();
        int lower = Math.max(1, Math.min(min, max));
        int upper = Math.max(lower, Math.max(min, max));
        return ThreadLocalRandom.current().nextInt(lower, upper + 1);
    }

    private int getBridgeReleaseDelayTicks() {
        return Math.max(0, this.bridgeReleaseDelay.getValue().intValue());
    }

    private void resetBridgeEdgeState() {
        this.edgeDistanceArmed = false;
        this.currentEdgeDistance = 0.0;
        this.remainingLedgeTicks = 0;
        this.releaseDelayTicks = 0;
    }

    private void tryAutoPlace() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        Rotation placeRotation = RotationHandler.getSmoothedRotation(this);
        if (placeRotation == null || mc.gameMode == null || mc.player.tickCount == this.lastPlaceTick) {
            return;
        }
        InteractionHand hand = this.getPlaceHand();
        if (hand == null) {
            return;
        }
        BlockHitResult hit = this.getPlacementHit(placeRotation);
        if (hit == null) {
            return;
        }
        PlacementTarget target = this.findMatchingPlacementTarget(hit);
        if (target == null) {
            return;
        }
        this.currentTarget = target;

        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
        this.lastPlaceTick = mc.player.tickCount;
        if (result == InteractionResult.SUCCESS) {
            mc.player.swing(hand);
        }
    }

    private PlacementTarget findPlacementTarget() {
        BlockPos placePos = this.getTargetPlacePos();
        if (placePos == null) {
            return null;
        }
        return this.findBestPlacementTarget(placePos, null);
    }

    private PlacementTarget findMatchingPlacementTarget(BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (this.isValidPlacementTarget(this.currentTarget) && this.doesHitMatchTarget(hit, this.currentTarget)) {
            return this.currentTarget;
        }
        BlockPos placePos = this.getTargetPlacePos();
        if (placePos == null) {
            return null;
        }
        return this.findBestPlacementTarget(placePos, hit);
    }

    private PlacementTarget findBestPlacementTarget(BlockPos basePlacePos, BlockHitResult requiredHit) {
        PlacementTarget bestTarget = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (BlockPos offset : PLACEMENT_OFFSETS) {
            BlockPos placedBlock = basePlacePos.offset(offset);
            for (Direction facing : Direction.values()) {
                PlacementTarget target = this.createPlacementTarget(placedBlock, facing);
                if (target == null) {
                    continue;
                }
                if (requiredHit != null && !this.doesHitMatchTarget(requiredHit, target)) {
                    continue;
                }
                double score = this.getPlacementTargetScore(target, basePlacePos, requiredHit);
                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = target;
                }
            }
        }
        return bestTarget;
    }

    private PlacementTarget createPlacementTarget(BlockPos placedBlock, Direction facing) {
        if (mc.level == null || mc.player == null || mc.level.isOutsideBuildHeight(placedBlock) || !this.isReplaceable(placedBlock)) {
            return null;
        }
        BlockPos interactedBlock = placedBlock.relative(facing.getOpposite());
        if (mc.level.isOutsideBuildHeight(interactedBlock)) {
            return null;
        }
        BlockState state = mc.level.getBlockState(interactedBlock);
        if (state.isAir() || state.canBeReplaced() || !BlockUtil.isSolid(state) || state.getCollisionShape(mc.level, interactedBlock).isEmpty()) {
            return null;
        }
        FaceTarget faceTarget = this.getFaceTarget(interactedBlock, state, facing);
        if (faceTarget == null || !this.isFaceVisibleToPlayer(faceTarget.point(), facing)) {
            return null;
        }
        PlacementTarget target = new PlacementTarget(interactedBlock, placedBlock, facing, faceTarget.point(), faceTarget.minPlacementY());
        return this.isValidPlacementTarget(target) ? target : null;
    }

    private BlockPos getTargetPlacePos() {
        if (mc.player == null) {
            return null;
        }
        Vec3 probe = mc.player.position().add(this.getInputPredictionVector(TARGET_PROBE_DISTANCE));
        return BlockPos.containing(probe.x, Math.floor(mc.player.getY() - 0.5), probe.z);
    }

    private boolean isValidPlacementTarget(PlacementTarget target) {
        if (target == null || mc.level == null || mc.player == null) {
            return false;
        }
        if (mc.level.isOutsideBuildHeight(target.placedBlockPos()) || !this.isReplaceable(target.placedBlockPos())) {
            return false;
        }
        BlockState state = mc.level.getBlockState(target.interactedBlockPos());
        if (state.isAir() || state.canBeReplaced() || !BlockUtil.isSolid(state) || state.getCollisionShape(mc.level, target.interactedBlockPos()).isEmpty()) {
            return false;
        }
        return mc.player.getEyePosition().distanceToSqr(target.targetPoint()) <= MAX_PLACE_DISTANCE_SQ;
    }

    private boolean isReplaceable(BlockPos pos) {
        return mc.level != null && (mc.level.isEmptyBlock(pos) || mc.level.getBlockState(pos).canBeReplaced());
    }

    private boolean doesRotationHitTarget(Rotation rotation, PlacementTarget target) {
        return this.getPlacementHit(rotation, target) != null;
    }

    private boolean canPlaceWithRotation(Rotation rotation) {
        BlockHitResult hit = this.getPlacementHit(rotation);
        return hit != null && this.findMatchingPlacementTarget(hit) != null;
    }

    private BlockHitResult getPlacementHit(Rotation rotation, PlacementTarget target) {
        BlockHitResult blockHit = this.getPlacementHit(rotation);
        if (blockHit == null || !this.doesHitMatchTarget(blockHit, target)) {
            return null;
        }
        return blockHit;
    }

    private BlockHitResult getPlacementHit(Rotation rotation) {
        if (rotation == null || mc.player == null || mc.level == null) {
            return null;
        }
        HitResult result = this.rayTrace(rotation, 4.5);
        if (!(result instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return blockHit;
    }

    private boolean doesHitMatchTarget(BlockHitResult blockHit, PlacementTarget target) {
        return blockHit != null
                && target != null
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(target.interactedBlockPos())
                && blockHit.getDirection() == target.facing()
                && blockHit.getLocation().y + 1.0E-4 >= target.minPlacementY()
                && this.isReplaceable(target.placedBlockPos());
    }

    private double getPlacementTargetScore(PlacementTarget target, BlockPos basePlacePos, BlockHitResult requiredHit) {
        if (requiredHit != null) {
            return requiredHit.getLocation().distanceToSqr(target.targetPoint());
        }
        Rotation smoothedRotation = RotationHandler.getSmoothedRotation(this);
        if (smoothedRotation != null && this.getPlacementHit(smoothedRotation, target) != null) {
            return -1000.0 + this.blockDistanceSqr(target.placedBlockPos(), basePlacePos);
        }
        Rotation preferredRotation = this.angleSolver.solve(
                this.toSolverTarget(target),
                this.getPhysicalForward(),
                this.getPhysicalStrafe());
        Rotation lookRotation = this.getRotationToPoint(target.targetPoint());
        double score = this.blockDistanceSqr(target.placedBlockPos(), basePlacePos);
        if (preferredRotation != null) {
            if (this.getPlacementHit(preferredRotation, target) != null) {
                score -= 500.0;
            }
            score += this.rotationDistance(preferredRotation, lookRotation) * 0.05;
        }
        return score;
    }

    private double blockDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private double rotationDistance(Rotation first, Rotation second) {
        return Math.abs(Mth.wrapDegrees(first.getYaw() - second.getYaw()))
                + Math.abs(first.getPitch() - second.getPitch());
    }

    private Rotation getRotationToPoint(Vec3 point) {
        Vec3 eye = mc.player.getEyePosition(1.0f);
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0f, 90.0f));
    }

    private FaceTarget getFaceTarget(BlockPos interactedBlock, BlockState state, Direction facing) {
        AABB bestBox = null;
        double bestPlane = 0.0;
        for (AABB box : state.getShape(mc.level, interactedBlock).toAabbs()) {
            double plane = this.getFacePlane(box, facing);
            if (bestBox == null || this.isBetterFaceBox(box, plane, bestBox, bestPlane, facing)) {
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

    private double getFacePlane(AABB box, Direction facing) {
        return switch (facing) {
            case EAST -> box.maxX;
            case WEST -> box.minX;
            case SOUTH -> box.maxZ;
            case NORTH -> box.minZ;
            case UP -> box.maxY;
            case DOWN -> box.minY;
        };
    }

    private boolean isBetterFaceBox(AABB box, double plane, AABB bestBox, double bestPlane, Direction facing) {
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

    private boolean isFaceVisibleToPlayer(Vec3 targetPoint, Direction facing) {
        Vec3 delta = mc.player.getEyePosition(1.0f).subtract(targetPoint);
        double length = delta.length();
        return length > 1.0E-5 && delta.dot(Vec3.atLowerCornerOf(facing.getNormal())) >= 0.0;
    }

    private HitResult rayTrace(Rotation rotation, double range) {
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookDir = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 endPos = eyePos.add(lookDir.scale(range));
        return mc.level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    private Vec3 getHitVec(PlacementTarget target) {
        return target.targetPoint();
    }

    private boolean willWalkOffEdge() {
        if (mc.player == null || mc.level == null || !mc.player.onGround()) {
            return false;
        }
        Vec3 prediction = this.getInputPredictionVector(LEDGE_PREDICT_DISTANCE);
        if (prediction.lengthSqr() < 1.0E-5) {
            return false;
        }
        AABB probe = mc.player.getBoundingBox()
                .move(prediction.x, -0.5, prediction.z)
                .inflate(-EDGE_INFLATE, 0.0, -EDGE_INFLATE);
        return !mc.level.getCollisions(mc.player, probe).iterator().hasNext();
    }

    private Vec3 getInputPredictionVector(double distance) {
        int forward = this.getPhysicalForward();
        int strafe = this.getPhysicalStrafe();
        if (forward == 0 && strafe == 0) {
            return Vec3.ZERO;
        }
        double yaw = MovementUtil.getDirectionYaw(mc.player.getYRot(), forward, strafe);
        return new Vec3(-Math.sin(yaw) * distance, 0.0, Math.cos(yaw) * distance);
    }

    private InteractionHand getPlaceHand() {
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof BlockItem && BlockUtil.isPlaceable(main)) {
            return InteractionHand.MAIN_HAND;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private void updateAutoBlockSlot() {
        if (!this.autoBlock.getValue()) {
            this.restoreAutoBlockSlot();
            return;
        }
        if (mc.player == null) {
            this.savedAutoBlockSlot = -1;
            this.lockedBlockSlot = -1;
            return;
        }

        int blockSlot = this.findAutoBlockSlot();
        if (blockSlot == -1) {
            this.restoreAutoBlockSlot();
            return;
        }
        if (this.savedAutoBlockSlot == -1) {
            this.savedAutoBlockSlot = mc.player.getInventory().selected;
        }

        this.lockedBlockSlot = blockSlot;
        mc.player.getInventory().selected = blockSlot;
    }

    private void restoreAutoBlockSlot() {
        if (mc.player != null && this.savedAutoBlockSlot >= 0 && this.savedAutoBlockSlot < 9) {
            mc.player.getInventory().selected = this.savedAutoBlockSlot;
        }
        this.savedAutoBlockSlot = -1;
        this.lockedBlockSlot = -1;
    }

    private int findAutoBlockSlot() {
        if (mc.player == null) {
            return -1;
        }
        int selected = mc.player.getInventory().selected;
        if (this.savedAutoBlockSlot == -1 && this.isHotbarPlaceableBlock(selected)) {
            return selected;
        }
        if (this.isHotbarPlaceableBlock(this.lockedBlockSlot)) {
            return this.lockedBlockSlot;
        }

        int bestSlot = -1;
        int bestCount = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack) && stack.getCount() > bestCount) {
                bestSlot = i;
                bestCount = stack.getCount();
            }
        }
        return bestSlot;
    }

    private boolean isHotbarPlaceableBlock(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) {
            return false;
        }
        ItemStack stack = mc.player.getInventory().getItem(slot);
        return stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack);
    }

    public boolean isCounterOnIslandActive() {
        return this.isEnabled() && this.counterOnIsland.getValue() && this.getPlaceableBlockCount() > 0;
    }

    public ItemStack getCounterBlockItem() {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack selected = mc.player.getMainHandItem();
        if (selected.getItem() instanceof BlockItem && BlockUtil.isPlaceable(selected)) {
            return selected;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                return stack;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                return stack;
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
            return offhand;
        }
        return ItemStack.EMPTY;
    }

    public int getPlaceableBlockCount() {
        if (mc.player == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) {
                total += stack.getCount();
            }
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
            total += offhand.getCount();
        }
        return total;
    }

    private boolean isInputActive() {
        return this.getPhysicalForward() != 0 || this.getPhysicalStrafe() != 0;
    }

    private int getPhysicalForward() {
        if (mc.options == null || mc.getWindow() == null) {
            return 0;
        }
        int forward = 0;
        if (this.isPhysicalKeyDown(mc.options.keyUp)) {
            forward++;
        }
        if (this.isPhysicalKeyDown(mc.options.keyDown)) {
            forward--;
        }
        return Mth.clamp(forward, -1, 1);
    }

    private int getPhysicalStrafe() {
        if (mc.options == null || mc.getWindow() == null) {
            return 0;
        }
        int strafe = 0;
        if (this.isPhysicalKeyDown(mc.options.keyLeft)) {
            strafe++;
        }
        if (this.isPhysicalKeyDown(mc.options.keyRight)) {
            strafe--;
        }
        return Mth.clamp(strafe, -1, 1);
    }

    private boolean isPhysicalKeyDown(KeyMapping keyMapping) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), keyMapping.getKey().getValue());
    }

    private void setKeyDown(KeyMapping keyMapping, boolean down) {
        KeyMapping.set(keyMapping.getKey(), down);
        keyMapping.setDown(down);
    }

    private void restoreControlledKeys() {
        if (mc == null || mc.options == null || mc.getWindow() == null) {
            return;
        }
        if (this.moduleSneakDown) {
            this.setKeyDown(mc.options.keyShift, this.isPhysicalKeyDown(mc.options.keyShift));
            this.moduleSneakDown = false;
        }
        if (this.moduleJumpDown) {
            this.setKeyDown(mc.options.keyJump, this.isPhysicalKeyDown(mc.options.keyJump));
            this.moduleJumpDown = false;
        }
        if (this.moduleUseDown) {
            this.setKeyDown(mc.options.keyUse, this.isPhysicalKeyDown(mc.options.keyUse));
            this.moduleUseDown = false;
        }
    }

    private record PlacementTarget(BlockPos interactedBlockPos, BlockPos placedBlockPos, Direction facing, Vec3 targetPoint, double minPlacementY) {
    }

    private record FaceTarget(Vec3 point, double minPlacementY) {
    }
}
