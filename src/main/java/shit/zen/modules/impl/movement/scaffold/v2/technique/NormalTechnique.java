/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldNormalTechnique.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.technique;

import java.util.List;
import net.minecraft.core.BlockPos;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.rotation.Rotation;

public final class NormalTechnique implements Technique {
    private final Settings settings;

    public NormalTechnique() {
        this(Settings.DEFAULT);
    }

    public NormalTechnique(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULT : settings;
    }

    @Override
    public String name() {
        return "Normal";
    }

    public Settings settings() {
        return this.settings;
    }

    @Override
    public List<TargetOffset> targetOffsets(TargetInput input) {
        return List.of(new TargetOffset(
                BlockPos.ZERO,
                SearchOffsets.NORMAL,
                TargetPriority.LINE_OR_POSITION,
                this.settings.aimMode(),
                false));
    }

    @Override
    public Rotation rotation(RotationInput input) {
        if (input.doNotAim()) {
            if (input.resetMode() == AimResetMode.RESET) {
                return null;
            }
            float yaw = TechniqueMath.roundToStep(input.playerYaw(), 45.0f);
            float pitch = input.playerPitch() < 45.0f ? 45.0f : input.playerPitch();
            return new Rotation(yaw, pitch);
        }

        BlockPlacementTarget target = input.target();
        if (target == null || target.rotation() == null) {
            return null;
        }
        if (this.settings.requiresSight() && !input.targetVisible()) {
            return null;
        }
        return target.rotation().clone();
    }

    public record Settings(AimMode aimMode, boolean requiresSight) {
        public static final Settings DEFAULT = new Settings(AimMode.STABILIZED, false);

        public Settings {
            if (aimMode == null) {
                throw new IllegalArgumentException("aimMode must not be null");
            }
        }
    }
}
