/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTechnique and scaffold techniques:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Java strategy boundary.
 */
package shit.zen.modules.impl.movement.scaffold.v2.technique;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import shit.zen.modules.impl.movement.scaffold.v2.LedgeAction;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;

public interface Technique {
    String name();

    List<TargetOffset> targetOffsets(TargetInput input);

    default Rotation rotation(RotationInput input) {
        BlockPlacementTarget target = input.target();
        return target == null || target.rotation() == null
                ? null
                : target.rotation().clone();
    }

    default DirectionalInput adjustInput(MovementInput input) {
        return input.directionalInput();
    }

    default LedgeAction ledgeAction(LedgeInput input) {
        return LedgeAction.NO_LEDGE;
    }

    enum SearchOffsets {
        NORMAL,
        DOWN,
        CARDINAL,
        EXACT
    }

    enum TargetPriority {
        LINE_OR_POSITION,
        POSITION
    }

    enum AimMode {
        CENTER,
        RANDOM,
        STABILIZED,
        NEAREST_ROTATION,
        REVERSE_YAW,
        DIAGONAL_YAW,
        ANGLE_YAW,
        EDGE_POINT
    }

    enum AimResetMode {
        RESET,
        REVERSE
    }

    record TargetOffset(
            BlockPos offset,
            SearchOffsets searchOffsets,
            TargetPriority priority,
            AimMode aimMode,
            boolean considerFacingAwayFaces) {
        public TargetOffset {
            if (offset == null || searchOffsets == null || priority == null || aimMode == null) {
                throw new IllegalArgumentException("Target offset fields must not be null");
            }
        }
    }

    record TargetInput(float playerYaw) {
    }

    record RotationInput(
            BlockPlacementTarget target,
            Vec3 eyePosition,
            float playerYaw,
            float playerPitch,
            DirectionalInput rawInput,
            boolean onGround,
            double playerX,
            double playerZ,
            boolean leaningOffBlock,
            boolean nextBlockAir,
            boolean doNotAim,
            AimResetMode resetMode,
            boolean targetVisible) {
        public RotationInput {
            rawInput = rawInput == null ? DirectionalInput.NONE : rawInput;
            resetMode = resetMode == null ? AimResetMode.RESET : resetMode;
        }
    }

    record MovementInput(
            DirectionalInput directionalInput,
            boolean shiftDown,
            boolean blockBelowAir,
            long nowMillis,
            double playerX,
            double playerZ,
            float playerYaw,
            double randomSample) {
        public MovementInput {
            directionalInput = directionalInput == null ? DirectionalInput.NONE : directionalInput;
        }
    }

    record LedgeInput(
            boolean selected,
            boolean snapshotLedged,
            BlockPlacementTarget target,
            boolean projectedTargetMatches,
            boolean projectedCrosshairValid,
            int blockCount,
            double actionSample,
            double sneakSample) {
    }

}
