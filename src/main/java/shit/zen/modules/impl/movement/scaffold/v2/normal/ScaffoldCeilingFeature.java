/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldCeilingFeature and ModuleScaffold:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Java strategy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.normal;

import java.util.Objects;
import net.minecraft.core.BlockPos;

public final class ScaffoldCeilingFeature {
    public static final Settings DEFAULTS = new Settings(false);

    private ScaffoldCeilingFeature() {
    }

    public static Decision decide(
            Settings settings,
            BlockPos blockPos,
            boolean blockBelowAir) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(blockPos, "blockPos");
        boolean canConstructCeiling = !blockBelowAir;
        boolean active = settings.enabled() && canConstructCeiling;
        return new Decision(
                canConstructCeiling,
                active,
                active ? blockPos.above(3) : blockPos);
    }

    public record Settings(boolean enabled) {
    }

    public record Decision(
            boolean canConstructCeiling,
            boolean active,
            BlockPos targetedPosition) {
        public Decision {
            Objects.requireNonNull(targetedPosition, "targetedPosition");
        }
    }
}
