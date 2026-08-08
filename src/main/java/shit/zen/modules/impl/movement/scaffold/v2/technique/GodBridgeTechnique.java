/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldGodBridgeTechnique.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.technique;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import shit.zen.modules.impl.movement.scaffold.v2.LedgeAction;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;

public final class GodBridgeTechnique implements Technique {
    private final Settings settings;
    private boolean onRightSide;

    public GodBridgeTechnique() {
        this(Settings.DEFAULT);
    }

    public GodBridgeTechnique(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULT : settings;
    }

    @Override
    public String name() {
        return "GodBridge";
    }

    public Settings settings() {
        return this.settings;
    }

    public boolean isOnRightSide() {
        return this.onRightSide;
    }

    public void reset() {
        this.onRightSide = false;
    }

    @Override
    public List<TargetOffset> targetOffsets(TargetInput input) {
        return List.of(new TargetOffset(
                BlockPos.ZERO,
                SearchOffsets.NORMAL,
                TargetPriority.POSITION,
                AimMode.CENTER,
                false));
    }

    @Override
    public Rotation rotation(RotationInput input) {
        DirectionalInput rawInput = input.rawInput();
        if (!rawInput.isMoving()) {
            return noInputRotation(input.target());
        }

        float direction = TechniqueMath.movementYaw(input.playerYaw(), rawInput) + 180.0f;
        float movingYaw = TechniqueMath.roundToStep(direction, 45.0f);
        if (movingYaw % 90.0f != 0.0f) {
            return new Rotation(movingYaw, 75.6f);
        }

        if (input.onGround()) {
            double radians = Math.toRadians(movingYaw);
            this.onRightSide = Math.floor(input.playerX() + Math.cos(radians) * 0.5)
                    != Math.floor(input.playerX())
                    || Math.floor(input.playerZ() + Math.sin(radians) * 0.5)
                    != Math.floor(input.playerZ());
            if (input.leaningOffBlock() && input.nextBlockAir()) {
                this.onRightSide = !this.onRightSide;
            }
        }

        return new Rotation(movingYaw + (this.onRightSide ? 45.0f : -45.0f), 75.7f);
    }

    @Override
    public LedgeAction ledgeAction(LedgeInput input) {
        if (!input.selected() || !input.snapshotLedged() || input.target() == null) {
            return LedgeAction.NO_LEDGE;
        }
        if (input.projectedTargetMatches() && input.projectedCrosshairValid()) {
            return LedgeAction.NO_LEDGE;
        }

        LedgeMode mode = input.blockCount() < this.settings.forceSneakBelowCount()
                ? LedgeMode.SNEAK
                : this.selectMode(input.actionSample());
        return switch (mode) {
            case JUMP -> new LedgeAction(true, 0, false, false);
            case SNEAK -> new LedgeAction(false, TechniqueMath.sampleInclusive(
                    this.settings.sneakTicksMin(),
                    this.settings.sneakTicksMax(),
                    input.sneakSample()), false, false);
            case STOP_INPUT -> new LedgeAction(false, 0, true, false);
            case BACKWARDS -> new LedgeAction(false, 0, false, true);
        };
    }

    private LedgeMode selectMode(double sample) {
        List<LedgeMode> modes = new ArrayList<>(this.settings.modes());
        int index = Math.min(
                modes.size() - 1,
                (int) (TechniqueMath.unitSample(sample) * modes.size()));
        return modes.get(index);
    }

    private static Rotation noInputRotation(BlockPlacementTarget target) {
        if (target == null || target.rotation() == null) {
            return null;
        }
        float axisMovement = (float) (Math.floor(target.rotation().yaw / 90.0f) * 90.0f);
        return new Rotation(axisMovement + 45.0f, 75.0f);
    }

    public enum LedgeMode {
        JUMP,
        SNEAK,
        STOP_INPUT,
        BACKWARDS
    }

    public record Settings(
            Set<LedgeMode> modes,
            int forceSneakBelowCount,
            int sneakTicksMin,
            int sneakTicksMax) {
        public static final Settings DEFAULT = new Settings(Set.of(LedgeMode.JUMP), 3, 1, 1);

        public Settings {
            if (modes == null || modes.isEmpty()) {
                throw new IllegalArgumentException("modes must not be empty");
            }
            modes = Collections.unmodifiableSet(EnumSet.copyOf(modes));
            if (forceSneakBelowCount < 0 || forceSneakBelowCount > 10) {
                throw new IllegalArgumentException("forceSneakBelowCount must be in [0, 10]");
            }
            if (sneakTicksMin < 1 || sneakTicksMax > 10 || sneakTicksMin > sneakTicksMax) {
                throw new IllegalArgumentException("sneak ticks must be an ordered range in [1, 10]");
            }
        }
    }
}
