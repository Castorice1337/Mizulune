/*
 * This file includes logic adapted from LiquidBounce Nextgen:
 * net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.InterpolationAngleSmooth
 * net.ccbluex.liquidbounce.utils.combat.TargetTracker
 * net.ccbluex.liquidbounce.utils.aiming.point.PointTracker
 * net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen as a visible legit/ghost AimAssist provider
 * with no silent rotation and no automatic attacking.
 */
package shit.zen.modules.impl.misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.AntiBots;
import shit.zen.modules.impl.combat.AntiKB;
import shit.zen.modules.impl.combat.AutoThrow;
import shit.zen.modules.impl.combat.CrystalAura;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.impl.movement.FireballBlink;
import shit.zen.modules.impl.movement.GodBridgeAssist;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.player.AntiTNT;
import shit.zen.modules.impl.player.AntiWeb;
import shit.zen.modules.impl.player.AutoMLG;
import shit.zen.modules.impl.player.AutoWebPlace;
import shit.zen.modules.impl.player.Helper;
import shit.zen.modules.impl.player.MidPearl;
import shit.zen.modules.impl.world.Teams;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationApplyMode;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.utils.rotation.RotationProvider;
import shit.zen.utils.rotation.SmoothMode;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class AimAssist extends Module implements RotationProvider {
    public static AimAssist INSTANCE;

    private static final double WALLS_RANGE = 0.0;
    private static final double FRICTION_FULL_ANGLE = 8.0;
    private static final double FRICTION_MIN_FACTOR = 0.28;
    private static final int DRIFT_TICKS_MIN = 8;
    private static final int DRIFT_TICKS_MAX = 16;

    private final ModeValue mode = new ModeValue("Mode", "Advanced", "Old").withDefault("Advanced");
    private final NumberValue fov = new NumberValue("Fov", 60, 1, 180, 1);
    private final NumberValue range = new NumberValue("Range", 4.5, 1.0, 8.0, 0.1);
    private final NumberValue strength = new NumberValue("Strength", 0.45, 0.05, 1.0, 0.01, this::isAdvancedMode);
    private final NumberValue maxYawSpeed = new NumberValue("Max Yaw Speed", 120, 1, 180, 1, this::isAdvancedMode);
    private final NumberValue maxPitchSpeed = new NumberValue("Max Pitch Speed", 55, 1, 180, 1, this::isAdvancedMode);
    private final ModeValue smoothMode = new ModeValue("Smooth Mode", "INTERPOLATION", "SIGMOID", "LINEAR", "SNAP").withDefault("INTERPOLATION");
    private final ModeValue targetPriority = new ModeValue("Target Priority", "Crosshair", "Distance", "Hybrid").withDefault("Crosshair");
    private final ModeValue aimPoint = new ModeValue("Aim Point", "Dynamic", "Chest", "Nearest").withDefault("Dynamic");
    private final NumberValue stickiness = new NumberValue("Stickiness", 65, 0, 100, 1, this::isAdvancedMode);
    private final NumberValue prediction = new NumberValue("Prediction", 1, 0, 3, 1, this::isAdvancedMode);
    private final ModeValue pitchAssist = new ModeValue("Pitch Assist", "Weak", "Normal", "Off").withDefault("Weak");
    private final BooleanValue aimFriction = new BooleanValue("Aim Friction", true, this::isAdvancedMode);
    private final BooleanValue yawAssist = new BooleanValue("Yaw Assist", true);
    private final BooleanValue mouseDownOnly = new BooleanValue("MouseDownOnly", true);
    private final BooleanValue breakBlock = new BooleanValue("Break Block", true);
    private final NumberValue randomYawOffset = new NumberValue("Random Yaw Offset", 1.2, 0, 5.0, 0.01);
    private final NumberValue randomPitchOffset = new NumberValue("Random Pitch Offset", 0.35, 0, 2.0, 0.01);
    private final BooleanValue oldAdaptive = new BooleanValue("Old Adaptive", true, this::isOldMode);
    private final NumberValue oldAdaptiveOffset = new NumberValue("Old Adaptive Offset", 3, 0.1, 15.0, 0.01, this::isOldMode);
    private final NumberValue oldSmoothAmount = new NumberValue("Old Smooth Amount", 15, 1.0, 90.0, 0.1, this::isOldMode);

    private LivingEntity currentTarget;
    private Rotation currentTargetRotation;
    private double driftYaw;
    private double driftPitch;
    private double targetDriftYaw;
    private double targetDriftPitch;
    private int nextDriftTick;

    public AimAssist() {
        super("AimAssist", Category.MISC);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        this.resetState();
        RotationHandler.registerProvider(this);
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        RotationHandler.unregisterProvider(this);
        this.resetState();
        super.onDisable();
    }

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (!this.canUpdate()) {
            this.resetTracking();
            return;
        }

        this.updateDrift();
        Rotation currentRotation = this.getCurrentRotation();
        if (this.isOldMode()) {
            this.updateOldTarget(currentRotation);
        } else {
            this.updateAdvancedTarget(currentRotation);
        }
    }

    @Override
    public String getName() {
        return "AimAssist";
    }

    @Override
    public Rotation getRotation() {
        return this.isBlockedByHigherPriorityRotation() ? null : this.currentTargetRotation;
    }

    @Override
    public boolean isRotationActive() {
        return this.isEnabled()
                && this.currentTargetRotation != null
                && !this.isBlockedByHigherPriorityRotation();
    }

    @Override
    public RotationApplyMode getApplyMode() {
        return RotationApplyMode.CHANGE_LOOK;
    }

    @Override
    public SmoothMode getSmoothMode() {
        if (this.isOldMode()) {
            return SmoothMode.SNAP;
        }
        if (this.smoothMode.is("SNAP")) {
            return SmoothMode.SNAP;
        }
        if (this.smoothMode.is("LINEAR")) {
            return SmoothMode.LINEAR;
        }
        if (this.smoothMode.is("SIGMOID")) {
            return SmoothMode.SIGMOID;
        }
        return SmoothMode.INTERPOLATION;
    }

    @Override
    public int getSmoothDurationTicks() {
        return 1;
    }

    @Override
    public double getSmoothSteepness() {
        return 8.0;
    }

    @Override
    public double getMaxYawSpeed() {
        return this.isAdvancedMode() ? this.maxYawSpeed.getValue().doubleValue() : 180.0;
    }

    @Override
    public double getMaxPitchSpeed() {
        return this.isAdvancedMode() ? this.maxPitchSpeed.getValue().doubleValue() : 180.0;
    }

    @Override
    public double getMinStep() {
        return 0.03;
    }

    @Override
    public double getRotationEpsilon() {
        return 0.08;
    }

    @Override
    public boolean shouldFixMovement() {
        return false;
    }

    @Override
    public int getRotationPriority() {
        return 5;
    }

    private boolean canUpdate() {
        if (mc.player == null || mc.level == null || mc.options == null || !ZenClient.isReady()) {
            return false;
        }
        if (mc.screen != null) {
            return false;
        }
        if (this.mouseDownOnly.getValue() && !mc.options.keyAttack.isDown()) {
            return false;
        }
        if (this.breakBlock.getValue()
                && mc.options.keyAttack.isDown()
                && mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            return false;
        }
        return !this.isBlockedByHigherPriorityRotation();
    }

    private void updateAdvancedTarget(Rotation currentRotation) {
        TargetCandidate candidate = this.selectTarget(currentRotation, false);
        if (candidate == null) {
            this.resetTracking();
            return;
        }

        LocalPlayer player = mc.player;
        Vec3 eyes = player.getEyePosition(1.0f);
        AABB box = this.getPredictedBox(candidate.entity());
        Vec3 preferredPoint = this.resolveAimPoint(candidate.entity(), box, eyes, currentRotation);
        RotationWithPoint bestRotation = this.raytraceBox(eyes, box, preferredPoint, currentRotation);
        if (bestRotation == null) {
            this.resetTracking();
            return;
        }

        this.currentTarget = candidate.entity();
        Rotation desired = bestRotation.rotation();
        desired.setYaw(desired.getYaw() + (float) this.driftYaw);
        desired.setPitch(Mth.clamp(desired.getPitch() + (float) this.driftPitch, -90.0f, 90.0f));

        double friction = this.resolveFriction(eyes, box, currentRotation, desired);
        double baseStrength = this.strength.getValue().doubleValue() * friction;
        float yaw = currentRotation.getYaw();
        float pitch = currentRotation.getPitch();
        if (this.yawAssist.getValue()) {
            yaw += (float) (Mth.wrapDegrees(desired.getYaw() - yaw) * baseStrength);
        }
        double pitchFactor = this.getPitchAssistFactor();
        if (pitchFactor > 0.0) {
            pitch += (float) ((desired.getPitch() - pitch) * baseStrength * pitchFactor);
        }
        this.currentTargetRotation = new Rotation(yaw, Mth.clamp(pitch, -90.0f, 90.0f));
    }

    private void updateOldTarget(Rotation currentRotation) {
        TargetCandidate candidate = this.selectTarget(currentRotation, true);
        if (candidate == null) {
            this.resetTracking();
            return;
        }

        LocalPlayer player = mc.player;
        Vec3 eyes = player.getEyePosition(1.0f);
        LivingEntity target = candidate.entity();
        Vec3 targetBase = target.position();
        Vec3 targetEye = targetBase.add(0.0, target.getEyeHeight(), 0.0);
        Rotation rotToFeet = RotationUtil.rotationTo(eyes, targetBase);
        Rotation rotToEye = RotationUtil.rotationTo(eyes, targetEye);
        double smooth = Math.max(1.0, this.oldSmoothAmount.getValue().doubleValue());

        double yawOffset = this.driftYaw;
        if (this.oldAdaptive.getValue()) {
            if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
                yawOffset -= this.oldAdaptiveOffset.getValue().doubleValue();
            }
            if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
                yawOffset += this.oldAdaptiveOffset.getValue().doubleValue();
            }
        }

        float yaw = currentRotation.getYaw();
        if (this.yawAssist.getValue()) {
            yaw += (float) ((Mth.wrapDegrees(rotToEye.getYaw() - currentRotation.getYaw()) + yawOffset) / smooth);
        }

        float pitch = currentRotation.getPitch();
        if (this.getPitchAssistFactor() > 0.0) {
            float feetPitchDiff = rotToFeet.getPitch() - currentRotation.getPitch();
            float eyePitchDiff = rotToEye.getPitch() - currentRotation.getPitch();
            if (currentRotation.getPitch() > rotToFeet.getPitch() || currentRotation.getPitch() < rotToEye.getPitch()) {
                pitch += (float) ((Math.abs(feetPitchDiff) > Math.abs(eyePitchDiff) ? eyePitchDiff : feetPitchDiff) / smooth);
            }
            pitch += (float) this.driftPitch;
        }

        this.currentTarget = target;
        this.currentTargetRotation = new Rotation(yaw, Mth.clamp(pitch, -90.0f, 90.0f));
    }

    private TargetCandidate selectTarget(Rotation currentRotation, boolean distanceOnly) {
        List<TargetCandidate> candidates = new ArrayList<>();
        Vec3 eyes = mc.player.getEyePosition(1.0f);
        for (Player player : mc.level.players()) {
            if (!this.isValidTarget(player)) {
                continue;
            }
            AABB box = this.getPredictedBox(player);
            Vec3 center = center(box);
            double distance = this.distanceToBox(eyes, box);
            double angle = this.angleToPoint(currentRotation, eyes, center);
            if (angle > this.fov.getValue().doubleValue()) {
                continue;
            }
            candidates.add(new TargetCandidate(player, this.scoreTarget(angle, distance, distanceOnly)));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(TargetCandidate::score));
        TargetCandidate best = candidates.get(0);
        if (this.currentTarget instanceof Player && this.isValidTarget(this.currentTarget)) {
            TargetCandidate current = candidates.stream()
                    .filter(candidate -> candidate.entity() == this.currentTarget)
                    .findFirst()
                    .orElse(null);
            if (current != null) {
                double threshold = 0.03 + this.stickiness.getValue().doubleValue() / 100.0 * 0.25;
                if (best.score() + threshold >= current.score()) {
                    return current;
                }
            }
        }
        return best;
    }

    private double scoreTarget(double angle, double distance, boolean distanceOnly) {
        double angleScore = angle / Math.max(1.0, this.fov.getValue().doubleValue());
        double distanceScore = distance / Math.max(0.1, this.range.getValue().doubleValue());
        if (distanceOnly || this.targetPriority.is("Distance")) {
            return distanceScore + angleScore * 0.15;
        }
        if (this.targetPriority.is("Hybrid")) {
            return angleScore * 0.65 + distanceScore * 0.35;
        }
        return angleScore + distanceScore * 0.15;
    }

    public boolean isValidTarget(LivingEntity entity) {
        if (entity == null || mc.player == null || mc.level == null || entity == mc.player) {
            return false;
        }
        if (entity.isRemoved() || entity.isDeadOrDying() || entity.getHealth() <= 0.0f || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof Player player && (player.isSpectator() || player.isSleeping())) {
            return false;
        }
        if (Teams.isSameTeam(entity)) {
            return false;
        }
        AntiBots antiBots = AntiBots.INSTANCE;
        if (antiBots != null && antiBots.isEnabled() && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) {
            return false;
        }
        if (!mc.player.hasLineOfSight(entity)) {
            return false;
        }
        Vec3 eyes = mc.player.getEyePosition(1.0f);
        return this.distanceToBox(eyes, entity.getBoundingBox()) <= this.range.getValue().doubleValue();
    }

    private Vec3 resolveAimPoint(LivingEntity entity, AABB box, Vec3 eyes, Rotation currentRotation) {
        if (this.aimPoint.is("Chest")) {
            return this.chestPoint(box);
        }
        if (this.aimPoint.is("Nearest")) {
            return this.nearestPointToLookRay(eyes, currentRotation, box);
        }

        Vec3 rayPoint = this.nearestPointToLookRay(eyes, currentRotation, box);
        Vec3 chest = this.chestPoint(box);
        double vertical = 0.55;
        double dy = entity.getDeltaMovement().y;
        if (dy > 0.08) {
            vertical = 0.48;
        } else if (dy < -0.08 || entity.fallDistance > 0.0f) {
            vertical = 0.62;
        }
        Vec3 dynamic = new Vec3(
                lerp(rayPoint.x, chest.x, 0.45),
                box.minY + boxHeight(box) * vertical,
                lerp(rayPoint.z, chest.z, 0.45));
        return clampPoint(dynamic, box);
    }

    private RotationWithPoint raytraceBox(Vec3 eyes, AABB box, Vec3 preferredPoint, Rotation currentRotation) {
        double rangeSq = this.range.getValue().doubleValue() * this.range.getValue().doubleValue();
        Vec3 preferredOnBox = this.firstHitOnBox(eyes, box, preferredPoint).orElse(preferredPoint);
        if (eyes.distanceToSqr(preferredOnBox) <= rangeSq && this.isVisible(eyes, preferredOnBox)) {
            return new RotationWithPoint(RotationUtil.rotationTo(eyes, preferredPoint), preferredPoint);
        }

        List<Vec3> points = new ArrayList<>();
        points.add(preferredPoint);
        points.add(this.nearestPointToLookRay(eyes, currentRotation, box));
        points.add(this.chestPoint(box));
        points.add(center(box));
        points.add(new Vec3(center(box).x, box.minY + boxHeight(box) * 0.40, center(box).z));
        points.add(new Vec3(center(box).x, box.minY + boxHeight(box) * 0.68, center(box).z));

        RotationWithPoint best = null;
        double bestScore = Double.MAX_VALUE;
        for (Vec3 point : points) {
            Vec3 clamped = clampPoint(point, box);
            Optional<Vec3> hit = this.firstHitOnBox(eyes, box, clamped);
            Vec3 spotOnBox = hit.orElse(clamped);
            double distanceSq = eyes.distanceToSqr(spotOnBox);
            if (distanceSq > rangeSq || (!this.isVisible(eyes, spotOnBox) && distanceSq > WALLS_RANGE * WALLS_RANGE)) {
                continue;
            }
            Rotation rotation = RotationUtil.rotationTo(eyes, clamped);
            double score = this.angleBetween(currentRotation, rotation) + clamped.distanceToSqr(preferredPoint) * 4.0;
            if (score < bestScore) {
                bestScore = score;
                best = new RotationWithPoint(rotation, clamped);
            }
        }
        return best;
    }

    private Optional<Vec3> firstHitOnBox(Vec3 eyes, AABB box, Vec3 point) {
        Vec3 rayTarget = eyes.add(point.subtract(eyes).scale(2.0));
        return box.clip(eyes, rayTarget);
    }

    private boolean isVisible(Vec3 eyes, Vec3 point) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        HitResult result = mc.level.clip(new ClipContext(
                eyes,
                point,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
        return result == null || result.getType() == HitResult.Type.MISS;
    }

    private double resolveFriction(Vec3 eyes, AABB box, Rotation currentRotation, Rotation desired) {
        if (!this.aimFriction.getValue()) {
            return 1.0;
        }
        Vec3 direction = RotationUtil.directionFromRotation(currentRotation);
        Vec3 end = eyes.add(direction.scale(this.range.getValue().doubleValue()));
        if (box.inflate(0.08).clip(eyes, end).isPresent()) {
            return FRICTION_MIN_FACTOR;
        }
        double angle = this.angleBetween(currentRotation, desired);
        return Mth.clamp(angle / FRICTION_FULL_ANGLE, FRICTION_MIN_FACTOR, 1.0);
    }

    private AABB getPredictedBox(LivingEntity entity) {
        int ticks = Mth.clamp(this.prediction.getValue().intValue(), 0, 3);
        Vec3 motion = entity.getDeltaMovement().scale(ticks);
        return entity.getBoundingBox().move(motion);
    }

    private Vec3 chestPoint(AABB box) {
        return new Vec3(center(box).x, box.minY + boxHeight(box) * 0.55, center(box).z);
    }

    private Vec3 nearestPointToLookRay(Vec3 eyes, Rotation rotation, AABB box) {
        Vec3 direction = RotationUtil.directionFromRotation(rotation);
        Vec3 center = center(box);
        double projection = Math.max(0.0, center.subtract(eyes).dot(direction));
        Vec3 pointOnRay = eyes.add(direction.scale(projection));
        return closestPoint(pointOnRay, box);
    }

    private double getPitchAssistFactor() {
        if (this.pitchAssist.is("Off")) {
            return 0.0;
        }
        if (this.pitchAssist.is("Normal")) {
            return 1.0;
        }
        return 0.45;
    }

    private void updateDrift() {
        int tick = mc.player.tickCount;
        if (tick >= this.nextDriftTick) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.targetDriftYaw = randomSymmetric(this.randomYawOffset.getValue().doubleValue());
            this.targetDriftPitch = randomSymmetric(this.randomPitchOffset.getValue().doubleValue());
            this.nextDriftTick = tick + random.nextInt(DRIFT_TICKS_MIN, DRIFT_TICKS_MAX + 1);
        }
        this.driftYaw = lerp(this.driftYaw, this.targetDriftYaw, 0.12);
        this.driftPitch = lerp(this.driftPitch, this.targetDriftPitch, 0.12);
    }

    private boolean isBlockedByHigherPriorityRotation() {
        if (GodBridgeAssist.INSTANCE != null && GodBridgeAssist.INSTANCE.isRotationActive()) {
            return true;
        }
        if (AutoMLG.INSTANCE != null && AutoMLG.INSTANCE.isEnabled() && AutoMLG.INSTANCE.targetRotation != null) {
            return true;
        }
        if (CrystalAura.INSTANCE != null && CrystalAura.INSTANCE.isEnabled() && CrystalAura.aimRotation != null) {
            return true;
        }
        if (FireballBlink.INSTANCE != null && FireballBlink.INSTANCE.isEnabled() && FireballBlink.rotation != null) {
            return true;
        }
        if (MidPearl.INSTANCE != null && MidPearl.INSTANCE.isEnabled() && MidPearl.targetRotation != null) {
            return true;
        }
        if (AntiTNT.INSTANCE != null && AntiTNT.INSTANCE.isEnabled() && AntiTNT.targetRotation != null) {
            return true;
        }
        if (Helper.INSTANCE != null && Helper.INSTANCE.isEnabled() && Helper.INSTANCE.hasTargetRotation()) {
            return true;
        }
        if (AntiWeb.INSTANCE != null && AntiWeb.INSTANCE.isEnabled() && AntiWeb.targetRotation != null) {
            return true;
        }
        if (AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled() && AutoWebPlace.targetRotation != null) {
            return true;
        }
        if (AutoThrow.INSTANCE != null && AutoThrow.INSTANCE.isEnabled() && AutoThrow.INSTANCE.targetRotation != null) {
            return true;
        }
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            return true;
        }
        if (KillAura.INSTANCE != null
                && KillAura.INSTANCE.isEnabled()
                && KillAura.target != null
                && KillAura.INSTANCE.rotation != null) {
            return true;
        }
        return AntiKB.INSTANCE != null && AntiKB.INSTANCE.isEnabled() && AntiKB.rotation != null;
    }

    private Rotation getCurrentRotation() {
        return new Rotation(mc.player.getYRot(), mc.player.getXRot());
    }

    private void resetState() {
        this.resetTracking();
        this.driftYaw = 0.0;
        this.driftPitch = 0.0;
        this.targetDriftYaw = 0.0;
        this.targetDriftPitch = 0.0;
        this.nextDriftTick = 0;
    }

    private void resetTracking() {
        this.currentTarget = null;
        this.currentTargetRotation = null;
    }

    private boolean isAdvancedMode() {
        return this.mode.is("Advanced");
    }

    private boolean isOldMode() {
        return this.mode.is("Old");
    }

    private double distanceToBox(Vec3 point, AABB box) {
        return closestPoint(point, box).distanceTo(point);
    }

    private double angleToPoint(Rotation currentRotation, Vec3 eyes, Vec3 point) {
        return this.angleBetween(currentRotation, RotationUtil.rotationTo(eyes, point));
    }

    private double angleBetween(Rotation a, Rotation b) {
        float yaw = Mth.wrapDegrees(b.getYaw() - a.getYaw());
        float pitch = b.getPitch() - a.getPitch();
        return Math.min(180.0, Math.sqrt(yaw * yaw + pitch * pitch));
    }

    private static Vec3 center(AABB box) {
        return new Vec3(
                (box.minX + box.maxX) * 0.5,
                (box.minY + box.maxY) * 0.5,
                (box.minZ + box.maxZ) * 0.5);
    }

    private static double boxHeight(AABB box) {
        return box.maxY - box.minY;
    }

    private static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    private static Vec3 clampPoint(Vec3 point, AABB box) {
        return closestPoint(point, box);
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static double randomSymmetric(double radius) {
        double safeRadius = Math.max(0.0, radius);
        if (safeRadius <= 1.0E-6) {
            return 0.0;
        }
        return ThreadLocalRandom.current().nextDouble(-safeRadius, safeRadius);
    }

    private record TargetCandidate(LivingEntity entity, double score) {
    }

    private record RotationWithPoint(Rotation rotation, Vec3 point) {
    }
}
