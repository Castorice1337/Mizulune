/*
 * Southside Telly behavior is adapted from OpenSSNG Scaffold.
 * Copyright (c) 2026 Un4nown. Licensed under MIT; see
 * liquidSRC/OpenSSNGScaffoldAndClutch-main/LICENSE.
 */
package shit.zen.modules.impl.movement.scaffold.v2.newtelly;

import java.util.List;
import java.util.Objects;
import net.minecraft.util.Mth;
import shit.zen.utils.rotation.Rotation;

/**
 * Side-effect-free Southside Telly timing and rotation policy.
 *
 * <p>The source snapshot has missing rotation helpers. The yaw limiter used
 * here is the agreed OpenZen adapter: move from the current server yaw toward
 * the target by at most the supplied degree limit.</p>
 */
public final class ScaffoldNewTellyPolicy {
    private static final double DUPLICATE_EPSILON = 1.0E-4;

    private Rotation lastRotation;
    private Rotation lastIssuedRotation;
    private int slowUpPlanningTicks;
    private int missingTargetTicks;
    private double lastSuccessfulPitchDifference = Double.NaN;

    public void reset() {
        this.lastRotation = null;
        this.lastIssuedRotation = null;
        this.slowUpPlanningTicks = 0;
        this.missingTargetTicks = 0;
        this.lastSuccessfulPitchDifference = Double.NaN;
    }

    public MovementDecision movement(Settings settings, MovementFrame frame) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(frame, "frame");

        int requiredGroundTicks = settings.heyPixelUpTelly()
                && settings.safeMode()
                && !settings.testOnGround()
                ? 1
                : 0;
        boolean readyToJump = frame.groundTicks() > requiredGroundTicks
                && !frame.physicalJump()
                && frame.moving()
                && frame.hasBlocks();
        boolean jump = readyToJump && switch (settings.jumpMode()) {
            case NORMAL -> true;
            case PARKOUR -> frame.firstForwardBlockAir() || frame.secondForwardBlockAir();
            case NONE -> false;
        };

