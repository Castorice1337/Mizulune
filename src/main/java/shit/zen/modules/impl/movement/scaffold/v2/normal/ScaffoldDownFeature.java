/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldDownFeature and ScaffoldNormalTechnique:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Java strategy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.normal;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import shit.zen.modules.impl.movement.scaffold.v2.technique.Technique;

public final class ScaffoldDownFeature {
    public static final Settings DEFAULTS = new Settings(false);

    private ScaffoldDownFeature() {
    }

    public static State evaluate(
            Settings settings,
            boolean shiftDown,
            boolean canStandTwoBlocksBelow) {
        Objects.requireNonNull(settings, "settings");
        boolean shouldGoDown = settings.enabled() && shiftDown;
        return new State(
                shouldGoDown,
                shouldGoDown && canStandTwoBlocksBelow);
    }

    public static BlockPos targetedPosition(BlockPos blockPos, State state) {
        Objects.requireNonNull(blockPos, "blockPos");
        Objects.requireNonNull(state, "state");
        return state.shouldGoDown() ? blockPos.below(2) : blockPos;
    }

    public static MovementInputDecision movementInput(boolean currentSneak, State state) {
        Objects.requireNonNull(state, "state");
        return state.shouldFallOffBlock()
                ? new MovementInputDecision(false, true)
                : new MovementInputDecision(currentSneak, false);
    }

    public static SafeWalkDecision safeWalk(boolean currentSafeWalk, State state) {
        Objects.requireNonNull(state, "state");
        return state.shouldFallOffBlock()
                ? new SafeWalkDecision(false, true)
                : new SafeWalkDecision(currentSafeWalk, false);
    }

    public static boolean considerFacingAwayFaces(State state) {
        Objects.requireNonNull(state, "state");
        return state.shouldGoDown();
    }

    public static Technique.SearchOffsets searchOffsets(
            Technique.SearchOffsets current,
            State state) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(state, "state");
        return state.shouldGoDown() ? Technique.SearchOffsets.DOWN : current;
    }

    public record Settings(boolean enabled) {
    }

    public record State(boolean shouldGoDown, boolean shouldFallOffBlock) {
    }

    public record MovementInputDecision(boolean sneak, boolean overridden) {
    }

    public record SafeWalkDecision(boolean safeWalk, boolean overridden) {
    }
}
