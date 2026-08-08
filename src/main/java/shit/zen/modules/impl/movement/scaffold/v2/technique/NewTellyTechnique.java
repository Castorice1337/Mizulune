/*
 * This file is part of Mizulune/OpenZen.
 *
 * Southside Telly behavior is adapted from OpenSSNG Scaffold.
 * Copyright (c) 2026 Un4nown. Licensed under MIT; see
 * liquidSRC/OpenSSNGScaffoldAndClutch-main/LICENSE.
 */
package shit.zen.modules.impl.movement.scaffold.v2.technique;

import java.util.List;
import net.minecraft.core.BlockPos;

/** Finder declaration for the independent Southside-style New Telly mode. */
public final class NewTellyTechnique implements Technique {
    private static final List<TargetOffset> FRESH_TARGETS = List.of(
            new TargetOffset(
                    BlockPos.ZERO,
                    SearchOffsets.EXACT,
                    TargetPriority.POSITION,
                    AimMode.NEAREST_ROTATION,
                    false),
            new TargetOffset(
                    BlockPos.ZERO,
                    SearchOffsets.CARDINAL,
                    TargetPriority.POSITION,
                    AimMode.NEAREST_ROTATION,
                    false));
    private static final List<TargetOffset> PENDING_TARGET = List.of(new TargetOffset(
            BlockPos.ZERO,
            SearchOffsets.EXACT,
            TargetPriority.POSITION,
            AimMode.NEAREST_ROTATION,
            false));

    @Override
    public String name() {
        return "New Telly";
    }

    @Override
    public List<TargetOffset> targetOffsets(TargetInput input) {
        return FRESH_TARGETS;
    }

    public List<TargetOffset> pendingTargetOffsets() {
        return PENDING_TARGET;
    }
}