        boolean slowInput = frame.groundTicks() == 1
                && settings.testOnGround()
                && settings.heyPixelUpTelly()
                && !settings.noUpTelly()
                && settings.safeMode()
                && frame.physicalJump();
        return new MovementDecision(jump, slowInput ? 0.2f : 1.0f);
    }

    public boolean canPlace(Settings settings, TimingFrame frame) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(frame, "frame");
        if (frame.airTicks() >= settings.placeTick()) {
            return true;
        }
        return settings.safeMode()
                && settings.testOnGround()
                && frame.physicalJump()
                && frame.groundTicks() == 1;
    }

    public static int pendingTargetTicks(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        return Math.max(settings.placeTick(), settings.rotationTick()) + 2;
    }

    public RotationDecision rotation(
            Settings settings,
            TimingFrame frame,
            Rotation targetRotation,
            Rotation serverRotation,
            Rotation playerRotation,
            boolean previousRotationHitsTarget,
            RotationNoise noise) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(frame, "frame");
        RotationNoise safeNoise = noise == null ? RotationNoise.NONE : noise;
        if (targetRotation == null || serverRotation == null || playerRotation == null) {
            return RotationDecision.none("missing-rotation");
        }
        this.missingTargetTicks = 0;

        Rotation target = continuousTarget(targetRotation, serverRotation);
        float playerYaw = nearestEquivalentYaw(playerRotation.getYaw(), serverRotation.getYaw());
        Rotation next = target;
        int jumpDelay = 0;
        String source = "target";
        boolean reachesFinalReuse = true;
        if (frame.physicalJump() && settings.noUpTelly()) {
            source = "no-up-telly";
            reachesFinalReuse = false;
        } else if (frame.physicalJump()
                && settings.slowUpTelly()
                && ++this.slowUpPlanningTicks % 2 == 0) {
            source = "slow-up-direct";
            reachesFinalReuse = false;
        } else {
            if (settings.heyPixelUpTelly()
                    && (frame.airTicks() < settings.rotationTick() || settings.safeMode())) {
                if (frame.groundTicks() > 0) {
                    if (settings.safeMode()
                            && (!settings.testOnGround() || frame.physicalJump())) {
                        if (frame.groundTicks() == 1) {
                            jumpDelay = 2;
                            if (!frame.forceRotation()) {
                                float difference = Mth.wrapDegrees(
                                        target.getYaw() - serverRotation.getYaw());
                                next = new Rotation(
                                        limitedYaw(serverRotation.getYaw(), target.getYaw(),
                                                Math.abs(difference) * 0.5f),
                                        75.5f);
                                source = "safe-ground-half";
                            } else {
                                source = "safe-ground-force";
                            }
                        } else if (frame.groundTicks() == 2) {
                            next = new Rotation(playerYaw, 75.5f);
                            source = "safe-ground-player";
                            reachesFinalReuse = false;
                        }
                    } else {
                        next = new Rotation(playerYaw, 75.5f);
                        source = "ground-player";
                        reachesFinalReuse = false;
                    }
                } else {
                    float baseLimit = frame.airTicks() == 1 ? 80.0f : 50.0f;
                    float limit = Math.max(0.0f, baseLimit - safeNoise.smoothReduction());
                    next = new Rotation(
                            limitedYaw(serverRotation.getYaw(), target.getYaw(), limit),
                            target.getPitch());
                    source = frame.airTicks() == 1 ? "air-80" : "air-50";
                }
            } else if (frame.airTicks() < settings.rotationTick()) {
                next = new Rotation(
                        playerYaw,
                        85.0f + Mth.clamp(safeNoise.earlyPitchAddition(), 0.0f, 1.0f));
                source = "early-player";
                reachesFinalReuse = false;
            }
        }

        if (!settings.alwaysUpdateRotation()
                && previousRotationHitsTarget
                && this.lastRotation != null) {
            next = this.lastRotation.clone();
            source = "previous-strict-hit";
        }

        if (settings.duplicateRotPlace()) {
            next = new Rotation(
                    next.getYaw() - Mth.clamp(safeNoise.yawJitter(), 0.0001f, 0.0003f),
                    Mth.clamp(
                            next.getPitch()
                                    - Mth.clamp(safeNoise.pitchJitter(), 0.001f, 0.003f)
                                    - Mth.clamp(safeNoise.secondPitchJitter(), 0.001f, 0.003f),
                            -90.0f,
                            90.0f));
        }
        if (reachesFinalReuse) {
            this.lastRotation = next.clone();
        }
        this.lastIssuedRotation = next.clone();
        return new RotationDecision(next, jumpDelay, source);
    }

    public RotationDecision holdForMissingTarget(int maxTicks) {
        if (maxTicks <= 0 || this.lastIssuedRotation == null) {
            return RotationDecision.none("no-target");
        }
        // Bridge cells briefly disappear between a successful place and the next target.
        this.missingTargetTicks++;
        if (this.missingTargetTicks > maxTicks) {
            return RotationDecision.none("gap-hold-expired");
        }
        return new RotationDecision(
                this.lastIssuedRotation,
                0,
                "gap-hold-" + this.missingTargetTicks + "/" + maxTicks);
    }

    public boolean blocksDuplicatePlacement(Settings settings, double pitchDifference) {
        Objects.requireNonNull(settings, "settings");
        return settings.duplicateRotPlace()
                && Double.isFinite(pitchDifference)
                && pitchDifference > 2.0
                && Double.isFinite(this.lastSuccessfulPitchDifference)
                && Math.abs(pitchDifference - this.lastSuccessfulPitchDifference)
                < DUPLICATE_EPSILON;
    }

    public void onPlacementSuccess(double pitchDifference) {
        if (Double.isFinite(pitchDifference) && pitchDifference > 0.0) {
            this.lastSuccessfulPitchDifference = pitchDifference;
        }
    }

    public Rotation lastRotation() {
        return this.lastRotation == null ? null : this.lastRotation.clone();
    }

    public double lastSuccessfulPitchDifference() {
        return this.lastSuccessfulPitchDifference;
    }

    public static int selectHotbarSlot(
            BlockSlotMode mode,
            int selectedSlot,
            int doNotUseBelowCount,
            List<SlotCandidate> candidates) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(candidates, "candidates");

        List<SlotCandidate> valid = candidates.stream()
                .filter(candidate -> candidate != null
                        && candidate.valid()
                        && candidate.slot() >= 0
                        && candidate.slot() < 9)
                .toList();
        SlotCandidate selected = valid.stream()
                .filter(candidate -> candidate.slot() == selectedSlot)
                .findFirst()
                .orElse(null);
        if (mode == BlockSlotMode.FARTHEST && selected != null) {
            return selectedSlot;
        }

        List<SlotCandidate> aboveThreshold = valid.stream()
                .filter(candidate -> candidate.count() > doNotUseBelowCount)
                .toList();
        List<SlotCandidate> pool = aboveThreshold.isEmpty() ? valid : aboveThreshold;
        if (pool.isEmpty()) {
            return -1;
        }

        if (mode == BlockSlotMode.FARTHEST) {
            return pool.stream().mapToInt(SlotCandidate::slot).max().orElse(-1);
        }

        SlotCandidate best = selected != null && pool.contains(selected) ? selected : null;
        for (SlotCandidate candidate : pool) {
            if (best == null || candidate.count() > best.count()) {
                best = candidate;
            }
        }
        return best == null ? -1 : best.slot();
    }

    static float limitedYaw(float serverYaw, float targetYaw, float limit) {
        float difference = Mth.wrapDegrees(targetYaw - serverYaw);
        return serverYaw + Mth.clamp(difference, -Math.abs(limit), Math.abs(limit));
    }

    private static Rotation continuousTarget(Rotation target, Rotation server) {
        return new Rotation(
                nearestEquivalentYaw(target.getYaw(), server.getYaw()),
                Mth.clamp(target.getPitch(), -90.0f, 90.0f));
    }

    private static float nearestEquivalentYaw(float yaw, float reference) {
        return reference + Mth.wrapDegrees(yaw - reference);
    }

    public enum JumpMode {
        PARKOUR,
        NORMAL,
        NONE
    }

    public enum BlockSlotMode {
        FARTHEST,
        MOST_BLOCKS
    }

    public record Settings(
            boolean alwaysUpdateRotation,
            int placeTick,
            int rotationTick,
            boolean noUpTelly,
            boolean heyPixelUpTelly,
            boolean safeMode,
            boolean testOnGround,
            boolean fixRotation,
            boolean slowUpTelly,
            boolean duplicateRotPlace,
            boolean interactItemBeforePlace,
            JumpMode jumpMode,
            BlockSlotMode blockSlotMode) {
        public static final Settings SCREENSHOT_DEFAULTS = new Settings(
                true,
                1,
                3,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                true,
                JumpMode.NORMAL,
                BlockSlotMode.FARTHEST);

        public Settings {
            if (placeTick < 1 || placeTick > 5) {
                throw new IllegalArgumentException("placeTick must be in 1..5");
            }
            if (rotationTick < 1 || rotationTick > 5) {
                throw new IllegalArgumentException("rotationTick must be in 1..5");
            }
            Objects.requireNonNull(jumpMode, "jumpMode");
            Objects.requireNonNull(blockSlotMode, "blockSlotMode");
        }
    }

    public record MovementFrame(
            int groundTicks,
            boolean physicalJump,
            boolean moving,
            boolean hasBlocks,
            boolean firstForwardBlockAir,
            boolean secondForwardBlockAir) {
    }

    public record MovementDecision(boolean jump, float inputScale) {
    }

    public record TimingFrame(
            int groundTicks,
            int airTicks,
            boolean physicalJump,
            boolean forceRotation) {
    }

    public record RotationNoise(
            float smoothReduction,
            float earlyPitchAddition,
            float yawJitter,
            float pitchJitter,
            float secondPitchJitter) {
        public static final RotationNoise NONE = new RotationNoise(
                0.0f, 0.0f, 0.0001f, 0.001f, 0.001f);
    }

    public record RotationDecision(Rotation rotation, int jumpDelayTicks, String source) {
        public RotationDecision {
            rotation = rotation == null ? null : rotation.clone();
            source = source == null ? "unknown" : source;
        }

        private static RotationDecision none(String source) {
            return new RotationDecision(null, 0, source);
        }
    }

    public record SlotCandidate(int slot, int count, boolean valid) {
    }
}
