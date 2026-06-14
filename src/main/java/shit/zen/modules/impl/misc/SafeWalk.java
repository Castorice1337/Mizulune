package shit.zen.modules.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.NumberValue;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.EdgeSafetyUtil;
import shit.zen.utils.game.EdgeSafetyUtil.MovementVector;

public class SafeWalk
extends Module {
    private static final double ADAPTIVE_PREDICT_DISTANCE = 0.08;
    private static final double ADAPTIVE_SIDE_MARGIN = 0.04;
    private static final int AIRBORNE_SNEAK_GRACE_TICKS = 2;

    public final NumberValue edgeDistanceMin = new NumberValue("Edge Distance Min", 0.08, 0.01, 0.5, 0.01);
    public final NumberValue edgeDistanceMax = new NumberValue("Edge Distance Max", 0.22, 0.01, 0.5, 0.01);
    public final NumberValue sneakTicksMin = new NumberValue("Sneak Ticks Min", 2, 1, 20, 1);
    public final NumberValue sneakTicksMax = new NumberValue("Sneak Ticks Max", 5, 1, 20, 1);
    public final NumberValue releaseDelay = new NumberValue("Release Delay", 2, 1, 3, 1);
    public final BooleanValue onlyBlocks = new BooleanValue("Only Blocks", true);
    public final BooleanValue ignoreForward = new BooleanValue("Ignore Forward", true);
    public final BooleanValue adaptive = new BooleanValue("Adaptive", true);
    public final NumberValue adaptiveChance = new NumberValue("Adaptive Chance", 0.16, 0.02, 0.4, 0.01);
    public final BooleanValue debug = new BooleanValue("Debug", false);
    public final NumberValue debugInterval = new NumberValue("Debug Interval", 1, 1, 20, 1);

    private boolean moduleSneaking;
    private boolean edgeDistanceArmed;
    private double currentEdgeDistance;
    private int remainingSneakTicks;
    private int releaseDelayTicks;
    private int adaptiveTapTicks;
    private int adaptiveCooldownTicks;
    private int adaptiveDirection = 1;
    private int adaptiveBalanceTicks;
    private int airborneSneakGraceTicks;
    private int debugTicks;

    public SafeWalk() {
        super("SafeWalk", Category.MISC);
    }

    public static boolean isOnBlockEdge(float distance) {
        return SafeWalk.isNearBlockEdge(distance);
    }

    @EventTarget
    public void onGameTick(GameTickEvent event) {
        String cannotEvaluateReason = this.getCannotEvaluateReason();
        if (cannotEvaluateReason != null) {
            if ("airborne".equals(cannotEvaluateReason) && this.moduleSneaking && this.airborneSneakGraceTicks > 0) {
                this.airborneSneakGraceTicks--;
                this.debugLog("hold-airborne-grace", null, 0.0, 0.0);
                this.holdModuleSneak(false, false);
                return;
            }
            this.debugLog("skip-" + cannotEvaluateReason, null, 0.0, 0.0);
            this.releaseModuleSneak();
            return;
        }

        if (this.isPhysicalShiftDown()) {
            this.debugLog("manual-shift", null, 0.0, 0.0);
            this.resetState();
            mc.options.keyShift.setDown(true);
            return;
        }

        if (!this.edgeDistanceArmed) {
            this.currentEdgeDistance = this.randomEdgeDistance();
            this.edgeDistanceArmed = true;
        }

        MovementVector movementVector = this.getPhysicalMovementVector();
        double effectiveEdgeDistance = this.getEffectiveEdgeDistance(movementVector);
        double distanceToFall = EdgeSafetyUtil.getDistanceToFall(mc.player.getBoundingBox(), movementVector);
        double globalDistanceToFall = EdgeSafetyUtil.getDistanceToFall(mc.player.getBoundingBox());
        boolean nearDirectionalEdge = distanceToFall <= effectiveEdgeDistance;
        boolean nearGlobalEdge = this.shouldTriggerForGlobalEdgeRisk(globalDistanceToFall);
        boolean nearEdge = nearDirectionalEdge || nearGlobalEdge;
        this.debugLog(nearEdge ? nearDirectionalEdge ? "near-edge" : "near-global-edge" : "scan", movementVector, distanceToFall, effectiveEdgeDistance);
        if (nearEdge) {
            boolean triggeredNow = false;
            if (!this.moduleSneaking) {
                this.remainingSneakTicks = this.randomSneakTicks();
                this.moduleSneaking = true;
                triggeredNow = true;
                this.debugLog("trigger", movementVector, distanceToFall, effectiveEdgeDistance);
            }
            if (!triggeredNow && this.remainingSneakTicks > 0) {
                this.remainingSneakTicks--;
            }
            this.releaseDelayTicks = Math.max(0, this.releaseDelay.getValue().intValue());
            this.debugLog("hold-near-edge", movementVector, distanceToFall, effectiveEdgeDistance);
            this.holdModuleSneak(true);
            return;
        }

        if (this.moduleSneaking) {
            if (this.shouldHoldForUnsafeRelease(distanceToFall, effectiveEdgeDistance)) {
                this.releaseDelayTicks = Math.max(1, Math.max(this.releaseDelayTicks, this.releaseDelay.getValue().intValue()));
                this.debugLog("hold-unsafe-release", movementVector, distanceToFall, effectiveEdgeDistance);
                this.holdModuleSneak(true);
                return;
            }
            if (this.remainingSneakTicks > 0) {
                this.remainingSneakTicks--;
                this.debugLog("hold-sneak-ticks", movementVector, distanceToFall, effectiveEdgeDistance);
                this.holdModuleSneak(true);
                return;
            }
            if (this.releaseDelayTicks > 0) {
                this.releaseDelayTicks--;
                this.debugLog("hold-release-delay", movementVector, distanceToFall, effectiveEdgeDistance);
                this.holdModuleSneak(true);
                return;
            }
            if (this.shouldHoldForPredictedRelease(distanceToFall, movementVector)) {
                this.debugLog("hold-predicted-release", movementVector, distanceToFall, effectiveEdgeDistance);
                this.holdModuleSneak(true);
                return;
            }
            if (this.shouldHoldForGlobalEdgeRisk()) {
                this.debugLog("hold-global-edge-risk", movementVector, distanceToFall, effectiveEdgeDistance);
                this.holdModuleSneak(true);
                return;
            }
            this.debugLog("release-safe", movementVector, distanceToFall, effectiveEdgeDistance);
            this.releaseModuleSneak();
        }
    }

    @Override
    public void onDisable() {
        this.resetState();
        this.restorePhysicalShift();
    }

    private String getCannotEvaluateReason() {
        if (mc == null || mc.player == null || mc.level == null || mc.options == null || mc.getWindow() == null) {
            return "missing-client";
        }
        if (!mc.player.onGround()) {
            return "airborne";
        }
        if (this.onlyBlocks.getValue() && !this.hasPlaceableBlockInHands()) {
            return "no-block";
        }
        if (this.ignoreForward.getValue() && this.isKeyDown(mc.options.keyUp)) {
            return "forward";
        }
        return null;
    }

    private boolean hasPlaceableBlockInHands() {
        return this.isPlaceableBlock(mc.player.getMainHandItem()) || this.isPlaceableBlock(mc.player.getOffhandItem());
    }

    private boolean isPlaceableBlock(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack);
    }

    private void holdModuleSneak(boolean allowAdaptive) {
        this.holdModuleSneak(allowAdaptive, true);
    }

    private void holdModuleSneak(boolean allowAdaptive, boolean refreshAirborneGrace) {
        boolean forcePlayerSneak = this.shouldForceTrueEdgeSneak();
        mc.options.keyShift.setDown(true);
        if (forcePlayerSneak) {
            mc.player.setShiftKeyDown(true);
            this.debugLog("force-true-edge-sneak", this.getPhysicalMovementVector(), EdgeSafetyUtil.getDistanceToFall(mc.player.getBoundingBox()), this.currentEdgeDistance);
        }
        if (refreshAirborneGrace) {
            this.airborneSneakGraceTicks = AIRBORNE_SNEAK_GRACE_TICKS;
        }
        if (allowAdaptive) {
            this.updateAdaptiveTap();
        } else {
            this.resetAdaptiveTap();
        }
    }

    private boolean shouldForceTrueEdgeSneak() {
        if (mc == null || mc.player == null || mc.level == null || mc.options == null) {
            return false;
        }
        if (mc.options.keyShift.isDown() && mc.player.isShiftKeyDown()) {
            return false;
        }
        return EdgeSafetyUtil.isTrueEdge(mc.player.getBoundingBox());
    }

    private void releaseModuleSneak() {
        this.resetState();
        this.restorePhysicalShift();
    }

    private void restorePhysicalShift() {
        if (mc == null || mc.options == null || mc.getWindow() == null) {
            return;
        }
        boolean keyDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyShift.getKey().getValue());
        mc.options.keyShift.setDown(keyDown);
    }

    private void resetState() {
        this.moduleSneaking = false;
        this.edgeDistanceArmed = false;
        this.currentEdgeDistance = 0.0;
        this.remainingSneakTicks = 0;
        this.releaseDelayTicks = 0;
        this.airborneSneakGraceTicks = 0;
        this.resetAdaptiveTap();
    }

    private boolean isPhysicalShiftDown() {
        if (mc == null || mc.options == null || mc.getWindow() == null) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyShift.getKey().getValue());
    }

    private double randomEdgeDistance() {
        double min = this.edgeDistanceMin.getValue().doubleValue();
        double max = this.edgeDistanceMax.getValue().doubleValue();
        double lower = Math.max(0.0, Math.min(min, max));
        double upper = Math.max(lower, Math.max(min, max));
        if (upper <= lower) {
            return lower;
        }
        return ThreadLocalRandom.current().nextDouble(lower, upper);
    }

    private int randomSneakTicks() {
        int min = this.sneakTicksMin.getValue().intValue();
        int max = this.sneakTicksMax.getValue().intValue();
        int lower = Math.max(1, Math.min(min, max));
        int upper = Math.max(lower, Math.max(min, max));
        return ThreadLocalRandom.current().nextInt(lower, upper + 1);
    }

    private boolean shouldHoldForUnsafeRelease(double distanceToFall, double effectiveEdgeDistance) {
        return distanceToFall <= effectiveEdgeDistance + EdgeSafetyUtil.RELEASE_DISTANCE_HYSTERESIS;
    }

    private boolean shouldHoldForPredictedRelease(double distanceToFall, MovementVector movementVector) {
        if (movementVector == null) {
            return false;
        }
        return distanceToFall <= this.getPredictedUnsneakStepDistance(movementVector) + EdgeSafetyUtil.MOVEMENT_TRIGGER_MARGIN;
    }

    private boolean shouldTriggerForGlobalEdgeRisk(double globalDistance) {
        return globalDistance <= this.currentEdgeDistance;
    }

    private boolean shouldHoldForGlobalEdgeRisk() {
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        double globalDistance = EdgeSafetyUtil.getDistanceToFall(mc.player.getBoundingBox());
        double threshold = this.currentEdgeDistance + EdgeSafetyUtil.RELEASE_DISTANCE_HYSTERESIS + ADAPTIVE_PREDICT_DISTANCE;
        return globalDistance <= threshold;
    }

    private double getEffectiveEdgeDistance(MovementVector movementVector) {
        if (movementVector == null) {
            return this.currentEdgeDistance;
        }
        return Math.max(this.currentEdgeDistance, this.getPredictedUnsneakStepDistance(movementVector) + EdgeSafetyUtil.MOVEMENT_TRIGGER_MARGIN);
    }

    private boolean shouldApplyAdaptiveTap() {
        if (!this.adaptive.getValue() || !this.moduleSneaking || mc == null || mc.player == null || mc.level == null || mc.options == null || mc.getWindow() == null) {
            return false;
        }
        if (this.isPhysicalShiftDown()) {
            return false;
        }
        if (this.onlyBlocks.getValue() && !this.hasPlaceableBlockInHands()) {
            return false;
        }
        if (!this.isOnlyBackKeyDown()) {
            return false;
        }
        return mc.player.onGround();
    }

    private boolean isOnlyBackKeyDown() {
        return this.isKeyDown(mc.options.keyDown)
                && !this.isKeyDown(mc.options.keyUp)
                && !this.isKeyDown(mc.options.keyLeft)
                && !this.isKeyDown(mc.options.keyRight);
    }

    private boolean isKeyDown(KeyMapping keyMapping) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), keyMapping.getKey().getValue());
    }

    private MovementVector getPhysicalMovementVector() {
        int forward = 0;
        int strafe = 0;
        if (this.isKeyDown(mc.options.keyUp)) {
            forward += 1;
        }
        if (this.isKeyDown(mc.options.keyDown)) {
            forward -= 1;
        }
        if (this.isKeyDown(mc.options.keyLeft)) {
            strafe += 1;
        }
        if (this.isKeyDown(mc.options.keyRight)) {
            strafe -= 1;
        }
        return EdgeSafetyUtil.getMovementVectorFromInput(mc.player.getYRot(), forward, strafe);
    }

    private double getPredictedUnsneakStepDistance(MovementVector movementVector) {
        return EdgeSafetyUtil.getPredictedUnsneakStepDistance(mc.player.getDeltaMovement(), movementVector, mc.options.keyShift.isDown());
    }

    private void debugLog(String reason, MovementVector movementVector, double distanceToFall, double effectiveEdgeDistance) {
        if (!this.debug.getValue() || mc == null || mc.player == null || mc.options == null) {
            return;
        }
        boolean important = this.moduleSneaking
                || reason.startsWith("trigger")
                || reason.startsWith("hold")
                || reason.startsWith("release")
                || reason.startsWith("skip")
                || reason.startsWith("force")
                || EdgeSafetyUtil.getDistanceToFall(mc.player.getBoundingBox()) <= Math.max(EdgeSafetyUtil.TRUE_EDGE_FALL_DISTANCE, this.currentEdgeDistance);
        if (!important) {
            return;
        }
        this.debugTicks++;
        int interval = Math.max(1, this.debugInterval.getValue().intValue());
        if (this.debugTicks % interval != 0 && !reason.startsWith("trigger") && !reason.startsWith("release") && !reason.startsWith("skip") && !reason.startsWith("force")) {
            return;
        }
        Vec3 delta = mc.player.getDeltaMovement();
        double globalDistance = EdgeSafetyUtil.getDistanceToFall(mc.player.getBoundingBox());
        double predictedStep = movementVector == null ? 0.0 : this.getPredictedUnsneakStepDistance(movementVector);
        logger.info(String.format(Locale.US,
                "[SafeWalkDebug] reason=%s moduleSneak=%s keyShift=%s playerShift=%s physicalShift=%s onGround=%s "
                        + "dist=%.4f globalDist=%.4f edge=%.4f effEdge=%.4f predStep=%.4f rem=%d delay=%d "
                        + "move=(%.3f,%.3f) delta=(%.4f,%.4f,%.4f) pos=(%.3f,%.3f,%.3f) adaptiveTap=%d adaptiveCd=%d balance=%d",
                reason,
                this.moduleSneaking,
                mc.options.keyShift.isDown(),
                mc.player.isShiftKeyDown(),
                this.isPhysicalShiftDown(),
                mc.player.onGround(),
                distanceToFall,
                globalDistance,
                this.currentEdgeDistance,
                effectiveEdgeDistance,
                predictedStep,
                this.remainingSneakTicks,
                this.releaseDelayTicks,
                movementVector == null ? 0.0 : movementVector.x(),
                movementVector == null ? 0.0 : movementVector.z(),
                delta.x,
                delta.y,
                delta.z,
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                this.adaptiveTapTicks,
                this.adaptiveCooldownTicks,
                this.adaptiveBalanceTicks));
    }

    private void updateAdaptiveTap() {
        this.restorePhysicalHorizontalKeys();
        if (!this.shouldApplyAdaptiveTap()) {
            this.resetAdaptiveTap();
            return;
        }

        if (this.adaptiveCooldownTicks > 0) {
            this.adaptiveCooldownTicks--;
            return;
        }

        if (this.adaptiveTapTicks <= 0) {
            this.pickBalancedAdaptiveTap();
        }

        if (this.adaptiveTapTicks <= 0) {
            return;
        }

        if (!this.isAdaptiveDirectionSafe(this.adaptiveDirection)) {
            this.adaptiveTapTicks = 0;
            this.adaptiveCooldownTicks = ThreadLocalRandom.current().nextInt(3, 8);
            return;
        }

        if (this.adaptiveDirection > 0) {
            mc.options.keyRight.setDown(true);
        } else {
            mc.options.keyLeft.setDown(true);
        }
        this.adaptiveBalanceTicks += this.adaptiveDirection;
        this.adaptiveTapTicks--;
        if (this.adaptiveTapTicks <= 0) {
            this.adaptiveCooldownTicks = this.randomAdaptiveCooldown();
        }
    }

    private void pickBalancedAdaptiveTap() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int tapTicks = random.nextDouble() < 0.75 ? 1 : 2;
        int direction = this.pickBalancedAdaptiveDirection(tapTicks);
        if (direction == 0) {
            this.adaptiveCooldownTicks = this.randomAdaptiveCooldown();
            return;
        }
        this.adaptiveDirection = direction;
        this.adaptiveTapTicks = tapTicks;
    }

    private int randomAdaptiveCooldown() {
        double density = Math.max(0.02, Math.min(0.4, this.adaptiveChance.getValue().doubleValue()));
        int base = Math.max(2, (int) Math.round(1.0 / density));
        int lower = Math.max(1, base / 2);
        int upper = Math.max(lower + 1, base + 3);
        return ThreadLocalRandom.current().nextInt(lower, upper + 1);
    }

    private int pickBalancedAdaptiveDirection(int tapTicks) {
        int preferred = this.adaptiveBalanceTicks > 0 ? -1 : this.adaptiveBalanceTicks < 0 ? 1 : -this.adaptiveDirection;
        int alternate = -preferred;
        boolean preferredSafe = this.isAdaptiveDirectionSafe(preferred);
        boolean alternateSafe = this.isAdaptiveDirectionSafe(alternate);
        if (!preferredSafe && !alternateSafe) {
            return 0;
        }
        if (preferredSafe && !alternateSafe) {
            return preferred;
        }
        if (!preferredSafe) {
            return alternate;
        }

        int preferredBalance = Math.abs(this.adaptiveBalanceTicks + preferred * tapTicks);
        int alternateBalance = Math.abs(this.adaptiveBalanceTicks + alternate * tapTicks);
        if (preferredBalance < alternateBalance) {
            return preferred;
        }
        if (alternateBalance < preferredBalance) {
            return alternate;
        }
        return preferred;
    }

    private void resetAdaptiveTap() {
        this.restorePhysicalHorizontalKeys();
        this.adaptiveTapTicks = 0;
        this.adaptiveCooldownTicks = 0;
        this.adaptiveBalanceTicks = 0;
    }

    private void restorePhysicalHorizontalKeys() {
        if (mc == null || mc.options == null || mc.getWindow() == null) {
            return;
        }
        mc.options.keyLeft.setDown(this.isKeyDown(mc.options.keyLeft));
        mc.options.keyRight.setDown(this.isKeyDown(mc.options.keyRight));
    }

    private boolean isAdaptiveDirectionSafe(int direction) {
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        double globalSafetyThreshold = this.currentEdgeDistance + ADAPTIVE_SIDE_MARGIN + ADAPTIVE_PREDICT_DISTANCE;
        if (EdgeSafetyUtil.getDistanceToFall(mc.player.getBoundingBox()) <= globalSafetyThreshold) {
            return false;
        }
        double strafe = direction > 0 ? -1.0 : 1.0;
        double yaw = Math.toRadians(mc.player.getYRot());
        double unitX = strafe * Math.cos(yaw);
        double unitZ = strafe * Math.sin(yaw);
        AABB predicted = mc.player.getBoundingBox().move(unitX * ADAPTIVE_PREDICT_DISTANCE, 0.0, unitZ * ADAPTIVE_PREDICT_DISTANCE);
        double sideDistance = EdgeSafetyUtil.getDirectionalDistanceToFall(predicted, unitX, unitZ);
        double globalDistance = EdgeSafetyUtil.getDistanceToFall(predicted);
        return sideDistance > this.currentEdgeDistance + ADAPTIVE_SIDE_MARGIN
                && globalDistance > globalSafetyThreshold;
    }

    private static boolean isNearBlockEdge(double distance) {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        AABB box = mc.player.getBoundingBox();
        double threshold = Math.max(0.0, distance);
        return EdgeSafetyUtil.getDistanceToFall(box) <= threshold;
    }
}
